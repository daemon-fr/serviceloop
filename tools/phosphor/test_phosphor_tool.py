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

    def test_every_manifest_entry_resolves_to_declared_strict_style(self):
        manifest = json.loads(phosphor_tool.MANIFEST.read_text(encoding="utf-8"))
        self.assertEqual(manifest["style"], "fill")
        self.assertEqual(manifest["style_exceptions"], {"Back": "bold"})
        for source_name in manifest["icons"].values():
            alias = next(alias for alias, value in manifest["icons"].items() if value == source_name)
            style = manifest["style_exceptions"].get(alias, "fill")
            source = phosphor_tool.SOURCE / style / f"{source_name}-{style}.svg"
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

    def test_aliases_are_unique_and_only_the_explicit_check_fat_semantics_share_a_source(self):
        manifest = json.loads(phosphor_tool.MANIFEST.read_text(encoding="utf-8"))
        aliases = list(manifest["icons"])
        self.assertEqual(len(aliases), len(set(aliases)))
        by_source = {}
        for alias, source in manifest["icons"].items():
            by_source.setdefault(source, set()).add(alias)
        duplicates = {source: aliases for source, aliases in by_source.items() if len(aliases) > 1}
        self.assertEqual({"check-fat": {"LocalSaved", "SelectionCheck"}}, duplicates)

    def test_wrong_style_is_rejected_by_generate(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "manifest.json"
            path.write_text(json.dumps({"style": "regular", "style_exceptions": {"Back": "bold"}, "icons": {"Home": "house"}}))
            with self.assertRaisesRegex(ValueError, "exactly 'fill'"):
                phosphor_tool.load_manifest(path)

    def test_unknown_fill_icon_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "manifest.json"
            path.write_text(json.dumps({"style": "fill", "style_exceptions": {"Back": "bold"}, "icons": {"Unknown": "not-a-real-icon"}}))
            with self.assertRaisesRegex(ValueError, "missing fill icon"):
                phosphor_tool.load_manifest(path)

    def test_only_named_back_bold_exception_is_allowed(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "manifest.json"
            path.write_text(json.dumps({"style": "fill", "style_exceptions": {"Home": "bold"}, "icons": {"Home": "house"}}))
            with self.assertRaisesRegex(ValueError, "only permitted style exception"):
                phosphor_tool.load_manifest(path)

    def test_bold_back_preserves_upstream_stroke_geometry(self):
        paths = phosphor_tool.svg_paths(phosphor_tool.SOURCE / "bold" / "arrow-left-bold.svg")
        self.assertEqual(["24", "24"], [path["strokeWidth"] for path in paths])
        xml = phosphor_tool.vector_xml(paths)
        self.assertIn('android:strokeLineCap="round"', xml)
        self.assertIn('android:strokeLineJoin="round"', xml)
        self.assertIn('android:viewportWidth="256"', xml)

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
