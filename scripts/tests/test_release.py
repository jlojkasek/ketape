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


class SourceGateTests(unittest.TestCase):
    def test_dirty_source_never_reaches_network(self):
        with patch.object(release, 'run', return_value=' M app.kt') as run:
            with self.assertRaisesRegex(ValueError, 'sauber'):
                release.check_source('1.1.1')
            self.assertEqual(1, run.call_count)

    def test_local_only_tag_is_not_publishable(self):
        with patch.object(release, 'run', side_effect=['', 'abc', 'v1.1.1', '']):
            with self.assertRaisesRegex(ValueError, 'origin'):
                release.check_source('1.1.1')

    def test_failed_ci_blocks_even_if_an_older_run_passed(self):
        import json
        runs = [dict(headSha='abc', status='completed', conclusion=c) for c in ['failure', 'success']]
        with patch.object(release, 'run', side_effect=['', 'abc', 'v1.1.1', 'abc\trefs/tags/v1.1.1', json.dumps(runs)]):
            with self.assertRaisesRegex(ValueError, 'CI'):
                release.check_source('1.1.1')

    def test_annotated_tag_and_green_ci_are_accepted(self):
        import json
        runs = [dict(headSha='abc', status='completed', conclusion='success')]
        with patch.object(release, 'run', side_effect=['', 'abc', 'v1.1.1',
                'tagobject\trefs/tags/v1.1.1\nabc\trefs/tags/v1.1.1^{}', json.dumps(runs)]):
            self.assertEqual('abc', release.check_source('1.1.1'))


class PublicVerificationTests(unittest.TestCase):
    def fixture(self):
        import json, hashlib
        document = release.manifest('1.1.1', 13, 'Test')
        manifest = json.dumps(document).encode()
        apk = b'test apk'
        sums = (hashlib.sha256(manifest).hexdigest() + '  manifest.json\n' +
                hashlib.sha256(apk).hexdigest() + '  ketape-1.1.1.apk\n').encode()
        return {'manifest.json': manifest, 'ketape-1.1.1.apk': apk, 'SHA256SUMS': sums}

    def test_verification_is_read_only_and_checks_signature(self):
        payloads = self.fixture()
        with patch.object(release, 'public_bytes', side_effect=lambda url: payloads[url.rsplit('/', 1)[1]]), \
                patch.object(release, 'inspect_apk', return_value=('1.1.1', 13)) as inspect, \
                patch.object(release, 'run') as run:
            release.verify_public('1.1.1', Path('/tools'))
            inspect.assert_called_once()
            run.assert_not_called()

    def test_corrupt_public_apk_is_rejected_before_signature_check(self):
        payloads = self.fixture()
        payloads['ketape-1.1.1.apk'] = b'corrupt'
        with patch.object(release, 'public_bytes', side_effect=lambda url: payloads[url.rsplit('/', 1)[1]]), \
                patch.object(release, 'inspect_apk') as inspect:
            with self.assertRaisesRegex(ValueError, 'Prüfsumme'):
                release.verify_public('1.1.1', Path('/tools'))
            inspect.assert_not_called()

    def test_unexpected_download_host_is_rejected(self):
        import json
        payloads = self.fixture()
        document = json.loads(payloads['manifest.json'])
        document['latestApkUrl'] = 'https://other.example/app.apk'
        with patch.object(release, 'public_bytes', return_value=json.dumps(document).encode()):
            with self.assertRaisesRegex(ValueError, 'erwartete APK'):
                release.verify_public('1.1.1', Path('/tools'))

    def test_default_command_prepares_without_publication(self):
        with patch('sys.argv', ['release.py']), patch.object(release, 'environment', return_value=Path('/tools')), \
                patch.object(release, 'run'), patch.object(release, 'prepare', return_value=('1.1.1', 13, Path('/tmp/release'), [])), \
                patch.object(release, 'publish') as publish, patch.object(release, 'check_source') as gate:
            release.main()
            publish.assert_not_called()
            gate.assert_not_called()


class PublicationReadbackTests(unittest.TestCase):
    def publish_with_draft(self, wrong_download=False, failed_public_check=False):
        import json, tempfile
        with tempfile.TemporaryDirectory() as folder:
            asset = Path(folder) / 'ketape-1.1.1.apk'
            asset.write_bytes(b'prepared apk')
            calls = []

            def run(*args, **kwargs):
                calls.append(args)
                if args[:3] == ('gh', 'api', '--paginate'):
                    if 'user/repos' in args[-1]:
                        return json.dumps([[dict(full_name=release.CHANNEL['repository'], private=False)]])
                    return '[[]]'
                if args[:3] == ('gh', 'release', 'download'):
                    Path(args[-1]).write_bytes(b'wrong' if wrong_download else asset.read_bytes())
                return ''

            with patch.object(release, 'run', side_effect=run), \
                    patch.object(release, 'environment', return_value=Path('/tools')), \
                    patch.object(release, 'verify_public', side_effect=OSError('network') if failed_public_check else None):
                if wrong_download:
                    with self.assertRaisesRegex(ValueError, 'Entwurfsdatei'):
                        release.publish('1.1.1', 13, [asset], False)
                    self.assertFalse(any(c[:3] == ('gh', 'release', 'edit') for c in calls))
                elif failed_public_check:
                    with self.assertRaisesRegex(release.PublishedVerificationError, 'IST veröffentlicht'):
                        release.publish('1.1.1', 13, [asset], False)
                    self.assertEqual(1, sum(c[:3] == ('gh', 'release', 'edit') for c in calls))

    def test_corrupt_draft_never_becomes_public(self):
        self.publish_with_draft(wrong_download=True)

    def test_failed_post_publication_verification_reports_actual_state(self):
        self.publish_with_draft(failed_public_check=True)
