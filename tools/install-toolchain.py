#!/usr/bin/env python3
"""Explicitly approved, project-local bootstrap. No Homebrew or global writes."""
import hashlib
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TC = ROOT / '.toolchain'
DOWNLOADS = TC / 'downloads'


def fetch(url, name, digest):
    target = DOWNLOADS / name
    if not target.exists():
        subprocess.run(['curl', '--fail', '--location', '--silent', '--show-error',
                        '--retry', '2', url, '--output', str(target)], check=True)
    actual = hashlib.sha256(target.read_bytes()).hexdigest()
    if actual != digest:
        raise RuntimeError(f'Checksum mismatch: {name}; file preserved for inspection')
    print(f'SHA256_OK {name} {actual}', flush=True)
    return target


def main():
    DOWNLOADS.mkdir(parents=True, exist_ok=True)
    for folder in ('logs', 'tmp', 'android-user', 'java-prefs', 'user-home'):
        (TC / folder).mkdir(exist_ok=True)
    lock_path = ROOT / 'tools/toolchain-lock.json'
    lock = json.loads(lock_path.read_text())
    for tool in lock['bootstrap']:
        archive = fetch(tool['url'], tool['archive'], tool['sha256'])
        dest = ROOT / tool['destination']
        if dest.exists():
            print(f'PRESERVED {tool["destination"]}', flush=True)
            continue
        if tool['id'] == 'jdk':
            extracted = TC / 'jdk-distribution'
            extracted.mkdir(exist_ok=True)
            subprocess.run(['tar', '-xzf', str(archive), '-C', str(extracted)], check=True)
            homes = list(extracted.glob('*/Contents/Home'))
            if len(homes) != 1:
                raise RuntimeError('Expected exactly one macOS JDK Home')
            dest.symlink_to(homes[0].relative_to(TC), target_is_directory=True)
        elif tool['id'] == 'gradle':
            subprocess.run(['unzip', '-q', str(archive), '-d', str(TC)], check=True)
        elif tool['id'] == 'android-commandline':
            extracted = TC / 'commandline-distribution'
            extracted.mkdir(exist_ok=True)
            subprocess.run(['unzip', '-q', str(archive), '-d', str(extracted)], check=True)
            dest.parent.mkdir(parents=True, exist_ok=True)
            (extracted / 'cmdline-tools').rename(dest)
        print(f'INSTALLED {tool["destination"]}', flush=True)


if __name__ == '__main__':
    main()
