# vp-mcp-router

MCP router for Visual Paradigm: an in-process plugin that serves the open
project as JSON over Streamable HTTP, and writes back through previewed,
compensable batches.

- **Reads** — diagrams, the model tree (including members), neighborhoods,
  PNG/SVG/PDF export
- **Writes** — create, connect, move, style, delete via
  `vp_preview_batch` → `vp_apply_batch`; partial failures compensate in
  reverse; saving is explicit and confirm-gated
- **Schema packs** — versioned per VP minor, so VP updates ship as data,
  not plugin rebuilds

Built for [opencode](https://opencode.ai): an agent reads and models
UML/ERD diagrams in your locally running Visual Paradigm project.

## Quickstart

```bash
curl -fsSL https://raw.githubusercontent.com/chramiq/vp-mcp-router/main/install.sh | bash
```

One command, interactive: fetches the latest release, asks for your
Visual Paradigm plugins directory, and wires up opencode.

Requires Visual Paradigm (tested on 18.1), Java 11+ and Maven.
Full build/install/uninstall walkthrough: [docs/INSTALL.md](docs/INSTALL.md).

## Docs

- [docs/INSTALL.md](docs/INSTALL.md) — build, install, schema packs, HotSwap
- [docs/INDEX.md](docs/INDEX.md) — architecture decision records
- [AGENTS.md](AGENTS.md) — workflow and safety rules for coding agents

## License

[MIT](LICENSE). Vendored MCP core attribution: [plugin/NOTICE.txt](plugin/NOTICE.txt).
