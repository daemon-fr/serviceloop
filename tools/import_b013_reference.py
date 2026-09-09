#!/usr/bin/env python3
"""Import the exact owner-supplied ServiceLoop UI/UX Reference v1.0 ZIP.

Usage from repository root:

    python tools/import_b013_reference.py ServiceLoop_UI_UX_Reference_v1_0.zip

The script verifies the original ZIP and every expected extracted file before
replacing docs/ui-reference/v1.0 package payload. It deliberately preserves the
repository-owned README.md intake note already present in that directory.
"""

from __future__ import annotations

import argparse
import hashlib
import shutil
import sys
import tempfile
import zipfile
from pathlib import Path

ZIP_SHA256 = "d1a94a95d75a9d9a36e82384d84696c46ed92fd5e7549a47b2cd7775ea03c03c"
PACKAGE_ROOT = "ServiceLoop_UI_UX_Reference_v1_0"
EXPECTED = {
    "README.txt": "4777836f768ba7310008c2666794b28e26c088b8a913cafd1a0862f71f93917e",
    "ServiceLoop_Source_Manifest_v1_0.json": "20fc672259c1c1bea6e8474c07c44ea8b6aadd61e6795a30cff72cab8fc9c256",
    "ServiceLoop_UI_Audit_v1_0.json": "d033bdc907bbcfe3d83ad75219d8fb3314e10f690807912464685245ce3d2a6e",
    "ServiceLoop_UI_Coverage_v1_0.csv": "1b6f13d40f05977d92b3bb043e623753f1f599065d3897bf052af0c0c147e4e2",
    "ServiceLoop_UI_Tokens_v1_0.json": "7757a15c129b198f043faf40111836395946a54a8c3c1f2a49fe0780c7ef919c",
    "ServiceLoop_UI_UX_Implementation_Reference_v1_0.html": "98120be8cc1d1bc407687616463ebe0ceb33f1b5e2903937177c8ee482ee07b2",
    "ServiceLoop_UI_UX_Implementation_Reference_v1_0.md": "5cfdbd8c520f73eeb4ff66216d9f21a3a0137a800378f70c8de77d907b0dbdbe",
    "references/mockup_editor.png": "3e81229e7c921fea9d475dedcba91e4ff4c5ac2811b2009d51053988b8dcdb20",
    "references/mockup_outbox.png": "993b5c8376eebf052c9df01e97932b1d77da3572f853733f97b2e2a165bc7184",
    "references/mockup_review.png": "34d574e194eec80cec5b73f57120cd2282be34b16620755c498026539dab9a46",
    "references/mockup_selected.png": "cdabb353913d28486ee7979a0a17e77037153c200a92e9781fabbf8005ac5616",
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def fail(message: str) -> "NoReturn":
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(2)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("zip_path", nargs="?", default="ServiceLoop_UI_UX_Reference_v1_0.zip")
    args = parser.parse_args()

    repo = Path.cwd().resolve()
    zip_path = Path(args.zip_path).expanduser().resolve()
    target = repo / "docs" / "ui-reference" / "v1.0"

    if not (repo / ".git").exists():
        fail("run this script from the ServiceLoop repository root")
    if not zip_path.is_file():
        fail(f"ZIP not found: {zip_path}")
    actual_zip_hash = sha256(zip_path)
    if actual_zip_hash != ZIP_SHA256:
        fail(f"ZIP SHA-256 mismatch: expected {ZIP_SHA256}, got {actual_zip_hash}")

    with tempfile.TemporaryDirectory(prefix="serviceloop-ui-ref-") as temp_name:
        temp = Path(temp_name)
        with zipfile.ZipFile(zip_path) as archive:
            members = [name for name in archive.namelist() if not name.endswith("/")]
            expected_members = {f"{PACKAGE_ROOT}/{relative}" for relative in EXPECTED}
            if set(members) != expected_members:
                missing = sorted(expected_members - set(members))
                extra = sorted(set(members) - expected_members)
                fail(f"ZIP member mismatch; missing={missing}, extra={extra}")
            archive.extractall(temp)

        source = temp / PACKAGE_ROOT
        for relative, expected_hash in EXPECTED.items():
            path = source / relative
            actual = sha256(path)
            if actual != expected_hash:
                fail(f"SHA-256 mismatch for {relative}: expected {expected_hash}, got {actual}")

        target.mkdir(parents=True, exist_ok=True)
        keep = target / "README.md"
        keep_bytes = keep.read_bytes() if keep.is_file() else None

        for relative in EXPECTED:
            destination = target / relative
            if destination.exists():
                if destination.is_dir():
                    shutil.rmtree(destination)
                else:
                    destination.unlink()

        for relative in EXPECTED:
            source_path = source / relative
            destination = target / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source_path, destination)

        if keep_bytes is not None:
            keep.write_bytes(keep_bytes)

    print("ServiceLoop UI/UX Reference v1.0 imported and verified.")
    print(f"Source ZIP SHA-256: {ZIP_SHA256}")
    print(f"Target: {target}")
    print("Next: inspect git status/diff, then commit the exact extracted package on codex/b013-ui-overhaul.")


if __name__ == "__main__":
    main()
