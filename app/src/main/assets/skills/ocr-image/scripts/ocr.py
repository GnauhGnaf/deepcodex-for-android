"""CLI wrapper for DeepSeek OCR — extract text from images and PDFs."""
import argparse
import io
import os
import sys

# Force UTF-8 on stdout so Chinese/Unicode text survives pipe/subprocess capture.
# On Chinese Windows, sys.stdout defaults to GBK; without this, piping output
# produces mojibake or UnicodeDecodeError.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
else:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

from deepseek_ocr import DeepSeekOCR


def main():
    parser = argparse.ArgumentParser(
        description="Extract text from images/PDFs using DeepSeek OCR"
    )
    parser.add_argument("file", help="Path to image or PDF file")
    parser.add_argument(
        "-m", "--mode",
        choices=["free_ocr", "grounding", "ocr_image"],
        default="free_ocr",
        help="OCR mode: free_ocr (fast), grounding (complex tables), ocr_image (detailed)",
    )
    parser.add_argument(
        "-k", "--api-key",
        default=os.environ.get("DS_OCR_API_KEY", ""),
        help="API key (default: $DS_OCR_API_KEY)",
    )
    parser.add_argument(
        "-b", "--base-url",
        default="https://api.siliconflow.cn/v1/chat/completions",
        help="API base URL",
    )
    parser.add_argument(
        "--dpi",
        type=int,
        default=200,
        help="DPI for PDF rendering (default: 200)",
    )
    parser.add_argument(
        "-p", "--page",
        type=int,
        default=1,
        help="PDF page to process (1-indexed, default: 1). Use 0 for all pages.",
    )
    parser.add_argument(
        "--raw",
        action="store_true",
        help="Output raw text including bounding-box coordinates",
    )
    args = parser.parse_args()

    if not args.api_key:
        print("Error: No API key provided. Set DS_OCR_API_KEY env var or use -k.", file=sys.stderr)
        sys.exit(1)

    client = DeepSeekOCR(
        api_key=args.api_key,
        base_url=args.base_url,
        dpi=args.dpi,
    )

    pages = None if args.page == 0 else args.page
    text = client.parse(args.file, mode=args.mode, pages=pages)

    if not args.raw:
        # Strip bounding-box coordinate tags for clean output
        import re
        text = re.sub(r"<\|/ref\|>\[\[.*?\]\]<\|/det\|>", "", text)
        text = re.sub(r"\n{3,}", "\n\n", text)
        text = text.strip()

    print(text)


if __name__ == "__main__":
    main()
