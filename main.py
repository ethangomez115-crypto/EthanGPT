#!/usr/bin/env python3
"""
EthanGPT entry point.

Only Stage 1 is implemented right now, so this script runs the Stage 1
environment check. Later stages will add subcommands such as `train`,
`generate` and `chat` here.
"""

import argparse
import os
import runpy
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))


def main():
    parser = argparse.ArgumentParser(description="EthanGPT (Stage 1)")
    parser.add_argument(
        "stage",
        nargs="?",
        default="check",
        choices=["check"],
        help="stage to run (only 'check' exists in Stage 1)",
    )
    args = parser.parse_args()

    print("EthanGPT - Stage 1 (environment check only)")
    print("The model, training and chat stages are not built yet.\n")
    script = os.path.join(ROOT, "config", "environment_check.py")
    result = runpy.run_path(script, run_name="__main__")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
