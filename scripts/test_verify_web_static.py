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
    def write_distribution(
        self,
        html: str,
        javascript: str = 'console.log(\'fixture\'); r.p+"app.wasm";',
        wasm_names: tuple[str, ...] = ("app.wasm",),
    ) -> Path:
        directory = Path(tempfile.mkdtemp())
        (directory / "index.html").write_text(html, encoding="utf-8")
        (directory / "web-app.js").write_text(javascript, encoding="utf-8")
        for wasm_name in wasm_names:
            (directory / wasm_name).write_bytes(b"wasm")
        return directory

    def test_relative_assets_and_wasm_are_accepted(self):
        directory = self.write_distribution(
            '<!doctype html><script src="web-app.js"></script>'
        )
        checked = verifier.verify_distribution(directory)
        self.assertIn("web-app.js", checked)
        self.assertIn("app.wasm", checked)

    def test_all_javascript_referenced_wasm_assets_are_required(self):
        directory = self.write_distribution(
            '<!doctype html><script src="web-app.js"></script>',
            javascript='r.p+"app.wasm"; r.p+"runtime.wasm";',
            wasm_names=("app.wasm", "runtime.wasm"),
        )
        self.assertIn("runtime.wasm", verifier.verify_distribution(directory))

    def test_missing_referenced_wasm_is_rejected_even_when_unrelated_wasm_exists(self):
        directory = self.write_distribution(
            '<!doctype html><script src="web-app.js"></script>',
            javascript='r.p+"required.wasm";',
            wasm_names=("unrelated.wasm",),
        )
        with self.assertRaisesRegex(verifier.VerificationError, "required.wasm"):
            verifier.verify_distribution(directory)

    def test_unrecognized_wasm_reference_is_rejected(self):
        directory = self.write_distribution(
            '<!doctype html><script src="web-app.js"></script>',
            javascript="console.log('no wasm reference');",
        )
        with self.assertRaisesRegex(verifier.VerificationError, "recognizable"):
            verifier.verify_distribution(directory)

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
