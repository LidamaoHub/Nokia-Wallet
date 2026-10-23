"""Fetch hash-pinned build dependencies into this project's private cache."""
from pathlib import Path
import argparse
import hashlib
import io
import shutil
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def digest(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--from-cache', type=Path, help='Read verified download archives from an existing cache instead of the network')
    parser.add_argument('--dest', type=Path, default=ROOT / '.deps/j2me')
    args = parser.parse_args()
    rows = [line.split('\t') for line in (ROOT / 'deps.lock').read_text().splitlines() if line and not line.startswith('#')]
    args.dest.mkdir(parents=True, exist_ok=True)
    for name, expected, _license, url in rows:
        target = args.dest / name
        if target.is_file() and digest(target.read_bytes()) == expected and not name.endswith('.zip'):
            continue
        if not url.startswith('https://'):
            raise SystemExit('Missing verified extracted dependency: ' + name)
        if target.is_file() and digest(target.read_bytes()) == expected:
            data = target.read_bytes()
        elif args.from_cache:
            data = (args.from_cache / name).read_bytes()
        else:
            with urllib.request.urlopen(url, timeout=90) as response:
                data = response.read()
        if digest(data) != expected:
            raise SystemExit('Dependency hash mismatch: ' + name)
        temporary = target.with_name(target.name + '.download')
        temporary.write_bytes(data)
        temporary.replace(target)
        if name.endswith('.zip'):
            with zipfile.ZipFile(io.BytesIO(data)) as archive:
                for member in archive.infolist():
                    if not member.filename.startswith('proguard-7.9.1/'):
                        raise SystemExit('Unexpected dependency archive entry')
                    output = (args.dest / member.filename).resolve()
                    if args.dest.resolve() not in output.parents:
                        raise SystemExit('Unsafe dependency archive path')
                    if member.is_dir():
                        output.mkdir(parents=True, exist_ok=True)
                    else:
                        output.parent.mkdir(parents=True, exist_ok=True)
                        with archive.open(member) as source, output.open('wb') as destination:
                            shutil.copyfileobj(source, destination)
    for name, expected, _license, _url in rows:
        if digest((args.dest / name).read_bytes()) != expected:
            raise SystemExit('Dependency verification failed: ' + name)
    print('PASS: all pinned build dependency hashes; project-local cache ready')


if __name__ == '__main__':
    main()
