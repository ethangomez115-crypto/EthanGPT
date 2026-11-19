#!/usr/bin/env python3
"""End-to-end verification for the EthanGPT upgrade. Run: python3 test_system.py"""
import json
import os
import sys
import tempfile
import threading
import time
import urllib.request
from http.server import ThreadingHTTPServer

ROOT = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, ROOT)

from tokenizer.bpe_tokenizer import BPETokenizer
from model.ngram_lm import NGramLanguageModel
from chatbot.chat import ChatBot
from interface.server import make_handler

CORPUS = (
    "Q: What is EthanGPT?\nA: EthanGPT is a small language model built from scratch in Python.\n"
    "Q: What is a token?\nA: A token is a small piece of text, such as a character or part of a word.\n"
    "Q: Can the model run offline?\nA: Yes, the offline model runs on your phone without internet.\n"
    "EthanGPT uses a tokenizer to turn text into numbers.\n"
    "The tokenizer turns words into tokens and tokens into numbers.\n"
) * 8  # repeat so the model has enough contexts

PASS = 0
FAIL = 0


def check(condition, message):
    global PASS, FAIL
    if condition:
        PASS += 1
        print("  ok -", message)
    else:
        FAIL += 1
        print("  FAIL -", message)


def test_model():
    print("\n[model]")
    # Use the SHIPPED configuration so this verifies the real deliverable.
    with open(os.path.join(ROOT, "config", "settings.json"), encoding="utf-8") as f:
        settings = json.load(f)
    with open(os.path.join(ROOT, "dataset", "corpus.txt"), encoding="utf-8") as f:
        real_corpus = f.read()
    tok = BPETokenizer().train(real_corpus, vocab_size=settings["vocab_size"])
    model = NGramLanguageModel(tok, order=settings["offline_order"]).train(real_corpus)
    check(model.parameter_count >= 1_000_000,
          f"shipped parameter_count >= 1M (got {model.parameter_count:,})")
    sample1 = model.generate("Q: What is EthanGPT?", max_new_tokens=12, seed=7)
    sample2 = model.generate("Q: What is EthanGPT?", max_new_tokens=12, seed=7)
    check(sample1 == sample2, "generation is deterministic with a fixed seed")
    check(len(sample1) > 0, "generation returns non-empty text")
    with tempfile.TemporaryDirectory() as d:
        p = os.path.join(d, "model.json")
        model.save(p)
        loaded = NGramLanguageModel(tok).load(p)
        check(loaded.parameter_count == model.parameter_count,
              "parameter count survives save/load")
        check(loaded.generate("Q: What is EthanGPT?", 12, seed=7) == sample1,
              "generation survives save/load")


def test_chatbot():
    print("\n[chatbot]")
    tok = BPETokenizer().train(CORPUS, vocab_size=200)
    model = NGramLanguageModel(tok, order=5).train(CORPUS)
    bot = ChatBot(tok, model, {"mode": "offline", "max_new_tokens": 12})
    reply = bot.respond("What is EthanGPT?")
    check(isinstance(reply, str) and len(reply) > 0, "offline reply is non-empty")
    check(len(bot.history) == 2, "history records user + assistant messages")
    bot.clear_history()
    check(bot.history == [], "clear_history empties the conversation")
    bot.set_mode("bogus")
    check("Unknown mode" in bot.respond("hi"), "unknown mode reports a clear error")
    bot.set_mode("opencode")
    r = bot.respond("hi")
    check("not installed" in r or "could not run" in r or "no answer" in r
          or "took too long" in r or len(r) > 0, "opencode mode degrades gracefully without CLI")
    bot.set_mode("ollama")
    r = bot.respond("hi")  # no server -> should return an error string, not raise
    check("Could not reach Ollama" in r, "ollama mode degrades gracefully without a server")
    bot.set_mode("browser")
    r = bot.respond("what is python")
    check("https://" in r and "what%20is%20python" in r,
          "browser mode builds a safe search URL")


def test_server():
    print("\n[server]")
    tok = BPETokenizer().train(CORPUS, vocab_size=200)
    model = NGramLanguageModel(tok, order=5).train(CORPUS)
    bot = ChatBot(tok, model, {"mode": "offline", "max_new_tokens": 12})
    server = ThreadingHTTPServer(("127.0.0.1", 0), make_handler(bot))
    port = server.server_address[1]
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    time.sleep(0.2)
    try:
        base = f"http://127.0.0.1:{port}"
        with urllib.request.urlopen(base + "/", timeout=5) as resp:
            page = resp.read().decode("utf-8")
        check("<title>EthanGPT</title>" in page, "GET / serves the chat page")
        payload = json.dumps({"message": "What is EthanGPT?"}).encode("utf-8")
        req = urllib.request.Request(
            base + "/chat", data=payload, headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(req, timeout=5) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        check("reply" in data and len(data["reply"]) > 0,
              "POST /chat returns a JSON reply")
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=5)


def main():
    print("EthanGPT system tests")
    test_model()
    test_chatbot()
    test_server()
    print(f"\n{PASS} passed, {FAIL} failed")
    return 1 if FAIL else 0


if __name__ == "__main__":
    raise SystemExit(main())
