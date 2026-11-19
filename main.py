#!/usr/bin/env python3
"""
EthanGPT entry point.

Commands:
  check   - Stage 1 environment check
  info    - show tokenizer/model sizes and mode
  chat    - interactive chat in the terminal
  serve   - start the local HTTP server for the Android app / browser
"""

import argparse
import json
import os
import runpy
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, ROOT)

from tokenizer.bpe_tokenizer import BPETokenizer      # noqa: E402
from model.ngram_lm import NGramLanguageModel        # noqa: E402
from chatbot.chat import ChatBot                     # noqa: E402


def read_settings():
    path = os.path.join(ROOT, "config", "settings.json")
    with open(path, "r", encoding="utf-8") as handle:
        return json.load(handle)


def load_components(settings=None):
    """Load or build the tokenizer and offline model, caching both."""
    settings = settings or read_settings()
    checkpoint_dir = os.path.join(ROOT, "checkpoints")
    os.makedirs(checkpoint_dir, exist_ok=True)
    tok_path = os.path.join(checkpoint_dir, "tokenizer.json")
    model_path = os.path.join(checkpoint_dir, "ngram.json")
    corpus_path = os.path.join(ROOT, "dataset", "corpus.txt")

    with open(corpus_path, "r", encoding="utf-8") as handle:
        corpus = handle.read()

    if os.path.exists(tok_path):
        tokenizer = BPETokenizer.load(tok_path)
    else:
        tokenizer = BPETokenizer().train(corpus, settings.get("vocab_size", 200))
        tokenizer.save(tok_path)

    if os.path.exists(model_path):
        model = NGramLanguageModel(tokenizer).load(model_path)
    else:
        model = NGramLanguageModel(tokenizer, order=settings.get("offline_order", 5))
        model.train(corpus, verbose=True)
        model.save(model_path)

    return tokenizer, model, settings


def make_bot():
    tokenizer, model, settings = load_components()
    return ChatBot(tokenizer, model, settings), tokenizer, model


def command_check(_args):
    script = os.path.join(ROOT, "config", "environment_check.py")
    runpy.run_path(script, run_name="__main__")
    return 0


def command_info(_args):
    _bot, tokenizer, model = make_bot()
    print("EthanGPT information")
    print(f"  Vocabulary:      {len(tokenizer):,} tokens")
    print(f"  Offline model:   {model}")
    print(f"  Context order:   {model.order}")
    print(f"  Default mode:    {read_settings().get('mode')}")
    print("  Modes:           offline, opencode, ollama, browser")
    return 0


def command_chat(args):
    bot, _tokenizer, model = make_bot()
    print(f"EthanGPT chat - mode: {bot.mode}")
    print(f"Offline model: {model.parameter_count:,} parameters")
    print("Commands: /mode offline|opencode|ollama|browser, /clear, /quit\n")
    while True:
        try:
            message = input("You: ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\nBye.")
            return 0
        if not message:
            continue
        if message == "/quit":
            return 0
        if message == "/clear":
            bot.clear_history()
            print("History cleared.")
            continue
        if message.startswith("/mode "):
            new_mode = message.split(None, 1)[1].strip()
            bot.set_mode(new_mode)
            print(f"Mode is now {new_mode}.")
            continue
        print("EthanGPT:", bot.respond(message))


def command_serve(args):
    from interface.server import serve
    bot, _tokenizer, model = make_bot()
    print(f"Offline model: {model.parameter_count:,} parameters")
    serve(bot, args.host, args.port)
    return 0


def main():
    parser = argparse.ArgumentParser(description="EthanGPT")
    sub = parser.add_subparsers(dest="command")
    sub.add_parser("check", help="check the phone environment")
    sub.add_parser("info", help="show model information")
    sub.add_parser("chat", help="interactive chat")
    serve_parser = sub.add_parser("serve", help="start the local HTTP server")
    serve_parser.add_argument("--host", default=None)
    serve_parser.add_argument("--port", type=int, default=None)

    args = parser.parse_args()
    settings = read_settings()
    if args.command == "serve":
        args.host = args.host or settings.get("server_host", "0.0.0.0")
        args.port = args.port or settings.get("server_port", 8080)

    handlers = {
        None: command_info,
        "check": command_check,
        "info": command_info,
        "chat": command_chat,
        "serve": command_serve,
    }
    return handlers[args.command](args)


if __name__ == "__main__":
    raise SystemExit(main())
