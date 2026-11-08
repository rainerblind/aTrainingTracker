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
import ssl
import sys
import urllib.request
from urllib.parse import urlencode

_tools_dir = os.path.dirname(os.path.abspath(__file__))
if _tools_dir not in sys.path:
    sys.path.insert(0, _tools_dir)

from sprint_metrics import (
    calculate_spc_limits,
    format_metrics_markdown,
    load_historical_metrics,
    parse_sprint_gate_metrics,
    record_sprint_metrics,
)

DEFAULT_CONFIG_LOCATIONS = [
    os.path.join(os.path.dirname(__file__), "hoechstmass_config.json"),
    os.path.join(os.path.dirname(__file__), "..", "hoechstmass_config.json"),
    os.path.join(os.path.dirname(__file__), "..", "..", "hoechstmass_config.json"),
]

DEFAULT_ENV_LOCATIONS = [
    os.path.join(os.path.dirname(__file__), ".env.jira"),
    os.path.join(os.path.dirname(__file__), "..", ".env.jira"),
    os.path.join(os.path.dirname(__file__), "..", "..", ".env.jira"),
    os.path.join(os.path.dirname(__file__), "..", "..", "..", ".env.jira"),
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
        "deployment_type": "cloud",
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
            "epic_link": "customfield_10014",
            "sprint": "customfield_10020"
        }
    }


def is_datacenter(config=None, h_config=None):
    """Determines whether active deployment is Jira Data Center (or Server) vs Jira Cloud."""
    if config is None:
        config = load_env_config()
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
        config = load_env_config()
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
        config = load_env_config()
    base_url = config.get("JIRA_URL", "").rstrip("/")
    if is_datacenter(config, h_config):
        return f"{base_url}/rest/api/2/search"
    return f"{base_url}/rest/api/3/search/jql"


def get_ssl_context(config=None):
    """Constructs SSLContext supporting corporate CA bundles and verification toggles."""
    if config is None:
        config = load_env_config()
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


# Alias for cross-tool parity
get_config = load_env_config


def mask_secret(text):
    """Masks tokens or passwords in log output or error strings."""
    if not text:
        return ""
    if len(text) <= 6:
        return "***"
    return text[:2] + "***" + text[-2:]


def resolve_assignee_identifier(role_or_id, config=None, h_config=None):
    """Resolves assignee identifier: username for Jira Data Center, accountId for Jira Cloud."""
    if not role_or_id:
        return None
    if config is None:
        config = load_env_config()
    if h_config is None:
        h_config = load_hoechstmass_config()

    role_key = str(role_or_id).lower().strip()
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
        return role_or_id
    else:
        return get_account_id_for_role(role_or_id, config=config, h_config=h_config)


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


def resolve_account(role="agent1", config=None, h_config=None):
    """Resolves (username, token) for a role with fallback to default credentials or Bearer PAT in Data Center."""
    if config is None:
        config = load_env_config()
    if h_config is None:
        h_config = load_hoechstmass_config()

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

    auth_type = (config.get("JIRA_AUTH_TYPE") or "").lower()
    if token and (is_datacenter(config, h_config) or auth_type == "bearer"):
        return user or "", token

    default_user = config.get("JIRA_USER")
    default_token = config.get("JIRA_TOKEN") or config.get("JIRA_API_TOKEN")
    if default_user and default_token:
        return default_user, default_token

    if default_token and (is_datacenter(config, h_config) or auth_type == "bearer"):
        return default_user or "", default_token

    print(f"Error: Missing Jira credentials for role '{role}' and no default credentials found.", file=sys.stderr)
    sys.exit(1)


def get_headers(config=None, role="agent1", h_config=None):
    """Builds HTTP headers supporting Basic Auth or Bearer PAT in Jira Data Center."""
    if config is None:
        config = load_env_config()
    if h_config is None:
        h_config = load_hoechstmass_config()

    user, token = resolve_account(role, config, h_config)
    auth_type = (config.get("JIRA_AUTH_TYPE") or "").lower()
    is_dc = is_datacenter(config, h_config)

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
        "Content-Type": "application/json",
        "Accept": "application/json",
    }


def jira_request(url, method="GET", payload=None, role="agent1", config=None, h_config=None):
    """Executes an authenticated HTTP request to the Jira REST API."""
    if config is None:
        config = load_env_config()
    if h_config is None:
        h_config = load_hoechstmass_config()

    headers = get_headers(config=config, role=role, h_config=h_config)
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    ssl_context = get_ssl_context(config)

    try:
        with urllib.request.urlopen(req, context=ssl_context) as resp:
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
    """Assigns an issue to username in Data Center or accountId in Cloud."""
    h_config = load_hoechstmass_config()
    config = load_env_config()
    if is_datacenter(config, h_config):
        username = resolve_assignee_identifier(role_or_id, config=config, h_config=h_config)
        if not username:
            print(f"Error: Could not resolve username for '{role_or_id}'.", file=sys.stderr)
            return
        payload = {"name": username}
        target_disp = f"{role_or_id} ({username})"
    else:
        account_id = get_account_id_for_role(role_or_id, config=config, h_config=h_config)
        if not account_id:
            print(f"Error: Could not resolve account ID for '{role_or_id}'.", file=sys.stderr)
            return
        payload = {"accountId": account_id}
        target_disp = f"{role_or_id} ({account_id})"

    url = f"{config.get('JIRA_URL', '')}/rest/api/2/issue/{issue_key}/assignee"
    jira_request(url, method="PUT", payload=payload, role=as_role, config=config, h_config=h_config)
    print(f"Assigned {issue_key} to {target_disp}.")


def search_issues(jql, as_role="agent1"):
    """Searches issues using JQL across Cloud (/rest/api/3/search/jql) or Data Center (/rest/api/2/search)."""
    config = load_env_config()
    h_config = load_hoechstmass_config()
    url = get_search_endpoint(config, h_config)
    payload = {
        "jql": jql,
        "fields": ["summary", "status", "issuetype"]
    }
    data = jira_request(url, method="POST", payload=payload, role=as_role, config=config, h_config=h_config)
    issues = data.get("issues", [])
    for i in issues:
        fields = i.get("fields", {})
        itype = fields.get("issuetype", {}).get("name", "Unknown")
        status = fields.get("status", {}).get("name", "Unknown")
        summary = fields.get("summary", "")
        print(f"{i.get('key')}: [{itype}] {summary} [{status}]")
    return issues


def paged_jql_search(jql, fields, role="agent1", config=None, h_config=None, max_total=5000):
    """
    Executes a paginated JQL search supporting both Jira Cloud (nextPageToken)
    and Jira Data Center (startAt). Returns a list of issue dictionaries.
    """
    if config is None:
        config = load_env_config()
    if h_config is None:
        h_config = load_hoechstmass_config()
    url = get_search_endpoint(config, h_config)
    is_dc = is_datacenter(config, h_config)
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

        data = jira_request(url, method="POST", payload=payload, role=role, config=config, h_config=h_config)
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


def list_epics(role="agent1", config=None, h_config=None):
    """
    REQ-PRO-040.1 / TST-PRO-040.1:
    Lists all Epics in the project with Done/Open child ticket counts.
    Supports both Jira Cloud and Jira Data Center deployments.
    """
    if config is None:
        config = load_env_config()
    if h_config is None:
        h_config = load_hoechstmass_config()

    jql = "issuetype = Epic ORDER BY key ASC"
    fields = ["summary", "status", "issuetype"]
    epics = paged_jql_search(jql, fields, role=role, config=config, h_config=h_config)

    if not epics:
        print("No Epics found in project.")
        return

    epic_keys = [e["key"] for e in epics]
    done_counts = {k: 0 for k in epic_keys}
    open_counts = {k: 0 for k in epic_keys}

    is_dc = is_datacenter(config, h_config)
    epic_field = get_epic_link_field(config, h_config) if is_dc else None
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

        children = paged_jql_search(child_jql, fields_to_fetch, role=role, config=config, h_config=h_config)
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


def close_epic(epic_key, role="coordinator", config=None, h_config=None):
    """
    REQ-PRO-040.2 / TST-PRO-040.2:
    Closes an Epic to 'Erledigt' under triple guardrail enforcement:
    1. Role Guard: Restricted strictly to 'coordinator' or 'human' (rejects 'agent1', 'agent2').
    2. Issue Type Guard: Verifies target issue is an 'Epic' (rejects standard parent tickets).
    3. Completeness Guard: Verifies 0 open child tickets (aborts with list of unfinished tickets).
    """
    if config is None:
        config = load_env_config()
    if h_config is None:
        h_config = load_hoechstmass_config()

    # Guard 1: Role check
    if role not in ("coordinator", "human"):
        print(f"ERROR: Role '{role}' is not authorized to close Epics.\n"
              f"Closing an Epic is restricted strictly to '--as coordinator' or 'human'.",
              file=sys.stderr)
        sys.exit(1)

    # Fetch issue details
    url = f"{config.get('JIRA_URL', '')}/rest/api/2/issue/{epic_key}?fields=issuetype,status,summary"
    issue_info = jira_request(url, role=role, config=config, h_config=h_config)
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
    is_dc = is_datacenter(config, h_config)
    if is_dc:
        epic_field = get_epic_link_field(config, h_config)
        child_jql = f'"{epic_field}" = "{epic_key}" OR parent = "{epic_key}"'
        fields_to_fetch = ["summary", "status", "issuetype", epic_field, "parent"]
    else:
        child_jql = f'parent = "{epic_key}"'
        fields_to_fetch = ["summary", "status", "issuetype", "parent"]

    child_issues = paged_jql_search(child_jql, fields_to_fetch, role=role, config=config, h_config=h_config)

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
    transition_issue(epic_key, "erledigt", as_role=role, allow_epic_close=True, config=config, h_config=h_config)
    print(f"Epic '{epic_key}' ({summary}) successfully verified and closed.")


def transition_issue(issue_key, status_name, as_role="agent1", allow_epic_close=False, config=None, h_config=None):
    if config is None:
        config = load_env_config()
    if h_config is None:
        h_config = load_hoechstmass_config()

    url = f"{config.get('JIRA_URL', '')}/rest/api/2/issue/{issue_key}?fields=issuetype,status,summary"
    issue_info = jira_request(url, role=as_role, config=config, h_config=h_config)
    fields_data = issue_info.get("fields", {})
    is_subtask = fields_data.get("issuetype", {}).get("subtask", False)
    is_epic = fields_data.get("issuetype", {}).get("name", "").lower() == "epic"
    normalized_input = status_name.lower().strip()

    if not is_subtask and normalized_input in ["erledigt", "done", "freigabe erteilt"]:
        if not (allow_epic_close and is_epic):
            print(f"ERROR: Transitioning parent ticket '{issue_key}' to '{status_name}' is strictly prohibited for AI agents.\n"
                  f"Moving parent tickets to 'Erledigt' is a Human Decision Gate reserved exclusively for the human user.",
                  file=sys.stderr)
            sys.exit(1)

    t_url = f"{config.get('JIRA_URL', '')}/rest/api/2/issue/{issue_key}/transitions"
    done_id = h_config.get("transitions", {}).get("done", "41")
    jira_request(t_url, method="POST", payload={"transition": {"id": done_id}}, role=as_role, config=config, h_config=h_config)
    print(f"Successfully moved {issue_key} to Done via transition '{done_id}'.")


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


def collect_sprint_metrics_for_id(sprint_id, sprint_name, board_id, as_role="coordinator", config=None, h_config=None):
    if config is None:
        config = load_env_config()
    if h_config is None:
        h_config = load_hoechstmass_config()

    base = config.get("JIRA_URL", "").rstrip("/")
    sp_field = h_config.get("custom_fields", {}).get("story_points", "customfield_10016")
    url = f"{base}/rest/agile/1.0/sprint/{sprint_id}/issue?fields=summary,status,issuetype,priority,subtasks,{sp_field}&maxResults=100"
    res = jira_request(url, role=as_role, config=config)
    issues = res.get("issues", [])

    parent_issues = [i for i in issues if not i.get("fields", {}).get("issuetype", {}).get("subtask", False)]
    for p in parent_issues:
        subtasks = p.get("fields", {}).get("subtasks", [])
        for st in subtasks:
            st_key = st.get("key")
            if st_key:
                try:
                    c_res = jira_request(f"{base}/rest/api/2/issue/{st_key}/comment", role=as_role, config=config)
                    st["comments"] = c_res.get("comments", [])
                except Exception:
                    st["comments"] = []

    sprint_info = {"id": sprint_id, "name": sprint_name}
    return parse_sprint_gate_metrics(parent_issues, sprint_info)


def cmd_sprint_metrics(sprint_arg=None, record=False, post_to_retro=False, backfill=None, as_role="coordinator"):
    config = load_env_config()
    h_config = load_hoechstmass_config()
    base = config.get("JIRA_URL", "").rstrip("/")
    board_id = h_config.get("board_id", 1)

    if backfill is not None:
        try:
            n_backfill = int(backfill)
        except ValueError:
            n_backfill = 3
        print(f"Backfilling metrics for the last {n_backfill} closed sprints...")
        all_closed = []
        start_at = 0
        while True:
            s_page = jira_request(f"{base}/rest/agile/1.0/board/{board_id}/sprint?state=closed&startAt={start_at}&maxResults=50", role=as_role, config=config)
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
            m = collect_sprint_metrics_for_id(s_id, s_name, board_id, as_role=as_role, config=config, h_config=h_config)
            record_sprint_metrics(m)
        print("\nBackfill complete.")
        return

    target_sprint = None
    if sprint_arg:
        # Check active sprint first
        active_sprints = jira_request(f"{base}/rest/agile/1.0/board/{board_id}/sprint?state=active", role=as_role, config=config).get("values", [])
        for a in active_sprints:
            if str(a["id"]) == str(sprint_arg) or a["name"].strip() == str(sprint_arg).strip():
                target_sprint = a
                break
        if not target_sprint and str(sprint_arg).isdigit():
            try:
                s_obj = jira_request(f"{base}/rest/agile/1.0/sprint/{sprint_arg}", role=as_role, config=config)
                if s_obj and "id" in s_obj:
                    target_sprint = s_obj
            except Exception:
                pass

        if not target_sprint:
            start_at = 0
            while True:
                s_page = jira_request(f"{base}/rest/agile/1.0/board/{board_id}/sprint?startAt={start_at}&maxResults=50", role=as_role, config=config)
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
        active_sprints = jira_request(f"{base}/rest/agile/1.0/board/{board_id}/sprint?state=active", role=as_role, config=config).get("values", [])
        dev_sprints = [s for s in active_sprints if s.get("name", "").strip().lower() != "human review"]
        target_sprint = dev_sprints[0] if dev_sprints else (active_sprints[0] if active_sprints else None)
        if not target_sprint:
            print("Error: No active sprint found.", file=sys.stderr)
            sys.exit(1)

    s_id = target_sprint["id"]
    s_name = target_sprint["name"]
    print(f"Collecting quantitative metrics for Sprint '{s_name}' (ID: {s_id})...")
    metrics = collect_sprint_metrics_for_id(s_id, s_name, board_id, as_role=as_role, config=config, h_config=h_config)
    history = load_historical_metrics()
    spc = calculate_spc_limits(history, current_metrics=metrics)

    report_md = format_metrics_markdown(metrics, spc)
    print("\n" + report_md + "\n")

    if record:
        record_sprint_metrics(metrics)

    if post_to_retro:
        retro_key = None
        sprint_issues = jira_request(f"{base}/rest/agile/1.0/sprint/{s_id}/issue?fields=summary,issuetype", role=as_role, config=config).get("issues", [])
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
            add_comment(retro_key, report_md, as_role=as_role)
            print(f"Posted quantitative sprint scorecard to {retro_key}.")
        else:
            print("Warning: No 'Review & Retro' ticket found in this sprint to post metrics to.", file=sys.stderr)


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
    elif cmd == "search":
        if len(args) < 2:
            print("Usage: jira_util.py search <JQL>", file=sys.stderr)
            sys.exit(1)
        search_issues(args[1], as_role=as_role)
    elif cmd in ("list-epics", "list_epics"):
        list_epics(role=as_role)
    elif cmd in ("close-epic", "close_epic"):
        if len(args) < 2:
            print("Usage: jira_util.py close-epic <EPIC_KEY>", file=sys.stderr)
            sys.exit(1)
        close_epic(args[1], role=as_role)
    elif cmd in ("sprint-metrics", "sprint_metrics"):
        sprint_arg = None
        record = False
        post_to_retro = False
        backfill = None
        idx = 1
        while idx < len(args):
            a = args[idx]
            if a == "--record":
                record = True
            elif a in ("--post-to-retro", "--retro"):
                post_to_retro = True
            elif a.startswith("--sprint="):
                sprint_arg = a.split("=", 1)[1]
            elif a == "--sprint" and idx + 1 < len(args):
                sprint_arg = args[idx + 1]
                idx += 1
            elif a.startswith("--backfill="):
                backfill = a.split("=", 1)[1]
            elif a == "--backfill" and idx + 1 < len(args):
                backfill = args[idx + 1]
                idx += 1
            elif not a.startswith("--") and not sprint_arg:
                sprint_arg = a
            idx += 1
        cmd_sprint_metrics(sprint_arg=sprint_arg, record=record, post_to_retro=post_to_retro, backfill=backfill, as_role=as_role)
    else:
        print(f"Command '{cmd}' ready in parameterized client.")


if __name__ == "__main__":
    main()
