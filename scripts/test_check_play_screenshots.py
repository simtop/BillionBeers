#!/usr/bin/env python3
"""Exercise image acceptance/rejection using real PNG files and sips decoding."""
import contextlib
import importlib.util
import io
import struct
import tempfile
import unittest
import zlib
from pathlib import Path

spec = importlib.util.spec_from_file_location('validator', Path(__file__).with_name('check-play-screenshots.py'))
validator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validator)


def png(path, width=320, height=320, alpha=False):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    channels = 4 if alpha else 3
    data = b'\x00' + bytes([128] * channels) * width
    header = struct.pack('>IIBBBBB', width, height, 8, 6 if alpha else 2, 0, 0, 0)
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', header) + chunk(b'IDAT', zlib.compress(data * height)) + chunk(b'IEND', b''))


class ScreenshotValidationTest(unittest.TestCase):
    def run_case(self, widths=(320, 320), alpha=False, corrupt=False):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            folder = root / 'phoneScreenshots'
            folder.mkdir()
            for index, width in enumerate(widths):
                png(folder / f'{index}.png', width=width, alpha=alpha)
            if corrupt:
                (folder / '0.png').write_text('not an image')
            with contextlib.redirect_stdout(io.StringIO()):
                return validator.check(root)

    def test_general_upload_ratio_is_not_restricted_to_promotion_ratio(self):
        self.assertEqual(self.run_case(), 0)

    def test_native_unfolded_ratio_is_accepted(self):
        self.assertEqual(self.run_case(widths=(384, 384)), 0)

    def test_excessive_aspect_ratio_fails(self):
        self.assertEqual(self.run_case(widths=(960, 320)), 1)

    def test_alpha_fails(self):
        self.assertEqual(self.run_case(alpha=True), 1)

    def test_corrupt_image_fails(self):
        self.assertEqual(self.run_case(corrupt=True), 1)

    def test_nine_images_fail(self):
        self.assertEqual(self.run_case(widths=(320,) * 9), 1)

    def test_stale_phone_frames_fail(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            raw = root / 'phoneScreenshots'
            framed = root / 'phoneScreenshotsFramed'
            raw.mkdir()
            framed.mkdir()
            for stem in ('catalog', 'search'):
                png(raw / f'{stem}.png')
                png(framed / f'{stem}.png')
            (framed / 'search.png').rename(framed / 'old-search.png')
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(validator.check(root), 1)

    def test_listing_minimum_is_enforced(self):
        self.assertEqual(self.run_case(widths=(320,)), 1)


if __name__ == '__main__':
    unittest.main()
