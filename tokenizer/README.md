# Stage 2 — Tokenizer from scratch

**Goal:** turn text into numbers and back, using only the Python standard
library. Stage 3 will feed these numbers into the neural network.

## What we built

`bpe_tokenizer.py` — a character-level Byte-Pair Encoding tokenizer:

- `train(text, vocab_size)` learns merges from a corpus.
- `encode(text, bos=False, eos=False)` returns a list of integer ids.
- `decode(ids, skip_special=True)` turns ids back into text.
- `save(path)` / `BPETokenizer.load(path)` persist the vocabulary and merges.
- `token_strings(text)` shows the pieces, for learning/debugging.

`test_tokenizer.py` — five checks that the stage works.
`demo.py` — a printable walkthrough that also saves a vocabulary file.

## Why a tokenizer is necessary

A neural network multiplies matrices. It can only consume numbers. The tokenizer
is the bridge between human text and the model:

```
text -> tokens -> ids -> embedding vectors -> transformer -> ids -> text
```

## Key ideas

**Token:** a small piece of text (a character, part of a word, a whole word, or
punctuation). `"EthanGPT"` might become `["Ethan", "GPT"]`.

**Vocabulary:** the list of all known tokens, each with a unique id. The id of a
token is the row index in the model's embedding matrix, so vocabulary size
directly determines model size. We use four reserved tokens:

| Token | Id | Meaning |
|---|---|---|
| `<pad>` | 0 | padding for batching |
| `<bos>` | 1 | beginning of sequence |
| `<eos>` | 2 | end of sequence |
| `<unk>` | 3 | character never seen in training |

**Encoding:** split into characters, then repeatedly apply the learned merges
in rank order, then map each token string to its id.

**BPE training:** count all neighbouring pairs, merge the most frequent pair,
and repeat until the vocabulary is big enough. Frequent strings like `" th"`
become one token, which shortens sequences.

**Decoding:** look up each id's token string and concatenate them. Because our
base alphabet is characters, this is exact — no lossy reconstruction.

## Run on Android (Termux)

```bash
cd ~/EthanGPT
python tokenizer/test_tokenizer.py
python tokenizer/demo.py
```

No `pip install` is needed for this stage. If you have not installed Python yet:

```bash
pkg install -y python
```

## Expected test result

```
Vocabulary size: 200
Learned merges:  ...
  ok - vocabulary stayed within its budget
  ok - at least one merge was learned
  ok - encode -> decode is lossless on training text
  ok - BOS and EOS ids are added at the right ends
  ok - special tokens are hidden when decoding
  ok - unknown characters map to <unk>
  ok - vocabulary size survives save/load
  ok - encoding survives save/load
  ok - decoding survives save/load
  ok - encoding is deterministic

All Stage 2 tokenizer tests passed.
```

## Troubleshooting

**`ModuleNotFoundError: No module named 'bpe_tokenizer'`**
Run the script from the project root (`cd ~/EthanGPT` first), or run
`python tokenizer/test_tokenizer.py` exactly as shown — the script adds its own
folder to the import path implicitly.

**Vocabulary smaller than requested**
The corpus may contain too few repeated pairs. Add more text or lower
`vocab_size`.

**Unicode characters become `<unk>`**
They were not in the training corpus. Add them to the corpus, or accept `<unk>`
as the model's way of seeing an unfamiliar character.

**Ids look large**
Ids start at 4 because the four special tokens occupy 0–3. That is normal.

## What is deliberately NOT done yet

- No training data file yet (Stage 4).
- No neural network yet (Stage 3).
- The demo corpus is tiny, so its merges are not useful for real generation.

Next stage: the decoder-only transformer in NumPy.
