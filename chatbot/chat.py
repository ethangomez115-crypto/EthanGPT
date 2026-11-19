"""
EthanGPT chatbot.

Four modes, all using the same tokenizer and conversation history:

  offline  - local N-gram model, no internet (always available)
  opencode - runs the free, open-source `opencode` CLI if it is installed
  ollama   - talks to a local Ollama server (also free, local model)
  browser  - opens a browser page with the question

No API keys are stored here. If a provider requires payment, that is outside
EthanGPT; the offline mode is always free and works without a connection.
"""

import json
import shutil
import subprocess
import urllib.parse
import urllib.request
import webbrowser


class ChatBot:
    """A small chat wrapper with pluggable backends."""

    def __init__(self, tokenizer, model=None, settings=None):
        self.tokenizer = tokenizer
        self.model = model
        self.settings = settings or {}
        self.history = []

    # ------------------------------------------------------------------
    def clear_history(self):
        self.history = []

    def set_mode(self, mode):
        self.settings["mode"] = mode

    @property
    def mode(self):
        return self.settings.get("mode", "offline")

    # ------------------------------------------------------------------
    def respond(self, message, mode=None, max_new_tokens=None):
        """Return a reply string for `message`."""
        message = (message or "").strip()
        if not message:
            return "Please type a message."

        active_mode = mode or self.mode
        self.history.append({"role": "user", "content": message})

        if active_mode == "offline":
            reply = self._reply_offline(message, max_new_tokens)
        elif active_mode == "opencode":
            reply = self._reply_opencode(message)
        elif active_mode == "ollama":
            reply = self._reply_ollama(message)
        elif active_mode == "browser":
            reply = self._reply_browser(message)
        else:
            reply = f"Unknown mode '{active_mode}'. Try offline, opencode, ollama or browser."

        self.history.append({"role": "assistant", "content": reply})
        return reply

    # ------------------------------------------------------------------
    def _reply_offline(self, message, max_new_tokens):
        if self.model is None:
            return "Offline model is not loaded yet."
        limit = max_new_tokens or self.settings.get("max_new_tokens", 60)
        prompt = "Q: " + message + "\nA:"
        answer = self.model.generate(
            prompt,
            max_new_tokens=limit,
            temperature=self.settings.get("temperature", 0.8),
            top_k=self.settings.get("top_k", 10),
            top_p=self.settings.get("top_p", 0.9),
        ).strip()
        if not answer:
            return ("I do not know an answer from my small offline training data. "
                    "Try browser or opencode mode for this question.")
        return answer

    def _reply_opencode(self, message):
        if shutil.which("opencode") is None:
            return ("Open Code CLI is not installed in this Python environment. "
                    "Install it on your computer/phone PATH, or use offline mode.")
        try:
            result = subprocess.run(
                ["opencode", "run", message],
                capture_output=True, text=True, timeout=120,
            )
            output = (result.stdout or "").strip()
            if not output:
                output = (result.stderr or "").strip()
            return output or "Open Code returned no answer."
        except subprocess.TimeoutExpired:
            return "Open Code took too long. Try a shorter prompt."
        except Exception as exc:
            return f"Open Code could not run: {exc}"

    def _reply_ollama(self, message):
        url = self.settings.get("ollama_url", "http://127.0.0.1:11434/api/generate")
        model_name = self.settings.get("ollama_model", "qwen2.5:0.5b")
        payload = json.dumps({
            "model": model_name, "prompt": message, "stream": False,
        }).encode("utf-8")
        request = urllib.request.Request(
            url, data=payload, headers={"Content-Type": "application/json"}
        )
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                data = json.loads(response.read().decode("utf-8"))
            return data.get("response", "").strip() or "The local model returned no answer."
        except Exception as exc:
            return f"Could not reach Ollama at {url}: {exc}"

    def _reply_browser(self, message):
        template = self.settings.get(
            "browser_url_template", "https://www.google.com/search?q={q}"
        )
        url = template.replace("{q}", urllib.parse.quote(message))
        try:
            webbrowser.open(url)
        except Exception:
            pass  # headless environments simply print the link
        return ("Opened the browser with your question. If nothing opened, "
                "copy this link: " + url)
