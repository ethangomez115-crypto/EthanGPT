"""
Tiny local HTTP server so the Android app (or any browser) can talk to EthanGPT.

Run from the project root:  python main.py serve
Then open http://<phone-ip>:8080 in a browser, or set that address in the app.
"""

import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


PAGE = """<!doctype html>
<html><head><meta charset="utf-8"><title>EthanGPT</title></head>
<body>
<h1>EthanGPT</h1>
<p>Offline chat server. Mode: <b>{mode}</b></p>
<form id="f">
<textarea id="m" rows="3" cols="60" placeholder="Ask something..."></textarea><br>
<button>Send</button>
</form>
<pre id="o"></pre>
<script>
const f=document.getElementById('f'), m=document.getElementById('m'), o=document.getElementById('o');
f.onsubmit=async e=>{{e.preventDefault();
 const r=await fetch('/chat',{{method:'POST',headers:{{'Content-Type':'application/json'}},
   body:JSON.stringify({{message:m.value}})}});
 const d=await r.json(); o.textContent += 'You: '+m.value+'\\nEthanGPT: '+d.reply+'\\n\\n';
 m.value='';}};
</script>
</body></html>"""


def make_handler(bot):
    """Create a request handler bound to a ChatBot instance."""

    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def _headers(self, status, content_type):
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Access-Control-Allow-Headers", "Content-Type")
            self.send_header("Connection", "close")
            self.end_headers()

        def do_GET(self):
            if self.path.split("?")[0] == "/info":
                tokenizer = getattr(bot, "tokenizer", None)
                model = getattr(bot, "model", None)
                info = {
                    "mode": bot.mode,
                    "vocab_size": len(tokenizer) if tokenizer else 0,
                    "parameter_count": getattr(model, "parameter_count", 0) if model else 0,
                    "order": getattr(model, "order", 0) if model else 0,
                    "history_length": len(getattr(bot, "history", [])),
                }
                body = json.dumps(info).encode("utf-8")
                self._headers(200, "application/json; charset=utf-8")
            else:
                body = PAGE.format(mode=bot.mode).encode("utf-8")
                self._headers(200, "text/html; charset=utf-8")
            self.wfile.write(body)

        def do_OPTIONS(self):
            self._headers(200, "text/plain")

        def do_POST(self):
            try:
                length = int(self.headers.get("Content-Length", 0))
                payload = json.loads(self.rfile.read(length).decode("utf-8"))
                message = payload.get("message", "")
                mode = payload.get("mode")
                reply = bot.respond(message, mode=mode)
                body = json.dumps({"reply": reply}).encode("utf-8")
                self._headers(200, "application/json; charset=utf-8")
            except Exception as exc:
                body = json.dumps({"reply": f"Server error: {exc}"}).encode("utf-8")
                self._headers(500, "application/json; charset=utf-8")
            self.wfile.write(body)

        def log_message(self, fmt, *args):
            pass  # keep phone logs quiet

    return Handler


def serve(bot, host="0.0.0.0", port=8080):
    server = ThreadingHTTPServer((host, port), make_handler(bot))
    print(f"EthanGPT server listening on http://{host}:{port} (mode={bot.mode})")
    print("Press Ctrl-C to stop.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopped.")
    finally:
        server.server_close()
