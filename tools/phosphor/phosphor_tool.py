#!/usr/bin/env python3
"""Verify the vendored Phosphor tree and generate ServiceLoop VectorDrawables."""

from __future__ import annotations

import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "source" / "SVGs"
MANIFEST = ROOT / "serviceloop-icons.json"
OUTPUT = ROOT.parent.parent / "app" / "src" / "main" / "res" / "drawable"
API = ROOT.parent.parent / "app" / "src" / "main" / "java" / "com" / "v16studio" / "serviceloop" / "ui" / "icons" / "ServiceLoopIcons.kt"
STYLES = ("bold", "duotone", "fill", "light", "regular", "thin")


def tree_identity() -> tuple[int, str]:
    files = sorted(SOURCE.glob("*/*.svg"), key=lambda p: p.relative_to(SOURCE).as_posix())
    digest = hashlib.sha256()
    for path in files:
        rel = path.relative_to(SOURCE).as_posix().encode("utf-8")
        data = path.read_bytes()
        digest.update(len(rel).to_bytes(4, "big"))
        digest.update(rel)
        digest.update(len(data).to_bytes(8, "big"))
        digest.update(data)
    return len(files), digest.hexdigest()


def verify() -> None:
    actual = tuple(sorted(p.name.lower() for p in SOURCE.iterdir() if p.is_dir()))
    if actual != STYLES:
        raise SystemExit(f"style directories differ: expected {STYLES}, found {actual}")
    counts = {style: len(list((SOURCE / style).glob("*.svg"))) for style in STYLES}
    if len(set(counts.values())) != 1 or next(iter(counts.values())) == 0:
        raise SystemExit(f"inconsistent style counts: {counts}")
    count, digest = tree_identity()
    print(f"styles: {', '.join(STYLES)}")
    print(f"SVG files: {count} ({counts})")
    print(f"tree SHA-256: {digest}")


def drawable_name(alias: str) -> str:
    snake = re.sub(r"(?<!^)(?=[A-Z])", "_", alias).lower()
    return f"ic_sl_{snake}"


def svg_paths(path: Path) -> list[dict[str, str]]:
    root = ET.fromstring(path.read_bytes())
    if root.attrib.get("viewBox") != "0 0 256 256":
        raise ValueError(f"{path}: unsupported viewBox {root.attrib.get('viewBox')!r}")
    paths: list[dict[str, str]] = []
    for node in root.iter():
        tag = node.tag.rsplit("}", 1)[-1]
        if tag in {"svg", "g"}:
            continue
        if tag == "rect" and node.attrib.get("fill") == "none":
            continue
        if tag == "rect":
            allowed = {"x", "y", "width", "height", "rx"}
            if set(node.attrib) - allowed:
                raise ValueError(f"{path}: unsupported filled <rect> attributes")
            x = node.attrib.get("x", "0")
            y = node.attrib.get("y", "0")
            width = node.attrib.get("width")
            height = node.attrib.get("height")
            radius = node.attrib.get("rx", "0")
            if width is None or height is None:
                raise ValueError(f"{path}: filled <rect> needs width and height")
            # VectorDrawable supports SVG arc commands; preserve the source rectangle rather
            # than substituting a hand-authored product vector for a Phosphor asset.
            paths.append({"d": f"M{x},{y} H{float(x) + float(width):g} V{float(y) + float(height):g} H{x} Z" if radius == "0" else f"M{float(x) + float(radius):g},{y} H{float(x) + float(width) - float(radius):g} A{radius},{radius} 0,0,1 {float(x) + float(width):g},{float(y) + float(radius):g} V{float(y) + float(height) - float(radius):g} A{radius},{radius} 0,0,1 {float(x) + float(width) - float(radius):g},{float(y) + float(height):g} H{float(x) + float(radius):g} A{radius},{radius} 0,0,1 {x},{float(y) + float(height) - float(radius):g} V{float(y) + float(radius):g} A{radius},{radius} 0,0,1 {float(x) + float(radius):g},{y} Z"})
            continue
        if tag == "line" and node.attrib.get("x1") == node.attrib.get("x2") and node.attrib.get("y1") == node.attrib.get("y2"):
            # Some upstream Fill SVGs retain an explicitly zero-length, non-rendering line.
            continue
        if tag in {"line", "polyline"}:
            required = {"fill", "stroke", "stroke-linecap", "stroke-linejoin", "stroke-width"}
            if node.attrib.get("fill") != "none" or node.attrib.get("stroke") != "currentColor" or set(node.attrib) - (required | {"x1", "y1", "x2", "y2", "points"}):
                raise ValueError(f"{path}: unsupported stroked <{tag}> attributes")
            if node.attrib.get("stroke-linecap") != "round" or node.attrib.get("stroke-linejoin") != "round":
                raise ValueError(f"{path}: unsupported stroke caps/joins")
            if tag == "line":
                data = f"M{node.attrib['x1']},{node.attrib['y1']} L{node.attrib['x2']},{node.attrib['y2']}"
            else:
                coordinates = re.findall(r"-?(?:\d+(?:\.\d*)?|\.\d+)", node.attrib.get("points", ""))
                if len(coordinates) < 4 or len(coordinates) % 2:
                    raise ValueError(f"{path}: unsupported polyline points")
                points = [f"{coordinates[index]},{coordinates[index + 1]}" for index in range(0, len(coordinates), 2)]
                data = "M" + " L".join(points)
            paths.append({"d": data, "strokeWidth": node.attrib["stroke-width"]})
            continue
        if tag != "path" or not node.attrib.get("d"):
            raise ValueError(f"{path}: unsupported visible <{tag}> element")
        unsupported = set(node.attrib) - {"d", "fill", "fill-rule", "clip-rule", "opacity"}
        if unsupported:
            raise ValueError(f"{path}: unsupported path attributes {sorted(unsupported)}")
        if node.attrib.get("fill") == "none":
            continue
        paths.append({"d": node.attrib["d"]})
    if not paths:
        raise ValueError(f"{path}: no renderable paths")
    return paths


def vector_xml(paths: list[dict[str, str]]) -> str:
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="24dp"',
        '    android:height="24dp"',
        '    android:viewportWidth="256"',
        '    android:viewportHeight="256">',
    ]
    for path in paths:
        escaped = path["d"].replace("&", "&amp;").replace('"', "&quot;")
        if "strokeWidth" in path:
            lines.append(f'    <path android:fillColor="#00000000" android:strokeColor="#FF000000" android:strokeWidth="{path["strokeWidth"]}" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="{escaped}" />')
        else:
            lines.append(f'    <path android:fillColor="#FF000000" android:pathData="{escaped}" />')
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


def load_manifest(path: Path = MANIFEST) -> dict[str, tuple[str, str]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if payload.get("style") != "fill":
        raise ValueError("manifest style must be exactly 'fill'")
    icons = payload.get("icons")
    if not isinstance(icons, dict) or not icons:
        raise ValueError("manifest icons must be a non-empty object")
    exceptions = payload.get("style_exceptions", {})
    if exceptions != {"Back": "bold"}:
        raise ValueError("the only permitted style exception is Back: bold")
    resolved = {}
    for alias, source_name in icons.items():
        style = exceptions.get(alias, "fill")
        suffix = "-fill" if style == "fill" else f"-{style}"
        source = SOURCE / style / f"{source_name}{suffix}.svg"
        if not source.is_file():
            raise ValueError(f"missing {style} icon for {alias}: {source}")
        resolved[alias] = (source_name, style)
    return resolved


def generate() -> None:
    icons = load_manifest()
    expected = set()
    for alias, (source_name, style) in sorted(icons.items()):
        suffix = "-fill" if style == "fill" else f"-{style}"
        source = SOURCE / style / f"{source_name}{suffix}.svg"
        name = drawable_name(alias)
        expected.add(f"{name}.xml")
        (OUTPUT / f"{name}.xml").write_text(vector_xml(svg_paths(source)), encoding="utf-8", newline="\n")
    for old in OUTPUT.glob("ic_sl_*.xml"):
        if old.name not in expected:
            old.unlink()
    entries = [f"    @DrawableRes val {alias} = R.drawable.{drawable_name(alias)}" for alias in sorted(icons)]
    api = """// Generated by tools/phosphor/phosphor_tool.py. Do not edit by hand.
package com.v16studio.serviceloop.ui.icons

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.v16studio.serviceloop.R

object ServiceLoopIcons {
""" + "\n".join(entries) + """
}

@Composable
fun serviceLoopIconPainter(@DrawableRes icon: Int): Painter = painterResource(icon)

@Composable
fun ServiceLoopIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) = Icon(serviceLoopIconPainter(icon), contentDescription, modifier, tint)
"""
    API.parent.mkdir(parents=True, exist_ok=True)
    API.write_text(api, encoding="utf-8", newline="\n")
    print(f"generated {len(icons)} icons (Fill default; Back Bold exception) and {API.relative_to(ROOT.parent.parent)}")


if __name__ == "__main__":
    command = sys.argv[1] if len(sys.argv) == 2 else ""
    if command == "verify":
        verify()
    elif command == "generate":
        verify()
        generate()
    else:
        raise SystemExit("usage: phosphor_tool.py verify|generate")
