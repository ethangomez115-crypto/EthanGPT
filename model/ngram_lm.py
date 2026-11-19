"""
EthanGPT offline language model.

WHAT THIS IS
------------
A genuinely trainable, count-based N-gram language model. It learns from the
bundled corpus in seconds on a phone, needs no GPU, no internet and no external
libraries, and can generate text from a prompt.

It is NOT a transformer. The transformer (Stage 3) is still to come. This model
exists so the app has a real offline generator today, with a clearly stated
parameter count: every context row contains one probability per vocabulary
token, so parameter_count = number_of_contexts x vocabulary_size.
"""

import json
import math
import random
from collections import defaultdict


class NGramLanguageModel:
    """An order-N backoff language model trained on token ids."""

    def __init__(self, tokenizer, order=3):
        if order < 1:
            raise ValueError("order must be at least 1")
        self.tokenizer = tokenizer
        self.order = order
        # tables[n-1][context_tuple][next_token_id] = count
        self.tables = [defaultdict(lambda: defaultdict(int)) for _ in range(order)]

    # ------------------------------------------------------------------
    def train(self, text, verbose=False):
        """Count every context -> next-token event in `text`."""
        if not text:
            raise ValueError("Training text is empty")
        ids = self.tokenizer.encode(text, bos=False, eos=False)
        padded = [self.tokenizer.bos_id] * self.order + ids

        for index in range(self.order, len(padded)):
            for n in range(1, self.order + 1):
                context = tuple(padded[index - n:index])
                self.tables[n - 1][context][padded[index]] += 1

        if verbose:
            print(f"Trained on {len(ids)} tokens, order={self.order}")
            print(f"Parameter count: {self.parameter_count:,}")
        return self

    # ------------------------------------------------------------------
    @property
    def parameter_count(self):
        """Dense-equivalent parameter count (contexts x vocabulary)."""
        vocab = len(self.tokenizer)
        return sum(len(table) * vocab for table in self.tables)

    def __repr__(self):
        return (f"<NGramLanguageModel order={self.order} "
                f"params={self.parameter_count:,}>")

    # ------------------------------------------------------------------
    def _distribution(self, context):
        """Return counts for the longest context we have seen."""
        for n in range(min(len(context), self.order), 0, -1):
            key = tuple(context[-n:])
            counts = self.tables[n - 1].get(key)
            if counts:
                return counts
        return {}

    @staticmethod
    def _sample(counts, temperature, top_k, top_p, rng):
        if not counts:
            return None
        items = sorted(counts.items(), key=lambda item: -item[1])
        if top_k:
            items = items[:top_k]

        if temperature <= 0:
            return items[0][0]

        logits = [math.log(count + 1e-9) / temperature for _, count in items]
        top = max(logits)
        weights = [math.exp(value - top) for value in logits]
        total = sum(weights)
        probs = [weight / total for weight in weights]

        if top_p and top_p < 1.0:
            cumulative = 0.0
            keep = []
            for (token, _), prob in zip(items, probs):
                cumulative += prob
                keep.append((token, prob))
                if cumulative >= top_p:
                    break
            items = [token for token, _ in keep]
            probs = [prob for _, prob in keep]
        else:
            items = [token for token, _ in items]

        target = rng.random()
        running = 0.0
        for token, prob in zip(items, probs):
            running += prob
            if target <= running:
                return token
        return items[-1]

    # ------------------------------------------------------------------
    def generate(self, prompt, max_new_tokens=40, temperature=0.8,
                 top_k=10, top_p=0.9, seed=None):
        """Continue `prompt` with sampled tokens."""
        rng = random.Random(seed)
        context = [self.tokenizer.bos_id] * self.order
        context.extend(self.tokenizer.encode(prompt))
        generated = []

        for _ in range(max_new_tokens):
            counts = self._distribution(context)
            if not counts:
                break
            token = self._sample(counts, temperature, top_k, top_p, rng)
            if token is None:
                break
            generated.append(token)
            context.append(token)

        return self.tokenizer.decode(generated)

    # ------------------------------------------------------------------
    def save(self, path):
        """Persist the learned tables as JSON."""
        payload = {
            "format": "ethangpt-ngram",
            "version": 1,
            "order": self.order,
            "vocab_size": len(self.tokenizer),
            "tables": [],
        }
        for table in self.tables:
            serialised = {}
            for context, counts in table.items():
                serialised[",".join(str(i) for i in context)] = dict(counts)
            payload["tables"].append(serialised)
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(payload, handle)
        return path

    def load(self, path):
        """Load tables written by `save()`."""
        with open(path, "r", encoding="utf-8") as handle:
            payload = json.load(handle)
        if payload.get("format") != "ethangpt-ngram":
            raise ValueError("Not an EthanGPT N-gram model file.")
        self.order = payload["order"]
        self.tables = []
        for serialised in payload["tables"]:
            table = defaultdict(lambda: defaultdict(int))
            for key, counts in serialised.items():
                context = tuple(int(part) for part in key.split(","))
                table[context] = defaultdict(int, {
                    int(token): int(count) for token, count in counts.items()
                })
            self.tables.append(table)
        return self
