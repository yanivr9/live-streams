#!/bin/sh
# Publishes the release build (shrunk, optimized, same signing key) as the app's update: dist/StreamDesk.apk and dist/update.json, read by Updates.UPDATE_URL.
# Usage: scripts/release.sh "What's new, one line"   (then commit and push dist/)
set -e
cd "$(dirname "$0")/.."
meta=app/build/outputs/apk/release/output-metadata.json
code=$(sed -n 's/.*"versionCode": \([0-9]*\).*/\1/p' "$meta")
name=$(sed -n 's/.*"versionName": "\([^"]*\)".*/\1/p' "$meta")
cp app/build/outputs/apk/release/app-release.apk dist/StreamDesk.apk
python3 -c 'import json,sys; print(json.dumps({"versionCode": int(sys.argv[1]), "versionName": sys.argv[2], "apk": "StreamDesk.apk", "notes": sys.argv[3]}, indent=2, ensure_ascii=False))' \
    "$code" "$name" "${1:-}" > dist/update.json
echo "dist/ now offers $name ($code)"
