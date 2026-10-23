"""Audit the exact Git index before publishing; never print matched contents."""
from pathlib import Path
import json
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)


def main():
    names = git('ls-files', '-z').decode().split('\0')
    names = [name for name in names if name]
    failures = []
    total = 0
    top = set()
    blocked_parts = {'.git', '.codex', '.agents', '.impeccable', 'node_modules', 'build', 'dist', '.deps', 'design-prototype'}
    content_rules = [
        ('local home path', re.compile(rb'(?:/Users/|/home/)[A-Za-z0-9_.-]+/')),
        ('conversation export', re.compile(rb'<(?:send_user_message_question_reply|in-app-browser-context|oai-mem-citation)>|"(?:rollout_path|thread_id|session_id)"\s*:|"role"\s*:\s*"(?:assistant|system)"')),
        ('private key', re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----')),
        ('credential token', re.compile(rb'\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|sk-(?:proj-)?[A-Za-z0-9_-]{32,}|AKIA[A-Z0-9]{16})\b')),
    ]
    for name in names:
        path = Path(name)
        top.add(path.parts[0])
        mode = git('ls-files', '-s', '--', name).decode().split()[0]
        if mode in {'160000', '120000'}:
            failures.append({'file': name, 'reason': 'submodule or symlink requires review'})
        if any(part in blocked_parts or part.startswith('.next') for part in path.parts):
            failures.append({'file': name, 'reason': 'local/generated directory'})
        if name.startswith('docs/') and not name.startswith('docs/public/'):
            failures.append({'file': name, 'reason': 'private documentation'})
        if path.name in {'PRODUCT.md', 'DESIGN.md', 'AGENTS.md', 'SKILL.md'} or path.name.startswith('.env') or re.search(r'(?i)(prompt|conversation|chat-history|对话|提示词)', path.name):
            failures.append({'file': name, 'reason': 'private context or environment file'})
        if path.suffix.lower() in {'.jsonl', '.log', '.jar', '.zip', '.class', '.har', '.pem', '.key', '.p12', '.pfx', '.jks', '.keystore'}:
            failures.append({'file': name, 'reason': 'archive, log, binary build or credential file'})
        data = git('show', ':' + name)
        total += len(data)
        if len(data) > 5 * 1024 * 1024:
            failures.append({'file': name, 'reason': 'file larger than 5 MiB'})
        if name != 'tools/check-publication.py':
            for reason, pattern in content_rules:
                if pattern.search(data):
                    failures.append({'file': name, 'reason': reason})
    required = ['README.md', 'web/package.json', 'web/package-lock.json', 'web/src/components/wallet-client.tsx', 'signer/src/NWalletMIDlet.java', 'signer/deps.lock', 'mac-bridge/Sources/main.swift']
    for name in required:
        if name not in names:
            failures.append({'file': name, 'reason': 'missing required monorepo component'})
    result = {'files': len(names), 'bytes': total, 'topLevel': sorted(top), 'failures': failures}
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 1 if failures or not names else 0


if __name__ == '__main__':
    sys.exit(main())
