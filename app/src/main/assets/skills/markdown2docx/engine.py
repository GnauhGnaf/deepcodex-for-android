#!/usr/bin/env python3
"""
Markdown → DOCX conversion engine.
Parameterized — all template-specific values are passed in via ConversionConfig.
Never edit this file per-template; create a new Config each time instead.
"""

import os, sys, re, shutil, hashlib, zipfile
from pathlib import Path
from dataclasses import dataclass, field
from typing import Optional
from PIL import Image


# ═══════════════════════════════════════════════════════════════
# Config — all template-specific values in ONE place
# ═══════════════════════════════════════════════════════════════

@dataclass
class ConversionConfig:
    """Every value that can differ between templates."""

    # ── Paths ──
    template_path: Path
    md_path: Path
    figures_dir: Path
    output_path: Path

    # ── Spacing (must match template styles.xml) ──
    heading_spacing: dict = field(default_factory=lambda: {
        '1': '<w:spacing w:before="936" w:after="156"/>',
        '2': '<w:spacing w:before="468" w:after="93"/>',
        '3': '<w:spacing w:before="312" w:after="62"/>',
    })
    heading_centered: dict = field(default_factory=lambda: {'h1': True, 'h2': False, 'h3': False})
    body_spacing: str = '<w:spacing w:before="62" w:after="62"/><w:ind w:firstLine="480"/>'

    # ── Style IDs (as named in template styles.xml) ──
    heading_style_ids: dict = field(default_factory=lambda: {'h1': '1', 'h2': '2', 'h3': '3'})
    body_style_id: str = 'a0'
    centered_style_id: str = 'a0'
    table_style_id: str = 'a8'

    # ── Font defaults (applied as inline rPr; empty = inherit from style) ──
    # Heading font/size per level ('h1'/'h2'/'h3'); empty string or 0 = no override
    heading_font_eastasia: dict = field(default_factory=lambda: {'h1': '黑体', 'h2': '宋体', 'h3': '宋体'})
    heading_font_ascii: dict = field(default_factory=lambda: {'h1': 'Times New Roman', 'h2': 'Times New Roman', 'h3': 'Times New Roman'})
    heading_font_hAnsi: dict = field(default_factory=lambda: {'h1': 'Times New Roman', 'h2': 'Times New Roman', 'h3': 'Times New Roman'})
    heading_font_size: dict = field(default_factory=lambda: {'h1': 28, 'h2': 24, 'h3': 24})  # half-pts: 28=四号, 24=小四
    # Body font
    body_font_eastasia: str = '宋体'
    body_font_ascii: str = 'Times New Roman'
    body_font_hAnsi: str = 'Times New Roman'
    body_font_size: int = 24  # half-pts: 24 = 12pt = 小四

    # ── Numbering ──
    heading_num_id: int = 1
    heading_abstract_num_id: int = 3
    start_chapter: Optional[int] = None  # e.g. 6 → headings start at "第6章"
    ordered_list_base_num_id: int = 10   # first ordered list group gets this numId
    strip_heading_numbers: bool = True   # strip "1 ", "2.1 " from headings (turn off when template has no numPr)

    # ── Font replacements (key → replacement in styles.xml) ──
    font_replacements: dict = field(default_factory=lambda: {
        'w:ascii="Arial"': 'w:ascii="Times New Roman"',
        'w:hAnsi="Arial"': 'w:hAnsi="Times New Roman"',
    })

    # ── Image ──
    max_img_width_cm: float = 14.0

    # ── Table defaults (used when template has no table examples) ──
    table_total_width_dxa: int = 8850
    table_header_top_border: int = 12
    table_header_bottom_border: int = 6
    table_first_data_top_border: int = 6
    table_last_row_bottom_border: int = 12
    table_vertical_border: int = 6
    table_cell_font_ascii: str = ''
    table_cell_font_eastasia: str = ''
    table_header_color: str = 'auto'
    table_body_color: str = 'auto'
    table_cell_before: int = 93
    table_cell_after: int = 93

    # ── Code block ──
    code_font_size: int = 20  # half-points (20 = 10pt)


# ═══════════════════════════════════════════════════════════════
# Engine internals (generic, no template-specific values)
# ═══════════════════════════════════════════════════════════════

EMU_PER_CM = 360000
WORK_DIR_NAME = 'docx_work'


def emu(cm):
    return int(cm * EMU_PER_CM)


def escape_xml(s):
    return s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('"', '&quot;').replace("'", '&apos;')


def _encode_opc_part_name(filename):
    """URL-encode spaces and special chars in OPC PartNames per RFC 3986."""
    return filename.replace(' ', '%20').replace('#', '%23')


# ── Config extraction helper ───────────────────────────────────

def extract_config_from_template(template_path: Path) -> ConversionConfig:
    """Read template styles.xml, numbering.xml, and document.xml to auto-populate a Config.
    Returns a Config with template-matched values. Caller should still set
    start_chapter, paths, and review spacing values."""
    cfg = ConversionConfig(template_path=template_path, md_path=Path('.'), figures_dir=Path('.'), output_path=Path('.'))
    _detect_styles_from_template(cfg)
    _fill_spacing_from_template(cfg)
    return cfg


def _detect_styles_from_template(cfg: ConversionConfig):
    """Auto-detect table style and body style from the template's document.xml."""
    import zipfile as _zf
    with _zf.ZipFile(cfg.template_path, 'r') as z:
        doc_xml = z.read('word/document.xml').decode('utf-8')

    import re as _re

    # Detect table style from first table that uses w:tblStyle
    tbl_style_m = _re.search(r'<w:tblStyle w:val="([^"]+)"', doc_xml)
    if tbl_style_m:
        cfg.table_style_id = tbl_style_m.group(1)
        print(f"  Detected table style: {cfg.table_style_id}")
    else:
        print(f"  No tblStyle found in template, using default: {cfg.table_style_id}")

    # Detect body style: count explicit pStyle usage AND no-style (which inherits Normal/a0)
    style_counts = {}
    total_paras = 0
    for p in _re.findall(r'<w:p[ >].*?</w:p>', doc_xml, _re.DOTALL):
        total_paras += 1
        m = _re.search(r'<w:pStyle w:val="([^"]+)"', p)
        sid = m.group(1) if m else '(default)'
        if sid not in ('1', '2', '3'):  # skip heading styles
            style_counts[sid] = style_counts.get(sid, 0) + 1
    if style_counts:
        best = max(style_counts, key=style_counts.get)
        # Map '(default)' → 'a0' (Normal), otherwise use detected style
        cfg.body_style_id = 'a0' if best == '(default)' else best
        cfg.centered_style_id = cfg.body_style_id
        print(f"  Detected body style: {cfg.body_style_id} ({style_counts[best]}/{total_paras} paragraphs)")
    else:
        print(f"  No body paragraphs found, using default body style: {cfg.body_style_id}")

    # Detect heading centering: check if heading 1 paragraphs have jc="center"
    h1_paras = _re.findall(r'<w:pStyle w:val="1".*?</w:p>', doc_xml, _re.DOTALL)
    if h1_paras:
        centered_count = sum(1 for p in h1_paras if _re.search(r'<w:jc w:val="center"', p))
        if centered_count > len(h1_paras) // 2:
            cfg.heading_centered['h1'] = True
            print(f"  Detected heading 1 centering: True ({centered_count}/{len(h1_paras)})")
    h2_paras = _re.findall(r'<w:pStyle w:val="2".*?</w:p>', doc_xml, _re.DOTALL)
    if h2_paras:
        centered_count = sum(1 for p in h2_paras if _re.search(r'<w:jc w:val="center"', p))
        if centered_count > len(h2_paras) // 2:
            cfg.heading_centered['h2'] = True
            print(f"  Detected heading 2 centering: True ({centered_count}/{len(h2_paras)})")


def _fill_spacing_from_template(cfg: ConversionConfig):
    """Read actual paragraph spacing from template's document.xml sample paragraphs.
    Styles.xml defines defaults, but the template's real paragraphs use inline overrides.
    We extract from document.xml to match the effective spacing."""
    import zipfile as _zf
    with _zf.ZipFile(cfg.template_path, 'r') as z:
        doc_xml = z.read('word/document.xml').decode('utf-8')

    # Find a sample paragraph for each style and extract its inline spacing
    for style_id in list(cfg.heading_style_ids.values()) + [cfg.body_style_id]:
        spacing_xml, first_line = _extract_inline_spacing(doc_xml, style_id)
        if spacing_xml is not None:
            if style_id in cfg.heading_style_ids.values():
                cfg.heading_spacing[style_id] = spacing_xml
            elif style_id == cfg.body_style_id:
                indent = f'<w:ind w:firstLine="{first_line}"/>' if first_line else ''
                cfg.body_spacing = spacing_xml + indent
        else:
            if style_id == cfg.body_style_id:
                # Fallback: extract spacing from an unstyled body paragraph
                spacing_xml, first_line = _extract_spacing_from_default_para(doc_xml)
                if spacing_xml is not None:
                    indent = f'<w:ind w:firstLine="{first_line}"/>' if first_line else ''
                    cfg.body_spacing = spacing_xml + indent
                    print(f"  Body spacing extracted from default paragraph: {spacing_xml}")


def _extract_spacing_from_default_para(doc_xml):
    """Extract spacing from a representative body paragraph (has text, not centered, not empty)."""
    import re as _re
    for p in _re.findall(r'<w:p[ >].*?</w:p>', doc_xml, _re.DOTALL):
        if _re.search(r'<w:pStyle', p):
            continue
        if _re.search(r'<w:numPr>', p):
            continue
        if _re.search(r'<w:jc w:val="center"', p):
            continue
        # Skip empty paragraphs (no run content)
        if not _re.search(r'<w:t[ >]', p):
            continue
        spacing_m = _re.search(r'<w:spacing[^>]*/>', p)
        spacing_xml = spacing_m.group(0) if spacing_m else '<w:spacing w:before="0" w:after="0"/>'
        fl_m = _re.search(r'w:firstLine="(\d+)"', p)
        first_line = fl_m.group(1) if fl_m else None
        return spacing_xml, first_line
    return None, None


def _extract_inline_spacing(doc_xml, style_id):
    """Find the first paragraph using style_id in document.xml and return (spacing_xml, first_line).
    spacing_xml is a complete <w:spacing .../> element string.
    Returns (None, None) if no sample paragraph found."""
    import re as _re
    # Find <w:p> that uses w:pStyle w:val="style_id" and extract inline w:spacing
    pattern = rf'<w:pStyle w:val="{style_id}".*?</w:p>'
    m = _re.search(pattern, doc_xml, _re.DOTALL)
    if not m:
        print(f"  WARNING: no sample paragraph found for style '{style_id}' in template")
        return (None, None)

    chunk = m.group(0)
    # Extract the full <w:spacing .../> element if present
    spacing_m = _re.search(r'<w:spacing[^>]*/>', chunk)
    if spacing_m:
        spacing_xml = spacing_m.group(0)
    else:
        spacing_xml = '<w:spacing w:before="0" w:after="0"/>'

    fl_m = _re.search(r'w:firstLine="(\d+)"', chunk)
    first_line = fl_m.group(1) if fl_m else None
    return spacing_xml, first_line


# ── 1. Unpack ──────────────────────────────────────────────────

def unpack_template(template_path, work_dir):
    if work_dir.exists():
        shutil.rmtree(work_dir)
    work_dir.mkdir(parents=True)
    with zipfile.ZipFile(template_path, 'r') as z:
        z.extractall(work_dir)


# ── 2. Markdown parser ─────────────────────────────────────────

def split_table_cells(line):
    placeholder = '\x00ESCPIPE\x00'
    cleaned = line.strip().replace('\\|', placeholder)
    return [c.strip().replace(placeholder, '|') for c in cleaned.split('|')[1:-1]]


def parse_md(md_path):
    text = md_path.read_text(encoding='utf-8')
    lines = text.split('\n')
    elements = []
    i = 0
    in_code_block = False
    code_lines = []

    while i < len(lines):
        line = lines[i]

        if line.strip().startswith('```'):
            if in_code_block:
                elements.append(('code_block', '\n'.join(code_lines)))
                code_lines = []
                in_code_block = False
            else:
                in_code_block = True
            i += 1
            continue

        if in_code_block:
            code_lines.append(line)
            i += 1
            continue

        if line.strip().startswith('|') and line.strip().endswith('|'):
            cells = split_table_cells(line)
            if i + 1 < len(lines) and re.match(r'^\|[\s\-:|]+\|$', lines[i + 1].strip()):
                table_rows = [cells]
                i += 2
                while i < len(lines) and lines[i].strip().startswith('|') and lines[i].strip().endswith('|'):
                    table_rows.append(split_table_cells(lines[i]))
                    i += 1
                elements.append(('table', table_rows))
                continue

        if line.startswith('#### '):
            elements.append(('heading3', line[5:].strip()))
        elif line.startswith('### '):
            elements.append(('heading2', line[4:].strip()))
        elif line.startswith('## '):
            elements.append(('heading1', line[3:].strip()))
        elif line.startswith('![') and '](' in line:
            m = re.match(r'!\[(.*?)\]\((.*?)\)', line)
            if m:
                elements.append(('image', m.group(1), m.group(2)))
        elif line.strip() == '---':
            elements.append(('hr',))
        elif line.strip().startswith('- '):
            text_content = line.strip()[2:]
            list_lines = [text_content]
            j = i + 1
            while j < len(lines) and lines[j].strip() and not lines[j].startswith('#') and not lines[j].startswith('!') and not lines[j].startswith('|') and not lines[j].startswith('```') and not lines[j].startswith('---') and not lines[j].startswith('- '):
                if lines[j].startswith('  ') or lines[j].startswith('\t'):
                    list_lines.append(lines[j].strip())
                else:
                    break
                j += 1
            i = j - 1
            elements.append(('list_item', '\n'.join(list_lines)))
        elif re.match(r'^\d+\.\s+', line.strip()) or re.match(r'^\*\*（\d+）', line.strip()):
            def _strip_ol_prefix(t):
                if t.startswith('**（'):
                    return re.sub(r'^\*\*（\d+）', '**', t)
                else:
                    return re.sub(r'^\d+\.\s+', '', t)
            text = _strip_ol_prefix(line.strip())
            ol_lines = [text]
            j = i + 1
            while j < len(lines):
                t = lines[j].strip()
                if not t:
                    j += 1
                    continue
                if t.startswith('#') or t.startswith('!') or t.startswith('|') or t.startswith('```') or t.startswith('---') or t.startswith('- '):
                    break
                if re.match(r'^\d+\.\s+', t) or re.match(r'^\*\*（\d+）', t):
                    ol_lines.append(_strip_ol_prefix(t))
                    j += 1
                else:
                    break
            i = j - 1
            elements.append(('ordered_list', ol_lines))
        elif line.strip():
            para_lines = [line.strip()]
            j = i + 1
            while j < len(lines) and lines[j].strip() and not lines[j].startswith('#') and not lines[j].startswith('!') and not lines[j].startswith('|') and not lines[j].startswith('```') and not lines[j].startswith('---') and not lines[j].startswith('- '):
                para_lines.append(lines[j].strip())
                j += 1
            i = j - 1
            elements.append(('paragraph', ' '.join(para_lines)))
        else:
            elements.append(('empty',))
        i += 1

    return elements


# ── 3. Inline parser ───────────────────────────────────────────

def parse_inline_runs(text):
    runs = []
    pattern = r'(\*\*(.+?)\*\*|`(.+?)`)'
    last_end = 0
    for m in re.finditer(pattern, text):
        if m.start() > last_end:
            before = text[last_end:m.start()]
            if before:
                runs.append((before, False, False))
        if m.group(2) is not None:        # **bold**
            runs.append((m.group(2), True, False))
        elif m.group(3) is not None:      # `code`
            runs.append((m.group(3), False, True))
        last_end = m.end()
    if last_end < len(text):
        remaining = text[last_end:]
        if remaining:
            runs.append((remaining, False, False))
    return runs


# ── 4. OOXML generators ────────────────────────────────────────

def make_heading_run(text, font_eastasia='', font_ascii='', font_hAnsi='', font_size=0):
    rpr_parts = []
    if font_eastasia or font_ascii or font_hAnsi:
        parts = []
        if font_eastasia:
            parts.append(f'w:eastAsia="{font_eastasia}"')
        if font_ascii:
            parts.append(f'w:ascii="{font_ascii}"')
        if font_hAnsi:
            parts.append(f'w:hAnsi="{font_hAnsi}"')
        rpr_parts.append(f'<w:rFonts {" ".join(parts)}/>')
    if font_size > 0:
        rpr_parts.append(f'<w:sz w:val="{font_size}"/>')
        rpr_parts.append(f'<w:szCs w:val="{font_size}"/>')
    if rpr_parts:
        rpr = '<w:rPr>' + ''.join(rpr_parts) + '</w:rPr>'
    else:
        rpr = ''
    return f'<w:r>{rpr}<w:t xml:space="preserve">{escape_xml(text)}</w:t></w:r>'


def make_body_run(text, bold=False, code=False, code_font_size=20, font_eastasia='', font_ascii='', font_hAnsi='', font_size=0):
    rpr_parts = []
    if font_eastasia or font_ascii or font_hAnsi:
        parts = []
        if font_eastasia:
            parts.append(f'w:eastAsia="{font_eastasia}"')
        if font_ascii:
            parts.append(f'w:ascii="{font_ascii}"')
        if font_hAnsi:
            parts.append(f'w:hAnsi="{font_hAnsi}"')
        rpr_parts.append(f'<w:rFonts {" ".join(parts)}/>')
    if bold:
        rpr_parts.append('<w:b/><w:bCs/>')
    if code:
        rpr_parts.append(f'<w:sz w:val="{code_font_size}"/>')
    elif font_size > 0:
        rpr_parts.append(f'<w:sz w:val="{font_size}"/>')
        rpr_parts.append(f'<w:szCs w:val="{font_size}"/>')
    if rpr_parts:
        rpr = '<w:rPr>' + ''.join(rpr_parts) + '</w:rPr>'
    else:
        rpr = ''
    return f'<w:r>{rpr}<w:t xml:space="preserve">{escape_xml(text)}</w:t></w:r>'


def make_table_cell_run(text, bold=False, cfg=None):
    font_ascii = cfg.table_cell_font_ascii if cfg else ''
    font_ea = cfg.table_cell_font_eastasia if cfg else ''
    color = cfg.table_header_color if bold else cfg.table_body_color if cfg else ('auto' if bold else 'auto')
    rpr_parts = []
    if font_ascii:
        rpr_parts.append(f'<w:rFonts w:ascii="{font_ascii}" w:eastAsia="{font_ea}" w:hAnsi="{font_ascii}" w:cs="{font_ascii}"/>')
    if bold:
        rpr_parts.append('<w:b/><w:bCs/>')
    if color and color != 'auto':
        rpr_parts.append(f'<w:color w:val="{color}"/>')
    if rpr_parts:
        rpr = '<w:rPr>' + ''.join(rpr_parts) + '</w:rPr>'
    else:
        rpr = ''
    return f'<w:r>{rpr}<w:t xml:space="preserve">{escape_xml(text)}</w:t></w:r>'


def heading_p(xml_id, text, spacing, centered=False, font_eastasia='', font_ascii='', font_hAnsi='', font_size=0, strip_numbers=True):
    clean = re.sub(r'^\d+(\.\d+)*\.?\s*', '', text) if strip_numbers else text
    jc = '<w:jc w:val="center"/>' if centered else ''
    return f'<w:p><w:pPr><w:pStyle w:val="{xml_id}"/>{spacing}{jc}</w:pPr>{make_heading_run(clean, font_eastasia, font_ascii, font_hAnsi, font_size)}</w:p>'


def body_p(runs, body_spacing, body_style_id='a0', code_font_size=20, cover_mode=False,
           font_eastasia='', font_ascii='', font_hAnsi='', font_size=0):
    run_xml = ''.join(make_body_run(t, b, c, code_font_size, font_eastasia, font_ascii, font_hAnsi, font_size)
                      for t, b, c in runs)
    if cover_mode:
        # Cover page: centered, no indent
        ppr = f'<w:pStyle w:val="{body_style_id}"/><w:jc w:val="center"/>'
    else:
        ppr = f'<w:pStyle w:val="{body_style_id}"/>{body_spacing}'
    return f'<w:p><w:pPr>{ppr}</w:pPr>{run_xml}</w:p>'


def ordered_list_p(runs, num_id, body_spacing, body_style_id='a0', code_font_size=20,
                   font_eastasia='', font_ascii='', font_hAnsi='', font_size=0):
    run_xml = ''.join(make_body_run(t, b, c, code_font_size, font_eastasia, font_ascii, font_hAnsi, font_size)
                      for t, b, c in runs)
    # Only keep spacing tags, strip indentation — list level defines its own indent
    spacing_only = re.sub(r'<w:ind[^>]*/>', '', body_spacing) if body_spacing else ''
    return f'<w:p><w:pPr><w:pStyle w:val="{body_style_id}"/><w:numPr><w:ilvl w:val="0"/><w:numId w:val="{num_id}"/></w:numPr>{spacing_only}</w:pPr>{run_xml}</w:p>'


def hr_p(body_spacing, body_style_id='a0'):
    return f'<w:p><w:pPr><w:pStyle w:val="{body_style_id}"/>{body_spacing}<w:pBdr><w:bottom w:val="single" w:sz="6" w:space="1" w:color="auto"/></w:pBdr></w:pPr></w:p>'


def code_block_p(lines, body_spacing, body_style_id='a0', code_font_size=20):
    result = []
    for line in lines.split('\n'):
        run = make_body_run(line, code=True, code_font_size=code_font_size)
        result.append(f'<w:p><w:pPr><w:pStyle w:val="{body_style_id}"/>{body_spacing}<w:ind w:left="420"/></w:pPr>{run}</w:p>')
    return result


def image_p(img_filename, rId, img_full_path, max_img_width_cm, centered_style_id):
    try:
        pil_img = Image.open(img_full_path)
        img_w_px, img_h_px = pil_img.size
    except Exception:
        img_w_px, img_h_px = 800, 600

    max_w_emu = emu(max_img_width_cm)
    aspect = img_h_px / img_w_px
    w_emu = max_w_emu
    h_emu = int(w_emu * aspect)

    img_name = f'图片_{img_filename}'
    docpr_id = hash(img_name) & 0x7FFFFFFF

    drawing_xml = (
        f'<w:drawing>'
        f'<wp:inline distT="0" distB="0" distL="0" distR="0">'
        f'<wp:extent cx="{w_emu}" cy="{h_emu}"/>'
        f'<wp:effectExtent l="0" t="0" r="0" b="0"/>'
        f'<wp:docPr id="{docpr_id}" name="{escape_xml(img_name)}"/>'
        f'<wp:cNvGraphicFramePr>'
        f'<a:graphicFrameLocks noChangeAspect="1"/>'
        f'</wp:cNvGraphicFramePr>'
        f'<a:graphic>'
        f'<a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">'
        f'<pic:pic>'
        f'<pic:nvPicPr>'
        f'<pic:cNvPr id="{docpr_id}" name="{escape_xml(img_name)}"/>'
        f'<pic:cNvPicPr><a:picLocks noChangeAspect="1" noChangeArrowheads="1"/></pic:cNvPicPr>'
        f'</pic:nvPicPr>'
        f'<pic:blipFill>'
        f'<a:blip r:embed="{rId}"/>'
        f'<a:stretch><a:fillRect/></a:stretch>'
        f'</pic:blipFill>'
        f'<pic:spPr>'
        f'<a:xfrm><a:off x="0" y="0"/><a:ext cx="{w_emu}" cy="{h_emu}"/></a:xfrm>'
        f'<a:prstGeom prst="rect"><a:avLst/></a:prstGeom>'
        f'<a:noFill/><a:ln><a:noFill/></a:ln>'
        f'</pic:spPr>'
        f'</pic:pic>'
        f'</a:graphicData>'
        f'</a:graphic>'
        f'</wp:inline>'
        f'</w:drawing>'
    )

    run_xml = f'<w:r><w:rPr><w:noProof/></w:rPr>{drawing_xml}</w:r>'
    return (
        f'<w:p xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"'
        f' xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"'
        f' xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"'
        f' xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"'
        f'><w:pPr><w:pStyle w:val="{centered_style_id}"/><w:jc w:val="center"/></w:pPr>{run_xml}</w:p>'
    )


# ── Table ──────────────────────────────────────────────────────

def _cell_open(col_width, col_idx, num_cols, row_idx, total_rows, cfg):
    border_parts = []
    is_first_row = (row_idx == 0)
    is_last_row = (row_idx == total_rows - 1)
    is_second_row = (row_idx == 1)

    if col_idx == 0 and num_cols > 1:
        border_parts.append(f'<w:right w:val="single" w:sz="{cfg.table_vertical_border}" w:space="0" w:color="auto"/>')
    elif col_idx == 1 and num_cols > 1:
        border_parts.append(f'<w:left w:val="single" w:sz="{cfg.table_vertical_border}" w:space="0" w:color="auto"/>')

    if is_first_row:
        border_parts.append(f'<w:top w:val="single" w:sz="{cfg.table_header_top_border}" w:space="0" w:color="auto"/>')
        border_parts.append(f'<w:bottom w:val="single" w:sz="{cfg.table_header_bottom_border}" w:space="0" w:color="auto"/>')
    elif is_last_row:
        border_parts.append(f'<w:bottom w:val="single" w:sz="{cfg.table_last_row_bottom_border}" w:space="0" w:color="auto"/>')
    elif is_second_row:
        border_parts.append(f'<w:top w:val="single" w:sz="{cfg.table_first_data_top_border}" w:space="0" w:color="auto"/>')

    borders = f'<w:tcBorders>{"".join(border_parts)}</w:tcBorders>' if border_parts else ''

    return (
        f'<w:tc>'
        f'<w:tcPr>'
        f'<w:tcW w:w="{col_width}" w:type="dxa"/>'
        f'{borders}'
        f'<w:vAlign w:val="center"/>'
        f'</w:tcPr>'
    )


def table_xml(rows, cfg):
    header = rows[0]
    num_cols = len(header)
    total_rows = len(rows)
    total_width = cfg.table_total_width_dxa

    col_max_chars = [0] * num_cols
    for row in rows:
        for ci, cell_text in enumerate(row):
            if ci < num_cols:
                col_max_chars[ci] = max(col_max_chars[ci], len(cell_text))

    col_max_chars = [max(c, 3) for c in col_max_chars]
    total_chars = sum(col_max_chars)
    col_widths = [max(int(total_width * c / total_chars), 400) for c in col_max_chars]
    diff = total_width - sum(col_widths)
    if diff != 0 and col_widths:
        widest = col_widths.index(max(col_widths))
        col_widths[widest] += diff

    parts = ['<w:tbl>']
    parts.append('<w:tblPr>')
    parts.append(f'<w:tblStyle w:val="{cfg.table_style_id}"/>')
    parts.append(f'<w:tblW w:w="{total_width}" w:type="dxa"/>')
    parts.append('<w:jc w:val="center"/>')
    parts.append('<w:tblBorders>')
    for border in ['top', 'left', 'bottom', 'right', 'insideH', 'insideV']:
        parts.append(f'<w:{border} w:val="none" w:sz="0" w:space="0" w:color="auto"/>')
    parts.append('</w:tblBorders>')
    parts.append('<w:tblLayout w:type="fixed"/>')
    parts.append('<w:tblLook w:val="04A0" w:firstRow="1" w:lastRow="0" w:firstColumn="1" w:lastColumn="0" w:noHBand="0" w:noVBand="1"/>')
    parts.append('</w:tblPr>')

    parts.append('<w:tblGrid>')
    for w in col_widths:
        parts.append(f'<w:gridCol w:w="{w}"/>')
    parts.append('</w:tblGrid>')

    for row_idx, row in enumerate(rows):
        parts.append('<w:tr><w:trPr><w:jc w:val="center"/></w:trPr>')
        norm_row = list(row)
        while len(norm_row) < num_cols:
            norm_row.append('')
        norm_row = norm_row[:num_cols]

        for ci, cell_text in enumerate(norm_row):
            is_header = (row_idx == 0)
            parts.append(_cell_open(col_widths[ci], ci, num_cols, row_idx, total_rows, cfg))
            cell_run = make_table_cell_run(cell_text, bold=is_header, cfg=cfg)
            before = cfg.table_cell_before
            after = cfg.table_cell_after
            parts.append(
                f'<w:p><w:pPr><w:pStyle w:val="{cfg.body_style_id}"/>'
                f'<w:spacing w:before="{before}" w:after="{after}"/></w:pPr>{cell_run}</w:p>'
            )
            parts.append('</w:tc>')
        parts.append('</w:tr>')

    parts.append('</w:tbl>')
    return ''.join(parts)


# ── 5. Generate OOXML ──────────────────────────────────────────

def generate_ooxml(elements, image_map, used_rIds, cfg):
    result = []
    ordered_list_groups = 0
    h_ids = cfg.heading_style_ids
    hs = cfg.heading_spacing
    hc = cfg.heading_centered
    hfe = cfg.heading_font_eastasia
    hfa = cfg.heading_font_ascii
    hfh = cfg.heading_font_hAnsi
    hfs = cfg.heading_font_size
    bfe = cfg.body_font_eastasia
    bfa = cfg.body_font_ascii
    bfh = cfg.body_font_hAnsi
    bfs = cfg.body_font_size
    past_first_heading = False

    for elem in elements:
        kind = elem[0]

        if kind == 'heading1':
            past_first_heading = True
            result.append(heading_p(h_ids['h1'], elem[1], hs.get(h_ids['h1'], ''), centered=hc.get('h1', False),
                                    font_eastasia=hfe.get('h1', ''), font_ascii=hfa.get('h1', ''), font_hAnsi=hfh.get('h1', ''), font_size=hfs.get('h1', 0), strip_numbers=cfg.strip_heading_numbers))
        elif kind == 'heading2':
            past_first_heading = True
            result.append(heading_p(h_ids['h2'], elem[1], hs.get(h_ids['h2'], ''), centered=hc.get('h2', False),
                                    font_eastasia=hfe.get('h2', ''), font_ascii=hfa.get('h2', ''), font_hAnsi=hfh.get('h2', ''), font_size=hfs.get('h2', 0), strip_numbers=cfg.strip_heading_numbers))
        elif kind == 'heading3':
            past_first_heading = True
            result.append(heading_p(h_ids['h3'], elem[1], hs.get(h_ids['h3'], ''), centered=hc.get('h3', False),
                                    font_eastasia=hfe.get('h3', ''), font_ascii=hfa.get('h3', ''), font_hAnsi=hfh.get('h3', ''), font_size=hfs.get('h3', 0), strip_numbers=cfg.strip_heading_numbers))
        elif kind == 'paragraph':
            if not past_first_heading:
                continue
            runs = parse_inline_runs(elem[1])
            result.append(body_p(runs, cfg.body_spacing, cfg.body_style_id, cfg.code_font_size,
                                 font_eastasia=bfe, font_ascii=bfa, font_hAnsi=bfh, font_size=bfs))
        elif kind == 'empty':
            pass
        elif kind == 'list_item':
            if not past_first_heading:
                continue
            runs = parse_inline_runs(elem[1])
            result.append(body_p(runs, cfg.body_spacing, cfg.body_style_id, cfg.code_font_size,
                                 font_eastasia=bfe, font_ascii=bfa, font_hAnsi=bfh, font_size=bfs))
        elif kind == 'ordered_list':
            past_first_heading = True
            num_id = cfg.ordered_list_base_num_id + ordered_list_groups
            ordered_list_groups += 1
            for text in elem[1]:
                runs = parse_inline_runs(text)
                result.append(ordered_list_p(runs, num_id, cfg.body_spacing, cfg.body_style_id, cfg.code_font_size,
                                             font_eastasia=bfe, font_ascii=bfa, font_hAnsi=bfh, font_size=bfs))
        elif kind == 'code_block':
            past_first_heading = True
            result.extend(code_block_p(elem[1], cfg.body_spacing, cfg.body_style_id, cfg.code_font_size))
        elif kind == 'hr':
            if not past_first_heading:
                continue
            result.append(hr_p(cfg.body_spacing, cfg.body_style_id))
        elif kind == 'image':
            alt = elem[1]
            img_rel_path = elem[2]
            img_filename = os.path.basename(img_rel_path)
            img_full_path = cfg.figures_dir / img_filename

            if img_filename not in image_map:
                new_rId = f'rId{len(image_map) + 28}'
                image_map[img_filename] = {'rId': new_rId, 'path': img_full_path}
                used_rIds.append(new_rId)

            rId = image_map[img_filename]['rId']
            result.append(image_p(img_filename, rId, img_full_path, cfg.max_img_width_cm, cfg.centered_style_id))
        elif kind == 'table':
            result.append(table_xml(elem[1], cfg))

    return result, ordered_list_groups


# ── 5b. Numbering ──────────────────────────────────────────────

def update_numbering(work_dir, ordered_list_groups, cfg):
    num_path = work_dir / 'word' / 'numbering.xml'
    text = num_path.read_text(encoding='utf-8')

    if cfg.start_chapter is not None:
        old_num = f'<w:num w:numId="{cfg.heading_num_id}"'
        old_idx = text.find(old_num)
        if old_idx >= 0:
            end_idx = text.find('</w:num>', old_idx) + len('</w:num>')
            old_block = text[old_idx:end_idx]
            # Insert lvlOverride before </w:num>
            new_block = old_block.replace('</w:num>',
                f'<w:lvlOverride w:ilvl="0"><w:startOverride w:val="{cfg.start_chapter}"/></w:lvlOverride></w:num>')
            text = text.replace(old_block, new_block)
            print(f"  Added lvlOverride start={cfg.start_chapter} to numId={cfg.heading_num_id}")
        else:
            print(f"  WARNING: numId={cfg.heading_num_id} not found in numbering.xml")

    if ordered_list_groups > 0:
        base = cfg.ordered_list_base_num_id
        abstract_num_xml = (
            f'<w:abstractNum w:abstractNumId="{base}">'
            f'<w:nsid w:val="5D3F7E2C"/>'
            f'<w:multiLevelType w:val="hybridMultilevel"/>'
            f'<w:tmpl w:val="A8C1D5F2"/>'
            f'<w:lvl w:ilvl="0">'
            f'<w:start w:val="1"/>'
            f'<w:numFmt w:val="decimal"/>'
            f'<w:lvlText w:val="%1."/>'
            f'<w:lvlJc w:val="left"/>'
            f'<w:pPr><w:ind w:left="420" w:hanging="420"/></w:pPr>'
            f'</w:lvl>'
            f'</w:abstractNum>'
        )
        text = text.replace(f'<w:num w:numId="{cfg.heading_num_id}"', abstract_num_xml + f'<w:num w:numId="{cfg.heading_num_id}"')
        nums_xml = ''.join(
            f'<w:num w:numId="{base + g}"><w:abstractNumId w:val="{base}"/><w:lvlOverride w:ilvl="0"><w:startOverride w:val="1"/></w:lvlOverride></w:num>'
            for g in range(ordered_list_groups)
        )
        text = text.replace('</w:numbering>', nums_xml + '</w:numbering>')
        print(f"  Added ordered list numbering: {ordered_list_groups} group(s), numIds {base}-{base + ordered_list_groups - 1}")

    num_path.write_text(text, encoding='utf-8')


# ── 5c. Styles ─────────────────────────────────────────────────

def update_styles(work_dir, cfg):
    styles_path = work_dir / 'word' / 'styles.xml'
    text = styles_path.read_text(encoding='utf-8')
    for old, new in cfg.font_replacements.items():
        text = text.replace(old, new)
    styles_path.write_text(text, encoding='utf-8')
    print(f"  Font replacements applied: {len(cfg.font_replacements)} rule(s)")


# ── 6. Replace body ────────────────────────────────────────────

def replace_body(work_dir, ooxml_strings):
    doc_path = work_dir / 'word' / 'document.xml'
    content = doc_path.read_text(encoding='utf-8')

    body_start = content.find('<w:body>') + len('<w:body>')
    body_end = content.find('</w:body>')

    # Keep template cover page: the cover page typically contains a table layout.
    # The first content chapter heading (style 1) comes AFTER the first table.
    # We preserve everything before that first content chapter.
    import re as _re
    body = content[body_start:body_end]

    def _find_enclosing_wp(text, style_pos):
        """Find the enclosing <w:p> for a <w:pStyle> at style_pos.
        Must skip past intervening <w:pPr> elements."""
        pos = style_pos
        while True:
            pos = text.rfind('<w:p', 0, pos)
            if pos == -1:
                return -1
            # Distinguish <w:p> / <w:p ...> from <w:pPr>
            if text[pos:pos+5] in ('<w:p ', '<w:p>'):
                return pos
            # It was <w:pPr> — continue searching backwards

    # Find the first </w:tbl> — cover page tables end before content chapters start
    tbl_end_match = _re.search(r'</w:tbl>', body)
    search_start = tbl_end_match.end() if tbl_end_match else 0

    # Find the first <w:pStyle w:val="1"/> whose enclosing <w:p> is after the first table
    style_match = _re.search(r'<w:pStyle w:val="1"', body[search_start:])
    if style_match:
        style_pos = search_start + style_match.start()
        preceding_p = _find_enclosing_wp(body, style_pos)
        if preceding_p != -1 and preceding_p >= search_start:
            replace_start = body_start + preceding_p
            print(f"  Preserving template cover page (keeping bytes {body_start}-{replace_start})")
        else:
            # The style is inside the table; search for the next one after the table
            style_match2 = _re.search(r'<w:pStyle w:val="1"', body[search_start + style_match.end():])
            if style_match2:
                style_pos = search_start + style_match.end() + style_match2.start()
                preceding_p = _find_enclosing_wp(body, style_pos)
                if preceding_p != -1:
                    replace_start = body_start + preceding_p
                    print(f"  Preserving template cover page (keeping bytes {body_start}-{replace_start})")
                else:
                    replace_start = body_start
                    print(f"  WARNING: could not locate enclosing <w:p for style 1 (2nd attempt), using fallback")
            else:
                replace_start = body_start
                print(f"  WARNING: no second style 1 found after table, using fallback")
    else:
        # Fallback: no heading 1 at all, keep entire body
        replace_start = body_end

    search_pos = body_start
    all_sectpr = []
    while True:
        pos = content.find('<w:sectPr', search_pos)
        if pos == -1 or pos >= body_end:
            break
        end = content.find('</w:sectPr>', pos) + len('</w:sectPr>')
        if end > pos:
            all_sectpr.append((pos, end))
        search_pos = end

    sectpr_xml = ''
    if all_sectpr:
        sectpr_xml = content[all_sectpr[-1][0]:all_sectpr[-1][1]]

    our_content = ''.join(ooxml_strings)
    new_body = content[body_start:replace_start] + our_content + sectpr_xml
    new_content = content[:body_start] + new_body + content[body_end:]
    doc_path.write_text(new_content, encoding='utf-8')
    print(f"  Body replaced. {len(our_content)} chars inserted.")


# ── 7-9. Relationships, images, content types ──────────────────

def update_relationships(work_dir, image_map):
    import xml.etree.ElementTree as ET
    rels_path = work_dir / 'word' / '_rels' / 'document.xml.rels'
    ET.register_namespace('', 'http://schemas.openxmlformats.org/package/2006/relationships')
    tree = ET.parse(str(rels_path))
    root = tree.getroot()

    max_id = 0
    for rel in root:
        rid = rel.get('Id', '')
        if rid.startswith('rId'):
            try:
                max_id = max(max_id, int(rid[3:]))
            except ValueError:
                pass

    next_id = max_id + 1
    for img_filename, info in image_map.items():
        encoded_name = _encode_opc_part_name(img_filename)
        exists = any(rel.get('Target') == f'media/{encoded_name}' for rel in root)
        if not exists:
            new_rel = ET.Element('Relationship', {
                'Id': f'rId{next_id}',
                'Type': 'http://schemas.openxmlformats.org/officeDocument/2006/relationships/image',
                'Target': f'media/{encoded_name}',
            })
            root.append(new_rel)
            info['rId'] = f'rId{next_id}'
            next_id += 1

    rels_path.write_bytes(ET.tostring(root, encoding='UTF-8', xml_declaration=True))


def copy_images(work_dir, image_map):
    media_dir = work_dir / 'word' / 'media'
    for img_filename, info in image_map.items():
        dst = media_dir / img_filename
        if not dst.exists():
            shutil.copy2(str(info['path']), str(dst))
            print(f"  Copied: {img_filename}")


def update_content_types(work_dir, image_map):
    import xml.etree.ElementTree as ET
    ct_path = work_dir / '[Content_Types].xml'
    CT_NS = 'http://schemas.openxmlformats.org/package/2006/content-types'
    ET.register_namespace('', CT_NS)
    tree = ET.parse(str(ct_path))
    root = tree.getroot()

    for img_filename in image_map:
        ext = img_filename.rsplit('.', 1)[-1].lower()
        encoded_name = _encode_opc_part_name(img_filename)
        part_name = f'/word/media/{encoded_name}'
        exists = any(ov.get('PartName') == part_name for ov in root.findall(f'{{{CT_NS}}}Override'))
        if not exists:
            content_type = 'image/png' if ext == 'png' else 'image/jpeg'
            root.append(ET.Element(f'{{{CT_NS}}}Override', {
                'PartName': part_name, 'ContentType': content_type
            }))

    ct_path.write_bytes(ET.tostring(root, encoding='UTF-8', xml_declaration=True))


# ── 10. Repack ─────────────────────────────────────────────────

def repack_docx(work_dir, output_path):
    if output_path.exists():
        output_path.unlink()
    with zipfile.ZipFile(str(output_path), 'w', zipfile.ZIP_DEFLATED) as z:
        for f in work_dir.rglob('*'):
            if f.is_file():
                z.write(str(f), str(f.relative_to(work_dir)))
    print(f"  Output: {output_path}")


# ── Main entry ─────────────────────────────────────────────────

def convert(cfg: ConversionConfig, work_dir: Optional[Path] = None):
    """Run the full conversion pipeline. cfg contains all template-specific values.

    Args:
        cfg: ConversionConfig with all paths, spacing, style IDs, etc.
        work_dir: Temporary work directory. Defaults to cfg.output_path.parent / 'docx_work'.
    """
    if work_dir is None:
        work_dir = cfg.output_path.parent / WORK_DIR_NAME

    print("=" * 60)
    print("Step 1: Unpacking template...")
    unpack_template(cfg.template_path, work_dir)

    print("\nStep 2: Parsing markdown...")
    elements = parse_md(cfg.md_path)
    print(f"  Parsed {len(elements)} elements")

    print("\nStep 3: Generating OOXML...")
    image_map = {}
    used_rIds = []
    ooxml, ordered_list_groups = generate_ooxml(elements, image_map, used_rIds, cfg)
    # Save initial rIds assigned during OOXML generation for later patching
    old_rIds = {img_filename: info['rId'] for img_filename, info in image_map.items()}
    print(f"  Generated {len(ooxml)} XML blocks")
    print(f"  Images: {len(image_map)}, initial rIds: {sorted(set(old_rIds.values()))}")
    if ordered_list_groups > 0:
        print(f"  Ordered list groups: {ordered_list_groups}")

    print("\nStep 4: Updating numbering...")
    update_numbering(work_dir, ordered_list_groups, cfg)

    print("\nStep 5: Updating styles...")
    update_styles(work_dir, cfg)

    print("\nStep 6: Replacing body content...")
    replace_body(work_dir, ooxml)

    print("\nStep 7: Updating relationships...")
    update_relationships(work_dir, image_map)

    print("\nStep 7.5: Patching rId references in document.xml...")
    _patch_rIds(work_dir, image_map, old_rIds)

    print("\nStep 8: Copying images...")
    media_dir = work_dir / 'word' / 'media'
    if not media_dir.exists():
        media_dir.mkdir(parents=True)
    copy_images(work_dir, image_map)

    print("\nStep 9: Updating Content_Types...")
    update_content_types(work_dir, image_map)

    print("\nStep 10: Repacking docx...")
    repack_docx(work_dir, cfg.output_path)

    # Verification
    print("\n" + "=" * 60)
    _verify(work_dir, cfg)

    print("\nDone!")


def _patch_rIds(work_dir, image_map, old_rIds):
    """Patch r:embed references in document.xml to match updated relationship IDs.
    update_relationships() may reassign rIds, so we must sync the document.xml."""
    doc_path = work_dir / 'word' / 'document.xml'
    doc_xml = doc_path.read_text(encoding='utf-8')
    patches = 0
    for img_filename, info in image_map.items():
        old_rId = old_rIds[img_filename]
        new_rId = info['rId']
        if old_rId != new_rId:
            count_before = doc_xml.count(f'r:embed="{old_rId}"')
            doc_xml = doc_xml.replace(f'r:embed="{old_rId}"', f'r:embed="{new_rId}"')
            patches += 1
            if count_before > 0:
                print(f"  {img_filename}: {old_rId} -> {new_rId} ({count_before} occurrence(s))")
    doc_path.write_text(doc_xml, encoding='utf-8')
    if patches == 0:
        print("  No rId patches needed")


def _verify(work_dir, cfg):
    try:
        import xml.etree.ElementTree as ET
        doc_path = work_dir / 'word' / 'document.xml'
        tree = ET.parse(str(doc_path))
        ns = 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'
        body = tree.getroot().find(f'{{{ns}}}body')
        w_p = len(body.findall(f'.//{{{ns}}}p'))
        w_tbl = len(body.findall(f'.//{{{ns}}}tbl'))
        w_r = len(body.findall(f'.//{{{ns}}}r'))
        print(f"  Body children: {len(list(body))}")
        print(f"  Paragraphs: {w_p}")
        print(f"  Tables: {w_tbl}")
        print(f"  Runs: {w_r}")
    except Exception as e:
        print(f"  Verify: XML parse skipped ({e})")

    styles_text = (work_dir / 'word' / 'styles.xml').read_text(encoding='utf-8')
    doc_text = (work_dir / 'word' / 'document.xml').read_text(encoding='utf-8')
    print(f"  Consolas in output: doc={doc_text.count('Consolas')}, styles={styles_text.count('Consolas')}")
    print(f"  Arial in output: doc={doc_text.count('Arial')}, styles={styles_text.count('Arial')}")
