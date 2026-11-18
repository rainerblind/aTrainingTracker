#!/usr/bin/env python3
"""
tools/check_secrets_hygiene.py

Offline secret-pattern scanner for tracked repository files and local credential
file permission hygiene helper (REQ-PRO-124 / ATT-8170 / ASPICE SUP.8 BP2/BP3):
- Default mode: scans all tracked files (`git ls-files`) for high-confidence
  API keys, tokens, PEM private keys, Basic Auth headers, and non-placeholder
  credential assignments while narrowly allowlisting documented build placeholders
  and explicit unit test fixtures by `(rel_path, sha256)`.
- `--check-local-perms` / `--fix-local-perms`: verifies or enforces `0600`
  owner-only Unix permissions on existing untracked local credential/signing
  files (`.env.claude`, `.env.gemini`, `.env.jira*`, `secrets.properties`,
  `release.properties`, `app/google-services.json`, `release.keystore`).
"""

import argparse
import hashlib
import os
import re
import stat
import subprocess
import sys
from pathlib import Path

LOCAL_CREDENTIAL_FILENAMES = (
    ".env.claude",
    ".env.gemini",
    "secrets.properties",
    "release.properties",
    "app/google-services.json",
    "release.keystore",
)

# SHA-256 digests of exact documented placeholders and unit test fixtures,
# scoped strictly to their authorized repository paths.
PLACEHOLDER_MAPS_HASH = "9b5fb46c8a72edb4aafa6e9b994ec7f838b911c18d36bc012ce812d362685500"
TEST_JIRA_ATATT_HASH = "427abccc576c198b1189893f7d316455c413de3f998b0db63ed39429b748323d"
TEST_JIRA_BASIC_HASH = "c7a225ed0817573d5067b036a0131e012ebe155c52d391e93b1c816437ac6a73"
STRAVA_TEST_SECRET_HASH = hashlib.sha256(b"client_secret=supersecret123").hexdigest()

ALLOWLISTED_FILE_HASHES = {
    ("secrets.defaults.properties", PLACEHOLDER_MAPS_HASH),
    ("app/build.gradle", PLACEHOLDER_MAPS_HASH),
    ("docs/engineering/plans/ATT-4004_plan.md", PLACEHOLDER_MAPS_HASH),
    ("docs/engineering/analysis/ATT-8170_analysis.md", PLACEHOLDER_MAPS_HASH),
    ("docs/engineering/analysis/ATT-8170_analysis.md", TEST_JIRA_BASIC_HASH),
    ("docs/engineering/plans/ATT-8170_plan.md", PLACEHOLDER_MAPS_HASH),
    ("tools/test_jira_accounts.py", TEST_JIRA_ATATT_HASH),
    ("tools/test_jira_accounts.py", TEST_JIRA_BASIC_HASH),
    (
        "app/src/test/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaHelperTest.kt",
        STRAVA_TEST_SECRET_HASH,
    ),
}

# Benign assignment values (case-insensitive substring or exact checks) for
# build placeholders, CI dummies, redactions, and template references.
BENIGN_ASSIGNMENT_MARKERS = (
    "placeholder",
    "ci-dummy",
    "redacted",
    "your_api_key",
    "supersecret123",
    "example",
    "dummy",
)

SECRET_PATTERNS = (
    ("GOOGLE_API_KEY", re.compile(r"AIza[0-9A-Za-z_\-]{20,}")),
    ("AWS_ACCESS_KEY_ID", re.compile(r"\bAKIA[0-9A-Z]{16}\b")),
    ("GITHUB_TOKEN", re.compile(r"\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9_]{20,}\b")),
    ("SLACK_TOKEN", re.compile(r"\bxox[baprs]-[0-9A-Za-z\-]{10,}\b")),
    ("ATLASSIAN_API_TOKEN", re.compile(r"\bATATT3x[A-Za-z0-9_\-=]{20,}")),
    ("PEM_PRIVATE_KEY", re.compile(r"-----BEGIN (?:RSA |EC |DSA |OPENSSH |PGP )?PRIVATE KEY-----")),
    ("BASIC_AUTH_HEADER", re.compile(r"\bBasic\s+(?!Authorization\b|Authentication\b)[A-Za-z0-9+/]{12,}={0,2}")),
    (
        "CREDENTIAL_ASSIGNMENT",
        re.compile(
            r"""\b(?:STRAVA_CLIENT_SECRET|DROPBOX_APP_KEY|MAPS_API_KEY|JIRA_API_TOKEN|JIRA_TOKEN|RELEASE_KEYSTORE_PASSWORD|RELEASE_KEY_PASSWORD|keyStorePassword|keyAliasPassword|client_secret)\s*[:=]\s*["']?([A-Za-z0-9_\-/+=]{12,})["']?"""
        ),
    ),
)


def _default_repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def _token_sha256(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def _is_benign_assignment(rule_name: str, full_match: str, captured: str) -> bool:
    if rule_name != "CREDENTIAL_ASSIGNMENT":
        return False
    lowered = captured.lower()
    if any(marker in lowered for marker in BENIGN_ASSIGNMENT_MARKERS):
        return True
    if set(captured) <= {"0", "x", "X", "*"}:
        return True
    return False


def scan_text(rel_path: str, text: str) -> list[dict]:
    """Scans a single file's text and returns non-allowlisted secret findings."""
    findings = []
    norm_path = rel_path.replace("\\", "/")
    for lineno, line in enumerate(text.splitlines(), start=1):
        for rule_name, pattern in SECRET_PATTERNS:
            for match in pattern.finditer(line):
                token = match.group(0)
                captured = match.group(1) if match.lastindex else token
                if _is_benign_assignment(rule_name, token, captured):
                    continue
                if (norm_path, _token_sha256(token)) in ALLOWLISTED_FILE_HASHES:
                    continue
                if (norm_path, _token_sha256(captured)) in ALLOWLISTED_FILE_HASHES:
                    continue
                findings.append(
                    {
                        "path": norm_path,
                        "line": lineno,
                        "rule": rule_name,
                        "preview": token[:8] + "..." if len(token) > 8 else token,
                    }
                )
    return findings


def list_tracked_files(repo_root: Path) -> list[str]:
    """Lists tracked repository files via `git ls-files -z`."""
    res = subprocess.run(
        ["git", "-C", str(repo_root), "ls-files", "-z"],
        capture_output=True,
        check=True,
    )
    raw = res.stdout.decode("utf-8", errors="replace")
    return [entry for entry in raw.split("\0") if entry]


def scan_repository(repo_root: Path | None = None) -> list[dict]:
    """Scans all tracked text files in `repo_root` for secret-pattern leaks."""
    root = Path(repo_root) if repo_root else _default_repo_root()
    all_findings = []
    for rel_path in list_tracked_files(root):
        full_path = root / rel_path
        if not full_path.is_file():
            continue
        raw_bytes = full_path.read_bytes()
        if b"\x00" in raw_bytes[:8192]:
            continue
        text = raw_bytes.decode("utf-8", errors="replace")
        all_findings.extend(scan_text(rel_path, text))
    return all_findings


def discover_local_credential_files(workspace_dir: Path) -> list[Path]:
    """Discovers existing local credential/signing files in `workspace_dir`."""
    candidates: list[Path] = []
    for rel in LOCAL_CREDENTIAL_FILENAMES:
        p = workspace_dir / rel
        if p.exists() and p.is_file():
            candidates.append(p)
    for entry in sorted(workspace_dir.iterdir()):
        if entry.name.startswith(".env.jira") and not entry.name.endswith(".example"):
            if entry.exists() and entry.is_file() and entry not in candidates:
                candidates.append(entry)
    return candidates


def check_local_permissions(workspace_dir: Path | None = None) -> list[dict]:
    """Returns a list of local credential files whose Unix mode is not 0o600."""
    ws = Path(workspace_dir) if workspace_dir else _default_repo_root()
    violations = []
    for path in discover_local_credential_files(ws):
        mode = stat.S_IMODE(os.stat(path).st_mode)
        if mode != 0o600:
            violations.append(
                {
                    "path": str(path),
                    "mode": oct(mode),
                    "expected": "0o600",
                }
            )
    return violations


def fix_local_permissions(workspace_dir: Path | None = None) -> list[dict]:
    """Sets `0600` permissions on all existing local credential files in `workspace_dir`."""
    ws = Path(workspace_dir) if workspace_dir else _default_repo_root()
    changed = []
    for path in discover_local_credential_files(ws):
        before = stat.S_IMODE(os.stat(path).st_mode)
        if before != 0o600:
            os.chmod(path, 0o600)
            after = stat.S_IMODE(os.stat(path).st_mode)
            changed.append(
                {
                    "path": str(path),
                    "before": oct(before),
                    "after": oct(after),
                }
            )
    return changed


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Verify tracked files for secret leaks and check/tighten local credential permissions."
    )
    parser.add_argument(
        "--repo-root",
        default=None,
        help="Repository root for tracked-file secret scan (defaults to repo root).",
    )
    parser.add_argument(
        "--workspace-dir",
        default=None,
        help="Workspace directory for local credential permission checks.",
    )
    parser.add_argument(
        "--check-local-perms",
        action="store_true",
        help="Verify existing local credential/signing files have 0600 permissions.",
    )
    parser.add_argument(
        "--fix-local-perms",
        action="store_true",
        help="Tighten existing local credential/signing files to 0600 permissions.",
    )
    args = parser.parse_args(argv)

    repo_root = Path(args.repo_root) if args.repo_root else _default_repo_root()
    workspace_dir = Path(args.workspace_dir) if args.workspace_dir else repo_root

    if args.fix_local_perms:
        changed = fix_local_permissions(workspace_dir)
        for item in changed:
            print(f"FIXED {item['path']}: {item['before']} -> {item['after']}")
        remaining = check_local_permissions(workspace_dir)
        if remaining:
            for item in remaining:
                print(f"FAIL {item['path']}: mode {item['mode']} != 0o600", file=sys.stderr)
            return 1
        print(f"PASS: All local credential files in {workspace_dir} have 0600 permissions ({len(changed)} tightened).")
        return 0

    if args.check_local_perms:
        violations = check_local_permissions(workspace_dir)
        if violations:
            for item in violations:
                print(
                    f"FAIL {item['path']}: mode {item['mode']} (expected 0o600). Run with --fix-local-perms.",
                    file=sys.stderr,
                )
            return 1
        checked = discover_local_credential_files(workspace_dir)
        print(f"PASS: Local credential permissions verified 0600 across {len(checked)} file(s) in {workspace_dir}.")
        return 0

    findings = scan_repository(repo_root)
    if findings:
        for finding in findings:
            print(
                f"FAIL {finding['path']}:{finding['line']} [{finding['rule']}] ({finding['preview']})",
                file=sys.stderr,
            )
        print(f"FAIL: {len(findings)} potential secret leak(s) detected in tracked files.", file=sys.stderr)
        return 1

    tracked_count = len(list_tracked_files(repo_root))
    print(f"PASS: Secret hygiene scan clean across {tracked_count} tracked file(s).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
