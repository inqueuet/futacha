#!/usr/bin/env python3
"""Keep useful native symbols in the AAB and archive the complete originals."""
import argparse
import json
from pathlib import Path
import shutil
import struct
import zipfile


def has_debug_symbols(data):
    # Unknown formats are retained conservatively.
    if data[:4] != b'\x7fELF':
        return True
    endian = '<' if data[5] == 1 else '>'
    if data[4] == 2:
        offset = struct.unpack_from(endian + 'Q', data, 40)[0]
        size, count, strings = struct.unpack_from(endian + 'HHH', data, 58)
        fmt = endian + 'IIQQQQIIQQ'
    elif data[4] == 1:
        offset = struct.unpack_from(endian + 'I', data, 32)[0]
        size, count, strings = struct.unpack_from(endian + 'HHH', data, 46)
        fmt = endian + 'IIIIIIIIII'
    else:
        return True
    sections = [struct.unpack_from(fmt, data, offset + i * size) for i in range(count)]
    if not sections or strings >= len(sections):
        return True
    table = sections[strings]
    names = data[table[4]:table[4] + table[5]]
    for section in sections:
        name = names[section[0]:].split(b'\0', 1)[0]
        if section[5] and (section[1] == 2 or name.startswith((b'.debug_', b'.zdebug_', b'.gnu_debug'))):
            return True
    # An already-stripped ELF with only dynamic exports contributes no new
    # debug information. Its executable is already present under base/lib.
    return False


def optimize(inputs, output, archive):
    files = {}
    for directory in inputs:
        for source in sorted(directory.rglob('*')):
            if not source.is_file():
                continue
            relative = source.relative_to(directory).as_posix()
            data = source.read_bytes()
            if relative in files and files[relative] != data:
                raise ValueError('Conflicting native symbols: ' + relative)
            files[relative] = data
    if output.exists():
        shutil.rmtree(output)
    output.mkdir(parents=True)
    archive.parent.mkdir(parents=True, exist_ok=True)
    removed = []
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as complete:
        for relative, data in files.items():
            complete.writestr(relative, data)
            if relative.endswith('.sym') and not has_debug_symbols(data):
                removed.append(relative)
                continue
            target = output / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
    return removed


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inputs', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--archive', type=Path, required=True)
    args = parser.parse_args()
    removed = optimize([Path(p) for p in json.loads(args.inputs.read_text(encoding='utf-8'))],
                       args.output, args.archive)
    print('Excluded %d symbol copies with no debug information; originals archived at %s' % (len(removed), args.archive))
