#!/usr/bin/env python3
"""Prepare pinned native speech libraries. Never downloads the user's voice pack.

Android: --android --ndk <path>. iOS: --ios iosArm64|iosSimulatorArm64.
Rust/Cargo may be provided through PATH or the project-local P0 toolchain.
"""
import argparse
from contextlib import contextmanager

@contextmanager
def preparation_lock(path):
    with path.open("a+b") as stream:
        if os.name == "nt":
            import msvcrt
            stream.write(b"0"); stream.flush(); stream.seek(0)
            msvcrt.locking(stream.fileno(), msvcrt.LK_LOCK, 1)
            try: yield
            finally:
                stream.seek(0); msvcrt.locking(stream.fileno(), msvcrt.LK_UNLCK, 1)
        else:
            import fcntl
            fcntl.flock(stream, fcntl.LOCK_EX)
            try: yield
            finally: fcntl.flock(stream, fcntl.LOCK_UN)

import platform
import hashlib
import json
import os
from pathlib import Path
import plistlib
import shutil
import subprocess
import tarfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "build/voicevox"
CORE_COMMIT = "68ae4b2cf3d134bd14a6e466f753f9cfaf7f441a"
ASSETS = {
    "voicevox_core-android-arm64-0.17.1.zip": ("core", "069f29018ea27d939e3198ef34592a489181ba3d0287819f80c518963c639d37"),
    "voicevox_core-android-x86_64-0.17.1.zip": ("core", "43e8ef75b199491b6e3126be5f4328542b6a8d3a0984c5e98931b696d752cdc2"),
    "voicevox_onnxruntime-android-arm64-1.23.2.tgz": ("ort", "43e6fc2a89ea6412d4cc20215042a3b34da22fdf05c1bb76256a686f82378d46"),
    "voicevox_onnxruntime-android-x64-1.23.2.tgz": ("ort", "807e2ea50dfb2a309416adb1cee6edc53d3d8eae1d702ad4e6f199a0f91f2d24"),
    "voicevox_onnxruntime-ios-xcframework-1.23.2.zip": ("ort", "c9c33b2653125441d6a2475194ed7971a908687c0d99d8944337b0ca85ea646d"),
}


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def artifact(name):
    family, expected = ASSETS[name]
    target = OUT / "downloads" / name
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.exists():
        researched = ROOT / "build/character-voice-research-2026-10-19" / name
        if researched.exists() and digest(researched) == expected:
            shutil.copyfile(researched, target)
        else:
            repo, tag = ("voicevox_core", "0.17.1") if family == "core" else ("onnxruntime-builder", "voicevox_onnxruntime-1.23.2")
            partial = target.with_suffix(target.suffix + ".part")
            with urllib.request.urlopen(f"https://github.com/VOICEVOX/{repo}/releases/download/{tag}/{name}", timeout=60) as response, partial.open("wb") as output:
                shutil.copyfileobj(response, output, 1024 * 1024)
            partial.replace(target)
    if digest(target) != expected:
        raise RuntimeError(f"Checksum mismatch: {name}")
    return target


def write_if_changed(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists() or path.read_bytes() != data:
        temporary = path.with_name(path.name + f".{os.getpid()}.part")
        temporary.write_bytes(data)
        temporary.replace(path)


def android(ndk):
    host_name = {"Darwin": "darwin-x86_64", "Linux": "linux-x86_64", "Windows": "windows-x86_64"}[platform.system()]
    host = ndk / "toolchains/llvm/prebuilt" / host_name
    if not host.exists():
        raise RuntimeError("Android NDK host tools were not found")
    for abi, core_arch, ort_arch, triple in [
        ("arm64-v8a", "arm64", "arm64", "aarch64-linux-android"),
        ("x86_64", "x86_64", "x64", "x86_64-linux-android"),
    ]:
        dest = OUT / "android/jniLibs" / abi
        with zipfile.ZipFile(artifact(f"voicevox_core-android-{core_arch}-0.17.1.zip")) as archive:
            for member in archive.namelist():
                if member.endswith("/lib/libvoicevox_core.so"):
                    write_if_changed(dest / "libvoicevox_core.so", archive.read(member))
                elif member.endswith("/include/voicevox_core.h"):
                    write_if_changed(OUT / "include/voicevox_core.h", archive.read(member))
        with tarfile.open(artifact(f"voicevox_onnxruntime-android-{ort_arch}-1.23.2.tgz")) as archive:
            member = next(m for m in archive.getmembers() if m.isfile() and m.name.endswith("/lib/libvoicevox_onnxruntime.so"))
            write_if_changed(dest / "libvoicevox_onnxruntime.so", archive.extractfile(member).read())
        # Upstream Open JTalk needs the shared C++ runtime; existing tracking is static.
        # Strip only the generated copy, never the NDK or an upstream source file.
        original = host / "sysroot/usr/lib" / triple / "libc++_shared.so"
        stamp = dest / "libcxx-source.sha256"
        if not stamp.exists() or stamp.read_text() != digest(original):
            dest.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(original, dest / "libc++_shared.so")
            subprocess.run([str(host / ("bin/llvm-strip.exe" if platform.system() == "Windows" else "bin/llvm-strip")), "--strip-debug", str(dest / "libc++_shared.so")], check=True)
            stamp.write_text(digest(original))


def ios(target):
    rust_target = {"iosArm64": "aarch64-apple-ios", "iosSimulatorArm64": "aarch64-apple-ios-sim"}[target]
    source = ROOT / "build/voicevox-source/core-0.17.1"
    if not (source / ".git").exists():
        source.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(["git", "clone", "--depth", "1", "--filter=blob:none", "--sparse", "--branch", "0.17.1", "https://github.com/VOICEVOX/voicevox_core.git", str(source)], check=True)
        # The source archive also carries large example models. They are not
        # needed by the C API engine; user voice data remains an explicit download.
        subprocess.run(["git", "-C", str(source), "sparse-checkout", "set", ".cargo", "crates"], check=True)
    revision = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
    if revision != CORE_COMMIT or subprocess.check_output(["git", "-C", str(source), "status", "--porcelain"], text=True).strip():
        raise RuntimeError("VOICEVOX source must match the unmodified pinned revision")
    env = os.environ.copy()
    local = ROOT / "build/voicevox-toolchain"
    if (local / "cargo/bin/cargo").exists():
        env.update(CARGO_HOME=str(local / "cargo"), RUSTUP_HOME=str(local / "rustup"))
        env["PATH"] = str(local / "cargo/bin") + os.pathsep + env.get("PATH", "")
    cargo = shutil.which("cargo", path=env.get("PATH"))
    if not cargo or not subprocess.check_output([cargo, "--version"], env=env, text=True).startswith("cargo 1.96.0 "):
        raise RuntimeError("Install the pinned Rust toolchain 1.96.0 (including the requested iOS target)")
    # Use the same compiler/SDK under Xcode and a terminal. Otherwise CMake
    # resets its cached configuration when Xcode supplies a different CC path,
    # losing Cargo's local install prefix and attempting to use /usr/local.
    sdk = "iphoneos" if target == "iosArm64" else "iphonesimulator"
    for variable in ("CC", "CXX"):
        env.pop(variable, None)
    for variable, tool in (("CC", "clang"), ("CXX", "clang++")):
        env[f"{variable}_{rust_target.replace('-', '_')}"] = subprocess.check_output(
            ["xcrun", "--sdk", sdk, "--find", tool], text=True).strip()
    env["SDKROOT"] = subprocess.check_output(["xcrun", "--sdk", sdk, "--show-sdk-path"], text=True).strip()
    env.update(IPHONEOS_DEPLOYMENT_TARGET="15.1", CARGO_PROFILE_C_API_STRIP="none",
               CARGO_TARGET_DIR=str(ROOT / "build/voicevox-source/target"))
    compiler = env[f"CC_{rust_target.replace('-', '_')}"]
    native_builds = Path(env["CARGO_TARGET_DIR"]) / rust_target / "c-api/build"
    for cache in native_builds.glob("open_jtalk-sys-*/out/build/CMakeCache.txt"):
        values = dict(line.split("=", 1) for line in cache.read_text().splitlines()
                      if line and not line.startswith(("#", "//")) and "=" in line)
        cached_compiler = values.get("CMAKE_C_COMPILER:FILEPATH") or values.get("CMAKE_C_COMPILER:STRING")
        prefix = values.get("CMAKE_INSTALL_PREFIX:PATH")
        if cached_compiler != compiler or prefix != str(cache.parent.parent):
            cache.unlink()
            shutil.rmtree(cache.parent / "CMakeFiles", ignore_errors=True)
    subprocess.run([cargo, "build", "--manifest-path", str(source / "Cargo.toml"), "-p", "voicevox_core_c_api",
                    "--features", "load-onnxruntime", "--profile", "c-api", "--target", rust_target, "--locked"], env=env, check=True)
    generated = Path(env["CARGO_TARGET_DIR"]) / rust_target / "c-api/libvoicevox_core.dylib"
    framework = OUT / "ios" / target / "voicevox_core.framework"
    framework.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(generated, framework / "voicevox_core")
    subprocess.run(["xcrun", "install_name_tool", "-id", "@rpath/voicevox_core.framework/voicevox_core", str(framework / "voicevox_core")], check=True)
    # Rust's strip produced a malformed LINKEDIT with this Xcode. Use Apple's tool.
    subprocess.run(["xcrun", "strip", "-S", "-x", str(framework / "voicevox_core")], check=True)
    info = dict(CFBundleExecutable="voicevox_core", CFBundleIdentifier="com.valoser.futacha.voicevox-core",
                CFBundleName="voicevox_core", CFBundlePackageType="FMWK", CFBundleVersion="1",
                CFBundleShortVersionString="0.17.1", MinimumOSVersion="15.1",
                CFBundleSupportedPlatforms=["iPhoneOS" if target == "iosArm64" else "iPhoneSimulator"])
    write_if_changed(framework / "Info.plist", plistlib.dumps(info))
    # Preserve the official runtime code and minimum OS. It is loaded only on iOS >=16.
    prefix = "ios-arm64" if target == "iosArm64" else "ios-arm64_x86_64-simulator"
    runtime_dest = framework.parent / "voicevox_onnxruntime.framework"
    with zipfile.ZipFile(artifact("voicevox_onnxruntime-ios-xcframework-1.23.2.zip")) as archive:
        base = f"voicevox_onnxruntime.xcframework/{prefix}/voicevox_onnxruntime.framework/"
        for member in archive.namelist():
            if member.startswith(base) and not member.endswith("/"):
                relative = member[len(base):]
                if relative.startswith("Headers/") or relative.startswith("Modules/") or relative.startswith("_CodeSignature/"):
                    continue
                write_if_changed(runtime_dest / relative, archive.read(member))
    # The upstream framework identifier contains an underscore, which Xcode's
    # signed iOS application validation rejects. Repackage only this metadata;
    # keep the runtime executable and its deployment target unchanged.
    runtime_info = plistlib.loads((runtime_dest / "Info.plist").read_bytes())
    runtime_info["CFBundleIdentifier"] = "com.valoser.futacha.voicevox-onnxruntime"
    write_if_changed(runtime_dest / "Info.plist", plistlib.dumps(runtime_info))
    if target == "iosSimulatorArm64":
        runtime = runtime_dest / "voicevox_onnxruntime"
        archs = subprocess.check_output(["xcrun", "lipo", "-archs", str(runtime)], text=True).split()
        if len(archs) > 1:
            subprocess.run(["xcrun", "lipo", str(runtime), "-thin", "arm64", "-output", str(runtime) + ".thin"], check=True)
            Path(str(runtime) + ".thin").replace(runtime)
    subprocess.run(["codesign", "--force", "--sign", "-", str(framework)], check=True)
    subprocess.run(["codesign", "--force", "--sign", "-", str(runtime_dest)], check=True)
    write_if_changed(OUT / "include/voicevox_core.h", (source / "crates/voicevox_core_c_api/include/voicevox_core.h").read_bytes())
    (framework.parent / "provenance.json").write_text(json.dumps({"core_commit": revision, "target": target,
        "core_minos": "15.1", "runtime_minos": "16.0", "runtime_delayed": True,
        "runtime_bundle_identifier": runtime_info["CFBundleIdentifier"]}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--android", action="store_true")
    parser.add_argument("--ndk", type=Path)
    parser.add_argument("--ios", choices=["iosArm64", "iosSimulatorArm64"])
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    with preparation_lock(OUT / "prepare.lock"):
        if args.android:
            if args.ndk is None:
                parser.error("--android requires --ndk")
            android(args.ndk)
        if args.ios:
            ios(args.ios)
