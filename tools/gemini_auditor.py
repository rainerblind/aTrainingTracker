#!/usr/bin/env python3
import os
import sys
import json
import subprocess
import urllib.request
from urllib.parse import urlencode

# Import jira_util helpers
sys.path.append(os.path.dirname(__file__))
import jira_util

DEFAULT_MODEL = "gemini-3-flash-preview"

def get_gemini_api_key():
    key = os.environ.get("GEMINI_API_KEY")
    if key:
        return key
    env_file = os.path.join(os.path.dirname(__file__), "..", ".env.gemini")
    if os.path.exists(env_file):
        with open(env_file, "r") as f:
            for line in f:
                if "=" in line and not line.startswith("#"):
                    k, v = line.strip().split("=", 1)
                    if k == "GEMINI_API_KEY":
                        return v
    print("Error: GEMINI_API_KEY not found in environment or .env.gemini")
    sys.exit(1)

def run_git(cmd_list):
    try:
        res = subprocess.run(["git"] + cmd_list, capture_output=True, text=True, check=True)
        return res.stdout
    except Exception as e:
        return f"git error: {e}"

def call_gemini(prompt, system_instruction=None, model=DEFAULT_MODEL):
    api_key = get_gemini_api_key()
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}"
    
    contents = [{
        "role": "user",
        "parts": [{"text": prompt}]
    }]
    
    payload = {
        "contents": contents,
        "generationConfig": {
            "temperature": 0.2,
            "maxOutputTokens": 2048
        }
    }
    
    if system_instruction:
        payload["systemInstruction"] = {
            "parts": [{"text": system_instruction}]
        }
        
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST", headers={"Content-Type": "application/json"})
    
    with urllib.request.urlopen(req) as resp:
        body = resp.read().decode("utf-8")
        res_json = json.loads(body)
        
    try:
        text = res_json["candidates"][0]["content"]["parts"][0]["text"]
        return text
    except (KeyError, IndexError) as e:
        print("Unexpected Gemini response structure:", res_json)
        sys.exit(1)

def build_review_context(subtask_key, stage):
    config = jira_util.get_config()
    subtask_url = f"{config['JIRA_URL']}/rest/api/2/issue/{subtask_key}"
    subtask_data = jira_util.jira_request(subtask_url)
    
    subtask_summary = subtask_data["fields"]["summary"]
    subtask_desc = subtask_data["fields"].get("description") or ""
    parent = subtask_data["fields"].get("parent", {})
    parent_key = parent.get("key", "")
    parent_summary = parent.get("fields", {}).get("summary", "")
    
    # Git context
    current_branch = run_git(["branch", "--show-current"]).strip()
    recent_commits = run_git(["log", "-n", "3", "--oneline"])
    git_diff_summary = run_git(["diff", "--stat", "develop...HEAD"])
    
    # Relevant engineering docs
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    plan_files = []
    walkthrough_files = []
    
    plans_dir = os.path.join(repo_root, "docs", "engineering", "plans")
    if os.path.exists(plans_dir):
        for f in os.listdir(plans_dir):
            if parent_key in f or subtask_key in f:
                with open(os.path.join(plans_dir, f), "r") as pf:
                    plan_files.append((f, pf.read()[:3000]))
                    
    walkthroughs_dir = os.path.join(repo_root, "docs", "engineering", "walkthroughs")
    if os.path.exists(walkthroughs_dir):
        for f in os.listdir(walkthroughs_dir):
            if parent_key in f or subtask_key in f:
                with open(os.path.join(walkthroughs_dir, f), "r") as wf:
                    walkthrough_files.append((f, wf.read()[:3000]))

    context = f"""
=== JIRA TICKET CONTEXT ===
Parent Issue: {parent_key}: {parent_summary}
Sub-Task: {subtask_key}: {subtask_summary}
Stage: {stage.upper()}
Sub-Task Description:
{subtask_desc}

=== GIT REPOSITORY CONTEXT ===
Active Branch: {current_branch}
Recent Commits on Branch:
{recent_commits}

Diff Summary (vs develop):
{git_diff_summary}
"""
    if plan_files:
        for fname, content in plan_files:
            context += f"\n=== PLAN ARTIFACT: {fname} ===\n{content}\n"

    if walkthrough_files:
        for fname, content in walkthrough_files:
            context += f"\n=== WALKTHROUGH ARTIFACT: {fname} ===\n{content}\n"
            
    return context

def review_subtask(subtask_key, stage="rca", model=DEFAULT_MODEL, post_comment=True):
    print(f"Gathering context for {subtask_key} (Stage: {stage})...")
    context = build_review_context(subtask_key, stage)
    
    system_instruction = (
        "You are Agent 2 (Auditor), an elite senior Android software architect and quality assurance auditor. "
        "You are conducting a strict, independent audit gate review on a proposed engineering artifact or fix for the Android app 'aTrainingTracker'.\n"
        "Your role is to critically analyze root causes, architectural changes, test designs, safety invariants, and backward compatibility.\n"
        "You must output your evaluation in Jira markup format starting with:\n"
        f"h3. Agent 2 (External Auditor - {model}) Gate Review\n\n"
        "Followed by:\n"
        "*Evaluation*:\n"
        "1. *Root Cause / Architectural Soundness*: ...\n"
        "2. *Scope, Safety Invariants & Regressions*: ...\n"
        "3. *Verification & Testability*: ...\n\n"
        "*Verdict*: End with either '*RECOMMEND PASS*' or '*REQUEST CHANGES*' with a concise final recommendation."
    )
    
    prompt = f"""
Please perform a rigorous audit review for the following task at stage: {stage.upper()}.

{context}

Provide your detailed review according to your instructions.
"""
    print(f"Calling Gemini API ({model}) for independent review...")
    review_output = call_gemini(prompt, system_instruction=system_instruction, model=model)
    print("\n--- AUDIT REVIEW RESULT ---")
    print(review_output)
    print("---------------------------\n")
    
    if post_comment:
        print(f"Posting audit comment to Jira ticket {subtask_key}...")
        jira_util.add_comment(subtask_key, review_output)
        
        if "*RECOMMEND PASS*" in review_output:
            print(f"Auditor recommends PASS. Transitioning {subtask_key} to 'freigabe'...")
            try:
                jira_util.transition_issue(subtask_key, "freigabe")
            except Exception as e:
                print(f"Could not transition issue to freigabe: {e}")
        else:
            print(f"Auditor did NOT recommend pass. Leaving in current state for revisions.")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: gemini_auditor.py <SUBTASK_KEY> [stage: rca|plan|test_impl] [model]")
        sys.exit(1)
        
    subtask = sys.argv[1]
    stage_arg = sys.argv[2] if len(sys.argv) >= 3 else "rca"
    model_arg = sys.argv[3] if len(sys.argv) >= 4 else DEFAULT_MODEL
    
    review_subtask(subtask, stage=stage_arg, model=model_arg)
