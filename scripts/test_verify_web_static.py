#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("verify_web_static.py")
spec = importlib.util.spec_from_file_location("verify_web_static", MODULE_PATH)
verifier = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(verifier)


class VerifyWebStaticTest(unittest.TestCase):
    def write_distribution(self, html: str) -> Path:
        directory = Path(tempfile.mkdtemp())
        (directory / "index.html").write_text(html, encoding="utf-8")
        (directory / "web-app.js").write_text("console.log('fixture');", encoding="utf-8")
        (directory / "app.wasm").write_bytes(b"wasm")
        return directory

    def test_relative_assets_and_wasm_are_accepted(self):
        directory = self.write_distribution(
            '<!doctype html><script src="web-app.js"></script>'
        )
        checked = verifier.verify_distribution(directory)
        self.assertIn("web-app.js", checked)
        self.assertIn("app.wasm", checked)

    def test_missing_asset_is_rejected(self):
        directory = self.write_distribution(
            '<!doctype html><script src="missing.js"></script>'
        )
        with self.assertRaises(verifier.VerificationError):
            verifier.verify_distribution(directory)

    def test_absolute_asset_is_rejected(self):
        directory = self.write_distribution(
            '<!doctype html><script src="https://example.com/app.js"></script>'
        )
        with self.assertRaises(verifier.VerificationError):
            verifier.verify_distribution(directory)

    def test_root_relative_asset_is_rejected(self):
        directory = self.write_distribution(
            '<!doctype html><script src="/app.js"></script>'
        )
        with self.assertRaises(verifier.VerificationError):
            verifier.verify_distribution(directory)

    def test_missing_wasm_is_rejected(self):
        directory = self.write_distribution('<!doctype html><script src="web-app.js"></script>')
        (directory / "app.wasm").unlink()
        with self.assertRaises(verifier.VerificationError):
            verifier.verify_distribution(directory)


if __name__ == "__main__":
    unittest.main()
