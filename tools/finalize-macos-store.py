#!/usr/bin/env python3
"""Finalize Store signatures; reject library entitlements and quarantined payloads."""
import argparse
from pathlib import Path
import plistlib
import subprocess
import zipfile


# Rejected by App Review in VLC's unused macOS screen-capture input plugin.
# This is a regression list, not an exhaustive catalogue of Apple's private APIs.
REJECTED_API_SYMBOLS = (b'CGSCreateRegisteredCursorImage', b'CGSMainConnectionID')


def verify_no_rejected_api_references(app):
    matches = []
    def check(data, location):
        for symbol in REJECTED_API_SYMBOLS:
            if symbol in data:
                matches.append(f'{location}: {symbol.decode()}')
    for path in sorted(app.rglob('*')):
        if path.is_symlink() or not path.is_file():
            continue
        relative = path.relative_to(app)
        check(path.read_bytes(), relative)
        if path.suffix.lower() == '.jar':
            with zipfile.ZipFile(path) as archive:
                for entry in archive.infolist():
                    if not entry.is_dir():
                        check(archive.read(entry), f'{relative}!/{entry.filename}')
    if matches:
        raise ValueError('Rejected private API references in app contents:\n' + '\n'.join(matches))


def signed_entitlements(path):
    data = subprocess.check_output(['codesign', '-d', '--entitlements', '-', '--xml', str(path)],
                                   stderr=subprocess.DEVNULL)
    return plistlib.loads(data) if data.strip() else {}


def mach_o_paths(app):
    # Check code by its file header, not its suffix: jspawnhelper has no extension.
    magic = {bytes.fromhex(value) for value in (
        'feedface', 'cefaedfe', 'feedfacf', 'cffaedfe',
        'cafebabe', 'bebafeca', 'cafebabf', 'bfbafeca')}
    for path in sorted(app.rglob('*')):
        if path.is_symlink() or not path.is_file():
            continue
        with path.open('rb') as source:
            if source.read(4) not in magic:
                continue
        yield path


def mach_o_filetypes(path):
    # CFBundleExecutable and executable permission bits do not distinguish a
    # process entry point from library code. Inspect every Mach-O architecture.
    output = subprocess.check_output(['otool', '-hv', str(path)], text=True)
    types = {line.split()[4] for line in output.splitlines()
             if line.strip().startswith(('MH_MAGIC', 'MH_CIGAM'))}
    if not types:
        raise ValueError(f'{path}: cannot determine Mach-O file type')
    return types


def verify_nested_identifiers(app, profiled_executables):
    allowed = {path.resolve(): app_id for path, app_id in profiled_executables.items()}
    count = 0
    for path in mach_o_paths(app):
        count += 1
        entitlements = signed_entitlements(path)
        if entitlements and mach_o_filetypes(path) != {'EXECUTE'}:
            raise ValueError(f'{path.relative_to(app)}: library code has entitlements (91166)')
        app_id = entitlements.get('com.apple.application-identifier')
        if app_id is not None and app_id != allowed.get(path.resolve()):
            raise ValueError(f'{path.relative_to(app)}: application identifier {app_id} '
                             'has no matching embedded profile for this executable (90885)')
    return count


def quarantine_paths(app):
    # -s checks links themselves, without following them outside the generated app.
    output = subprocess.check_output(['xattr', '-r', '-s', str(app)], text=True)
    return [line for line in output.splitlines()
            if 'com.apple.quarantine' in line.rsplit(': ', 1)[-1].split()]


def verify_no_quarantine(app):
    paths = quarantine_paths(app)
    if paths:
        raise ValueError('quarantine attribute is forbidden in the package (91109):\n' + '\n'.join(paths))


def remove_quarantine(app):
    paths = quarantine_paths(app)
    if paths:
        subprocess.run(['xattr', '-d', '-r', '-s', 'com.apple.quarantine', str(app)], check=True)
    verify_no_quarantine(app)
    return len(paths)


def verify_app(app, entitlements_dir):
    verify_no_rejected_api_references(app)
    subprocess.run(['codesign', '--verify', '--deep', '--strict', str(app)], check=True)
    actual = signed_entitlements(app)
    expected = plistlib.loads((entitlements_dir / 'app.entitlements').read_bytes())
    if actual != expected:
        raise ValueError('app: signed entitlements do not match')
    profile_path = app / 'Contents/embedded.provisionprofile'
    profile = plistlib.loads(subprocess.check_output(['security', 'cms', '-D', '-i',
        str(profile_path)], stderr=subprocess.DEVNULL))
    info = plistlib.loads((app / 'Contents/Info.plist').read_bytes())
    app_id = actual['com.apple.application-identifier']
    if app_id != profile['Entitlements']['com.apple.application-identifier']:
        raise ValueError('app: profile and signed application identifier do not match')
    if not app_id.endswith('.' + info['CFBundleIdentifier']):
        raise ValueError('app: Bundle ID does not match')
    profiled_executables = {app / 'Contents/MacOS' / info['CFBundleExecutable']: app_id}
    count = verify_nested_identifiers(app, profiled_executables)
    for path in app.rglob('embedded.provisionprofile'):
        if path != profile_path:
            raise ValueError(f'{path.relative_to(app)}: unexpected library provisioning profile (90886)')
    helper = app / 'Contents/runtime/Contents/Home/lib/jspawnhelper'
    expected_helper = plistlib.loads((entitlements_dir / 'helper.entitlements').read_bytes())
    if signed_entitlements(helper) != expected_helper:
        raise ValueError('jspawnhelper: must inherit the sandbox without a bundle application identifier')
    verify_no_quarantine(app)
    print(f'Verified {count} Mach-O files: app profile matches; no library entitlements, '
          'unprovisioned identifiers, quarantine attributes or rejected API references; '
          'jspawnhelper inherits the sandbox.')
    return count


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--app', type=Path, required=True)
    parser.add_argument('--entitlements', type=Path, required=True)
    parser.add_argument('--identity')
    parser.add_argument('--verify-only', action='store_true')
    args = parser.parse_args()
    if args.verify_only:
        verify_app(args.app, args.entitlements)
        return
    if not args.identity:
        parser.error('--identity is required unless --verify-only is used')
    runtime = args.app / 'Contents/runtime'
    if mach_o_filetypes(runtime / 'Contents/MacOS/libjli.dylib') != {'DYLIB'}:
        raise ValueError('Expected libjli to be library code')
    # A profile without its application-identifier entitlement would cause 90886.
    # The Java runtime is library code, so neither belongs on this bundle.
    (runtime / 'Contents/embedded.provisionprofile').unlink(missing_ok=True)
    print(f'Removed quarantine from {remove_quarantine(args.app)} generated app paths.')
    app_info = plistlib.loads((args.app / 'Contents/Info.plist').read_bytes())
    runtime_info_path = runtime / 'Contents/Info.plist'
    runtime_info = plistlib.loads(runtime_info_path.read_bytes())
    for key in ['CFBundleShortVersionString', 'CFBundleVersion']:
        runtime_info[key] = app_info[key]
    runtime_info_path.write_bytes(plistlib.dumps(runtime_info))
    prefix = '3rd Party Mac Developer Application: '
    identity = args.identity if args.identity.startswith(prefix) else prefix + args.identity
    # On pre-macOS 15 hosts Compose can attach helper entitlements to libraries.
    # Re-sign such libraries without any entitlements before sealing their bundles.
    for path in mach_o_paths(args.app):
        if path == runtime / 'Contents/MacOS/libjli.dylib':
            continue  # Signed as a bundle below.
        if signed_entitlements(path) and mach_o_filetypes(path) != {'EXECUTE'}:
            subprocess.run(['codesign', '--force', '--timestamp', '--sign', identity, str(path)], check=True)
    for name, bundle in [('runtime', runtime), ('app', args.app)]:
        command = ['codesign', '--force', '--timestamp', '--sign', identity]
        if name == 'app':
            command += ['--options', 'runtime', '--entitlements', str(args.entitlements / 'app.entitlements')]
        subprocess.run(command + [str(bundle)], check=True)
    verify_app(args.app, args.entitlements)


if __name__ == '__main__':
    main()
