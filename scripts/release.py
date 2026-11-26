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
import tempfile
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
    tools = Path(sdk) / 'build-tools' / '35.0.0'
    if not (tools / 'apksigner').exists() or not (tools / 'aapt').exists():
        raise ValueError('Android Build Tools 35.0.0 fehlen. Mit sdkmanager installieren.')
    return tools


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
    image_base = f'https://raw.githubusercontent.com/{repo}/refs/tags/v{version}/docs/images/karoo'
    return dict(label='kEtape', packageName=CHANNEL['packageName'],
                latestApkUrl=f'https://github.com/{repo}/releases/download/v{version}/ketape-{version}.apk',
                latestVersion=version, latestVersionCode=code, developer='Jannes Lojkasek',
                description=(
                    'Bessere Fahrdaten für deinen Karoo. Passe deine Stacked Fields an, '
                    'vergleiche mit Palmarès deine Leistungsbestwerte und verfolge mit Réserve deine W′-Reserve.'
                ),
                releaseNotes=notes,
                screenshotUrls=[f'{image_base}/{name}.png' for name in ('stacked-fields', 'palmares', 'reserve')])


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
    target = folder / f'ketape-{version}.apk'
    shutil.copy2(apk, target)
    document = manifest(version, code, notes)
    source = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml')
    urls = [n.get(ANDROID+'value') for n in source.findall('./application/meta-data')
            if n.get(ANDROID+'name') == 'io.hammerhead.karooext.MANIFEST_URL']
    expected_url = f"https://github.com/{CHANNEL['repository']}/releases/latest/download/manifest.json"
    if urls != [expected_url]:
        raise ValueError('Update-Adresse und Veröffentlichungskanal passen nicht zusammen.')
    (folder / 'manifest.json').write_text(json.dumps(document, indent=2, ensure_ascii=False)+'\n')
    info = {
        'sourceCommit': run('git', 'rev-parse', 'HEAD', capture=True).strip(),
        'sourceDirty': bool(run('git', 'status', '--porcelain', '--untracked-files=all', capture=True).strip()),
        'version': version, 'versionCode': code,
        'apkSha256': hashlib.sha256(target.read_bytes()).hexdigest(),
        'gradle': '8.9', 'androidGradlePlugin': '8.7.3', 'kotlin': '2.0.21',
        'androidBuildTools': tools.name,
        'java': run(Path(os.environ['JAVA_HOME']) / 'bin/java' if os.environ.get('JAVA_HOME') else 'java',
                    '--version', capture=True).splitlines()[0],
    }
    (folder / 'build-info.json').write_text(json.dumps(info, indent=2)+'\n')
    assets = [target, folder / 'manifest.json', folder / 'build-info.json']
    checksums = ''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n' for p in assets)
    (folder / 'SHA256SUMS').write_text(checksums)
    return version, code, folder, assets + [folder / 'SHA256SUMS']


def check_source(version):
    if run('git', 'status', '--porcelain', '--untracked-files=all', capture=True).strip():
        raise ValueError('Release-Änderungen zuerst committen. Das Arbeitsverzeichnis muss sauber sein.')
    head = run('git', 'rev-parse', 'HEAD', capture=True).strip()
    tag = f'v{version}'
    if tag not in run('git', 'tag', '--points-at', 'HEAD', capture=True).splitlines():
        raise ValueError(f'Der aktuelle Commit braucht den Tag {tag}.')
    remote = run('git', 'ls-remote', 'origin', f'refs/tags/{tag}', f'refs/tags/{tag}^{{}}', capture=True)
    refs = dict(line.split()[::-1] for line in remote.splitlines())
    if refs.get(f'refs/tags/{tag}^{{}}', refs.get(f'refs/tags/{tag}')) != head:
        raise ValueError('Quellcommit und Tag müssen vor der Veröffentlichung auf origin gesichert sein.')
    runs = json.loads(run('gh', 'run', 'list', '--commit', head, '--workflow', 'build.yml',
                         '--event', 'push', '--branch', 'main', '--limit', '20', '--json', 'headSha,status,conclusion', capture=True))
    matching = [item for item in runs if item['headSha'] == head]
    if not matching or matching[0]['status'] != 'completed' or matching[0]['conclusion'] != 'success':
        raise ValueError('Für diesen Quellcommit fehlt ein erfolgreicher aktueller CI-Lauf.')
    return head


def public_bytes(url):
    with urllib.request.urlopen(url, timeout=60) as response:
        return response.read()


def verify_public(version, tools, expected_assets=None, require_latest=False):
    repo = CHANNEL['repository']
    if not re.fullmatch(r'\d+\.\d+\.\d+', version):
        raise ValueError('Ungültige Release-Version.')
    base = f"https://github.com/{repo}/releases/download/v{version}/"
    raw = public_bytes(base + 'manifest.json')
    document = json.loads(raw)
    apk_name = f'ketape-{version}.apk'
    if document.get('packageName') != CHANNEL['packageName'] or document.get('latestVersion') != version:
        raise ValueError('Öffentliches Manifest hat falsches Paket oder falsche Version.')
    if document.get('latestApkUrl') != base + apk_name:
        raise ValueError('Öffentliches Manifest verweist nicht auf das erwartete APK.')
    checksums = {}
    for line in public_bytes(base + 'SHA256SUMS').decode().splitlines():
        digest, name = line.split()
        if name in checksums or not re.fullmatch('[0-9a-f]{64}', digest):
            raise ValueError('Ungültige Prüfsummenliste.')
        if name not in {apk_name, 'manifest.json', 'build-info.json'}:
            raise ValueError('Unerwartete Datei in Prüfsummenliste.')
        checksums[name] = digest
    if not {apk_name, 'manifest.json'} <= checksums.keys():
        raise ValueError('Unvollständige Prüfsummenliste.')
    payloads = {name: raw if name == 'manifest.json' else public_bytes(base + name) for name in checksums}
    for name, data in payloads.items():
        if hashlib.sha256(data).hexdigest() != checksums[name]:
            raise ValueError(f'Öffentliche Prüfsumme stimmt nicht: {name}')
    if expected_assets:
        for asset in expected_assets:
            actual = payloads.get(asset.name) if asset.name != 'SHA256SUMS' else public_bytes(base + asset.name)
            if actual != asset.read_bytes():
                raise ValueError(f'Öffentliche Datei stimmt nicht mit lokalem Release überein: {asset.name}')
    with tempfile.TemporaryDirectory(prefix='ketape-verify-') as folder:
        apk = Path(folder) / apk_name
        apk.write_bytes(payloads[apk_name])
        if inspect_apk(apk, tools) != (version, document['latestVersionCode']):
            raise ValueError('APK-Version und Manifest stimmen nicht überein.')
    if require_latest:
        latest = json.loads(public_bytes(f"https://github.com/{repo}/releases/latest/download/manifest.json"))
        if latest != document:
            raise ValueError('Der Latest-Kanal verweist nicht auf diesen Release.')
    print(f'Release {version}: öffentliche Dateien, Prüfsummen, Paket und Signatur geprüft.')


class PublishedVerificationError(ValueError):
    pass


def publish(version, code, assets):
    repo = CHANNEL['repository']
    run('gh', 'auth', 'status')
    # Distinguish missing repositories from network/auth failures; never hide errors.
    owned = json.loads(run('gh', 'api', '--paginate', '--slurp',
                          'user/repos?per_page=100&affiliation=owner', capture=True))
    repos = [r for page in owned for r in page]
    info = next((r for r in repos if r['full_name'].lower() == repo.lower()), None)
    if info is None:
        raise ValueError('Release-Repository fehlt.')
    elif info['private']:
        raise ValueError('Download-Repository ist privat. Es wird nicht automatisch öffentlich gemacht.')
    releases = json.loads(run('gh', 'api', '--paginate', '--slurp',
                             f'repos/{repo}/releases?per_page=100', capture=True))
    releases = [r for page in releases for r in page]
    tag = f'v{version}'
    existing = next((r for r in releases if r['tag_name'] == tag), None)
    if existing and not existing['draft']:
        raise ValueError('Diese Version ist bereits veröffentlicht und wird nicht überschrieben.')
    check_version_codes(repo, code, releases)
    if not existing:
        run('gh', 'release', 'create', tag, '--repo', repo, '--draft', '--title',
            f'kEtape {version}', '--notes-file', ROOT / f'releases/{version}.md', '--verify-tag')
    run('gh', 'release', 'upload', tag, *assets, '--repo', repo, '--clobber')
    # Read the uploaded draft assets back before switching the public channel.
    for asset in assets:
        with tempfile.TemporaryDirectory(prefix='ketape-draft-') as folder:
            downloaded = Path(folder) / asset.name
            run('gh', 'release', 'download', tag, '--repo', repo, '--pattern', asset.name,
                '--output', downloaded)
            if downloaded.read_bytes() != asset.read_bytes():
                raise ValueError(f'Entwurfsdatei stimmt nicht: {asset.name}')
    run('gh', 'release', 'edit', tag, '--repo', repo, '--draft=false', '--latest')
    try:
        verify_public(version, environment(), assets, require_latest=True)
    except Exception as error:
        raise PublishedVerificationError(
            f'Release {version} IST veröffentlicht; die Nachprüfung ist fehlgeschlagen. '
            f'Nicht erneut veröffentlichen. Prüfen mit: python3 scripts/release.py --verify-only {version}'
        ) from error


def check_version_codes(repository, code, releases=None):
    """Check every published stable release, not just GitHub's mutable latest pointer."""
    if releases is None:
        pages = json.loads(run('gh', 'api', '--paginate', '--slurp',
                               f'repos/{repository}/releases?per_page=100', capture=True))
        releases = [item for page in pages for item in page]
    for item in releases:
        if item['draft'] or item.get('prerelease', False):
            continue
        prior = json.loads(run('gh', 'release', 'download', item['tag_name'], '--repo', repository,
                               '--pattern', 'manifest.json', '--output', '-', capture=True))
        if prior.get('packageName') != CHANNEL['packageName']:
            raise ValueError('Bestehender Kanal hat ein unerwartetes Paket.')
        if type(prior.get('latestVersionCode')) is not int or code <= prior['latestVersionCode']:
            raise ValueError('versionCode muss höher als in allen bisherigen Updates sein.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--prepare-only', action='store_true', help='Lokale Release-Dateien erzeugen (Standard)')
    mode.add_argument('--publish', action='store_true', help='Geprüften Quellstand bauen und veröffentlichen')
    mode.add_argument('--verify-only', metavar='VERSION', help='Öffentlichen Release ohne Build und Änderungen prüfen')
    parser.add_argument('--test-on-karoo', action='store_true', help='Release-APK per ADB testen')
    args = parser.parse_args()
    tools = environment()
    if args.verify_only:
        if args.test_on_karoo:
            parser.error('--verify-only kann nicht mit Installation kombiniert werden.')
        verify_public(args.verify_only, tools)
        return
    version = re.search(r'versionName\s*=\s*"([^"]+)"', (ROOT / 'app/build.gradle.kts').read_text())[1]
    head = check_source(version) if args.publish else None
    run('sh', ROOT / 'scripts/build.sh')
    if args.publish and check_source(version) != head:
        raise ValueError('Quellstand hat sich während des Builds geändert.')
    version, code, folder, assets = prepare(ROOT / 'app/build/outputs/apk/release/app-release.apk', tools)
    print(f'Release {version} ({code}) vorbereitet: {folder}', flush=True)
    if args.test_on_karoo:
        run('sh', ROOT / 'scripts/install.sh', assets[0])
        if args.publish and input('Karoo-Test bestanden? Zum Veröffentlichen „ja“ eingeben: ').strip().lower() != 'ja':
            return
    if args.publish:
        publish(version, code, assets)


if __name__ == '__main__':
    try:
        main()
    except PublishedVerificationError as error:
        print(str(error), file=sys.stderr)
        sys.exit(2)
    except (ValueError, OSError, EOFError, subprocess.CalledProcessError) as error:
        print(f'Release nicht abgeschlossen: {error}', file=sys.stderr)
        sys.exit(1)
