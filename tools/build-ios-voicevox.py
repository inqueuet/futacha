#!/usr/bin/env python3
"""Build only the C loader shim into the K/N interop. Native frameworks are delayed."""
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
target = sys.argv[1]
subprocess.run([sys.executable, str(ROOT / "tools/prepare-voicevox.py"), "--ios", target], check=True)
sdk = {"iosArm64": "iphoneos", "iosSimulatorArm64": "iphonesimulator"}[target]
triple = "arm64-apple-ios15.1" + ("-simulator" if sdk == "iphonesimulator" else "")
out = ROOT / "shared/build/nativeVoicevox" / target
out.mkdir(parents=True, exist_ok=True)
subprocess.run(["xcrun", "--sdk", sdk, "clang", "-target", triple, "-O2", "-fvisibility=hidden",
                "-I" + str(ROOT / "shared/src/nativeInterop/voicevox"), "-I" + str(ROOT / "build/voicevox/include"),
                "-c", str(ROOT / "shared/src/nativeInterop/voicevox/FutachaVoicevox.c"), "-o", str(out / "voicevox.o")], check=True)
subprocess.run(["xcrun", "libtool", "-static", "-o", str(out / "libfutacha_voicevox.a"), str(out / "voicevox.o")], check=True)
