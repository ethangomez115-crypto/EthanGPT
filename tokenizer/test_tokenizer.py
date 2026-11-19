#!/usr/bin/env python3
"""Stage 2 verification tests. Run: python tokenizer/test_tokenizer.py"""

import os
import tempfile

from bpe_tokenizer import BPETokenizer

CORPUS = (
    "EthanGPT is a tiny language model. "
    "EthanGPT learns from text and generates text. "
    "A tiny model runs on a phone. "
    "The tokenizer turns text into numbers. "
    "The tokenizer turns words into tokens. "
    "Text in, text out, numbers in between."
)


def check(condition, message):
    if not condition:
        raise AssertionError(message)
    print("  ok -", message)


def main():
    print("EthanGPT Stage 2 tokenizer tests\n")

    tok = BPETokenizer().train(CORPUS, vocab_size=200)
    print(f"Vocabulary size: {len(tok)}")
    print(f"Learned merges:  {len(tok.merges)}")
    print(f"First merges:    {tok.merges[:6]}\n")

    check(4 <= len(tok) <= 200, "vocabulary stayed within its budget")
    check(len(tok.merges) > 0, "at least one merge was learned")

    # 1. Round trip on the training text must be exact.
    ids = tok.encode(CORPUS)
    check(tok.decode(ids) == CORPUS, "encode -> decode is lossless on training text")
    check(len(ids) > 10, "text became several tokens, not one giant token")
    print(f"  {len(CORPUS)} characters became {len(ids)} token ids\n")

    # 2. Special tokens get their own fixed ids.
    special = tok.encode("hi", bos=True, eos=True)
    check(special[0] == tok.bos_id and special[-1] == tok.eos_id,
          "BOS and EOS ids are added at the right ends")
    check(tok.decode(special) == "hi", "special tokens are hidden when decoding")

    # 3. Characters never seen during training map to <unk>.
    unknown = tok.encode("😀")
    check(tok.unk_id in unknown, "unknown characters map to <unk>")

    # 4. Save/load must reproduce identical behaviour.
    with tempfile.TemporaryDirectory() as folder:
        path = os.path.join(folder, "tokenizer.json")
        tok.save(path)
        loaded = BPETokenizer.load(path)
        check(len(loaded) == len(tok), "vocabulary size survives save/load")
        check(loaded.encode(CORPUS) == ids, "encoding survives save/load")
        check(loaded.decode(ids) == CORPUS, "decoding survives save/load")

    # 5. Same text always gives the same tokens.
    check(tok.encode(CORPUS) == tok.encode(CORPUS), "encoding is deterministic")

    print("\nAll Stage 2 tokenizer tests passed.")


if __name__ == "__main__":
    main()
