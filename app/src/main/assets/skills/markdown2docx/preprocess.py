#!/usr/bin/env python3
"""
Reusable markdown preprocessing for the docx conversion engine.

This module handles markdown syntax that the engine's parser doesn't support:
HTML tables, <p>/<br> tags, [text](url) links, # single-level headings,
Chinese number prefixes that would duplicate template auto-numbering.

Import this from your conversion script:
    from preprocess import preprocess
    processed = preprocess(original_markdown_text)
"""

import re


def html_table_to_md(text):
    """Convert HTML <table>...</table> to markdown pipe table, expanding colspans."""

    def _parse_tr(tr_content):
        cells = []
        for m in re.finditer(r'<(td|th)\b([^>]*)>(.*?)</\1>', tr_content, re.DOTALL | re.IGNORECASE):
            attrs = m.group(2)
            content = m.group(3)
            colspan = 1
            cm = re.search(r'colspan\s*=\s*["\']?(\d+)', attrs, re.IGNORECASE)
            if cm:
                colspan = int(cm.group(1))
            clean = re.sub(r'<[^>]+>', '', content).strip()
            cells.append((clean, colspan))
        expanded = []
        for cell_text, cs in cells:
            expanded.append(cell_text)
            for _ in range(cs - 1):
                expanded.append('')
        return expanded

    def _process_table(match):
        table_html = match.group(0)
        rows = []
        for tr_m in re.finditer(r'<tr[^>]*>(.*?)</tr>', table_html, re.DOTALL | re.IGNORECASE):
            rows.append(_parse_tr(tr_m.group(1)))
        if not rows:
            return ''
        max_cols = max(len(r) for r in rows)
        for r in rows:
            while len(r) < max_cols:
                r.append('')
        md_lines = []
        md_lines.append('| ' + ' | '.join(rows[0]) + ' |')
        md_lines.append('| ' + ' | '.join(['---'] * max_cols) + ' |')
        for r in rows[1:]:
            md_lines.append('| ' + ' | '.join(r) + ' |')
        return '\n'.join(md_lines) + '\n'

    return re.sub(r'<table>.*?</table>', _process_table, text, flags=re.DOTALL | re.IGNORECASE)


def preprocess(text):
    """Apply all preprocessing transformations to a markdown string.

    Pipeline
    --------
    1. HTML <table> → markdown pipe table (colspan expanded)
    2. <p> tags → unwrapped text
    3. <br> tags → removed
    4. "步骤N：" prefixes stripped from headings
    5. [text](url) → text only
    6. TOC section removed (between the two --- separators)
    7. Cover page headings stripped to plain paragraphs
    8. # heading after --- → ## chapter heading, or plain paragraph
       if it's the document title (appears before any ##/### sections)
    9. ## [一二三…] sections demoted to ### (sections, not chapters)
    10. Chinese number prefixes stripped from all headings

    The function expects markdown with this structure
        [cover page content]
        ---
        [TOC / auxiliary content]
        ---
        [body content: # title, ## sections, ### subsections, …]

    Parameters
    ----------
    text : str
        Raw markdown source.

    Returns
    -------
    str
        Preprocessed markdown ready for the conversion engine.
    """
    # ── 1. HTML tables ────────────────────────────────────────────
    text = html_table_to_md(text)

    # ── 2. <p> tags → text ────────────────────────────────────────
    text = re.sub(r'<p[^>]*>(.*?)</p>', r'\1', text, flags=re.DOTALL)

    # ── 3. <br> → remove ──────────────────────────────────────────
    text = re.sub(r'<br\s*/?>', '', text, flags=re.IGNORECASE)

    # ── 4. Strip "步骤N：" from headings ───────────────────────────
    text = re.sub(r'^(#{2,4} )步骤\d+[：:]\s*', r'\1', text, flags=re.MULTILINE)

    # ── 5. Strip [text](url) → text ────────────────────────────────
    text = re.sub(r'\[([^\]]+)\]\([^)]+\)', r'\1', text)

    # ── 6. Section-based processing ────────────────────────────────
    lines = text.split('\n')
    first_hr = next((i for i, l in enumerate(lines) if l.strip() == '---'), len(lines))
    second_hr = next((i for i, l in enumerate(lines) if l.strip() == '---' and i > first_hr), len(lines))

    # Remove TOC section (Word auto-TOC handles this)
    if second_hr > first_hr:
        lines = lines[:first_hr + 1] + lines[second_hr:]

    for i, line in enumerate(lines):
        # 7. # heading → ## / plain paragraph
        if re.match(r'^# (?!#)', line):
            if i < first_hr:
                # Before first --- → cover page, plain paragraph
                lines[i] = line[2:]
            else:
                # After ---: if this is the first heading (no ##/### before it),
                # it's a document title → plain paragraph. Otherwise → ## chapter.
                prev_since_sep = lines[first_hr + 1:i]
                has_section = any(re.match(r'^(##|###) ', l) for l in prev_since_sep)
                if has_section:
                    lines[i] = '## ' + line[2:]
                else:
                    lines[i] = line[2:]

        # 8. Demote ## Chinese-numbered sections → ###
        if re.match(r'^## [一二三四五六七八九十]+、', lines[i]):
            lines[i] = '### ' + lines[i][3:]

    text = '\n'.join(lines)

    # ── 9. Strip Chinese numbered prefixes from headings ──────────
    text = re.sub(r'^(#{2,4} )[一二三四五六七八九十]+、\s*', r'\1', text, flags=re.MULTILINE)

    return text
