#!/usr/bin/env python3
"""Build and publish Karoo updates. Signing keys never leave this computer."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
CHANNEL = json.loads((ROOT / 'releases/channel.json').read_text())
ANDROID = '{http://schemas.android.com/apk/res/android}'


def run(*args, capture=False):
    return subprocess.run([str(a) for a in args], cwd=ROOT, check=True,
                          text=True, stdout=subprocess.PIPE if capture else None).stdout


def environment():
    properties = ROOT / '.gradle/config.properties'
    if not os.environ.get('JAVA_HOME') and properties.exists():
        match = re.search(r'^java.home=(.+)$', properties.read_text(), re.M)
        if match:
            os.environ['JAVA_HOME'] = match[1]
    local = ROOT / 'local.properties'
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if not sdk and local.exists():
        match = re.search(r'^sdk.dir=(.+)$', local.read_text(), re.M)
        sdk = match[1] if match else None
    if not sdk:
        raise ValueError('Android SDK fehlt. Projekt zuerst in Android Studio synchronisieren.')
    versions = [p for p in (Path(sdk) / 'build-tools').iterdir()
                if (p / 'apksigner').exists() and (p / 'aapt').exists()]
    if not versions:
        raise ValueError('Android Build Tools fehlen.')
    return sorted(versions, key=lambda p: tuple(int(n) for n in re.findall(r'\d+', p.name)))[-1]


def inspect_apk(apk, tools):
    certs = run(tools / 'apksigner', 'verify', '--print-certs', apk, capture=True)
    digests = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)', certs)
    if digests != [CHANNEL['certificateSha256']]:
        raise ValueError('Signatur passt nicht zur installierten App. Veröffentlichung abgebrochen.')
    badging = run(tools / 'aapt', 'dump', 'badging', apk, capture=True)
    match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not match or match[1] != CHANNEL['packageName']:
        raise ValueError('Falsches APK-Paket.')
    return match[3], int(match[2])


def manifest(version, code, notes):
    repo = CHANNEL['repository']
    return dict(label='Stacked Fields', packageName=CHANNEL['packageName'],
                latestApkUrl=f'https://github.com/{repo}/releases/download/v{version}/karoo-stacked-fields-{version}.apk',
                latestVersion=version, latestVersionCode=code, developer='Jannes Lojkasek',
                description='Kompakte Datenfelder für Leistung, Herzfrequenz, Geschwindigkeit, Strecke und Trittfrequenz.',
                releaseNotes=notes)


def prepare(apk, tools):
    version, code = inspect_apk(apk, tools)
    gradle = (ROOT / 'app/build.gradle.kts').read_text()
    expected_version = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)[1]
    expected_code = int(re.search(r'versionCode\s*=\s*(\d+)', gradle)[1])
    if (version, code) != (expected_version, expected_code):
        raise ValueError('APK ist veraltet. Vorher neu bauen.')
    if not re.fullmatch(r'\d+\.\d+\.\d+', version):
        raise ValueError('Release-Version muss das Format 0.1.2 haben.')
    notes = (ROOT / f'releases/{version}.md').read_text().strip()
    folder = ROOT / 'dist' / f'v{version}'
    folder.mkdir(parents=True, exist_ok=True)
    target = folder / f'karoo-stacked-fields-{version}.apk'
    shutil.copy2(apk, target)
    document = manifest(version, code, notes)
    source = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml')
    urls = [n.get(ANDROID+'value') for n in source.findall('./application/meta-data')
            if n.get(ANDROID+'name') == 'io.hammerhead.karooext.MANIFEST_URL']
    expected_url = f"https://github.com/{CHANNEL['repository']}/releases/latest/download/manifest.json"
    if urls != [expected_url]:
        raise ValueError('Update-Adresse und Veröffentlichungskanal passen nicht zusammen.')
    (folder / 'manifest.json').write_text(json.dumps(document, indent=2, ensure_ascii=False)+'\n')
    assets = [target, folder / 'manifest.json']
    checksums = ''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n' for p in assets)
    (folder / 'SHA256SUMS').write_text(checksums)
    return version, code, folder, assets + [folder / 'SHA256SUMS']


def publish(version, code, assets, setup):
    repo = CHANNEL['repository']
    run('gh', 'auth', 'status')
    # Distinguish missing repositories from network/auth failures; never hide errors.
    owned = json.loads(run('gh', 'api', '--paginate', '--slurp',
                          'user/repos?per_page=100&affiliation=owner', capture=True))
    repos = [r for page in owned for r in page]
    info = next((r for r in repos if r['full_name'].lower() == repo.lower()), None)
    if info is None:
        if not setup:
            raise ValueError('Download-Repository fehlt. Einmal mit --setup veröffentlichen.')
        run('gh', 'repo', 'create', repo, '--public', '--add-readme', '--description',
            'APK downloads and update manifest for Karoo Stacked Fields. Source code is maintained separately.')
    elif info['private']:
        raise ValueError('Download-Repository ist privat. Es wird nicht automatisch öffentlich gemacht.')
    releases = json.loads(run('gh', 'api', '--paginate', '--slurp',
                             f'repos/{repo}/releases?per_page=100', capture=True))
    releases = [r for page in releases for r in page]
    tag = f'v{version}'
    existing = next((r for r in releases if r['tag_name'] == tag), None)
    if existing and not existing['draft']:
        raise ValueError('Diese Version ist bereits veröffentlicht und wird nicht überschrieben.')
    latest = next((r for r in releases if not r['draft'] and not r['prerelease']), None)
    if latest:
        prior = json.loads(run('gh', 'release', 'download', latest['tag_name'], '--repo', repo,
                               '--pattern', 'manifest.json', '--output', '-', capture=True))
        if code <= prior['latestVersionCode']:
            raise ValueError('versionCode muss höher als im bisherigen Update sein.')
    if not existing:
        run('gh', 'release', 'create', tag, '--repo', repo, '--draft', '--title',
            f'Karoo Stacked Fields {version}', '--notes-file', ROOT / f'releases/{version}.md')
    run('gh', 'release', 'upload', tag, *assets, '--repo', repo, '--clobber')
    run('gh', 'release', 'edit', tag, '--repo', repo, '--draft=false', '--latest')
    # Prove that the device can fetch both files without GitHub credentials.
    url = f'https://github.com/{repo}/releases/latest/download/manifest.json'
    with urllib.request.urlopen(url, timeout=60) as response:
        published = json.load(response)
    if published != json.loads(assets[1].read_text()):
        raise ValueError('Öffentliches Manifest stimmt nicht mit dem Release überein.')
    with urllib.request.urlopen(published['latestApkUrl'], timeout=60) as response:
        digest = hashlib.sha256()
        for chunk in iter(lambda: response.read(1024*1024), b''):
            digest.update(chunk)
    if digest.hexdigest() != hashlib.sha256(assets[0].read_bytes()).hexdigest():
        raise ValueError('Öffentlicher APK-Download stimmt nicht mit dem Release überein.')
    print(f'Update öffentlich geprüft: {url}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prepare-only', action='store_true', help='Nur lokale Release-Dateien erzeugen')
    parser.add_argument('--test-on-karoo', action='store_true', help='Die Release-APK vor der Veröffentlichung per ADB testen')
    parser.add_argument('--setup', action='store_true', help='Fehlendes öffentliches Download-Repository anlegen')
    args = parser.parse_args()
    if not args.prepare_only:
        if run('git', 'status', '--porcelain', '--untracked-files=all', capture=True).strip():
            raise ValueError('Release-Änderungen zuerst committen. Das Arbeitsverzeichnis muss sauber sein.')
        version = re.search(r'versionName\s*=\s*"([^"]+)"', (ROOT / 'app/build.gradle.kts').read_text())[1]
        tags = run('git', 'tag', '--points-at', 'HEAD', capture=True).splitlines()
        if f'v{version}' not in tags:
            raise ValueError(f'Der aktuelle Commit braucht den Tag v{version}.')
    tools = environment()
    run(sys.executable, '-m', 'unittest', 'discover', '-s', 'scripts/tests')
    run('./gradlew', ':app:testReleaseUnitTest', ':app:lintRelease', ':app:assembleRelease')
    version, code, folder, assets = prepare(ROOT / 'app/build/outputs/apk/release/app-release.apk', tools)
    print(f'Release {version} ({code}) vorbereitet: {folder}', flush=True)
    if args.test_on_karoo:
        run('sh', ROOT / 'scripts/install.sh', assets[0])
        if not args.prepare_only and input('Karoo-Test bestanden? Zum Veröffentlichen „ja“ eingeben: ').strip().lower() != 'ja':
            return
    if not args.prepare_only:
        publish(version, code, assets, args.setup)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, EOFError, subprocess.CalledProcessError) as error:
        print(f'Release nicht abgeschlossen: {error}', file=sys.stderr)
        sys.exit(1)
