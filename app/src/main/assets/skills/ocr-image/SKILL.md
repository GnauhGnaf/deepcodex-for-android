---
name: ocr-image
description: "Extract text from images and PDFs using DeepSeek OCR via SiliconFlow API. Use when the user asks to read text from a screenshot, photo, scanned document, or PDF. Triggers: 'OCR this image', 'read text from this screenshot', 'extract text from this picture/photo/PDF', 'what does this image say', 'recognize text in this image'."
---

# OCR Image Text Extraction

Extract text from images (PNG, JPG, etc.) and PDF files using the DeepSeek OCR model hosted on SiliconFlow.

## Requirements

- Python package: `deepseek-ocr` (v0.3.0+)
- API key: SiliconFlow API key with access to DeepSeek-OCR model
- The API key is read from `DS_OCR_API_KEY` environment variable, or passed via `-k`

## Usage

```bash
python scripts/ocr.py <image_or_pdf_path> [options]
```

### OCR Modes

| Mode | Speed | Best For |
|------|-------|----------|
| `free_ocr` (default) | 4-11s/page | 80% of documents, simple markdown output |
| `grounding` | 5-8s/page | Complex tables (≥20 rows), HTML table output with bounding boxes |
| `ocr_image` | 19-26s/page | Edge cases needing word-level bounding boxes |

### Common Options

| Option | Description |
|--------|-------------|
| `-m free_ocr` | Fast mode (default) |
| `-m grounding` | Complex table mode with bounding boxes |
| `-m ocr_image` | Detailed word-level extraction |
| `-k <key>` | API key override |
| `-b <url>` | API base URL override |
| `--dpi 300` | Higher DPI for PDF rendering |
| `-p 1` | Process single PDF page (1-indexed) |
| `-p 0` | Process all PDF pages |
| `--raw` | Keep bounding-box coordinate tags in output |

### Examples

```bash
# Basic OCR on a screenshot
python scripts/ocr.py "screenshot.png"

# Complex table extraction
python scripts/ocr.py "table.png" -m grounding

# PDF with specific page
python scripts/ocr.py "document.pdf" -p 1 -m free_ocr

# All pages of a PDF
python scripts/ocr.py "document.pdf" -p 0 -m grounding

# With explicit API key
python scripts/ocr.py "photo.jpg" -k "sk-xxx"
```

## Workflow

1. When the user asks to OCR an image, first locate the image file path
2. Run `python /c/Users/27602/.claude/skills/ocr-image/scripts/ocr.py "<file_path>"` with appropriate mode.
   - **On Chinese Windows:** redirect output to a file to avoid console encoding issues:
     `python scripts/ocr.py "file.jpg" > output.txt 2>&1`
     Then read `output.txt` to get the result.
3. Present the extracted text clearly to the user, organizing tables and sections as needed
4. If the output seems truncated (< 500 chars) or garbled, re-run with `-m grounding` and redirect to file

## Notes

- The script automatically uses `DS_OCR_API_KEY` from the environment if set
- For images with complex tables, prefer `-m grounding`
- PDF DPI defaults to 200; use `--dpi 300` for small text

## Troubleshooting

### Garbled Chinese text / mojibake on Windows

Chinese Windows consoles default to GBK encoding. Even when the script forces UTF-8 on stdout (v0.3.1+), the Bash/Cmd wrapper may still misinterpret the bytes. Workaround:

```bash
python scripts/ocr.py "image.jpg" -m grounding > output.txt 2>&1
```

Then read `output.txt` — the file will contain properly encoded UTF-8 text. This is the most reliable approach on Chinese Windows. The underlying root cause is that the `Bash` tool's output capture layer expects UTF-8 bytes, but the OCR result contains CJK characters that may be double-encoded through the GBK→UTF-8 pipeline.
