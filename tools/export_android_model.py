#!/usr/bin/env python3
"""
Export the trained tokenizer + N-gram model into a compact, line-based text
format that the Android app can load in plain Java (no Python, no server).

Also writes test vectors so the Java tokenizer port can be verified exactly.

Run: python3 tools/export_android_model.py <assets_dir> <vectors_file>
"""
import base64
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)

from main import load_components  # noqa: E402


def b64(text):
    return base64.b64encode(text.encode("utf-8")).decode("ascii")


def export_model(tokenizer, model, out_path):
    lines = ["ETHANGPT_MODEL 1"]
    lines.append(f"order {model.order}")
    lines.append(f"vocab {len(tokenizer)}")
    for token_id in range(len(tokenizer)):
        lines.append(f"V {token_id} {b64(tokenizer.id_to_token[token_id])}")

    lines.append(f"N {len(tokenizer.merges)}")
    for left, right in tokenizer.merges:
        combined = left + right
        lines.append(
            f"G {tokenizer.vocab[left]} {tokenizer.vocab[right]} "
            f"{tokenizer.vocab[combined]}"
        )

    for n in range(1, model.order + 1):
        for context, counts in model.tables[n - 1].items():
            ctx_ids = " ".join(str(x) for x in context)
            pairs = " ".join(f"{tok}:{cnt}" for tok, cnt in counts.items())
            lines.append(f"C {n} {ctx_ids} | {pairs}")

    with open(out_path, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")
    return os.path.getsize(out_path)


def export_vectors(tokenizer, out_path):
    samples = [
        "What is EthanGPT?",
        "What is a token?",
        "Can the model run offline?",
        "How do I install Python on Android?",
        "EthanGPT turns text into numbers.",
        "  leading and trailing  ",
        "Unicode café ☕ test",
    ]
    # Include real corpus lines too.
    corpus_path = os.path.join(ROOT, "dataset", "corpus.txt")
    with open(corpus_path, "r", encoding="utf-8") as handle:
        for line in handle.read().splitlines()[:12]:
            if line.strip():
                samples.append(line)

    lines = []
    for text in samples:
        ids = tokenizer.encode(text)
        id_str = ",".join(str(i) for i in ids)
        lines.append(f"{b64(text)} {id_str}")
    with open(out_path, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")
    return len(samples)


def main():
    if len(sys.argv) < 3:
        print("usage: export_android_model.py <assets_dir> <vectors_file>")
        return 2
    assets_dir, vectors_file = sys.argv[1], sys.argv[2]
    os.makedirs(assets_dir, exist_ok=True)
    tokenizer, model, _settings = load_components()

    out_path = os.path.join(assets_dir, "model.txt")
    size = export_model(tokenizer, model, out_path)
    count = export_vectors(tokenizer, vectors_file)

    print(f"model:   {out_path} ({size:,} bytes)")
    print(f"vectors: {vectors_file} ({count} samples)")
    print(f"params:  {model.parameter_count:,}  vocab: {len(tokenizer)}  "
          f"order: {model.order}  merges: {len(tokenizer.merges)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
