import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('android_symbols', Path(__file__).resolve().parents[1] / 'optimize-android-symbols.py')
symbols = importlib.util.module_from_spec(spec)
spec.loader.exec_module(symbols)


def elf(bits, private):
    header_size, section_size = (64, 64) if bits == 64 else (52, 40)
    names = b'\0.shstrtab\0.text\0.dynsym\0.symtab\0'
    data = bytearray(header_size)
    data[:7] = b'\x7fELF' + bytes([2 if bits == 64 else 1, 1, 1])
    sections = [(0,) * 10]
    for name, kind, flags, payload in [('.shstrtab', 3, 0, names), ('.text', 1, 6, b'code'),
                                        ('.dynsym', 11, 2, b'exports')] + ([('.symtab', 2, 0, b'private symbols')] if private else []):
        sections.append((names.index(name.encode()), kind, flags, 0, len(data), len(payload), 0, 0, 1, 0))
        data.extend(payload)
    offset = len(data)
    for section in sections:
        data.extend(struct.pack('<IIQQQQIIQQ' if bits == 64 else '<IIIIIIIIII', *section))
    struct.pack_into('<Q' if bits == 64 else '<I', data, 40 if bits == 64 else 32, offset)
    struct.pack_into('<HHH', data, 58 if bits == 64 else 46, section_size, len(sections), 1)
    return bytes(data)


class AndroidSymbolsTest(unittest.TestCase):
    def test_only_redundant_elf_copies_leave_aab_and_all_originals_remain_archived(self):
        for bits in [32, 64]:
            with self.subTest(bits=bits), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                source = root / 'input' / 'abi'
                source.mkdir(parents=True)
                files = {'runtime.so.sym': elf(bits, False), 'tracking.so.sym': elf(bits, True), 'notice.txt': b'notice'}
                for name, data in files.items():
                    (source / name).write_bytes(data)
                removed = symbols.optimize([source.parent], root / 'out', root / 'all.zip')
                self.assertEqual(removed, ['abi/runtime.so.sym'])
                self.assertFalse((root / 'out/abi/runtime.so.sym').exists())
                self.assertEqual((root / 'out/abi/tracking.so.sym').read_bytes(), files['tracking.so.sym'])
                self.assertEqual((root / 'out/abi/notice.txt').read_bytes(), files['notice.txt'])
                with zipfile.ZipFile(root / 'all.zip') as archive:
                    for name, data in files.items():
                        self.assertEqual(archive.read('abi/' + name), data)
                        self.assertEqual((source / name).read_bytes(), data)
                # Gradle finalizes the extractor's own output directory.
                self.assertEqual(symbols.optimize([source.parent], source.parent, root / 'in-place.zip'), removed)
                self.assertFalse((source / 'runtime.so.sym').exists())
                self.assertEqual((source / 'tracking.so.sym').read_bytes(), files['tracking.so.sym'])
                with zipfile.ZipFile(root / 'in-place.zip') as archive:
                    for name, data in files.items():
                        self.assertEqual(archive.read('abi/' + name), data)


if __name__ == '__main__':
    unittest.main()
