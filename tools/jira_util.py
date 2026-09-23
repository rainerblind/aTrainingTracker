#!/usr/bin/env python3
import urllib.request
import base64
import json
import os
import re
import sys
from urllib.parse import urlencode

# Transitions for ATT project
TRANSITIONS = {
    "todo": "11",
    "in_progress": "21",
    "in_review": "31",
    "done": "41"
}

VALID_ROLES = ["agent1", "agent2", "coordinator", "human"]

ROLE_ACCOUNT_IDS = {
    "agent1": "712020:63ce5f53-e2ba-43f5-9879-edf4a22ff748",
    "agent2": "712020:1b6bc33f-f738-43f1-a4cf-373952d25316",
    "coordinator": "712020:3d1e82c8-2875-4952-a22d-90f428dd604b",
    "human": "712020:9d9c7abd-b9d3-4b45-b2e0-91c432c3ab78",
    "user": "712020:9d9c7abd-b9d3-4b45-b2e0-91c432c3ab78",
    "rainer": "712020:9d9c7abd-b9d3-4b45-b2e0-91c432c3ab78",
}

def get_account_id_for_role(role_or_account_id, config=None):
    if not role_or_account_id:
        return None
    if config is None:
        config = get_config()
    role_key = str(role_or_account_id).lower().strip()
    env_key = f"JIRA_{role_key.upper()}_ACCOUNT_ID"
    if env_key in config:
        return config[env_key]
    if role_key in ROLE_ACCOUNT_IDS:
        return ROLE_ACCOUNT_IDS[role_key]
    return role_or_account_id

def assign_issue(issue_key, target_role_or_account_id, role="agent1"):
    account_id = get_account_id_for_role(target_role_or_account_id)
    if not account_id:
        print(f"Error: Could not resolve account ID for '{target_role_or_account_id}'.", file=sys.stderr)
        return
    config = get_config()
    url = f"{config['JIRA_URL']}/rest/api/2/issue/{issue_key}/assignee"
    payload = {"accountId": account_id}
    try:
        jira_request(url, method="PUT", payload=payload, role=role)
        print(f"Assigned {issue_key} to {target_role_or_account_id} ({account_id}).")
    except Exception as e:
        print(f"Warning: Failed to assign {issue_key} to {target_role_or_account_id}: {e}", file=sys.stderr)

def get_config():
    env_file = os.path.join(os.path.dirname(__file__), "..", ".env.jira")
    config = {}
    if not os.path.exists(env_file):
        print(f"Error: {env_file} not found.", file=sys.stderr)
        sys.exit(1)
    with open(env_file, "r", encoding="utf-8") as f:
        for line in f:
            if "=" in line and not line.startswith("#"):
                k, v = line.strip().split("=", 1)
                config[k.strip()] = v.strip()
    return config

def resolve_account(role="agent1", config=None):
    """
    Resolves (username, token) atomically for the given role.
    If role keys are partially missing, falls back to (JIRA_USER, JIRA_TOKEN) with advisory notice.
    If neither role nor default credentials exist, halts with exit code 1.
    """
    if config is None:
        config = get_config()

    role_mapping = {
        "agent1": ("JIRA_AGENT1_USER", "JIRA_AGENT1_TOKEN"),
        "agent2": ("JIRA_AGENT2_USER", "JIRA_AGENT2_TOKEN"),
        "coordinator": ("JIRA_COORDINATOR_USER", "JIRA_COORDINATOR_TOKEN"),
    }

    role_user_key, role_token_key = role_mapping.get(role, (None, None))
    role_user = config.get(role_user_key) if role_user_key else None
    role_token = config.get(role_token_key) if role_token_key else None

    # Check if both are present and non-empty
    if role_user and role_token:
        return role_user, role_token

    # If partial definition (one is present, other is empty/missing), advise and fallback
    if role_user or role_token:
        missing = role_token_key if role_user else role_user_key
        print(f"Advisory: Partial Jira credentials for role '{role}' ({missing} missing). "
              f"Falling back atomically to default (JIRA_USER, JIRA_TOKEN).", file=sys.stderr)

    default_user = config.get("JIRA_USER")
    default_token = config.get("JIRA_TOKEN")
    if default_user and default_token:
        return default_user, default_token

    print(f"Error: No Jira credentials found for role '{role}' and no default JIRA_USER/JIRA_TOKEN in config.", file=sys.stderr)
    sys.exit(1)

def sanitize_secrets(text, secrets_to_mask=None):
    """Sanitizes sensitive tokens and Basic Auth credentials from text before printing."""
    if not text:
        return ""
    if not isinstance(text, str):
        text = str(text)

    sanitized = text
    if secrets_to_mask:
        for sec in secrets_to_mask:
            if sec and len(sec) > 4:
                sanitized = sanitized.replace(sec, "[MASKED]")

    # Scrub standard JWT / Atlassian tokens pattern
    sanitized = re.sub(r'ATATT3[A-Za-z0-9_\-=]+', '[MASKED]', sanitized)
    # Scrub Basic Auth base64 pattern in Authorization headers
    sanitized = re.sub(r'Basic\s+[A-Za-z0-9+/=]{16,}', 'Basic [MASKED]', sanitized)
    return sanitized

def get_headers(config, role="agent1"):
    user, token = resolve_account(role, config)
    auth_str = f"{user}:{token}"
    encoded_auth = base64.b64encode(auth_str.encode("ascii")).decode("ascii")
    return {
        "Authorization": f"Basic {encoded_auth}",
        "Accept": "application/json",
        "Content-Type": "application/json"
    }

def get_comment_prefix(role="agent1"):
    prefixes = {
        "agent1": "[Automated comment by AI Agent 1 (Implementer)]\n\n",
        "agent2": "[Automated comment by AI Agent 2 (Auditor)]\n\n",
        "coordinator": "[Automated comment by AI Coordinator]\n\n",
    }
    return prefixes.get(role, "[Automated comment by AI Agent]\n\n")

def parse_role_from_args(args=None):
    """
    Parses active role from CLI arguments (--as <role>), environment variable JIRA_ACTOR,
    or falls back to default 'agent1'.
    Returns (active_role, remaining_args).
    """
    if args is None:
        args = sys.argv[1:]

    role_from_cli = None
    remaining_args = []
    i = 0
    while i < len(args):
        if args[i] == "--as" and i + 1 < len(args):
            role_from_cli = args[i + 1]
            i += 2
        elif args[i].startswith("--as="):
            role_from_cli = args[i].split("=", 1)[1]
            i += 1
        else:
            remaining_args.append(args[i])
            i += 1

    if role_from_cli:
        if role_from_cli not in VALID_ROLES:
            print(f"Error: Invalid role '{role_from_cli}'. Recognized roles: {', '.join(VALID_ROLES)}", file=sys.stderr)
            sys.exit(1)
        return role_from_cli, remaining_args

    env_actor = os.environ.get("JIRA_ACTOR")
    if env_actor:
        if env_actor not in VALID_ROLES:
            print(f"Error: Invalid role '{env_actor}' in JIRA_ACTOR environment variable. Recognized roles: {', '.join(VALID_ROLES)}", file=sys.stderr)
            sys.exit(1)
        return env_actor, remaining_args

    return "agent1", remaining_args

def jira_request(url, method="GET", payload=None, is_binary=False, role="agent1"):
    config = get_config()
    headers = get_headers(config, role=role)
    data = json.dumps(payload).encode("utf-8") if payload else None

    req = urllib.request.Request(url, data=data, method=method)
    for k, v in headers.items():
        req.add_header(k, v)

    user, token = resolve_account(role, config)

    try:
        with urllib.request.urlopen(req) as response:
            if is_binary:
                return response.read()
            body = response.read().decode("utf-8")
            return json.loads(body) if body else {}
    except Exception as e:
        if hasattr(e, "read"):
            err_body = e.read().decode('utf-8', errors='replace')
            masked_body = sanitize_secrets(err_body, [token])
            print(f"API Error: {masked_body}", file=sys.stderr)
        raise e

def list_sprint_issues(role="agent1"):
    config = get_config()
    # 1. Find the board
    boards = jira_request(f"{config['JIRA_URL']}/rest/agile/1.0/board", role=role)["values"]
    board_id = boards[0]["id"]

    # 2. Find active sprint
    sprints = jira_request(f"{config['JIRA_URL']}/rest/agile/1.0/board/{board_id}/sprint?state=active", role=role)["values"]
    if not sprints:
        print("No active sprint found.")
        return
    sprint_id = sprints[0]["id"]
    print(f"Active Sprint: {sprints[0]['name']}")

    # 3. Get issues
    issues = jira_request(f"{config['JIRA_URL']}/rest/agile/1.0/sprint/{sprint_id}/issue?fields=summary,status,issuetype,fixVersions", role=role)["issues"]
    for i in issues:
        itype = i['fields']['issuetype']['name']
        fvs = [v.get('name', '') for v in i['fields'].get('fixVersions', [])]
        fv_str = f" [FixVersion: {', '.join(fvs)}]" if fvs else " [No FixVersion!]"
        print(f"{i['key']}: [{itype}] {i['fields']['summary']} [{i['fields']['status']['name']}]{fv_str}")

def show_issue(issue_key, role="agent1"):
    config = get_config()
    url = f"{config['JIRA_URL']}/rest/api/2/issue/{issue_key}?fields=summary,description,comment,attachment,parent,issuetype,status,subtasks,fixVersions,assignee"
    issue = jira_request(url, role=role)

    itype = issue['fields']['issuetype']['name']
    status = issue['fields'].get('status', {}).get('name', 'Unknown')
    print(f"h1. {issue['key']}: [{itype}] {issue['fields']['summary']}")
    print(f"\n*Status*: {status}")

    assignee = issue['fields'].get('assignee')
    assignee_name = assignee.get('displayName', 'Unassigned') if assignee else 'Unassigned'
    print(f"\n*Assignee*: {assignee_name}")

    # Fix Version / Lösungsversion
    fix_versions = issue['fields'].get('fixVersions', [])
    if fix_versions:
        fv_names = ", ".join([v.get('name', '') for v in fix_versions])
        print(f"\n*Lösungsversion (Fix Version/s)*: {fv_names}")
    else:
        # Check if parent has a fixVersion if this is a subtask
        parent_fv = None
        if issue['fields'].get('parent'):
            parent_key = issue['fields']['parent']['key']
            parent_url = f"{config['JIRA_URL']}/rest/api/2/issue/{parent_key}?fields=fixVersions"
            try:
                parent_res = jira_request(parent_url, role=role)
                pfvs = parent_res.get('fields', {}).get('fixVersions', [])
                if pfvs:
                    parent_fv = ", ".join([v.get('name', '') for v in pfvs])
            except Exception:
                pass
        if parent_fv:
            print(f"\n*Lösungsversion (Fix Version/s)*: None on subtask (Inherited from Parent: {parent_fv})")
        else:
            print(f"\n*Lösungsversion (Fix Version/s)*: None (WARNING: Mandatory field missing!)")

    # Epic/Parent context
    parent = issue['fields'].get('parent')
    if parent:
        parent_key = parent['key']
        parent_summary = parent['fields']['summary']
        parent_type = parent['fields']['issuetype']['name']
        print(f"\n*Parent ({parent_type})*: {parent_key}: {parent_summary}")

        # If parent is an Epic, fetch its description for more context
        if parent_type == "Epic":
            epic_url = f"{config['JIRA_URL']}/rest/api/2/issue/{parent_key}?fields=description"
            epic = jira_request(epic_url, role=role)
            epic_desc = epic['fields'].get('description', 'No description')
            print(f"\n*Epic Description*:\n{epic_desc}")

    print("\n*Sub-tasks*:")
    subtasks = issue['fields'].get('subtasks', [])
    if not subtasks:
        print("None")
    for st in subtasks:
        st_key = st.get('key')
        st_summary = st.get('fields', {}).get('summary', '')
        st_status = st.get('fields', {}).get('status', {}).get('name', 'Unknown')
        print(f"* {st_key}: {st_summary} [{st_status}]")

    print(f"\n*Description*:\n{issue['fields']['description']}")

    print("\n*Attachments*:")
    attachments = issue['fields'].get('attachment', [])
    if not attachments:
        print("None")
    for a in attachments:
        print(f"* {a['filename']} ({a['size']} bytes) - ID: {a['id']} - URL: {a['content']}")

    print("\n*Comments*:")
    for c in issue['fields']['comment']['comments']:
        print(f"--- {c['author']['displayName']} ({c['created']}) ---\n{c['body']}\n")

def list_versions(role="agent1"):
    config = get_config()
    url = f"{config['JIRA_URL']}/rest/api/2/project/ATT/versions"
    versions = jira_request(url, role=role)
    print("Project ATT Versions (Lösungsversionen):")
    for v in versions:
        status = "RELEASED" if v.get("released") else ("ARCHIVED" if v.get("archived") else "ACTIVE/UNRELEASED")
        print(f"- {v.get('name')} (id: {v.get('id')}) [{status}]")

def set_fix_version(issue_key, version_name, role="agent1"):
    config = get_config()
    # Validate against project ATT versions
    url_versions = f"{config['JIRA_URL']}/rest/api/2/project/ATT/versions"
    versions = jira_request(url_versions, role=role)
    valid_names = [v.get('name') for v in versions]
    if version_name not in valid_names:
        active_versions = [v.get('name') for v in versions if not v.get('released') and not v.get('archived')]
        print(f"Error: Version '{version_name}' not found in project ATT.", file=sys.stderr)
        print(f"Available active unreleased versions: {', '.join(active_versions)}", file=sys.stderr)
        sys.exit(1)

    url = f"{config['JIRA_URL']}/rest/api/2/issue/{issue_key}"
    payload = {
        "fields": {
            "fixVersions": [{"name": version_name}]
        }
    }
    jira_request(url, method="PUT", payload=payload, role=role)
    print(f"Lösungsversion (Fix Version) '{version_name}' successfully set on {issue_key}.")

def print_status(issue_key, role="agent1"):
    config = get_config()
    url = f"{config['JIRA_URL']}/rest/api/2/issue/{issue_key}?fields=status"
    issue = jira_request(url, role=role)
    status = issue['fields'].get('status', {}).get('name', 'Unknown')
    print(f"{issue_key} status: {status}")
    return status

def check_gate(issue_key, role="agent1"):
    config = get_config()
    url = f"{config['JIRA_URL']}/rest/api/2/issue/{issue_key}?fields=status"
    issue = jira_request(url, role=role)
    status = issue['fields'].get('status', {}).get('name', 'Unknown')
    if status == "Erledigt":
        print(f"GATE_PASSED: {issue_key} is Erledigt")
        sys.exit(0)
    else:
        print(f"GATE_BLOCKED: {issue_key} is in status '{status}' (Expected: Erledigt)")
        sys.exit(1)

def download_attachment(url, filename, role="agent1"):
    print(f"Downloading {filename}...")
    content = jira_request(url, is_binary=True, role=role)

    os.makedirs("docs/attachments", exist_ok=True)
    path = os.path.join("docs/attachments", filename)
    with open(path, "wb") as f:
        f.write(content)
    print(f"Saved to {path}")

def download_all_attachments(issue_key, role="agent1"):
    config = get_config()
    url = f"{config['JIRA_URL']}/rest/api/2/issue/{issue_key}?fields=attachment"
    issue = jira_request(url, role=role)
    attachments = issue['fields'].get('attachment', [])
    if not attachments:
        print(f"No attachments found for {issue_key}.")
        return

    print(f"Found {len(attachments)} attachments for {issue_key}.")
    for a in attachments:
        download_attachment(a['content'], a['filename'], role=role)

def transition_issue(issue_key, status_name, role="agent1"):
    # Strict Human Gate Guard: Prohibit AI agents from moving to Erledigt / Freigabe erteilt
    prohibited_targets = ["erledigt", "done", "freigabe erteilt"]
    normalized_input = status_name.lower().strip()
    if normalized_input in prohibited_targets:
        print(f"ERROR: Transitioning '{issue_key}' to '{status_name}' is strictly prohibited for AI agents.\n"
              f"Moving tickets or sub-tasks to 'Erledigt' ('Freigabe erteilt') is a Human Decision Gate reserved exclusively for the human user.",
              file=sys.stderr)
        sys.exit(1)

    config = get_config()
    url = f"{config['JIRA_URL']}/rest/api/3/issue/{issue_key}/transitions"
    data = jira_request(url, role=role)
    available_transitions = data.get("transitions", [])

    aliases = {
        "todo": "zu erledigen",
        "in_progress": "in bearbeitung",
        "in_review": "in überprüfung",
        "review": "in überprüfung",
        "freigabe": "freigabe (human)",
        "human": "freigabe (human)",
        "revision": "in bearbeitung",
        "rework": "in bearbeitung",
        "nochmals von vorne": "in bearbeitung"
    }
    normalized_target = aliases.get(normalized_input, normalized_input)

    chosen_trans = None
    for t in available_transitions:
        target_name = t.get("to", {}).get("name", "").lower()
        trans_name = t.get("name", "").lower()
        if (normalized_target == target_name or 
            normalized_target == trans_name or 
            normalized_target in target_name or 
            normalized_target in trans_name):
            chosen_trans = t
            break

    if not chosen_trans:
        avail_str = ", ".join([f"'{t['name']}' -> '{t.get('to', {}).get('name')}' (id {t['id']})" for t in available_transitions])
        print(f"Error: Cannot transition '{issue_key}' to '{status_name}'. Available transitions: {avail_str}", file=sys.stderr)
        return

    target_name = chosen_trans.get("to", {}).get("name", "").lower()
    trans_name = chosen_trans.get("name", "").lower()
    if target_name == "erledigt" or trans_name == "freigabe erteilt":
        print(f"ERROR: Transition '{chosen_trans['name']}' to '{chosen_trans.get('to', {}).get('name')}' is strictly prohibited for AI agents.\n"
              f"This transition is a Human Decision Gate reserved exclusively for the human user.", file=sys.stderr)
        sys.exit(1)

    trans_id = chosen_trans["id"]
    jira_request(url, method="POST", payload={"transition": {"id": trans_id}}, role=role)
    target_status = chosen_trans.get("to", {}).get("name", status_name)
    print(f"Successfully moved {issue_key} to '{target_status}' via transition '{chosen_trans['name']}'.")

    # Auto-assign based on target workflow stage
    norm_target_lower = target_name.lower()
    if "überprüfung" in norm_target_lower or "review" in norm_target_lower:
        assign_issue(issue_key, "agent2", role=role)
    elif "bearbeitung" in norm_target_lower or "progress" in norm_target_lower:
        assign_issue(issue_key, "agent1", role=role)

def add_comment(issue_key, text, role="agent1"):
    if text.startswith("@") and os.path.exists(text[1:]):
        with open(text[1:], "r", encoding="utf-8") as f:
            text = f.read()

    config = get_config()
    url = f"{config['JIRA_URL']}/rest/api/2/issue/{issue_key}/comment"

    identity_prefix = get_comment_prefix(role)
    full_text = identity_prefix + text

    payload = {
        "body": full_text
    }
    jira_request(url, method="POST", payload=payload, role=role)
    print(f"Comment added to {issue_key}.")

def search_issues(jql, role="agent1"):
    config = get_config()
    url = f"{config['JIRA_URL']}/rest/api/3/search/jql"
    payload = {
        "jql": jql,
        "fields": ["summary", "status", "issuetype"]
    }
    data = jira_request(url, method="POST", payload=payload, role=role)
    for i in data.get("issues", []):
        itype = i['fields']['issuetype']['name']
        print(f"{i['key']}: [{itype}] {i['fields']['summary']} [{i['fields']['status']['name']}]")

def update_issue_description(issue_key, description, role="agent1"):
    if description.startswith("@") and os.path.exists(description[1:]):
        with open(description[1:], "r", encoding="utf-8") as f:
            description = f.read()

    config = get_config()
    url = f"{config['JIRA_URL']}/rest/api/2/issue/{issue_key}"
    payload = {
        "fields": {
            "description": description
        }
    }
    jira_request(url, method="PUT", payload=payload, role=role)
    print(f"Description updated for {issue_key}.")

def update_issue_summary(issue_key, summary, role="agent1"):
    config = get_config()
    url = f"{config['JIRA_URL']}/rest/api/2/issue/{issue_key}"
    payload = {
        "fields": {
            "summary": summary
        }
    }
    jira_request(url, method="PUT", payload=payload, role=role)
    print(f"Summary updated for {issue_key}.")

def create_subtask(parent_key, summary, description, role="coordinator", add_to_sprint=False, fix_version=None):
    if summary.startswith("[Impl] "):
        summary = "[Implementation] " + summary[7:]
        print("Normalized subtask prefix '[Impl]' -> '[Implementation]' to ensure Jira automation compatibility.")

    config = get_config()
    url = f"{config['JIRA_URL']}/rest/api/2/issue"
    fields = {
        "project": {"key": "ATT"},
        "parent": {"key": parent_key},
        "summary": summary,
        "description": description,
        "issuetype": {"id": "10002"}  # Subtask ID
    }
    if fix_version:
        fields["fixVersions"] = [{"name": fix_version}]

    payload = {"fields": fields}
    data = jira_request(url, method="POST", payload=payload, role=role)
    new_key = data['key']
    print(f"Sub-task {new_key} created for parent {parent_key}.")

    if add_to_sprint:
        add_to_active_sprint(new_key, role=role)

    # Initial assignment: default subtasks to agent1
    assign_issue(new_key, "agent1", role=role)

    return new_key

def create_issue(summary, description, issuetype_id="10008", parent_key=None, role="agent1", add_to_sprint=False, fix_version=None):
    if description.startswith("@") and os.path.exists(description[1:]):
        with open(description[1:], "r", encoding="utf-8") as f:
            description = f.read()

    config = get_config()
    url = f"{config['JIRA_URL']}/rest/api/2/issue"
    fields = {
        "project": {"key": "ATT"},
        "summary": summary,
        "description": description,
        "issuetype": {"id": issuetype_id}
    }
    if parent_key:
        fields["parent"] = {"key": parent_key}
    if fix_version:
        fields["fixVersions"] = [{"name": fix_version}]

    payload = {"fields": fields}
    data = jira_request(url, method="POST", payload=payload, role=role)
    new_key = data['key']
    print(f"Issue {new_key} created.")

    if add_to_sprint:
        add_to_active_sprint(new_key, role=role)

    return new_key

def add_to_active_sprint(issue_key, role="agent1"):
    config = get_config()
    boards = jira_request(f"{config['JIRA_URL']}/rest/agile/1.0/board", role=role)["values"]
    board_id = boards[0]["id"]
    sprints = jira_request(f"{config['JIRA_URL']}/rest/agile/1.0/board/{board_id}/sprint?state=active", role=role)["values"]
    if not sprints:
        print("No active sprint found.")
        return
    sprint_id = sprints[0]["id"]
    url = f"{config['JIRA_URL']}/rest/agile/1.0/sprint/{sprint_id}/issue"
    payload = {"issues": [issue_key]}
    jira_request(url, method="POST", payload=payload, role=role)
    print(f"Added {issue_key} to active sprint '{sprints[0]['name']}' (id {sprint_id}).")

if __name__ == "__main__":
    active_role, remaining_argv = parse_role_from_args(sys.argv[1:])

    if len(remaining_argv) < 1:
        print("Usage: jira_util.py [--as agent1|agent2|coordinator] [list | show KEY | status KEY | check-gate KEY | versions | set-fixversion KEY VERSION | move KEY todo|in_progress|in_review|freigabe | comment KEY TEXT | download URL FILENAME | download-all KEY | search JQL | update-desc KEY TEXT | create-subtask PARENT_KEY SUMMARY DESC [--add-to-sprint] [--fixversion=VERSION] | create-issue SUMMARY DESC [TYPE_ID] [PARENT_KEY] [--add-to-sprint] [--fixversion=VERSION] | add-to-sprint KEY]", file=sys.stderr)
        sys.exit(1)

    cmd = remaining_argv[0]
    if cmd == "list":
        list_sprint_issues(role=active_role)
    elif cmd == "show" and len(remaining_argv) == 2:
        show_issue(remaining_argv[1], role=active_role)
    elif cmd == "status" and len(remaining_argv) == 2:
        print_status(remaining_argv[1], role=active_role)
    elif cmd == "check-gate" and len(remaining_argv) == 2:
        check_gate(remaining_argv[1], role=active_role)
    elif cmd == "versions":
        list_versions(role=active_role)
    elif cmd == "set-fixversion" and len(remaining_argv) == 3:
        set_fix_version(remaining_argv[1], remaining_argv[2], role=active_role)
    elif cmd == "download" and len(remaining_argv) == 3:
        download_attachment(remaining_argv[1], remaining_argv[2], role=active_role)
    elif cmd == "download-all" and len(remaining_argv) == 2:
        download_all_attachments(remaining_argv[1], role=active_role)
    elif cmd == "move" and len(remaining_argv) == 3:
        transition_issue(remaining_argv[1], remaining_argv[2], role=active_role)
    elif (cmd == "comment" or cmd == "add-comment") and len(remaining_argv) == 3:
        add_comment(remaining_argv[1], remaining_argv[2], role=active_role)
    elif cmd == "search" and len(remaining_argv) == 2:
        search_issues(remaining_argv[1], role=active_role)
    elif cmd == "update-desc" and len(remaining_argv) == 3:
        update_issue_description(remaining_argv[1], remaining_argv[2], role=active_role)
    elif cmd == "update-summary" and len(remaining_argv) == 3:
        update_issue_summary(remaining_argv[1], remaining_argv[2], role=active_role)
    elif cmd == "assign" and len(remaining_argv) == 3:
        assign_issue(remaining_argv[1], remaining_argv[2], role=active_role)
    elif cmd == "create-subtask" and len(remaining_argv) >= 4:
        # Parse optional flags
        add_sprint = False
        fix_ver = None
        filtered_args = []
        for arg in remaining_argv[1:]:
            if arg == "--add-to-sprint":
                add_sprint = True
            elif arg.startswith("--fixversion="):
                fix_ver = arg.split("=", 1)[1]
            elif arg == "--fixversion" or arg.startswith("--fix-version"):
                # Handle next arg if separated
                pass
            else:
                filtered_args.append(arg)

        if len(filtered_args) < 3:
            print("Usage: create-subtask PARENT_KEY SUMMARY DESC [--add-to-sprint] [--fixversion=VERSION]", file=sys.stderr)
            sys.exit(1)

        parent_k = filtered_args[0]
        summ = filtered_args[1]
        desc = filtered_args[2]

        # Default subtask creation role to coordinator unless explicitly overridden
        subtask_role = active_role if active_role != "agent1" or "--as" in sys.argv or any(a.startswith("--as=") for a in sys.argv) or os.environ.get("JIRA_ACTOR") else "coordinator"
        create_subtask(parent_k, summ, desc, role=subtask_role, add_to_sprint=add_sprint, fix_version=fix_ver)
    elif cmd == "create-issue" and len(remaining_argv) >= 3:
        add_sprint = False
        fix_ver = None
        filtered_args = []
        for arg in remaining_argv[1:]:
            if arg == "--add-to-sprint":
                add_sprint = True
            elif arg.startswith("--fixversion="):
                fix_ver = arg.split("=", 1)[1]
            else:
                filtered_args.append(arg)

        if len(filtered_args) < 2:
            print("Usage: create-issue SUMMARY DESC [TYPE_ID] [PARENT_KEY] [--add-to-sprint] [--fixversion=VERSION]", file=sys.stderr)
            sys.exit(1)

        summary = filtered_args[0]
        desc = filtered_args[1]
        type_id = filtered_args[2] if len(filtered_args) >= 3 else "10008"
        parent = filtered_args[3] if len(filtered_args) >= 4 else None
        create_issue(summary, desc, type_id, parent, role=active_role, add_to_sprint=add_sprint, fix_version=fix_ver)
    elif cmd == "add-to-sprint" and len(remaining_argv) == 2:
        add_to_active_sprint(remaining_argv[1], role=active_role)
    else:
        print("Invalid command or arguments.", file=sys.stderr)
        sys.exit(1)
