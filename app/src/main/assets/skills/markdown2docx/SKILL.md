---
name: markdown2docx
description: "Use this skill when the user asks to convert a markdown (.md) file to Word (.docx) using a pre-existing Word template. This skill is specifically for MD→DOCX conversion via template filling — it does NOT create documents from scratch, generate content, or create templates. Triggers include: 'convert this md to docx', '生成docx', 'md转word', '用模板生成报告', or similar requests to produce a .docx from an existing .md file and a .docx template. Do NOT use for creating new documents without a markdown source, editing existing .docx files, or general Word document creation (use the docx skill for those)."
license: Proprietary. LICENSE.txt has complete terms
---

# Markdown to DOCX Conversion Skill

**This skill converts existing markdown (.md) files to Word (.docx) documents by filling a Word template. It does NOT generate content from scratch — the user must already have a markdown file.**

## Project location

The engine code and preprocess module are at `C:\Users\27602\.claude\skills\markdown2docx\`. All paths in this skill refer to that directory.

```
C:\Users\27602\.claude\skills\markdown2docx\
├── engine.py        ← parameterized engine (configure via ConversionConfig)
├── preprocess.py    ← shared markdown preprocessing module
└── SKILL.md         ← this file
```

## Iron Rules — NEVER violate

1. **Never edit `engine.py` for template-specific settings.** All customizations go through `ConversionConfig` or markdown preprocessing.
2. **Template written instructions override style definitions.** When the template's content text ("内容") specifies formatting (e.g. "黑体四号" for chapter headings) that conflicts with style definitions in `styles.xml`, the written instructions take priority. Configure these overrides via `ConversionConfig` fields (`heading_font_*`, `body_font_*`, etc.).
3. **No manually typed numbers in headings.** Template's numbering library (`w:numPr`) generates all heading numbers.
4. **No bullet points.** No `•` characters anywhere in output.
5. **English fonts follow the template**, defaulting to Times New Roman where unspecified.
6. **Preprocess markdown before the engine** when the markdown uses syntax the engine's parser doesn't handle.
7. **Template styles provide the baseline.** Numbering format, spacing, indentation all come from the template — only override fonts/sizes when template instructions explicitly require it.
8. **Never modify template administrative sections.** Sections like "实验评语" (grading rubrics/evaluation criteria), "任课教师评语", and similar template-provided reference content must be preserved exactly as-is — tables, bullet symbols (•), formatting, everything. These are not part of the markdown content and are copied intact from the template. The engine's cover/content boundary detection already preserves them. Note: Rule #4 (no bullet points) applies only to generated content from markdown, not to preserved template sections.

## When preprocessing is needed

The engine's markdown parser (`parse_md`) only handles standard markdown: `## headings`, `| tables |`, `**bold**`, `` `code` ``, ordered lists (`1. `), code blocks (``` ```). For everything else, import `preprocess.py` and call `preprocess()` before passing text to the engine.

## Preprocessing module (`preprocess.py`)

```python
from preprocess import preprocess, html_table_to_md

processed = preprocess(raw_markdown_text)  # full 9-step pipeline
```

### Pipeline steps (applied in order):

| Step | What | Regex/Logic |
|------|------|-------------|
| 1 | HTML `<table>` → markdown pipe table | `html_table_to_md()` with colspan expansion |
| 2 | `<p>tags</p>` → unwrap text | `re.sub(r'<p[^>]*>(.*?)</p>', r'\1', text)` |
| 3 | `<br>` → remove | `re.sub(r'<br\s*/?>', '', text)` |
| 4 | Strip `步骤N：` from headings | `re.sub(r'^(#{2,4} )步骤\d+[：:]\s*', r'\1', ...)` |
| 5 | `[text](url)` → text only | `re.sub(r'\[([^\]]+)\]\([^)]+\)', r'\1', text)` |
| 6 | Remove TOC section | Delete all lines between the first and second `---` |
| 7 | `# heading` → `##` or plain paragraph | Before first `---`: strip to plain paragraph (cover). After `---`, before any `##`/`###`: document title → plain paragraph. After a `##`/`###` exists: → `## ` (chapter heading) |
| 8 | `## 一、Section` → `### 一、Section` | Demote Chinese-numbered `##` sections — they're sections under the title, not chapters |
| 9 | Strip `一、二、三…` from all headings | `re.sub(r'^(#{2,4} )[一二三四五六七八九十]+、\s*', r'\1', ...)` |

### Expected markdown structure after preprocessing:

```markdown
Cover Title 1              ← was # heading, now plain paragraph
Cover Title 2              ← was # heading, now plain paragraph

---

Document Title             ← was # heading, now plain paragraph (first after ---)
## Section One             ← was ## 一、Section One, demoted from chapter to section
### Subsection             ← was ### 步骤1：Subsection, prefix stripped
#### Detail                ← engine strips hand-typed "4.1.1", template re-numbers
```

## Conversion workflow

### For each conversion:

1. **Read the markdown** to understand its structure and identify preprocessing needs
2. **Read the template's `word/document.xml`** to check for tables, heading samples, and style usage
3. **Read the template's `word/styles.xml`** to understand font configuration
4. **Create a call site** (or reuse an existing one like `run_selinux.py`):
   ```python
   import sys
   from pathlib import Path
   sys.path.insert(0, str(Path(__file__).resolve().parent.parent / 'markdown2docx'))
   from engine import ConversionConfig, convert, extract_config_from_template
   from preprocess import preprocess

   cfg = extract_config_from_template(TEMPLATE)
   cfg.md_path = MD_PREPROCESSED
   cfg.figures_dir = FIGURES
   cfg.output_path = OUTPUT
   cfg.start_chapter = None  # override if headings should start from chapter N

   convert(cfg)
   ```
5. **Run the conversion** with `python run_xxx.py`
6. **Verify the output** using the checklist below

### `start_chapter` rule:

- `None`: auto-numbering starts from 1 (or from template default)
- Integer `N`: chapter headings start from "第N章"
- Only set when the first chapter heading in content should NOT be "第1章"
- Document titles that are cover elements (plain paragraphs) don't count — `start_chapter` only affects actual chapter headings (Style 1)

## Verification checklist

After each conversion, verify the output .docx by unpacking and checking:

```
python -c "
import zipfile, re
docx = zipfile.ZipFile('output.docx', 'r')
doc = docx.read('word/document.xml').decode('utf-8')

# Essential checks
s1 = len(re.findall(r'<w:pStyle w:val=\"1\"', doc))   # Style 1 (chapters)
s2 = len(re.findall(r'<w:pStyle w:val=\"2\"', doc))   # Style 2 (sections)
print(f'Style 1: {s1}, Style 2: {s2}')

print('Hyperlinks:', len(re.findall(r'<w:hyperlink', doc)))
print('Bullets:', len(re.findall(r'[•◆]', doc)))
print('Consolas:', len(re.findall(r'Consolas', doc)))
print('Arial:', len(re.findall(r'Arial', doc)))
print('Helvetica:', len(re.findall(r'Helvetica', doc)))
print('Raw links:', len(re.findall(r'\[([^\]]+)\]\([^)]+\)', doc)))
print('Chinese prefixes:', len(re.findall(r'[一二三四五六七八九十]+、', doc)))
print('Step prefixes:', len(re.findall(r'步骤\d+[：:]', doc)))
print('numPr items:', len(re.findall(r'<w:numPr>', doc)))

# Font checks
print('Heading 黑体:', len(re.findall(r'w:eastAsia=\"黑体\"', doc)))
print('Body 宋体:', len(re.findall(r'w:eastAsia=\"宋体\"', doc)))
print('Body sz=24 (小四):', len(re.findall(r'<w:sz w:val=\"24\"', doc)))
print('Body sz=21 (五号):', len(re.findall(r'<w:sz w:val=\"21\"', doc)))
"
```

| Check | Criterion |
|-------|-----------|
| Style 1 count | Document title is plain paragraph (Style 1 = 0) or correct chapter count |
| Style 2/3 count | Sections/subsections properly nested |
| Hyperlinks | 0 (TOC is Word auto-generated) |
| Bullets | 0 |
| Consolas / Arial / Helvetica | 0 in both document.xml and styles.xml |
| Raw `[text](url)` | 0 |
| Chinese prefixes | 0 |
| Step prefixes | 0 |
| `numPr` items | Matches expected ordered list count |
| Heading 黑体 / Body 宋体 | Fonts match template instructions (content over style) |
| Body sz=24 (小四) / sz=21 (五号) | Body size is 小四 (24), not 五号 (21) |
| Engine unchanged | `git diff markdown2docx/engine.py` is empty |
