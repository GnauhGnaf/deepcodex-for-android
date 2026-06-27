"""OCR via SiliconFlow DeepSeek-OCR — supports images and PDFs."""
import argparse
import base64
import json
import os
import sys
import urllib.request

DEFAULT_BASE_URL = "https://api.siliconflow.cn/v1/chat/completions"
MODEL = "deepseek-ai/DeepSeek-OCR"

PROMPTS = {
    "free_ocr": "<image>\nFree OCR.",
    "grounding": "<image>\n<|grounding|>Convert the document to markdown.",
    "ocr_image": "<image>\n<|grounding|>OCR this image with detailed layout.",
    "parse_figure": "<image>\nParse the figure.",
    "describe": "<image>\nDescribe this image in detail.",
}


def load_config():
    """Read API key and base URL from config file or environment."""
    api_key = os.environ.get("DS_OCR_API_KEY", "")
    base_url = os.environ.get("DS_OCR_BASE_URL", DEFAULT_BASE_URL)

    config_paths = ["/root/.ocr_config", os.path.expanduser("~/.ocr_config")]
    for path in config_paths:
        if os.path.exists(path):
            with open(path, "r") as f:
                for line in f:
                    line = line.strip()
                    if line.startswith("DS_OCR_API_KEY="):
                        api_key = line.split("=", 1)[1].strip().strip('"').strip("'")
                    elif line.startswith("DS_OCR_BASE_URL="):
                        base_url = line.split("=", 1)[1].strip().strip('"').strip("'")
    return api_key, base_url


def file_to_base64(filepath: str) -> tuple:
    """Read file and return (base64_string, mime_type)."""
    ext = os.path.splitext(filepath)[1].lower()
    mime_map = {
        ".png": "image/png",
        ".jpg": "image/jpeg",
        ".jpeg": "image/jpeg",
        ".webp": "image/webp",
        ".gif": "image/gif",
        ".bmp": "image/bmp",
        ".tiff": "image/tiff",
        ".tif": "image/tiff",
        ".pdf": "application/pdf",
    }
    mime = mime_map.get(ext, "application/octet-stream")
    with open(filepath, "rb") as f:
        data = base64.b64encode(f.read()).decode("utf-8")
    return data, mime


def call_ocr(filepath: str, mode: str, api_key: str, base_url: str) -> str:
    """Send file to SiliconFlow DeepSeek-OCR and return extracted text."""
    b64_data, mime = file_to_base64(filepath)
    prompt = PROMPTS.get(mode, PROMPTS["grounding"])

    payload = {
        "model": MODEL,
        "messages": [
            {
                "role": "user",
                "content": [
                    {
                        "type": "image_url",
                        "image_url": {"url": f"data:{mime};base64,{b64_data}"}
                    },
                    {"type": "text", "text": prompt}
                ]
            }
        ],
        "stream": False,
        "max_tokens": 4096,
    }

    req = urllib.request.Request(
        base_url,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            result = json.loads(resp.read().decode("utf-8"))
            content = result["choices"][0]["message"]["content"]
            return content
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        return f"HTTP {e.code}: {body}"
    except Exception as e:
        return f"Error: {e}"


def main():
    parser = argparse.ArgumentParser(
        description="OCR images and PDFs via SiliconFlow DeepSeek-OCR"
    )
    parser.add_argument("file", help="Path to image or PDF file")
    parser.add_argument(
        "-m", "--mode",
        choices=list(PROMPTS.keys()),
        default="grounding",
        help="OCR mode (default: grounding)",
    )
    parser.add_argument(
        "-k", "--api-key",
        default="",
        help="API key override",
    )
    parser.add_argument(
        "-b", "--base-url",
        default="",
        help="API base URL override",
    )
    parser.add_argument(
        "--raw",
        action="store_true",
        help="Output raw result without cleanup",
    )
    args = parser.parse_args()

    if not os.path.exists(args.file):
        print(f"Error: file not found: {args.file}", file=sys.stderr)
        sys.exit(1)

    api_key, base_url = load_config()

    if args.api_key:
        api_key = args.api_key
    if args.base_url:
        base_url = args.base_url

    if not api_key:
        print(
            "Error: No API key. Set DS_OCR_API_KEY env var, "
            "create /root/.ocr_config, or use -k.",
            file=sys.stderr,
        )
        sys.exit(1)

    text = call_ocr(args.file, args.mode, api_key, base_url)

    if not args.raw:
        import re
        text = re.sub(r"<\|/ref\|>\[\[.*?\]\]<\|/det\|>", "", text)
        text = re.sub(r"\n{3,}", "\n\n", text)
        text = text.strip()

    print(text)


if __name__ == "__main__":
    main()
