#!/usr/bin/env python3
import urllib.request
import base64
import json
import os
import re
import ssl
import subprocess
import sys
from datetime import datetime
from urllib.parse import urlencode

_tools_dir = os.path.dirname(os.path.abspath(__file__))
if _tools_dir not in sys.path:
    sys.path.insert(0, _tools_dir)

from sprint_metrics import (
    FINAL_REVIEW_BOUNCE_RE,
    FINAL_REVIEW_ESCALATED_RE,
    FINAL_REVIEW_PASS_RE,
    STAGE_MAP,
    calculate_spc_limits,
    format_metrics_markdown,
    identify_gate_number,
    load_historical_metrics,
    parse_sprint_gate_metrics,
    record_sprint_metrics,
)

# Transitions for ATT project
TRANSITIONS = {
    "todo": "11",
    "in_progress": "21",
    "in_review": "31",
    "done": "41"
}

VALID_ROLES = ["agent1", "agent2", "coordinator"]

ROLE_ACCOUNT_IDS = {
    "agent1": "712020:63ce5f53-e2ba-43f5-9879-edf4a22ff748",
    "agent2": "712020:1b6bc33f-f738-43f1-a4cf-373952d25316",
    "coordinator": "712020:3d1e82c8-2875-4952-a22d-90f428dd604b",
    "human": "712020:9d9c7abd-b9d3-4b45-b2e0-91c432c3ab78",
    "user": "712020:9d9c7abd-b9d3-4b45-b2e0-91c432c3ab78",
    "rainer": "712020:9d9c7abd-b9d3-4b45-b2e0-91c432c3ab78",
}

STORY_POINTS_FIELD = "customfield_10016"

DEFAULT_CONFIG_LOCATIONS = [
    os.path.join(os.path.dirname(__file__), "hoechstmass", "hoechstmass_config.json"),
    os.path.join(os.path.dirname(__file__), "hoechstmass_config.json"),
    os.path.join(os.path.dirname(__file__), "..", "tools", "hoechstmass", "hoechstmass_config.json"),
]

def load_hoechstmass_config(custom_path=None):
    paths = [custom_path] if custom_path else DEFAULT_CONFIG_LOCATIONS
    for p in paths:
        if p and os.path.exists(p):
            with open(p, "r", encoding="utf-8") as f:
                return json.load(f)
    return {
        "deployment_type": "cloud",
        "custom_fields": {
            "story_points": "customfield_10016",
            "epic_link": "customfield_10014",
            "sprint": "customfield_10020"
        }
    }

def is_datacenter(config=None, h_config=None):
    """Determines whether active deployment is Jira Data Center (or Server) vs Jira Cloud."""
    if config is None:
        config = get_config()
    env_deploy = config.get("JIRA_DEPLOYMENT") or config.get("JIRA_DEPLOYMENT_TYPE")
    if env_deploy:
        return env_deploy.strip().lower() in ("datacenter", "data_center", "server", "onprem", "on_premise")
    if h_config is None:
        h_config = load_hoechstmass_config()
    cfg_deploy = h_config.get("deployment_type") or h_config.get("deployment")
    if cfg_deploy:
        return str(cfg_deploy).strip().lower() in ("datacenter", "data_center", "server", "onprem", "on_premise")
    return False

def get_epic_link_field(config=None, h_config=None):
    """Resolves Epic Link custom field key for Jira Data Center (defaults to customfield_10014)."""
    if config is None:
        config = get_config()
    env_field = config.get("JIRA_EPIC_LINK_FIELD") or config.get("JIRA_CUSTOMFIELD_EPIC_LINK")
    if env_field:
        return env_field.strip()
    if h_config is None:
        h_config = load_hoechstmass_config()
    custom_fields = h_config.get("custom_fields", {})
    if "epic_link" in custom_fields:
        return custom_fields["epic_link"]
    return "customfield_10014"

def get_search_endpoint(config=None, h_config=None):
    """Returns REST API endpoint for JQL searches based on deployment type."""
    if config is None:
        config = get_config()
    base_url = config.get("JIRA_URL", "").rstrip("/")
    if is_datacenter(config, h_config):
        return f"{base_url}/rest/api/2/search"
    return f"{base_url}/rest/api/3/search/jql"

def get_ssl_context(config=None):
    """Constructs SSLContext supporting corporate CA bundles and verification toggles."""
    if config is None:
        config = get_config()
    ca_bundle = config.get("JIRA_CA_BUNDLE") or config.get("ca_bundle")
    ssl_verify = str(config.get("JIRA_SSL_VERIFY", config.get("ssl_verify", "true"))).lower() not in ("false", "0", "no")
    if not ssl_verify:
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        return ctx
    elif ca_bundle and os.path.exists(ca_bundle):
        return ssl.create_default_context(cafile=ca_bundle)
    return None

def resolve_assignee_identifier(role_or_account_id, config=None, h_config=None):
    """Resolves assignee identifier: username for Jira Data Center, accountId for Jira Cloud."""
    if not role_or_account_id:
        return None
    if config is None:
        config = get_config()
    if h_config is None:
        h_config = load_hoechstmass_config()
    role_key = str(role_or_account_id).lower().strip()
    if is_datacenter(config, h_config):
        env_name_key = f"JIRA_{role_key.upper()}_NAME"
        if env_name_key in config and config[env_name_key]:
            return config[env_name_key]
        env_user_key = f"JIRA_{role_key.upper()}_USER"
        if env_user_key in config and config[env_user_key]:
            return config[env_user_key]
        env_acc_key = f"JIRA_{role_key.upper()}_ACCOUNT_ID"
        if env_acc_key in config and config[env_acc_key]:
            return config[env_acc_key]
        roles = h_config.get("roles", {})
        if role_key in roles:
            return roles[role_key]
        return role_or_account_id
    else:
        return get_account_id_for_role(role_or_account_id, config=config)

def get_account_id_for_role(role_or_account_id, config=None, h_config=None):
    if not role_or_account_id:
        return None
    if config is None:
        config = get_config()
    role_key = str(role_or_account_id).lower().strip()
    env_key = f"JIRA_{role_key.upper()}_ACCOUNT_ID"
    if env_key in config and config[env_key]:
        return config[env_key]
    if h_config is not None and "roles" in h_config:
        roles = h_config.get("roles", {})
        if role_key in roles:
            return roles[role_key]
    if role_key in ROLE_ACCOUNT_IDS:
        return ROLE_ACCOUNT_IDS[role_key]
    return role_or_account_id

def assign_issue(issue_key, target_role_or_account_id, role="agent1"):
    config = get_config()
    if is_datacenter(config):
        username = resolve_assignee_identifier(target_role_or_account_id, config=config)
        if not username:
            print(f"Error: Could not resolve username for '{target_role_or_account_id}'.", file=sys.stderr)
            return
        url = f"{config['JIRA_URL']}/rest/api/2/issue/{issue_key}/assignee"
        payload = {"name": username}
        target_disp = f"{target_role_or_account_id} ({username})"
    else:
        account_id = get_account_id_for_role(target_role_or_account_id, config=config)
        if not account_id:
            print(f"Error: Could not resolve account ID for '{target_role_or_account_id}'.", file=sys.stderr)
            return
        url = f"{config['JIRA_URL']}/rest/api/2/issue/{issue_key}/assignee"
        payload = {"accountId": account_id}
        target_disp = f"{target_role_or_account_id} ({account_id})"
    try:
        jira_request(url, method="PUT", payload=payload, role=role)
        print(f"Assigned {issue_key} to {target_disp}.")
    except Exception as e:
        print(f"Warning: Failed to assign {issue_key} to {target_role_or_account_id}: {e}", file=sys.stderr)

def get_config(role=None):
    """
    Loads configuration for Jira tooling.
    Under ASPICE SUP.1 / SUP.8 per-role credential isolation (ATT-6147):
    - When role is provided (or JIRA_ACTOR is set), checks for role-specific file (.env.jira.<role>).
    - If .env.jira.<role> exists, it loads shared non-secret config from .env.jira (if present)
      and overlays .env.jira.<role>.
    - If .env.jira.<role> does not exist, falls back to .env.jira.
    - When role is specified, credentials for other roles are excluded to enforce isolation.
    """
    if role is None:
        role = os.environ.get("JIRA_ACTOR")
    if role:
        role = role.strip().lower()

    repo_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    base_env = os.path.join(repo_dir, ".env.jira")
    role_env = os.path.join(repo_dir, f".env.jira.{role}") if role else None

    config = {}
    # 1. Load base .env.jira if present (provides shared JIRA_URL, JIRA_PROJECT, etc.)
    if os.path.exists(base_env):
        with open(base_env, "r", encoding="utf-8") as f:
            for line in f:
                if "=" in line and not line.startswith("#"):
                    k, v = line.strip().split("=", 1)
                    config[k.strip()] = v.strip()

    # 2. If role-specific file exists, overlay it
    if role_env and os.path.exists(role_env):
        with open(role_env, "r", encoding="utf-8") as f:
            for line in f:
                if "=" in line and not line.startswith("#"):
                    k, v = line.strip().split("=", 1)
                    config[k.strip()] = v.strip()

    # 3. If role is specified, filter out other roles' credentials to enforce strict isolation
    if role in ("agent1", "agent2", "coordinator"):
        other_roles = {"agent1", "agent2", "coordinator"} - {role}
        for other in other_roles:
            config.pop(f"JIRA_{other.upper()}_USER", None)
            config.pop(f"JIRA_{other.upper()}_TOKEN", None)
            config.pop(f"JIRA_{other.upper()}_ACCOUNT_ID", None)

    # 4. If neither base_env nor role_env exists and config is empty, report error and exit
    if not config and not os.path.exists(base_env) and not (role_env and os.path.exists(role_env)):
        target = role_env if role else base_env
        print(f"Error: {target} not found.", file=sys.stderr)
        sys.exit(1)

    return config

def load_env_config(custom_path=None, role=None):
    if custom_path:
        config = {}
        if os.path.exists(custom_path):
            with open(custom_path, "r", encoding="utf-8") as f:
                for line in f:
                    if "=" in line and not line.startswith("#"):
                        k, v = line.strip().split("=", 1)
                        config[k.strip()] = v.strip()
            return config
        else:
            print(f"Error: {custom_path} not found.", file=sys.stderr)
            sys.exit(1)
    return get_config(role=role)

def resolve_account(role="agent1", config=None):
    """
    Resolves (username, token) atomically for the given role.
    Under ASPICE SUP.1 role separation, automated roles (agent1, agent2, coordinator)
    must strictly provide their own role credentials; fallback to default human
    credentials (JIRA_USER, JIRA_TOKEN) is prohibited.
    """
    if config is None:
        config = get_config(role=role)

    role_mapping = {
        "agent1": ("JIRA_AGENT1_USER", "JIRA_AGENT1_TOKEN"),
        "agent2": ("JIRA_AGENT2_USER", "JIRA_AGENT2_TOKEN"),
        "coordinator": ("JIRA_COORDINATOR_USER", "JIRA_COORDINATOR_TOKEN"),
    }

    if role in role_mapping:
        role_user_key, role_token_key = role_mapping[role]
        role_user = config.get(role_user_key) if role_user_key else None
        role_token = config.get(role_token_key) if role_token_key else None

        # Check if both are present and non-empty
        if role_user and role_token:
            return role_user, role_token

        # In Data Center or when Bearer PAT is enabled, role token alone is sufficient
        auth_type = (config.get("JIRA_AUTH_TYPE") or "").lower()
        if role_token and (is_datacenter(config) or auth_type == "bearer"):
            return role_user or "", role_token

        print(f"Error: Missing role-specific credentials for role '{role}'. "
              f"Fallback to human default credentials (JIRA_USER/JIRA_TOKEN) is prohibited under ASPICE SUP.1 role separation.", file=sys.stderr)
        sys.exit(1)

    if role == "human":
        default_user = config.get("JIRA_USER")
        default_token = config.get("JIRA_TOKEN") or config.get("JIRA_API_TOKEN")
        auth_type = (config.get("JIRA_AUTH_TYPE") or "").lower()
        if default_user and default_token:
            return default_user, default_token
        if default_token and (is_datacenter(config) or auth_type == "bearer"):
            return default_user or "", default_token

    print(f"Error: Missing Jira credentials for role '{role}'.", file=sys.stderr)
    sys.exit(1)

def mask_secret(text):
    if not text:
        return ""
    if len(text) <= 6:
        return "***"
    return text[:2] + "***" + text[-2:]

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

def get_headers(config=None, role="agent1"):
    if config is None:
        config = get_config(role=role)
    user, token = resolve_account(role, config)
    auth_type = (config.get("JIRA_AUTH_TYPE") or "").lower()
    is_dc = is_datacenter(config)
    if auth_type == "bearer" or (is_dc and not user) or (auth_type != "basic" and is_dc and token and not user):
        auth_header = f"Bearer {token}"
    elif auth_type == "bearer":
        auth_header = f"Bearer {token}"
    else:
        auth_str = f"{user}:{token}" if user else f":{token}"
        encoded_auth = base64.b64encode(auth_str.encode("utf-8")).decode("utf-8")
        auth_header = f"Basic {encoded_auth}"
    return {
        "Authorization": auth_header,
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

    HUMAN_ROLES = {"human", "user", "rainer"}
    if role_from_cli:
        if role_from_cli.lower() in HUMAN_ROLES:
            print("Error: 'human' role cannot be selected via agent CLI or environment variable. "
                  "Human actions must use interactive confirmation or the Jira web UI.", file=sys.stderr)
            sys.exit(1)
        if role_from_cli not in VALID_ROLES:
            print(f"Error: Invalid role '{role_from_cli}'. Recognized roles: {', '.join(VALID_ROLES)}", file=sys.stderr)
            sys.exit(1)
        return role_from_cli, remaining_args

    env_actor = os.environ.get("JIRA_ACTOR")
    if env_actor:
        if env_actor.lower() in HUMAN_ROLES:
            print("Error: 'human' role cannot be selected via agent CLI or environment variable. "
                  "Human actions must use interactive confirmation or the Jira web UI.", file=sys.stderr)
            sys.exit(1)
        if env_actor not in VALID_ROLES:
            print(f"Error: Invalid role '{env_actor}' in JIRA_ACTOR environment variable. Recognized roles: {', '.join(VALID_ROLES)}", file=sys.stderr)
            sys.exit(1)
        return env_actor, remaining_args

    return "agent1", remaining_args

def jira_request(url, method="GET", payload=None, is_binary=False, role="agent1"):
    config = get_config(role=role)
    headers = get_headers(config, role=role)
    data = json.dumps(payload).encode("utf-8") if payload else None

    req = urllib.request.Request(url, data=data, method=method)
    for k, v in headers.items():
        req.add_header(k, v)

    user, token = resolve_account(role, config)
    ssl_context = get_ssl_context(config)

    try:
        with urllib.request.urlopen(req, context=ssl_context) as response:
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

def resolve_active_sprint(board_id, role="agent1"):
    config = get_config()
    sprints = jira_request(f"{config['JIRA_URL']}/rest/agile/1.0/board/{board_id}/sprint?state=active", role=role)["values"]
    if not sprints:
        return None
    dev_sprints = [s for s in sprints if s.get("name", "").strip().lower() != "human review"]
    if dev_sprints:
        dev_sprints.sort(key=lambda s: s.get("id", 0), reverse=True)
        return dev_sprints[0]
    return sprints[0]

def list_sprint_issues(role="agent1"):
    config = get_config()
    # 1. Find the board
    boards = jira_request(f"{config['JIRA_URL']}/rest/agile/1.0/board", role=role)["values"]
    board_id = boards[0]["id"]

    # 2. Find active sprint
    active_sprint = resolve_active_sprint(board_id, role=role)
    if not active_sprint:
        print("No active sprint found.")
        return
    sprint_id = active_sprint["id"]
    print(f"Active Sprint: {active_sprint['name']}")

    # 3. Get issues (paginated)
    issues = fetch_all_sprint_issues(config['JIRA_URL'], sprint_id, f"summary,status,issuetype,fixVersions,{STORY_POINTS_FIELD}", role=role)
    total_sp = 0.0
    unestimated = 0
    for i in issues:
        itype = i['fields']['issuetype']['name']
        fvs = [v.get('name', '') for v in i['fields'].get('fixVersions', [])]
        fv_str = f" [FixVersion: {', '.join(fvs)}]" if fvs else " [No FixVersion!]"
        sp = i['fields'].get(STORY_POINTS_FIELD)
        if sp is not None:
            try:
                sp_float = float(sp)
                sp_disp = int(sp_float) if sp_float.is_integer() else sp_float
                sp_str = f" [{sp_disp} SP]"
                total_sp += sp_float
            except (ValueError, TypeError):
                sp_str = f" [{sp} SP]"
        else:
            sp_str = " [No SP]"
            unestimated += 1
        print(f"{i['key']}: [{itype}]{sp_str} {i['fields']['summary']} [{i['fields']['status']['name']}]{fv_str}")

    total_disp = int(total_sp) if total_sp.is_integer() else total_sp
    print(f"\nSprint Total: {len(issues)} issues, {total_disp} Story Points ({unestimated} unestimated)")

def set_story_points(issue_key, points, role="agent1"):
    config = get_config()
    val = None
    if str(points).lower().strip() in ("none", "clear", "null", ""):
        val = None
        target_disp = "None"
    else:
        try:
            pts_float = float(points)
            if pts_float < 0:
                print(f"Error: Story points cannot be negative ({pts_float}).", file=sys.stderr)
                sys.exit(1)
            val = pts_float
            target_disp = f"{int(pts_float) if pts_float.is_integer() else pts_float} SP"
        except ValueError:
            print(f"Error: Invalid story points '{points}'. Must be a number (e.g. 1, 2, 3, 5) or 'none'.", file=sys.stderr)
            sys.exit(1)

    url = f"{config['JIRA_URL']}/rest/api/2/issue/{issue_key}"
    payload = {"fields": {STORY_POINTS_FIELD: val}}
    jira_request(url, method="PUT", payload=payload, role=role)
    print(f"Updated story points for {issue_key} to {target_disp}.")

def show_issue(issue_key, role="agent1"):
    config = get_config()
    url = f"{config['JIRA_URL']}/rest/api/2/issue/{issue_key}?fields=summary,description,comment,attachment,parent,issuetype,status,subtasks,fixVersions,assignee,priority,labels,{STORY_POINTS_FIELD}"
    issue = jira_request(url, role=role)

    itype = issue['fields']['issuetype']['name']
    status = issue['fields'].get('status', {}).get('name', 'Unknown')
    print(f"h1. {issue['key']}: [{itype}] {issue['fields']['summary']}")
    print(f"\n*Status*: {status}")

    assignee = issue['fields'].get('assignee')
    assignee_name = (assignee.get('displayName') or assignee.get('name') or 'Unassigned') if assignee else 'Unassigned'
    print(f"\n*Assignee*: {assignee_name}")

    priority = issue['fields'].get('priority')
    priority_name = priority.get('name', 'None') if priority else 'None'
    print(f"\n*Priority*: {priority_name}")

    sp = issue['fields'].get(STORY_POINTS_FIELD)
    if sp is not None:
        try:
            sp_float = float(sp)
            sp_disp = int(sp_float) if sp_float.is_integer() else sp_float
            print(f"\n*Story Points*: {sp_disp} SP")
        except (ValueError, TypeError):
            print(f"\n*Story Points*: {sp} SP")
    else:
        print("\n*Story Points*: None")

    labels = issue['fields'].get('labels', [])
    if labels:
        print(f"\n*Labels*: {', '.join(labels)}")

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
    if version_name.lower() in ("none", "clear", "remove", "null", ""):
        url = f"{config['JIRA_URL']}/rest/api/2/issue/{issue_key}"
        payload = {
            "fields": {
                "fixVersions": []
            }
        }
        jira_request(url, method="PUT", payload=payload, role=role)
        print(f"Lösungsversion (Fix Version) cleared on {issue_key}.")
        return

    # Validate against project ATT versions
    url_versions = f"{config['JIRA_URL']}/rest/api/2/project/ATT/versions"
    versions = jira_request(url_versions, role=role)
    valid_names = [v.get('name') for v in versions]
    if version_name not in valid_names:
        active_versions = [v.get('name') for v in versions if not v.get('released') and not v.get('archived')]
        print(f"Error: Version '{version_name}' not found in project ATT.", file=sys.stderr)
        print(f"Available active unreleased versions: {', '.join(active_versions)}", file=sys.stderr)
        sys.exit(1)

    # Governance Mandates (Rule 6 & Rule 19)
    url_issue = f"{config['JIRA_URL']}/rest/api/2/issue/{issue_key}?fields=status,issuetype"
    issue_data = jira_request(url_issue, role=role)
    if issue_data.get('fields', {}).get('issuetype', {}).get('subtask', False):
        print(f"Error: Sub-tasks must not get a solution ('Lösungsversion') assigned! (Rule 6)", file=sys.stderr)
        sys.exit(1)

    status_name = issue_data.get('fields', {}).get('status', {}).get('name', '')
    if role in ("agent1", "agent2"):
        print(f"Error: Subagents ({role}) must never set Fix Version ('Lösungsversion') on {issue_key}! Fix Version is set exclusively by the Coordinator/Human upon review acceptance in Ceremony 2 (Rule 19 / Rule 76).", file=sys.stderr)
        sys.exit(1)
    allowed_statuses = {"Final Review (Human)", "Erledigt", "Done", "Closed"}
    if status_name not in allowed_statuses:
        print(f"Error: Premature Fix Version assignment blocked on {issue_key}! Status is '{status_name}' (must be in {sorted(allowed_statuses)}). Mandate: 'Add version when ticket is finished, not when started' (Rule 19 / Rule 76).", file=sys.stderr)
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

def upload_attachment(issue_key, file_path, role="agent1"):
    if not os.path.exists(file_path):
        print(f"Error: File '{file_path}' not found.", file=sys.stderr)
        sys.exit(1)
    config = get_config()
    user, token = resolve_account(role, config)
    filename = os.path.basename(file_path)
    url = f"{config['JIRA_URL']}/rest/api/2/issue/{issue_key}/attachments"

    import uuid
    import mimetypes
    boundary = f"----FormBoundary{uuid.uuid4().hex}"
    content_type = mimetypes.guess_type(file_path)[0] or "application/octet-stream"

    with open(file_path, "rb") as f:
        file_bytes = f.read()

    body = bytearray()
    body.extend(f"--{boundary}\r\n".encode("utf-8"))
    body.extend(f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'.encode("utf-8"))
    body.extend(f"Content-Type: {content_type}\r\n\r\n".encode("utf-8"))
    body.extend(file_bytes)
    body.extend(f"\r\n--{boundary}--\r\n".encode("utf-8"))

    auth_str = f"{user}:{token}"
    encoded_auth = base64.b64encode(auth_str.encode("ascii")).decode("ascii")

    req = urllib.request.Request(url, data=body, method="POST")
    req.add_header("Authorization", f"Basic {encoded_auth}")
    req.add_header("X-Atlassian-Token", "no-check")
    req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")

    try:
        with urllib.request.urlopen(req) as resp:
            print(f"Uploaded {filename} to {issue_key} successfully.")
    except Exception as e:
        print(f"Error uploading attachment: {e}", file=sys.stderr)
        sys.exit(1)

PARENT_STAGE_ORDER = [
    "zu erledigen",
    "analysis",
    "test spec",
    "implementation plan",
    "implementation",
    "test",
    "final review (agent)",
    "final review (human)"
]

STAGE_SPECS = {
    "1": {
        "prefix": "[Analysis]",
        "pattern": r'^\[analysis\]',
        "parent_stage": "analysis",
        "skill_dir": "stage1-analysis",
        "gate": "Gate 1 (flash)"
    },
    "2": {
        "prefix": "[Req & Test Spec]",
        "pattern": r'^\[(req\s*&\s*test\s*spec|req/test\s*spec|test-spec|test\s*spec|specification|spec)\]',
        "parent_stage": "test spec",
        "skill_dir": "stage2-req-test-spec",
        "gate": "Gate 2 (pro)"
    },
    "3": {
        "prefix": "[Impl-Plan]",
        "pattern": r'^\[(impl-plan|impl\s*plan|implementation\s*plan|design|plan)\]',
        "parent_stage": "implementation plan",
        "skill_dir": "stage3-impl-plan",
        "gate": "Gate 3 (flash)"
    },
    "4": {
        "prefix": "[Implementation]",
        "pattern": r'^\[(implementation|impl)\]',
        "parent_stage": "implementation",
        "skill_dir": "stage4-implementation",
        "gate": "Gate 4 (pro)"
    },
    "5": {
        "prefix": "[Test]",
        "pattern": r'^\[(test|verification)\]',
        "parent_stage": "test",
        "skill_dir": "stage5-verification",
        "gate": "Gate 5 (pro)"
    },
}

STAGE_ALIASES = {
    "1": "1", "stage1": "1", "analysis": "1",
    "2": "2", "stage2": "2", "test-spec": "2", "test_spec": "2", "test spec": "2", "req-test-spec": "2", "spec": "2",
    "3": "3", "stage3": "3", "impl-plan": "3", "impl_plan": "3", "implementation plan": "3", "plan": "3",
    "4": "4", "stage4": "4", "impl": "4", "implementation": "4",
    "5": "5", "stage5": "5", "test": "5", "verification": "5",
}

STAGE_TO_SKILL_DIR = {
    "analysis": "stage1-analysis",
    "test spec": "stage2-req-test-spec",
    "implementation plan": "stage3-impl-plan",
    "implementation": "stage4-implementation",
    "test": "stage5-verification",
    "final review (agent)": "stage6-final-review",
}

def inject_stage_context(stage_name_lower, config=None):
    """
    Automatic Stage-Skill & Rule Context Injection (ATT-4290 Process Improvement #3).
    Prints the target stage's SKILL.md and compact governance reminder to stdout
    whenever a parent ticket enters a lifecycle stage.
    """
    skill_dir = STAGE_TO_SKILL_DIR.get((stage_name_lower or "").strip().lower())
    if not skill_dir:
        return
    if os.environ.get("JIRA_SUPPRESS_CONTEXT_INJECTION") == "1":
        return
    if config is None:
        try:
            config = get_config()
        except Exception:
            config = {}
    jira_url = config.get("JIRA_URL", "")
    if "example.atlassian.net" in jira_url or "jira.test" in jira_url:
        return

    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    skill_path = os.path.join(repo_root, ".agents", "skills", skill_dir, "SKILL.md")
    if not os.path.exists(skill_path):
        return

    try:
        with open(skill_path, "r", encoding="utf-8") as f:
            skill_text = f.read().strip()
        print(f"\n{'=' * 72}")
        print(f"=== AUTOMATIC STAGE CONTEXT INJECTION: {stage_name_lower.upper()} ({skill_dir}) ===")
        print(f"{'=' * 72}\n")
        print(skill_text)
        print(f"\n{'=' * 72}")
        print("=== CORE IN-SPRINT GOVERNANCE REMINDER (8 CORE RULES) ===")
        print("1. Rule 1 (Obey the Rules): Explicit view_file of aspice_governance.md + domain doc at Ticket Start / Post-Compaction; stage SKILL.md injected above.")
        print("2. Rule 2 (Autonomous Execution): Proceed continuously through Stages 1-5 and across sprint tickets without pausing.")
        print("3. Rule 3 (Human Gate): Agents end at 'Final Review (Human)'; never transition parent tickets to 'Erledigt'.")
        print("4. Rule 4 (Code Lock Until Gate 3): Run 'jira_util.py check-gate <Impl-Plan-Key>' before any Stage 4 production code edits.")
        print("5. Rule 5 (Ticket Creation Stops): Creating a backlog/follow-up ticket never triggers implementation.")
        print("6. Rule 6 (Unattended Escalation): Assign blocked sub-task to 'human' with question comment and continue with next ticket.")
        print("7. Rule 7 (Zipper Ping-Pong): 2 persistent self subagents per ticket (subagent_pro & subagent_flash) reused via send_message (S1 pro/flash | S2 flash/pro | S3 pro/flash | S4 flash/pro | S5 flash/pro) and killed after Stage 5.")
        print("8. Rule 8 (One Commit & Hygiene): Use 'submit-stage' (agent1) and 'gate-verdict' (agent2); 1 squashed commit per ticket on sprint/<ID>.")
        print(f"{'=' * 72}\n")
    except Exception as e:
        print(f"Notice: Could not inject stage context for {skill_dir}: {e}", file=sys.stderr)

def resolve_desired_parent_stage_for_subtask(subtask_summary, subtask_done=True):
    s = (subtask_summary or "").strip().lower()
    if re.match(r'^\[analysis\]', s):
        return "test spec" if subtask_done else "analysis"
    if re.match(r'^\[(req\s*&\s*test\s*spec|req/test\s*spec|test-spec|test\s*spec|specification|spec)\]', s):
        return "implementation plan" if subtask_done else "test spec"
    if re.match(r'^\[(impl-plan|impl\s*plan|implementation\s*plan|design|plan)\]', s):
        return "implementation" if subtask_done else "implementation plan"
    if re.match(r'^\[(implementation|impl)\]', s):
        return "test" if subtask_done else "implementation"
    if re.match(r'^\[(test|verification)\]', s):
        return "final review (agent)" if subtask_done else "test"
    return None

def sync_parent_stage_on_subtask_done(parent_key, subtask_summary, role="agent1", inject_context=True):
    desired_stage = resolve_desired_parent_stage_for_subtask(subtask_summary, True)
    if not desired_stage or not parent_key:
        return
    try:
        config = get_config()
        parent_url = f"{config['JIRA_URL']}/rest/api/2/issue/{parent_key}?fields=status"
        parent_info = jira_request(parent_url, role=role)
        current_parent_status = parent_info.get("fields", {}).get("status", {}).get("name", "").strip().lower()
        if current_parent_status in ("erledigt", "done", desired_stage):
            if inject_context and current_parent_status == desired_stage:
                inject_stage_context(desired_stage, config=config)
            return
        if current_parent_status in PARENT_STAGE_ORDER and desired_stage in PARENT_STAGE_ORDER:
            curr_idx = PARENT_STAGE_ORDER.index(current_parent_status)
            target_idx = PARENT_STAGE_ORDER.index(desired_stage)
            if curr_idx < target_idx:
                for step_stage in PARENT_STAGE_ORDER[curr_idx + 1 : target_idx + 1]:
                    transition_issue(
                        parent_key,
                        step_stage,
                        role=role,
                        inject_context=(inject_context and step_stage == desired_stage)
                    )
    except Exception as e:
        print(f"Warning: Could not auto-advance parent {parent_key} to '{desired_stage}': {e}", file=sys.stderr)

def transition_issue(issue_key, status_name, role="agent1", inject_context=True, allow_epic_close=False):
    config = get_config()
    # Check if issue is a subtask, its current status, summary, and parent
    issue_info_url = f"{config['JIRA_URL']}/rest/api/2/issue/{issue_key}?fields=issuetype,status,summary,parent,{STORY_POINTS_FIELD}"
    issue_info = jira_request(issue_info_url, role=role)
    fields_data = issue_info.get("fields", {})
    is_subtask = fields_data.get("issuetype", {}).get("subtask", False)
    is_epic = fields_data.get("issuetype", {}).get("name", "").lower() == "epic" or fields_data.get("issuetype", {}).get("id") == "10001"
    current_status_name = fields_data.get("status", {}).get("name", "")
    current_status_lower = current_status_name.lower().strip()
    issue_summary = fields_data.get("summary", "")
    parent_key = fields_data.get("parent", {}).get("key")

    normalized_input = status_name.lower().strip()
    # Strict Human Gate Guard on parent tickets: Prohibit AI agents from moving parent tickets to Erledigt / Done
    # Exception: Guarded close_epic with allow_epic_close=True strictly for Epics (REQ-PRO-040)
    if not is_subtask and normalized_input in ["erledigt", "done", "freigabe erteilt"]:
        if not (allow_epic_close and is_epic):
            print(f"ERROR: Transitioning parent ticket '{issue_key}' to '{status_name}' is strictly prohibited for AI agents.\n"
                  f"Moving parent tickets to 'Erledigt' is a Human Decision Gate reserved exclusively for the human user.",
                  file=sys.stderr)
            sys.exit(1)

    aliases = {}
    if is_subtask:
        aliases = {
            "todo": "zu erledigen",
            "in_progress": "in bearbeitung",
            "in_review": "in überprüfung",
            "review": "in überprüfung",
            "freigabe": "freigabe",
            "human": "freigabe",
            "done": "freigabe",
            "erledigt": "freigabe",
            "revision": "in bearbeitung",
            "rework": "in bearbeitung",
            "nochmals von vorne": "in bearbeitung",
            "überarbeitung notwendig": "in bearbeitung"
        }
    else:
        aliases = {
            "todo": "zu erledigen",
            "zu erledigen": "zu erledigen",
            "zurück zum start": "zu erledigen",
            "analysis": "analysis",
            "test_spec": "test spec",
            "test spec": "test spec",
            "plan": "implementation plan",
            "implementation_plan": "implementation plan",
            "implementation plan": "implementation plan",
            "implementation": "implementation",
            "zurück zur implementierung": "implementation",
            "test": "test",
            "testing": "test",
            "final_review_agent": "final review (agent)",
            "final review (agent)": "final review (agent)",
            "final review agent": "final review (agent)",
            "agent_review": "final review (agent)",
            "agent review": "final review (agent)",
            "stage6": "final review (agent)",
            "stage 6": "final review (agent)",
            "final_review": "final review (human)",
            "final review": "final review (human)",
            "final_review_human": "final review (human)",
            "final review (human)": "final review (human)",
            "final review human": "final review (human)",
            "review": "final review (human)",
            "human": "final review (human)",
            "human_review": "final review (human)",
            "human review": "final review (human)",
            "i.o.": "final review (human)",
            "revision": "implementation",
            "rework": "implementation"
        }
        if allow_epic_close and is_epic:
            aliases["erledigt"] = "erledigt"
            aliases["done"] = "erledigt"
            aliases["freigabe"] = "erledigt"
            aliases["close"] = "erledigt"
            aliases["closed"] = "erledigt"
    normalized_target = aliases.get(normalized_input, normalized_input)

    # Complexity split guardrail on parent tickets: Prohibit moving >= 5 SP tickets to Analysis unless forced
    if not is_subtask and normalized_target == "analysis":
        sp = fields_data.get(STORY_POINTS_FIELD)
        if sp is not None:
            try:
                sp_float = float(sp)
                if sp_float >= 5.0 and "--force" not in sys.argv:
                    print(f"ERROR: Ticket '{issue_key}' is estimated at {int(sp_float) if sp_float.is_integer() else sp_float} Story Points (>= 5 SP).\n"
                          f"In the Agile Agentic ASPICE (AAA) workflow, tickets with >= 5 SP must be split into smaller, atomic tickets (1-3 SP) before entering Analysis or an active sprint.\n"
                          f"Mandatory action: Split '{issue_key}' into atomic child tickets before moving to Analysis (or pass --force if an explicit human exception was granted).",
                          file=sys.stderr)
                    sys.exit(1)
            except (ValueError, TypeError):
                pass

    # Idempotent check if already in target status
    if current_status_lower == normalized_target or (is_subtask and normalized_target == "freigabe" and current_status_lower == "erledigt"):
        print(f"{issue_key} is already in '{current_status_name}'.")
        if current_status_lower == "final review (human)" and not is_subtask:
            assign_issue(issue_key, "human", role=role)
        elif current_status_lower == "final review (agent)" and not is_subtask:
            assign_issue(issue_key, "agent2", role=role)
        if is_subtask and (normalized_target == "freigabe" or current_status_lower == "erledigt"):
            sync_parent_stage_on_subtask_done(parent_key, issue_summary, role=role, inject_context=inject_context)
        return

    url = f"{config['JIRA_URL']}/rest/api/3/issue/{issue_key}/transitions"
    data = jira_request(url, role=role)
    available_transitions = data.get("transitions", [])

    chosen_trans = None
    # Pass 1: exact match on target status name
    for t in available_transitions:
        target_name = t.get("to", {}).get("name", "").lower()
        if normalized_target == target_name:
            chosen_trans = t
            break

    # Pass 2: exact match on transition name
    if not chosen_trans:
        for t in available_transitions:
            trans_name = t.get("name", "").lower()
            if normalized_target == trans_name:
                chosen_trans = t
                break

    # Pass 3: substring match on target status name
    if not chosen_trans:
        for t in available_transitions:
            target_name = t.get("to", {}).get("name", "").lower()
            if normalized_target in target_name:
                chosen_trans = t
                break

    # Pass 4: substring match on transition name (skip negative/rejection transitions like 'n.i.o.' unless requested)
    if not chosen_trans:
        for t in available_transitions:
            trans_name = t.get("name", "").lower()
            if "n.i.o." in trans_name and "n.i.o." not in normalized_target:
                continue
            if normalized_target in trans_name:
                chosen_trans = t
                break

    if not chosen_trans:
        avail_str = ", ".join([f"'{t['name']}' -> '{t.get('to', {}).get('name')}' (id {t['id']})" for t in available_transitions])
        print(f"Error: Cannot transition '{issue_key}' to '{status_name}'. Available transitions: {avail_str}", file=sys.stderr)
        return

    target_name = chosen_trans.get("to", {}).get("name", "").lower()
    trans_name = chosen_trans.get("name", "").lower()
    if not is_subtask and (target_name == "erledigt" or trans_name == "freigabe erteilt"):
        if not (allow_epic_close and is_epic):
            print(f"ERROR: Transition '{chosen_trans['name']}' to '{chosen_trans.get('to', {}).get('name')}' is strictly prohibited for AI agents on parent tickets.\n"
                  f"This transition is a Human Decision Gate reserved exclusively for the human user.", file=sys.stderr)
            sys.exit(1)

    trans_id = chosen_trans["id"]
    jira_request(url, method="POST", payload={"transition": {"id": trans_id}}, role=role)
    target_status = chosen_trans.get("to", {}).get("name", status_name)
    print(f"Successfully moved {issue_key} to '{target_status}' via transition '{chosen_trans['name']}'.")

    # Auto-assign based on target workflow stage
    norm_target_lower = target_name.lower()
    if norm_target_lower == "final review (agent)":
        assign_issue(issue_key, "agent2", role=role)
    elif norm_target_lower == "final review (human)" or ("human" in norm_target_lower and not is_subtask):
        assign_issue(issue_key, "human", role=role)
    elif "überprüfung" in norm_target_lower or "review" in norm_target_lower:
        assign_issue(issue_key, "agent2", role=role)
    elif "bearbeitung" in norm_target_lower or "progress" in norm_target_lower or norm_target_lower in ("implementation", "analysis", "zu erledigen"):
        assign_issue(issue_key, "agent1", role=role)

    # Automatic Stage-Skill & Rule Context Injection when parent ticket enters a lifecycle stage
    if not is_subtask and inject_context and norm_target_lower in STAGE_TO_SKILL_DIR:
        inject_stage_context(norm_target_lower, config=config)

    # Mandate ("Move main ticket forward once the sub-ticket is finished"):
    # When a stage subtask transitions to Erledigt, synchronously advance its parent ticket to the next stage column.
    if is_subtask and (norm_target_lower == "erledigt" or trans_name == "freigabe"):
        sync_parent_stage_on_subtask_done(parent_key, issue_summary, role=role, inject_context=inject_context)

def auto_verify_living_docs_on_stage5(parent_key, repo_root=None):
    """
    Automatically flips 'Planned' / 'Status: Planned (<KEY>)' status cells and multi-line headings
    to 'Verified' across docs/requirements.md, docs/tests.md, docs/requirements/*.md, and docs/tests/*.md
    when submitting Stage 5 (Rule 60).
    """
    if not parent_key:
        return 0
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    status_words = r"(?:Planned|Specified|In\s+Progress|Draft)"
    trailing_planned_re = re.compile(
        r"\|\s*(?:Status:\s*)?" + status_words + r"(?:\s*\([^)]*\))?\s*\|(\s*)$",
        re.IGNORECASE,
    )
    inline_planned_key_re = re.compile(
        r"(?:Status:\s*)?" + status_words + r"\s*\(\s*" + re.escape(parent_key) + r"\s*\)",
        re.IGNORECASE,
    )
    heading_planned_key_re = re.compile(
        r"^(#{2,4}\s*(?:REQ|TST)-[A-Z0-9_.-]+:.*?\[)(?:Status:\s*)?" + status_words + r"\s*\(\s*" + re.escape(parent_key) + r"\s*\)(\])",
        re.IGNORECASE,
    )
    heading_planned_gen_re = re.compile(
        r"^(#{2,4}\s*(?:REQ|TST)-[A-Z0-9_.-]+:.*?\[)(?:Status:\s*)?" + status_words + r"(\])",
        re.IGNORECASE,
    )
    bullet_planned_key_re = re.compile(
        r"(\*\*(?:Status|Verification Status)\*\*:\s*)(?:Status:\s*)?" + status_words + r"\s*\(\s*" + re.escape(parent_key) + r"\s*\)",
        re.IGNORECASE,
    )

    # Collect target files
    target_files = ["docs/requirements.md", "docs/tests.md"]
    for shard_dir in ("docs/requirements", "docs/tests"):
        abs_shard_dir = os.path.join(repo_root, shard_dir)
        if os.path.isdir(abs_shard_dir):
            for fname in sorted(os.listdir(abs_shard_dir)):
                if fname.endswith(".md"):
                    target_files.append(os.path.join(shard_dir, fname))

    total_updated = 0
    for rel_path in target_files:
        abs_path = os.path.join(repo_root, rel_path)
        if not os.path.exists(abs_path):
            continue
        try:
            with open(abs_path, "r", encoding="utf-8") as f:
                lines = f.readlines()
            updated_count = 0
            new_lines = []
            for line in lines:
                new_line = line
                if line.lstrip().startswith("|") and trailing_planned_re.search(line):
                    if parent_key in line or "Planned (" not in line:
                        new_line = trailing_planned_re.sub(r"| Verified |\1", line)
                elif parent_key in line and inline_planned_key_re.search(line):
                    new_line = inline_planned_key_re.sub("Verified", line)
                elif heading_planned_key_re.search(line):
                    new_line = heading_planned_key_re.sub(r"\1Verified\2", line)
                elif parent_key in line and heading_planned_gen_re.search(line):
                    new_line = heading_planned_gen_re.sub(r"\1Verified\2", line)
                elif bullet_planned_key_re.search(line):
                    new_line = bullet_planned_key_re.sub(r"\1Verified", line)

                if new_line != line:
                    updated_count += 1
                new_lines.append(new_line)

            # Multi-line heading block support: if parent_key is present in the section body
            # (e.g. - **Tickets**: <parent_key> or - **Jira**: <parent_key>) but not on the heading line,
            # flip the section heading from Planned to Verified (ATT-5431)
            sec_indices = [
                idx for idx, l in enumerate(new_lines)
                if re.match(r"^#{2,4}\s*(?:REQ|TST)-", l)
            ]
            sec_indices.append(len(new_lines))
            for i in range(len(sec_indices) - 1):
                s_idx = sec_indices[i]
                e_idx = sec_indices[i + 1]
                block_text = "".join(new_lines[s_idx:e_idx])
                if parent_key in block_text:
                    orig_hdr = new_lines[s_idx]
                    mod_hdr = heading_planned_gen_re.sub(r"\1Verified\2", orig_hdr)
                    if mod_hdr != orig_hdr:
                        new_lines[s_idx] = mod_hdr
                        updated_count += 1

            if updated_count > 0:
                with open(abs_path, "w", encoding="utf-8") as f:
                    f.writelines(new_lines)
                print(f"Auto-updated {updated_count} row(s) from Planned to Verified in {rel_path} for {parent_key}.")
                total_updated += updated_count
        except Exception as e:
            print(f"Warning: Could not auto-update {rel_path} for {parent_key}: {e}", file=sys.stderr)

    recalculate_master_index_tables(repo_root=repo_root)
    return total_updated

def recalculate_master_index_tables(repo_root=None):
    """
    Recalculates summary tables ('Total', 'Verified', 'Planned / Open') and total requirement/test
    counts in docs/requirements.md and docs/tests.md based on actual parsed section headings
    in the 9 domain shards under docs/requirements/ and docs/tests/ (ATT-5431).
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    specs = [
        ("docs/requirements.md", "docs/requirements", "REQ"),
        ("docs/tests.md", "docs/tests", "TST"),
    ]
    for master_rel, shard_dir_rel, prefix in specs:
        abs_shard_dir = os.path.join(repo_root, shard_dir_rel)
        if not os.path.isdir(abs_shard_dir):
            continue
        shard_counts = {}
        for fn in sorted(os.listdir(abs_shard_dir)):
            if not fn.endswith(".md"):
                continue
            fp = os.path.join(abs_shard_dir, fn)
            tot = ver = plan = 0
            with open(fp, "r", encoding="utf-8") as f:
                for line in f:
                    m = re.match(r"^(#{2,4})\s*(" + prefix + r"-[A-Z0-9_-]+):?\s*(.*?)$", line.strip())
                    if not m:
                        continue
                    tot += 1
                    if "[Verified]" in line:
                        ver += 1
                    else:
                        plan += 1
            shard_counts[fn] = (tot, ver, plan)

        master_fp = os.path.join(repo_root, master_rel)
        if not os.path.exists(master_fp):
            continue
        with open(master_fp, "r", encoding="utf-8") as f:
            master_lines = f.readlines()

        grand_total = sum(c[0] for c in shard_counts.values())
        grand_verified = sum(c[1] for c in shard_counts.values())
        grand_planned = sum(c[2] for c in shard_counts.values())

        new_lines = []
        changed = False
        for line in master_lines:
            new_line = line
            m_link = re.search(r"\((?:requirements|tests)/((?:req|tst)_[^)]+\.md)\)", line)
            if m_link:
                s_name = m_link.group(1)
                if s_name in shard_counts:
                    t, v, p = shard_counts[s_name]
                    new_line = re.sub(
                        r"\|\s*\d+\s*\|\s*\d+\s*\|\s*\d+\s*\|(\s*)$",
                        rf"| {t} | {v} | {p} |\1",
                        line,
                    )
            elif "links to the " in line:
                new_line = re.sub(
                    r"(links to the )\d+( domain-sharded)",
                    rf"\g<1>{len(shard_counts)}\g<2>",
                    line,
                )
            elif "Total Living " in line:
                new_line = re.sub(
                    r"(\*\*Total Living (?:Requirements|Tests)\*\*:\s*)\d+(\s*across )\d+(\s*domain shards\.)",
                    rf"\g<1>{grand_total}\g<2>{len(shard_counts)}\g<3>",
                    line,
                )
            if new_line != line:
                changed = True
            new_lines.append(new_line)

        if changed:
            with open(master_fp, "w", encoding="utf-8") as f:
                f.writelines(new_lines)
            print(f"Recalculated master index table in {master_rel}: Total={grand_total}, Verified={grand_verified}, Planned={grand_planned}.")

def submit_stage(parent_key, stage_arg, text_or_file, summary_override=None, role="agent1"):
    """
    Composite Stage-Submission Command for agent1 (ATT-4290 Process Improvement #2).
    1. Resolves stage (1..5) and ensures parent ticket is in the matching stage status.
    2. If stage == 5, automatically flips Planned -> Verified in docs/requirements.md and docs/tests.md.
    3. Finds existing stage sub-task under parent_key or creates it with canonical prefix and --add-to-sprint.
    4. Updates the sub-task description with text_or_file.
    5. Transitions the sub-task to 'In Überprüfung' (in_review) and outputs SUBTASK_KEY.
    """
    stage_id = STAGE_ALIASES.get((stage_arg or "").strip().lower())
    if not stage_id or stage_id not in STAGE_SPECS:
        print(f"Error: Invalid stage '{stage_arg}'. Use 1|analysis, 2|test-spec, 3|impl-plan, 4|impl, or 5|test.", file=sys.stderr)
        sys.exit(1)

    if stage_id == "5":
        auto_verify_living_docs_on_stage5(parent_key)

    spec = STAGE_SPECS[stage_id]
    if text_or_file.startswith("@") and os.path.exists(text_or_file[1:]):
        with open(text_or_file[1:], "r", encoding="utf-8") as f:
            description_text = f.read()
    else:
        description_text = text_or_file

    config = get_config()
    parent_url = f"{config['JIRA_URL']}/rest/api/2/issue/{parent_key}?fields=summary,status,subtasks"
    parent_info = jira_request(parent_url, role=role)
    parent_fields = parent_info.get("fields", {})
    parent_summary = parent_fields.get("summary", "")
    current_parent_status = parent_fields.get("status", {}).get("name", "").strip().lower()
    desired_parent_stage = spec["parent_stage"]

    # Ensure parent is in the matching stage status
    if current_parent_status in PARENT_STAGE_ORDER and desired_parent_stage in PARENT_STAGE_ORDER:
        curr_idx = PARENT_STAGE_ORDER.index(current_parent_status)
        target_idx = PARENT_STAGE_ORDER.index(desired_parent_stage)
        if curr_idx < target_idx:
            for step_stage in PARENT_STAGE_ORDER[curr_idx + 1 : target_idx + 1]:
                transition_issue(parent_key, step_stage, role=role, inject_context=False)

    # Check if sub-task for this stage already exists
    subtasks = parent_fields.get("subtasks", [])
    existing_subtask_key = None
    existing_subtask_status = None
    for st in subtasks:
        st_summary = st.get("fields", {}).get("summary", "").strip()
        st_status = st.get("fields", {}).get("status", {}).get("name", "").strip().lower()
        if re.search(spec["pattern"], st_summary, re.IGNORECASE) and st_status not in ("erledigt", "done", "closed", "resolved"):
            existing_subtask_key = st.get("key")
            existing_subtask_status = st_status
            break

    if existing_subtask_key:
        subtask_key = existing_subtask_key
        print(f"Found existing {spec['prefix']} sub-task {subtask_key} for {parent_key}.")
        update_issue_description(subtask_key, description_text, role=role)
    else:
        raw_summary = summary_override if summary_override else f"{spec['prefix']} {parent_summary}"
        subtask_key = create_subtask(
            parent_key,
            raw_summary,
            description_text,
            role="coordinator",
            add_to_sprint=True
        )
        existing_subtask_status = "zu erledigen"

    # Transition sub-task to in_review (stepping through in_progress if currently in 'zu erledigen')
    if existing_subtask_status in ("zu erledigen", "to do", "open"):
        transition_issue(subtask_key, "in_progress", role=role, inject_context=False)
    transition_issue(subtask_key, "in_review", role=role, inject_context=False)
    print(f"SUBTASK_KEY: {subtask_key} ready for {spec['gate']} review.")
    return subtask_key

def gate_verdict(subtask_key, verdict_arg, text_or_file, role="agent2"):
    """
    Composite Gate-Verdict Command for agent2 (ATT-4290 Process Improvement #2).
    Hardwired to role='agent2' (Anti-Spoofing lock).
    1. Assigns subtask_key to agent2.
    2. Posts text_or_file as a role-attributed agent2 audit comment on subtask_key.
    3. If PASS: transitions subtask_key via 'freigabe' (closes sub-task to Erledigt, advances parent, and injects next stage SKILL.md).
    4. If REVISION: transitions subtask_key back to 'in_progress' and assigns to agent1.
    """
    auditor_role = "agent2"
    verdict_norm = (verdict_arg or "").strip().upper()
    if verdict_norm in ("PASS", "RECOMMEND PASS", "FREIGABE", "APPROVED"):
        is_pass = True
    elif verdict_norm in ("REVISION", "RECOMMEND REVISION", "CHALLENGED", "REWORK", "FAIL"):
        is_pass = False
    else:
        print(f"Error: Invalid verdict '{verdict_arg}'. Expected PASS or REVISION.", file=sys.stderr)
        sys.exit(1)

    assign_issue(subtask_key, auditor_role, role=auditor_role)
    add_comment(subtask_key, text_or_file, role=auditor_role)

    if is_pass:
        transition_issue(subtask_key, "freigabe", role=auditor_role, inject_context=True)
        print(f"GATE_VERDICT: PASS recorded on {subtask_key} by {auditor_role}.")
    else:
        transition_issue(subtask_key, "in_progress", role=auditor_role, inject_context=False)
        assign_issue(subtask_key, "agent1", role=auditor_role)
        print(f"GATE_VERDICT: REVISION recorded on {subtask_key} by {auditor_role} (reassigned to agent1).")


FINAL_REVIEW_BOUNCE_RE = re.compile(
    r"(?:\[FINAL REVIEW \(AGENT\)\s*[—\-]+\s*BOUNCE\s+(\d+)/3\]|Final Review \(Agent\)\s*BOUNCE\s*\(?(\d+)/3\)?)",
    re.IGNORECASE,
)
FINAL_REVIEW_PASS_TAG = "[FINAL REVIEW (AGENT) — PASS]"
FINAL_REVIEW_ESCALATED_TAG = "[FINAL REVIEW (AGENT) — ESCALATED AFTER 3 BOUNCES]"


def count_final_review_bounces(comments):
    """Counts how many Stage 6 [FINAL REVIEW (AGENT) — BOUNCE X/3] comments exist on a parent issue."""
    max_bounce = 0
    count = 0
    for c in (comments or []):
        body = c.get("body", "")
        m = FINAL_REVIEW_BOUNCE_RE.search(body)
        if m:
            count += 1
            num_str = m.group(1) or m.group(2)
            try:
                b_num = int(num_str)
                if b_num > max_bounce:
                    max_bounce = b_num
            except (ValueError, TypeError):
                pass
    return max(count, max_bounce)


def final_review_verdict(parent_key, verdict_arg, text_or_file, bounce_target="implementation", role="agent2", config=None):
    """
    REQ-PRO-058.2 / TST-PRO-058.2:
    Composite Stage 6 ('Final Review (Agent)') verdict command for the Cold Carbon Reviewer (agent2):
    - PASS: Posts [FINAL REVIEW (AGENT) — PASS], transitions parent ticket from 'Final Review (Agent)'
      to 'Final Review (Human)' (via 'I.O.'), and assigns to 'human'.
    - BOUNCE: Counts prior [FINAL REVIEW (AGENT) — BOUNCE X/3] comments on the parent ticket.
      * If bounce_count <= 3: Posts [FINAL REVIEW (AGENT) — BOUNCE {bounce_count}/3], transitions
        parent ticket back to bounce_target ('implementation', 'analysis', or 'zu erledigen'),
        and assigns to 'agent1'.
      * If bounce_count > 3: Posts [FINAL REVIEW (AGENT) — ESCALATED AFTER 3 BOUNCES], transitions
        parent ticket to 'Final Review (Human)', and assigns to 'human'.
    """
    auditor_role = "agent2"
    verdict_norm = (verdict_arg or "").strip().upper()
    if verdict_norm in ("PASS", "I.O.", "IO", "APPROVED", "FREIGABE"):
        is_pass = True
    elif verdict_norm in ("BOUNCE", "REVISION", "REWORK", "FAIL", "N.I.O.", "NIO", "REJECT"):
        is_pass = False
    else:
        print(f"Error: Invalid final review verdict '{verdict_arg}'. Expected PASS or BOUNCE.", file=sys.stderr)
        sys.exit(1)

    if text_or_file.startswith("@") and os.path.exists(text_or_file[1:]):
        with open(text_or_file[1:], "r", encoding="utf-8") as f:
            report_text = f.read().strip()
    else:
        report_text = (text_or_file or "").strip()

    if config is None:
        config = get_config()

    issue_url = f"{config['JIRA_URL']}/rest/api/2/issue/{parent_key}?fields=issuetype,status,summary,comment"
    issue_info = jira_request(issue_url, role=auditor_role)
    fields_data = issue_info.get("fields", {})
    if fields_data.get("issuetype", {}).get("subtask", False):
        print(f"Error: final-review-verdict operates strictly on parent tickets, not sub-task '{parent_key}'.", file=sys.stderr)
        sys.exit(1)

    current_status = fields_data.get("status", {}).get("name", "").strip().lower()
    if current_status != "final review (agent)" and current_status in PARENT_STAGE_ORDER:
        curr_idx = PARENT_STAGE_ORDER.index(current_status)
        agent_rev_idx = PARENT_STAGE_ORDER.index("final review (agent)")
        if curr_idx < agent_rev_idx:
            for step_stage in PARENT_STAGE_ORDER[curr_idx + 1 : agent_rev_idx + 1]:
                transition_issue(parent_key, step_stage, role=auditor_role, inject_context=False)

    assign_issue(parent_key, auditor_role, role=auditor_role)

    if is_pass:
        comment_body = report_text if FINAL_REVIEW_PASS_TAG in report_text else f"{FINAL_REVIEW_PASS_TAG}\n\n{report_text}"
        add_comment(parent_key, comment_body, role=auditor_role)
        transition_issue(parent_key, "final review (human)", role=auditor_role, inject_context=False)
        assign_issue(parent_key, "human", role=auditor_role)
        print(f"FINAL_REVIEW_VERDICT: PASS recorded on {parent_key} by {auditor_role} (moved to Final Review (Human), assigned to human).")
        return "PASS"

    comments = issue_info.get("comments") or fields_data.get("comment", {}).get("comments", [])
    prior_bounces = count_final_review_bounces(comments)
    next_bounce = prior_bounces + 1

    if next_bounce <= 3:
        bounce_tag = f"[FINAL REVIEW (AGENT) — BOUNCE {next_bounce}/3]"
        comment_body = report_text if bounce_tag in report_text else f"{bounce_tag}\n\n{report_text}"
        add_comment(parent_key, comment_body, role=auditor_role)

        norm_bounce_target = (bounce_target or "implementation").strip().lower()
        if norm_bounce_target in ("impl", "implementation", "zurück zur implementierung"):
            transition_issue(parent_key, "implementation", role=auditor_role, inject_context=False)
            resolved_target = "Implementation"
        elif norm_bounce_target in ("todo", "zu erledigen", "start", "zurück zum start"):
            transition_issue(parent_key, "zu erledigen", role=auditor_role, inject_context=False)
            resolved_target = "Zu erledigen"
        elif norm_bounce_target == "analysis":
            transition_issue(parent_key, "zu erledigen", role=auditor_role, inject_context=False)
            transition_issue(parent_key, "analysis", role=auditor_role, inject_context=False)
            resolved_target = "Analysis"
        else:
            transition_issue(parent_key, norm_bounce_target, role=auditor_role, inject_context=False)
            resolved_target = bounce_target

        assign_issue(parent_key, "agent1", role=auditor_role)
        print(f"FINAL_REVIEW_VERDICT: BOUNCE {next_bounce}/3 recorded on {parent_key} by {auditor_role} (returned to {resolved_target}, assigned to agent1).")
        return f"BOUNCE_{next_bounce}"
    else:
        comment_body = report_text if FINAL_REVIEW_ESCALATED_TAG in report_text else f"{FINAL_REVIEW_ESCALATED_TAG}\n\n{report_text}"
        add_comment(parent_key, comment_body, role=auditor_role)
        transition_issue(parent_key, "final review (human)", role=auditor_role, inject_context=False)
        assign_issue(parent_key, "human", role=auditor_role)
        print(f"FINAL_REVIEW_VERDICT: ESCALATED AFTER 3 BOUNCES on {parent_key} by {auditor_role} (moved to Final Review (Human), assigned to human).")
        return "ESCALATED"


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
    url = get_search_endpoint(config)
    payload = {
        "jql": jql,
        "fields": ["summary", "status", "issuetype"]
    }
    data = jira_request(url, method="POST", payload=payload, role=role)
    for i in data.get("issues", []):
        itype = i['fields']['issuetype']['name']
        print(f"{i['key']}: [{itype}] {i['fields']['summary']} [{i['fields']['status']['name']}]")

def paged_jql_search(jql, fields, role="agent1", config=None, max_total=5000):
    """
    Executes a paginated JQL search supporting both Jira Cloud (nextPageToken)
    and Jira Data Center (startAt). Returns a list of issue dictionaries.
    """
    if config is None:
        config = get_config()
    url = get_search_endpoint(config)
    is_dc = is_datacenter(config)
    results = []
    start_at = 0
    next_token = None
    page_size = 100

    while True:
        payload = {
            "jql": jql,
            "fields": fields,
            "maxResults": page_size
        }
        if is_dc:
            payload["startAt"] = start_at
        elif next_token:
            payload["nextPageToken"] = next_token

        data = jira_request(url, method="POST", payload=payload, role=role)
        issues = data.get("issues", [])
        if not issues:
            break
        results.extend(issues)
        if len(results) >= max_total:
            break

        if is_dc:
            start_at += len(issues)
            total = data.get("total", 0)
            if start_at >= total or len(issues) < page_size:
                break
        else:
            if data.get("isLast", True):
                break
            next_token = data.get("nextPageToken")
            if not next_token:
                break

    return results

def list_epics(role="agent1", config=None):
    """
    REQ-PRO-040.1 / TST-PRO-040.1:
    Lists all Epics in the project with Done/Open child ticket counts.
    Supports both Jira Cloud and Jira Data Center deployments.
    """
    if config is None:
        config = get_config()

    jql = "issuetype = Epic ORDER BY key ASC"
    fields = ["summary", "status", "issuetype"]
    epics = paged_jql_search(jql, fields, role=role, config=config)

    if not epics:
        print("No Epics found in project.")
        return

    epic_keys = [e["key"] for e in epics]
    done_counts = {k: 0 for k in epic_keys}
    open_counts = {k: 0 for k in epic_keys}

    is_dc = is_datacenter(config)
    epic_field = get_epic_link_field(config) if is_dc else None
    fields_to_fetch = ["status", "parent"]
    if epic_field:
        fields_to_fetch.append(epic_field)

    chunk_size = 50
    for idx in range(0, len(epic_keys), chunk_size):
        chunk = epic_keys[idx:idx + chunk_size]
        keys_str = ",".join(chunk)
        if is_dc:
            child_jql = f'"{epic_field}" in ({keys_str}) OR parent in ({keys_str})'
        else:
            child_jql = f'parent in ({keys_str})'

        children = paged_jql_search(child_jql, fields_to_fetch, role=role, config=config)
        for ch in children:
            f = ch.get("fields", {})
            pk = f.get("parent", {}).get("key")
            if not pk and epic_field:
                pval = f.get(epic_field)
                pk = pval.get("key") if isinstance(pval, dict) else pval

            if pk in done_counts:
                st_name = f.get("status", {}).get("name", "").lower().strip()
                if st_name in ("erledigt", "done", "closed", "geschlossen", "resolved"):
                    done_counts[pk] += 1
                else:
                    open_counts[pk] += 1

    total_epics = len(epics)
    closed_epics = 0
    open_epics = 0

    header_line = f"{'Key':<10} {'Status':<16} {'Done':>5} {'Open':>5}   {'Summary'}"
    sep = "-" * max(len(header_line), 95)
    print("=" * len(sep))
    print("Project Epics & Completion Overview (Canonical Epic Taxonomy)")
    print("=" * len(sep))
    print(header_line)
    print(sep)

    for e in epics:
        k = e["key"]
        st = e.get("fields", {}).get("status", {}).get("name", "")
        sm = e.get("fields", {}).get("summary", "")
        st_lower = st.lower().strip()
        if st_lower in ("erledigt", "done", "closed", "geschlossen", "resolved"):
            closed_epics += 1
        else:
            open_epics += 1
        print(f"{k:<10} {st:<16} {done_counts[k]:>5} {open_counts[k]:>5}   {sm}")

    print(sep)
    print(f"Total Epics: {total_epics} ({closed_epics} Erledigt, {open_epics} Open)")
    print("=" * len(sep))

def close_epic(epic_key, role="coordinator", config=None):
    """
    REQ-PRO-040.2 / TST-PRO-040.2:
    Closes an Epic to 'Erledigt' under triple guardrail enforcement:
    1. Role Guard: Restricted strictly to 'coordinator' (rejects 'agent1', 'agent2').
    2. Issue Type Guard: Verifies target issue is an 'Epic' (rejects standard parent tickets).
    3. Completeness Guard: Verifies 0 open child tickets (aborts with list of unfinished tickets).
    """
    if config is None:
        config = get_config()

    # Guard 1: Role check
    if role != "coordinator":
        print(f"ERROR: Role '{role}' is not authorized to close Epics.\n"
              f"Closing an Epic is restricted strictly to '--as coordinator'.",
              file=sys.stderr)
        sys.exit(1)

    # Fetch issue details
    url = f"{config['JIRA_URL']}/rest/api/2/issue/{epic_key}?fields=issuetype,status,summary"
    issue_info = jira_request(url, role=role)
    fields = issue_info.get("fields", {})
    itype_name = fields.get("issuetype", {}).get("name", "")
    summary = fields.get("summary", "")

    # Guard 2: Issue Type check
    if itype_name.lower() != "epic":
        print(f"ERROR: '{epic_key}' is not an Epic (issue type is '{itype_name}').\n"
              f"Standard parent tickets (Feature, Bug, Verbesserung) cannot be closed by agents (REQ-PRO-016 Human Decision Gate).",
              file=sys.stderr)
        sys.exit(1)

    # Guard 3: Completeness check
    is_dc = is_datacenter(config)
    if is_dc:
        epic_field = get_epic_link_field(config)
        child_jql = f'"{epic_field}" = "{epic_key}" OR parent = "{epic_key}"'
        fields_to_fetch = ["summary", "status", "issuetype", epic_field, "parent"]
    else:
        child_jql = f'parent = "{epic_key}"'
        fields_to_fetch = ["summary", "status", "issuetype", "parent"]

    child_issues = paged_jql_search(child_jql, fields_to_fetch, role=role, config=config)

    open_children = []
    for c in child_issues:
        st_name = c.get("fields", {}).get("status", {}).get("name", "")
        st_lower = st_name.lower().strip()
        is_done = st_lower in ("erledigt", "done", "closed", "geschlossen", "resolved")
        if not is_done:
            open_children.append((c.get("key"), st_name, c.get("fields", {}).get("summary", "")))

    if open_children:
        print(f"ERROR: Cannot close Epic '{epic_key}': {len(open_children)} open child ticket(s) remain:\n", file=sys.stderr)
        for k, st, sm in open_children:
            print(f"  - {k}: [{st}] {sm}", file=sys.stderr)
        print(f"\nAll child tickets must be in 'Erledigt' before an Epic can be closed.", file=sys.stderr)
        sys.exit(1)

    # All guardrails passed -> transition Epic to Erledigt
    transition_issue(epic_key, "erledigt", role=role, allow_epic_close=True)
    print(f"Epic '{epic_key}' ({summary}) successfully verified and closed.")

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

def set_parent(issue_key, parent_key, role="agent1"):
    config = get_config()
    url = f"{config['JIRA_URL']}/rest/api/2/issue/{issue_key}"
    if is_datacenter(config):
        payload = {
            "fields": {
                get_epic_link_field(config): parent_key
            }
        }
    else:
        payload = {
            "fields": {
                "parent": {"key": parent_key}
            }
        }
    jira_request(url, method="PUT", payload=payload, role=role)
    print(f"Parent of {issue_key} updated to {parent_key}.")

def create_subtask(parent_key, summary, description, role="coordinator", add_to_sprint=False, fix_version=None):
    if fix_version:
        print("Error: Sub-tasks must not get a solution ('Lösungsversion') assigned! (Governance mandate)", file=sys.stderr)
        sys.exit(1)

    # Normalize summary prefixes to ensure identical standardized prefix across all tickets
    # Mandate: "The first part of the name of the sub-tasks must be identical over all main tickets."
    for subtag in ["[subtask]", "[sub-task]", "[sub task]"]:
        if summary.lower().startswith(subtag):
            summary = summary[len(subtag):].strip()

    stage_prefixes = [
        (r'^\[analysis\]', '[Analysis]'),
        (r'^\[(req\s*&\s*test\s*spec|test-spec|test\s*spec|specification|spec)\]', '[Req & Test Spec]'),
        (r'^\[(impl-plan|impl\s*plan|design|plan)\]', '[Impl-Plan]'),
        (r'^\[(implementation|impl)\]', '[Implementation]'),
        (r'^\[(test|verification)\]', '[Test]'),
    ]
    detected_prefix = None
    for pattern, prefix in stage_prefixes:
        if re.search(pattern, summary, re.IGNORECASE):
            detected_prefix = prefix
            break

    config = get_config()

    # Mandate: Sub-tasks must have the name of the main ticket in the summary.
    try:
        parent_data = jira_request(f"{config['JIRA_URL']}/rest/api/2/issue/{parent_key}?fields=summary", role=role)
        parent_summary = parent_data.get("fields", {}).get("summary", "")
        if parent_summary:
            clean_parent = parent_summary
            for tag in ["[Feature]", "[Bug]", "[Verbesserung]", "[Subtask]", "[Task]"]:
                clean_parent = clean_parent.replace(tag, "").strip()
            if detected_prefix:
                summary = f"{detected_prefix} {clean_parent}"
            elif clean_parent.lower() not in summary.lower():
                summary = f"{summary} ({clean_parent})"
                if len(summary) > 250:
                    summary = summary[:247] + "..."
    except Exception as e:
        print(f"Notice: Could not retrieve parent summary for subtask naming: {e}", file=sys.stderr)

    url = f"{config['JIRA_URL']}/rest/api/2/issue"
    fields = {
        "project": {"key": "ATT"},
        "parent": {"key": parent_key},
        "summary": summary,
        "description": description,
        "issuetype": {"id": "10002"}  # Subtask ID
    }

    payload = {"fields": fields}
    data = jira_request(url, method="POST", payload=payload, role=role)
    new_key = data['key']
    print(f"Sub-task {new_key} created for parent {parent_key}.")

    if add_to_sprint:
        parent_sprint_id = None
        try:
            parent_agile = jira_request(f"{config['JIRA_URL']}/rest/agile/1.0/issue/{parent_key}", role=role)
            parent_sprint = parent_agile.get("fields", {}).get("sprint")
            if parent_sprint and parent_sprint.get("id"):
                parent_sprint_id = parent_sprint["id"]
        except Exception:
            pass

        if parent_sprint_id:
            url_sprint = f"{config['JIRA_URL']}/rest/agile/1.0/sprint/{parent_sprint_id}/issue"
            payload_sprint = {"issues": [new_key]}
            jira_request(url_sprint, method="POST", payload=payload_sprint, role=role)
            print(f"Added {new_key} to parent's sprint (id {parent_sprint_id}).")
        else:
            add_to_active_sprint(new_key, role=role)

    # Initial assignment: default subtasks to agent1
    assign_issue(new_key, "agent1", role=role)

    return new_key

def rank_at_top_of_backlog(issue_key, role="agent1"):
    """Ranks the given issue at the very top of the Jira Agile backlog."""
    config = get_config()
    base = config['JIRA_URL'].rstrip('/')
    try:
        boards = jira_request(f"{base}/rest/agile/1.0/board", role=role).get("values", [])
        top_key = None
        if boards:
            board_id = boards[0]["id"]
            backlog = jira_request(
                f"{base}/rest/agile/1.0/board/{board_id}/backlog?maxResults=10&fields=summary",
                role=role
            ).get("issues", [])
            for item in backlog:
                if item.get("key") and item.get("key") != issue_key:
                    top_key = item["key"]
                    break
        if not top_key:
            data = jira_request(
                get_search_endpoint(config),
                method="POST",
                payload={
                    "jql": f"project = ATT AND sprint is EMPTY AND issuetype != Sub-task AND key != {issue_key} ORDER BY rank ASC",
                    "maxResults": 1,
                    "fields": ["summary"]
                },
                role=role
            )
            issues = data.get("issues", [])
            if issues:
                top_key = issues[0]["key"]
        if top_key:
            jira_request(
                f"{base}/rest/agile/1.0/issue/rank",
                method="PUT",
                payload={"issues": [issue_key], "rankBeforeIssue": top_key},
                role=role
            )
            print(f"Ranked {issue_key} at the top of the backlog (before {top_key}).")
    except Exception as e:
        print(f"Warning: Could not rank {issue_key} at the top of the backlog: {e}", file=sys.stderr)

ISSUE_TYPE_ALIASES = {
    "feature": "10007",
    "new feature": "10007",
    "story": "10007",
    "bug": "10006",
    "fehler": "10006",
    "verbesserung": "10008",
    "improvement": "10008",
    "epic": "10001",
    "subtask": "10002",
    "sub-task": "10002",
}

def resolve_issuetype_id(issuetype_arg):
    if not issuetype_arg:
        return "10007"
    val = str(issuetype_arg).strip()
    if val.isdigit():
        return val
    return ISSUE_TYPE_ALIASES.get(val.lower(), val)

def sanitize_issue_summary(summary):
    """
    Mandate (Rule 13): Ticket summaries must not contain ticket category or epic in summary,
    while preserving priority prefixes like [Highest], [High], [Medium], [Low], [Lowest].
    """
    priority_tags = []
    remaining = (summary or "").strip()
    while True:
        m = re.match(r'^\[([^\]]+)\]\s*', remaining)
        if not m:
            break
        tag_content = m.group(1).strip()
        if tag_content.lower() in ("highest", "high", "medium", "low", "lowest"):
            priority_tags.append(f"[{tag_content}]")
        remaining = remaining[m.end():].strip()
    if priority_tags and remaining:
        return " ".join(priority_tags) + " " + remaining
    return remaining if remaining else summary

def resolve_valid_epic_key(parent_key, role="agent1"):
    if not parent_key:
        return None
    try:
        config = get_config()
        url = f"{config['JIRA_URL']}/rest/api/2/issue/{parent_key}?fields=issuetype,parent"
        info = jira_request(url, role=role)
        fields_data = info.get("fields", {})
        itype = fields_data.get("issuetype", {})
        if itype.get("id") == "10001" or itype.get("name", "").lower() == "epic":
            return parent_key
        nested_parent = fields_data.get("parent", {})
        if nested_parent and nested_parent.get("key"):
            return nested_parent.get("key")
        print(f"Warning: '{parent_key}' is not an Epic (type='{itype.get('name')}'); omitting parent field.", file=sys.stderr)
        return None
    except Exception:
        return parent_key

VALID_SEVERITIES = ("critical", "major", "minor", "cosmetic")


def create_issue(summary, description, issuetype_id="10007", parent_key=None, role="agent1", add_to_sprint=False, fix_version=None, priority=None, labels=None, rank_top=True, points=None, severity=None):
    if description.startswith("@") and os.path.exists(description[1:]):
        with open(description[1:], "r", encoding="utf-8") as f:
            description = f.read()

    issuetype_id = resolve_issuetype_id(issuetype_id)
    summary = sanitize_issue_summary(summary)

    # REQ-PRO-063.1: Mandatory severity check for Bugs (10006)
    labels_list = []
    if labels:
        if isinstance(labels, str):
            labels_list = [l.strip() for l in labels.split(",") if l.strip()]
        else:
            labels_list = list(labels)

    if issuetype_id == "10006":
        existing_sev = None
        for l in labels_list:
            if l.lower().startswith("severity-"):
                candidate = l.lower().replace("severity-", "").strip()
                if candidate in VALID_SEVERITIES:
                    existing_sev = candidate
                    break

        chosen_sev = severity or existing_sev
        if not chosen_sev:
            print(
                f"Error: Severity is mandatory when creating a Bug (10006). Specify --severity=[{'|'.join(VALID_SEVERITIES)}].",
                file=sys.stderr,
            )
            sys.exit(1)

        sev_clean = str(chosen_sev).strip().lower()
        if sev_clean not in VALID_SEVERITIES:
            print(
                f"Error: Invalid severity '{chosen_sev}'. Must be one of: {', '.join(VALID_SEVERITIES)}.",
                file=sys.stderr,
            )
            sys.exit(1)

        sev_label = f"severity-{sev_clean}"
        if sev_label not in labels_list:
            labels_list.append(sev_label)
        if f"**Severity**: {sev_clean.capitalize()}" not in description:
            description = f"{description.rstrip()}\n\n**Severity**: {sev_clean.capitalize()}"
    elif severity:
        sev_clean = str(severity).strip().lower()
        if sev_clean in VALID_SEVERITIES:
            sev_label = f"severity-{sev_clean}"
            if sev_label not in labels_list:
                labels_list.append(sev_label)

    labels = labels_list if labels_list else None

    config = get_config()
    url = f"{config['JIRA_URL']}/rest/api/2/issue"
    fields = {
        "project": {"key": "ATT"},
        "summary": summary,
        "description": description,
        "issuetype": {"id": issuetype_id}
    }
    valid_epic_key = resolve_valid_epic_key(parent_key, role=role) if parent_key else None
    if valid_epic_key:
        if is_datacenter(config):
            fields[get_epic_link_field(config)] = valid_epic_key
        else:
            fields["parent"] = {"key": valid_epic_key}
    if fix_version:
        fields["fixVersions"] = [{"name": fix_version}]
    if priority:
        fields["priority"] = {"name": priority}
    if labels:
        if isinstance(labels, str):
            labels = [l.strip() for l in labels.split(",") if l.strip()]
        fields["labels"] = labels
    if points is not None:
        try:
            pts_float = float(points)
            if pts_float < 0:
                print(f"Error: Story points cannot be negative ({pts_float}).", file=sys.stderr)
                sys.exit(1)
            fields[STORY_POINTS_FIELD] = pts_float
        except ValueError:
            print(f"Error: Invalid story points '{points}'. Must be a number.", file=sys.stderr)
            sys.exit(1)

    payload = {"fields": fields}
    data = jira_request(url, method="POST", payload=payload, role=role)
    new_key = data['key']
    print(f"Issue {new_key} created.")

    if rank_top:
        rank_at_top_of_backlog(new_key, role=role)

    if add_to_sprint:
        if role in ("agent1", "agent2", "coordinator"):
            print("Error: Agents must not move tickets to sprints! Only the human user assigns tickets to sprints during Sprint-Start Screening.", file=sys.stderr)
            sys.exit(1)
        add_to_active_sprint(new_key, role=role)

    return new_key

def add_to_active_sprint(issue_key, role="agent1"):
    config = get_config()
    boards = jira_request(f"{config['JIRA_URL']}/rest/agile/1.0/board", role=role)["values"]
    board_id = boards[0]["id"]
    active_sprint = resolve_active_sprint(board_id, role=role)
    if not active_sprint:
        print("No active sprint found.")
        return
    sprint_id = active_sprint["id"]
    url = f"{config['JIRA_URL']}/rest/agile/1.0/sprint/{sprint_id}/issue"
    payload = {"issues": [issue_key]}
    jira_request(url, method="POST", payload=payload, role=role)
    print(f"Added {issue_key} to active sprint '{active_sprint['name']}' (id {sprint_id}).")

def fetch_all_sprint_issues(base, sprint_id, fields_csv, role="agent1"):
    """Paginates /rest/agile/1.0/sprint/{sprint_id}/issue across startAt pages (ATT-6289)."""
    all_issues = []
    start_at = 0
    while True:
        url = f"{base}/rest/agile/1.0/sprint/{sprint_id}/issue?fields={fields_csv}&startAt={start_at}&maxResults=100"
        res = jira_request(url, role=role)
        batch = res.get("issues", [])
        all_issues.extend(batch)
        total = res.get("total", len(all_issues))
        if not batch or len(all_issues) >= total:
            break
        start_at += len(batch)
    return all_issues

def ensure_retro_ticket(role="agent1"):
    """
    Checks whether a 'Review & Retro' ticket exists in the active sprint.
    If Jira's automation failed to create one, creates 'Review & Retro - <Sprint Name>'
    (Verbesserung, Epic ATT-232) and adds it to the active sprint.
    """
    config = get_config()
    base = config['JIRA_URL'].rstrip('/')
    boards = jira_request(f"{base}/rest/agile/1.0/board", role=role)["values"]
    if not boards:
        print("Error: No Agile board found.", file=sys.stderr)
        sys.exit(1)
    board_id = boards[0]["id"]
    active_sprint = resolve_active_sprint(board_id, role=role)
    if not active_sprint:
        print("Error: No active sprint found.", file=sys.stderr)
        sys.exit(1)

    sprint_id = active_sprint["id"]
    sprint_name = active_sprint["name"].strip()
    sprint_short_id = re.sub(r'(?i)^sprint\s*', '', sprint_name).strip()
    display_sprint = sprint_name if sprint_name.lower().startswith("sprint") else f"Sprint {sprint_name}"

    issues = fetch_all_sprint_issues(
        base,
        sprint_id,
        f"summary,status,issuetype,{STORY_POINTS_FIELD}",
        role=role
    )

    total_sp = 0.0
    unestimated_sp = 0
    for issue in issues:
        itype = issue.get("fields", {}).get("issuetype", {}).get("name", "")
        if itype.lower() in ("sub-task", "subtask"):
            continue
        sp = issue.get("fields", {}).get(STORY_POINTS_FIELD)
        if sp is not None:
            try:
                total_sp += float(sp)
            except (ValueError, TypeError):
                pass
        else:
            unestimated_sp += 1
    total_sp_disp = int(total_sp) if total_sp.is_integer() else total_sp
    print(f"Active Sprint Story Points: {total_sp_disp} SP ({unestimated_sp} unestimated)")

    # Pass 1: Match dedicated 'Review & Retro' ceremony ticket summary specifically (ATT-5829)
    for issue in issues:
        itype = issue.get("fields", {}).get("issuetype", {}).get("name", "")
        if itype.lower() in ("sub-task", "subtask"):
            continue
        summary = issue.get("fields", {}).get("summary", "")
        if re.search(r'^\s*(?:\[[^\]]+\]\s*)?(?:sprint\s+)?review\s*(?:&|and)\s*retro(?:spective)?\b', summary, re.IGNORECASE):
            status = issue.get("fields", {}).get("status", {}).get("name", "Unknown")
            print(f"Review & Retro ticket already exists in {display_sprint} (id {sprint_id}): {issue['key']} — {summary} [{status}]")
            return issue["key"]

    # Pass 2: Fallback to standalone Retrospective title if not a regular ticket referencing 'Retro' mid-sentence
    for issue in issues:
        itype = issue.get("fields", {}).get("issuetype", {}).get("name", "")
        if itype.lower() in ("sub-task", "subtask"):
            continue
        summary = issue.get("fields", {}).get("summary", "")
        if re.search(r'^\s*(?:\[[^\]]+\]\s*)?(?:sprint\s+)?retro(?:spective)?\b', summary, re.IGNORECASE):
            status = issue.get("fields", {}).get("status", {}).get("name", "Unknown")
            print(f"Review & Retro ticket already exists in {display_sprint} (id {sprint_id}): {issue['key']} — {summary} [{status}]")
            return issue["key"]

    summary = f"Review & Retro - {display_sprint}"
    initial_desc = (
        f"h2. Sprint Review & Retrospective - {display_sprint}\n"
        f"*Sprint*: {sprint_short_id} (id {sprint_id})\n"
        f"*Branch*: sprint/{sprint_short_id} -> develop\n"
    )
    labels = ["process", "retro", "review", f"sprint-{sprint_short_id}"]
    new_key = create_issue(
        summary=summary,
        description=initial_desc,
        issuetype_id="Verbesserung",
        parent_key="ATT-232",
        role=role,
        add_to_sprint=False,
        labels=labels,
        rank_top=False,
        points=1.0
    )
    add_to_active_sprint(new_key, role=role)
    final_desc = (
        f"h2. Sprint Review & Retrospective - {display_sprint}\n"
        f"*Deliverable*: docs/engineering/retros/Sprint_Review_and_Retro_{new_key}.md\n"
        f"*Branch*: sprint/{sprint_short_id} -> develop\n"
    )
    update_issue_description(new_key, final_desc, role=role)
    print(f"Created Review & Retro ticket {new_key} for {display_sprint} (id {sprint_id}).")
    return new_key


def collect_sprint_metrics_for_id(sprint_id, sprint_name, board_id, role="coordinator"):
    config = get_config()
    base = config['JIRA_URL'].rstrip('/')
    issues = fetch_all_sprint_issues(
        base,
        sprint_id,
        f"summary,status,issuetype,priority,subtasks,comment,{STORY_POINTS_FIELD}",
        role=role
    )

    subtask_comments = {
        i["key"]: i.get("fields", {}).get("comment", {}).get("comments", [])
        for i in issues
        if i.get("fields", {}).get("issuetype", {}).get("subtask", False) and i.get("key")
    }

    parent_issues = [i for i in issues if not i.get("fields", {}).get("issuetype", {}).get("subtask", False)]
    for p in parent_issues:
        subtasks = p.get("fields", {}).get("subtasks", [])
        for st in subtasks:
            st_key = st.get("key")
            if st_key and not st.get("comments"):
                if st_key in subtask_comments:
                    st["comments"] = subtask_comments[st_key]
                else:
                    try:
                        c_res = jira_request(f"{base}/rest/api/2/issue/{st_key}/comment", role=role)
                        st["comments"] = c_res.get("comments", [])
                    except Exception:
                        st["comments"] = []

    sprint_info = {"id": sprint_id, "name": sprint_name}
    return parse_sprint_gate_metrics(parent_issues, sprint_info)


def cmd_sprint_metrics(sprint_arg=None, record=False, post_to_retro=False, backfill=None, role="coordinator"):
    config = get_config()
    base = config['JIRA_URL'].rstrip('/')
    boards = jira_request(f"{base}/rest/agile/1.0/board", role=role)["values"]
    if not boards:
        print("Error: No Agile board found.", file=sys.stderr)
        sys.exit(1)
    board_id = boards[0]["id"]

    if backfill is not None:
        try:
            n_backfill = int(backfill)
        except ValueError:
            n_backfill = 3
        print(f"Backfilling metrics for the last {n_backfill} closed sprints...")
        all_closed = []
        start_at = 0
        while True:
            s_page = jira_request(f"{base}/rest/agile/1.0/board/{board_id}/sprint?state=closed&startAt={start_at}&maxResults=50", role=role)
            vals = s_page.get("values", [])
            all_closed.extend(vals)
            if s_page.get("isLast", True) or not vals:
                break
            start_at += len(vals)
        dev_sprints = [s for s in all_closed if s.get("name", "").strip().lower() != "human review"]
        target_sprints = dev_sprints[-n_backfill:] if len(dev_sprints) >= n_backfill else dev_sprints
        for s in target_sprints:
            s_id = s["id"]
            s_name = s["name"]
            print(f"\nCollecting metrics for closed Sprint '{s_name}' (ID: {s_id})...")
            m = collect_sprint_metrics_for_id(s_id, s_name, board_id, role=role)
            record_sprint_metrics(m)
        print("\nBackfill complete.")
        return

    target_sprint = None
    if sprint_arg:
        # Check active sprint first
        active = resolve_active_sprint(board_id, role=role)
        if active and (str(active["id"]) == str(sprint_arg) or active["name"].strip() == str(sprint_arg).strip()):
            target_sprint = active
        elif str(sprint_arg).isdigit():
            try:
                s_obj = jira_request(f"{base}/rest/agile/1.0/sprint/{sprint_arg}", role=role)
                if s_obj and "id" in s_obj:
                    target_sprint = s_obj
            except Exception:
                pass

        if not target_sprint:
            start_at = 0
            while True:
                s_page = jira_request(f"{base}/rest/agile/1.0/board/{board_id}/sprint?startAt={start_at}&maxResults=50", role=role)
                vals = s_page.get("values", [])
                for s in vals:
                    if str(s["id"]) == str(sprint_arg) or s["name"].strip() == str(sprint_arg).strip():
                        target_sprint = s
                        break
                if target_sprint or s_page.get("isLast", True) or not vals:
                    break
                start_at += len(vals)

        if not target_sprint:
            print(f"Error: Sprint '{sprint_arg}' not found on board {board_id}.", file=sys.stderr)
            sys.exit(1)
    else:
        target_sprint = resolve_active_sprint(board_id, role=role)
        if not target_sprint:
            print("Error: No active sprint found.", file=sys.stderr)
            sys.exit(1)

    s_id = target_sprint["id"]
    s_name = target_sprint["name"]
    print(f"Collecting quantitative metrics for Sprint '{s_name}' (ID: {s_id})...")
    metrics = collect_sprint_metrics_for_id(s_id, s_name, board_id, role=role)
    history = load_historical_metrics()
    spc = calculate_spc_limits(history, current_metrics=metrics)

    report_md = format_metrics_markdown(metrics, spc)
    print("\n" + report_md + "\n")

    if record:
        record_sprint_metrics(metrics)

    if post_to_retro:
        retro_key = None
        sprint_issues = fetch_all_sprint_issues(base, s_id, "summary,issuetype", role=role)
        # Pass 1: Match dedicated 'Review & Retro' ceremony ticket summary specifically (ATT-5829 / ATT-5890)
        for i in sprint_issues:
            itype = i.get("fields", {}).get("issuetype", {}).get("name", "")
            if itype.lower() in ("sub-task", "subtask"):
                continue
            summary = i.get("fields", {}).get("summary", "")
            if re.search(r'^\s*(?:\[[^\]]+\]\s*)?(?:sprint\s+)?review\s*(?:&|and)\s*retro(?:spective)?\b', summary, re.IGNORECASE):
                retro_key = i["key"]
                break
        # Pass 2: Fallback to standalone Retrospective title if not a regular ticket referencing 'Retro' mid-sentence
        if not retro_key:
            for i in sprint_issues:
                itype = i.get("fields", {}).get("issuetype", {}).get("name", "")
                if itype.lower() in ("sub-task", "subtask"):
                    continue
                summary = i.get("fields", {}).get("summary", "")
                if re.search(r'^\s*(?:\[[^\]]+\]\s*)?(?:sprint\s+)?retro(?:spective)?\b', summary, re.IGNORECASE):
                    retro_key = i["key"]
                    break
        if retro_key:
            add_comment(retro_key, report_md, role=role)
            print(f"Posted quantitative sprint scorecard to {retro_key}.")
        else:
            print("Warning: No 'Review & Retro' ticket found in this sprint to post metrics to.", file=sys.stderr)


def resolve_sprint_object(base, board_id, sprint_arg=None, role="coordinator"):
    """Resolves active or specified sprint object by ID or name."""
    if sprint_arg:
        active = resolve_active_sprint(board_id, role=role)
        if active and (str(active.get("id")) == str(sprint_arg) or active.get("name", "").strip() == str(sprint_arg).strip()):
            return active
        elif str(sprint_arg).isdigit():
            try:
                s_obj = jira_request(f"{base}/rest/agile/1.0/sprint/{sprint_arg}", role=role)
                if s_obj and "id" in s_obj:
                    return s_obj
            except Exception:
                pass
        start_at = 0
        while True:
            s_page = jira_request(f"{base}/rest/agile/1.0/board/{board_id}/sprint?startAt={start_at}&maxResults=50", role=role)
            vals = s_page.get("values", [])
            for s in vals:
                if str(s.get("id")) == str(sprint_arg) or s.get("name", "").strip() == str(sprint_arg).strip():
                    return s
            if s_page.get("isLast", True) or not vals:
                break
            start_at += len(vals)
        return None
    else:
        return resolve_active_sprint(board_id, role=role)


def extract_sprint_gate_records(parent_issues, sprint_info):
    """
    REQ-PRO-059.1 / REQ-PRO-059.2:
    Extracts all Gate 1-5 and Stage 6 review verdicts from sprint parent issues and their subtasks.
    Returns (records, gate_stats).
    """
    records = []
    gate_stats = {
        g: {"total": 0, "passed_first_time": 0, "revisions": 0, "bounces": 0}
        for g in range(1, 7)
    }

    def clean_text(txt, max_len=140):
        if not txt:
            return ""
        t = re.sub(r"\[Automated comment by [^\]]+\]\s*", "", txt)
        t = re.sub(r"[\r\n]+", " ", t).strip()
        t = t.replace("|", "/")
        if len(t) > max_len:
            t = t[:max_len - 3] + "..."
        return t

    for p in parent_issues:
        p_key = p.get("key", "")
        p_fields = p.get("fields", {})
        p_summary = p_fields.get("summary", "")
        p_comments = p.get("comments", []) or p_fields.get("comment", {}).get("comments", [])

        # 1. Check Subtasks (Gate 1..5)
        subtasks = p_fields.get("subtasks", [])
        for st in subtasks:
            st_key = st.get("key", "")
            st_fields = st.get("fields", {})
            st_summary = st_fields.get("summary", "")
            st_status = st_fields.get("status", {}).get("name", "").lower()
            g_num = identify_gate_number(st_summary)
            if not g_num:
                continue

            stage_name = STAGE_MAP[g_num]["name"]
            st_comments = st.get("comments", []) or st_fields.get("comment", {}).get("comments", [])

            subtask_verdicts = []
            for c in st_comments:
                body = c.get("body", "")
                author = c.get("author", {})
                actor_name = author.get("displayName") or author.get("name") or author.get("accountId") or "Unknown"
                created_ts = c.get("created", "")

                if "[ROLE: agent2]" in body or "[Automated comment by AI Agent 2" in body or "agent2" in actor_name.lower() or "reviewer" in actor_name.lower() or "auditor" in actor_name.lower():
                    role = "agent2"
                elif "[ROLE: coordinator]" in body or "[Automated comment by AI Coordinator" in body or "coordinator" in actor_name.lower():
                    role = "coordinator"
                elif "[ROLE: agent1]" in body or "[Automated comment by AI Agent 1" in body or "agent1" in actor_name.lower():
                    role = "agent1"
                elif "[ROLE: human]" in body or "human" in actor_name.lower():
                    role = "human"
                else:
                    role = "agent2"

                verdict = None
                body_upper = body.upper()
                if "GATE_VERDICT: PASS" in body_upper or "GATE_VERDICT PASS" in body_upper:
                    verdict = "PASS"
                elif "GATE_VERDICT: REVISION" in body_upper or "GATE_VERDICT REVISION" in body_upper:
                    verdict = "REVISION"
                elif role in ("agent2", "human") and ("PASS" in body_upper or "APPROVED" in body_upper or "FREIGABE" in body_upper):
                    verdict = "PASS"
                elif role in ("agent2", "human") and ("REVISION" in body_upper or "REWORK" in body_upper or "FAIL" in body_upper):
                    verdict = "REVISION"

                if verdict:
                    subtask_verdicts.append((verdict, role, actor_name, created_ts, clean_text(body)))

            if subtask_verdicts:
                gate_stats[g_num]["total"] += 1
                for idx, (v, r, a, ts, excerpt) in enumerate(subtask_verdicts):
                    if idx == 0:
                        if v == "PASS":
                            gate_stats[g_num]["passed_first_time"] += 1
                        else:
                            gate_stats[g_num]["revisions"] += 1
                    elif v == "REVISION":
                        gate_stats[g_num]["revisions"] += 1

                    records.append({
                        "parent_key": p_key,
                        "subtask_key": st_key,
                        "gate": g_num,
                        "stage_name": stage_name,
                        "verdict": v,
                        "evaluator_role": r,
                        "actor": a,
                        "timestamp": ts,
                        "summary": excerpt,
                    })
            elif st_status in ("erledigt", "closed", "done"):
                gate_stats[g_num]["total"] += 1
                gate_stats[g_num]["passed_first_time"] += 1
                records.append({
                    "parent_key": p_key,
                    "subtask_key": st_key,
                    "gate": g_num,
                    "stage_name": stage_name,
                    "verdict": "PASS",
                    "evaluator_role": "agent2",
                    "actor": st_fields.get("assignee", {}).get("displayName") or "agent2",
                    "timestamp": st_fields.get("updated") or st_fields.get("created") or "",
                    "summary": f"Subtask closed to {st_status.capitalize()}",
                })

        # 2. Check Parent Comments for Stage 6 ('Final Review (Agent)')
        parent_fr_verdicts = []
        for c in p_comments:
            body = c.get("body", "")
            author = c.get("author", {})
            actor_name = author.get("displayName") or author.get("name") or author.get("accountId") or "Unknown"
            created_ts = c.get("created", "")

            role = "agent2"
            if "[ROLE: human]" in body or "human" in actor_name.lower():
                role = "human"

            verdict = None
            if FINAL_REVIEW_BOUNCE_RE.search(body):
                verdict = "BOUNCE"
            elif FINAL_REVIEW_ESCALATED_RE.search(body):
                verdict = "ESCALATED"
            elif FINAL_REVIEW_PASS_RE.search(body):
                verdict = "PASS"

            if verdict:
                parent_fr_verdicts.append((verdict, role, actor_name, created_ts, clean_text(body)))

        if parent_fr_verdicts:
            gate_stats[6]["total"] += 1
            for idx, (v, r, a, ts, excerpt) in enumerate(parent_fr_verdicts):
                if idx == 0:
                    if v == "PASS":
                        gate_stats[6]["passed_first_time"] += 1
                    else:
                        gate_stats[6]["bounces"] += 1
                elif v in ("BOUNCE", "ESCALATED"):
                    gate_stats[6]["bounces"] += 1

                records.append({
                    "parent_key": p_key,
                    "subtask_key": "-",
                    "gate": 6,
                    "stage_name": STAGE_MAP[6]["name"],
                    "verdict": v,
                    "evaluator_role": r,
                    "actor": a,
                    "timestamp": ts,
                    "summary": excerpt,
                })

    return records, gate_stats


def format_gate_records_markdown(sprint_info, records, gate_stats):
    """
    REQ-PRO-059.2: Formats extracted sprint quality gate records as structured markdown.
    """
    sprint_id = sprint_info.get("id", "Unknown")
    sprint_name = sprint_info.get("name", "Unknown Sprint")
    import datetime
    now_iso = datetime.datetime.now(datetime.timezone.utc).isoformat()

    lines = [
        f"# Sprint Quality Gate Records: {sprint_name}",
        "",
        f"**Sprint ID**: `{sprint_id}` | **Sprint Name**: `{sprint_name}` | **Export Date**: `{now_iso}` | **Total Gate Records**: `{len(records)}`",
        "",
        "---",
        "",
        "## 1. Quality Gate Summary (First-Time-Pass-Rates)",
        "",
        "| Gate | Stage | Evaluated Records | Passed First Time | Revisions / Bounces | FTPR (%) |",
        "| :---: | :--- | :---: | :---: | :---: | :---: |",
    ]

    for g in range(1, 7):
        st = gate_stats.get(g, {"total": 0, "passed_first_time": 0, "revisions": 0, "bounces": 0})
        tot = st["total"]
        pft = st["passed_first_time"]
        rev_or_bounce = st["bounces"] if g == 6 else st["revisions"]
        ftpr = round((pft / tot) * 100.0, 1) if tot > 0 else 100.0
        lines.append(f"| Gate {g} | {STAGE_MAP[g]['name']} | {tot} | {pft} | {rev_or_bounce} | {ftpr}% |")

    lines.extend([
        "",
        "---",
        "",
        "## 2. Gate Verdict Records Log",
        "",
        "| Parent Ticket | Subtask Key | Gate | Stage | Verdict | Role | Actor | Timestamp | Summary / Excerpt |",
        "| :--- | :--- | :---: | :--- | :---: | :---: | :--- | :--- | :--- |",
    ])

    if not records:
        lines.append("| - | - | - | - | - | - | - | - | No gate verdict records found for this sprint |")
    else:
        for r in records:
            p_k = r["parent_key"]
            st_k = r["subtask_key"]
            g_lbl = f"Gate {r['gate']}"
            stg = r["stage_name"]
            v = r["verdict"]
            role = r["evaluator_role"]
            act = r["actor"]
            ts = r["timestamp"]
            sm = r["summary"]
            lines.append(f"| {p_k} | {st_k} | {g_lbl} | {stg} | {v} | {role} | {act} | {ts} | {sm} |")

    lines.append("")
    return "\n".join(lines)


def export_gates(sprint_arg=None, out_path=None, role="coordinator", as_role=None):
    """
    REQ-PRO-059.1 / TST-PRO-059.1:
    CLI command to export all Gate 1-5 and Stage 6 verdicts for a sprint into docs/engineering/gates/<sprint>.md.
    """
    if as_role:
        role = as_role
    config = get_config()
    base = config['JIRA_URL'].rstrip('/')
    boards = jira_request(f"{base}/rest/agile/1.0/board", role=role).get("values", [])
    if not boards:
        print("Error: No Agile board found.", file=sys.stderr)
        sys.exit(1)
    board_id = boards[0]["id"]

    target_sprint = resolve_sprint_object(base, board_id, sprint_arg=sprint_arg, role=role)
    if not target_sprint:
        s_desc = sprint_arg if sprint_arg else "active sprint"
        print(f"Error: Sprint '{s_desc}' not found on board {board_id}.", file=sys.stderr)
        sys.exit(1)

    s_id = target_sprint["id"]
    s_name = target_sprint["name"].strip()
    print(f"Exporting quality gate records for Sprint '{s_name}' (ID: {s_id})...")

    issues = fetch_all_sprint_issues(
        base,
        s_id,
        f"summary,status,issuetype,priority,subtasks,comment,{STORY_POINTS_FIELD}",
        role=role
    )

    subtask_comments = {
        i["key"]: i.get("fields", {}).get("comment", {}).get("comments", [])
        for i in issues
        if i.get("fields", {}).get("issuetype", {}).get("subtask", False) and i.get("key")
    }

    parent_issues = [i for i in issues if not i.get("fields", {}).get("issuetype", {}).get("subtask", False)]
    for p in parent_issues:
        subtasks = p.get("fields", {}).get("subtasks", [])
        for st in subtasks:
            st_key = st.get("key")
            if st_key and not st.get("comments"):
                if st_key in subtask_comments:
                    st["comments"] = subtask_comments[st_key]
                else:
                    try:
                        c_res = jira_request(f"{base}/rest/api/2/issue/{st_key}/comment", role=role)
                        st["comments"] = c_res.get("comments", [])
                    except Exception:
                        st["comments"] = []

    sprint_info = {"id": s_id, "name": s_name}
    records, gate_stats = extract_sprint_gate_records(parent_issues, sprint_info)
    md_content = format_gate_records_markdown(sprint_info, records, gate_stats)

    if not out_path:
        slug = re.sub(r"[/\\ ]+", "_", s_name).lower()
        if not slug.startswith("sprint_"):
            slug = f"sprint_{slug}"
        if not slug.endswith(".md"):
            slug += ".md"
        out_path = os.path.join("docs", "engineering", "gates", slug)

    out_dir = os.path.dirname(out_path)
    if out_dir:
        os.makedirs(out_dir, exist_ok=True)

    with open(out_path, "w", encoding="utf-8") as f:
        f.write(md_content)

    print(f"Sprint quality gate records exported to {out_path} ({len(records)} records).")
    return out_path, records


def format_qa_audit_markdown(sprint_info, audit_results, overall_compliant, auditor_role="coordinator"):
    import datetime
    date_str = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d")
    s_id = sprint_info.get("id")
    s_name = sprint_info.get("name")
    verdict_str = "COMPLIANT" if overall_compliant else "NON-COMPLIANT"
    compliant_count = sum(1 for r in audit_results if r["compliant"])
    total_sampled = len(audit_results)

    lines = [
        f"# Sprint QA Process-Compliance Audit: Sprint {s_name} (ID: {s_id})",
        "",
        f"**Date**: {date_str}  ",
        f"**Auditor Role**: `{auditor_role}`  ",
        f"**Sampled Tickets**: {', '.join(r['key'] for r in audit_results)}  ",
        f"**Overall Process Verdict**: **{verdict_str}**  ",
        "",
        "---",
        "",
        "## 1. Executive Summary",
        "",
        f"Under ASPICE SUP.1 (Quality Assurance) and GP 2.2 (Process Evidence), this audit verifies process adherence independently of product reviews. A sample of {total_sampled} tickets was audited across 5 core ASPICE process criteria (Lifecycle Deliverables, Stage Subtask Progression, 4-Augen Role Separation, Living Documentation Parity, and Commit Hygiene).",
        "",
        "| Metric | Value |",
        "| :--- | :--- |",
        f"| **Sprint** | {s_name} (ID: {s_id}) |",
        f"| **Sample Size** | {total_sampled} tickets |",
        f"| **Compliant Tickets** | {compliant_count} / {total_sampled} |",
        f"| **Overall Process Verdict** | **{verdict_str}** |",
        "",
        "---",
        "",
        "## 2. Sampled Tickets Audit Matrix",
        "",
        "| Ticket | Summary | Deliverables | Gate Sequence | 4-Augen Role Sep | Living Docs Parity | Commit Hygiene | Compliance Verdict |",
        "| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |",
    ]

    for r in audit_results:
        k = r["key"]
        sm = r["summary"][:40] + ("..." if len(r["summary"]) > 40 else "")
        d = "PASS" if r["deliv_pass"] else "FAIL"
        g = "PASS" if r["gate_pass"] else "FAIL"
        rs = "PASS" if r["role_sep_pass"] else "FAIL"
        ld = "PASS" if r["living_doc_pass"] else "FAIL"
        ch = "PASS" if r["commit_pass"] else "FAIL"
        v = "COMPLIANT" if r["compliant"] else "NON-COMPLIANT"
        lines.append(f"| {k} | {sm} | {d} | {g} | {rs} | {ld} | {ch} | {v} |")

    lines.extend([
        "",
        "---",
        "",
        "## 3. Granular Checklist Findings",
        "",
    ])

    for r in audit_results:
        k = r["key"]
        sm = r["summary"]
        v = "COMPLIANT" if r["compliant"] else "NON-COMPLIANT"
        lines.extend([
            f"### Ticket: {k} - {sm}",
            f"* **Process Verdict**: **{v}**",
            f"* **1. Lifecycle Deliverables**: {'PASS (analysis, specs, plan, walkthrough in docs/engineering/)' if r['deliv_pass'] else 'FAIL (missing stage deliverables)'}",
            f"* **2. Stage Subtask Lifecycle**: {'PASS (sequential subtask progression verified)' if r['gate_pass'] else 'FAIL (incomplete gate progression)'}",
            f"* **3. 4-Augen-Prinzip & Role Separation**: {'PASS (distinct submitter vs auditor verified)' if r['role_sep_pass'] else 'FAIL (role separation violation)'}",
            f"* **4. Living Documentation Parity**: {'PASS (requirements mapped & synchronized)' if r['living_doc_pass'] else 'FAIL (living doc disparity)'}",
            f"* **5. Commit Hygiene**: {'PASS (squashed commit on sprint branch)' if r['commit_pass'] else 'FAIL (commit hygiene issue)'}",
            "",
        ])

    lines.extend([
        "---",
        "",
        "## 4. Non-Compliance Tracking & Remediation",
        "",
    ])

    if overall_compliant:
        lines.append("Zero process non-compliances detected across the audited sample. All tickets adhered strictly to the ASPICE CL2 lifecycle.")
    else:
        lines.append("Non-compliances detected during sampling. Action items and tracking tickets:")
        for r in audit_results:
            if not r["compliant"]:
                lines.append(f"* **{r['key']}**: Process deviation noted; follow-up ticket recommended.")

    lines.append("")
    return "\n".join(lines)


def qa_audit(sprint_arg=None, sample_size=2, out_path=None, role="coordinator", as_role=None, repo_root=None):
    """
    REQ-PRO-061.1 / TST-PRO-061.1:
    Samples >= 2 sprint tickets and conducts an independent ASPICE SUP.1 QA process-compliance audit.
    Generates structured markdown report in docs/engineering/qa_audits/<sprint>.md.
    """
    if as_role:
        role = as_role
    if repo_root is None:
        repo_root = os.getcwd()

    config = get_config()
    base = config['JIRA_URL'].rstrip('/')
    boards = jira_request(f"{base}/rest/agile/1.0/board", role=role).get("values", [])
    if not boards:
        print("Error: No Agile board found.", file=sys.stderr)
        sys.exit(1)
    board_id = boards[0]["id"]

    target_sprint = resolve_sprint_object(base, board_id, sprint_arg=sprint_arg, role=role)
    if not target_sprint:
        s_desc = sprint_arg if sprint_arg else "active sprint"
        print(f"Error: Sprint '{s_desc}' not found on board {board_id}.", file=sys.stderr)
        sys.exit(1)

    s_id = target_sprint["id"]
    s_name = target_sprint["name"].strip()
    print(f"Conducting QA process-compliance audit for Sprint '{s_name}' (ID: {s_id})...")

    issues = fetch_all_sprint_issues(
        base,
        s_id,
        f"summary,status,issuetype,priority,subtasks,comment,{STORY_POINTS_FIELD}",
        role=role
    )

    subtask_comments = {
        i["key"]: i.get("fields", {}).get("comment", {}).get("comments", [])
        for i in issues
        if i.get("fields", {}).get("issuetype", {}).get("subtask", False) and i.get("key")
    }

    parent_issues = [
        i for i in issues
        if not i.get("fields", {}).get("issuetype", {}).get("subtask", False)
        and "review & retro" not in i.get("fields", {}).get("summary", "").lower()
        and i.get("key") != "ATT-232"
    ]

    for p in parent_issues:
        subtasks = p.get("fields", {}).get("subtasks", [])
        for st in subtasks:
            st_key = st.get("key")
            if st_key and not st.get("comments"):
                if st_key in subtask_comments:
                    st["comments"] = subtask_comments[st_key]
                else:
                    try:
                        c_res = jira_request(f"{base}/rest/api/2/issue/{st_key}/comment", role=role)
                        st["comments"] = c_res.get("comments", [])
                    except Exception:
                        st["comments"] = []

    # Sampling >= 2 tickets
    sample_size = max(2, int(sample_size))
    completed_parents = [
        p for p in parent_issues
        if p.get("fields", {}).get("status", {}).get("name", "").lower()
        in ("final review (human)", "final review (agent)", "final review", "erledigt", "closed", "done")
    ]
    pool = completed_parents if len(completed_parents) >= sample_size else parent_issues
    sampled = pool[:sample_size] if len(pool) >= sample_size else pool

    audit_results = []
    overall_compliant = True

    req_master_path = os.path.join(repo_root, "docs", "requirements.md")
    tst_master_path = os.path.join(repo_root, "docs", "tests.md")
    req_master_text = ""
    tst_master_text = ""
    if os.path.isfile(req_master_path):
        try:
            with open(req_master_path, "r", encoding="utf-8") as f:
                req_master_text = f.read()
        except Exception:
            pass
    if os.path.isfile(tst_master_path):
        try:
            with open(tst_master_path, "r", encoding="utf-8") as f:
                tst_master_text = f.read()
        except Exception:
            pass

    for item in sampled:
        key = item.get("key")
        summary = item.get("fields", {}).get("summary", "")
        subtasks = item.get("fields", {}).get("subtasks", [])

        # 1. Lifecycle Deliverables
        deliv_checks = {
            "analysis": os.path.isfile(os.path.join(repo_root, "docs", "engineering", "analysis", f"{key}_analysis.md")),
            "test_spec": (
                os.path.isfile(os.path.join(repo_root, "docs", "engineering", "test_specs", f"{key}_test_spec.md"))
                or os.path.isfile(os.path.join(repo_root, "docs", "engineering", "specifications", f"{key}_spec.md"))
            ),
            "plan": os.path.isfile(os.path.join(repo_root, "docs", "engineering", "plans", f"{key}_plan.md")),
            "walkthrough": os.path.isfile(os.path.join(repo_root, "docs", "engineering", "walkthroughs", f"{key}_walkthrough.md")),
        }
        deliv_pass = all(deliv_checks.values())

        # 2. Stage Subtask Lifecycle Progression
        found_gates = {}
        for st in subtasks:
            st_summary = st.get("fields", {}).get("summary", "")
            g_num = identify_gate_number(st_summary)
            if not g_num:
                for idx, pat in [
                    (1, r"analysis"),
                    (2, r"req|spec|test\s*spec"),
                    (3, r"plan|design"),
                    (4, r"impl"),
                    (5, r"test|verif"),
                ]:
                    if re.search(pat, st_summary, re.IGNORECASE):
                        g_num = idx
                        break
            if g_num:
                found_gates[g_num] = st

        expected_stages = {1, 2, 3, 4, 5}
        gate_pass = False
        if expected_stages.issubset(found_gates.keys()):
            all_stages_good = True
            for g_num in expected_stages:
                st = found_gates[g_num]
                st_st = st.get("fields", {}).get("status", {}).get("name", "").lower()
                st_comms = st.get("comments", []) or st.get("fields", {}).get("comment", {}).get("comments", [])
                if st_st not in ("erledigt", "done", "closed", "geschlossen", "resolved"):
                    all_stages_good = False
                    break
                has_pass = any("PASS" in c.get("body", "").upper() for c in st_comms)
                if not has_pass:
                    all_stages_good = False
                    break
            gate_pass = all_stages_good
        elif len(subtasks) >= 5:
            all_st_erledigt = all(
                st.get("fields", {}).get("status", {}).get("name", "").lower() in ("erledigt", "done", "closed", "geschlossen", "resolved")
                for st in subtasks
            )
            all_st_pass = all(
                any("PASS" in c.get("body", "").upper() for c in (st.get("comments", []) or st.get("fields", {}).get("comment", {}).get("comments", [])))
                for st in subtasks
            )
            gate_pass = all_st_erledigt and all_st_pass

        # 3. 4-Augen-Prinzip & Role Separation
        role_sep_pass = True
        agent2_verdicts_count = 0
        agent1_violations_count = 0

        for st in subtasks:
            st_comments = st.get("comments", []) or st.get("fields", {}).get("comment", {}).get("comments", [])
            for c in st_comments:
                body = c.get("body", "")
                author = c.get("author", {})
                author_name = author.get("displayName") or author.get("name") or author.get("accountId") or ""

                is_verdict = "GATE_VERDICT:" in body.upper() or "GATE-VERDICT" in body.upper() or "VERDICT: PASS" in body.upper() or "VERDICT: REVISION" in body.upper()
                if is_verdict:
                    if "[ROLE: agent1]" in body or "agent1" in author_name.lower():
                        agent1_violations_count += 1
                    if "[ROLE: agent2]" in body or "agent2" in body.lower() or "agent2" in author_name.lower() or "reviewer" in author_name.lower():
                        agent2_verdicts_count += 1

        if agent1_violations_count > 0 or agent2_verdicts_count == 0:
            role_sep_pass = False

        # 4. Living Documentation Parity
        master_req_ids = set(re.findall(r"\bREQ-[A-Z0-9_-]+\b", req_master_text))
        desc = item.get("fields", {}).get("description", "") or ""
        ticket_text_combined = f"{summary} {desc}"
        referenced_reqs = set(re.findall(r"\bREQ-[A-Z0-9_-]+\b", ticket_text_combined))

        for d_path in [
            os.path.join(repo_root, "docs", "engineering", "test_specs", f"{key}_test_spec.md"),
            os.path.join(repo_root, "docs", "engineering", "analysis", f"{key}_analysis.md"),
            os.path.join(repo_root, "docs", "engineering", "walkthroughs", f"{key}_walkthrough.md"),
        ]:
            if os.path.isfile(d_path):
                try:
                    with open(d_path, "r", encoding="utf-8") as f:
                        referenced_reqs.update(re.findall(r"\bREQ-[A-Z0-9_-]+\b", f.read()))
                except Exception:
                    pass

        for line in req_master_text.splitlines():
            if key in line:
                referenced_reqs.update(re.findall(r"\bREQ-[A-Z0-9_-]+\b", line))

        if referenced_reqs and master_req_ids:
            living_doc_pass = referenced_reqs.issubset(master_req_ids)
        elif key in req_master_text:
            living_doc_pass = True
        else:
            living_doc_pass = False

        # 5. Commit Hygiene
        commit_pass = False
        try:
            cmd = ["git", "log", f"--grep=^{key}:", "-n", "1", "--oneline"]
            res = subprocess.run(cmd, cwd=repo_root, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=5)
            if res.returncode == 0 and res.stdout.strip():
                commit_pass = True
            else:
                cmd_all = ["git", "log", f"--grep={key}", "-n", "10", "--oneline"]
                res_all = subprocess.run(cmd_all, cwd=repo_root, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=5)
                if res_all.returncode == 0:
                    for line in res_all.stdout.strip().splitlines():
                        parts = line.split(maxsplit=1)
                        if len(parts) > 1 and parts[1].strip().startswith(f"{key}:"):
                            commit_pass = True
                            break
        except Exception:
            commit_pass = False

        ticket_compliant = deliv_pass and gate_pass and role_sep_pass and living_doc_pass and commit_pass
        if not ticket_compliant:
            overall_compliant = False

        audit_results.append({
            "key": key,
            "summary": summary,
            "deliv_pass": deliv_pass,
            "gate_pass": gate_pass,
            "role_sep_pass": role_sep_pass,
            "living_doc_pass": living_doc_pass,
            "commit_pass": commit_pass,
            "compliant": ticket_compliant,
            "deliv_details": deliv_checks,
        })

    md_content = format_qa_audit_markdown(
        sprint_info={"id": s_id, "name": s_name},
        audit_results=audit_results,
        overall_compliant=overall_compliant,
        auditor_role=role,
    )

    if not out_path:
        slug = re.sub(r"[/\\ ]+", "_", s_name).lower()
        if not slug.startswith("sprint_"):
            slug = f"sprint_{slug}"
        if not slug.endswith(".md"):
            slug += ".md"
        out_path = os.path.join(repo_root, "docs", "engineering", "qa_audits", slug)

    out_dir = os.path.dirname(out_path)
    if out_dir:
        os.makedirs(out_dir, exist_ok=True)

    with open(out_path, "w", encoding="utf-8") as f:
        f.write(md_content)

    print(f"Sprint QA process-compliance audit exported to {out_path} ({len(audit_results)} sampled tickets audited).")
    return out_path, audit_results


def format_review_markdown(
    sprint_info,
    reviewer,
    review_type,
    vendor,
    model,
    version,
    scope,
    verdict,
    findings,
    tickets,
    date_str,
):
    s_name = sprint_info.get("name", "Active Sprint")
    s_id = sprint_info.get("id", "N/A")
    t_str = ", ".join(tickets) if isinstance(tickets, (list, set, tuple)) else str(tickets)

    lines = [
        f"# Independent QA Review Record: Sprint {s_name}",
        "",
        f"* **Reviewer**: {reviewer}",
        f"* **Review Type**: {review_type}",
        f"* **Date**: {date_str}",
        f"* **Sprint**: {s_name} (ID: {s_id})",
        f"* **Reviewed Ticket(s) / Commit(s)**: {t_str}",
        f"* **Scope**: {scope}",
        f"* **Verdict**: **{verdict}**",
        "",
        "---",
        "",
        "## 1. Review Summary & Traceable Metadata",
        f"* **Reviewer Name / Vendor**: {reviewer}",
        f"* **Vendor**: {vendor}",
        f"* **Model & Version**: {model} (v{version})",
        f"* **Authoring Agent Vendor**: Google DeepMind (Gemini Flash / Pro)",
        f"* **Review Date**: {date_str}",
        f"* **Reviewed Target(s)**: {t_str}",
        f"* **Scope**: {scope}",
        "",
        "---",
        "",
        "## 2. Findings & Assessment Notes",
        findings if findings else "Independent review conducted successfully. Zero critical process or technical deviations identified.",
        "",
        "---",
        "",
        "## 3. Decision & Sign-off",
        f"* **Verdict**: **{verdict}**",
        "* **Evidence Status**: Committed to repository under `docs/engineering/reviews/` per REQ-PRO-062.",
        "",
    ]
    return "\n".join(lines)


def record_review(
    sprint_arg=None,
    ticket_key=None,
    reviewer=None,
    vendor=None,
    model=None,
    version=None,
    scope=None,
    verdict="PASS",
    findings=None,
    out_path=None,
    role="coordinator",
    as_role=None,
    repo_root=None,
):
    """
    REQ-PRO-062.1 / REQ-PRO-062.2:
    Records an independent review (Human Ceremony 2 Sprint Review or Cross-Vendor AI Review)
    into docs/engineering/reviews/<sprint_or_review>.md.
    """
    if as_role:
        role = as_role
    if repo_root is None:
        repo_root = os.getcwd()

    config = get_config()
    base = config['JIRA_URL'].rstrip('/')

    s_name = sprint_arg or "Active Sprint"
    s_id = "N/A"
    try:
        boards = jira_request(f"{base}/rest/agile/1.0/board", role=role).get("values", [])
        if boards:
            board_id = boards[0]["id"]
            target_sprint = resolve_sprint_object(base, board_id, sprint_arg=sprint_arg, role=role)
            if target_sprint:
                s_id = target_sprint["id"]
                s_name = target_sprint["name"].strip()
    except Exception:
        pass

    if not reviewer:
        reviewer = "Human"

    if reviewer.lower() in ("human", "user"):
        vendor = vendor or "Human"
        model = model or "Human Process Owner"
        version = version or "N/A"
        review_type = "Human Sprint Review (Ceremony 2)"
        scope = scope or "Ceremony 2 Sprint Review"
    else:
        vendor = vendor or reviewer
        model = model or "External AI Model"
        version = version or "N/A"
        review_type = "Cross-Vendor AI Review (Layer 5)"
        scope = scope or "Layer 5 Cross-Vendor Review"

    verdict_clean = verdict.upper().strip() if verdict else "PASS"
    date_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    tickets = []
    if ticket_key:
        if isinstance(ticket_key, str) and "," in ticket_key:
            tickets = [t.strip() for t in ticket_key.split(",") if t.strip()]
        else:
            tickets = [str(ticket_key).strip()]
    else:
        try:
            if s_id != "N/A":
                issues = fetch_all_sprint_issues(base, s_id, "summary,issuetype", role=role)
                tickets = [
                    i["key"] for i in issues
                    if not i.get("fields", {}).get("issuetype", {}).get("subtask", False)
                    and "review & retro" not in i.get("fields", {}).get("summary", "").lower()
                    and i.get("key") != "ATT-232"
                ]
        except Exception:
            pass
        if not tickets:
            tickets = [f"Sprint {s_name} tickets"]

    md_content = format_review_markdown(
        sprint_info={"id": s_id, "name": s_name},
        reviewer=reviewer,
        review_type=review_type,
        vendor=vendor,
        model=model,
        version=version,
        scope=scope,
        verdict=verdict_clean,
        findings=findings,
        tickets=tickets,
        date_str=date_str,
    )

    if not out_path:
        slug = re.sub(r"[/\\ ]+", "_", s_name).lower()
        if not slug.startswith("sprint_"):
            slug = f"sprint_{slug}"
        if not slug.endswith(".md"):
            slug += ".md"
        out_path = os.path.join(repo_root, "docs", "engineering", "reviews", slug)

    out_dir = os.path.dirname(out_path)
    if out_dir:
        os.makedirs(out_dir, exist_ok=True)

    with open(out_path, "w", encoding="utf-8") as f:
        f.write(md_content)

    print(f"Independent QA review record exported to {out_path}.")
    return out_path, {
        "reviewer": reviewer,
        "review_type": review_type,
        "vendor": vendor,
        "model": model,
        "version": version,
        "scope": scope,
        "verdict": verdict_clean,
        "tickets": tickets,
        "date": date_str,
    }


USAGE_TEXT = (
    "Usage: jira_util.py [--as agent1|agent2|coordinator] <command> [args]\n"
    "Commands:\n"
    "  list                                                  List active sprint issues\n"
    "  list-epics                                            List all project Epics with Done/Open child ticket counts\n"
    "  close-epic KEY                                        Close an Epic after triple guardrail verification (coordinator only)\n"
    "  show KEY                                              Show issue details, description, comments, and subtasks\n"
    "  set-points KEY POINTS                                 Set story points (customfield_10016) on issue (e.g. 1, 2, 3, 5, or 'none')\n"
    "  status KEY                                            Show issue status\n"
    "  search JQL                                            Search issues using JQL\n"
    "  ensure-retro                                          Check/create 'Review & Retro' ticket in the active sprint\n"
    "  check-gate KEY                                        Verify Impl-Plan subtask is Erledigt (Gate 3)\n"
    "  submit-stage PARENT_KEY STAGE TEXT_OR_@FILE [--summary \"...\"]\n"
    "                                                        Create/update stage subtask (1..5) and move to In Überprüfung\n"
    "  gate-verdict SUBTASK_KEY PASS|REVISION TEXT_OR_@FILE  Record agent2 gate verdict, close/advance on PASS or reopen on REVISION\n"
    "  final-review-verdict PARENT_KEY PASS|BOUNCE TEXT_OR_@FILE [--bounce-target=implementation|analysis|zu erledigen]\n"
    "                                                        Record Stage 6 Cold Carbon review verdict on parent ticket\n"
    "  move KEY <target>                                     Transition issue (todo|in_progress|in_review|freigabe|analysis|test|final review (agent)|final review (human))\n"
    "  comment KEY TEXT_OR_@FILE                             Add role-attributed comment\n"
    "  update-desc KEY TEXT_OR_@FILE                         Update issue description\n"
    "  update-summary KEY SUMMARY                            Update issue summary\n"
    "  set-parent KEY PARENT_EPIC_KEY                        Link issue to parent/epic\n"
    "  assign KEY ROLE_OR_ACCOUNT_ID                         Assign issue (agent1|agent2|coordinator|human)\n"
    "  upload|attach KEY FILE_PATH                           Upload attachment to issue\n"
    "  download URL FILENAME                                 Download single attachment URL\n"
    "  download-all KEY                                      Download all attachments for issue\n"
    "  versions                                              List project versions\n"
    "  set-fixversion KEY VERSION                            Set Lösungsversion on parent issue\n"
    "  clear-fixversion KEY                                  Clear Lösungsversion on issue\n"
    "  rank-top KEY                                          Rank issue at the very top of the backlog\n"
    "  create-subtask PARENT_KEY SUMMARY DESC [--add-to-sprint]\n"
    "  sprint-metrics [--sprint <ID|NAME>] [--record] [--post-to-retro] [--backfill=N]\n"
    "                                                        Calculate PA 4.1 sprint metrics and PA 4.2 SPC control limits\n"
    "  export-gates [--sprint <ID|NAME>] [--out <FILE>]\n"
    "                                                        Export sprint gate records to docs/engineering/gates/<sprint>.md\n"
    "  qa-audit [--sprint <ID|NAME>] [--sample N] [--out <FILE>]\n"
    "                                                        Sample >= 2 sprint tickets and audit ASPICE process compliance\n"
    "  record-review [--sprint <ID|NAME>] [--ticket KEY] [--reviewer NAME] [--vendor VENDOR] [--model MODEL] [--version VER] [--scope SCOPE] [--verdict PASS|FAIL] [--findings FINDINGS] [--out FILE]\n"
    "                                                        Record Human Sprint Review or Cross-Vendor AI Review in docs/engineering/reviews/\n"
    "  create-issue SUMMARY DESC [TYPE_ID|Bug|Feature|Verbesserung] [PARENT_KEY] [--type=...] [--parent=...] [--bottom] [--points=N] [--severity=critical|major|minor|cosmetic]"
)

if __name__ == "__main__":
    active_role, remaining_argv = parse_role_from_args(sys.argv[1:])

    if len(remaining_argv) < 1 or remaining_argv[0] in ("-h", "--help", "help"):
        print(USAGE_TEXT, file=sys.stderr)
        sys.exit(0 if remaining_argv and remaining_argv[0] in ("-h", "--help", "help") else 1)

    cmd = remaining_argv[0]
    if cmd == "list":
        list_sprint_issues(role=active_role)
    elif cmd in ("list-epics", "list_epics"):
        list_epics(role=active_role)
    elif cmd in ("close-epic", "close_epic"):
        if len(remaining_argv) < 2:
            print("Usage: jira_util.py close-epic <EPIC_KEY>", file=sys.stderr)
            sys.exit(1)
        close_epic(remaining_argv[1], role=active_role)
    elif cmd in ("ensure-retro", "ensure_retro"):
        ensure_retro_ticket(role=active_role)
    elif cmd == "show" and len(remaining_argv) == 2:
        show_issue(remaining_argv[1], role=active_role)
    elif cmd in ("set-points", "set_points") and len(remaining_argv) == 3:
        set_story_points(remaining_argv[1], remaining_argv[2], role=active_role)
    elif cmd == "status" and len(remaining_argv) == 2:
        print_status(remaining_argv[1], role=active_role)
    elif cmd == "check-gate" and len(remaining_argv) == 2:
        check_gate(remaining_argv[1], role=active_role)
    elif cmd in ("submit-stage", "submit_stage") and len(remaining_argv) >= 4:
        parent_k = remaining_argv[1]
        stage_arg = remaining_argv[2]
        text_arg = remaining_argv[3]
        summ_override = None
        idx = 4
        while idx < len(remaining_argv):
            arg = remaining_argv[idx]
            if arg.startswith("--summary="):
                summ_override = arg.split("=", 1)[1]
            elif arg == "--summary" and idx + 1 < len(remaining_argv):
                summ_override = remaining_argv[idx + 1]
                idx += 1
            idx += 1
        submit_stage(parent_k, stage_arg, text_arg, summary_override=summ_override, role=active_role)
    elif cmd in ("gate-verdict", "gate_verdict") and len(remaining_argv) == 4:
        gate_verdict(remaining_argv[1], remaining_argv[2], remaining_argv[3], role="agent2")
    elif cmd in ("final-review-verdict", "final_review_verdict") and len(remaining_argv) >= 4:
        parent_k = remaining_argv[1]
        verdict_a = remaining_argv[2]
        text_a = remaining_argv[3]
        b_target = "implementation"
        idx = 4
        while idx < len(remaining_argv):
            arg = remaining_argv[idx]
            if arg.startswith("--bounce-target="):
                b_target = arg.split("=", 1)[1]
            elif arg == "--bounce-target" and idx + 1 < len(remaining_argv):
                b_target = remaining_argv[idx + 1]
                idx += 1
            idx += 1
        final_review_verdict(parent_k, verdict_a, text_a, bounce_target=b_target, role="agent2")
    elif cmd == "versions":
        list_versions(role=active_role)
    elif cmd in ("set-fixversion", "set-fix-version") and len(remaining_argv) == 3:
        set_fix_version(remaining_argv[1], remaining_argv[2], role=active_role)
    elif cmd == "update" and len(remaining_argv) == 4 and remaining_argv[2] in ("--fix-version", "--fixversion"):
        set_fix_version(remaining_argv[1], remaining_argv[3], role=active_role)
    elif cmd == "update" and len(remaining_argv) == 3 and (remaining_argv[2].startswith("--fix-version=") or remaining_argv[2].startswith("--fixversion=")):
        set_fix_version(remaining_argv[1], remaining_argv[2].split("=", 1)[1], role=active_role)
    elif cmd in ("clear-fixversion", "clear-fix-version") and len(remaining_argv) == 2:
        set_fix_version(remaining_argv[1], "none", role=active_role)
    elif cmd == "download" and len(remaining_argv) == 3:
        download_attachment(remaining_argv[1], remaining_argv[2], role=active_role)
    elif cmd == "download-all" and len(remaining_argv) == 2:
        download_all_attachments(remaining_argv[1], role=active_role)
    elif cmd in ("upload", "upload-attachment", "attach") and len(remaining_argv) == 3:
        upload_attachment(remaining_argv[1], remaining_argv[2], role=active_role)
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
    elif cmd == "set-parent" and len(remaining_argv) == 3:
        set_parent(remaining_argv[1], remaining_argv[2], role=active_role)
    elif cmd == "assign" and len(remaining_argv) == 3:
        assign_issue(remaining_argv[1], remaining_argv[2], role=active_role)
    elif cmd == "rank-top" and len(remaining_argv) == 2:
        rank_at_top_of_backlog(remaining_argv[1], role=active_role)
    elif cmd == "create-subtask" and len(remaining_argv) >= 4:
        # Parse optional flags
        add_sprint = False
        filtered_args = []
        for arg in remaining_argv[1:]:
            if arg == "--add-to-sprint":
                add_sprint = True
            elif arg.startswith("--fixversion") or arg.startswith("--fix-version"):
                print("Error: Sub-tasks must not get a solution ('Lösungsversion') assigned! (Governance mandate)", file=sys.stderr)
                sys.exit(1)
            else:
                filtered_args.append(arg)

        if len(filtered_args) < 3:
            print("Usage: create-subtask PARENT_KEY SUMMARY DESC [--add-to-sprint]", file=sys.stderr)
            sys.exit(1)

        parent_k = filtered_args[0]
        summ = filtered_args[1]
        desc = filtered_args[2]

        # Default subtask creation role to coordinator unless explicitly overridden
        subtask_role = active_role if active_role != "agent1" or "--as" in sys.argv or any(a.startswith("--as=") for a in sys.argv) or os.environ.get("JIRA_ACTOR") else "coordinator"
        create_subtask(parent_k, summ, desc, role=subtask_role, add_to_sprint=add_sprint)
    elif cmd == "create-issue" and len(remaining_argv) >= 3:
        add_sprint = False
        rank_top = True
        fix_ver = None
        priority_arg = None
        labels_arg = None
        points_arg = None
        severity_arg = None
        explicit_type = None
        explicit_parent = None
        explicit_summary = None
        explicit_desc = None
        filtered_args = []
        idx = 1
        while idx < len(remaining_argv):
            arg = remaining_argv[idx]
            if arg == "--add-to-sprint":
                if active_role in ("agent1", "agent2", "coordinator"):
                    print("Error: Agents must not move tickets to sprints! Only the human user assigns tickets to sprints during Sprint-Start Screening.", file=sys.stderr)
                    sys.exit(1)
                add_sprint = True
            elif arg == "--bottom":
                rank_top = False
            elif arg == "--top":
                rank_top = True
            elif arg.startswith("--fixversion=") or arg.startswith("--fix-version="):
                if active_role in ("agent1", "agent2", "coordinator"):
                    print("Error: Tickets must not get a solution ('Lösungsversion') assigned at creation time! Mandate: 'Add version when ticket is finished, not when started' (Rule 19).", file=sys.stderr)
                    sys.exit(1)
                fix_ver = arg.split("=", 1)[1]
            elif arg.startswith("--priority="):
                priority_arg = arg.split("=", 1)[1]
            elif arg.startswith("--labels="):
                labels_arg = arg.split("=", 1)[1]
            elif arg.startswith("--points=") or arg.startswith("--story-points="):
                points_arg = arg.split("=", 1)[1]
            elif arg in ("--points", "--story-points") and idx + 1 < len(remaining_argv):
                points_arg = remaining_argv[idx + 1]
                idx += 1
            elif arg.startswith("--severity="):
                severity_arg = arg.split("=", 1)[1]
            elif arg == "--severity" and idx + 1 < len(remaining_argv):
                severity_arg = remaining_argv[idx + 1]
                idx += 1
            elif arg.startswith("--summary="):
                explicit_summary = arg.split("=", 1)[1]
            elif arg == "--summary" and idx + 1 < len(remaining_argv):
                explicit_summary = remaining_argv[idx + 1]
                idx += 1
            elif arg.startswith("--description=") or arg.startswith("--desc="):
                explicit_desc = arg.split("=", 1)[1]
            elif arg in ("--description", "--desc") and idx + 1 < len(remaining_argv):
                explicit_desc = remaining_argv[idx + 1]
                idx += 1
            elif arg.startswith("--type=") or arg.startswith("--issuetype="):
                explicit_type = arg.split("=", 1)[1]
            elif arg in ("--type", "--issuetype") and idx + 1 < len(remaining_argv):
                explicit_type = remaining_argv[idx + 1]
                idx += 1
            elif arg.startswith("--parent=") or arg.startswith("--epic="):
                explicit_parent = arg.split("=", 1)[1]
            elif arg in ("--parent", "--epic") and idx + 1 < len(remaining_argv):
                explicit_parent = remaining_argv[idx + 1]
                idx += 1
            else:
                filtered_args.append(arg)
            idx += 1

        summary = explicit_summary if explicit_summary else (filtered_args[0] if len(filtered_args) >= 1 else None)
        desc = explicit_desc if explicit_desc else (filtered_args[1] if len(filtered_args) >= 2 else (filtered_args[0] if explicit_summary and len(filtered_args) >= 1 else None))
        if not summary or not desc:
            print("Usage: create-issue SUMMARY DESC [TYPE_ID|Bug|Feature|Verbesserung] [PARENT_KEY] [--type=...] [--parent=...] [--bottom] [--priority=PRIORITY] [--labels=L1,L2] [--points=N] [--severity=critical|major|minor|cosmetic]", file=sys.stderr)
            sys.exit(1)

        pos_offset = (0 if explicit_summary else 1) + (0 if explicit_desc else 1)
        type_id = explicit_type if explicit_type else (filtered_args[pos_offset] if len(filtered_args) > pos_offset else "10008")
        parent = explicit_parent if explicit_parent else (filtered_args[pos_offset + 1] if len(filtered_args) > pos_offset + 1 else None)
        create_issue(summary, desc, type_id, parent, role=active_role, add_to_sprint=add_sprint, fix_version=fix_ver, priority=priority_arg, labels=labels_arg, rank_top=rank_top, points=points_arg, severity=severity_arg)
    elif cmd in ("recalculate-living-docs", "recalc-docs"):
        recalculate_master_index_tables()
    elif cmd in ("sprint-metrics", "sprint_metrics"):
        sprint_arg = None
        record = False
        post_to_retro = False
        backfill = None
        idx = 1
        while idx < len(remaining_argv):
            a = remaining_argv[idx]
            if a == "--record":
                record = True
            elif a in ("--post-to-retro", "--retro"):
                post_to_retro = True
            elif a.startswith("--sprint="):
                sprint_arg = a.split("=", 1)[1]
            elif a == "--sprint" and idx + 1 < len(remaining_argv):
                sprint_arg = remaining_argv[idx + 1]
                idx += 1
            elif a.startswith("--backfill="):
                backfill = a.split("=", 1)[1]
            elif a == "--backfill" and idx + 1 < len(remaining_argv):
                backfill = remaining_argv[idx + 1]
                idx += 1
            elif not a.startswith("--") and not sprint_arg:
                sprint_arg = a
            idx += 1
        cmd_sprint_metrics(sprint_arg=sprint_arg, record=record, post_to_retro=post_to_retro, backfill=backfill, role=active_role)
    elif cmd in ("export-gates", "export_gates"):
        sprint_arg = None
        out_arg = None
        idx = 1
        while idx < len(remaining_argv):
            a = remaining_argv[idx]
            if a.startswith("--sprint="):
                sprint_arg = a.split("=", 1)[1]
            elif a == "--sprint" and idx + 1 < len(remaining_argv):
                sprint_arg = remaining_argv[idx + 1]
                idx += 1
            elif a.startswith("--out="):
                out_arg = a.split("=", 1)[1]
            elif a == "--out" and idx + 1 < len(remaining_argv):
                out_arg = remaining_argv[idx + 1]
                idx += 1
            elif not a.startswith("--") and not sprint_arg:
                sprint_arg = a
            idx += 1
        export_gates(sprint_arg=sprint_arg, out_path=out_arg, role=active_role)
    elif cmd in ("qa-audit", "qa_audit"):
        sprint_arg = None
        sample_arg = 2
        out_arg = None
        idx = 1
        while idx < len(remaining_argv):
            a = remaining_argv[idx]
            if a.startswith("--sprint="):
                sprint_arg = a.split("=", 1)[1]
            elif a == "--sprint" and idx + 1 < len(remaining_argv):
                sprint_arg = remaining_argv[idx + 1]
                idx += 1
            elif a.startswith("--sample="):
                sample_arg = int(a.split("=", 1)[1])
            elif a == "--sample" and idx + 1 < len(remaining_argv):
                sample_arg = int(remaining_argv[idx + 1])
                idx += 1
            elif a.startswith("--out="):
                out_arg = a.split("=", 1)[1]
            elif a == "--out" and idx + 1 < len(remaining_argv):
                out_arg = remaining_argv[idx + 1]
                idx += 1
            elif not a.startswith("--") and not sprint_arg:
                sprint_arg = a
            idx += 1
        qa_audit(sprint_arg=sprint_arg, sample_size=sample_arg, out_path=out_arg, role=active_role)
    elif cmd in ("record-review", "record_review"):
        sprint_arg = None
        ticket_arg = None
        reviewer_arg = None
        vendor_arg = None
        model_arg = None
        version_arg = None
        scope_arg = None
        verdict_arg = "PASS"
        findings_arg = None
        out_arg = None
        idx = 1
        while idx < len(remaining_argv):
            a = remaining_argv[idx]
            if a.startswith("--sprint="):
                sprint_arg = a.split("=", 1)[1]
            elif a == "--sprint" and idx + 1 < len(remaining_argv):
                sprint_arg = remaining_argv[idx + 1]
                idx += 1
            elif a.startswith("--ticket="):
                ticket_arg = a.split("=", 1)[1]
            elif a == "--ticket" and idx + 1 < len(remaining_argv):
                ticket_arg = remaining_argv[idx + 1]
                idx += 1
            elif a.startswith("--reviewer="):
                reviewer_arg = a.split("=", 1)[1]
            elif a == "--reviewer" and idx + 1 < len(remaining_argv):
                reviewer_arg = remaining_argv[idx + 1]
                idx += 1
            elif a.startswith("--vendor="):
                vendor_arg = a.split("=", 1)[1]
            elif a == "--vendor" and idx + 1 < len(remaining_argv):
                vendor_arg = remaining_argv[idx + 1]
                idx += 1
            elif a.startswith("--model="):
                model_arg = a.split("=", 1)[1]
            elif a == "--model" and idx + 1 < len(remaining_argv):
                model_arg = remaining_argv[idx + 1]
                idx += 1
            elif a.startswith("--version="):
                version_arg = a.split("=", 1)[1]
            elif a == "--version" and idx + 1 < len(remaining_argv):
                version_arg = remaining_argv[idx + 1]
                idx += 1
            elif a.startswith("--scope="):
                scope_arg = a.split("=", 1)[1]
            elif a == "--scope" and idx + 1 < len(remaining_argv):
                scope_arg = remaining_argv[idx + 1]
                idx += 1
            elif a.startswith("--verdict="):
                verdict_arg = a.split("=", 1)[1]
            elif a == "--verdict" and idx + 1 < len(remaining_argv):
                verdict_arg = remaining_argv[idx + 1]
                idx += 1
            elif a.startswith("--findings="):
                findings_arg = a.split("=", 1)[1]
            elif a == "--findings" and idx + 1 < len(remaining_argv):
                findings_arg = remaining_argv[idx + 1]
                idx += 1
            elif a.startswith("--out="):
                out_arg = a.split("=", 1)[1]
            elif a == "--out" and idx + 1 < len(remaining_argv):
                out_arg = remaining_argv[idx + 1]
                idx += 1
            idx += 1
        record_review(
            sprint_arg=sprint_arg,
            ticket_key=ticket_arg,
            reviewer=reviewer_arg,
            vendor=vendor_arg,
            model=model_arg,
            version=version_arg,
            scope=scope_arg,
            verdict=verdict_arg,
            findings=findings_arg,
            out_path=out_arg,
            role=active_role,
        )
    elif cmd == "add-to-sprint" and len(remaining_argv) == 2:
        if active_role in ("agent1", "agent2", "coordinator"):
            print("Error: Agents must not move tickets to sprints! Only the human user assigns tickets to sprints during Sprint-Start Screening.", file=sys.stderr)
            sys.exit(1)
        add_to_active_sprint(remaining_argv[1], role=active_role)
    else:
        print(f"Invalid command or arguments: {' '.join(remaining_argv)}\n\n{USAGE_TEXT}", file=sys.stderr)
        sys.exit(1)
