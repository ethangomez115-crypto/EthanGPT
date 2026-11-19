# EthanGPT — Stage 1: Phone capability check and Python environment

**Status:** Stage 1 is complete. Stages 2–9 are planned but not built yet.
We are building one stage at a time, and I wait for your confirmation before
starting the next one.

**Also included:** an Android companion app (`android/`) with a signed APK at
`android/build/app.apk`. It is an honest chat-style UI: the model is not trained
yet, so it never pretends to give model answers. The source of this repository
is public on GitHub so you can track progress.

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
├── tokenizer/          # Stage 2: from-scratch tokenizer (empty for now)
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

## Roadmap (planned, not yet built)

- **Stage 2** — tokenizer from scratch: vocabulary, encode, decode, save/load.
- **Stage 3** — decoder-only transformer in NumPy: embeddings, positional
  encoding, self-attention, multi-head attention, feed-forward, blocks.
- **Stage 4** — dataset, batching, loss, manual backprop, Adam, checkpoints.
- **Stage 5** — EthanGPT text generation: temperature, top-k, top-p, history.
- **Stage 6** — save/load weights and tokenizer; continue training.
- **Stage 7** — command-line chat, later a simple Android-friendly interface.
- **Stage 8** — scaling, better data, fine-tuning, limits, evaluation.
- **Stage 9** — complete, documented project.

Next step: your confirmation to start **Stage 2**.
