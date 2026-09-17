#!/usr/bin/env python3
"""Regenerate schemas/<vp-version>/ from openapi.jar via javap (no class loading).

Usage: autovendor.py --openapi <openapi.jar> --out schemas/v18.1 [--vp-version 18.1]
Rerun on every VP update, diff the output, commit.
"""
import argparse
import hashlib
import json
import re
import subprocess
import sys
from datetime import date

METHOD_RE = re.compile(r"^\s*public [\w<>\[\]., ]+ (\w+)\((.*)\);$")
CONST_RE = re.compile(r'^\s*public static final java\.lang\.String (\w+) = "(.*)";$')


def javap(openapi, cls, constants=False):
    cmd = ["javap", "-classpath", openapi]
    if constants:
        cmd.append("-constants")
    cmd.append(cls)
    out = subprocess.run(cmd, capture_output=True, text=True, check=True)
    return out.stdout.splitlines()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--openapi", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--vp-version", required=True)
    args = ap.parse_args()

    def entries(cls):
        names, consts = [], {}
        for line in javap(args.openapi, cls, constants=True):
            m = METHOD_RE.match(line)
            if m:
                name, params = m.group(1), m.group(2).strip()
                names.append({"name": name, "params": params} if params else {"name": name})
                continue
            c = CONST_RE.match(line)
            if c:
                consts[c.group(1)] = c.group(2)
        return names, consts

    _, diagram_types = entries("com.vp.plugin.diagram.IDiagramTypeConstants")
    _, shape_types = entries("com.vp.plugin.diagram.IShapeTypeConstants")
    methods, _ = entries("com.vp.plugin.model.factory.IModelElementFactory")
    creates = sorted(m["name"] for m in methods if m["name"].startswith("create"))

    import os
    os.makedirs(args.out, exist_ok=True)

    def write(name, payload):
        with open(os.path.join(args.out, name), "w") as f:
            json.dump(payload, f, indent=2, sort_keys=True)
            f.write("\n")

    with open(args.openapi, "rb") as f:
        sha = hashlib.sha256(f.read()).hexdigest()[:16]
    write("meta.json", {
        "vp_version": args.vp_version,
        "openapi_sha16": sha,
        "generated": date.today().isoformat(),
        "counts": {
            "diagram_types": len(diagram_types),
            "shape_types": len(shape_types),
            "factory_creates": len(creates),
        },
    })
    write("diagram-types.json", [{"name": k, "value": v} for k, v in sorted(diagram_types.items())])
    write("shape-types.json", [{"name": k, "value": v} for k, v in sorted(shape_types.items())])
    write("factory-creates.json", creates)
    print(f"wrote {args.out}: {len(diagram_types)} diagrams, {len(shape_types)} shapes, {len(creates)} creates")


if __name__ == "__main__":
    sys.exit(main())
