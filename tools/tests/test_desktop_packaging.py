import importlib.util
from pathlib import Path
import plistlib
import platform
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile
import xml.etree.ElementTree as ET

TOOLS = Path(__file__).resolve().parents[1]
ROOT = TOOLS.parent
sys.path.insert(0, str(TOOLS))
from desktop_vlc import prune_vlc

spec = importlib.util.spec_from_file_location('desktop_runtime', TOOLS / 'optimize-desktop-runtime.py')
runtime = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runtime)
spec = importlib.util.spec_from_file_location('desktop_shrink', TOOLS / 'finalize-desktop-shrink.py')
shrink = importlib.util.module_from_spec(spec)
spec.loader.exec_module(shrink)
spec = importlib.util.spec_from_file_location('macos_store', TOOLS / 'finalize-macos-store.py')
macos_store = importlib.util.module_from_spec(spec)
spec.loader.exec_module(macos_store)


class DesktopPackagingTest(unittest.TestCase):
    @unittest.skipUnless(sys.platform == 'win32', 'requires Windows paths')
    def test_runtime_optimizer_uses_extended_windows_paths_for_long_dependency_cache_entries(self):
        source = Path('C:/') / ('dependency-' + 'a' * 230 + '.jar')
        self.assertGreaterEqual(len(str(source)), 248)
        self.assertTrue(str(runtime.io_path(source)).startswith('\\\\?\\'))

    def test_windows_store_manifest_uses_reserved_identity_and_full_trust_desktop_target(self):
        template = ROOT / 'app-desktop/src/msix/AppxManifest.xml.template'
        source = template.read_text(encoding='utf-8')
        self.assertEqual(source.count('@MSIX_VERSION@'), 1)
        root = ET.fromstring(source.replace('@MSIX_VERSION@', '11.4.0.0'))
        ns = {
            'f': 'http://schemas.microsoft.com/appx/manifest/foundation/windows10',
            'uap': 'http://schemas.microsoft.com/appx/manifest/uap/windows10',
            'rescap': 'http://schemas.microsoft.com/appx/manifest/foundation/windows10/restrictedcapabilities',
        }
        identity = root.find('f:Identity', ns)
        self.assertEqual(identity.attrib, {
            'Name': 'valoser.Futacha',
            'Publisher': 'CN=0CA8C6F9-F8C3-490D-975D-62A8593B097A',
            'Version': '11.4.0.0',
            'ProcessorArchitecture': 'x64',
        })
        self.assertEqual(root.findtext('f:Properties/f:PublisherDisplayName', namespaces=ns), 'valoser')
        target = root.find('f:Dependencies/f:TargetDeviceFamily', ns)
        self.assertEqual(target.attrib['Name'], 'Windows.Desktop')
        application = root.find('f:Applications/f:Application', ns)
        self.assertEqual(application.attrib['Executable'], 'Futacha.exe')
        self.assertEqual(application.attrib['EntryPoint'], 'Windows.FullTrustApplication')
        capabilities = root.find('f:Capabilities', ns)
        self.assertIsNotNone(capabilities.find("rescap:Capability[@Name='runFullTrust']", ns))

    def test_rejected_api_is_detected_in_native_files_and_compressed_jar_entries(self):
        with tempfile.TemporaryDirectory() as directory:
            app = Path(directory)
            native = app / 'libscreen_plugin.dylib'
            native.write_bytes(b'\x00_CGSCreateRegisteredCursorImage\x00')
            with self.assertRaisesRegex(ValueError, 'libscreen_plugin.dylib.*CGSCreateRegisteredCursorImage'):
                macos_store.verify_no_rejected_api_references(app)
            native.unlink()
            archive_path = app / 'dependency.jar'
            with zipfile.ZipFile(archive_path, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
                archive.writestr('native/screen.dylib', b'\x00_CGSMainConnectionID\x00')
            with self.assertRaisesRegex(ValueError, 'dependency.jar!/native/screen.dylib.*CGSMainConnectionID'):
                macos_store.verify_no_rejected_api_references(app)
            with zipfile.ZipFile(archive_path, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
                archive.writestr('native/video.dylib', b'public APIs only')
            macos_store.verify_no_rejected_api_references(app)

    @unittest.skipUnless(sys.platform == 'darwin', 'requires macOS codesign')
    def test_runtime_library_cannot_have_entitlements_even_as_bundle_executable(self):
        with tempfile.TemporaryDirectory() as directory:
            app = Path(directory) / 'Futacha.app'
            library = app / 'Contents/runtime/Contents/MacOS/libjli.dylib'
            library.parent.mkdir(parents=True)
            (library.parent.parent / 'Info.plist').write_bytes(plistlib.dumps({
                'CFBundleExecutable': 'libjli.dylib', 'CFBundlePackageType': 'BNDL',
                'CFBundleIdentifier': 'test.runtime',
            }))
            subprocess.run(['xcrun', 'clang', '-dynamiclib', '-x', 'c', '-', '-o', str(library)],
                           input=b'int example(void) { return 1; }', check=True, capture_output=True)
            entitlements = Path(directory) / 'bad.entitlements'
            app_id = 'TESTTEAM.test.runtime'
            entitlements.write_bytes(plistlib.dumps({'com.apple.application-identifier': app_id}))
            command = ['codesign', '--force', '--sign', '-', '--entitlements', str(entitlements)]
            # Reproduce build 2's invalid signature even on hosts that omit library entitlements.
            if int(platform.mac_ver()[0].split('.')[0]) >= 15:
                command.append('--force-library-entitlements')
            subprocess.run(command + [str(library)], check=True, capture_output=True)
            with self.assertRaisesRegex(ValueError, 'libjli.dylib.*91166'):
                macos_store.verify_nested_identifiers(app, {library: app_id})
            subprocess.run(['codesign', '--force', '--sign', '-', str(library)],
                           check=True, capture_output=True)
            self.assertEqual(macos_store.signed_entitlements(library), {})
            self.assertEqual(macos_store.verify_nested_identifiers(app, {}), 1)

    @unittest.skipUnless(sys.platform == 'darwin', 'requires macOS xattr')
    def test_quarantine_is_rejected_and_removed_only_within_generated_app(self):
        with tempfile.TemporaryDirectory(prefix='日本語 space ') as directory:
            root = Path(directory)
            app = root / 'Futacha.app'
            profile = app / 'Contents/embedded.provisionprofile'
            profile.parent.mkdir(parents=True)
            profile.write_bytes(b'profile')
            downloaded = root / 'original.provisionprofile'
            downloaded.write_bytes(b'original profile')
            (app / 'external-link').symlink_to(downloaded)
            for path in (app, profile, downloaded):
                subprocess.run(['xattr', '-w', 'com.apple.quarantine', '0081;test;Test;', str(path)], check=True)
            subprocess.run(['xattr', '-w', 'com.futacha.keep', 'retain', str(profile)], check=True)
            with self.assertRaisesRegex(ValueError, '91109'):
                macos_store.verify_no_quarantine(app)
            self.assertEqual(macos_store.remove_quarantine(app), 2)
            macos_store.verify_no_quarantine(app)
            self.assertEqual(macos_store.remove_quarantine(app), 0)
            self.assertEqual(subprocess.check_output(['xattr', '-p', 'com.futacha.keep', str(profile)]).strip(), b'retain')
            self.assertIn(b'com.apple.quarantine', subprocess.check_output(['xattr', str(downloaded)]))

    @unittest.skipUnless(sys.platform == 'darwin', 'requires macOS codesign')
    def test_nested_helper_cannot_borrow_the_enclosing_runtime_profile(self):
        # Exercise actual signed Mach-O code. A matching identifier elsewhere in
        # the runtime is not sufficient to provision a standalone executable.
        with tempfile.TemporaryDirectory() as directory:
            app = Path(directory) / 'Futacha.app'
            helper = app / 'Contents/runtime/Contents/Home/lib/jspawnhelper'
            helper.parent.mkdir(parents=True)
            shutil.copyfile('/usr/bin/true', helper)
            entitlements_path = Path(directory) / 'helper.entitlements'
            app_id = 'TESTTEAM.com.oracle.java.com.valoser.futacha'
            entitlements = {'com.apple.security.app-sandbox': True,
                            'com.apple.application-identifier': app_id}
            def sign():
                entitlements_path.write_bytes(plistlib.dumps(entitlements))
                subprocess.run(['codesign', '--force', '--sign', '-', '--entitlements',
                                str(entitlements_path), str(helper)], check=True, capture_output=True)
            sign()
            provisioned = {app / 'Contents/runtime/Contents/MacOS/libjli.dylib': app_id}
            with self.assertRaisesRegex(ValueError, 'jspawnhelper.*90885'):
                macos_store.verify_nested_identifiers(app, provisioned)
            del entitlements['com.apple.application-identifier']
            entitlements['com.apple.security.inherit'] = True
            sign()
            self.assertEqual(macos_store.verify_nested_identifiers(app, provisioned), 1)
            self.assertEqual(macos_store.signed_entitlements(helper), entitlements)

    def test_store_native_code_is_externalized_without_intel_libraries_or_stale_jar_signatures(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, target, native = root / 'input.jar', root / 'output.jar', root / 'native'
            native.mkdir()
            with zipfile.ZipFile(source, 'w') as archive:
                archive.writestr('com/sun/jna/darwin-aarch64/libjnidispatch.jnilib', b'arm64')
                archive.writestr('com/sun/jna/darwin-x86-64/libjnidispatch.jnilib', b'intel')
                archive.writestr('ai/onnxruntime/native/osx-aarch64/libonnxruntime.dylib', b'onnx')
                archive.writestr('ai/onnxruntime/native/osx-aarch64/libonnxruntime.dylib.dSYM/Contents/Resources/DWARF/libonnxruntime.dylib', b'symbols')
                archive.writestr('libskiko-macos-arm64.dylib', b'compose extracts this')
                archive.writestr('Api.class', b'original API')
                archive.writestr('META-INF/LICENSE', b'license')
                archive.writestr('META-INF/upstream.SF', b'jar signature')
            original = source.read_bytes()
            runtime.optimize_jar(source, target, 'macos', native)
            self.assertEqual((native / 'libjnidispatch.jnilib').read_bytes(), b'arm64')
            self.assertEqual((native / 'libonnxruntime.dylib').read_bytes(), b'onnx')
            with zipfile.ZipFile(target) as archive:
                self.assertEqual(set(archive.namelist()), {'Api.class', 'META-INF/LICENSE', 'libskiko-macos-arm64.dylib'})
                self.assertEqual(archive.read('Api.class'), b'original API')
            self.assertEqual(source.read_bytes(), original)

    def test_store_externalization_rejects_conflicting_native_library_names(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            native = root / 'native'
            native.mkdir()
            source = root / 'source.jar'
            with zipfile.ZipFile(source, 'w') as archive:
                archive.writestr('first/macosx-arm64/libsame.dylib', b'one')
                archive.writestr('second/macosx-arm64/libsame.dylib', b'two')
            with self.assertRaisesRegex(ValueError, 'Conflicting native libraries'):
                runtime.optimize_jar(source, root / 'out.jar', 'macos', native)

    def test_release_uses_original_compose_bytecode_and_only_shrunk_icons(self):
        with tempfile.TemporaryDirectory(prefix='日本語 space ') as directory:
            root = Path(directory)
            output = root / 'out'
            output.mkdir()
            lines = []
            for name in ['shared-jvm.jar', 'material-icons-extended-desktop-1.7.3.jar']:
                source, target = root / name, output / name
                if name == 'shared-jvm.jar':
                    source.write_bytes(b'original stack maps and multi-release entries')
                    target.write_bytes(b'proguard result')
                else:
                    with zipfile.ZipFile(source, 'w') as archive:
                        archive.writestr('androidx/compose/material/icons/Used.class', b'original icon stack maps')
                        archive.writestr('androidx/compose/material/icons/Unused.class', b'unused icon')
                        archive.writestr('META-INF/LICENSE.txt', b'original license')
                    with zipfile.ZipFile(target, 'w') as archive:
                        archive.writestr('androidx/compose/material/icons/Used.class', b'no stack maps')
                lines += ["-injars '%s'" % source.as_posix(), "-outjars '%s'" % target.as_posix()]
            config = root / 'jars-config.pro'
            config.write_text('\n'.join(lines), encoding='utf-8')
            self.assertEqual(shrink.finalize(config, output), 1)
            self.assertEqual((output / 'shared-jvm.jar').read_bytes(), (root / 'shared-jvm.jar').read_bytes())
            with zipfile.ZipFile(output / 'material-icons-extended-desktop-1.7.3.jar') as archive:
                self.assertEqual(set(archive.namelist()), {
                    'androidx/compose/material/icons/Used.class', 'META-INF/LICENSE.txt'})
                self.assertEqual(archive.read('androidx/compose/material/icons/Used.class'), b'original icon stack maps')
                self.assertEqual(archive.read('META-INF/LICENSE.txt'), b'original license')

    def test_each_distribution_keeps_its_native_runtime_and_all_java_services_and_notices(self):
        entries = {
            'ai/onnxruntime/OrtEnvironment.class': b'java API',
            'ai/onnxruntime/native/osx-aarch64/libonnxruntime.dylib': b'mac ONNX',
            'ai/onnxruntime/native/win-x64/onnxruntime.dll': b'windows ONNX',
            'ai/onnxruntime/native/linux-x64/libonnxruntime.so': b'linux ONNX',
            'ai/onnxruntime/native/osx-aarch64/libonnxruntime.dylib.dSYM/Contents/Resources/DWARF/lib': b'symbols',
            'org/sqlite/native/Mac/aarch64/libsqlitejdbc.dylib': b'mac database',
            'org/sqlite/native/Windows/x86_64/sqlitejdbc.dll': b'windows database',
            'org/sqlite/native/Linux/x86_64/libsqlitejdbc.so': b'linux database',
            'libskiko-macos-arm64.dylib': b'mac drawing',
            'libskiko-macos-x64.dylib': b'intel drawing',
            'skiko-windows-x64.dll': b'windows drawing',
            'skiko-windows-x64.dll.sha256': b'windows checksum',
            'META-INF/services/java.sql.Driver': b'org.sqlite.JDBC',
            'META-INF/versions/9/module-info.class': b'module',
            'ThirdPartyNotices.txt': b'license notices',
            'META-INF/upstream.RSA': b'old signature',
            'META-INF/upstream.SF': b'old digests',
        }
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / 'source.jar'
            with zipfile.ZipFile(source, 'w') as archive:
                for name, content in entries.items():
                    archive.writestr(name, content)
            original = source.read_bytes()
            for platform, native in [('macos', ['ai/onnxruntime/native/osx-aarch64/libonnxruntime.dylib',
                                                 'org/sqlite/native/Mac/aarch64/libsqlitejdbc.dylib',
                                                 'libskiko-macos-arm64.dylib']),
                                     ('windows', ['ai/onnxruntime/native/win-x64/onnxruntime.dll',
                                                  'org/sqlite/native/Windows/x86_64/sqlitejdbc.dll',
                                                  'skiko-windows-x64.dll', 'skiko-windows-x64.dll.sha256'])]:
                with self.subTest(platform=platform):
                    target = Path(directory) / (platform + '.jar')
                    runtime.optimize_jar(source, target, platform)
                    with zipfile.ZipFile(target) as archive:
                        keep = native + ['ai/onnxruntime/OrtEnvironment.class', 'META-INF/services/java.sql.Driver',
                                         'META-INF/versions/9/module-info.class', 'ThirdPartyNotices.txt']
                        self.assertEqual(set(archive.namelist()), set(keep))
                        for name in keep:
                            self.assertEqual(archive.read(name), entries[name])
            self.assertEqual(source.read_bytes(), original)

    def test_untouched_signed_dependency_is_copied_byte_for_byte(self):
        with tempfile.TemporaryDirectory() as directory:
            source, target = Path(directory) / 'source.jar', Path(directory) / 'target.jar'
            with zipfile.ZipFile(source, 'w') as archive:
                archive.writestr('example.class', b'class bytes')
                archive.writestr('META-INF/upstream.RSA', b'signature')
            self.assertEqual(runtime.optimize_jar(source, target, 'macos'), [])
            self.assertEqual(source.read_bytes(), target.read_bytes())

    def test_vlc_retains_codecs_output_network_localized_errors_and_licenses(self):
        for platform in ['macos', 'windows']:
            with self.subTest(platform=platform), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                preserved = ['plugins/codec/avcodec', 'plugins/access/http', 'plugins/audio_output/output',
                             'plugins/demux/mkv', 'locale/ja/LC_MESSAGES/vlc.mo', 'COPYING.txt',
                             'libvlc.dll', 'lib/libvlc.dylib']
                removed = ['lua/playlist/script', 'skins/theme', 'vlc.exe', 'npvlc.dll', 'plugins/plugins.dat']
                if platform == 'windows':
                    removed += ['plugins/gui/libqt_plugin.dll']
                    preserved += ['plugins/access/libscreen_plugin.dll']
                else:
                    removed += ['plugins/libmacosx_plugin.dylib', 'plugins/libscreen_plugin.dylib']
                    preserved += ['plugins/libavcodec_plugin.dylib', 'plugins/libmp4_plugin.dylib',
                                  'plugins/libmkv_plugin.dylib', 'plugins/libvmem_plugin.dylib',
                                  'plugins/libauhal_plugin.dylib']
                for name in preserved + removed:
                    path = root / name
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(b'original')
                prune_vlc(root, platform)
                prune_vlc(root, platform)
                for name in preserved:
                    self.assertEqual((root / name).read_bytes(), b'original')
                for name in removed:
                    self.assertFalse((root / name).exists())


if __name__ == '__main__':
    unittest.main()
