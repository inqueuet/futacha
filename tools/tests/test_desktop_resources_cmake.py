import importlib.util
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
spec = importlib.util.spec_from_file_location('desktop_resources', TOOLS / 'prepare-desktop-resources.py')
resources = importlib.util.module_from_spec(spec)
spec.loader.exec_module(resources)


def fake_cmake(path, version):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(f'#!/bin/sh\necho "cmake version {version}"\n')
    path.chmod(0o755)
    return path


class DesktopCmakeDiscoveryTest(unittest.TestCase):
    def isolated(self, home, environment=None, which=None):
        # Hide machine-wide installations so each case sees only its fixtures.
        actual_is_file = Path.is_file
        return [patch.dict(os.environ, environment or {}, clear=True),
                patch.object(resources.shutil, 'which', return_value=which),
                patch.object(resources, 'ROOT', home),
                patch.object(Path, 'home', return_value=home),
                patch.object(Path, 'is_file',
                             lambda path: str(path).startswith(str(home)) and actual_is_file(path))]

    def run_isolated(self, home, **kwargs):
        patches = self.isolated(home, **kwargs)
        for item in patches: item.start()
        try:
            return resources.find_cmake()
        finally:
            for item in reversed(patches): item.stop()

    def test_android_sdk_cmake_is_used_when_path_has_no_cmake(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            sdk = home / 'Library/Android/sdk/cmake'
            fake_cmake(sdk / '3.18.1/bin/cmake', '3.18.1')
            expected = fake_cmake(sdk / '3.22.1/bin/cmake', '3.22.1')
            self.assertEqual(self.run_isolated(home), str(expected))

    def test_too_old_path_cmake_falls_back_to_supported_one(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            old = fake_cmake(home / 'bin/cmake', '3.16.3')
            expected = fake_cmake(home / 'sdk/cmake/3.31.6/bin/cmake', '3.31.6')
            self.assertEqual(
                self.run_isolated(home, environment={'ANDROID_HOME': str(home / 'sdk')}, which=str(old)),
                str(expected))

    def test_explicit_override_must_meet_minimum_version(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            good = fake_cmake(home / 'good/cmake', '4.0.2')
            self.assertEqual(self.run_isolated(home, environment={'FUTACHA_CMAKE': str(good)}), str(good))
            old = fake_cmake(home / 'old/cmake', '3.20.0')
            with self.assertRaisesRegex(RuntimeError, 'FUTACHA_CMAKE=.*CMake >= 3.21'):
                self.run_isolated(home, environment={'FUTACHA_CMAKE': str(old)})

    def test_missing_cmake_explains_required_tool_and_rejected_versions(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            old = fake_cmake(home / 'bin/cmake', '3.10.2')
            with self.assertRaisesRegex(RuntimeError, r'CMake >= 3\.21.*FUTACHA_CMAKE.*3\.10'):
                self.run_isolated(home, which=str(old))


if __name__ == '__main__':
    unittest.main()
