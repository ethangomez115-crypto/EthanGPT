#!/data/data/com.termux/files/usr/bin/bash
# EthanGPT - Stage 1 setup for Termux on Android.
# Run this inside Termux, NOT inside Buddy's Linux.
#
#   bash ~/EthanGPT/stage1/setup_termux.sh
#
# It is safe to run more than once.

set -e

echo "=== EthanGPT Stage 1: Termux setup ==="

echo "[1/5] Updating Termux packages..."
pkg update -y
pkg upgrade -y

echo "[2/5] Installing Python and unzip..."
pkg install -y python unzip

echo "[3/5] Installing NumPy..."
# Modern Termux calls the package "numpy"; older repos call it "python-numpy".
if ! pkg install -y numpy; then
    echo "Trying the older package name python-numpy..."
    pkg install -y python-numpy
fi

echo "[4/5] Verifying NumPy actually works..."
python -c "import numpy as np; print('NumPy version:', np.__version__); print(np.array([1,2,3]) * 2)"

echo "[5/5] Unpacking the project and running the check..."
if [ -f "/sdcard/Download/Buddy/EthanGPT.zip" ]; then
    mkdir -p "$HOME/EthanGPT"
    unzip -o "/sdcard/Download/Buddy/EthanGPT.zip" -d "$HOME/EthanGPT"
    cd "$HOME/EthanGPT"
else
    echo "EthanGPT.zip not found in shared storage; looking for an existing project..."
    cd "$HOME/EthanGPT" || { echo "Project folder missing. Copy EthanGPT.zip first."; exit 1; }
fi

python config/environment_check.py || true

echo "=== Stage 1 setup finished ==="
