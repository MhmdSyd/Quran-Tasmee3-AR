#!/usr/bin/env bash
# Fetches the two large binary dependencies of the offline ASR engine (build-time only;
# the app itself has NO INTERNET permission and never downloads anything):
#   1. sherpa-onnx v2.0.1 Android native libs  -> app/src/main/jniLibs/<abi>/libsherpa-onnx-jni.so
#   2. quran-asr-small INT8 ONNX model         -> app/src/main/assets/asr/quran-asr-small/model.int8.onnx
# Everything is pinned and SHA-256 verified. Re-running is a no-op when files already verify.
# Offline build machines: download the files elsewhere and place them at the paths above.
set -euo pipefail
cd "$(dirname "$0")/.."

SHERPA_VER="v2.0.1"
SHERPA_ARCHIVE="sherpa-onnx-${SHERPA_VER}-android-static-link-onnxruntime.tar.bz2"
SHERPA_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/${SHERPA_VER}/${SHERPA_ARCHIVE}"
SHERPA_SHA256="fe1c9ceffd86770218a1d4bba6b410d6f726b2b8dda6a5488499b05250b53aea"
ABIS=(arm64-v8a armeabi-v7a x86_64)

HF_COMMIT="0c2744b9d70aaf45ba574c30c5d318c575258e66"
MODEL_DIR="app/src/main/assets/asr/quran-asr-small"
MODEL_URL="https://huggingface.co/Abdou/quran-asr-small/resolve/${HF_COMMIT}/model.int8.onnx"
MODEL_SHA256="88f9df290d57b8890275d79187b77515aeeb12626a42bc76cbd0f8bfcaff00f0"
TOKENS_URL="https://huggingface.co/Abdou/quran-asr-small/resolve/${HF_COMMIT}/tokens.txt"

sha() { sha256sum "$1" | cut -d' ' -f1; }

# ---- model ----
if [[ -f "$MODEL_DIR/model.int8.onnx" && "$(sha "$MODEL_DIR/model.int8.onnx")" == "$MODEL_SHA256" ]]; then
  echo "model.int8.onnx: OK (already present)"
else
  echo "Downloading model.int8.onnx (~36 MB) ..."
  curl -fL --retry 3 -o "$MODEL_DIR/model.int8.onnx.part" "$MODEL_URL"
  got="$(sha "$MODEL_DIR/model.int8.onnx.part")"
  [[ "$got" == "$MODEL_SHA256" ]] || { echo "SHA-256 mismatch for model: $got"; rm -f "$MODEL_DIR/model.int8.onnx.part"; exit 1; }
  mv "$MODEL_DIR/model.int8.onnx.part" "$MODEL_DIR/model.int8.onnx"
  echo "model.int8.onnx: OK"
fi

# upstream tokens.txt must equal the committed copy (guards against a changed vocabulary)
curl -fsL --retry 3 "$TOKENS_URL" -o /tmp/upstream_tokens.txt
cmp -s /tmp/upstream_tokens.txt "$MODEL_DIR/tokens.txt" || { echo "Upstream tokens.txt differs from committed copy!"; exit 1; }
echo "tokens.txt: OK (matches upstream)"

# ---- sherpa-onnx native libs ----
missing=0
for abi in "${ABIS[@]}"; do [[ -f "app/src/main/jniLibs/$abi/libsherpa-onnx-jni.so" ]] || missing=1; done
if [[ $missing -eq 0 ]]; then
  echo "sherpa-onnx native libs: OK (already present)"
else
  tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
  echo "Downloading $SHERPA_ARCHIVE ..."
  curl -fL --retry 3 -o "$tmp/$SHERPA_ARCHIVE" "$SHERPA_URL"
  [[ "$(sha "$tmp/$SHERPA_ARCHIVE")" == "$SHERPA_SHA256" ]] || { echo "SHA-256 mismatch for sherpa-onnx archive"; exit 1; }
  tar xjf "$tmp/$SHERPA_ARCHIVE" -C "$tmp"
  for abi in "${ABIS[@]}"; do
    mkdir -p "app/src/main/jniLibs/$abi"
    cp "$tmp/jniLibs/$abi/libsherpa-onnx-jni.so" "app/src/main/jniLibs/$abi/"
  done
  echo "sherpa-onnx native libs: OK (${ABIS[*]})"
fi
