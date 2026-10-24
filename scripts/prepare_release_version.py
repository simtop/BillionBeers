#!/usr/bin/env python3
"""Set automatic Android versions in a properties file.

Names use YYYYMMDD.N and codes use YYYYMMDDN, with N from 0 to 9.
The local calendar date starts a new sequence. A generation attempt consumes a
version before the build starts, even if the build later fails. Keep the updated
properties file between runs and in version control to retain the sequence.

Copy this standalone, standard-library-only script into another app and configure
its properties path and keys with the command-line options.
"""

from __future__ import annotations

import argparse
from datetime import date, timedelta
from pathlib import Path
import re
import stat
import sys
import tempfile


PLAY_MAX_VERSION_CODE = 2_100_000_000
RELEASES_PER_DAY = 10
# Accept the original dotted date format when migrating an existing app.
DAILY_VERSION = re.compile(r"([0-9]{8}|[0-9]{4}\.[0-9]{2}\.[0-9]{2})\.([0-9]+)")


def property_value(contents: str, key: str) -> re.Match[str]:
    # Look ahead for CRLF as well, preserving the original file's line endings.
    pattern = re.compile(rf"^{re.escape(key)}=([^\r\n]*)(?=\r?$)", re.MULTILINE)
    matches = list(pattern.finditer(contents))
    if len(matches) != 1:
        raise ValueError(f"properties file must define {key} exactly once")
    return matches[0]


def prepare_release_version(
    properties_path: Path,
    release_date: date,
    version_code_key: str = "versionCode",
    version_name_key: str = "versionName",
) -> tuple[int, str]:
    if not version_code_key or not version_name_key or version_code_key == version_name_key:
        raise ValueError("version property keys must be nonempty and different")

    properties_path = properties_path.resolve()
    contents = properties_path.read_bytes().decode("utf-8")
    code_match = property_value(contents, version_code_key)
    name_match = property_value(contents, version_name_key)
    code_value = code_match.group(1).strip()
    if not re.fullmatch(r"[0-9]+", code_value):
        raise ValueError(f"{version_code_key} must contain a nonnegative integer")
    current_code = int(code_value)

    current_date: date | None = None
    current_sequence = 0
    daily_match = DAILY_VERSION.fullmatch(name_match.group(1).strip())
    if daily_match:
        previous_day = daily_match.group(1).replace(".", "")
        current_date = date(int(previous_day[:4]), int(previous_day[4:6]), int(previous_day[6:]))
        current_sequence = int(daily_match.group(2))

    # The code also retains the sequence if another tool has changed the display name.
    code_day = current_code // RELEASES_PER_DAY
    if 10_000_000 <= code_day <= 99_999_999:
        code_day_text = f"{code_day:08d}"
        try:
            code_date = date(
                int(code_day_text[:4]), int(code_day_text[4:6]), int(code_day_text[6:])
            )
        except ValueError:
            code_date = None
        if code_date is not None and (current_date is None or code_date > current_date):
            current_date = code_date
            current_sequence = current_code % RELEASES_PER_DAY
        elif code_date == current_date and code_date is not None:
            current_sequence = max(current_sequence, current_code % RELEASES_PER_DAY)

    version_date = max(release_date, current_date) if current_date else release_date
    sequence = current_sequence + 1 if current_date == version_date else 0
    if sequence >= RELEASES_PER_DAY:
        overflow_days, sequence = divmod(sequence, RELEASES_PER_DAY)
        try:
            version_date += timedelta(days=overflow_days)
        except OverflowError as error:
            raise ValueError("automatic release date exceeds the supported year range") from error

    date_prefix = f"{version_date.year:04d}{version_date.month:02d}{version_date.day:02d}"
    next_code = int(date_prefix) * RELEASES_PER_DAY + sequence
    next_name = f"{date_prefix}.{sequence}"
    if next_code > PLAY_MAX_VERSION_CODE:
        raise ValueError(f"generated versionCode {next_code} exceeds Google Play's limit")
    if next_code <= current_code:
        raise ValueError(
            f"generated versionCode {next_code} must exceed existing versionCode {current_code}; "
            "check the release date and existing version"
        )

    replacements = [(code_match.span(1), str(next_code)), (name_match.span(1), next_name)]
    for (start, end), value in sorted(replacements, reverse=True):
        contents = contents[:start] + value + contents[end:]

    # Replace both fields together so a failed write cannot leave half a version update.
    temporary_path = None
    try:
        with tempfile.NamedTemporaryFile(dir=properties_path.parent, delete=False) as temporary:
            temporary_path = Path(temporary.name)
            temporary.write(contents.encode("utf-8"))
        temporary_path.chmod(stat.S_IMODE(properties_path.stat().st_mode))
        temporary_path.replace(properties_path)
    finally:
        if temporary_path is not None:
            temporary_path.unlink(missing_ok=True)
    return next_code, next_name


def main() -> int:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("--properties", type=Path, default=Path("gradle.properties"))
    parser.add_argument("--version-code-key", default="versionCode")
    parser.add_argument("--version-name-key", default="versionName")
    parser.add_argument(
        "--date", type=date.fromisoformat, default=date.today(),
        help="release date (YYYY-MM-DD); defaults to the machine's local date",
    )
    args = parser.parse_args()

    try:
        version_code, version_name = prepare_release_version(
            args.properties, args.date, args.version_code_key, args.version_name_key
        )
    except (OSError, ValueError) as error:
        print(f"Release version update failed: {error}", file=sys.stderr)
        return 1

    print(f"Prepared Android release version {version_name} (versionCode {version_code}).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
