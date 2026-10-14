#!/usr/bin/env python3
"""Project-local SDK installation and immutable official archive evidence.

Only run with permission to install the documented Google SDK packages and accept
their SDK license. This script never installs on a connected Android device.
"""
import hashlib
import json
import os
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TC = ROOT / '.toolchain'
LOCK = ROOT / 'tools/toolchain-lock.json'


def revision(package):
    source = TC / 'android-sdk' / package.replace(';', '/') / 'source.properties'
    if not source.exists():
        return None
    return next(line.split('=', 1)[1] for line in source.read_text().splitlines()
                if line.startswith('Pkg.Revision='))


def main():
    lock = json.loads(LOCK.read_text())
    env = dict(os.environ, JAVA_HOME=str(TC / 'jdk'),
               ANDROID_HOME=str(TC / 'android-sdk'), ANDROID_SDK_ROOT=str(TC / 'android-sdk'),
               ANDROID_USER_HOME=str(TC / 'android-user'), TMPDIR=str(TC / 'tmp'),
               JAVA_TOOL_OPTIONS=f'-Djava.io.tmpdir={TC}/tmp -Djava.util.prefs.userRoot={TC}/java-prefs -Duser.home={TC}/user-home')
    for package in lock['android_packages']:
        name = package['url'].rsplit('/', 1)[1]
        archive = TC / 'downloads' / name
        if not archive.exists():
            subprocess.run(['curl', '--fail', '--location', '--silent', '--show-error',
                            '--retry', '2', package['url'], '--output', str(archive)], check=True)
        data = archive.read_bytes()
        if hashlib.sha1(data).hexdigest() != package['publisher_sha1']:
            raise RuntimeError(f'Official archive checksum mismatch: {name}; preserved')
        actual = hashlib.sha256(data).hexdigest()
        if 'sha256' in package and package['sha256'] != actual:
            raise RuntimeError(f'Locked SHA-256 mismatch: {name}; preserved')
        package['sha256'] = actual
        print(f'SDK_ARCHIVE_VERIFIED {name} {actual}', flush=True)
    LOCK.write_text(json.dumps(lock, indent=2) + '\n')
    needed = []
    for package in lock['android_packages']:
        installed = revision(package['package'])
        if installed is None:
            needed.append(package['package'])
        elif installed != package['revision']:
            raise RuntimeError(f'Unexpected SDK revision for {package["package"]}: {installed}; preserved')
    if needed:
        manager = TC / 'android-sdk/cmdline-tools/22.0/bin/sdkmanager'
        with (TC / 'logs/sdk-install.log').open('a') as log:
            subprocess.run([str(manager), f'--sdk_root={TC}/android-sdk', *needed],
                           input='y\n' * 20, text=True, env=env,
                           stdout=log, stderr=subprocess.STDOUT, check=True)
    for package in lock['android_packages']:
        actual = revision(package['package'])
        if actual != package['revision']:
            raise RuntimeError(f'SDK repository revision changed: {package["package"]}; '
                               'do not silently update the lock; inspect the retained pinned archive')
        print(f'SDK_INSTALLED {package["package"]} revision={actual}', flush=True)


if __name__ == '__main__':
    main()
