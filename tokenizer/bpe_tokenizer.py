"""
EthanGPT - Stage 2: a byte-pair-encoding (BPE) tokenizer built from scratch.

WHAT IS A TOKEN?
----------------
A neural network cannot read text; it reads numbers. A tokenizer splits text
into small pieces called tokens and gives each piece an integer id.

    "EthanGPT is small"  ->  ["Ethan", "GPT", " is", " small"]  ->  [12, 87, 4, 9]

THE VOCABULARY
--------------
The vocabulary is the dictionary mapping each token string to an id. Its size
is the number of rows the model's embedding matrix must have, so a smaller
vocabulary means a smaller model.

WHY BPE?
--------
Character-level tokenizers are simple but produce very long sequences. Word
tokenizers break on unfamiliar words. BPE starts from characters and repeatedly
merges the most frequent neighbouring pair, giving short tokens for common
patterns while still being able to spell anything. GPT-style models use this
family of tokenizers.

This implementation uses characters as its base alphabet, so it handles any
Unicode text and decoding is exact string concatenation.

No third-party libraries are needed - only the Python standard library.
"""

import json
import re
from collections import Counter


DEFAULT_SPECIAL_TOKENS = ["<pad>", "<bos>", "<eos>", "<unk>"]


class BPETokenizer:
    """A character-level BPE tokenizer with save/load support."""

    def __init__(self, special_tokens=None):
        self.special_tokens = list(special_tokens or DEFAULT_SPECIAL_TOKENS)
        self.vocab = {}
        self.id_to_token = {}
        self.merges = []          # list of (left_token, right_token), in rank order
        self._reset_vocab()

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------
    def _reset_vocab(self):
        self.vocab = {}
        for token in self.special_tokens:
            self.vocab[token] = len(self.vocab)
        self._sync_id_to_token()

    def _sync_id_to_token(self):
        self.id_to_token = {idx: token for token, idx in self.vocab.items()}

    def _add_token(self, token):
        """Add a token if new; return True when it was added."""
        if token in self.vocab:
            return False
        self.vocab[token] = len(self.vocab)
        self.id_to_token[len(self.vocab) - 1] = token
        return True

    @staticmethod
    def _pre_tokenize(text):
        """
        Split text into words and whitespace runs before merging.

        Why: if the whole corpus were one sequence, BPE would eventually merge
        the entire text into a single token. Real BPE counts pairs *inside*
        pre-tokens, so merges never cross word boundaries.
        """
        return re.findall(r"\S+|\s+", text)

    @staticmethod
    def _merge_sequence(sequence, pair):
        """Apply one merge to a list of token strings."""
        left, right = pair
        result = []
        index = 0
        while index < len(sequence):
            if (
                index + 1 < len(sequence)
                and sequence[index] == left
                and sequence[index + 1] == right
            ):
                result.append(left + right)
                index += 2
            else:
                result.append(sequence[index])
                index += 1
        return result

    @property
    def unk_id(self):
        return self.vocab["<unk>"]

    @property
    def pad_id(self):
        return self.vocab["<pad>"]

    @property
    def bos_id(self):
        return self.vocab["<bos>"]

    @property
    def eos_id(self):
        return self.vocab["<eos>"]

    def __len__(self):
        return len(self.vocab)

    # ------------------------------------------------------------------
    # Training
    # ------------------------------------------------------------------
    def train(self, text, vocab_size=300, verbose=False):
        """
        Learn merges from `text` until the vocabulary reaches `vocab_size`.

        Step 1: start with every unique character as a token.
        Step 2: count every adjacent pair of tokens.
        Step 3: merge the most frequent pair into one new token.
        Step 4: repeat until the target size is reached.
        """
        if not isinstance(text, str) or not text:
            raise ValueError("train() needs a non-empty string of training text.")
        if vocab_size <= len(self.special_tokens):
            raise ValueError("vocab_size must be larger than the special tokens.")

        self.merges = []
        self._reset_vocab()

        # Step 1: character alphabet.
        for character in sorted(set(text)):
            self._add_token(character)

        # Each word/whitespace run is merged independently, never across boundaries.
        sequences = [list(chunk) for chunk in self._pre_tokenize(text)]

        # Steps 2-4: greedy frequency-based merges.
        while len(self.vocab) < vocab_size:
            pair_counts = Counter()
            for sequence in sequences:
                pair_counts.update(zip(sequence, sequence[1:]))
            if not pair_counts:
                break

            # Sort by descending frequency, then alphabetically for determinism.
            ranked = sorted(
                pair_counts.items(), key=lambda item: (-item[1], item[0])
            )
            merged = False
            for pair, count in ranked:
                combined = pair[0] + pair[1]
                if combined in self.vocab:
                    continue  # would not grow the vocabulary

                sequences = [self._merge_sequence(seq, pair) for seq in sequences]
                self.merges.append(tuple(pair))
                self._add_token(combined)
                merged = True
                if verbose:
                    print(
                        f"  merge {len(self.merges):3d}: {pair!r} -> {combined!r} "
                        f"(seen {count}x, vocab {len(self.vocab)})"
                    )
                break

            if not merged:
                break  # no useful merge left

        return self

    # ------------------------------------------------------------------
    # Encoding and decoding
    # ------------------------------------------------------------------
    def encode(self, text, bos=False, eos=False):
        """Turn text into a list of integer token ids."""
        if not isinstance(text, str):
            raise TypeError("encode() expects a string.")

        # Apply learned merges in rank order, inside each pre-token.
        sequences = [list(chunk) for chunk in self._pre_tokenize(text)]
        for pair in self.merges:
            sequences = [self._merge_sequence(seq, pair) for seq in sequences]
        tokens = [token for sequence in sequences for token in sequence]

        ids = [self.vocab.get(token, self.unk_id) for token in tokens]
        if bos:
            ids.insert(0, self.bos_id)
        if eos:
            ids.append(self.eos_id)
        return ids

    def decode(self, ids, skip_special=True):
        """Turn integer token ids back into text."""
        pieces = []
        for token_id in ids:
            token = self.id_to_token.get(int(token_id), "<unk>")
            if skip_special and token in self.special_tokens:
                continue
            pieces.append(token)
        return "".join(pieces)

    def token_strings(self, text):
        """Return the token strings for `text` - useful for teaching/debugging."""
        sequences = [list(chunk) for chunk in self._pre_tokenize(text)]
        for pair in self.merges:
            sequences = [self._merge_sequence(seq, pair) for seq in sequences]
        return [token for sequence in sequences for token in sequence]

    # ------------------------------------------------------------------
    # Persistence
    # ------------------------------------------------------------------
    def save(self, path):
        """Save vocabulary and merges to a JSON file."""
        payload = {
            "format": "ethangpt-bpe",
            "version": 1,
            "special_tokens": self.special_tokens,
            "vocab": self.vocab,
            "merges": [list(pair) for pair in self.merges],
        }
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(payload, handle, ensure_ascii=False, indent=2)
        return path

    @classmethod
    def load(cls, path):
        """Rebuild a tokenizer from a JSON file saved by `save()`."""
        with open(path, "r", encoding="utf-8") as handle:
            payload = json.load(handle)
        if payload.get("format") != "ethangpt-bpe":
            raise ValueError("Not an EthanGPT BPE tokenizer file.")

        tokenizer = cls(payload.get("special_tokens", DEFAULT_SPECIAL_TOKENS))
        tokenizer.vocab = {token: int(idx) for token, idx in payload["vocab"].items()}
        tokenizer.merges = [tuple(pair) for pair in payload.get("merges", [])]
        tokenizer._sync_id_to_token()
        return tokenizer
