#!/usr/bin/env bash
set -euo pipefail

# Script to download contents from https://github.com/chauhaidang/xq-toolbox/tree/main/packages/xq-scripts
# and put them into the scripts directory

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_DIR="$SCRIPT_DIR/scripts"
mkdir -p "$TARGET_DIR"

REPO_URL="https://github.com/chauhaidang/xq-toolbox.git"
REMOTE_PATH="packages/xq-scripts"

echo "Downloading contents from $REPO_URL ($REMOTE_PATH) to $TARGET_DIR..."

TEMP_DIR=$(mktemp -d)

function cleanup {
  rm -rf "$TEMP_DIR"
}
trap cleanup EXIT

# Clone sparsely
git clone --depth 1 --filter=blob:none --sparse "$REPO_URL" "$TEMP_DIR" >/dev/null 2>&1
pushd "$TEMP_DIR" >/dev/null
git sparse-checkout set "$REMOTE_PATH" >/dev/null 2>&1
popd >/dev/null

# Copy contents
echo "$TEMP_DIR"
if [ -d "$TEMP_DIR/$REMOTE_PATH" ]; then
    # Use rsync to copy contents (files and subdirs) from the remote path to the target directory
    rsync -av --exclude='.git' "$TEMP_DIR/$REMOTE_PATH/" "$TARGET_DIR/"
else
    echo "Error: Remote path $REMOTE_PATH not found."
    exit 1
fi

echo "Download complete."
