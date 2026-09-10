import tempfile
import unittest
import json
import hashlib
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

    def test_aliases_and_selected_fill_sources_are_unique(self):
        manifest = json.loads(phosphor_tool.MANIFEST.read_text(encoding="utf-8"))
        aliases = list(manifest["icons"])
        self.assertEqual(len(aliases), len(set(aliases)))
        duplicates = {
            source for source in manifest["icons"].values()
            if list(manifest["icons"].values()).count(source) > 1
        }
        self.assertEqual(set(), duplicates)

    def test_wrong_style_is_rejected_by_generate(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "manifest.json"
            path.write_text(json.dumps({"style": "regular", "icons": {"Home": "house"}}))
            with self.assertRaisesRegex(ValueError, "exactly 'fill'"):
                phosphor_tool.load_manifest(path)

    def test_unknown_fill_icon_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "manifest.json"
            path.write_text(json.dumps({"style": "fill", "icons": {"Unknown": "not-a-real-icon"}}))
            with self.assertRaisesRegex(ValueError, "missing Fill icon"):
                phosphor_tool.load_manifest(path)

    def test_second_generation_is_byte_identical(self):
        phosphor_tool.generate()
        paths = sorted(phosphor_tool.OUTPUT.glob("ic_sl_*.xml")) + [phosphor_tool.API]
        first = hashlib.sha256(b"".join(path.read_bytes() for path in paths)).digest()
        phosphor_tool.generate()
        second = hashlib.sha256(b"".join(path.read_bytes() for path in paths)).digest()
        self.assertEqual(first, second)

    def test_root_navigation_has_no_unicode_icon_standins(self):
        source = (phosphor_tool.ROOT.parent.parent / "app" / "src" / "main" / "java" / "com" / "v16studio" / "serviceloop" / "ui" / "ServiceLoopApp.kt").read_text(encoding="utf-8")
        for standin in ("⌂", "✓", "◎"):
            self.assertNotIn(standin, source)
        for alias in ("ServiceLoopIcons.Home", "ServiceLoopIcons.Work", "ServiceLoopIcons.Customers"):
            self.assertIn(alias, source)


if __name__ == "__main__":
    unittest.main()
