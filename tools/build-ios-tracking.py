#!/usr/bin/env python3
"""Build the pinned OpenCV tracking subset for the two supported iOS architectures."""
import fcntl
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
SOURCE_COMMIT = "40738fb16ceddb5fb3fea747585f7ce6abb0605b"  # Official OpenCV 5.0.0 tag.


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)


def find_cmake():
    cmake = os.environ.get("FUTACHA_CMAKE") or shutil.which("cmake")
    if cmake:
        return cmake
    # Xcode does not inherit the interactive shell's PATH. Reuse installed tools
    # even after the disposable build/native-build-tools directory was cleaned.
    candidates = [ROOT / "build/native-build-tools/cmake/data/bin/cmake",
                  Path("/opt/homebrew/bin/cmake"), Path("/usr/local/bin/cmake"),
                  Path("/Applications/CMake.app/Contents/bin/cmake")]
    sdk_roots = [Path(os.environ[name]) for name in ("ANDROID_HOME", "ANDROID_SDK_ROOT")
                 if os.environ.get(name)]
    sdk_roots.append(Path.home() / "Library/Android/sdk")
    for sdk_root in sdk_roots:
        versions = [path for path in (sdk_root / "cmake").glob("*")
                    if all(part.isdigit() for part in path.name.split("."))]
        versions.sort(key=lambda path: tuple(map(int, path.name.split("."))), reverse=True)
        candidates.extend(path / "bin/cmake" for path in versions
                          if tuple(map(int, path.name.split("."))) >= (3, 21))
    for candidate in candidates:
        if candidate.is_file() and os.access(candidate, os.X_OK):
            return str(candidate)
    raise RuntimeError("Install CMake >= 3.21 or set FUTACHA_CMAKE to its executable path")


def main():
    target = sys.argv[1]
    sdk = {"iosArm64": "iphoneos", "iosSimulatorArm64": "iphonesimulator"}[target]
    cmake = find_cmake()

    cache = ROOT / "build/native-tracking"
    cache.mkdir(parents=True, exist_ok=True)
    source = cache / "opencv-5.0.0"
    with (cache / "source.lock").open("w") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        if not (source / ".git").exists():
            run("git", "-c", "http.version=HTTP/1.1", "clone", "--depth", "1", "--filter=blob:none",
                "--no-checkout", "--branch", "5.0.0", "https://github.com/opencv/opencv.git", source)
        commit = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
        if commit != SOURCE_COMMIT:
            raise RuntimeError("OpenCV source revision mismatch")
        # Exclude samples, test data, GUI, media codecs, and inference sources from the checkout.
        run("git", "-C", source, "config", "http.version", "HTTP/1.1")
        run("git", "-C", source, "sparse-checkout", "set", "--cone", "cmake", "3rdparty", "include",
            "platforms", "doc", "docs_sphinx", "modules/core", "modules/imgproc", "modules/features",
            "modules/geometry", "modules/video", "modules/flann")
        run("git", "-C", source, "checkout", "--detach", SOURCE_COMMIT)

    output = ROOT / "shared/build/nativeTracking" / target
    sdk_path = subprocess.check_output(["xcrun", "--sdk", sdk, "--show-sdk-path"], text=True).strip()
    run(cmake, "-S", ROOT / "shared/src/nativeInterop/tracking", "-B", output,
        "-DCMAKE_SYSTEM_NAME=iOS", "-DCMAKE_OSX_ARCHITECTURES=arm64",
        "-DCMAKE_OSX_SYSROOT=" + sdk_path, "-DCMAKE_OSX_DEPLOYMENT_TARGET=15.1",
        "-DCMAKE_BUILD_TYPE=Release", "-DOPENCV_SOURCE=" + str(source))
    run(cmake, "--build", output, "--target", "futacha_tracking_bridge", "--parallel", "4")
    # Bundle static objects into the cinterop klib; no extra framework is embedded in the app.
    libraries = sorted(path for path in output.rglob("*.a") if path.name != "libfutacha_tracking.a")
    run("xcrun", "libtool", "-static", "-o", output / "libfutacha_tracking.a", *libraries)


if __name__ == "__main__":
    main()
