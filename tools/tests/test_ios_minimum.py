import copy
import plistlib
import runpy
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


MODULE = runpy.run_path(str(Path(__file__).parents[1] / "verify-ios-minimum.py"))


class ResourceStubTest(unittest.TestCase):
    def setUp(self):
        self.result = dict(path="empty", minimums=["100.0"], text_sizes=["0x0"],
                           dependencies=[dict(path="/usr/lib/libSystem.B.dylib")])

    def check_stub(self, result, symbols=""):
        with patch("subprocess.check_output", return_value=symbols):
            return MODULE["is_resource_stub"](result, {"MinimumOSVersion": "100.0"})

    def test_xcode_empty_static_framework_stub(self):
        self.assertTrue(self.check_stub(self.result))

    def test_newer_real_code_is_not_exempt(self):
        result = copy.deepcopy(self.result)
        result["text_sizes"] = ["0x4"]
        self.assertFalse(self.check_stub(result))
        self.assertFalse(self.check_stub(self.result, "00000000 T _live_function"))

    def test_other_imports_or_missing_text_are_not_exempt(self):
        result = copy.deepcopy(self.result)
        result["dependencies"].append(dict(path="@rpath/Live.framework/Live"))
        self.assertFalse(self.check_stub(result))
        result = copy.deepcopy(self.result)
        result["text_sizes"] = []
        self.assertFalse(self.check_stub(result))

    def test_other_minimum_is_not_exempt(self):
        result = copy.deepcopy(self.result)
        result["minimums"] = ["16.0"]
        self.assertFalse(self.check_stub(result))

    def test_resource_stub_must_not_be_loaded_by_the_host(self):
        with tempfile.TemporaryDirectory() as directory:
            app = Path(directory) / "Example.app"
            framework = app / "Frameworks/Resource.framework"
            framework.mkdir(parents=True)
            (app / "Info.plist").write_bytes(plistlib.dumps(dict(
                CFBundleIdentifier="test.app", CFBundleExecutable="Example", MinimumOSVersion="15.1")))
            (framework / "Info.plist").write_bytes(plistlib.dumps(dict(
                CFBundleIdentifier="test.resource", CFBundleExecutable="Resource", MinimumOSVersion="100.0")))
            stub = dict(self.result, path=str(framework / "Resource"), architectures=["arm64"], platforms=["7"])
            host = dict(path=str(app / "Example"), architectures=["arm64"], platforms=["7"],
                        minimums=["15.1"], text_sizes=["0x100"], dependencies=[
                            dict(command="LC_LOAD_WEAK_DYLIB", path=f"/System/Library/Frameworks/{name}.framework/{name}")
                            for name in ("AppIntents", "FoundationModels")])
            results = {host["path"]: host, stub["path"]: stub}
            with patch.dict(MODULE["verify"].__globals__, {"inspect": lambda path: results[str(path)]}), \
                    patch("subprocess.check_output", return_value=""):
                self.assertEqual(2, len(MODULE["verify"](app, "15.1", "7")["binaries"]))
                host["dependencies"].append(dict(command="LC_LOAD_DYLIB", path="@rpath/Resource.framework/Resource"))
                with self.assertRaisesRegex(ValueError, "resource-only static framework is loaded"):
                    MODULE["verify"](app, "15.1", "7")


if __name__ == "__main__":
    unittest.main()
