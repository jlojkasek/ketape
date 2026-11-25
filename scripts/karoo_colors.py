#!/usr/bin/env python3
"""Inventory installed Karoo resources; install only explicitly verified color mappings."""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID_NS = 'http://schemas.android.com/apk/res/android'

def run(*args):
    return subprocess.run(args, check=True, text=True, capture_output=True).stdout

def tool(name, sdk):
    found = shutil.which(name)
    if found:
        return found
    choices = ([sdk / 'platform-tools' / name] if name == 'adb' else
               sorted((sdk / 'build-tools').glob('*/aapt2'), reverse=True))
    return next((str(p) for p in choices if p.is_file()), None)

def apply_mapping(source):
    data = json.loads(source.read_text())
    packages = set()
    for key, count in [('power', 7), ('heart', 5)]:
        entries = data.get(key)
        if not isinstance(entries, list) or len(entries) != count:
            raise ValueError(f'{key}: genau {count} verifizierte Einträge in Zonenreihenfolge erforderlich')
        for entry in entries:
            if not isinstance(entry, dict):
                raise ValueError('Eintrag muss package und resource enthalten')
            pkg, res = entry.get('package', ''), entry.get('resource', '')
            if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+', pkg):
                raise ValueError('Ungültiger Paketname')
            if not re.fullmatch(r'[a-z][a-z0-9_]*', res):
                raise ValueError('Ungültiger Farbressourcenname')
            packages.add(pkg)
    manifest = ROOT / 'app/src/main/AndroidManifest.xml'
    tree = ET.parse(manifest)
    root = tree.getroot()
    queries = root.find('queries')
    if queries is None:
        queries = ET.Element('queries')
        root.insert(0, queries)
    existing = {e.get(f'{{{ANDROID_NS}}}name') for e in queries.findall('package')}
    for pkg in sorted(packages - existing):
        ET.SubElement(queries, 'package', {f'{{{ANDROID_NS}}}name': pkg})
    asset = ROOT / 'app/src/main/assets/karoo-zone-resources.json'
    asset.parent.mkdir(parents=True, exist_ok=True)
    ET.register_namespace('android', ANDROID_NS)
    ET.indent(tree)
    # Validate every input before either project file is changed.
    manifest.write_text(ET.tostring(root, encoding='unicode') + '\n')
    asset.write_text(json.dumps(data, indent=2) + '\n')
    print('Zuordnung und Paketsichtbarkeit gespeichert. APK neu bauen und auf dem Karoo prüfen.')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', type=Path, help='Vom Gerät bestätigte Zuordnung übernehmen')
    parser.add_argument('--serial', help='ADB-Gerät auswählen')
    parser.add_argument('--package', action='append', help='Nur dieses Paket prüfen (mehrfach möglich)')
    parser.add_argument('--out', type=Path, default=ROOT / 'diagnostics/karoo-colors')
    args = parser.parse_args()
    if args.apply:
        apply_mapping(args.apply)
        return
    sdk = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Library/Android/sdk')))
    adb, aapt = tool('adb', sdk), tool('aapt2', sdk)
    if not adb or not aapt:
        raise ValueError('ADB und aapt2 fehlen. Android SDK Platform Tools und Build Tools installieren.')
    base = [adb] + (['-s', args.serial] if args.serial else [])
    if run(*base, 'get-state').strip() != 'device':
        raise ValueError('Kein freigegebener Karoo verbunden')
    packages = args.package or [line.removeprefix('package:').strip() for line in
        run(*base, 'shell', 'pm', 'list', 'packages').splitlines() if 'hammerhead' in line.lower()]
    if not packages:
        raise ValueError('Keine Hammerhead-Pakete gefunden. Mit --package explizit auswählen.')
    args.out.mkdir(parents=True, exist_ok=True)
    report = []
    for pkg in packages:
        if not re.fullmatch(r'[A-Za-z0-9_.]+', pkg):
            raise ValueError('Ungültiger Paketname')
        paths = [line.removeprefix('package:').strip() for line in
                 run(*base, 'shell', 'pm', 'path', pkg).splitlines() if line.startswith('package:')]
        for index, remote in enumerate(paths):
            apk = args.out / f'{pkg}-{index}.apk'
            run(*base, 'pull', remote, str(apk))
            dump = run(aapt, 'dump', 'resources', str(apk))
            (args.out / f'{pkg}-{index}-resources.txt').write_text(dump)
            color_names = sorted(set(re.findall(r'\bcolor/([A-Za-z0-9_]+)', dump)))
            likely = [name for name in color_names if re.search(r'zone|heart|power|training|effort', name, re.I)]
            report.append({'package': pkg, 'apk': apk.name, 'color_resources': color_names,
                           'zone_name_candidates': likely})
    (args.out / 'inventory.json').write_text(json.dumps(report, indent=2) + '\n')
    print(f'Ressourceninventar gespeichert: {args.out / "inventory.json"}')
    print('Kandidaten sind noch keine bestätigte Zonenpalette. Farbzuordnung im Ressourcen-Dump bzw. in der System-App prüfen.')

if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        print(f'Fehler: {error}', file=sys.stderr)
        sys.exit(1)
