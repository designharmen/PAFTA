#!/usr/bin/env python3
"""
Android metin kaynaklarını derlemeden önce denetler.

Checks the Android string resources before a build reaches aapt.

Android's resource compiler rejects a few things that ordinary XML accepts, and
it does so four minutes into a CI run. This catches them in a second:

  * an unescaped apostrophe      — aapt: "Invalid unicode escape sequence"
  * an unescaped double quote
  * a leading @ or ? (resource reference syntax)
  * a string id used from Kotlin that is not defined
  * more than one format argument without positional %1$s markers

Exits non-zero and prints the file and line on the first real problem found.
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STRINGS = ROOT / "app/src/main/res/values/strings.xml"
KOTLIN_DIRS = [ROOT / "app/src/main/kotlin"]
XML_DIRS = [ROOT / "app/src/main"]


def fail(message: str) -> None:
    print(f"HATA: {message}", file=sys.stderr)


def check_escaping(raw_text: str) -> list[str]:
    """Scans the raw file so escaping is judged before the XML parser eats it."""
    problems: list[str] = []
    pattern = re.compile(r'<string name="([^"]+)"\s*>(.*?)</string>', re.S)

    for match in pattern.finditer(raw_text):
        name = match.group(1)
        value = match.group(2)
        line = raw_text.count("\n", 0, match.start()) + 1

        # An apostrophe must be \' — aapt reports this as an invalid escape.
        for hit in re.finditer(r"'", value):
            if hit.start() == 0 or value[hit.start() - 1] != "\\":
                problems.append(
                    f"{STRINGS.name}:{line}: '{name}' içinde kaçırılmamış kesme "
                    f"işareti var. \\' olarak yazın."
                )
                break

        for hit in re.finditer(r'"', value):
            if hit.start() == 0 or value[hit.start() - 1] != "\\":
                problems.append(
                    f'{STRINGS.name}:{line}: \'{name}\' içinde kaçırılmamış çift '
                    f'tırnak var. \\" olarak yazın.'
                )
                break

        stripped = value.strip()
        if stripped[:1] in ("@", "?"):
            problems.append(
                f"{STRINGS.name}:{line}: '{name}' {stripped[0]} ile başlıyor; "
                f"Android bunu kaynak başvurusu sanar. \\{stripped[0]} yazın."
            )

        # Two or more arguments must be positional, or translation reorders break.
        args = re.findall(r"%(\d+\$)?[sdf]", value)
        if len(args) > 1 and any(a == "" for a in args):
            problems.append(
                f"{STRINGS.name}:{line}: '{name}' birden fazla değer alıyor ama "
                f"sırasız; %1$s, %2$s biçimini kullanın."
            )

    return problems


def check_references(defined: set[str]) -> list[str]:
    problems: list[str] = []
    used: dict[str, str] = {}

    for directory in KOTLIN_DIRS:
        for path in directory.rglob("*.kt"):
            for name in re.findall(r"R\.string\.(\w+)", path.read_text()):
                used.setdefault(name, str(path.relative_to(ROOT)))

    for directory in XML_DIRS:
        for path in directory.rglob("*.xml"):
            if path == STRINGS:
                continue
            for name in re.findall(r"@string/(\w+)", path.read_text()):
                used.setdefault(name, str(path.relative_to(ROOT)))

    for name, where in sorted(used.items()):
        if name not in defined:
            problems.append(f"{where}: R.string.{name} tanımlı değil.")

    unused = sorted(defined - set(used))
    if unused:
        # Not a failure: a string may be staged for the next phase.
        print(f"not: henüz kullanılmayan metin(ler): {', '.join(unused)}")

    return problems


def main() -> int:
    if not STRINGS.exists():
        fail(f"{STRINGS} bulunamadı")
        return 1

    raw = STRINGS.read_text()

    try:
        tree = ET.fromstring(raw)
    except ET.ParseError as error:
        fail(f"{STRINGS.name} geçerli XML değil: {error}")
        return 1

    defined = {
        element.get("name")
        for element in tree.findall("string")
        if element.get("name")
    }

    problems = check_escaping(raw) + check_references(defined)

    if problems:
        for problem in problems:
            fail(problem)
        return 1

    print(f"tamam: {len(defined)} Türkçe metin denetlendi, sorun yok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
