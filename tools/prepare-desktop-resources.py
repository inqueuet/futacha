#!/usr/bin/env python3
"""Build the Mac native bridge and stage pinned VLC libraries; no system installation."""
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import urllib.request
from desktop_vlc import prune_vlc

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'app-desktop/resources/macos'
CACHE = ROOT / 'build/desktop-dependencies'
VLC_SHA = 'c0e41e496f47edcda1fdba5e6a7f14671c87284acdbc73e669d532fc32c06824'
OPENCV_SHA = 'e4f78fbcf1a773fbed69e14500e8b734963eb585'

def run(*args):
    subprocess.run([str(a) for a in args], check=True)

def sha(path):
    h = hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()

CMAKE_MINIMUM = (3, 21)  # shared/src/nativeInterop/tracking/CMakeLists.txt

def cmake_version(cmake):
    try:
        output = subprocess.run([str(cmake), '--version'], capture_output=True, text=True, timeout=30).stdout
    except (OSError, subprocess.SubprocessError):
        return None
    match = re.search(r'cmake version (\d+)\.(\d+)', output)
    return (int(match.group(1)), int(match.group(2))) if match else None

def find_cmake():
    explicit = os.environ.get('FUTACHA_CMAKE')
    if explicit:
        version = cmake_version(explicit)
        if version is None or version < CMAKE_MINIMUM:
            raise RuntimeError(f'FUTACHA_CMAKE={explicit} is not CMake >= 3.21 (found {version or "no usable cmake"})')
        return explicit
    # Gradle and IDE launches often lack the interactive shell's PATH, so also look
    # in the same machine-wide and Android SDK locations as build-ios-tracking.py.
    candidates = [Path(found) for found in [shutil.which('cmake')] if found]
    candidates += [ROOT / 'build/native-build-tools/cmake/data/bin/cmake',
                   Path('/opt/homebrew/bin/cmake'), Path('/usr/local/bin/cmake'),
                   Path('/Applications/CMake.app/Contents/bin/cmake')]
    sdk_roots = [Path(os.environ[name]) for name in ('ANDROID_HOME', 'ANDROID_SDK_ROOT') if os.environ.get(name)]
    sdk_roots.append(Path.home() / 'Library/Android/sdk')
    for sdk_root in sdk_roots:
        versions = [path for path in (sdk_root / 'cmake').glob('*')
                    if path.name and all(part.isdigit() for part in path.name.split('.'))]
        versions.sort(key=lambda path: tuple(map(int, path.name.split('.'))), reverse=True)
        candidates += [path / 'bin/cmake' for path in versions]
    too_old = []
    for candidate in candidates:
        if not (candidate.is_file() and os.access(candidate, os.X_OK)):
            continue
        version = cmake_version(candidate)
        if version is not None and version >= CMAKE_MINIMUM:
            return str(candidate)
        too_old.append(f'{candidate} ({".".join(map(str, version)) if version else "unreadable version"})')
    detail = f' Rejected: {", ".join(too_old)}.' if too_old else ''
    raise RuntimeError('Install CMake >= 3.21 (Homebrew, CMake.app or Android SDK cmake) '
                       'or set FUTACHA_CMAKE to its executable path.' + detail)

def main():
    if os.uname().sysname != 'Darwin' or os.uname().machine != 'arm64':
        raise RuntimeError('This package currently targets Apple Silicon macOS.')
    CACHE.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    marker = OUT / 'vlc/.source-sha256'
    if not marker.exists() or marker.read_text().strip() != VLC_SHA:
        image = CACHE / 'vlc-4.0.0-dev-arm64-2384e64a.dmg'
        existing = ROOT / 'build/desktop-research/phase3/vlc-4.0.0-dev-arm64-2384e64a.dmg'
        if not image.exists() and existing.exists() and sha(existing) == VLC_SHA:
            shutil.copy2(existing, image)
        if not image.exists():
            partial = image.with_suffix('.download')
            urllib.request.urlretrieve('https://artifacts.videolan.org/vlc/nightly-macos-arm64/20261027-0426/vlc-4.0.0-dev-arm64-2384e64a.dmg', partial)
            if sha(partial) != VLC_SHA:
                raise RuntimeError('VLC archive checksum mismatch')
            partial.replace(image)
        if sha(image) != VLC_SHA:
            raise RuntimeError('VLC archive checksum mismatch')
        mount = CACHE / 'vlc-volume'
        run('hdiutil', 'attach', '-readonly', '-nobrowse', '-mountpoint', mount, image)
        try:
            # VLC 4 moved libVLC and its plugins into Contents/Frameworks.
            # Stage a complete runtime so plugins from the old ABI cannot survive.
            source = mount / 'VLC.app/Contents/Frameworks'
            staged = CACHE / 'vlc-stage'
            if staged.exists():
                shutil.rmtree(staged)
            (staged / 'lib').mkdir(parents=True)
            for library in source.glob('*.dylib'):
                if library.is_symlink():
                    (staged / 'lib' / library.name).symlink_to(os.readlink(library))
                else:
                    shutil.copy2(library, staged / 'lib' / library.name)
            run('ditto', source / 'plugins', staged / 'plugins')
            license_dir = staged / 'licenses'
            license_dir.mkdir(exist_ok=True)
            for license_file in (ROOT / 'app-desktop/resources/common/licenses').glob('VLC-*'):
                shutil.copy2(license_file, license_dir / license_file.name)
            prune_vlc(staged, 'macos')
            (staged / '.source-sha256').write_text(VLC_SHA + '\n')
            if (OUT / 'vlc').exists():
                shutil.rmtree(OUT / 'vlc')
            shutil.move(str(staged), OUT / 'vlc')
        finally:
            run('hdiutil', 'detach', mount)
    # Gradle/jpackage changes file timestamps; the upstream cache would be stale.
    prune_vlc(OUT / 'vlc', 'macos')
    source = ROOT / 'build/native-tracking/opencv-5.1.0'
    if not (source / '.git').exists():
        run('git', 'clone', '--depth', '1', '--filter=blob:none', '--no-checkout', '--branch', '5.1.0', 'https://github.com/opencv/opencv.git', source)
        run('git', '-C', source, 'sparse-checkout', 'set', '--cone', 'cmake', '3rdparty', 'include', 'platforms', 'doc', 'docs_sphinx', 'modules/core', 'modules/imgproc', 'modules/features', 'modules/geometry', 'modules/video', 'modules/flann')
        run('git', '-C', source, 'checkout', '--detach', OPENCV_SHA)
    if subprocess.check_output(['git', '-C', str(source), 'rev-parse', 'HEAD'], text=True).strip() != OPENCV_SHA:
        raise RuntimeError('OpenCV source revision mismatch')
    cmake = find_cmake()
    build = ROOT / 'shared/build/nativeTracking/macosArm64'
    run(cmake, '-S', ROOT / 'shared/src/nativeInterop/tracking', '-B', build,
        '-DCMAKE_BUILD_TYPE=Release', '-DCMAKE_OSX_ARCHITECTURES=arm64', '-DCMAKE_OSX_DEPLOYMENT_TARGET=13.0',
        '-DFUTACHA_DESKTOP=ON', '-DWITH_KLEIDICV=OFF', '-DWITH_CAROTENE=OFF', '-DOPENCV_SOURCE=' + str(source))
    run(cmake, '--build', build, '--target', 'futacha_tracking_bridge', '--parallel', '4')
    (OUT / 'native').mkdir(exist_ok=True)
    shutil.copy2(build / 'libfutacha_tracking_bridge.dylib', OUT / 'native')
    run('xcrun', 'clang', '-dynamiclib', '-fobjc-arc', '-fblocks', '-mmacosx-version-min=13.0', '-arch', 'arm64',
        '-framework', 'AppKit', '-framework', 'UserNotifications', '-framework', 'Speech', '-framework', 'AVFoundation',
        ROOT / 'shared/src/nativeInterop/macos/FutachaMac.m', '-o', OUT / 'native/libfutacha_macos.dylib')
    run('xcrun', 'swiftc', '-emit-library', '-O', '-swift-version', '5', '-target', 'arm64-apple-macosx13.0',
        '-module-name', 'FutachaDesktopAi', '-Xlinker', '-weak_framework', '-Xlinker', 'FoundationModels',
        ROOT / 'shared/src/nativeInterop/macos/FutachaAi.swift', '-o', OUT / 'native/libfutacha_ai.dylib')
    (OUT / 'icons').mkdir(exist_ok=True)
    for variant, name in [('Current', 'AppIcon'), ('Classic', 'AppIconClassic'), ('Midnight', 'AppIconMidnight')]:
        source_icons = ROOT / f'iosApp/iosApp/Assets.xcassets/{name}.appiconset'
        shutil.copy2(next(source_icons.glob('*.png')), OUT / f'icons/{variant}.png')
    iconset = CACHE / 'Futacha.iconset'
    iconset.mkdir(exist_ok=True)
    original = ROOT / 'iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png'
    for size in [16, 32, 128, 256, 512]:
        for scale in [1, 2]:
            suffix = '@2x' if scale == 2 else ''
            run('sips', '-z', size * scale, size * scale, original, '--out', iconset / f'icon_{size}x{size}{suffix}.png')
    run('iconutil', '-c', 'icns', iconset, '-o', OUT / 'Futacha.icns')

if __name__ == '__main__':
    main()
