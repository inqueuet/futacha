#!/usr/bin/env python3
"""Use ProGuard only to select icon classes; preserve original class bytes."""
import argparse
from pathlib import Path
import shlex
import shutil
import zipfile


def finalize(config: Path, output: Path):
    source = None
    count = 0
    for line in config.read_text(encoding='utf-8').splitlines():
        args = shlex.split(line)
        if not args:
            continue
        if args[0] == '-injars':
            if source is not None or len(args) != 2:
                raise ValueError('Unexpected ProGuard input configuration')
            source = Path(args[1])
        elif args[0] == '-outjars':
            if source is None or len(args) != 2:
                raise ValueError('Missing ProGuard input')
            destination = Path(args[1])
            if destination.parent.resolve() != output.resolve() or not destination.is_file():
                raise ValueError('Unexpected ProGuard output: ' + str(destination))
            if source.name.startswith(('material-icons-core-desktop-', 'material-icons-extended-desktop-')):
                with zipfile.ZipFile(destination) as shrunk:
                    retained_classes = {name for name in shrunk.namelist() if name.endswith('.class')}
                with zipfile.ZipFile(source) as original, zipfile.ZipFile(
                    destination, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9
                ) as result:
                    for entry in original.infolist():
                        if entry.filename.endswith('.class') and entry.filename not in retained_classes:
                            continue
                        result.writestr(entry, original.read(entry), compress_type=zipfile.ZIP_DEFLATED,
                                        compresslevel=9)
            else:
                shutil.copy2(source, destination)
                count += 1
            source = None
    if source is not None or count == 0:
        raise ValueError('Incomplete ProGuard JAR configuration')
    return count


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    print('Restored %d original non-icon JARs' % finalize(args.config, args.output))
