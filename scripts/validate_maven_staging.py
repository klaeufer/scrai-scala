#!/usr/bin/env python3
"""Validate the locally staged scrai Maven artifacts without uploading them."""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

SCALA_LINES = ("3.3", "3.4", "3.5", "3.6", "3.7", "3.8", "3.9", "3.10")
RUNTIME_ARTIFACTS = {f"scrai-runtime_{line}" for line in SCALA_LINES}
PLUGIN_PREFIX = "sbt-console-ai_"
REQUIRED_POM_FIELDS = ("name", "description", "url", "licenses", "developers", "scm")


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("staging", type=Path, nargs="?", default=Path("target/sona-staging"))
    parser.add_argument("--version", required=True, help="Expected Maven version")
    parser.add_argument("--signed", action="store_true", help="Require detached .asc signatures")
    args = parser.parse_args()

    root = args.staging.resolve()
    if not root.is_dir():
        fail(f"staging directory does not exist: {root}")
        return 1

    poms = sorted(root.rglob("*.pom"))
    if not poms:
        fail(f"no POM files found under {root}")
        return 1

    found_runtime: set[str] = set()
    found_plugins: set[str] = set()
    errors = 0
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}

    for pom in poms:
        try:
            project = ET.parse(pom).getroot()
        except ET.ParseError as exc:
            fail(f"invalid POM {pom}: {exc}")
            errors += 1
            continue

        def text(path: str) -> str:
            node = project.find(path, ns)
            return (node.text or "").strip() if node is not None else ""

        artifact = text("m:artifactId")
        version = text("m:version")
        if version.removesuffix("-SNAPSHOT") != args.version:
            fail(f"{pom}: expected version {args.version} (optionally -SNAPSHOT), found {version!r}")
            errors += 1
        if text("m:groupId") != "edu.luc.cs":
            fail(f"{pom}: expected groupId edu.luc.cs")
            errors += 1
        if artifact in RUNTIME_ARTIFACTS:
            found_runtime.add(artifact)
        elif artifact.startswith(PLUGIN_PREFIX):
            found_plugins.add(artifact)
        else:
            fail(f"unexpected staged artifact: {artifact} ({pom})")
            errors += 1

        for field in REQUIRED_POM_FIELDS:
            if project.find(f"m:{field}", ns) is None:
                fail(f"{pom}: required POM field {field!r} is missing")
                errors += 1

        artifact_base = f"{artifact}-{version}"
        for classifier, filename in (("binary", f"{artifact_base}.jar"), ("sources", f"{artifact_base}-sources.jar"), ("javadoc", f"{artifact_base}-javadoc.jar")):
            artifact_file = pom.parent / filename
            if not artifact_file.is_file():
                fail(f"{pom}: missing {classifier} artifact {artifact_file.name}")
                errors += 1
            if args.signed and not Path(str(artifact_file) + ".asc").is_file():
                fail(f"{pom}: missing signature for {artifact_file.name}")
                errors += 1
        if args.signed and not Path(str(pom) + ".asc").is_file():
            fail(f"{pom}: missing signature for POM")
            errors += 1

    missing_runtime = sorted(RUNTIME_ARTIFACTS - found_runtime)
    if missing_runtime:
        fail("missing runtime variants: " + ", ".join(missing_runtime))
        errors += 1
    if len(found_plugins) != 2:
        fail(f"expected 2 plugin variants, found {len(found_plugins)}: {sorted(found_plugins)}")
        errors += 1

    print(f"POMs checked: {len(poms)}")
    print(f"Runtime variants: {len(found_runtime)}/8 ({', '.join(sorted(found_runtime))})")
    print(f"Plugin variants: {len(found_plugins)}/2 ({', '.join(sorted(found_plugins))})")
    print(f"Signed artifacts required: {args.signed}")
    if errors:
        print(f"Validation failed with {errors} error(s).")
        return 1
    print("Local Maven staging validation passed; nothing was uploaded.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())