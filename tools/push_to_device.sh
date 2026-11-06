#!/bin/bash
# Export the iTunes library (asking FLAC or AAC for Apple Lossless tracks) and push it
# to the connected Android device.
# Install and launch Spinback once before running this, so the app owns its files dir.
#
# Usage: tools/push_to_device.sh [export dir]
# Folders come from tools/spinback.conf (see spinback.conf.example) unless given here.
# With several devices connected, pick one with ANDROID_SERIAL=<serial>.
set -euo pipefail

PACKAGE="com.minagawer.spinback"
DEST="/sdcard/Android/data/$PACKAGE/files"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
ADB="${ADB:-$(command -v adb || echo "$SDK_DIR/platform-tools/adb")}"

EXPORT_DIR="$(python3 -I "$SCRIPT_DIR/export_library.py" ${1:+--out "$1"} --print-export-dir)"

if [[ ! -x "$ADB" ]]; then
  echo "adb not found. Install Android SDK platform-tools, or set ADB=/path/to/adb." >&2
  exit 1
fi
if ! "$ADB" shell pm path "$PACKAGE" >/dev/null 2>&1; then
  echo "Spinback isn't installed on the device. Install it from Android Studio first." >&2
  exit 1
fi

# Default to whatever format the current export uses, so pressing Enter changes nothing.
current="flac"
if [[ -f "$EXPORT_DIR/library.json" ]] && ! compgen -G "$EXPORT_DIR/music/*.flac" >/dev/null; then
  current="aac"
fi

lossless="$current"
if [[ -t 0 ]]; then
  echo "Apple ロスレスの曲をどの形式で入れますか？"
  echo "  1) FLAC  ロスレスのまま（容量大）"
  echo "  2) AAC   256kbps（FLAC の約 1/4）"
  [[ "$current" == "flac" ]] && default_choice=1 || default_choice=2
  read -r -p "番号を入力 [$default_choice]: " choice
  case "${choice:-$default_choice}" in
    1) lossless="flac" ;;
    2) lossless="aac" ;;
    *) echo "1 か 2 を入力してください" >&2; exit 1 ;;
  esac
fi

echo "Exporting ($lossless)..."
python3 -I "$SCRIPT_DIR/export_library.py" --out "$EXPORT_DIR" --lossless "$lossless"

for dir in music artwork; do
  "$ADB" shell mkdir -p "$DEST/$dir"

  # Remove files on the device that are no longer in the export (e.g. after a format change).
  stale=$(comm -13 <(ls "$EXPORT_DIR/$dir" | sort) <("$ADB" shell ls "$DEST/$dir" | tr -d '\r' | sort))
  if [[ -n "$stale" ]]; then
    echo "Removing $(echo "$stale" | wc -l | tr -d ' ') stale files from $dir on the device"
    echo "$stale" | sed "s|^|$DEST/$dir/|" | "$ADB" shell xargs rm -f
  fi
  # --sync skips files already on the device, so re-running only sends what's new.
  "$ADB" push --sync "$EXPORT_DIR/$dir" "$DEST/"
done
"$ADB" push "$EXPORT_DIR/library.json" "$DEST/library.json"
echo "Done. Restart Spinback to load the library."
