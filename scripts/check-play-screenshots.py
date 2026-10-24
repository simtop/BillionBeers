#!/usr/bin/env python3
"""Validate raw and framed sets; separate upload requirements from recommendations."""
import subprocess
import sys
from pathlib import Path

# https://support.google.com/googleplay/android-developer/answer/9866151?hl=en
# Verified 2026-10-24. Use the conservative general 3840px maximum for all sets;
# Google's separate large-screen guidance also allows up to 7680px.
MIN_SIDE, MAX_SIDE, MAX_BYTES = 320, 3840, 8 * 1024 * 1024
MAX_COUNT, RECOMMENDED_COUNT, PROMOTION_MIN_SIDE = 8, 4, 1080
FOLDERS = (
    'phoneScreenshots', 'phoneScreenshotsFramed', 'sevenInchScreenshots',
    'tenInchScreenshots', 'foldableScreenshots',
)


def check(root):
    failed = False
    total = 0
    phone = {p.stem for p in (root / 'phoneScreenshots').glob('*') if p.suffix.lower() in ('.jpg', '.jpeg', '.png')}
    framed = {p.stem for p in (root / 'phoneScreenshotsFramed').glob('*') if p.suffix.lower() in ('.jpg', '.jpeg', '.png')}
    if phone and framed and phone != framed:
        print('BAD: raw and framed phone sets do not match; missing or stale frames')
        failed = True
    for folder in FOLDERS:
        directory = root / folder
        shots = sorted(p for p in directory.glob('*') if p.suffix.lower() in ('.jpg', '.jpeg', '.png'))
        if not shots:
            if directory.exists():
                print(f'BAD: empty screenshot set: {directory}')
                failed = True
            continue
        # Alternative phone frames must not be counted twice toward the listing minimum.
        if folder != 'phoneScreenshotsFramed':
            total += len(shots)
        print(f'{folder}: {len(shots)} screenshots')
        if len(shots) > MAX_COUNT:
            print(f'  BAD: maximum is {MAX_COUNT} per device type')
            failed = True
        if len(shots) < RECOMMENDED_COUNT:
            print(f'  recommend: at least {RECOMMENDED_COUNT} for promotion / large-screen presentation')
        for shot in shots:
            try:
                props = subprocess.check_output(
                    ['sips', '-g', 'pixelWidth', '-g', 'pixelHeight', '-g', 'hasAlpha', '-g', 'format', str(shot)], text=True)
                values = dict(line.strip().split(': ', 1) for line in props.splitlines() if ': ' in line)
                w, h = int(values['pixelWidth']), int(values['pixelHeight'])
                short, long = sorted((w, h))
                problems = []
                if values['format'] not in ('png', 'jpeg'):
                    problems.append('must be JPEG or PNG')
                if values['hasAlpha'] != 'no':
                    problems.append('alpha channel is not accepted')
                if shot.suffix.lower() == '.png':
                    header = shot.read_bytes()[:29]
                    if header[:8] != b'\x89PNG\r\n\x1a\n' or header[24:26] != bytes((8, 2)):
                        problems.append('PNG must be 24-bit RGB')
                if short < MIN_SIDE or long > MAX_SIDE or long > 2 * short:
                    problems.append(f'dimensions must be {MIN_SIDE}–{MAX_SIDE}px, ratio at most 2:1')
                if shot.stat().st_size > MAX_BYTES:
                    problems.append('exceeds 8MB')
                if problems:
                    print(f'  BAD: {shot.name}: {"; ".join(problems)}')
                    failed = True
                else:
                    print(f'  ok: {shot.name} {w}x{h}')
                if short < PROMOTION_MIN_SIDE or long * 9 != short * 16:
                    print('    note: outside 1080px+ 16:9 / 9:16 promotion guidance (native foldable ratio may be intentional)')
            except (KeyError, ValueError, subprocess.CalledProcessError) as error:
                print(f'  BAD: unreadable image {shot}: {error}')
                failed = True
    if total < 2:
        print('BAD: at least two screenshots are required across device types')
        failed = True
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(check(Path(sys.argv[1])))
