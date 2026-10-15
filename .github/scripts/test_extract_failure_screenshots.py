#!/usr/bin/env python3
"""Tests for managed-device screenshot extraction."""

from __future__ import annotations

import base64
from pathlib import Path
import tempfile
import unittest

import extract_failure_screenshots as extractor


class ExtractFailureScreenshotsTest(unittest.TestCase):
    def test_reassembles_chunked_png_beside_the_managed_device_logcat(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            logcat = Path(temporary_directory) / "logcat-com.example.Test-method.txt"
            png = extractor.PNG_SIGNATURE + b"image bytes"
            encoded = base64.b64encode(png).decode("ascii")
            chunks = [encoded[:8], encoded[8:]]
            lines = [
                "10-15 12:00:00.000 I FailureScreenshot: "
                f"SCREENSHOT_BEGIN|123|SomeTest-fails|{len(chunks)}",
                f"10-15 12:00:00.000 I FailureScreenshot: SCREENSHOT_CHUNK|123|1|{chunks[1]}",
                f"10-15 12:00:00.000 I FailureScreenshot: SCREENSHOT_CHUNK|123|0|{chunks[0]}",
                "10-15 12:00:00.000 I FailureScreenshot: SCREENSHOT_END|123",
            ]
            logcat.write_text("\n".join(lines), encoding="utf-8")

            output = extractor.extract_logcat(logcat)

            self.assertEqual(1, len(output))
            self.assertEqual(png, output[0].read_bytes())
            self.assertEqual("SomeTest-fails-123.png", output[0].name)

    def test_does_not_write_an_incomplete_capture(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            logcat = Path(temporary_directory) / "logcat.txt"
            logcat.write_text(
                "I FailureScreenshot: SCREENSHOT_BEGIN|123|SomeTest-fails|2\n"
                "I FailureScreenshot: SCREENSHOT_CHUNK|123|0|cG5n\n"
                "I FailureScreenshot: SCREENSHOT_END|123\n",
                encoding="utf-8",
            )

            self.assertEqual([], extractor.extract_logcat(logcat))
            self.assertFalse((Path(temporary_directory) / "failure-screenshots").exists())


if __name__ == "__main__":
    unittest.main()
