"""Freeze published app identity while repository migration is prepared."""
import json
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ANDROID = '{http://schemas.android.com/apk/res/android}'


class MigrationCompatibilityTests(unittest.TestCase):
    def test_published_package_and_certificate_are_preserved(self):
        channel = json.loads((ROOT / 'releases/channel.json').read_text())
        gradle = (ROOT / 'app/build.gradle.kts').read_text()
        self.assertEqual('io.github.jlojkasek.ketape', channel['packageName'])
        self.assertEqual(channel['packageName'], re.search(r'applicationId\s*=\s*"([^"]+)"', gradle)[1])
        self.assertEqual('1aa19d4c284fe6353076d6b0f3411fc3918ec9d1d20d226b4afe61682595a4ea',
                         channel['certificateSha256'])
        self.assertGreaterEqual(int(re.search(r'versionCode\s*=\s*(\d+)', gradle)[1]), 13)

    def test_existing_field_identity_is_preserved(self):
        info = ET.parse(ROOT / 'app/src/main/res/xml/extension_info.xml').getroot()
        self.assertEqual('ketape', info.get('id'))
        ids = [node.get('typeId') for node in info.findall('DataType')]
        self.assertEqual(len(ids), len(set(ids)))
        self.assertTrue({'power', 'heart-rate', 'speed', 'distance', 'cadence',
                         'palmares-table', 'palmares-auto', 'reserve-balance',
                         'reserve-available-power', 'reserve-insights'} <= set(ids))

    def test_embedded_update_channel_matches_release_configuration(self):
        channel = json.loads((ROOT / 'releases/channel.json').read_text())
        manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml')
        urls = [n.get(ANDROID + 'value') for n in manifest.findall('./application/meta-data')
                if n.get(ANDROID + 'name') == 'io.hammerhead.karooext.MANIFEST_URL']
        self.assertEqual([f"https://github.com/{channel['repository']}/releases/latest/download/manifest.json"], urls)
