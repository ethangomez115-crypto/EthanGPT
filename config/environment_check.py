#!/usr/bin/env python3
"""
EthanGPT - Stage 1 verification script.

WHAT THIS DOES
--------------
Checks that an Android phone running Termux has everything EthanGPT needs:
Python, a working NumPy, free RAM, free storage, and the project folders.

WHY THIS IS NECESSARY
---------------------
A tiny model that does not fit in RAM will be killed by Android before it
finishes training. This script tells you BEFORE you write the model whether
your current setup is safe, and saves a report you can compare later.

HOW TO RUN (in Termux)
----------------------
    cd ~/EthanGPT
    python config/environment_check.py

It only uses the Python standard library, so it works even before NumPy is
installed. That is intentional: it tells you exactly what is missing.
"""

import json
import os
import platform
import shutil
import subprocess
import sys
from datetime import datetime

# The project root is the parent of the "config" folder containing this file.
PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def section(title):
    print("\n" + title)
    print("-" * len(title))


def getprop(key):
    """Ask Android for a system property. Works in Termux, fails elsewhere."""
    try:
        out = subprocess.check_output(
            ["getprop", key], stderr=subprocess.DEVNULL, timeout=3
        )
        return out.decode("utf-8", "replace").strip() or "unknown"
    except Exception:
        return "unknown"


def read_memory():
    """Read /proc/meminfo, which Android exposes to Termux."""
    info = {}
    try:
        with open("/proc/meminfo", "r", encoding="utf-8") as handle:
            for line in handle:
                parts = line.split()
                if len(parts) >= 2 and parts[0].endswith(":"):
                    info[parts[0][:-1]] = int(parts[1])  # value is in kB
    except OSError:
        return None

    def gb(key):
        return round(info[key] / 1024 / 1024, 2) if key in info else None

    return {
        "total_gb": gb("MemTotal"),
        "available_gb": gb("MemAvailable"),
        "free_gb": gb("MemFree"),
    }


def check_numpy():
    """Import NumPy and do one real calculation, not just an import."""
    try:
        import numpy as np
    except ImportError:
        return {"ok": False, "version": None, "error": "not installed"}

    try:
        a = np.array([[1, 2], [3, 4]], dtype=np.float32)
        b = np.array([[5, 6], [7, 8]], dtype=np.float32)
        expected = np.array([[19.0, 22.0], [43.0, 50.0]], dtype=np.float32)
        ok = bool(np.allclose(a @ b, expected))
        return {"ok": ok, "version": np.__version__, "error": None}
    except Exception as exc:  # a broken install should be reported clearly
        return {"ok": False, "version": np.__version__, "error": str(exc)}


def check_torch():
    """PyTorch is optional. We expect it to be absent on Android."""
    try:
        import torch
        return {"ok": True, "version": torch.__version__}
    except Exception:
        return {"ok": False, "version": None}


def folder_check():
    wanted = [
        "tokenizer", "dataset", "model", "training",
        "checkpoints", "chatbot", "interface", "config",
    ]
    return {name: os.path.isdir(os.path.join(PROJECT_ROOT, name)) for name in wanted}


def main():
    report = {"checked_at": datetime.now().isoformat(timespec="seconds")}

    section("1. PYTHON")
    py = {
        "version": platform.python_version(),
        "implementation": platform.python_implementation(),
        "executable": sys.executable,
    }
    report["python"] = py
    for key, value in py.items():
        print(f"  {key:16}: {value}")
    python_ok = tuple(int(x) for x in py["version"].split(".")[:2]) >= (3, 9)
    print(f"  status           : {'OK' if python_ok else 'TOO OLD (need 3.9+)'}")

    section("2. DEVICE")
    device = {
        "model": getprop("ro.product.model"),
        "android": getprop("ro.build.version.release"),
        "sdk": getprop("ro.build.version.sdk"),
        "arch": getprop("ro.product.cpu.abi") or platform.machine(),
        "platform": platform.platform(),
    }
    report["device"] = device
    for key, value in device.items():
        print(f"  {key:16}: {value}")

    section("3. MEMORY (RAM)")
    memory = read_memory()
    report["memory"] = memory
    if memory:
        for key, value in memory.items():
            print(f"  {key:16}: {value} GB")
        ram_ok = (memory["available_gb"] or 0) >= 0.8
        print(f"  status           : {'OK for a tiny model' if ram_ok else 'LOW - close other apps'}")
    else:
        ram_ok = False
        print("  Could not read /proc/meminfo (are you outside Termux?)")

    section("4. STORAGE")
    usage = shutil.disk_usage(PROJECT_ROOT)
    storage = {
        "free_gb": round(usage.free / 1024 ** 3, 2),
        "total_gb": round(usage.total / 1024 ** 3, 2),
    }
    report["storage"] = storage
    for key, value in storage.items():
        print(f"  {key:16}: {value} GB")
    storage_ok = storage["free_gb"] >= 1.0
    print(f"  status           : {'OK' if storage_ok else 'LOW - free some space'}")

    section("5. LIBRARIES")
    numpy = check_numpy()
    torch = check_torch()
    report["numpy"] = numpy
    report["torch_optional"] = torch
    print(f"  numpy            : {numpy['version'] or numpy['error']} "
          f"({'verified OK' if numpy['ok'] else 'NOT READY'})")
    print(f"  torch (optional) : {torch['version'] or 'not installed - expected on Android'}")

    section("6. PROJECT FOLDERS")
    folders = folder_check()
    report["folders"] = folders
    for name, exists in folders.items():
        print(f"  {name:16}: {'OK' if exists else 'MISSING'}")
    folders_ok = all(folders.values())

    ready = python_ok and ram_ok and storage_ok and numpy["ok"] and folders_ok
    report["ready_for_stage_2"] = ready
    section("RESULT")
    print("  READY FOR STAGE 2" if ready else "  NOT READY YET")
    if not numpy["ok"]:
        print("  -> In Termux run: pkg install -y numpy")
    if not storage_ok:
        print("  -> Free at least 1 GB of storage.")
    if not ram_ok:
        print("  -> Close background apps before training.")

    out_path = os.path.join(PROJECT_ROOT, "config", "device_report.json")
    with open(out_path, "w", encoding="utf-8") as handle:
        json.dump(report, handle, indent=2)
    print(f"\n  Report saved to: {out_path}")
    return 0 if ready else 1


if __name__ == "__main__":
    raise SystemExit(main())
