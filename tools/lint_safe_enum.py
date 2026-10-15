#!/usr/bin/env python3
"""
Static Architecture Guard: lint_safe_enum.py
Enforces REQ-ARC-001 (Safe Enum Deserialization).

Scans app/src/main for unguarded Enum.valueOf invocations on external data sources
(Cursor, Intent, SharedPreferences, Bundle arguments, JSON).
"""

import os
import re
import sys

UNSAFE_PATTERNS = [
    (re.compile(r'\b[A-Z][a-zA-Z0-9_]*\.valueOf\s*\(\s*cursor\.getString', re.MULTILINE),
     "Direct Enum.valueOf on cursor.getString without safe deserialization"),
    (re.compile(r'\b[A-Z][a-zA-Z0-9_]*\.valueOf\s*\(\s*(?:intent|getIntent\(\))\.(?:getStringExtra|getSerializableExtra)', re.MULTILINE),
     "Direct Enum.valueOf on intent extra without safe deserialization"),
    (re.compile(r'\b[A-Z][a-zA-Z0-9_]*\.valueOf\s*\(\s*(?:arguments|getArguments\(\))\.getString', re.MULTILINE),
     "Direct Enum.valueOf on fragment arguments without safe deserialization"),
    (re.compile(r'\b[A-Z][a-zA-Z0-9_]*\.valueOf\s*\(\s*(?:cSharedPreferences|sharedPreferences|prefs)\.getString', re.MULTILINE),
     "Direct Enum.valueOf on SharedPreferences.getString without safe deserialization"),
    (re.compile(r'\b[A-Z][a-zA-Z0-9_]*\.valueOf\s*\(\s*(?:json|obj|jsonObject)\.getString', re.MULTILINE),
     "Direct Enum.valueOf on JSONObject.getString without safe deserialization"),
]

def scan_file(filepath):
    violations = []
    with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
        content = f.read()

    # Skip EnumUtils itself where Enum.valueOf is safely wrapped inside try/catch
    if filepath.endswith("EnumUtils.kt") or filepath.endswith("EnumUtils.java"):
        return violations

    for pattern, message in UNSAFE_PATTERNS:
        for match in pattern.finditer(content):
            line_no = content[:match.start()].count('\n') + 1
            snippet = match.group(0).replace('\n', ' ')
            violations.append((filepath, line_no, message, snippet))
    return violations

def main():
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "app", "src", "main"))
    if not os.path.exists(root_dir):
        print(f"Error: Directory not found: {root_dir}")
        sys.exit(1)

    all_violations = []
    for dirpath, _, filenames in os.walk(root_dir):
        for fname in filenames:
            if fname.endswith(('.java', '.kt')):
                fpath = os.path.join(dirpath, fname)
                violations = scan_file(fpath)
                all_violations.extend(violations)

    if all_violations:
        print(f"FAILED: Found {len(all_violations)} safe enum deserialization violation(s):")
        for fpath, lno, msg, snippet in all_violations:
            rel_path = os.path.relpath(fpath, os.path.dirname(__file__) + "/..")
            print(f"  {rel_path}:{lno}: {msg} -> `{snippet}`")
        sys.exit(1)
    else:
        print("PASS: Safe enum deserialization audit passed. Zero unguarded call sites found.")
        sys.exit(0)

if __name__ == "__main__":
    main()
