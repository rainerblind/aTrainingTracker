#!/usr/bin/env python3
"""
Autonomous Multi-Model Independent Review Agent (Agent 2) for aTrainingTracker.

Performs independent, out-of-process ASPICE quality gate reviews (Gates 1 through 5)
using decoupled external LLM providers (Google Gemini REST API and Anthropic Claude API)
with zero external pip dependencies (Python 3 standard library only).
"""

import os
import sys
import json
import re
import subprocess
import urllib.request
import urllib.error

# Import Jira helpers from existing tools module
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from jira_util import get_config, jira_request, add_comment, transition_issue


# Supported Gate Definitions
GATE_DEFINITIONS = {
    "Gate 1": {
        "prefix": "[Analysis]",
        "name": "Gate 1: Problem Domain & Analysis Review",
        "checklist": """1. Scrutinize Problem Domain & Analysis:
   - For bug tickets: Stress-test RCA conclusions (symptoms vs. cause, call-site audit, reproducible traces).
   - For features/improvements: Verify deep domain comprehension, user motivation, scope boundaries, and architectural side effects.
2. Call Site Audit:
   - Verify that all affected callers, classes, and resources are identified.
3. Requirement Mapping Audit:
   - Check mapped Requirement IDs against docs/requirements.md.
4. System Invariants Verification:
   - Confirm explicit system invariants (what existing behavior, data precision, schema, or API contracts MUST NOT change).
5. Risk Rating & Recommendation:
   - Assign risk rating (LOW, MEDIUM, HIGH) with technical justification.
   - Issue an explicit recommendation: RECOMMEND PASS, CHALLENGED, or RECOMMEND REVISION."""
    },
    "Gate 2": {
        "prefix": "[Test-Spec]",
        "name": "Gate 2: Test Specification & Requirements Review",
        "checklist": """1. Requirement Phrasing Standards:
   - Verify standard syntax: SHALL for functional behavior, MUST for technical constraints.
   - Verify atomic, unambiguous, system-centric, and state-oriented descriptions (not implementation steps).
   - Verify explicit Given-When-Then acceptance criteria and System Invariants.
2. Test Case Traceability:
   - Verify concrete test procedure and expected result in docs/tests.md.
   - Ensure complete bidirectional traceability between requirements and test cases.
3. Recommendation:
   - Issue an explicit recommendation: RECOMMEND PASS or RECOMMEND REVISION with itemized notes."""
    },
    "Gate 3": {
        "prefix": "[Impl-Plan]",
        "name": "Gate 3: Architectural & Invariant Plan Review",
        "checklist": """1. Architectural Integrity & Layering:
   - Verify component boundaries, layering rules (SWE.2), and interface stability.
2. Invariant Verification:
   - Confirm all system invariants, non-target metrics, and file schemas are explicitly protected.
3. Verification Coverage:
   - Ensure every proposed change maps to an automated or manual test case in docs/tests.md.
4. Recommendation:
   - Issue an explicit recommendation: RECOMMEND PASS or RECOMMEND REVISION."""
    },
    "Gate 4": {
        "prefix": "[Implementation]",
        "name": "Gate 4: Code Quality, Localization & Side-Effect Review",
        "checklist": """1. Diff Scrutiny:
   - Inspect git diff against the approved implementation plan. Confirm zero unapproved files or unintended modifications.
2. Side-Effect Audit:
   - Verify adjacent callers, interfaces, and non-target metrics/features were NOT altered or broken.
3. Quality & Compliance:
   - Verify class/method headers comply with self-documenting standards (KDoc/JavaDoc).
   - Verify 9-language localization parity across all user-facing strings (EN, DE, ES, FR, IT, JA, NL, PL, PT).
4. Recommendation:
   - Issue an explicit recommendation: RECOMMEND PASS or RECOMMEND REVISION with itemized audit notes."""
    },
    "Gate 5": {
        "prefix": "[Test]",
        "name": "Gate 5: Clean-Room Full-Suite Regression & Release Review",
        "checklist": """1. Full-Suite Regression Execution:
   - Verify ./gradlew testDebugUnitTest executed with 100% success (0 failures, 0 regressions) across all project modules.
2. Living Documentation Parity:
   - Confirm docs/requirements.md and docs/tests.md status fields are updated to Verified.
3. Mandatory Lösungsversion (Fix Version/s) Audit:
   - Confirm parent ticket has a valid active unreleased Lösungsversion assigned. If missing, flag as CHALLENGED / REVISE.
4. Recommendation:
   - Issue an explicit recommendation: RECOMMEND PASS or RECOMMEND REVISION."""
    }
}


def load_env_file(filepath):
    """Parses a simple KEY=VALUE file."""
    config = {}
    if not os.path.exists(filepath):
        return config
    with open(filepath, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                config[k.strip()] = v.strip()
    return config


def get_repo_root():
    """Returns absolute path to the git repository root."""
    try:
        root = subprocess.check_output(["git", "rev-parse", "--show-toplevel"], stderr=subprocess.DEVNULL)
        return root.decode("utf-8").strip()
    except Exception:
        return os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))


def call_gemini_api(prompt, system_instruction=None):
    """Invokes Google Gemini REST API using urllib with automated fallback across available models."""
    repo_root = get_repo_root()
    env_gemini = load_env_file(os.path.join(repo_root, ".env.gemini"))
    api_key = env_gemini.get("GEMINI_API_KEY")
    if not api_key:
        raise ValueError("GEMINI_API_KEY not found in .env.gemini")

    candidate_models = ["gemini-3-flash-preview", "gemini-3.6-flash", "gemini-3.7-flash"]
    last_error = None

    for model in candidate_models:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}"
        payload = {
            "contents": [
                {
                    "parts": [{"text": prompt}]
                }
            ],
            "generationConfig": {
                "temperature": 0.2,
                "maxOutputTokens": 4096
            }
        }
        if system_instruction:
            payload["systemInstruction"] = {
                "parts": [{"text": system_instruction}]
            }

        req = urllib.request.Request(
            url,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST"
        )

        try:
            with urllib.request.urlopen(req, timeout=30) as response:
                res_body = response.read().decode("utf-8")
                data = json.loads(res_body)
                candidates = data.get("candidates", [])
                if not candidates:
                    continue
                parts = candidates[0].get("content", {}).get("parts", [])
                if not parts:
                    continue
                return parts[0].get("text", "").strip(), f"Gemini ({model})"
        except Exception as e:
            last_error = e
            continue

    raise RuntimeError(f"All Gemini models failed. Last error: {last_error}")


def call_claude_api(prompt, system_instruction=None):
    """Invokes Anthropic Claude REST API using urllib."""
    repo_root = get_repo_root()
    env_claude = load_env_file(os.path.join(repo_root, ".env.claude"))
    api_key = env_claude.get("ANTHROPIC_API_KEY")
    if not api_key:
        raise ValueError("ANTHROPIC_API_KEY not found in .env.claude")

    url = "https://api.anthropic.com/v1/messages"
    headers = {
        "x-api-key": api_key,
        "anthropic-version": "2023-06-01",
        "content-type": "application/json"
    }

    payload = {
        "model": "claude-3-5-haiku-20241022",
        "max_tokens": 4096,
        "temperature": 0.2,
        "messages": [
            {"role": "user", "content": prompt}
        ]
    }
    if system_instruction:
        payload["system"] = system_instruction

    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers=headers,
        method="POST"
    )

    try:
        with urllib.request.urlopen(req, timeout=60) as response:
            res_body = response.read().decode("utf-8")
            data = json.loads(res_body)
            content_list = data.get("content", [])
            if not content_list:
                raise RuntimeError(f"Claude returned no content: {res_body}")
            return content_list[0].get("text", "").strip()
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode("utf-8") if hasattr(e, "read") else str(e)
        raise RuntimeError(f"Claude API HTTP Error {e.code}: {err_msg}")


def query_llm(prompt, system_instruction=None, preferred_provider="gemini"):
    """Queries preferred LLM provider with automated fallback to the other provider."""
    providers = ["gemini", "claude"] if preferred_provider == "gemini" else ["claude", "gemini"]
    errors = []

    for provider in providers:
        try:
            if provider == "gemini":
                text, model_name = call_gemini_api(prompt, system_instruction)
                return text, model_name
            elif provider == "claude":
                text = call_claude_api(prompt, system_instruction)
                return text, "Claude (claude-3-5-haiku)"
        except Exception as e:
            errors.append(f"{provider}: {e}")

    raise RuntimeError(f"All LLM providers failed:\n" + "\n".join(errors))


def detect_gate(summary):
    """Detects ASPICE gate from sub-task summary."""
    for gate_key, gate_info in GATE_DEFINITIONS.items():
        if summary.startswith(gate_info["prefix"]):
            return gate_key, gate_info
    # Fallback to keyword matching
    lower_summary = summary.lower()
    if "analysis" in lower_summary:
        return "Gate 1", GATE_DEFINITIONS["Gate 1"]
    elif "test-spec" in lower_summary or "test spec" in lower_summary:
        return "Gate 2", GATE_DEFINITIONS["Gate 2"]
    elif "impl-plan" in lower_summary or "plan" in lower_summary:
        return "Gate 3", GATE_DEFINITIONS["Gate 3"]
    elif "implementation" in lower_summary:
        return "Gate 4", GATE_DEFINITIONS["Gate 4"]
    elif "test" in lower_summary:
        return "Gate 5", GATE_DEFINITIONS["Gate 5"]

    return None, None


def get_git_diff():
    """Gathers recent git diff or working tree changes."""
    try:
        # Check uncommitted changes first
        diff = subprocess.check_output(["git", "diff", "HEAD"], stderr=subprocess.DEVNULL).decode("utf-8")
        if not diff.strip():
            # Check last commit diff
            diff = subprocess.check_output(["git", "diff", "HEAD~1", "HEAD"], stderr=subprocess.DEVNULL).decode("utf-8")
        return diff[:20000] # Cap to reasonable token length
    except Exception:
        return ""


def get_recent_commits():
    """Returns last 3 commits on current branch."""
    try:
        out = subprocess.check_output(["git", "log", "-n", "3", "--oneline"], stderr=subprocess.DEVNULL)
        return out.decode("utf-8").strip()
    except Exception:
        return ""


def build_audit_prompt(gate_key, gate_info, subtask, parent_issue):
    """Builds prompt containing gate checklist, deliverable, and context."""
    subtask_key = subtask.get("key")
    subtask_summary = subtask.get("fields", {}).get("summary", "")
    subtask_desc = subtask.get("fields", {}).get("description") or "None"
    parent_key = parent_issue.get("key") if parent_issue else "None"
    parent_summary = parent_issue.get("fields", {}).get("summary", "") if parent_issue else "None"
    parent_desc = parent_issue.get("fields", {}).get("description") or "None"
    parent_fix_version = ", ".join([v.get("name", "") for v in parent_issue.get("fields", {}).get("fixVersions", [])]) if parent_issue else "None"

    diff_context = ""
    if gate_key in ["Gate 3", "Gate 4", "Gate 5"]:
        diff = get_git_diff()
        commits = get_recent_commits()
        diff_context = f"\n\n--- Git Context ---\nRecent Commits:\n{commits}\n\nGit Diff:\n{diff}\n"

    system_instruction = (
        "You are Agent 2, the Senior Independent ASPICE Quality & Safety Auditor for the aTrainingTracker project. "
        "Your duty is to conduct a rigorous, objective, adversarial quality audit on the provided deliverable. "
        "Do NOT act as an agreeable assistant; actively look for discrepancies, architectural shortcuts, missing invariants, "
        "unsupported assumptions, lack of test coverage, formatting defects, and regressions. "
        "Output your review strictly formatted in Jira Wiki markup (e.g. h2., h3., *bold*, {code}). "
        "Always structure your output as follows:\n"
        f"h2. {gate_info['name']} (Automated Independent Auditor)\n\n"
        "*Audit Decision*: *RECOMMEND PASS* or *CHALLENGED* or *RECOMMEND REVISION*\n"
        "*Risk Level*: *LOW* or *MEDIUM* or *HIGH* (with concise technical justification)\n\n"
        "h3. 1. Scrutiny & Compliance Analysis\n"
        "<Itemized evaluation against the gate checklist>\n\n"
        "h3. 2. Preserved Invariants & Boundary Verification\n"
        "<Verification of system invariants and caller stability>\n\n"
        "h3. 3. Auditor Recommendation & Notes\n"
        "<Concrete final conclusion and actionable recommendations>\n"
    )

    prompt = (
        f"Audit Request for Sub-task: {subtask_key} ({subtask_summary})\n"
        f"Parent Ticket: {parent_key} ({parent_summary})\n"
        f"Parent Lösungsversion: {parent_fix_version}\n\n"
        f"--- Mandatory Audit Checklist for {gate_key} ---\n"
        f"{gate_info['checklist']}\n\n"
        f"--- Stage Deliverable (Sub-task Description) ---\n"
        f"{subtask_desc}\n\n"
        f"--- Parent Issue Description ---\n"
        f"{parent_desc}\n"
        f"{diff_context}\n\n"
        f"Perform the audit now according to the instructions and checklist above."
    )

    return prompt, system_instruction


def audit_subtask(subtask_key, preferred_provider="gemini", dry_run=False):
    """Conducts autonomous independent review on a Jira sub-task."""
    print(f"Fetching Jira details for sub-task {subtask_key}...")
    config = get_config()
    subtask_url = f"{config['JIRA_URL']}/rest/api/2/issue/{subtask_key}?fields=summary,description,status,parent,fixVersions"
    subtask = jira_request(subtask_url)

    status_name = subtask.get("fields", {}).get("status", {}).get("name", "Unknown")
    summary = subtask.get("fields", {}).get("summary", "")
    print(f"Sub-task: {subtask_key} - '{summary}' [{status_name}]")

    gate_key, gate_info = detect_gate(summary)
    if not gate_key:
        print(f"Error: Unable to identify ASPICE stage/gate from summary: '{summary}'")
        sys.exit(1)

    print(f"Detected Gate: {gate_key} ({gate_info['name']})")

    # Fetch parent issue details
    parent_issue = None
    parent_ref = subtask.get("fields", {}).get("parent")
    if parent_ref:
        parent_key = parent_ref.get("key")
        parent_url = f"{config['JIRA_URL']}/rest/api/2/issue/{parent_key}?fields=summary,description,status,fixVersions"
        parent_issue = jira_request(parent_url)

    # Build prompt and query external LLM
    prompt, system_instruction = build_audit_prompt(gate_key, gate_info, subtask, parent_issue)
    print(f"Invoking independent auditor model (preferred: {preferred_provider})...")
    review_body, model_name = query_llm(prompt, system_instruction, preferred_provider)
    print(f"Audit successfully generated via {model_name}.")

    if dry_run:
        print("\n--- DRY RUN OUTPUT ---")
        print(review_body)
        print("--- END DRY RUN ---")
        return

    # Post comment to Jira
    print(f"Posting audit report comment to {subtask_key}...")
    identity_comment = f"{review_body}\n\n_(Review conducted by Independent External Auditor: {model_name})_"
    add_comment(subtask_key, identity_comment)

    # Transition to Freigabe (Human) if currently in In Überprüfung
    if status_name.lower() in ["in überprüfung", "in review", "review"]:
        print(f"Transitioning {subtask_key} to 'Freigabe (Human)'...")
        transition_issue(subtask_key, "freigabe")
    else:
        print(f"Note: Current status is '{status_name}'. Subtask was not in 'In Überprüfung'; skipping transition.")


def test_connections():
    """Validates API connections to configured LLM providers."""
    print("Testing Google Gemini API connection...")
    try:
        res, model_name = call_gemini_api("Ping test. Reply with pong.")
        print(f"  [SUCCESS] {model_name}: {res}")
    except Exception as e:
        print(f"  [FAILURE] Gemini: {e}")

    print("Testing Anthropic Claude API connection...")
    try:
        res = call_claude_api("Ping test. Reply with pong.")
        print(f"  [SUCCESS] Claude: {res}")
    except Exception as e:
        print(f"  [FAILURE] Claude: {e}")


def main():
    if len(sys.argv) < 2:
        print("Usage: review_agent.py [audit SUBTASK_KEY [--provider gemini|claude] [--dry-run] | test-connection]")
        sys.exit(1)

    cmd = sys.argv[1]
    if cmd == "test-connection":
        test_connections()
    elif cmd == "audit":
        if len(sys.argv) < 3:
            print("Error: Sub-task key required. Usage: review_agent.py audit SUBTASK_KEY [--provider gemini|claude]")
            sys.exit(1)
        subtask_key = sys.argv[2]
        provider = "gemini"
        dry_run = False

        args = sys.argv[3:]
        i = 0
        while i < len(args):
            if args[i] == "--provider" and i + 1 < len(args):
                provider = args[i + 1].lower()
                i += 2
            elif args[i] == "--dry-run":
                dry_run = True
                i += 1
            else:
                i += 1

        audit_subtask(subtask_key, preferred_provider=provider, dry_run=dry_run)
    else:
        print(f"Unknown command: {cmd}")
        sys.exit(1)


if __name__ == "__main__":
    main()
