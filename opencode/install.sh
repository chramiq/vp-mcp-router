#!/usr/bin/env bash
# Install vp-mcp-router: VP plugin files + opencode MCP entry + skill.
# Idempotent and rerunnable. Always asks for directories; env overrides
# below exist so the script is testable without touching real configs.
#
#   opencode/install.sh [--dry-run]
#
# Env: VP_PLUGINS_DIR OPENCODE_JSON SKILL_DIR ROUTER_JAR SCHEMA_VERSION
#   or: opencode/install.sh --release <dist-zip> [--dry-run] (no Maven needed)
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
DRY_RUN=0
RELEASE_ZIP="${RELEASE_ZIP:-}"
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=1 ;;
    --release) WANT_RELEASE=1 ;;
    *) if [ "${WANT_RELEASE:-0}" = 1 ] && [ -z "$RELEASE_ZIP" ]; then RELEASE_ZIP="$arg"; fi ;;
  esac
done

run() {
  if [ "$DRY_RUN" -eq 1 ]; then
    echo "would run: $*"
  else
    "$@"
  fi
}

prompt() {
  local text="$1" def="${2:-}" ans=""
  if [ -n "$def" ]; then text="$text [$def]"; fi
  if [ -r /dev/tty ] && [ -w /dev/tty ]; then
    read -r -p "$text: " ans < /dev/tty || true
  else
    echo "(no terminal, using default)" >&2
  fi
  echo "${ans:-$def}"
}

guess_vp_plugins_dir() {
  for candidate in "$HOME/.config/VisualParadigm/plugins" \
      "$HOME/AppData/Roaming/VisualParadigm/plugins" \
      "$HOME/Library/Application Support/VisualParadigm/plugins"; do
    if [ -d "$candidate" ]; then echo "$candidate"; return; fi
  done
  echo ""
}

# --- 1. VP plugin files -------------------------------------------------
VP_PLUGINS_DIR="${VP_PLUGINS_DIR:-}"
if [ -z "$VP_PLUGINS_DIR" ]; then
  VP_PLUGINS_DIR="$(prompt "VP plugins directory" "$(guess_vp_plugins_dir)")"
fi
if [ -z "$VP_PLUGINS_DIR" ]; then echo "no directory given, aborting" >&2; exit 1; fi

ROUTER_JAR="${ROUTER_JAR:-$REPO/plugin/target/router.jar}"
if [ -n "$RELEASE_ZIP" ]; then
  if [ ! -f "$RELEASE_ZIP" ]; then echo "missing release zip: $RELEASE_ZIP" >&2; exit 1; fi
  UNPACK="$(mktemp -d)"
  unzip -q "$RELEASE_ZIP" -d "$UNPACK"
  INNER="$(echo "$UNPACK"/vp-router-*)"
  ROUTER_JAR="$INNER/router.jar"
  RELEASE_SCHEMAS="$INNER/schemas"
  RELEASE_SKILL="$INNER/skills/vp-router"
elif [ ! -f "$ROUTER_JAR" ]; then
  echo "building router.jar..."
  if [ "$DRY_RUN" -eq 1 ]; then
    echo "would run: mvn -q -f $REPO/plugin/pom.xml package"
  else
    mvn -q -f "$REPO/plugin/pom.xml" package
  fi
fi

SCHEMA_VERSION="${SCHEMA_VERSION:-}"
SCHEMA_SRC="${RELEASE_SCHEMAS:-$REPO/schemas}"
if [ -z "$SCHEMA_VERSION" ]; then
  options=$(ls "$SCHEMA_SRC" | grep -v autovendor || true)
  if [ "$(echo "$options" | wc -l)" -eq 1 ]; then
    SCHEMA_VERSION="$options"
    echo "single schema pack: $SCHEMA_VERSION"
  else
    echo "available schema packs:"; echo "$options"
    SCHEMA_VERSION="$(prompt "schema version")"
  fi
fi
if [ ! -d "$SCHEMA_SRC/$SCHEMA_VERSION" ]; then echo "unknown schema: $SCHEMA_VERSION" >&2; exit 1; fi

DEST="$VP_PLUGINS_DIR/vp.router"
PLUGIN_XML="${INNER:-$REPO/plugin/src/main/resources/vp.router}/plugin.xml"
if [ ! -f "$PLUGIN_XML" ]; then PLUGIN_XML="$REPO/plugin/src/main/resources/vp.router/plugin.xml"; fi
run mkdir -p "$DEST/lib"
run cp "$PLUGIN_XML" "$DEST/plugin.xml"
run cp "$ROUTER_JAR" "$DEST/lib/router.jar"
run rm -rf "$DEST/schemas"
run mkdir -p "$DEST/schemas"
run cp -r "$SCHEMA_SRC/$SCHEMA_VERSION" "$DEST/schemas/$SCHEMA_VERSION"
echo "VP plugin installed to $DEST"

# --- 2. opencode MCP entry ----------------------------------------------
OPENCODE_JSON="${OPENCODE_JSON:-}"
if [ -z "$OPENCODE_JSON" ]; then
  echo "opencode.json target: 1) ./opencode.json (project)  2) $HOME/.config/opencode/opencode.json (global)"
  choice="$(prompt "choice [1]")"
  if [ "$choice" = "2" ]; then
    OPENCODE_JSON="$HOME/.config/opencode/opencode.json"
  else
    OPENCODE_JSON="./opencode.json"
  fi
fi
if [ "$DRY_RUN" -eq 1 ]; then
  echo "would merge visual-paradigm MCP entry into $OPENCODE_JSON"
else
  mkdir -p "$(dirname "$OPENCODE_JSON")"
  ROUTER_URL="${ROUTER_URL:-http://127.0.0.1:8899/mcp}" VP_MCP_ENABLED="${VP_MCP_ENABLED:-true}" python3 - "$OPENCODE_JSON" <<'EOF'
import json, os, re, sys
path = sys.argv[1]
url = os.environ["ROUTER_URL"]
enabled = os.environ.get("VP_MCP_ENABLED", "true") == "true"
cfg = {}
if os.path.isfile(path):
    with open(path) as f:
        raw = f.read()
    raw = re.sub(r"(?m)^\s*//.*$", "", raw)
    raw = re.sub(r",\s*([}\]])", r"\1", raw)
    cfg = json.loads(raw)
cfg.setdefault("mcp", {})["visual-paradigm"] = {
    "type": "remote",
    "url": url,
    "enabled": enabled,
}
with open(path, "w") as f:
    json.dump(cfg, f, indent=2)
    f.write("\n")
EOF
  echo "MCP entry merged into $OPENCODE_JSON (existing comments not preserved)"
fi

# --- 3. skill ------------------------------------------------------------
SKILL_DIR="${SKILL_DIR:-}"
if [ -z "$SKILL_DIR" ]; then
  echo "skill destination: 1) ./.opencode/skills (project)  2) $HOME/.config/opencode/skills (global)"
  choice="$(prompt "choice [1]")"
  if [ "$choice" = "2" ]; then
    SKILL_DIR="$HOME/.config/opencode/skills"
  else
    SKILL_DIR="./.opencode/skills"
  fi
fi
run mkdir -p "$SKILL_DIR"
SKILL_SRC="${RELEASE_SKILL:-$REPO/opencode/skills/vp-router}"
run cp -r "$SKILL_SRC" "$SKILL_DIR/vp-router"
echo "skill installed to $SKILL_DIR/vp-router"

echo "done. Restart Visual Paradigm, then verify:"
echo "  curl -s -X POST http://127.0.0.1:8899/mcp -H 'Content-Type: application/json' -d '{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}'"
