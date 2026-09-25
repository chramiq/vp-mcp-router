#!/usr/bin/env bash
# Quick install for vp-mcp-router: downloads a release and runs the
# bundled interactive installer. Pipe-friendly:
#
#   curl -fsSL https://raw.githubusercontent.com/chramiq/vp-mcp-router/main/install.sh | bash
#   curl -fsSL ... | bash -s -- 0.16.1           # pin a version
#   curl -fsSL ... | bash -s -- latest --dry-run # preview
#
# Needs: bash, curl, unzip, python3. Prompts go to /dev/tty, so piping
# still asks interactively; with no terminal the defaults are used.
set -euo pipefail

GH_REPO="chramiq/vp-mcp-router"
VERSION="latest"
INSTALLER_ARGS=()
for arg in "$@"; do
  if [ "$arg" = "latest" ] && [ "$VERSION" = "latest" ]; then continue; fi
  if [ "${VERSION_SET:-0}" = 0 ] && [[ "$arg" != -* ]]; then
    VERSION="$arg"; VERSION_SET=1
  else
    INSTALLER_ARGS+=("$arg")
  fi
done

need() { command -v "$1" > /dev/null 2>&1 || { echo "missing dependency: $1" >&2; exit 1; }; }
need curl; need unzip; need python3

if [ "$VERSION" = "latest" ]; then
  VERSION="$(curl -fsSL "https://api.github.com/repos/$GH_REPO/releases/latest" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["tag_name"].lstrip("v"))')"
fi
echo "vp-mcp-router $VERSION"

ZIP="vp-router-$VERSION.zip"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

curl -fsSL "https://github.com/$GH_REPO/releases/download/v$VERSION/$ZIP" -o "$TMP/$ZIP"
unzip -q "$TMP/$ZIP" -d "$TMP"

INSTALLER="$TMP/vp-router-$VERSION/install.sh"
if [ ! -f "$INSTALLER" ]; then
  # older releases did not bundle the installer; fetch the matching one
  curl -fsSL "https://raw.githubusercontent.com/$GH_REPO/v$VERSION/opencode/install.sh" -o "$INSTALLER"
fi
bash "$INSTALLER" --release "$TMP/$ZIP" "${INSTALLER_ARGS[@]+"${INSTALLER_ARGS[@]}"}"
