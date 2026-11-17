#!/usr/bin/env python3
"""Validate the main app's Mac distribution profile and derive signing entitlements."""
import argparse
import datetime
import hashlib
from pathlib import Path
import plistlib
import subprocess


def read_profile(path, bundle_id, identities):
    result = subprocess.run(['security', 'cms', '-D', '-i', str(path)],
                            check=True, capture_output=True)
    profile = plistlib.loads(result.stdout)
    if profile.get('Platform') != ['OSX']:
        raise ValueError(f'{path.name}: a macOS profile is required')
    if profile.get('ProvisionedDevices') or profile.get('ProvisionsAllDevices'):
        raise ValueError(f'{path.name}: an App Store distribution profile is required')
    if profile['ExpirationDate'] <= datetime.datetime.now(datetime.timezone.utc).replace(tzinfo=None):
        raise ValueError(f'{path.name}: profile has expired')
    entitlements = profile['Entitlements']
    if entitlements.get('get-task-allow') or entitlements.get('com.apple.security.get-task-allow'):
        raise ValueError(f'{path.name}: development profiles cannot be used')
    team, = profile['TeamIdentifier']
    app_id = entitlements['com.apple.application-identifier']
    prefix, = profile['ApplicationIdentifierPrefix']
    if app_id != f'{prefix}.{bundle_id}':
        raise ValueError(f'{path.name}: expected {bundle_id}, got {app_id}')
    certs = {hashlib.sha1(cert).hexdigest().upper() for cert in profile['DeveloperCertificates']}
    if not any(cert in identities for cert in certs):
        raise ValueError(f'{path.name}: its signing certificate/private key is not installed')
    return team, app_id, certs


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profiles', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    identities = subprocess.check_output(['security', 'find-identity', '-v', '-p', 'basic'], text=True)
    if '3rd Party Mac Developer Installer:' not in identities:
        raise ValueError('Mac Installer Distribution certificate/private key is missing')
    app = read_profile(args.profiles / 'embedded.provisionprofile', 'com.valoser.futacha', identities)
    args.output.mkdir(parents=True, exist_ok=True)
    entitlements = {
        'com.apple.security.app-sandbox': True,
        'com.apple.security.cs.allow-jit': True,
        'com.apple.security.cs.allow-unsigned-executable-memory': True,
        'com.apple.application-identifier': app[1],
        'com.apple.developer.team-identifier': app[0],
        'com.apple.security.network.client': True,
        'com.apple.security.files.user-selected.read-write': True,
        'com.apple.security.files.bookmarks.app-scope': True,
        'com.apple.security.device.audio-input': True,
    }
    (args.output / 'app.entitlements').write_bytes(plistlib.dumps(entitlements))
    # libjli is MH_DYLIB, even though the runtime's CFBundleExecutable points to it.
    # Library code must not have entitlements or an embedded provisioning profile.
    (args.output / 'runtime.entitlements').unlink(missing_ok=True)
    # Compose applies runtimeEntitlementsFile to standalone helpers as well.
    # A helper cannot embed a profile; it must inherit the parent's sandbox and
    # must not claim the app's restricted application identifier (90885).
    (args.output / 'helper.entitlements').write_bytes(plistlib.dumps({
        'com.apple.security.app-sandbox': True,
        'com.apple.security.inherit': True,
    }))
    print('Validated macOS App Store app profile; app/helper entitlements generated.')


if __name__ == '__main__':
    main()
