#!/usr/bin/env python3
"""Stage Windows x64 media dependencies and build the existing OpenCV bridge locally."""
from concurrent.futures import ThreadPoolExecutor
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile
from desktop_vlc import prune_vlc

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / 'build/windows-dependencies'
OUT = ROOT / 'app-desktop/resources/windows'
OPENCV_SHA = '40738fb16ceddb5fb3fea747585f7ce6abb0605b'
ARCHIVES = {
    'llvm': ('https://github.com/mstorsjo/llvm-mingw/releases/download/20261020/llvm-mingw-20261020-ucrt-x86_64.zip',
             'fd5462b7f5d93b2ff981fcd3f9c7e427e47f3203c64ea91c27bcc444eebd74cd'),
    'cmake': ('https://github.com/Kitware/CMake/releases/download/v4.5.0-rc1/cmake-4.5.0-rc1-windows-x86_64.zip',
              '004ba0808f97795a2654906beacaf19bdfa949b48ed074d1a44c759cab8db6a7'),
    'ninja': ('https://github.com/ninja-build/ninja/releases/download/v1.13.2/ninja-win.zip',
              '07fc8261b42b20e71d1720b39068c2e14ffcee6396b76fb7a795fb460b78dc65'),
    'vlc': ('https://artifacts.videolan.org/vlc/nightly-win64/20261027-0425/vlc-4.0.0-dev-win64-2384e64a.zip',
            'fc7a6ac543f549d063029afadb4fe04e74c64e574b51f88394bbbeedf45262df'),
}

def run(*args, **kwargs):
    subprocess.run([str(a) for a in args], check=True, **kwargs)

def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def prepare_archive(name):
    url, digest = ARCHIVES[name]
    archive = CACHE / url.rsplit('/', 1)[-1]
    if not archive.is_file():
        print('Downloading ' + name, flush=True)
        partial = archive.with_suffix('.download')
        candidates = [url]
        for index in range(6):
            candidate = candidates[index % len(candidates)]
            try:
                offset = partial.stat().st_size if partial.exists() else 0
                request = urllib.request.Request(candidate, headers={'Range': f'bytes={offset}-'} if offset else {})
                with urllib.request.urlopen(request, timeout=45) as response:
                    append = response.status == 206 and response.headers.get('Content-Range', '').startswith(f'bytes {offset}-')
                    with partial.open('ab' if append else 'wb') as output:
                        shutil.copyfileobj(response, output)
                break
            except (OSError, TimeoutError):
                if index == 5: raise
                print('Retrying ' + name + ' download', flush=True)
        if sha(partial) != digest:
            raise RuntimeError(name + ' checksum mismatch')
        partial.replace(archive)
    if sha(archive) != digest:
        raise RuntimeError(name + ' checksum mismatch')
    target = CACHE / name
    marker = target / '.sha256'
    if not marker.is_file() or marker.read_text().strip() != digest:
        if target.exists():
            shutil.rmtree(target)
        with zipfile.ZipFile(archive) as source:
            for member in source.infolist():
                if not (target / member.filename).resolve().is_relative_to(target.resolve()):
                    raise RuntimeError('Unsafe archive path')
            source.extractall(target)
        marker.write_text(digest + '\n')

def main():
    if os.name != 'nt' or os.environ.get('PROCESSOR_ARCHITECTURE', '').upper() != 'AMD64':
        raise RuntimeError('Windows x64 is required to build this package.')
    CACHE.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(prepare_archive, ARCHIVES))
    llvm = CACHE / 'llvm/llvm-mingw-20261020-ucrt-x86_64/bin'
    cmake = CACHE / 'cmake/cmake-4.5.0-rc1-windows-x86_64/bin/cmake.exe'
    ninja = CACHE / 'ninja/ninja.exe'
    env = dict(os.environ, PATH=str(llvm) + os.pathsep + os.environ['PATH'])
    source = CACHE / 'opencv'
    if not (source / '.git').exists():
        run('git', 'clone', '--depth', '1', '--filter=blob:none', '--no-checkout', '--branch', '5.0.0',
            'https://github.com/opencv/opencv.git', source)
        run('git', '-C', source, 'sparse-checkout', 'set', '--cone', 'cmake', '3rdparty', 'include', 'platforms',
            'doc', 'docs_sphinx', 'modules/core', 'modules/imgproc', 'modules/features', 'modules/geometry', 'modules/video', 'modules/flann')
        run('git', '-C', source, 'checkout', '--detach', OPENCV_SHA)
    if subprocess.check_output(['git', '-C', str(source), 'rev-parse', 'HEAD'], text=True).strip() != OPENCV_SHA:
        raise RuntimeError('OpenCV source revision mismatch')
    build = ROOT / 'shared/build/nativeTracking/windowsX64'
    run(cmake, '-S', ROOT / 'shared/src/nativeInterop/tracking', '-B', build, '-G', 'Ninja',
        '-DCMAKE_BUILD_TYPE=Release', '-DCMAKE_MAKE_PROGRAM=' + ninja.as_posix(),
        '-DCMAKE_C_COMPILER=' + (llvm / 'x86_64-w64-mingw32-clang.exe').as_posix(),
        '-DCMAKE_CXX_COMPILER=' + (llvm / 'x86_64-w64-mingw32-clang++.exe').as_posix(),
        '-DFUTACHA_DESKTOP=ON', '-DWITH_MSMF=OFF', '-DWITH_DSHOW=OFF', '-DWITH_OPENEXR=OFF',
        '-DOPENCV_SOURCE=' + source.as_posix(), env=env)
    run(cmake, '--build', build, '--target', 'futacha_tracking_bridge', '--parallel', '4', env=env)
    native = OUT / 'native'
    native.mkdir(exist_ok=True)
    shutil.copy2(build / 'futacha_tracking_bridge.dll', native)
    # Keep the compiler runtime local to the bridge if this toolchain links it dynamically.
    for name in ['libc++.dll', 'libunwind.dll', 'libwinpthread-1.dll']:
        runtime = llvm / name
        if runtime.is_file(): shutil.copy2(runtime, native)
    vlc = CACHE / 'vlc/vlc-4.0.0-dev'
    staged_vlc = CACHE / 'vlc-stage'
    if staged_vlc.exists():
        shutil.rmtree(staged_vlc)
    shutil.copytree(vlc, staged_vlc)
    prune_vlc(staged_vlc, 'windows')
    if (OUT / 'vlc').exists():
        shutil.rmtree(OUT / 'vlc')
    shutil.move(str(staged_vlc), OUT / 'vlc')
    icons = OUT / 'icons'
    icons.mkdir(exist_ok=True)
    for variant, name in [('Current', 'AppIcon'), ('Classic', 'AppIconClassic'), ('Midnight', 'AppIconMidnight')]:
        source_icons = ROOT / f'iosApp/iosApp/Assets.xcassets/{name}.appiconset'
        shutil.copy2(next(source_icons.glob('*.png')), icons / (variant + '.png'))
    run('powershell.exe', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File',
        ROOT / 'tools/create-windows-icon.ps1', '-Source', icons / 'Current.png', '-Target', OUT / 'Futacha.ico')
    licenses = native / 'licenses'
    licenses.mkdir(exist_ok=True)
    shutil.copy2(source / 'LICENSE', licenses / 'OpenCV.txt')
    for license_file in (llvm.parent).glob('LICENSE*'):
        if license_file.is_file(): shutil.copy2(license_file, licenses / license_file.name)
    print('Windows resources prepared.', flush=True)

if __name__ == '__main__':
    main()
