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
from jira_util import get_config, jira_request, add_comment, transition_issue, assign_issue

# Anti-Spoofing & Role-Locking: Hardwired strictly to role "agent2"
AUDITOR_ROLE = "agent2"


# Supported Gate Definitions
GATE_DEFINITIONS = {
    "Gate 1": {
        "prefix": "[Analysis]",
        "name": "Gate 1: Problem Domain & Analysis Review",
        "checklist": """1. Scrutinize Problem Domain & Analysis:
   - For bug tickets: Stress-test RCA conclusions (symptoms vs. cause, call-site audit, reproducible traces).
   - For features/improvements: Verify deep domain comprehension, user motivation, scope boundaries, and architectural side effects.
   - User Scope Grounding (ATT-1250): Ground review expectations strictly in the user's specific problem statement and parent ticket description. Do NOT challenge deliverables or mandate out-of-scope refactorings, speculative edge cases, or theoretical redesigns not requested by the user.
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
   - User Scope Grounding (ATT-1250): Verify requirements strictly focus on the scoped feature or bugfix. Do NOT require expanding requirements to unrelated components or unrequested workflows.
2. Test Case Traceability:
   - Verify concrete test procedure and expected result in docs/tests.md.
   - Ensure complete bidirectional traceability between requirements and test cases.
3. Requirement Archaeology (Chesterton's Fence):
   - If existing requirements are modified, relaxed, or replaced in docs/requirements.md, verify presence and validity of the 4-field archaeology section:
     * Original Requirement ID & Target
     * Historical Origin & Commit Trace
     * Root Reason for Existing Formulation ("Why was this fence built?")
     * Preservation of Core Invariants ("Why is it safe to modify now?")
   - If existing requirements are altered without this full 4-field section, issue CHALLENGED or RECOMMEND REVISION.
   - Net-new requirements (novel IDs absent from base) are exempt from this hurdle.
4. Recommendation:
   - Issue an explicit recommendation: RECOMMEND PASS or RECOMMEND REVISION with itemized notes."""
    },
    "Gate 3": {
        "prefix": "[Impl-Plan]",
        "name": "Gate 3: Architectural & Invariant Plan Review",
        "checklist": """1. Architectural Integrity & Layering:
   - Verify component boundaries, layering rules (SWE.2), and interface stability. Note: This gate audits the architectural implementation plan document (Stage 3). Under ASPICE / TDD process governance, production code implementation is strictly deferred to Stage 4 after the plan receives human approval. Do not penalize Stage 3 for not yet having code modifications in the git diff.
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


def call_gemini_api(prompt, system_instruction=None, explicit_model=None):
    """Invokes Google Gemini REST API using urllib with automated fallback across available models."""
    repo_root = get_repo_root()
    env_gemini = load_env_file(os.path.join(repo_root, ".env.gemini"))
    api_key = env_gemini.get("GEMINI_API_KEY")
    if not api_key:
        raise ValueError("GEMINI_API_KEY not found in .env.gemini")

    default_candidates = [
        "gemini-3.5-flash-lite",
        "gemini-3.1-flash-lite",
        "gemini-flash-lite-latest",
        "gemini-3.8-flash",
        "gemini-3.7-flash",
        "gemini-3.6-flash",
        "gemini-3.5-flash",
        "gemini-3-flash-preview",
        "gemini-flash-latest"
    ]
    if explicit_model:
        candidate_models = [explicit_model] + [m for m in default_candidates if m != explicit_model]
    else:
        candidate_models = default_candidates
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

        timeout_val = int(os.environ.get("GEMINI_TIMEOUT", 90))
        try:
            with urllib.request.urlopen(req, timeout=timeout_val) as response:
                res_body = response.read().decode("utf-8")
                data = json.loads(res_body)
                candidates = data.get("candidates", [])
                if not candidates:
                    continue
                parts = candidates[0].get("content", {}).get("parts", [])
                if not parts:
                    continue
                return parts[0].get("text", "").strip(), f"Gemini ({model})"
        except urllib.error.HTTPError as e:
            last_error = e
            if e.code in [429, 503]:
                import time
                time.sleep(5)
            continue
        except Exception as e:
            last_error = e
            continue

    raise RuntimeError(f"All Gemini models failed. Last error: {last_error}")


def call_claude_api(prompt, system_instruction=None, explicit_model=None):
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

    model = explicit_model or "claude-3-5-haiku-20241022"
    payload = {
        "model": model,
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

    timeout_val = int(os.environ.get("CLAUDE_TIMEOUT", 90))
    try:
        with urllib.request.urlopen(req, timeout=timeout_val) as response:
            res_body = response.read().decode("utf-8")
            data = json.loads(res_body)
            content_list = data.get("content", [])
            if not content_list:
                raise RuntimeError(f"Claude returned no content: {res_body}")
            return content_list[0].get("text", "").strip(), f"Claude ({model})"
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode("utf-8") if hasattr(e, "read") else str(e)
        raise RuntimeError(f"Claude API HTTP Error {e.code}: {err_msg}")


def query_llm(prompt, system_instruction=None, preferred_provider="gemini", explicit_model=None):
    """Queries preferred LLM provider (default: gemini) with automated fallback to the other provider (claude)."""
    providers = ["gemini", "claude"] if preferred_provider == "gemini" else ["claude", "gemini"]
    errors = []

    for provider in providers:
        try:
            if provider == "gemini":
                text, model_name = call_gemini_api(prompt, system_instruction, explicit_model=explicit_model if preferred_provider == "gemini" else None)
                return text, model_name
            elif provider == "claude":
                text, model_name = call_claude_api(prompt, system_instruction, explicit_model=explicit_model if preferred_provider == "claude" else None)
                return text, model_name
        except Exception as e:
            errors.append(f"{provider}: {e}")

    raise RuntimeError(f"All LLM providers failed:\n" + "\n".join(errors))


def detect_gate(summary):
    """Detects ASPICE gate from sub-task summary."""
    for gate_key, gate_info in GATE_DEFINITIONS.items():
        if summary.startswith(gate_info["prefix"]):
            return gate_key, gate_info
    lower_summary = summary.lower()
    if "[impl]" in lower_summary or "implementation" in lower_summary or lower_summary.startswith("impl"):
        return "Gate 4", GATE_DEFINITIONS["Gate 4"]
    elif "analysis" in lower_summary:
        return "Gate 1", GATE_DEFINITIONS["Gate 1"]
    elif "test-spec" in lower_summary or "test spec" in lower_summary:
        return "Gate 2", GATE_DEFINITIONS["Gate 2"]
    elif "impl-plan" in lower_summary or "plan" in lower_summary:
        return "Gate 3", GATE_DEFINITIONS["Gate 3"]
    elif "test" in lower_summary:
        return "Gate 5", GATE_DEFINITIONS["Gate 5"]
    return None, None


def get_git_diff():
    """Returns complete ticket diff against develop merge-base, including uncommitted changes."""
    try:
        base = subprocess.check_output(["git", "merge-base", "develop", "HEAD"], stderr=subprocess.DEVNULL).decode("utf-8").strip()
        diff = subprocess.check_output(["git", "diff", base], stderr=subprocess.DEVNULL).decode("utf-8")
        if diff.strip():
            return diff[:100000]
    except Exception:
        pass
    try:
        diff = subprocess.check_output(["git", "diff", "HEAD"], stderr=subprocess.DEVNULL).decode("utf-8")
        if not diff.strip():
            diff = subprocess.check_output(["git", "diff", "HEAD~1", "HEAD"], stderr=subprocess.DEVNULL).decode("utf-8")
        return diff[:100000]
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
    if gate_key in ["Gate 2", "Gate 3", "Gate 4", "Gate 5"]:
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
        "*Auditor Model*: {AUDITOR_MODEL_PLACEHOLDER}\n"
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


def audit_subtask(subtask_key, preferred_provider="gemini", explicit_model=None, dry_run=False, review_file=None):
    """Conducts autonomous independent review on a Jira sub-task strictly as Agent 2."""
    print(f"Fetching Jira details for sub-task {subtask_key} as {AUDITOR_ROLE}...")
    config = get_config()
    subtask_url = f"{config['JIRA_URL']}/rest/api/2/issue/{subtask_key}?fields=summary,description,status,parent,fixVersions"
    subtask = jira_request(subtask_url, role=AUDITOR_ROLE)

    status_name = subtask.get("fields", {}).get("status", {}).get("name", "Unknown")
    summary = subtask.get("fields", {}).get("summary", "")
    print(f"Sub-task: {subtask_key} - '{summary}' [{status_name}]")

    gate_key, gate_info = detect_gate(summary)
    if not gate_key:
        print(f"Error: Unable to identify ASPICE stage/gate from summary: '{summary}'", file=sys.stderr)
        sys.exit(1)

    print(f"Detected Gate: {gate_key} ({gate_info['name']})")

    if not dry_run:
        # Reassign subtask to Auditor (Agent 2) upon audit initiation
        try:
            assign_issue(subtask_key, AUDITOR_ROLE, role=AUDITOR_ROLE)
        except Exception as e:
            print(f"Note: Could not assign {subtask_key} to {AUDITOR_ROLE}: {e}", file=sys.stderr)

    # Fetch parent issue details
    parent_issue = None
    parent_ref = subtask.get("fields", {}).get("parent")
    if parent_ref:
        parent_key = parent_ref.get("key")
        parent_url = f"{config['JIRA_URL']}/rest/api/2/issue/{parent_key}?fields=summary,description,status,fixVersions"
        parent_issue = jira_request(parent_url, role=AUDITOR_ROLE)

    if review_file:
        print(f"Loading local review deliverable from '{review_file}'...")
        with open(review_file, "r", encoding="utf-8") as f:
            review_body = f.read()
        model_name = "Local Independent Agent Instance"
    else:
        # Build prompt and query external LLM
        prompt, system_instruction = build_audit_prompt(gate_key, gate_info, subtask, parent_issue)
        print(f"Invoking independent auditor model (preferred: {preferred_provider}, model: {explicit_model or 'auto'})...")
        review_body, model_name = query_llm(prompt, system_instruction, preferred_provider, explicit_model=explicit_model)
    print(f"Audit successfully generated via {model_name}.")

    if dry_run:
        print("\n--- DRY RUN OUTPUT ---")
        print(review_body)
        print("--- END DRY RUN ---")
        return

    # Post comment to Jira strictly as agent2
    print(f"Posting audit report comment to {subtask_key} as {AUDITOR_ROLE}...")
    # Inject or replace model name in header placeholder
    if "{AUDITOR_MODEL_PLACEHOLDER}" in review_body:
        review_body = review_body.replace("{AUDITOR_MODEL_PLACEHOLDER}", f"*{model_name}*")
    elif re.search(r'\*Auditor Model\*:\s*[^\n]+', review_body):
        review_body = re.sub(r'\*Auditor Model\*:\s*[^\n]+', f"*Auditor Model*: *{model_name}*", review_body, count=1)
    else:
        review_body = f"*Auditor Model*: *{model_name}*\n\n{review_body}"

    identity_comment = f"{review_body}\n\n_(Review conducted by Independent External Auditor: {model_name})_"
    add_comment(subtask_key, identity_comment, role=AUDITOR_ROLE)

    # Determine audit decision from review body
    is_revision_needed = False
    decision_match = re.search(r'\*Audit Decision\*:\s*([^\n]+)', review_body, re.IGNORECASE)
    if decision_match:
        decision_text = decision_match.group(1).upper()
        if any(term in decision_text for term in ["REVISION", "CHALLENGED", "FAIL", "REJECT"]):
            is_revision_needed = True
    else:
        if re.search(r'RECOMMEND(ED)?\s+REVISION|CHALLENGED', review_body, re.IGNORECASE):
            is_revision_needed = True

    # Transition based on current status and audit decision
    if status_name.lower() in ["in überprüfung", "in review", "review"]:
        if is_revision_needed:
            print(f"Audit decision requires revision. Transitioning {subtask_key} back to 'In Bearbeitung' as {AUDITOR_ROLE}...")
            transition_issue(subtask_key, "in_progress", role=AUDITOR_ROLE)
            assign_issue(subtask_key, "agent1", role=AUDITOR_ROLE)
        else:
            print(f"Audit decision passed. Transitioning {subtask_key} to 'Freigabe (Human)' as {AUDITOR_ROLE}...")
            transition_issue(subtask_key, "freigabe", role=AUDITOR_ROLE)
            assign_issue(subtask_key, "human", role=AUDITOR_ROLE)
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
        print("Usage: review_agent.py [audit SUBTASK_KEY [--provider gemini|claude] [--model MODEL_NAME] [--dry-run] | test-connection]", file=sys.stderr)
        sys.exit(1)

    cmd = sys.argv[1]
    if cmd == "test-connection":
        test_connections()
    elif cmd == "audit":
        if len(sys.argv) < 3:
            print("Error: Sub-task key required. Usage: review_agent.py audit SUBTASK_KEY [--provider gemini|claude] [--model MODEL_NAME] [--review-file FILE]", file=sys.stderr)
            sys.exit(1)
        subtask_key = sys.argv[2]
        provider = "gemini"
        explicit_model = None
        dry_run = False
        review_file = None

        args = sys.argv[3:]
        i = 0
        while i < len(args):
            if args[i] == "--provider" and i + 1 < len(args):
                provider = args[i + 1].lower()
                i += 2
            elif args[i] == "--model" and i + 1 < len(args):
                explicit_model = args[i + 1]
                i += 2
            elif args[i] in ["--review-file", "--local", "--local-file"] and i + 1 < len(args):
                review_file = args[i + 1]
                i += 2
            elif args[i] == "--dry-run":
                dry_run = True
                i += 1
            else:
                i += 1

        audit_subtask(subtask_key, preferred_provider=provider, explicit_model=explicit_model, dry_run=dry_run, review_file=review_file)
    else:
        print(f"Unknown command: {cmd}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
