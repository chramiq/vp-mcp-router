# Install

Fastest path: `opencode/install.sh` (asks for every directory, merges
the MCP entry, copies the skill; `--dry-run` previews, env overrides
in the script header make it testable). What follows is the manual
equivalent.

## Build

```bash
cd plugin
mvn package
```

Requires Java 11+ and Maven. `openapi.jar` defaults to
`/usr/share/visual-paradigm/Application/lib/openapi.jar`; override with
`-Dvp.openApi=/path/to/openapi.jar`. Output is `target/router.jar`
(gson bundled, VP provides `openapi`).

## Install into Visual Paradigm

Copy three things into the VP user plugins directory
(`~/.config/VisualParadigm/plugins/` on Linux):

| Into `plugins/vp.router/` | From the repo |
|---|---|
| `plugin.xml` | `plugin/src/main/resources/vp.router/plugin.xml` |
| `lib/router.jar` | `plugin/target/router.jar` |
| `schemas/<version>/` | `schemas/<version>/` (match your VP, see below) |

Restart Visual Paradigm. `Tools → MCP Router` shows the status;
`plugins/vp.router/vpmcp.log` confirms
`MCP server listening on http://127.0.0.1:8899/mcp`.

If port 8899 is taken, create `plugins/vp.router/mcp.properties`
with `port=8910` (or pass `-Dvpmcp.port=8910` to VP).

## Schema pack version

Pick the directory under `schemas/` matching the running VP.
Regenerate for a new VP with
`python3 schemas/autovendor.py --openapi <openapi.jar> --out schemas/vX.Y --vp-version X.Y`,
diff, and commit. A mismatch degrades unknown types to standard dumps
rather than failing.

## Connect opencode

Merge `opencode/mcp.json` into your `opencode.json` (remote MCP at
`http://127.0.0.1:8899/mcp`). Copy `opencode/skills/vp-router/` to
`.opencode/skills/` in your workspace or
`~/.config/opencode/skills/` globally. VP must be running with a
project open before the agent calls any tool.

## Uninstall

Delete `plugins/vp.router/` and restart Visual Paradigm.
