#!/usr/bin/env python3
"""Fetch the same pinned OpenCV tracking subset used by iOS and desktop."""
from pathlib import Path
import subprocess
import shutil

ROOT = Path(__file__).resolve().parent.parent
REVISION = '40738fb16ceddb5fb3fea747585f7ce6abb0605b'
SOURCE = ROOT / 'build/android-tracking/opencv'


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)


def main():
    SOURCE.parent.mkdir(parents=True, exist_ok=True)
    if not (SOURCE / '.git').exists():
        run('git', 'clone', '--depth', '1', '--filter=blob:none', '--no-checkout',
            '--branch', '5.0.0', 'https://github.com/opencv/opencv.git', SOURCE)
    actual = subprocess.check_output(['git', '-C', str(SOURCE), 'rev-parse', 'HEAD'], text=True).strip()
    if actual != REVISION:
        raise RuntimeError('Unexpected OpenCV tracking revision')
    run('git', '-C', SOURCE, 'sparse-checkout', 'set', '--cone', 'cmake', '3rdparty', 'include',
        'platforms', 'doc', 'docs_sphinx', 'modules/core', 'modules/imgproc', 'modules/features',
        'modules/geometry', 'modules/video', 'modules/flann')
    run('git', '-C', SOURCE, 'checkout', '--detach', REVISION)
    notices = SOURCE.parent / 'licenses'
    notices.mkdir(exist_ok=True)
    shutil.copy2(SOURCE / 'LICENSE', notices / 'OpenCV-LICENSE.txt')


if __name__ == '__main__':
    main()
