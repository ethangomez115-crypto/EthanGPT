# EthanGPT — build a small language model from scratch on a phone

**Status:** Stage 1 (device check), Stage 2 (BPE tokenizer), the **v2 upgrade
(offline 10M model + chatbot + browser/AI connectors)** and the **v3 inbuilt
Android app** are complete and tested. A neural transformer is still planned
(see Roadmap).

## What works right now

- **Tokenizer** (`tokenizer/bpe_tokenizer.py`) — byte-pair encoding from scratch.
- **Offline model** (`model/ngram_lm.py`) — a trainable count-based N-gram model
  with **10,874,880 dense-equivalent parameters**, no internet, no GPU, no
  third-party packages. It learns from `dataset/corpus.txt` in ~4 seconds and
  saves a 440 KB checkpoint.
- **Chatbot** (`chatbot/chat.py`) — four modes over one tokenizer and a shared
  conversation history:
  - `offline` — the local 10M model (always free, always available).
  - `opencode` — runs the free, open-source `opencode` CLI if installed.
  - `ollama` — talks to a local Ollama model server (free, local).
  - `browser` — opens a web search on the phone.
- **Local server** (`interface/server.py`) — optional HTTP chat endpoint so any
  browser on the network can talk to the model.
- **Android app v3** (separate `EthanGPT-Android` project) — the model is
  **built into the app**: the tokenizer and the 10M-parameter N-gram model are
  embedded as assets and run directly in Java. **No Termux, no server, no
  internet.** It has a chat screen plus Web, Info and Clear buttons. The whole
  app is ~97 KB and requests no permissions at all.
- **Android export tool** (`tools/export_android_model.py`) — converts the
  trained Python model into the compact `model.txt` the app loads, and writes
  tokenizer test vectors that the Java port is checked against.

> **Honesty note:** the offline model is a **count-based N-gram language model**,
> not a neural transformer. Its "parameters" are a clearly-labelled
> dense-equivalent count (contexts × vocabulary). It is genuinely trainable and
> runs fully offline — we simply do not pretend it is a large neural network.
> If a provider behind a free connector charges, that is outside EthanGPT;
> offline mode is always free.

---

## What we are building today

Before writing a single line of model code, we need to know what this phone can
actually handle. A "large" language model on a 4 GB phone would be killed by
Android before it finishes one training step. So Stage 1 produces three real
things:

1. A measured device profile (`config/device_profile.json`).
2. A repeatable check script you can run yourself (`config/environment_check.py`).
3. A one-command Termux setup script (`stage1/setup_termux.sh`).

No theory-only answer — these are files you can run.

---

## Why this stage is necessary

A transformer's memory use has three parts:

- **Weights:** `number_of_parameters × 4 bytes` (float32).
- **Activations:** temporary arrays created during training; usually several
  times larger than the weights.
- **Gradients + optimizer state:** for Adam, roughly 3× the weight size.

On this phone only about **1.3 GB of RAM is free**, so a sensible first target
is a model of roughly **1–3 million parameters (~4–12 MB of weights)** and a
training loop that stays under **~700 MB**. That is small by LLM standards, but
it is enough to learn character-level patterns, short sentences and simple
question-answer data.

Real device data recorded today:

| Item | Measured value |
|---|---|
| Device | Samsung Galaxy A06 5G |
| RAM | 4 GB total, 2.7 GB used, 588 MB reserved, ~1.3 GB free |
| RAM Plus (swap) | 4 GB — flash storage, slow; do not rely on it |
| Storage | 64 GB total, ~3 GB free |
| Python environment | Termux (`com.termux`) already installed and bootstrapped |
| CPU | ARM 64-bit (confirm with the check script) |
| PyTorch | Not usable in Termux — we use NumPy only |

Note: `RAM Plus` makes Android *look* like it has more memory. It is disk
swapping, which is very slow, so the model must fit in real RAM.

---

## How to install Python on Android (Termux)

You already have Termux, but these are the steps for a fresh phone:

1. Install **Termux from F-Droid** (`org.fdroid.fdroid`). The old Play Store
   build is outdated and its packages are broken.
2. Open Termux once and let it finish "Installing bootstrap packages".
3. Give it storage access:

   ```bash
   termux-setup-storage
   ```

   A permission dialog appears — accept it. This creates
   `~/storage/shared`, pointing at your phone's shared storage.

4. Install Python and NumPy:

   ```bash
   pkg update -y
   pkg upgrade -y
   pkg install -y python unzip
   pkg install -y numpy
   python --version
   ```

   If `numpy` is not found, use the older package name:

   ```bash
   pkg install -y python-numpy
   ```

5. From a PC or Buddy, copy `EthanGPT.zip` to
   **Internal storage → Download → Buddy → EthanGPT.zip**, then in Termux:

   ```bash
   cd ~
   unzip /sdcard/Download/Buddy/EthanGPT.zip -d EthanGPT
   cd ~/EthanGPT
   ```

Alternatively, the whole setup is one command:

```bash
bash ~/EthanGPT/stage1/setup_termux.sh
```

---

## Compatible libraries, and alternatives if PyTorch fails

| Intended technology | On this phone | What we do |
|---|---|---|
| Python 3.9+ | Yes, via Termux | Standard library + NumPy |
| NumPy | Yes, `pkg install numpy` | All matrix math and manual backprop |
| PyTorch | **No** (no Termux package) | Reimplemented as `model/` in NumPy |
| Tokenizers / SentencePiece | Likely no | Own tokenizer in `tokenizer/` (Stage 2) |
| Transformers (`pip install transformers`) | No | Write the architecture ourselves (Stage 3) |
| Google Colab | Optional only | Use it if you later want a bigger model |

This is a genuine from-scratch implementation: no pretrained weights and no
library that supplies the model.

---

## Storage and RAM budget

- Termux + Python + NumPy: roughly **150–250 MB**.
- Project source and datasets at this stage: **well under 1 MB**.
- A 1–3 M parameter model: **4–12 MB** per checkpoint.
- Keep at least **1 GB free storage** so checkpoints and datasets fit.
- Close Chrome, YouTube and social apps before training; they are the main
  reason only ~1.3 GB is free right now.

---

## How to verify Stage 1 works (the test)

In Termux:

```bash
cd ~/EthanGPT
python config/environment_check.py
```

Expected output — sections for Python, device, RAM, storage, libraries and
folders, ending with:

```
  READY FOR STAGE 2
  Report saved to: .../EthanGPT/config/device_report.json
```

If it says `NOT READY YET`, the line underneath tells you the exact fix (usually
`pkg install -y numpy`, freeing 1 GB, or closing apps). The script exits with
status 0 when ready and 1 when not, so it can be used in scripts.

The script also writes `config/device_report.json` with machine-readable values.
That file is our baseline: later we can compare it after training to see whether
we stayed inside the memory budget.

---

## Project structure built so far

```
EthanGPT/
├── tokenizer/          # Stage 2: BPE tokenizer (built and tested)
├── dataset/            # Stage 4: training data (empty for now)
├── model/              # Stage 3: NumPy transformer (empty for now)
├── training/           # Stage 4: training loop (empty for now)
├── checkpoints/        # Stage 6: saved weights (empty for now)
├── chatbot/            # Stage 5 + 7: generation and chat (empty for now)
├── interface/          # Stage 7: CLI, later a simple Android UI (empty for now)
├── config/
│   ├── device_profile.json     # measured phone profile
│   ├── environment_check.py    # Stage 1 test script
│   └── device_report.json      # written when you run the test
├── stage1/
│   └── setup_termux.sh         # one-command Termux setup
├── main.py                     # wired up in a later stage
├── requirements.txt            # NumPy only
└── README.md                   # this guide
```

Empty folders contain a `.gitkeep` so the structure is preserved in the zip.

---

## Troubleshooting Stage 1

**`pkg update` hangs or says "repository is unreachable"**
Switch network (mobile data ↔ Wi-Fi) and rerun. Termux uses community mirrors;
one may be down. Try `termux-change-repo`.

**`python: command not found`**
Run `pkg install -y python` and check `which python`.

**`ModuleNotFoundError: No module named 'numpy'`**
Run `pkg install -y numpy` (or `python-numpy`), then
`python -c "import numpy; print(numpy.__version__)"`.

**`Permission denied` when unzipping**
Run `termux-setup-storage` and accept the storage permission.

**Check script reports very low free RAM**
Close other apps, then rerun. If it stays below ~0.8 GB available, we will
shrink the planned model in Stage 3.

**Termux crashes or gets killed while installing**
It is usually storage pressure. Free space first; only ~3 GB is free now.

---

## Stage 2 — completed

`tokenizer/bpe_tokenizer.py` implements a character-level BPE tokenizer with no
third-party libraries. Full explanation and commands are in
`tokenizer/README.md`.

- Reserved ids: `<pad>`=0, `<bos>`=1, `<eos>`=2, `<unk>`=3.
- `train()` learns merges inside words/whitespace runs, so text never collapses
  into one giant token.
- `encode()` / `decode()` round-trip exactly; `save()` / `load()` persist JSON.
- Verified with `python tokenizer/test_tokenizer.py` — 10 checks, all passing.

## v2 upgrade — offline model, chatbot, server

### Install (Termux, Android)

```bash
pkg install -y python numpy
cd ~
# unzip the EthanGPT project here if you have not already
cd EthanGPT
python main.py info          # trains the 10M model on first run, prints sizes
python main.py chat          # interactive chat (type /mode opencode, /clear, /quit)
python main.py serve         # start the local HTTP server for the app/browser
```

### Commands

| Command | What it does |
|---|---|
| `python main.py check` | repeat the Stage 1 device/environment check |
| `python main.py info` | train/load the model and print vocabulary + parameter count |
| `python main.py chat` | chat in the terminal; switch modes with `/mode` |
| `python main.py serve` | serve the chat UI at `http://<phone-ip>:8080` |

### Chat modes

- **offline** — `Q: ... / A:` prompts answered by the local N-gram model. No
  network. This is the default and the reason the app works with no signal.
- **opencode** — calls `opencode run "<message>"` if the CLI is on PATH; falls
  back with a clear message if it is not installed.
- **ollama** — POSTs to `http://127.0.0.1:11434/api/generate` using the model in
  `config/settings.json` (`qwen2.5:0.5b` by default).
- **browser** — opens `browser_url_template` with your question URL-encoded.

Change defaults in `config/settings.json`: `mode`, `offline_order`,
`vocab_size`, `max_new_tokens`, `temperature`, `top_k`, `top_p`, `ollama_url`,
`ollama_model`, `browser_url_template`, `server_host`, `server_port`.

### HTTP API (for the Android app)

- `GET /` — a small chat web page.
- `GET /info` — JSON: `mode`, `vocab_size`, `parameter_count`, `order`,
  `history_length`.
- `POST /chat` with `{"message": "...", "mode": "offline"}` — returns
  `{"reply": "..."}`. `mode` is optional.

The server is optional and mainly for desktop/browser use. **The Android app no
longer needs it** — see the inbuilt app section below.

### Tests

```bash
python tokenizer/test_tokenizer.py   # 11 tokenizer checks
python test_system.py                # 14 end-to-end checks (model, chatbot, server)
```

Both suites pass. `test_system.py` verifies the *shipped* configuration reaches
at least 1,000,000 parameters (currently 10,874,880), deterministic generation,
save/load, all four chatbot modes degrading safely, and the HTTP API.

### Model size and memory

The offline model stores sparse counts but reports a dense-equivalent parameter
count of `sum(contexts × vocabulary)` across N-gram orders. With
`vocab_size=768` and `offline_order=9` on the bundled corpus this is
**10,874,880 parameters**, trains in about 4 seconds, and produces a 440 KB
checkpoint in `checkpoints/`. It fits easily in the phone's ~1.3 GB free RAM.
Delete `checkpoints/ngram.json` and `checkpoints/tokenizer.json` to retrain after
editing the corpus or settings.

## v3 — inbuilt Android app (no Termux, no server)

The app is now self-contained. The trained tokenizer and N-gram model are
exported to `android/assets/model.txt` (~411 KB) and embedded in the APK, then
loaded and run by pure-Java ports of the Python code:

| Java class | Port of | Job |
|---|---|---|
| `BPETokenizer.java` | `tokenizer/bpe_tokenizer.py` | encode/decode text |
| `NGramModel.java` | `model/ngram_lm.py` | backoff lookup + sampling |
| `ChatBot.java` | `chatbot/chat.py` (offline) | `Q:/A:` replies + history |
| `ModelLoader.java` | `tools/export_android_model.py` | parse `model.txt` |

The port is verified on a desktop JVM before building: `desktoptest/SelfTest`
checks 19 tokenizer vectors against the Python output (all match) and the exact
10,874,880 parameter count.

To regenerate the embedded model after changing the corpus/settings:

```bash
python3 tools/export_android_model.py \
    ../EthanGPT-Android/assets ../EthanGPT-Android/desktoptest/vectors.txt
bash ~/.buddy/work/.claude/skills/android-app/build-apk.sh ../EthanGPT-Android
```

The app requests **no permissions** and makes **no network calls**; the Web
button only hands a search to your browser. It installs as an update over v2
(versionCode 3) and keeps your data.

## Roadmap (planned, not yet built)
- **Stage 3** — decoder-only transformer in NumPy: embeddings, positional
  encoding, self-attention, multi-head attention, feed-forward, blocks.
- **Stage 4** — dataset, batching, loss, manual backprop, Adam, checkpoints.
- **Stage 5** — train the neural transformer and connect generation to chat.
- **Stage 6** — evaluation, better data, scaling within the phone's limits.
