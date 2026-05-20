#!/usr/bin/env python3
import argparse
import datetime as dt
import json
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--apk-url", required=True)
    parser.add_argument("--apk-sha256", required=True)
    parser.add_argument("--apk-size", required=True, type=int)
    parser.add_argument("--notes-file", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    notes_path = Path(args.notes_file)
    notes = [
        line.strip()
        for line in notes_path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    if not notes:
        notes = [f"发布版本 {args.version_name}"]

    update_info = {
        "schemaVersion": 1,
        "versionCode": args.version_code,
        "versionName": args.version_name,
        "minSupportedVersionCode": 1,
        "forceUpdate": False,
        "apkUrl": args.apk_url,
        "apkSha256": args.apk_sha256,
        "apkSize": args.apk_size,
        "releaseNotes": notes,
        "publishedAt": dt.datetime.now(dt.timezone.utc)
        .replace(microsecond=0)
        .isoformat()
        .replace("+00:00", "Z"),
    }

    Path(args.output).write_text(
        json.dumps(update_info, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
