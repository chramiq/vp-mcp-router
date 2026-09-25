#!/usr/bin/env bash
# Cut a release: bump VERSION, test, build, package dist zip.
# Single entry point so the version label always matches the code.
#
#   opencode/release.sh 0.14.2
#
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="${1:-}"
if [ -z "$VERSION" ]; then echo "usage: release.sh <version>" >&2; exit 1; fi
if [ -n "$(cd "$REPO" && git status --porcelain)" ]; then
  echo "tree is dirty, commit first" >&2; exit 1
fi

SRC="$REPO/plugin/src/main/java/vpmcp/RouterPlugin.java"
sed -i "s/private static final String VERSION = \".*\";/private static final String VERSION = \"$VERSION\";/" "$SRC"
grep -q "VERSION = \"$VERSION\"" "$SRC" || { echo "bump failed" >&2; exit 1; }

(cd "$REPO/plugin" && mvn -q test > /tmp/opencode/release-test.log 2>&1) || {
  echo "tests failed, see /tmp/opencode/release-test.log" >&2
  exit 1
}
(cd "$REPO/plugin" && mvn -q package > /tmp/opencode/release-build.log 2>&1) || {
  echo "build failed, see /tmp/opencode/release-build.log" >&2
  exit 1
}

DIST="$REPO/dist/vp-router-$VERSION.zip"
mkdir -p "$REPO/dist"
STAGE="$(mktemp -d)"
mkdir -p "$STAGE/vp-router-$VERSION"
cp "$REPO/plugin/target/router.jar" "$STAGE/vp-router-$VERSION/"
cp "$REPO/plugin/src/main/resources/vp.router/plugin.xml" "$STAGE/vp-router-$VERSION/"
cp -r "$REPO/schemas" "$STAGE/vp-router-$VERSION/"
cp -r "$REPO/opencode/skills" "$STAGE/vp-router-$VERSION/skills"
(cd "$STAGE" && zip -qr "$DIST" "vp-router-$VERSION")
rm -rf "$STAGE"
echo "release $VERSION ready: $DIST"
echo "commit and tag with:"
echo "  git add -A && git commit -m \"Release $VERSION\" && git tag -a \"v$VERSION\" -m \"Release $VERSION\""
echo "publish with: git push --follow-tags && gh release create \"v$VERSION\" \"$DIST\""
