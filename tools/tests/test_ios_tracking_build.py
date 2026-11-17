import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location(
    'ios_tracking_build', Path(__file__).resolve().parents[1] / 'build-ios-tracking.py')
tracking = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tracking)


class CmakeDiscoveryTest(unittest.TestCase):
    def test_explicit_override_precedes_path(self):
        with patch.dict(os.environ, {'FUTACHA_CMAKE': '/custom/cmake'}, clear=True), \
                patch.object(tracking.shutil, 'which', return_value='/path/cmake'):
            self.assertEqual(tracking.find_cmake(), '/custom/cmake')

    def test_path_precedes_fallbacks(self):
        with patch.dict(os.environ, {}, clear=True), \
                patch.object(tracking.shutil, 'which', return_value='/path/cmake'):
            self.assertEqual(tracking.find_cmake(), '/path/cmake')

    def test_xcode_path_finds_newest_supported_sdk_cmake(self):
        for sdk_variable in [None, 'ANDROID_HOME', 'ANDROID_SDK_ROOT']:
            with self.subTest(sdk_variable=sdk_variable), tempfile.TemporaryDirectory() as directory:
                home = Path(directory)
                sdk = home / ('custom-sdk' if sdk_variable else 'Library/Android/sdk')
                executables = []
                for version in ['3.9.0', '3.22.1', '3.31.6', '4.0.0']:
                    executable = sdk / 'cmake' / version / 'bin/cmake'
                    executable.parent.mkdir(parents=True)
                    executable.touch()
                    executable.chmod(0o644 if version == '4.0.0' else 0o755)
                    executables.append(executable)
                environment = {sdk_variable: str(sdk)} if sdk_variable else {}
                actual_is_file = Path.is_file
                # Exclude machine-wide installations so this exercises the SDK fallback.
                with patch.dict(os.environ, environment, clear=True), \
                        patch.object(tracking.shutil, 'which', return_value=None), \
                        patch.object(tracking, 'ROOT', home), \
                        patch.object(Path, 'home', return_value=home), \
                        patch.object(Path, 'is_file', lambda path: path in executables and actual_is_file(path)):
                    self.assertEqual(tracking.find_cmake(), str(sdk / 'cmake/3.31.6/bin/cmake'))

    def test_missing_cmake_explains_required_tool(self):
        with tempfile.TemporaryDirectory() as directory, \
                patch.dict(os.environ, {}, clear=True), \
                patch.object(tracking.shutil, 'which', return_value=None), \
                patch.object(tracking, 'ROOT', Path(directory)), \
                patch.object(Path, 'home', return_value=Path(directory)), \
                patch.object(Path, 'is_file', return_value=False):
            with self.assertRaisesRegex(RuntimeError, 'CMake >= 3.21'):
                tracking.find_cmake()


if __name__ == '__main__':
    unittest.main()
