#!/usr/bin/env python3
import urllib.request
import base64
import json
import os
import sys
from urllib.parse import urlencode

# Transitions for ATT project
TRANSITIONS = {
    "todo": "11",
    "in_progress": "21",
    "in_review": "31",
    "done": "41"
}

def get_config():
    env_file = os.path.join(os.path.dirname(__file__), "..", ".env.jira")
    config = {}
    if not os.path.exists(env_file):
        print(f"Error: {env_file} not found.")
        sys.exit(1)
    with open(env_file, "r") as f:
        for line in f:
            if "=" in line and not line.startswith("#"):
                k, v = line.strip().split("=", 1)
                config[k] = v
    return config

def get_headers(config):
    auth_str = f"{config['JIRA_USER']}:{config['JIRA_TOKEN']}"
    encoded_auth = base64.b64encode(auth_str.encode("ascii")).decode("ascii")
    return {
        "Authorization": f"Basic {encoded_auth}",
        "Accept": "application/json",
        "Content-Type": "application/json"
    }

def jira_request(url, method="GET", payload=None, is_binary=False):
    config = get_config()
    headers = get_headers(config)
    data = json.dumps(payload).encode("utf-8") if payload else None

    req = urllib.request.Request(url, data=data, method=method)
    for k, v in headers.items():
        req.add_header(k, v)

    try:
        with urllib.request.urlopen(req) as response:
            if is_binary:
                return response.read()
            body = response.read().decode("utf-8")
            return json.loads(body) if body else {}
    except Exception as e:
        if hasattr(e, "read"):
            print(f"API Error: {e.read().decode('utf-8')}")
        raise e

def list_sprint_issues():
    config = get_config()
    # 1. Find the board
    boards = jira_request(f"{config['JIRA_URL']}/rest/agile/1.0/board")["values"]
    board_id = boards[0]["id"]

    # 2. Find active sprint
    sprints = jira_request(f"{config['JIRA_URL']}/rest/agile/1.0/board/{board_id}/sprint?state=active")["values"]
    if not sprints:
        print("No active sprint found.")
        return
    sprint_id = sprints[0]["id"]
    print(f"Active Sprint: {sprints[0]['name']}")

    # 3. Get issues
    issues = jira_request(f"{config['JIRA_URL']}/rest/agile/1.0/sprint/{sprint_id}/issue?fields=summary,status,issuetype")["issues"]
    for i in issues:
        itype = i['fields']['issuetype']['name']
        print(f"{i['key']}: [{itype}] {i['fields']['summary']} [{i['fields']['status']['name']}]")

def show_issue(issue_key):
    config = get_config()
    url = f"{config['JIRA_URL']}/rest/api/2/issue/{issue_key}?fields=summary,description,comment,attachment,parent,issuetype"
    issue = jira_request(url)

    itype = issue['fields']['issuetype']['name']
    print(f"h1. {issue['key']}: [{itype}] {issue['fields']['summary']}")

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
            epic = jira_request(epic_url)
            epic_desc = epic['fields'].get('description', 'No description')
            print(f"\n*Epic Description*:\n{epic_desc}")

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

def download_attachment(url, filename):
    print(f"Downloading {filename}...")
    content = jira_request(url, is_binary=True)

    # Save to a temporary or docs folder.
    # For now, let's assume current directory or a specific 'attachments' dir
    os.makedirs("docs/attachments", exist_ok=True)
    path = os.path.join("docs/attachments", filename)
    with open(path, "wb") as f:
        f.write(content)
    print(f"Saved to {path}")

def download_all_attachments(issue_key):
    config = get_config()
    url = f"{config['JIRA_URL']}/rest/api/2/issue/{issue_key}?fields=attachment"
    issue = jira_request(url)
    attachments = issue['fields'].get('attachment', [])
    if not attachments:
        print(f"No attachments found for {issue_key}.")
        return

    print(f"Found {len(attachments)} attachments for {issue_key}.")
    for a in attachments:
        download_attachment(a['content'], a['filename'])

def transition_issue(issue_key, status_name):
    config = get_config()
    trans_id = TRANSITIONS.get(status_name)
    if not trans_id:
        print(f"Error: Unknown transition '{status_name}'. Use: {list(TRANSITIONS.keys())}")
        return

    url = f"{config['JIRA_URL']}/rest/api/3/issue/{issue_key}/transitions"
    jira_request(url, method="POST", payload={"transition": {"id": trans_id}})
    print(f"Successfully moved {issue_key} to {status_name}.")

def add_comment(issue_key, text):
    config = get_config()
    # Use API v2 to support standard Jira Wiki Markup (e.g., h1., {code}, etc.)
    url = f"{config['JIRA_URL']}/rest/api/2/issue/{issue_key}/comment"

    # Prefix the comment to identify the AI Agent
    identity_prefix = "[Automated comment by AI Agent]\n\n"
    full_text = identity_prefix + text

    payload = {
        "body": full_text
    }
    jira_request(url, method="POST", payload=payload)
    print(f"Comment added to {issue_key}.")

def search_issues(jql):
    config = get_config()
    # Using API v3 POST for search as GET might be deprecated or removed
    url = f"{config['JIRA_URL']}/rest/api/3/search/jql"
    payload = {
        "jql": jql,
        "fields": ["summary", "status", "issuetype"]
    }
    data = jira_request(url, method="POST", payload=payload)
    for i in data.get("issues", []):
        itype = i['fields']['issuetype']['name']
        print(f"{i['key']}: [{itype}] {i['fields']['summary']} [{i['fields']['status']['name']}]")

def update_issue_description(issue_key, description):
    config = get_config()
    url = f"{config['JIRA_URL']}/rest/api/2/issue/{issue_key}"
    payload = {
        "fields": {
            "description": description
        }
    }
    jira_request(url, method="PUT", payload=payload)
    print(f"Description updated for {issue_key}.")

def create_subtask(parent_key, summary, description):
    config = get_config()
    url = f"{config['JIRA_URL']}/rest/api/2/issue"
    payload = {
        "fields": {
            "project": {"key": "ATT"},
            "parent": {"key": parent_key},
            "summary": summary,
            "description": description,
            "issuetype": {"id": "10002"}  # Subtask ID
        }
    }
    data = jira_request(url, method="POST", payload=payload)
    print(f"Sub-task {data['key']} created for parent {parent_key}.")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: jira_util.py [list | show KEY | move KEY todo|in_progress|in_review|done | comment KEY TEXT | download URL FILENAME | download-all KEY | search JQL | update-desc KEY TEXT | create-subtask PARENT_KEY SUMMARY DESC]")
        sys.exit(1)

    cmd = sys.argv[1]
    if cmd == "list":
        list_sprint_issues()
    elif cmd == "show" and len(sys.argv) == 3:
        show_issue(sys.argv[2])
    elif cmd == "download" and len(sys.argv) == 4:
        download_attachment(sys.argv[2], sys.argv[3])
    elif cmd == "download-all" and len(sys.argv) == 3:
        download_all_attachments(sys.argv[2])
    elif cmd == "move" and len(sys.argv) == 4:
        transition_issue(sys.argv[2], sys.argv[3])
    elif cmd == "comment" and len(sys.argv) == 4:
        add_comment(sys.argv[2], sys.argv[3])
    elif cmd == "search" and len(sys.argv) == 3:
        search_issues(sys.argv[2])
    elif cmd == "update-desc" and len(sys.argv) == 4:
        update_issue_description(sys.argv[2], sys.argv[3])
    elif cmd == "create-subtask" and len(sys.argv) == 5:
        create_subtask(sys.argv[2], sys.argv[3], sys.argv[4])
    else:
        print("Invalid command or arguments.")
