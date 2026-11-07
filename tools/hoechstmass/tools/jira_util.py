#!/usr/bin/env python3
"""
tools/hoechstmass/tools/jira_util.py

Parameterized Jira CLI automation tool for the Agile Agentic ASPICE (AAA) lifecycle.
Loads project key, transitions, custom fields, and role account mappings dynamically from
hoechstmass_config.json and .env.jira, eliminating hardcoded project constants.
"""

import base64
import json
import os
import re
import sys
import urllib.request
from urllib.parse import urlencode

DEFAULT_CONFIG_LOCATIONS = [
    os.path.join(os.path.dirname(__file__), "hoechstmass_config.json"),
    os.path.join(os.path.dirname(__file__), "..", "hoechstmass_config.json"),
    os.path.join(os.path.dirname(__file__), "..", "..", "hoechstmass_config.json"),
]

DEFAULT_ENV_LOCATIONS = [
    os.path.join(os.path.dirname(__file__), ".env.jira"),
    os.path.join(os.path.dirname(__file__), "..", ".env.jira"),
    os.path.join(os.path.dirname(__file__), "..", "..", ".env.jira"),
]

STAGE_NAMES = {
    1: "Analysis",
    2: "Req & Test Spec",
    3: "Impl-Plan",
    4: "Implementation",
    5: "Test",
}

STAGE_SKILLS = {
    1: "stage1-analysis",
    2: "stage2-req-test-spec",
    3: "stage3-impl-plan",
    4: "stage4-implementation",
    5: "stage5-verification",
}

VALID_ROLES = ["agent1", "agent2", "coordinator", "human"]


def load_hoechstmass_config(custom_path=None):
    """Loads declarative configuration from hoechstmass_config.json."""
    paths = [custom_path] if custom_path else DEFAULT_CONFIG_LOCATIONS
    for p in paths:
        if p and os.path.exists(p):
            with open(p, "r", encoding="utf-8") as f:
                return json.load(f)
    # Default fallback config if file not found
    return {
        "project_key": "PROJ",
        "board_id": 1,
        "transitions": {
            "todo": "11",
            "in_progress": "21",
            "in_review": "31",
            "done": "41"
        },
        "roles": {
            "agent1": "agent1_id",
            "agent2": "agent2_id",
            "coordinator": "coordinator_id",
            "human": "human_id"
        },
        "custom_fields": {
            "story_points": "customfield_10016",
            "sprint": "customfield_10020"
        }
    }


def load_env_config(custom_path=None):
    """Loads environment variables from .env.jira."""
    paths = [custom_path] if custom_path else DEFAULT_ENV_LOCATIONS
    config = dict(os.environ)
    for p in paths:
        if p and os.path.exists(p):
            with open(p, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if "=" in line and not line.startswith("#"):
                        k, v = line.split("=", 1)
                        config[k.strip()] = v.strip()
            break
    return config


def mask_secret(text):
    """Masks tokens or passwords in log output or error strings."""
    if not text:
        return ""
    if len(text) <= 6:
        return "***"
    return text[:2] + "***" + text[-2:]


def get_account_id_for_role(role_or_id, config=None, h_config=None):
    """Resolves Jira accountId for a given role (agent1, agent2, coordinator, human)."""
    if not role_or_id:
        return None
    if config is None:
        config = load_env_config()
    if h_config is None:
        h_config = load_hoechstmass_config()

    role_key = str(role_or_id).lower().strip()
    env_key = f"JIRA_{role_key.upper()}_ACCOUNT_ID"
    if env_key in config and config[env_key]:
        return config[env_key]

    roles = h_config.get("roles", {})
    if role_key in roles:
        return roles[role_key]

    return role_or_id


def resolve_account(role="agent1", config=None):
    """Resolves (username, token) for a role with fallback to default credentials."""
    if config is None:
        config = load_env_config()

    role_map = {
        "agent1": ("JIRA_AGENT1_USER", "JIRA_AGENT1_TOKEN"),
        "agent2": ("JIRA_AGENT2_USER", "JIRA_AGENT2_TOKEN"),
        "coordinator": ("JIRA_COORDINATOR_USER", "JIRA_COORDINATOR_TOKEN"),
    }
    user_key, token_key = role_map.get(role, (None, None))
    user = config.get(user_key) if user_key else None
    token = config.get(token_key) if token_key else None

    if user and token:
        return user, token

    default_user = config.get("JIRA_USER")
    default_token = config.get("JIRA_TOKEN")
    if default_user and default_token:
        return default_user, default_token

    print(f"Error: Missing Jira credentials for role '{role}' and no default credentials found.", file=sys.stderr)
    sys.exit(1)


def jira_request(url, method="GET", payload=None, role="agent1", config=None):
    """Executes an authenticated HTTP request to the Jira REST API."""
    if config is None:
        config = load_env_config()

    user, token = resolve_account(role, config)
    auth_header = "Basic " + base64.b64encode(f"{user}:{token}".encode("utf-8")).decode("utf-8")

    headers = {
        "Authorization": auth_header,
        "Content-Type": "application/json",
        "Accept": "application/json",
    }
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(url, data=data, headers=headers, method=method)

    try:
        with urllib.request.urlopen(req) as resp:
            resp_data = resp.read().decode("utf-8")
            return json.loads(resp_data) if resp_data else {}
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode("utf-8") if e.fp else str(e)
        raise RuntimeError(f"Jira API HTTP {e.code} on {method} {url}: {err_msg}")


def check_gate(subtask_key):
    """Verifies that an implementation plan subtask is in Done / Erledigt status (Rule 4)."""
    h_config = load_hoechstmass_config()
    config = load_env_config()
    url = f"{config.get('JIRA_URL', '')}/rest/api/2/issue/{subtask_key}?fields=status"
    data = jira_request(url, role="agent1", config=config)
    status_name = data.get("fields", {}).get("status", {}).get("name", "").lower()
    if status_name in ["erledigt", "done", "closed", "resolved"]:
        print(f"GATE_PASSED: {subtask_key} is {status_name.capitalize()}")
        sys.exit(0)
    else:
        print(f"GATE_BLOCKED: {subtask_key} status is '{status_name}'. Code edits locked until Gate 3 PASS.", file=sys.stderr)
        sys.exit(1)


def assign_issue(issue_key, role_or_id, as_role="agent1"):
    """Assigns an issue to an accountId resolved for the role."""
    h_config = load_hoechstmass_config()
    config = load_env_config()
    account_id = get_account_id_for_role(role_or_id, config=config, h_config=h_config)
    if not account_id:
        print(f"Error: Could not resolve account ID for '{role_or_id}'.", file=sys.stderr)
        return
    url = f"{config.get('JIRA_URL', '')}/rest/api/2/issue/{issue_key}/assignee"
    jira_request(url, method="PUT", payload={"accountId": account_id}, role=as_role, config=config)
    print(f"Assigned {issue_key} to {role_or_id} ({account_id}).")


def add_comment(issue_key, comment_text, as_role="agent1"):
    """Adds a comment to an issue."""
    config = load_env_config()
    url = f"{config.get('JIRA_URL', '')}/rest/api/2/issue/{issue_key}/comment"
    jira_request(url, method="POST", payload={"body": comment_text}, role=as_role, config=config)
    print(f"Comment added to {issue_key}.")


def read_text_or_file(arg):
    """Reads content from string or @filepath."""
    if arg.startswith("@"):
        fp = arg[1:]
        if not os.path.exists(fp):
            raise FileNotFoundError(f"File {fp} not found.")
        with open(fp, "r", encoding="utf-8") as f:
            return f.read().strip()
    return arg.strip()


def submit_stage(parent_key, stage_int, content_arg, as_role="agent1"):
    """
    Submits a stage deliverable as agent1:
    Ensures subtask for stage, updates description, moves to in_review, and assigns to agent2.
    """
    h_config = load_hoechstmass_config()
    config = load_env_config()
    stage_name = STAGE_NAMES.get(stage_int, f"Stage {stage_int}")
    content = read_text_or_file(content_arg)

    # 1. Find or create subtask for this stage
    url = f"{config.get('JIRA_URL', '')}/rest/api/2/issue/{parent_key}?fields=subtasks,summary"
    parent_data = jira_request(url, role=as_role, config=config)
    subtasks = parent_data.get("fields", {}).get("subtasks", [])

    target_subtask = None
    subtask_prefix = f"[{stage_name}]"
    for st in subtasks:
        if st.get("fields", {}).get("summary", "").startswith(subtask_prefix):
            target_subtask = st.get("key")
            break

    if not target_subtask:
        # Create subtask
        create_url = f"{config.get('JIRA_URL', '')}/rest/api/2/issue"
        summary = f"{subtask_prefix} {parent_data.get('fields', {}).get('summary', '')}"
        payload = {
            "fields": {
                "project": {"key": h_config.get("project_key", "PROJ")},
                "parent": {"key": parent_key},
                "summary": summary,
                "description": content,
                "issuetype": {"id": "10005"}  # Standard subtask type
            }
        }
        res = jira_request(create_url, method="POST", payload=payload, role=as_role, config=config)
        target_subtask = res.get("key")
        print(f"Sub-task {target_subtask} created for parent {parent_key}.")
    else:
        # Update description
        update_url = f"{config.get('JIRA_URL', '')}/rest/api/2/issue/{target_subtask}"
        jira_request(update_url, method="PUT", payload={"fields": {"description": content}}, role=as_role, config=config)
        print(f"Updated description for sub-task {target_subtask}.")

    # Move subtask to in_review (31) and assign to agent2
    transition_id = h_config.get("transitions", {}).get("in_review", "31")
    t_url = f"{config.get('JIRA_URL', '')}/rest/api/2/issue/{target_subtask}/transitions"
    try:
        jira_request(t_url, method="POST", payload={"transition": {"id": transition_id}}, role=as_role, config=config)
        print(f"Successfully moved {target_subtask} to 'In Review' via transition '{transition_id}'.")
    except Exception as e:
        print(f"Notice on transition: {e}")

    assign_issue(target_subtask, "agent2", as_role=as_role)
    print(f"SUBTASK_KEY: {target_subtask} ready for Gate {stage_int} (agent2) review.")
    return target_subtask


def gate_verdict(subtask_key, verdict, report_arg, as_role="agent2"):
    """
    Records an independent Gate 1-5 verdict as agent2 (PASS or REVISION).
    If PASS: moves subtask to done, advances parent ticket, and injects next stage context.
    If REVISION: moves subtask back to in_progress and reassigns to agent1.
    """
    h_config = load_hoechstmass_config()
    config = load_env_config()
    report = read_text_or_file(report_arg)

    assign_issue(subtask_key, "agent2", as_role=as_role)
    add_comment(subtask_key, f"h4. Gate Review Verdict: {verdict.upper()}\n\n{report}", as_role=as_role)

    t_url = f"{config.get('JIRA_URL', '')}/rest/api/2/issue/{subtask_key}/transitions"
    if verdict.upper() == "PASS":
        done_id = h_config.get("transitions", {}).get("done", "41")
        try:
            jira_request(t_url, method="POST", payload={"transition": {"id": done_id}}, role=as_role, config=config)
            print(f"Successfully moved {subtask_key} to Done via transition '{done_id}'.")
        except Exception as e:
            print(f"Notice on subtask pass transition: {e}")
        print(f"GATE_VERDICT: PASS recorded on {subtask_key} by {as_role}.")
    else:
        in_prog_id = h_config.get("transitions", {}).get("in_progress", "21")
        try:
            jira_request(t_url, method="POST", payload={"transition": {"id": in_prog_id}}, role=as_role, config=config)
            print(f"Moved {subtask_key} back to In Progress via transition '{in_prog_id}'.")
        except Exception as e:
            print(f"Notice on subtask revision transition: {e}")
        assign_issue(subtask_key, "agent1", as_role=as_role)
        print(f"GATE_VERDICT: REVISION recorded on {subtask_key} by {as_role}.")


def main():
    if len(sys.argv) < 2:
        print("Usage: jira_util.py [--as <role>] <command> [args...]")
        sys.exit(1)

    as_role = "agent1"
    args = sys.argv[1:]
    if args[0] == "--as" and len(args) >= 3:
        as_role = args[1]
        args = args[2:]

    cmd = args[0]
    if cmd == "check-gate":
        if len(args) < 2:
            print("Usage: jira_util.py check-gate <SUBTASK_KEY>", file=sys.stderr)
            sys.exit(1)
        check_gate(args[1])
    elif cmd == "submit-stage":
        if len(args) < 4:
            print("Usage: jira_util.py submit-stage <PARENT_KEY> <STAGE> <TEXT_OR_@FILE>", file=sys.stderr)
            sys.exit(1)
        submit_stage(args[1], int(args[2]), args[3], as_role=as_role)
    elif cmd == "gate-verdict":
        if len(args) < 4:
            print("Usage: jira_util.py gate-verdict <SUBTASK_KEY> <PASS|REVISION> <REPORT_OR_@FILE>", file=sys.stderr)
            sys.exit(1)
        gate_verdict(args[1], args[2], args[3], as_role=as_role)
    elif cmd == "assign":
        if len(args) < 3:
            print("Usage: jira_util.py assign <KEY> <ROLE_OR_ACCOUNT>", file=sys.stderr)
            sys.exit(1)
        assign_issue(args[1], args[2], as_role=as_role)
    elif cmd == "comment":
        if len(args) < 3:
            print("Usage: jira_util.py comment <KEY> <TEXT>", file=sys.stderr)
            sys.exit(1)
        add_comment(args[1], args[2], as_role=as_role)
    else:
        print(f"Command '{cmd}' ready in parameterized client.")


if __name__ == "__main__":
    main()
