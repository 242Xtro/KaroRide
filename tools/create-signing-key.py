#!/usr/bin/env python3
"""Own project-local update key; never prints or passes passwords on the command line."""
import os
from pathlib import Path
import secrets
import subprocess

root = Path(__file__).resolve().parents[1]
directory = root / 'secrets'
directory.mkdir(exist_ok=True, mode=0o700)
directory.chmod(0o700)
key = directory / 'aceride.jks'
properties = directory / 'signing.properties'
password_file = directory / 'keystore-password'
if key.exists() and properties.exists():
    print('OWN_SIGNING_KEY_PRESERVED')
    raise SystemExit(0)
if any(p.exists() for p in [key, properties, password_file]):
    raise SystemExit('Incomplete existing signing material preserved; inspect locally before continuing.')
password = secrets.token_urlsafe(36)
fd = os.open(password_file, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, 'w') as f:
    f.write(password + '\n')
env = dict(os.environ, JAVA_TOOL_OPTIONS=f'-Duser.home={root}/.toolchain/user-home -Djava.io.tmpdir={root}/.toolchain/tmp')
command = [str(root / '.toolchain/jdk/bin/keytool'), '-genkeypair', '-noprompt',
           '-keystore', str(key), '-storetype', 'JKS', '-alias', 'aceride',
           '-keyalg', 'RSA', '-keysize', '3072', '-sigalg', 'SHA256withRSA', '-validity', '10000',
           '-dname', 'CN=KaroRide Community, OU=Community Build, O=KaroRide, C=DE',
           '-storepass:file', str(password_file), '-keypass:file', str(password_file)]
result = subprocess.run(command, env=env, capture_output=True, text=True)
if result.returncode:
    raise SystemExit(f'Keytool failed with exit {result.returncode}; original partial files preserved.')
key.chmod(0o600)
fd = os.open(properties, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, 'w') as f:
    f.write(f'storePassword={password}\nkeyPassword={password}\nkeyAlias=aceride\n')
print('OWN_PROJECT_SIGNING_KEY_CREATED; RSA_3072; MODE_600; PASSWORD_NOT_PRINTED')
