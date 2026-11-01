#!/usr/bin/env bash
# Fetches the Whisper tiny model + a 16 kHz sample and pushes them to a connected
# device, so :core:connectedDebugAndroidTest can run real inference without the
# in-app ~99 MB download.
#
#   ./scripts/push-test-model.sh [serial]
#
set -euo pipefail

SERIAL="${1:-}"
ADB=(adb)
if [[ -n "$SERIAL" ]]; then
  ADB=(adb -s "$SERIAL")
fi

CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/openflow-test-model"
REMOTE_DIR="/data/local/tmp/openflow-models"
HF="https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/main"

mkdir -p "$CACHE_DIR"

fetch() {
  local name="$1" url="$2"
  if [[ ! -s "$CACHE_DIR/$name" ]]; then
    echo "downloading $name"
    curl -fL --retry 3 -o "$CACHE_DIR/$name" "$url"
  fi
}

fetch tiny-encoder.int8.onnx "$HF/tiny-encoder.int8.onnx"
fetch tiny-decoder.int8.onnx "$HF/tiny-decoder.int8.onnx"
fetch tiny-tokens.txt       "$HF/tiny-tokens.txt"
fetch silero_vad.onnx       "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"
fetch sample.wav            "$HF/test_wavs/0.wav"

"${ADB[@]}" shell mkdir -p "$REMOTE_DIR"
"${ADB[@]}" push "$CACHE_DIR/tiny-encoder.int8.onnx" "$REMOTE_DIR/"
"${ADB[@]}" push "$CACHE_DIR/tiny-decoder.int8.onnx" "$REMOTE_DIR/"
"${ADB[@]}" push "$CACHE_DIR/tiny-tokens.txt" "$REMOTE_DIR/"
"${ADB[@]}" push "$CACHE_DIR/silero_vad.onnx" "$REMOTE_DIR/"
"${ADB[@]}" push "$CACHE_DIR/sample.wav" "$REMOTE_DIR/"

echo "ready: ./gradlew :core:connectedDebugAndroidTest"
