#!/usr/bin/env python3
"""Prepare distribution JARs without changing dependency caches or Java APIs."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import zipfile

TARGETS = {
    'macos': ('osx-aarch64', 'Mac/aarch64', 'macos-arm64'),
    'windows': ('win-x64', 'Windows/x86_64', 'windows-x64'),
}


def io_path(path):
    """Return a Windows extended-length path when the normal path is too long."""
    if os.name != 'nt':
        return path
    value = str(path.absolute())
    if value.startswith('\\\\?\\') or len(value) < 248:
        return path
    if value.startswith('\\\\'):
        value = '\\\\?\\UNC\\' + value[2:]
    else:
        value = '\\\\?\\' + value
    return Path(value)


def keep_entry(name, platform):
    onnx, sqlite, skiko = TARGETS[platform]
    if any(part.endswith('.dSYM') for part in name.split('/')):
        return False
    for prefix, target in [('ai/onnxruntime/native/', onnx), ('org/sqlite/native/', sqlite)]:
        if name.startswith(prefix) and not name.endswith('/'):
            return name.startswith(prefix + target + '/')
    # Compose extracts the selected Skiko binary at packaging time. Keep that
    # original entry, but do not leave another architecture inside its JAR.
    if '/' not in name and (name.startswith('libskiko-') or name.startswith('skiko-')):
        return skiko in name
    return True


def signature_entry(name):
    upper = name.upper()
    return upper.startswith('META-INF/') and '/' not in name[len('META-INF/'):] and (
        upper.endswith(('.SF', '.RSA', '.DSA', '.EC')) or upper.startswith('META-INF/SIG-'))


def optimize_jar(source, destination, platform, mac_native_output=None):
    with zipfile.ZipFile(source) as archive:
        entries = archive.infolist()
        removed = [entry.filename for entry in entries if not keep_entry(entry.filename, platform)]
        if mac_native_output is not None:
            # Store builds load native code directly from the signed app bundle.
            # Skiko is extracted by the Compose packaging plugin itself.
            for entry in entries:
                name = entry.filename
                if not keep_entry(name, platform):
                    continue
                if not name.endswith(('.dylib', '.jnilib')) or name.startswith('libskiko-'):
                    continue
                removed.append(name)
                if not any(part in name for part in (
                    '/osx-aarch64/', '/Mac/aarch64/', '/darwin-aarch64/', '/macosx-arm64/',
                )):
                    continue
                target = mac_native_output / Path(name).name
                content = archive.read(entry)
                if target.exists() and target.read_bytes() != content:
                    raise ValueError('Conflicting native libraries: ' + target.name)
                target.write_bytes(content)
                target.chmod(0o755)
        if not removed:
            shutil.copy2(source, destination)
            return []
        # A modified JAR cannot retain its upstream signature. License notices,
        # class files, service descriptors and multi-release entries are retained.
        with zipfile.ZipFile(destination, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as result:
            for entry in entries:
                if entry.filename in removed or signature_entry(entry.filename):
                    continue
                result.writestr(entry, archive.read(entry), compress_type=zipfile.ZIP_DEFLATED,
                                compresslevel=9)
    return removed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--platform', choices=TARGETS, required=True)
    parser.add_argument('--inputs', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--mac-native-output', type=Path)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    if args.mac_native_output is not None:
        if args.platform != 'macos':
            parser.error('--mac-native-output requires --platform macos')
        args.mac_native_output.mkdir(parents=True, exist_ok=True)
        for stale in args.mac_native_output.iterdir():
            if stale.is_file():
                stale.unlink()
    expected = set()
    report = []
    for value in json.loads(args.inputs.read_text(encoding='utf-8')):
        source = Path(value)
        if source.suffix != '.jar':
            raise ValueError('Only JAR inputs are supported: ' + str(source))
        source_io = io_path(source)
        digest = hashlib.sha256(source_io.read_bytes()).hexdigest()[:16]
        name = source.stem + '-' + digest + '.jar'
        expected.add(name)
        destination = args.output / name
        destination_io = io_path(destination)
        removed = optimize_jar(source_io, destination_io, args.platform, args.mac_native_output)
        report.append({'input': str(source), 'output': name, 'before': source_io.stat().st_size,
                       'after': destination_io.stat().st_size, 'removed': removed})
    # Remove stale generated JARs when dependency versions or platforms change.
    for stale in args.output.glob('*.jar'):
        if stale.name not in expected:
            stale.unlink()
    (args.output / 'manifest.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print('Desktop JARs: %d -> %d bytes' % (sum(r['before'] for r in report), sum(r['after'] for r in report)))


if __name__ == '__main__':
    main()
