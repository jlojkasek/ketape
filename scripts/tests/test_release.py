import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('release', Path(__file__).parents[1] / 'release.py')
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseTests(unittest.TestCase):
    def test_manifest_uses_versioned_apk_and_numeric_code(self):
        manifest = release.manifest('0.1.2', 3, 'Notes')
        self.assertEqual(3, manifest['latestVersionCode'])
        self.assertEqual('io.github.jlojkasek.ketape', manifest['packageName'])
        self.assertTrue(manifest['latestApkUrl'].endswith('/v0.1.2/ketape-0.1.2.apk'))

    def test_wrong_signer_blocks_publication(self):
        with patch.object(release, 'run', return_value='Signer #1 certificate SHA-256 digest: deadbeef'):
            with self.assertRaisesRegex(ValueError, 'Signatur'):
                release.inspect_apk(Path('wrong.apk'), Path('/tools'))

    def test_wrong_package_blocks_publication(self):
        with patch.object(release, 'run', side_effect=[
            'Signer #1 certificate SHA-256 digest: ' + release.CHANNEL['certificateSha256'],
            "package: name='wrong.app' versionCode='3' versionName='0.1.2'"
        ]):
            with self.assertRaisesRegex(ValueError, 'Paket'):
                release.inspect_apk(Path('wrong.apk'), Path('/tools'))

    def test_private_distribution_is_not_exposed(self):
        with patch.object(release, 'run', side_effect=[None, '[[{"full_name":"' + release.CHANNEL['repository'] + '","private":true}]]']) as run:
            with self.assertRaisesRegex(ValueError, 'privat'):
                release.publish('0.1.2', 3, [], True)
            self.assertEqual(2, run.call_count)

    def test_published_release_cannot_be_overwritten(self):
        with patch.object(release, 'run', side_effect=[None, '[[{"full_name":"' + release.CHANNEL['repository'] + '","private":false}]]', '[[{"tag_name":"v0.1.2","draft":false}]]']) as run:
            with self.assertRaisesRegex(ValueError, 'bereits veröffentlicht'):
                release.publish('0.1.2', 3, [], False)
            self.assertEqual(3, run.call_count)
