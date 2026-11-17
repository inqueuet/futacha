#!/bin/sh
set -eu
case "$PLATFORM_NAME" in
  iphoneos) voice_target=iosArm64 ;;
  iphonesimulator) voice_target=iosSimulatorArm64 ;;
  *) echo "Unsupported voice platform: $PLATFORM_NAME" >&2; exit 1 ;;
esac
voice_root="${SRCROOT}/../build/voicevox/ios/${voice_target}"
voice_destination="${TARGET_BUILD_DIR}/${FRAMEWORKS_FOLDER_PATH}"
mkdir -p "$voice_destination"
for voice_name in voicevox_core voicevox_onnxruntime; do
  voice_source="$voice_root/$voice_name.framework"
  if [ ! -f "$voice_source/$voice_name" ]; then
    echo "Missing $voice_source. Build shared for $voice_target first." >&2
    exit 1
  fi
  rm -rf "$voice_destination/$voice_name.framework"
  /usr/bin/ditto "$voice_source" "$voice_destination/$voice_name.framework"
  if [ "${CODE_SIGNING_ALLOWED:-}" != "NO" ]; then
    /usr/bin/codesign --force --sign "${EXPANDED_CODE_SIGN_IDENTITY:--}" --timestamp=none "$voice_destination/$voice_name.framework"
  fi
done
