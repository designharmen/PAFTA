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
  * a file that uses R.string without importing R — the app module cannot be
    compiled in the development container, so this is caught statically
  * `const val` initialised from an R field, which Kotlin rejects
  * Modifier.padding mixing `horizontal`/`vertical` with per-side arguments,
    which has no such overload
  * a reference to StoreFailure.message, a property that was deliberately
    removed so that no English prose can reach a Turkish screen
  * two strings defined under the same name — Android's resource merger rejects
    the whole build for this, four minutes in, and says so in a step whose
    output our error summary does not even capture
  * a typeface outside the Harmen Design system, or a font file referenced from
    Kotlin that is not actually in res/font — the brand guideline names Inter,
    Roboto, Montserrat and Poppins as forbidden, and a missing font file is a
    build error the development container cannot otherwise catch
  * a named argument that the function being called does not have — the app
    module cannot be compiled here, so a parameter renamed in one file and
    still passed from another is otherwise only found by CI, minutes later
  * a Compose modifier used without its import — `Modifier.padding(...)` in a
    file that never imported `padding` is four minutes of CI to learn one line
  * a `when` over one of this project's own enums that has forgotten a value —
    adding a case in `core/` and not handling it in the interface compiles
    everywhere except the module that cannot be compiled here
  * a tool that is in the `Tool` enum but on neither of the two lists the
    interface draws from — this one compiles perfectly and simply leaves the
    tool off the screen, which is worse than a build failure because nothing
    reports it

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
# Argüman denetimi çekirdeği de kapsar: arayüz oradaki işlevleri çağırıyor.
ALL_KOTLIN_DIRS = [ROOT / "app/src/main/kotlin"] + sorted(ROOT.glob("core/*/src/main/kotlin"))
XML_DIRS = [ROOT / "app/src/main"]
FONT_DIR = ROOT / "app/src/main/res/font"

# Harmen Design marka rehberi, 12 — DO / DON'T: "Sistem dışı font eklemeyin;
# Inter, Roboto, Montserrat, Poppins yasak."
FORBIDDEN_TYPEFACES = ("inter", "roboto", "montserrat", "poppins", "jetbrains")
ALLOWED_TYPEFACES = ("archivo", "plex_sans", "plex_mono")


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


def check_duplicates(raw_text: str) -> list[str]:
    """Aynı isimle iki kez tanımlanmış metin var mı."""
    problems: list[str] = []
    seen: dict[str, int] = {}

    for number, line in enumerate(raw_text.splitlines(), start=1):
        match = re.search(r'<string\s+name="([^"]+)"', line)
        if not match:
            continue
        name = match.group(1)
        if name in seen:
            problems.append(
                f"{STRINGS.name}:{number}: '{name}' ikinci kez tanımlanmış "
                f"(ilki {seen[name]}. satırda). Android aynı isimli iki metni "
                f"kabul etmez ve derlemeyi durdurur."
            )
        else:
            seen[name] = number

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


def check_kotlin_usage() -> list[str]:
    """
    Static checks for the two mistakes that cost a full CI round trip.

    The app module cannot be compiled in the development container (no Android
    SDK), so these two error classes used to surface only after four minutes on
    a runner. Both are decidable by reading the source.
    """
    problems: list[str] = []
    # R lives in the app's namespace, so only files in that exact package may
    # use it unqualified without an import.
    r_package = "com.harmen.pafta"

    for directory in KOTLIN_DIRS:
        for path in directory.rglob("*.kt"):
            text = path.read_text()
            where = path.relative_to(ROOT)

            if "R.string." in text:
                package_match = re.search(r"^package\s+([\w.]+)", text, re.M)
                package = package_match.group(1) if package_match else ""
                imported = f"import {r_package}.R" in text
                if not imported and package != r_package:
                    line = text[: text.index("R.string.")].count("\n") + 1
                    problems.append(
                        f"{where}:{line}: R.string kullanılıyor ama "
                        f"'import {r_package}.R' yok."
                    )

            # Modifier.padding has (horizontal, vertical) and
            # (start, top, end, bottom) overloads, but none that mixes them.
            for match in re.finditer(
                r"padding\(\s*(?:horizontal|vertical)\s*=[^)]*?\b(?:start|top|end|bottom)\s*=",
                text,
            ):
                line = text.count("\n", 0, match.start()) + 1
                problems.append(
                    f"{where}:{line}: padding() içinde horizontal/vertical ile "
                    f"start/top/end/bottom karıştırılmış; böyle bir aşırı yükleme yok."
                )

            # StoreFailure carries structured data only; a `.message` would be
            # English prose one step from a Turkish screen.
            for match in re.finditer(r"\bfailure\.message\b", text):
                line = text.count("\n", 0, match.start()) + 1
                problems.append(
                    f"{where}:{line}: StoreFailure.message kaldırıldı; "
                    f"UiError.Store/SaveFailed kullanın."
                )

            # R fields come from generated Java, so Kotlin does not treat them as
            # compile-time constants: `const val X = R.string.y` does not compile.
            for match in re.finditer(r"^\s*(?:\w+\s+)*const\s+val\s+(\w+)[^=]*=\s*R\.", text, re.M):
                line = text.count("\n", 0, match.start()) + 1
                problems.append(
                    f"{where}:{line}: '{match.group(1)}' bir R alanından "
                    f"`const val` yapılmış; `val` olmalı."
                )

    return problems


def check_fonts() -> list[str]:
    """Marka dışı yazı tipi ve eksik font dosyası denetimi."""
    problems: list[str] = []

    if not FONT_DIR.is_dir():
        return [f"{FONT_DIR} yok: marka yazı tipleri eksik"]

    present = {path.stem for path in FONT_DIR.glob("*.ttf")}

    for name in sorted(present):
        if any(name.startswith(bad) for bad in FORBIDDEN_TYPEFACES):
            problems.append(
                f"{FONT_DIR.name}/{name}.ttf — marka rehberi bu yazı tipini "
                f"yasaklıyor; izin verilenler: {', '.join(ALLOWED_TYPEFACES)}"
            )
        elif not any(name.startswith(ok) for ok in ALLOWED_TYPEFACES):
            problems.append(
                f"{FONT_DIR.name}/{name}.ttf — sistem dışı yazı tipi; marka "
                f"rehberi en fazla üç aileye izin veriyor"
            )

    for directory in KOTLIN_DIRS:
        for path in directory.rglob("*.kt"):
            for number, line in enumerate(path.read_text().splitlines(), start=1):
                for used in re.findall(r"R\.font\.([A-Za-z0-9_]+)", line):
                    if used not in present:
                        problems.append(
                            f"{path.relative_to(ROOT)}:{number} — R.font.{used} "
                            f"kullanılıyor ama {FONT_DIR.name}/{used}.ttf yok"
                        )
                for bad in FORBIDDEN_TYPEFACES:
                    if re.search(rf"R\.font\.{bad}", line):
                        problems.append(
                            f"{path.relative_to(ROOT)}:{number} — marka rehberinin "
                            f"yasakladığı bir yazı tipi kullanılıyor"
                        )

    return problems



def _without_comments_and_strings(text: str) -> str:
    """The same text with comments and string bodies blanked out.

    Everything keeps its position and its newlines, so reported line numbers
    stay true; only the contents that could contain a stray bracket or an `=`
    are replaced. Without this a comment mentioning `foo(bar = 1)` reads as a
    call.
    """
    out = []
    i = 0
    n = len(text)
    while i < n:
        two = text[i:i + 2]
        if two == "//":
            while i < n and text[i] != "\n":
                out.append(" ")
                i += 1
        elif two == "/*":
            while i < n and text[i:i + 2] != "*/":
                out.append("\n" if text[i] == "\n" else " ")
                i += 1
            out.append("  ")
            i += 2
        elif text[i:i + 3] == '"""':
            out.append('"""')
            i += 3
            while i < n and text[i:i + 3] != '"""':
                out.append("\n" if text[i] == "\n" else " ")
                i += 1
            out.append('"""')
            i += 3
        elif text[i] == '"':
            out.append('"')
            i += 1
            while i < n and text[i] != '"':
                if text[i] == "\\":
                    out.append("  ")
                    i += 2
                    continue
                out.append(" ")
                i += 1
            out.append('"')
            i += 1
        else:
            out.append(text[i])
            i += 1
    return "".join(out)


def _balanced(text: str, open_at: int) -> tuple[str, int]:
    """The contents of the bracket that opens at [open_at], and the index after it."""
    depth = 0
    i = open_at
    n = len(text)
    while i < n:
        c = text[i]
        if c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
            if depth == 0:
                return text[open_at + 1:i], i + 1
        i += 1
    return "", n


def _split_arguments(body: str) -> list[str]:
    """Top-level comma-separated pieces of an argument or parameter list.

    Angle brackets count as brackets so that `Map<String, Int>` stays one piece,
    but not when they are half of `->`: a lambda parameter type would otherwise
    unbalance the count and swallow every comma after it.
    """
    parts = []
    depth = 0
    current: list[str] = []
    previous = ""
    for c in body:
        if c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
        elif c == "<" and previous not in ("-", "<", "="):
            depth += 1
        elif c == ">" and previous not in ("-", "="):
            depth -= 1
        if c == "," and depth == 0:
            parts.append("".join(current))
            current = []
        else:
            current.append(c)
        previous = c
    if "".join(current).strip():
        parts.append("".join(current))
    return parts


DECLARATION = re.compile(
    r"\b(?:fun|class)\s+(?:<[^>\n]*>\s*)?(?:[\w.]+\.)?(\w+)\s*(?:<[^>\n]*>\s*)?\("
)
CALL = re.compile(r"(?<![\w.])(\w+)\s*\(")
NAMED_ARGUMENT = re.compile(r"^\s*(\w+)\s*=(?!=)")
PARAMETER_NAME = re.compile(r"(\w+)\s*:")

# `copy` belongs to whichever data class the receiver is, which needs the type
# to know; the rest are Kotlin's own and never ours.
UNCHECKABLE = {"copy", "require", "check", "listOf", "setOf", "mapOf"}

IMPORT = re.compile(r"^\s*import\s+([\w.]+)", re.MULTILINE)


# Compose modifiers and the import each one needs. Only names that are, in
# practice, never anything else: a chain continuation reading `.padding(` is a
# modifier, and `.map {` is not in this table so it is never looked at.
MODIFIER_IMPORTS = {
    # androidx.compose.foundation.layout
    "padding": "androidx.compose.foundation.layout.padding",
    "size": "androidx.compose.foundation.layout.size",
    "width": "androidx.compose.foundation.layout.width",
    "height": "androidx.compose.foundation.layout.height",
    "widthIn": "androidx.compose.foundation.layout.widthIn",
    "heightIn": "androidx.compose.foundation.layout.heightIn",
    "sizeIn": "androidx.compose.foundation.layout.sizeIn",
    "fillMaxWidth": "androidx.compose.foundation.layout.fillMaxWidth",
    "fillMaxHeight": "androidx.compose.foundation.layout.fillMaxHeight",
    "fillMaxSize": "androidx.compose.foundation.layout.fillMaxSize",
    "offset": "androidx.compose.foundation.layout.offset",
    "aspectRatio": "androidx.compose.foundation.layout.aspectRatio",
    "defaultMinSize": "androidx.compose.foundation.layout.defaultMinSize",
    "wrapContentWidth": "androidx.compose.foundation.layout.wrapContentWidth",
    "wrapContentHeight": "androidx.compose.foundation.layout.wrapContentHeight",
    # androidx.compose.foundation
    "background": "androidx.compose.foundation.background",
    "clickable": "androidx.compose.foundation.clickable",
    "border": "androidx.compose.foundation.border",
    "verticalScroll": "androidx.compose.foundation.verticalScroll",
    "horizontalScroll": "androidx.compose.foundation.horizontalScroll",
    # androidx.compose.ui.draw
    "clip": "androidx.compose.ui.draw.clip",
    "clipToBounds": "androidx.compose.ui.draw.clipToBounds",
    "alpha": "androidx.compose.ui.draw.alpha",
    "rotate": "androidx.compose.ui.draw.rotate",
    "scale": "androidx.compose.ui.draw.scale",
    "shadow": "androidx.compose.ui.draw.shadow",
    "drawBehind": "androidx.compose.ui.draw.drawBehind",
    "drawWithContent": "androidx.compose.ui.draw.drawWithContent",
    # elsewhere
    "onSizeChanged": "androidx.compose.ui.layout.onSizeChanged",
    "onGloballyPositioned": "androidx.compose.ui.layout.onGloballyPositioned",
    "pointerInput": "androidx.compose.ui.input.pointer.pointerInput",
}

CHAIN_STEP = re.compile(r"(?:\bModifier\s*)?\.(\w+)\s*\(")


ENUM_DECLARATION = re.compile(r"\benum\s+class\s+(\w+)")
WHEN_START = re.compile(r"\bwhen\s*\(")
BRANCH_LABEL = re.compile(r"(?<![\w.])(\w+)\.(\w+)\s*(?=->|,)")
ELSE_BRANCH = re.compile(r"(?<![\w.])else\s*->")


def _enum_values(text: str, at: int) -> set[str]:
    """The names of the values of the enum whose body starts after [at]."""
    opening = text.find("{", at)
    if opening < 0:
        return set()
    body, _ = _balanced(text, opening)
    # A value may carry arguments, and the body may carry members after a `;`.
    body = body.split(";", 1)[0]
    values = set()
    for piece in _split_arguments(body):
        found = re.match(r"\s*(?:@\w+\s*)*([A-Z][A-Z0-9_]*)\b", piece)
        if found:
            values.add(found.group(1))
    return values


def check_enum_branches() -> list[str]:
    """A `when` over one of PAFTA's own enums must name every one of its values.

    This is the compiler's own rule, checked here because the module it keeps
    failing in — the interface — cannot be compiled in the development
    container. Adding a value in `core/` and forgetting the interface compiles
    the core, passes every test, and then stops CI four minutes later.

    Only a `when` whose branches are all written as `Enum.VALUE` is looked at,
    and only when it has no `else`. A `when` with an `else` has said what it
    wants to do about the rest.
    """
    sources = []
    for directory in ALL_KOTLIN_DIRS:
        if directory.is_dir():
            sources.extend(sorted(directory.rglob("*.kt")))

    cleaned = {path: _without_comments_and_strings(path.read_text()) for path in sources}

    values: dict[str, set[str]] = {}
    for text in cleaned.values():
        for match in ENUM_DECLARATION.finditer(text):
            found = _enum_values(text, match.end())
            if found:
                values.setdefault(match.group(1), set()).update(found)

    problems = []
    for path, text in cleaned.items():
        for match in WHEN_START.finditer(text):
            opening = text.find("{", match.end())
            if opening < 0:
                continue
            body, _ = _balanced(text, opening)
            if ELSE_BRANCH.search(body):
                continue

            named: dict[str, set[str]] = {}
            for label in BRANCH_LABEL.finditer(body):
                owner, value = label.group(1), label.group(2)
                if owner in values and value in values[owner]:
                    named.setdefault(owner, set()).add(value)

            # Exactly one enum, or there is no single set of values to be
            # complete about.
            if len(named) != 1:
                continue
            enum, used = next(iter(named.items()))
            missing = values[enum] - used
            if not missing:
                continue

            line = text.count("\n", 0, match.start()) + 1
            problems.append(
                f"{path.relative_to(ROOT)}:{line} — {enum} üzerindeki `when` "
                f"eksik: {', '.join(sorted(missing))} yok (ve `else` de yok)"
            )
    return problems


def check_modifier_imports() -> list[str]:
    """Every Compose modifier used must be imported.

    A modifier is an extension function, so a file that uses one it never
    imported does not fail until the Android module is compiled — which cannot
    happen in the development container, so it fails on CI four minutes later.
    That is what this costs a build for: two lines of `.padding(...)` in a file
    whose imports had everything else.

    Only the names in [MODIFIER_IMPORTS] are looked at, and every one of them is
    a word this project uses for nothing else, so an ordinary chain like
    `.map { }` is never mistaken for a modifier.
    """
    problems = []
    for directory in KOTLIN_DIRS:
        if not directory.is_dir():
            continue
        for path in sorted(directory.rglob("*.kt")):
            text = _without_comments_and_strings(path.read_text())
            imported = {match.group(1) for match in IMPORT.finditer(text)}

            for match in CHAIN_STEP.finditer(text):
                name = match.group(1)
                needed = MODIFIER_IMPORTS.get(name)
                if needed is None or needed in imported:
                    continue
                line = text.count("\n", 0, match.start()) + 1
                problems.append(
                    f"{path.relative_to(ROOT)}:{line} — {name}() kullanılmış ama "
                    f"`import {needed}` yok"
                )
    return problems


def check_named_arguments() -> list[str]:
    """Named arguments must be parameters the called function actually has.

    This is the mistake that cost a build: a property read off the wrong type,
    and an argument passed to a function that had no such parameter. The app
    module needs the Android SDK to compile and the development container does
    not have it, so nothing else catches this until CI does, minutes later.

    Deliberately conservative. Only functions and constructors declared in this
    repository are checked, and where a name is declared more than once every
    declaration's parameters are accepted, so an overload can never be reported
    as a mistake. It finds the wrong name, not the wrong type.
    """
    sources = []
    for directory in ALL_KOTLIN_DIRS:
        if directory.is_dir():
            sources.extend(sorted(directory.rglob("*.kt")))

    cleaned = {path: _without_comments_and_strings(path.read_text()) for path in sources}

    # A name imported from somewhere OTHER than PAFTA is left alone: PAFTA has
    # its own `DxfEntity.Text`, Compose has `Text`, and there is no way to tell
    # from the text of a call which one is meant. PAFTA's own imports are not
    # borrowed — those are exactly the calls worth checking.
    borrowed = set()
    for text in cleaned.values():
        for match in IMPORT.finditer(text):
            imported = match.group(1)
            if not imported.startswith("com.harmen.pafta."):
                borrowed.add(imported.rsplit(".", 1)[-1])

    parameters: dict[str, set[str]] = {}
    for text in cleaned.values():
        for match in DECLARATION.finditer(text):
            body, _ = _balanced(text, match.end() - 1)
            names = set()
            for piece in _split_arguments(body):
                found = PARAMETER_NAME.search(piece)
                if found:
                    names.add(found.group(1))
            parameters.setdefault(match.group(1), set()).update(names)

    problems = []
    for path, text in cleaned.items():
        for match in CALL.finditer(text):
            name = match.group(1)
            if name in UNCHECKABLE or name in borrowed or name not in parameters:
                continue
            body, _ = _balanced(text, match.end() - 1)
            for piece in _split_arguments(body):
                named = NAMED_ARGUMENT.match(piece)
                if not named:
                    continue
                argument = named.group(1)
                if argument not in parameters[name]:
                    line = text.count("\n", 0, match.start()) + 1
                    problems.append(
                        f"{path.relative_to(ROOT)}:{line} — {name}() çağrısında "
                        f"'{argument}' diye bir argüman yok "
                        f"(olanlar: {', '.join(sorted(parameters[name])) or 'yok'})"
                    )
    return problems


def check_tool_placement() -> list[str]:
    """Every tool must be somewhere the user can reach it.

    PAFTA draws its tools from two lists — `TOOLBAR_TOOLS` along the top and
    `ELEMENT_TOOLS` down the right — rather than from the enum itself, because
    the two rows are ordered differently from each other. The cost of that is
    that adding a value to the enum and forgetting the list compiles cleanly and
    puts the tool nowhere. There is no error, no test failure and nothing on the
    screen: the tool simply does not exist. So it is checked here.
    """
    source = ROOT / "app/src/main/kotlin/com/harmen/pafta/ui/state/EditorState.kt"
    if not source.exists():
        return [f"{source.name} bulunamadı"]

    text = _without_comments_and_strings(source.read_text())

    at = text.find("enum class Tool")
    if at < 0:
        return [f"{source.name}: Tool listesi bulunamadı"]
    values = _enum_values(text, at)
    if not values:
        return [f"{source.name}: Tool listesi okunamadı"]

    placed: set[str] = set()
    for name in ("TOOLBAR_TOOLS", "ELEMENT_TOOLS"):
        start = text.find(f"val {name}")
        if start < 0:
            return [f"{source.name}: {name} bulunamadı"]
        opening = text.find("(", start)
        if opening < 0:
            return [f"{source.name}: {name} okunamadı"]
        body, _ = _balanced(text, opening)
        placed |= set(re.findall(r"Tool\.([A-Z][A-Z0-9_]*)", body))

    missing = sorted(values - placed)
    if missing:
        return [
            f"{source.name}: bu araç(lar) hiçbir araç çubuğunda yok, yani "
            f"ekranda görünmeyecek: {', '.join(missing)}"
        ]

    stray = sorted(placed - values)
    if stray:
        return [f"{source.name}: araç çubuğunda tanımsız araç var: {', '.join(stray)}"]

    return []


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

    problems = (
        check_duplicates(raw)
        + check_escaping(raw)
        + check_references(defined)
        + check_kotlin_usage()
        + check_fonts()
        + check_named_arguments()
        + check_modifier_imports()
        + check_enum_branches()
        + check_tool_placement()
    )

    if problems:
        for problem in problems:
            fail(problem)
        return 1

    kotlin_files = sum(len(list(d.rglob("*.kt"))) for d in KOTLIN_DIRS)
    fonts = len(list(FONT_DIR.glob("*.ttf"))) if FONT_DIR.is_dir() else 0
    print(
        f"tamam: {len(defined)} Türkçe metin, {kotlin_files} Kotlin dosyası ve "
        f"{fonts} yazı tipi denetlendi, sorun yok"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
