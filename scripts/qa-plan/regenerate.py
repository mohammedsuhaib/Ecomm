#!/usr/bin/env python3
"""Regenerate the QA worksheet page from TEST_CASES.md.

TEST_CASES.md is canonical; the published QA artifact (an interactive worksheet
with per-case checkboxes) is derived. This script re-parses the markdown and
splices the data-bearing parts into template.html:

  - the masthead stats (case/priority/module counts)
  - the module rail (labels + per-module counts)
  - the "Release gate (N)" chip count
  - the embedded `const DATA = {modules, signoff}` blob the page renders from

template.html is a verbatim copy of the published page, so everything else —
styles, filter JS, the "Before you start" / known-gaps / sign-off panels — is
carried through unchanged. LIMITATION: those three panels are static here; if
sections 1, "Known gaps" or "Release sign-off" change in TEST_CASES.md, update
the template by hand (or republish and re-copy).

Usage: python3 scripts/qa-plan/regenerate.py <output.html>
"""

import html
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
SRC = ROOT / "TEST_CASES.md"
TEMPLATE = Path(__file__).resolve().parent / "template.html"


def inline(md: str) -> str:
    """Markdown inline -> HTML: `code` spans, **bold**, everything escaped."""
    out = []
    for part in re.split(r"(`[^`]+`)", md):
        if part.startswith("`") and part.endswith("`") and len(part) > 1:
            out.append(f"<code>{html.escape(part[1:-1])}</code>")
        else:
            esc = html.escape(part)
            esc = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", esc)
            out.append(esc)
    return "".join(out)


def slugify(heading: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", heading.lower()).strip("-")


def label_of(heading: str) -> str:
    """Heading minus the leading number and a trailing (`module`) parenthetical."""
    label = re.sub(r"^\d+\.\s*", "", heading)
    return re.sub(r"\s*\([^()]*\)\s*$", "", label).strip()


def parse():
    modules, signoff = [], []
    current = None
    section_heading = ""
    for line in SRC.read_text(encoding="utf-8").splitlines():
        m = re.match(r"^##\s+(.*)$", line)
        if m:
            section_heading = m.group(1).strip()
            current = None
            continue
        row = re.match(r"^\|\s*(TC-[A-Z0-9-]+[a-z]?)\s*\|", line)
        if row and section_heading:
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            if len(cells) < 6:
                continue
            if current is None or current["heading"] != section_heading:
                current = {"heading": section_heading,
                           "slug": slugify(section_heading),
                           "label": label_of(section_heading),
                           "cases": []}
                modules.append(current)
            current["cases"].append({
                "id": cells[0],
                "type": cells[1],
                "scenario": inline(cells[2]),
                "steps": inline(cells[3]),
                "expected": inline(cells[4]),
                "pri": cells[5],
            })
        if "sign-off" in section_heading.lower():
            for tc in re.findall(r"TC-[A-Z0-9]+-[0-9]+[a-z]?", line):
                if tc not in signoff:
                    signoff.append(tc)
    return modules, signoff


def main(out_path: str) -> None:
    modules, signoff = parse()
    cases = [c for m in modules for c in m["cases"]]
    pri = {p: sum(1 for c in cases if c["pri"] == p) for p in ("P1", "P2", "P3")}

    data = {"modules": [{"slug": m["slug"], "label": m["label"], "cases": m["cases"]}
                        for m in modules],
            "signoff": signoff}

    nav = "".join(
        f'<a class="rail-item" href="#{m["slug"]}" data-rail="{m["slug"]}">'
        f'<span class="rail-label">{html.escape(m["label"])}</span>'
        f'<span class="rail-count" data-railcount="{m["slug"]}">{len(m["cases"])}</span></a>'
        for m in modules)

    stats = (f'<span class="s-tot"><b>{len(cases)}</b> cases</span>\n'
             f'      <span class="s-p1"><b>{pri["P1"]}</b> P1</span>\n'
             f'      <span><b>{pri["P2"]}</b> P2</span>\n'
             f'      <span><b>{pri["P3"]}</b> P3</span>\n'
             f'      <span><b>{len(modules)}</b> modules</span>')

    page = TEMPLATE.read_text(encoding="utf-8")
    page, n1 = re.subn(r'<span class="s-tot">.*?</b> modules</span>', stats,
                       page, count=1, flags=re.S)
    page, n2 = re.subn(r'(<nav class="rail" aria-label="Modules">).*?(</nav>)',
                       lambda mm: mm.group(1) + nav + mm.group(2),
                       page, count=1, flags=re.S)
    page, n3 = re.subn(r'(id="gate" aria-pressed="false">Release gate \()\d+(\))',
                       lambda mm: mm.group(1) + str(len(signoff)) + mm.group(2),
                       page, count=1)
    blob = "const DATA = " + json.dumps(data, ensure_ascii=False) + ";"
    page, n4 = re.subn(r'const DATA = \{.*?\};', lambda mm: blob,
                       page, count=1, flags=re.S)
    assert (n1, n2, n3, n4) == (1, 1, 1, 1), f"template drift: {(n1, n2, n3, n4)}"

    Path(out_path).write_text(page, encoding="utf-8")
    print(f"{len(cases)} cases / {len(modules)} modules / {len(signoff)} gate ids "
          f"-> {out_path} ({len(page.encode('utf-8'))} bytes)")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "qa-test-plan.html")
