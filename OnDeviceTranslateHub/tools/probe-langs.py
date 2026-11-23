#!/usr/bin/env python3
"""
Developer-only sanity probe: ask a local GGUF model to translate a few short
sentences into the given languages and print what it produces.

This is NOT how the app decides which languages a model supports. The language
list is a contract and lives in catalog.json, sourced from official model cards.
A model's self-description is never deterministic enough to be a contract.

Usage (build llama.cpp for the host first, then):

    python tools/probe-langs.py path/to/model.gguf --langs zh-Hans ja yue
    python tools/probe-langs.py path/to/model.gguf --bin build/bin/llama-completion

Findings are for human review only; do not paste them into catalog.json.
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys

SAMPLES = [
    ("en", "It's on the house."),
    ("en", "Two coffees to go, please."),
    ("en", "The train leaves at seven."),
]

# English display names used by the Hy-MT family prompt.
DISPLAY = {
    "zh-Hans": "Chinese",
    "zh-Hant": "Traditional Chinese",
    "yue": "Cantonese",
    "bo": "Tibetan",
    "ug": "Uyghur",
    "mn": "Mongolian",
    "kk": "Kazakh",
    "en": "English",
    "ja": "Japanese",
    "ko": "Korean",
    "fr": "French",
    "de": "German",
    "es": "Spanish",
    "pt": "Portuguese",
    "ru": "Russian",
    "ar": "Arabic",
    "th": "Thai",
    "vi": "Vietnamese",
    "id": "Indonesian",
    "ms": "Malay",
    "fil": "Filipino",
    "hi": "Hindi",
}


def prompt_for(target: str, text: str) -> str:
    name = DISPLAY.get(target, target)
    return (
        "Translate the following segment into "
        f"{name}, without additional explanation.\n\n{text}"
    )


def run(binary: str, model: str, prompt: str, n_predict: int) -> str:
    cmd = [
        binary,
        "-m", model,
        "-p", prompt,
        "-n", str(n_predict),
        "-t", "4",
        "--temp", "0",
        "--no-warmup",
        "-st",
    ]
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
    except FileNotFoundError:
        print(f"[error] binary not found: {binary}", file=sys.stderr)
        raise SystemExit(2)
    if proc.returncode != 0:
        return f"<failed rc={proc.returncode}>\n{proc.stderr[-500:]}"
    out = proc.stdout
    # Trim the echoed prompt when the CLI prints it.
    if prompt in out:
        out = out.split(prompt, 1)[1]
    return out.strip()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("model", help="path to a .gguf file")
    parser.add_argument("--langs", nargs="+", default=["zh-Hans", "ja", "ko"])
    parser.add_argument("--bin", default="llama-completion",
                        help="llama.cpp CLI binary (host build)")
    parser.add_argument("--n", type=int, default=64, help="max new tokens")
    args = parser.parse_args()

    if not os.path.exists(args.model):
        print(f"[error] model not found: {args.model}", file=sys.stderr)
        return 2

    print(f"model: {args.model}")
    print("NOTE: results are for human review only; never feed them into catalog.json\n")

    for target in args.langs:
        print(f"=== {target} ({DISPLAY.get(target, target)}) ===")
        for _, text in SAMPLES:
            result = run(args.bin, args.model, prompt_for(target, text), args.n)
            first_line = result.splitlines()[0] if result else "<empty>"
            echo = " [SAME AS SOURCE]" if first_line.strip() == text else ""
            print(f"  {text}")
            print(f"    -> {first_line}{echo}")
        print()

    return 0


if __name__ == "__main__":
    sys.exit(main())
