#!/usr/bin/env python3
"""Fail if a jar ships a class whose FQCN is also in one of the jars it depends on.

Usage: check_no_duplicate_classes.py <jar> <dependency-jar-or-classpath> [...]
  A dependency argument may be a single jar or a classpath string / file with jars separated by
  os.pathsep (the output of `mvn dependency:build-classpath -Dmdep.outputFile=...`).

tinyexpression #224: tinyExpression(-jdk17) used to ship its own copies of
org.unlaxer.parser.Parser / AbstractParser / ParserFinderToChild / ParserTaggable with different
content, so which copy a consumer got depended on the classpath order. module-info and
package-info are ignored (they are not loadable classes and every jar may carry its own).
"""

import os
import sys
import zipfile

IGNORED = ("module-info.class", "package-info.class")


def class_names(jar: str) -> set[str]:
    with zipfile.ZipFile(jar) as archive:
        names = set()
        for name in archive.namelist():
            if not name.endswith(".class") or name.startswith("META-INF/"):
                continue
            if name.rsplit("/", 1)[-1] in IGNORED:
                continue
            names.add(name[: -len(".class")].replace("/", "."))
        return names


def dependency_jars(argument: str) -> list[str]:
    text = argument
    if not argument.endswith(".jar") and os.path.isfile(argument):
        with open(argument, encoding="utf-8") as handle:
            text = handle.read().strip()
    return [entry for entry in text.split(os.pathsep) if entry.endswith(".jar")]


def main(argv: list[str]) -> int:
    if len(argv) < 3:
        print(__doc__, file=sys.stderr)
        return 2
    jar = argv[1]
    own = class_names(jar)
    if not own:
        print(f"{jar}: no class files", file=sys.stderr)
        return 1
    dependencies = [dep for argument in argv[2:] for dep in dependency_jars(argument)]
    if not dependencies:
        print("no dependency jars given", file=sys.stderr)
        return 2
    failed = False
    for dependency in dependencies:
        duplicates = sorted(own & class_names(dependency))
        print(f"{jar} vs {dependency}: {len(duplicates)} duplicate classes")
        if duplicates:
            failed = True
            print("\n".join(f"  {name}" for name in duplicates), file=sys.stderr)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
