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
import subprocess
import sys
import urllib.request
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
    6: "stage6-final-review",
}

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

STAGE_TO_SKILL_DIR = {
    "analysis": "stage1-analysis",
    "test spec": "stage2-req-test-spec",
    "implementation plan": "stage3-impl-plan",
    "implementation": "stage4-implementation",
    "test": "stage5-verification",
    "final review (agent)": "stage6-final-review",
}

VALID_ROLES = ["agent1", "agent2", "coordinator"]


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


def load_env_config(custom_path=None, role=None):
    """
    Loads environment variables from .env.jira or .env.jira.<role>.
    Under ASPICE SUP.1 / SUP.8 per-role credential isolation (ATT-6147):
    - When role is provided (or JIRA_ACTOR is set), checks for role-specific file (.env.jira.<role>).
    - If .env.jira.<role> exists, overlays it onto base config.
    - Filters out other roles' credentials when role is specified.
    """
    if role is None:
        role = os.environ.get("JIRA_ACTOR")
    if role:
        role = role.strip().lower()

    paths = [custom_path] if custom_path else DEFAULT_ENV_LOCATIONS
    config = dict(os.environ)
    loaded_base_dir = None

    for p in paths:
        if p and os.path.exists(p):
            loaded_base_dir = os.path.dirname(p)
            with open(p, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if "=" in line and not line.startswith("#"):
                        k, v = line.split("=", 1)
                        config[k.strip()] = v.strip()
            break

    if role and loaded_base_dir:
        role_path = os.path.join(loaded_base_dir, f".env.jira.{role}")
        if os.path.exists(role_path):
            with open(role_path, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if "=" in line and not line.startswith("#"):
                        k, v = line.split("=", 1)
                        config[k.strip()] = v.strip()

    if role in ("agent1", "agent2", "coordinator"):
        other_roles = {"agent1", "agent2", "coordinator"} - {role}
        for other in other_roles:
            config.pop(f"JIRA_{other.upper()}_USER", None)
            config.pop(f"JIRA_{other.upper()}_TOKEN", None)
            config.pop(f"JIRA_{other.upper()}_ACCOUNT_ID", None)

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
    """
    Resolves (username, token) for a role.
    Under ASPICE SUP.1 role separation, automated roles (agent1, agent2, coordinator)
    must strictly provide their own role credentials; fallback to default human
    credentials (JIRA_USER, JIRA_TOKEN) is prohibited.
    """
    if config is None:
        config = load_env_config(role=role)
    if h_config is None:
        h_config = load_hoechstmass_config()

    role_map = {
        "agent1": ("JIRA_AGENT1_USER", "JIRA_AGENT1_TOKEN"),
        "agent2": ("JIRA_AGENT2_USER", "JIRA_AGENT2_TOKEN"),
        "coordinator": ("JIRA_COORDINATOR_USER", "JIRA_COORDINATOR_TOKEN"),
    }
    if role in role_map:
        user_key, token_key = role_map[role]
        user = config.get(user_key) if user_key else None
        token = config.get(token_key) if token_key else None

        if user and token:
            return user, token

        auth_type = (config.get("JIRA_AUTH_TYPE") or "").lower()
        if token and (is_datacenter(config, h_config) or auth_type == "bearer"):
            return user or "", token

        print(f"Error: Missing role-specific credentials for role '{role}'. "
              f"Fallback to human default credentials (JIRA_USER/JIRA_TOKEN) is prohibited under ASPICE SUP.1 role separation.", file=sys.stderr)
        sys.exit(1)

    if role == "human":
        default_user = config.get("JIRA_USER")
        default_token = config.get("JIRA_TOKEN") or config.get("JIRA_API_TOKEN")
        auth_type = (config.get("JIRA_AUTH_TYPE") or "").lower()
        if default_user and default_token:
            return default_user, default_token
        if default_token and (is_datacenter(config, h_config) or auth_type == "bearer"):
            return default_user or "", default_token

    print(f"Error: Missing Jira credentials for role '{role}'.", file=sys.stderr)
    sys.exit(1)


def get_headers(config=None, role="agent1", h_config=None):
    """Builds HTTP headers supporting Basic Auth or Bearer PAT in Jira Data Center."""
    if config is None:
        config = load_env_config(role=role)
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
        config = load_env_config(role=role)
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
    1. Role Guard: Restricted strictly to 'coordinator' (rejects 'agent1', 'agent2').
    2. Issue Type Guard: Verifies target issue is an 'Epic' (rejects standard parent tickets).
    3. Completeness Guard: Verifies 0 open child tickets (aborts with list of unfinished tickets).
    """
    if config is None:
        config = load_env_config()
    if h_config is None:
        h_config = load_hoechstmass_config()

    # Guard 1: Role check
    if role != "coordinator":
        print(f"ERROR: Role '{role}' is not authorized to close Epics.\n"
              f"Closing an Epic is restricted strictly to '--as coordinator'.",
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


def final_review_verdict(parent_key, verdict_arg, text_or_file, bounce_target="implementation", role="agent2", as_role=None, config=None, h_config=None):
    """
    REQ-PRO-058.2 / TST-PRO-058.2:
    Composite Stage 6 ('Final Review (Agent)') verdict command for the Cold Carbon Reviewer (agent2).
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

    report_text = read_text_or_file(text_or_file)
    if config is None:
        config = load_env_config()
    if h_config is None:
        h_config = load_hoechstmass_config()

    issue_url = f"{config.get('JIRA_URL', '')}/rest/api/2/issue/{parent_key}?fields=issuetype,status,summary,comment"
    issue_info = jira_request(issue_url, role=auditor_role, config=config, h_config=h_config)
    fields_data = issue_info.get("fields", {})
    if fields_data.get("issuetype", {}).get("subtask", False):
        print(f"Error: final-review-verdict operates strictly on parent tickets, not sub-task '{parent_key}'.", file=sys.stderr)
        sys.exit(1)

    assign_issue(parent_key, auditor_role, as_role=auditor_role)

    if is_pass:
        comment_body = report_text if FINAL_REVIEW_PASS_TAG in report_text else f"{FINAL_REVIEW_PASS_TAG}\n\n{report_text}"
        add_comment(parent_key, comment_body, as_role=auditor_role)
        transition_issue(parent_key, "final review (human)", as_role=auditor_role, config=config, h_config=h_config)
        assign_issue(parent_key, "human", as_role=auditor_role)
        print(f"FINAL_REVIEW_VERDICT: PASS recorded on {parent_key} by {auditor_role} (moved to Final Review (Human), assigned to human).")
        return "PASS"

    comments = issue_info.get("comments") or fields_data.get("comment", {}).get("comments", [])
    prior_bounces = count_final_review_bounces(comments)
    next_bounce = prior_bounces + 1

    if next_bounce <= 3:
        bounce_tag = f"[FINAL REVIEW (AGENT) — BOUNCE {next_bounce}/3]"
        comment_body = report_text if bounce_tag in report_text else f"{bounce_tag}\n\n{report_text}"
        add_comment(parent_key, comment_body, as_role=auditor_role)
        norm_bounce_target = (bounce_target or "implementation").strip().lower()
        transition_issue(parent_key, norm_bounce_target, as_role=auditor_role, config=config, h_config=h_config)
        assign_issue(parent_key, "agent1", as_role=auditor_role)
        print(f"FINAL_REVIEW_VERDICT: BOUNCE {next_bounce}/3 recorded on {parent_key} by {auditor_role} (returned to {bounce_target}, assigned to agent1).")
        return f"BOUNCE_{next_bounce}"
    else:
        comment_body = report_text if FINAL_REVIEW_ESCALATED_TAG in report_text else f"{FINAL_REVIEW_ESCALATED_TAG}\n\n{report_text}"
        add_comment(parent_key, comment_body, as_role=auditor_role)
        transition_issue(parent_key, "final review (human)", as_role=auditor_role, config=config, h_config=h_config)
        assign_issue(parent_key, "human", as_role=auditor_role)
        print(f"FINAL_REVIEW_VERDICT: ESCALATED AFTER 3 BOUNCES on {parent_key} by {auditor_role} (moved to Final Review (Human), assigned to human).")
        return "ESCALATED"



def collect_sprint_metrics_for_id(sprint_id, sprint_name, board_id, as_role="coordinator", config=None, h_config=None):
    if config is None:
        config = load_env_config()
    if h_config is None:
        h_config = load_hoechstmass_config()

    base = config.get("JIRA_URL", "").rstrip("/")
    sp_field = h_config.get("custom_fields", {}).get("story_points", "customfield_10016")
    url = f"{base}/rest/agile/1.0/sprint/{sprint_id}/issue?fields=summary,status,issuetype,priority,subtasks,comment,{sp_field}&maxResults=100"
    res = jira_request(url, role=as_role, config=config)
    issues = res.get("issues", [])

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


def resolve_sprint_object(base, board_id, sprint_arg=None, as_role="coordinator", config=None):
    if config is None:
        config = load_env_config()
    if sprint_arg:
        active_sprints = jira_request(f"{base}/rest/agile/1.0/board/{board_id}/sprint?state=active", role=as_role, config=config).get("values", [])
        for a in active_sprints:
            if str(a.get("id")) == str(sprint_arg) or a.get("name", "").strip() == str(sprint_arg).strip():
                return a
        if str(sprint_arg).isdigit():
            try:
                s_obj = jira_request(f"{base}/rest/agile/1.0/sprint/{sprint_arg}", role=as_role, config=config)
                if s_obj and "id" in s_obj:
                    return s_obj
            except Exception:
                pass
        start_at = 0
        while True:
            s_page = jira_request(f"{base}/rest/agile/1.0/board/{board_id}/sprint?startAt={start_at}&maxResults=50", role=as_role, config=config)
            vals = s_page.get("values", [])
            for s in vals:
                if str(s.get("id")) == str(sprint_arg) or s.get("name", "").strip() == str(sprint_arg).strip():
                    return s
            if s_page.get("isLast", True) or not vals:
                break
            start_at += len(vals)
        return None
    else:
        active_sprints = jira_request(f"{base}/rest/agile/1.0/board/{board_id}/sprint?state=active", role=as_role, config=config).get("values", [])
        dev_sprints = [s for s in active_sprints if s.get("name", "").strip().lower() != "human review"]
        return dev_sprints[0] if dev_sprints else (active_sprints[0] if active_sprints else None)


def extract_sprint_gate_records(parent_issues, sprint_info):
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


def export_gates(sprint_arg=None, out_path=None, as_role="coordinator", role=None, config=None, h_config=None):
    if role:
        as_role = role
    if config is None:
        config = load_env_config()
    if h_config is None:
        h_config = load_hoechstmass_config()

    base = config.get("JIRA_URL", "").rstrip("/")
    board_id = h_config.get("board_id", 1)

    target_sprint = resolve_sprint_object(base, board_id, sprint_arg=sprint_arg, as_role=as_role, config=config)
    if not target_sprint:
        s_desc = sprint_arg if sprint_arg else "active sprint"
        print(f"Error: Sprint '{s_desc}' not found on board {board_id}.", file=sys.stderr)
        sys.exit(1)

    s_id = target_sprint["id"]
    s_name = target_sprint["name"].strip()
    print(f"Exporting quality gate records for Sprint '{s_name}' (ID: {s_id})...")

    sp_field = h_config.get("custom_fields", {}).get("story_points", "customfield_10016")
    url = f"{base}/rest/agile/1.0/sprint/{s_id}/issue?fields=summary,status,issuetype,priority,subtasks,comment,{sp_field}&maxResults=100"
    res = jira_request(url, role=as_role, config=config)
    issues = res.get("issues", [])

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
                        c_res = jira_request(f"{base}/rest/api/2/issue/{st_key}/comment", role=as_role, config=config)
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


def qa_audit(sprint_arg=None, sample_size=2, out_path=None, as_role="coordinator", role=None, config=None, h_config=None, repo_root=None):
    if role:
        as_role = role
    if config is None:
        config = load_env_config()
    if h_config is None:
        h_config = load_hoechstmass_config()
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))

    base = config.get("JIRA_URL", "").rstrip("/")
    board_id = h_config.get("board_id", 1)

    target_sprint = resolve_sprint_object(base, board_id, sprint_arg=sprint_arg, as_role=as_role, config=config)
    if not target_sprint:
        s_desc = sprint_arg if sprint_arg else "active sprint"
        print(f"Error: Sprint '{s_desc}' not found on board {board_id}.", file=sys.stderr)
        sys.exit(1)

    s_id = target_sprint["id"]
    s_name = target_sprint["name"].strip()
    print(f"Conducting QA process-compliance audit for Sprint '{s_name}' (ID: {s_id})...")

    sp_field = h_config.get("custom_fields", {}).get("story_points", "customfield_10016")
    url = f"{base}/rest/agile/1.0/sprint/{s_id}/issue?fields=summary,status,issuetype,priority,subtasks,comment,{sp_field}&maxResults=100"
    res = jira_request(url, role=as_role, config=config)
    issues = res.get("issues", [])

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
                        c_res = jira_request(f"{base}/rest/api/2/issue/{st_key}/comment", role=as_role, config=config)
                        st["comments"] = c_res.get("comments", [])
                    except Exception:
                        st["comments"] = []

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
        auditor_role=as_role,
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
    as_role="coordinator",
    role=None,
    config=None,
    h_config=None,
    repo_root=None,
):
    """
    REQ-PRO-062.1 / REQ-PRO-062.2:
    Records an independent review (Human Ceremony 2 Sprint Review or Cross-Vendor AI Review)
    into docs/engineering/reviews/<sprint_or_review>.md.
    """
    if role and not as_role:
        as_role = role
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))

    if config is None:
        config = load_env_config()
    if h_config is None:
        h_config = load_hoechstmass_config()

    base = config['JIRA_URL'].rstrip('/')

    s_name = sprint_arg or "Active Sprint"
    s_id = "N/A"
    try:
        boards = jira_request(f"{base}/rest/agile/1.0/board", role=as_role, config=config).get("values", [])
        if boards:
            board_id = boards[0]["id"]
            target_sprint = resolve_sprint_object(base, board_id, sprint_arg=sprint_arg, as_role=as_role, config=config)
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
                issues = fetch_all_sprint_issues(base, s_id, "summary,issuetype", as_role=as_role, config=config)
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



def main():
    if len(sys.argv) < 2:
        print("Usage: jira_util.py [--as <role>] <command> [args...]")
        sys.exit(1)

    as_role = "agent1"
    args = sys.argv[1:]
    role_specified = False
    if args[0] == "--as" and len(args) >= 3:
        as_role = args[1]
        args = args[2:]
        role_specified = True
    elif args[0].startswith("--as="):
        as_role = args[0].split("=", 1)[1]
        args = args[1:]
        role_specified = True

    if not role_specified:
        env_actor = os.environ.get("JIRA_ACTOR")
        if env_actor:
            as_role = env_actor

    HUMAN_ROLES = {"human", "user", "rainer"}
    if as_role.lower() in HUMAN_ROLES:
        print("Error: 'human' role cannot be selected via agent CLI or environment variable. "
              "Human actions must use interactive confirmation or the Jira web UI.", file=sys.stderr)
        sys.exit(1)
    if as_role not in VALID_ROLES:
        print(f"Error: Invalid role '{as_role}'. Recognized roles: {', '.join(VALID_ROLES)}", file=sys.stderr)
        sys.exit(1)

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
    elif cmd in ("final-review-verdict", "final_review_verdict"):
        if len(args) < 4:
            print("Usage: jira_util.py final-review-verdict <PARENT_KEY> <PASS|BOUNCE> <REPORT_OR_@FILE> [--bounce-target=...]", file=sys.stderr)
            sys.exit(1)
        b_target = "implementation"
        idx = 4
        while idx < len(args):
            a = args[idx]
            if a.startswith("--bounce-target="):
                b_target = a.split("=", 1)[1]
            elif a == "--bounce-target" and idx + 1 < len(args):
                b_target = args[idx + 1]
                idx += 1
            idx += 1
        final_review_verdict(args[1], args[2], args[3], bounce_target=b_target, role="agent2")
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
    elif cmd in ("export-gates", "export_gates"):
        sprint_arg = None
        out_arg = None
        idx = 1
        while idx < len(args):
            a = args[idx]
            if a.startswith("--sprint="):
                sprint_arg = a.split("=", 1)[1]
            elif a == "--sprint" and idx + 1 < len(args):
                sprint_arg = args[idx + 1]
                idx += 1
            elif a.startswith("--out="):
                out_arg = a.split("=", 1)[1]
            elif a == "--out" and idx + 1 < len(args):
                out_arg = args[idx + 1]
                idx += 1
            elif not a.startswith("--") and not sprint_arg:
                sprint_arg = a
            idx += 1
        export_gates(sprint_arg=sprint_arg, out_path=out_arg, as_role=as_role)
    elif cmd in ("qa-audit", "qa_audit"):
        sprint_arg = None
        sample_arg = 2
        out_arg = None
        idx = 1
        while idx < len(args):
            a = args[idx]
            if a.startswith("--sprint="):
                sprint_arg = a.split("=", 1)[1]
            elif a == "--sprint" and idx + 1 < len(args):
                sprint_arg = args[idx + 1]
                idx += 1
            elif a.startswith("--sample="):
                sample_arg = int(a.split("=", 1)[1])
            elif a == "--sample" and idx + 1 < len(args):
                sample_arg = int(args[idx + 1])
                idx += 1
            elif a.startswith("--out="):
                out_arg = a.split("=", 1)[1]
            elif a == "--out" and idx + 1 < len(args):
                out_arg = args[idx + 1]
                idx += 1
            elif not a.startswith("--") and not sprint_arg:
                sprint_arg = a
            idx += 1
        qa_audit(sprint_arg=sprint_arg, sample_size=sample_arg, out_path=out_arg, as_role=as_role)
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
        while idx < len(args):
            a = args[idx]
            if a.startswith("--sprint="):
                sprint_arg = a.split("=", 1)[1]
            elif a == "--sprint" and idx + 1 < len(args):
                sprint_arg = args[idx + 1]
                idx += 1
            elif a.startswith("--ticket="):
                ticket_arg = a.split("=", 1)[1]
            elif a == "--ticket" and idx + 1 < len(args):
                ticket_arg = args[idx + 1]
                idx += 1
            elif a.startswith("--reviewer="):
                reviewer_arg = a.split("=", 1)[1]
            elif a == "--reviewer" and idx + 1 < len(args):
                reviewer_arg = args[idx + 1]
                idx += 1
            elif a.startswith("--vendor="):
                vendor_arg = a.split("=", 1)[1]
            elif a == "--vendor" and idx + 1 < len(args):
                vendor_arg = args[idx + 1]
                idx += 1
            elif a.startswith("--model="):
                model_arg = a.split("=", 1)[1]
            elif a == "--model" and idx + 1 < len(args):
                model_arg = args[idx + 1]
                idx += 1
            elif a.startswith("--version="):
                version_arg = a.split("=", 1)[1]
            elif a == "--version" and idx + 1 < len(args):
                version_arg = args[idx + 1]
                idx += 1
            elif a.startswith("--scope="):
                scope_arg = a.split("=", 1)[1]
            elif a == "--scope" and idx + 1 < len(args):
                scope_arg = args[idx + 1]
                idx += 1
            elif a.startswith("--verdict="):
                verdict_arg = a.split("=", 1)[1]
            elif a == "--verdict" and idx + 1 < len(args):
                verdict_arg = args[idx + 1]
                idx += 1
            elif a.startswith("--findings="):
                findings_arg = a.split("=", 1)[1]
            elif a == "--findings" and idx + 1 < len(args):
                findings_arg = args[idx + 1]
                idx += 1
            elif a.startswith("--out="):
                out_arg = a.split("=", 1)[1]
            elif a == "--out" and idx + 1 < len(args):
                out_arg = args[idx + 1]
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
            as_role=as_role,
        )
    else:
        print(f"Command '{cmd}' ready in parameterized client.")


if __name__ == "__main__":
    main()
