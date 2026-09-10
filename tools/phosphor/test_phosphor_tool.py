import tempfile
import unittest
from pathlib import Path

import phosphor_tool


class PhosphorToolTest(unittest.TestCase):
    def test_vendored_tree_has_all_six_equal_families(self):
        counts = {
            style: len(list((phosphor_tool.SOURCE / style).glob("*.svg")))
            for style in phosphor_tool.STYLES
        }
        self.assertEqual(set(counts.values()), {1512})
        self.assertEqual(phosphor_tool.tree_identity()[0], 9072)

    def test_every_manifest_entry_resolves_to_fill(self):
        import json

        manifest = json.loads(phosphor_tool.MANIFEST.read_text(encoding="utf-8"))
        self.assertEqual(manifest["style"], "fill")
        for source_name in manifest["icons"].values():
            source = phosphor_tool.SOURCE / "fill" / f"{source_name}-fill.svg"
            self.assertTrue(source.is_file(), source)
            self.assertTrue(phosphor_tool.svg_paths(source), source)

    def test_unsupported_visible_svg_fails_explicitly(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "unsupported.svg"
            source.write_text(
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256">'
                '<circle cx="128" cy="128" r="10"/></svg>',
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "unsupported visible"):
                phosphor_tool.svg_paths(source)


if __name__ == "__main__":
    unittest.main()
