"""Select libVLC runtime files; preserve all media codecs and output plugins."""
from pathlib import Path
import shutil


def prune_vlc(root: Path, platform: str):
    # The application uses --intf=dummy and --no-lua, and provides its own UI.
    # Keep locale data: libVLC may return localized errors to the application.
    names = ['lua', 'skins', 'msi', 'vlc.exe', 'vlc.ico', 'axvlc.dll', 'npvlc.dll', 'vlc-cache-gen.exe']
    # The macOS screen-input plugin calls private CGS cursor APIs. Futacha only
    # plays media files/URLs; decoded video frames do not use screen capture.
    plugins = ['plugins/gui'] if platform == 'windows' else [
        'plugins/libmacosx_plugin.dylib', 'plugins/libscreen_plugin.dylib',
    ]
    for relative in names + plugins + ['plugins/plugins.dat']:
        path = root / relative
        if path.is_dir():
            shutil.rmtree(path)
        elif path.exists():
            path.unlink()
