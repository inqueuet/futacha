#!/usr/bin/env python3
"""Read-only verification of an iOS .app / .xcarchive after lowering its deployment target."""

import argparse
import hashlib
import json
from pathlib import Path
import plistlib
import re
import subprocess


def version(value):
    parts = tuple(int(part) for part in value.split("."))
    return (parts + (0, 0, 0))[:3]


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.digest()


def verify_bundle_identifier(metadata, bundle):
    identifier = metadata.get("CFBundleIdentifier", "")
    if not isinstance(identifier, str) or not re.fullmatch(r"[A-Za-z0-9.-]+", identifier):
        raise ValueError(f"Invalid CFBundleIdentifier: {bundle}: {identifier}")


def inspect(binary):
    commands = subprocess.check_output(["xcrun", "otool", "-l", str(binary)], text=True)
    minimums = re.findall(r"\bminos\s+(\d+(?:\.\d+)*)", commands)
    minimums += re.findall(r"cmd LC_VERSION_MIN_IPHONEOS\s+cmdsize \d+\s+version (\S+)", commands)
    dependencies = []
    for block in re.split(r"Load command \d+", commands):
        match = re.search(r"cmd (LC_LOAD_DYLIB|LC_LOAD_WEAK_DYLIB)\b.*?\n\s*name (.*?) \(offset", block, re.S)
        if match:
            dependencies.append({"command": match[1], "path": match[2]})
    return {
        "path": str(binary),
        "architectures": subprocess.check_output(["xcrun", "lipo", "-archs", str(binary)], text=True).split(),
        "minimums": minimums,
        "platforms": re.findall(r"\bplatform\s+(\d+)", commands),
        "dependencies": dependencies,
        "text_sizes": re.findall(r"sectname __text\s+segname __TEXT\s+addr \S+\s+size (\S+)", commands),
    }


def is_resource_stub(result, metadata):
    """Xcode's static-framework copy step replaces archives with empty iOS-100 stubs.

    This is not permission to skip arbitrary newer binaries: require an empty
    text section, no symbols, only libSystem imports, and verify below that nobody loads it.
    The actual static code is linked into the host, whose minimum is checked.
    """
    if (metadata.get("MinimumOSVersion") != "100.0" or
            not result["minimums"] or any(version(v) != version("100.0") for v in result["minimums"]) or
            not result["text_sizes"] or any(int(size, 16) != 0 for size in result["text_sizes"]) or
            any(dep["path"] != "/usr/lib/libSystem.B.dylib" for dep in result["dependencies"])):
        return False
    symbols = subprocess.check_output(["xcrun", "nm", "-gU", result["path"]], text=True, stderr=subprocess.DEVNULL)
    return not symbols.strip()


def verify(artifact, minimum, expected_platform=None, shared_framework=None):
    apps = list((artifact / "Products/Applications").glob("*.app")) if artifact.suffix == ".xcarchive" else [artifact]
    if len(apps) != 1 or not apps[0].is_dir():
        raise ValueError("Pass one iOS .app or an .xcarchive containing one iOS application")
    app = apps[0]
    info = plistlib.loads((app / "Info.plist").read_bytes())
    verify_bundle_identifier(info, app)
    if version(info["MinimumOSVersion"]) != version(minimum):
        raise ValueError(f"App MinimumOSVersion is {info['MinimumOSVersion']}, expected {minimum}")
    main = app / info["CFBundleExecutable"]
    binaries = {main, *app.glob("*.dylib"), *(app / "Frameworks").rglob("*.dylib")}
    framework_metadata = {}
    # This one signed, bundled runtime is explicitly dlopened behind an iOS 16
    # availability check. It must have no load-command references from any binary.
    delayed = app / "Frameworks/voicevox_onnxruntime.framework/voicevox_onnxruntime"
    for framework in (app / "Frameworks").rglob("*.framework"):
        metadata = plistlib.loads((framework / "Info.plist").read_bytes())
        verify_bundle_identifier(metadata, framework)
        binary = framework / metadata["CFBundleExecutable"]
        framework_metadata[str(binary)] = metadata
        binaries.add(binary)
    results = [inspect(binary) for binary in sorted(binaries)]
    stubs = set()
    for result in results:
        metadata = framework_metadata.get(result["path"], {})
        result["resource_only_stub"] = is_resource_stub(result, metadata)
        if result["resource_only_stub"]:
            stubs.add(Path(result["path"]).parent.name)
            continue
        permitted = "16.0" if result["path"] == str(delayed) else minimum
        if "MinimumOSVersion" in metadata and version(metadata["MinimumOSVersion"]) > version(permitted):
            raise ValueError(f"{result['path']} declares a newer minimum OS")
    optional = {"AppIntents": False, "FoundationModels": False}
    for result in results:
        if "arm64" not in result["architectures"]:
            raise ValueError(f"Baseline arm64 is missing: {result['path']}")
        permitted = "16.0" if result["path"] == str(delayed) else minimum
        if not result["resource_only_stub"] and (not result["minimums"] or any(version(v) > version(permitted) for v in result["minimums"])):
            raise ValueError(f"Invalid Mach-O minimum OS: {result['path']}: {result['minimums']}")
        if expected_platform and any(p != expected_platform for p in result["platforms"]):
            raise ValueError(f"Wrong Mach-O platform: {result['path']}: {result['platforms']}")
        if result["path"] == str(main) and any(version(v) != version(minimum) for v in result["minimums"]):
            raise ValueError("The host executable must be rebuilt for the requested minimum OS")
        for dependency in result["dependencies"]:
            if any(f"/{stub}/" in dependency["path"] for stub in stubs):
                raise ValueError(f"A resource-only static framework is loaded: {result['path']}: {dependency['path']}")
            if "/voicevox_onnxruntime.framework/" in dependency["path"]:
                raise ValueError(f"Delayed speech runtime is referenced by a load command: {result['path']}")
            for name in optional:
                if f"/{name}.framework/" in dependency["path"]:
                    if dependency["command"] != "LC_LOAD_WEAK_DYLIB":
                        raise ValueError(f"{name} is required at launch: {result['path']}")
                    optional[name] = True
    if not all(optional.values()):
        raise ValueError(f"Expected optional-framework imports were not found: {optional}")
    if shared_framework:
        embedded = app / "Frameworks/shared.framework/shared"
        expected = shared_framework / "shared"
        if sha256(embedded) != sha256(expected):
            raise ValueError("Embedded shared.framework differs from the freshly built framework")
    return {"app": str(app), "minimum": minimum, "optional_frameworks": optional, "binaries": results}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifact", type=Path)
    parser.add_argument("--minimum", default="15.1")
    parser.add_argument("--platform", choices=["ios", "ios-simulator"])
    parser.add_argument("--shared-framework", type=Path, help="Compare an unsigned embedded framework byte-for-byte with this freshly built framework")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        result = verify(args.artifact, args.minimum, {"ios": "2", "ios-simulator": "7"}.get(args.platform), args.shared_framework)
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"iOS minimum verification failed: {error}\n")
    if args.output:
        args.output.write_text(json.dumps(result, indent=2) + "\n")
    print(f"Verified iOS {args.minimum}: {len(result['binaries'])} binaries; AppIntents / FoundationModels are optional")


if __name__ == "__main__":
    main()
