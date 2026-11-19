#!/usr/bin/env python3
"""A short, printable demonstration of the tokenizer. Run: python tokenizer/demo.py"""

from bpe_tokenizer import BPETokenizer

SAMPLE = (
    "EthanGPT is a tiny language model. "
    "EthanGPT is trained on text. "
    "EthanGPT turns text into numbers and numbers back into text."
)

tok = BPETokenizer().train(SAMPLE, vocab_size=160, verbose=True)

print("\nVocabulary size:", len(tok))
print("Learned merges:", len(tok.merges))

text = "EthanGPT turns text into numbers."
print("\nText:       ", repr(text))
print("Tokens:     ", tok.token_strings(text))
print("Token ids:  ", tok.encode(text, bos=True, eos=True))
print("Decoded:    ", repr(tok.decode(tok.encode(text, bos=True, eos=True))))

tok.save("tokenizer_demo.json")
print("\nVocabulary saved to tokenizer_demo.json")
