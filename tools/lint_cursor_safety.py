#!/usr/bin/env python3
"""
tools/lint_cursor_safety.py

Static architecture guard enforcing REQ-DAT-001 / TST-DAT-001:
Ensures all SQLite queries across legacy Java database managers use try-with-resources,
prohibits chained calls (.query(...).moveToFirst()), enforces @CursorProvider contracts,
and verifies that any @SuppressCursorAudit annotations have not expired.
"""

import os
import re
import sys
from datetime import date, datetime

TARGET_DIRECTORIES = [
    "app/src/main/java/com/atrainingtracker/banalservice/database",
    "app/src/main/java/com/atrainingtracker/trainingtracker/database",
    "app/src/main/java/com/atrainingtracker/trainingtracker/exporter",
    "app/src/main/java/com/atrainingtracker/trainingtracker/segments",
    "app/src/main/java/com/atrainingtracker/trainingtracker/smartwatch/pebble",
]

# Patterns for query detection
QUERY_PATTERN = re.compile(r'(?:db|database|getDatabase\(\)|getReadableDatabase\(\)|getWritableDatabase\(\))\s*\.\s*(?:rawQuery|query)\s*\(', re.MULTILINE)
CHAINED_CALL_PATTERN = re.compile(r'\.(?:rawQuery|query)\s*\([^;{]+?\)\s*\.', re.DOTALL)
SUPPRESS_ANNOTATION_PATTERN = re.compile(r'@SuppressCursorAudit\s*\(\s*ticketId\s*=\s*"([^"]+)"\s*,\s*reason\s*=\s*"([^"]+)"\s*,\s*expiryDate\s*=\s*"([^"]+)"\s*\)')

def parse_methods(content):
    """
    Extracts methods with their annotations and bodies from Java class content.
    Returns a list of dicts: {'name': str, 'annotations': str, 'body': str, 'start_line': int}
    """
    # Simple AST-like method chunking for Java
    # Matches annotations followed by method signature and { ... }
    lines = content.splitlines(keepends=True)
    methods = []
    
    # Track brace depth
    brace_depth = 0
    in_method = False
    current_method_lines = []
    current_start_line = 1
    current_annotations = []
    
    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        
        # Check annotations
        if not in_method and brace_depth == 1:
            if stripped.startswith("@"):
                current_annotations.append(stripped)
            elif re.search(r'\b(public|protected|private|static|final|synchronized)\b.*\(.*\).*(?:throws\s+[^{]+)?\s*\{', stripped) or (stripped.endswith("{") and "(" in stripped and not stripped.startswith("class ") and not stripped.startswith("interface ")):
                in_method = True
                current_start_line = i + 1
                current_method_lines = [line]
                brace_depth += line.count('{') - line.count('}')
                i += 1
                continue

        if in_method:
            current_method_lines.append(line)
            brace_depth += line.count('{') - line.count('}')
            if brace_depth <= 1:
                # Method completed
                methods.append({
                    'annotations': "\n".join(current_annotations),
                    'body': "".join(current_method_lines),
                    'start_line': current_start_line
                })
                in_method = False
                current_method_lines = []
                current_annotations = []
        else:
            brace_depth += line.count('{') - line.count('}')
            if brace_depth <= 1 and not stripped.startswith("@"):
                current_annotations = []
                
        i += 1
        
    return methods

def audit_file_content(content, filename="<string>", check_date=None):
    """
    Audits a single file's content string. Returns a list of violation strings.
    """
    if check_date is None:
        check_date = date.today()
        
    violations = []
    
    # 1. Check for expired @SuppressCursorAudit across the file
    for match in SUPPRESS_ANNOTATION_PATTERN.finditer(content):
        ticket_id, reason, expiry_str = match.groups()
        if not re.match(r'^ATT-\d+$', ticket_id):
            violations.append(f"{filename}: Invalid ticketId '{ticket_id}' in @SuppressCursorAudit (must match ATT-\\d+)")
        try:
            exp_date = datetime.strptime(expiry_str, "%Y-%m-%d").date()
            if exp_date < check_date:
                violations.append(f"{filename}: Expired @SuppressCursorAudit (ticket: {ticket_id}, expired on {expiry_str}, reason: '{reason}')")
        except ValueError:
            violations.append(f"{filename}: Invalid expiryDate format '{expiry_str}' in @SuppressCursorAudit (must be YYYY-MM-DD)")

    # 2. Check for chained calls like db.query(...).moveToFirst()
    # Strip comments first
    clean_content = re.sub(r'//.*', '', content)
    clean_content = re.sub(r'/\*.*?\*/', '', clean_content, flags=re.DOTALL)
    
    for match in re.finditer(r'\.(?:rawQuery|query)\s*\(', clean_content):
        start = match.end() - 1
        depth = 0
        end = -1
        for idx in range(start, len(clean_content)):
            if clean_content[idx] == '(':
                depth += 1
            elif clean_content[idx] == ')':
                depth -= 1
                if depth == 0:
                    end = idx
                    break
        if end != -1 and end + 1 < len(clean_content):
            remainder = clean_content[end+1:].lstrip()
            if remainder.startswith('.') and not remainder.startswith('..'):
                matched_text = clean_content[match.start():end+1] + "."
                violations.append(f"{filename}: Prohibited chained cursor query call detected: '{matched_text}' creates unclosed anonymous cursor")

    # 3. Method-level audit
    methods = parse_methods(clean_content)
    for m in methods:
        annotations = m['annotations']
        body = m['body']
        start_line = m['start_line']
        
        # Check if method is @CursorProvider
        if "@CursorProvider" in annotations:
            # Authorized cursor provider method: ownership transferred to caller
            continue
            
        # Check if method has valid unexpired @SuppressCursorAudit
        suppress_match = SUPPRESS_ANNOTATION_PATTERN.search(annotations)
        if suppress_match:
            ticket_id, reason, expiry_str = suppress_match.groups()
            try:
                exp_date = datetime.strptime(expiry_str, "%Y-%m-%d").date()
                if exp_date >= check_date:
                    # Valid active suppression
                    continue
            except ValueError:
                pass
        
        # Search for queries in body
        # For each query, it MUST be inside a try (...) statement:
        # e.g., 'try (' preceding the query within the same statement
        lines = body.splitlines()
        for idx, line in enumerate(lines):
            if QUERY_PATTERN.search(line) or ('.query(' in line and ('db' in line or 'database' in line)):
                # Check if this line or the immediate preceding lines form a `try (` block
                # Look backwards up to 3 lines for 'try ('
                enclosed_in_try = False
                for back in range(max(0, idx - 4), idx + 1):
                    sub = "\n".join(lines[back:idx+1])
                    if re.search(r'try\s*\([^\)]*?(?:db|database|getDatabase\(\)|getReadableDatabase\(\)|getWritableDatabase\(\))\s*\.\s*(?:rawQuery|query)', sub, re.DOTALL):
                        enclosed_in_try = True
                        break
                    if 'try (' in lines[back] or 'try(' in lines[back]:
                        # Check if try is closed before this line
                        try_stmt = "\n".join(lines[back:idx+1])
                        if try_stmt.count('(') > try_stmt.count(')'):
                            enclosed_in_try = True
                            break
                        elif '{' in lines[idx] and 'try' in lines[back]:
                            enclosed_in_try = True
                            break
                            
                if not enclosed_in_try:
                    violations.append(f"{filename}:{start_line + idx}: Unshielded SQLite query not enclosed in try-with-resources: '{line.strip()}'")

    return violations

def run_audit(root_dir="."):
    all_violations = []
    for rel_dir in TARGET_DIRECTORIES:
        full_dir = os.path.join(root_dir, rel_dir)
        if not os.path.exists(full_dir):
            continue
        for root, _, files in os.walk(full_dir):
            for file in files:
                if file.endswith(".java") and not file.startswith("DatabaseConstants") and not file.startswith("CursorUtils") and not file.startswith("CursorProvider") and not file.startswith("SuppressCursorAudit"):
                    file_path = os.path.join(root, file)
                    with open(file_path, "r", encoding="utf-8", errors="replace") as f:
                        content = f.read()
                    violations = audit_file_content(content, filename=os.path.relpath(file_path, root_dir))
                    all_violations.extend(violations)
    return all_violations

if __name__ == "__main__":
    project_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    violations = run_audit(project_root)
    if violations:
        print(f"FAILED: Found {len(violations)} cursor safety violations violating REQ-DAT-001:")
        for v in violations:
            print(f"  - {v}")
        sys.exit(1)
    else:
        print("SUCCESS: 100% of legacy database queries are protected by try-with-resources or authorized @CursorProvider.")
        sys.exit(0)
