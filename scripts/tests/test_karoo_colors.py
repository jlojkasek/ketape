import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('karoo_colors', Path(__file__).parents[1] / 'karoo_colors.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class MappingTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.original_root = module.ROOT
        module.ROOT = self.root
        self.addCleanup(setattr, module, 'ROOT', self.original_root)
        self.manifest = self.root / 'app/src/main/AndroidManifest.xml'
        self.manifest.parent.mkdir(parents=True)
        self.manifest.write_text('<manifest><application/></manifest>')
        self.mapping = self.root / 'mapping.json'

    def data(self):
        return {key: [{'package': 'io.hammerhead.test', 'resource': f'{key}_{i}'} for i in range(count)]
                for key, count in [('power', 7), ('heart', 5)]}

    def test_installs_mapping_and_visibility_without_duplicate_queries(self):
        self.mapping.write_text(json.dumps(self.data()))
        module.apply_mapping(self.mapping)
        module.apply_mapping(self.mapping)
        root = module.ET.parse(self.manifest).getroot()
        self.assertEqual(len(root.findall('queries/package')), 1)
        asset = self.root / 'app/src/main/assets/karoo-zone-resources.json'
        self.assertEqual(json.loads(asset.read_text()), self.data())
        self.assertIsNotNone(root.find('application'))

    def test_incomplete_mapping_does_not_modify_project(self):
        data = self.data(); data['heart'].pop()
        self.mapping.write_text(json.dumps(data))
        before = self.manifest.read_text()
        with self.assertRaises(ValueError): module.apply_mapping(self.mapping)
        self.assertEqual(before, self.manifest.read_text())
        self.assertFalse((self.root / 'app/src/main/assets').exists())

    def test_rejects_resource_paths_and_invalid_packages(self):
        for key, value in [('resource', '../not_a_color'), ('package', 'pkg;other')]:
            data = self.data(); data['power'][0][key] = value
            self.mapping.write_text(json.dumps(data))
            with self.assertRaises(ValueError): module.apply_mapping(self.mapping)

if __name__ == '__main__': unittest.main()
