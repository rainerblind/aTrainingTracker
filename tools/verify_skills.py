#!/usr/bin/env python3
"""
Verification utility for Antigravity Modular Skills (.agents/skills/).
Validates directory structure, SKILL.md frontmatter, and deliverable templates (REQ-PRO-024 / TST-PRO-017).
"""

import os
import re
import sys

EXPECTED_SKILLS = [
    "stage1-analysis",
    "stage2-req-test-spec",
    "stage3-impl-plan",
    "stage4-implementation",
    "stage5-verification",
    "jira-workflow",
    "ui-designer",
    "brainstormer",
    "sprint-planner",
]

EXPECTED_TEMPLATES = {
    "stage1-analysis": ["templates/analysis_template.md"],
    "stage2-req-test-spec": ["templates/test_spec_template.md"],
    "stage3-impl-plan": ["templates/plan_template.md"],
    "stage5-verification": ["templates/walkthrough_template.md"],
    "brainstormer": ["templates/backlog_ticket_template.md"],
}

FRONTMATTER_PATTERN = re.compile(
    r"^---\s*\n(.*?)\n---\s*\n",
    re.DOTALL
)

def verify_frontmatter(content, expected_name):
    match = FRONTMATTER_PATTERN.match(content)
    if not match:
        return False, "Missing or malformed YAML frontmatter delimiters ('---')"
    frontmatter_text = match.group(1)
    
    # Check name
    name_match = re.search(r"^name:\s*([a-zA-Z0-9_-]+)\s*$", frontmatter_text, re.MULTILINE)
    if not name_match:
        return False, "Missing 'name' field in frontmatter"
    actual_name = name_match.group(1).strip()
    if actual_name != expected_name:
        return False, f"Frontmatter name '{actual_name}' does not match expected skill name '{expected_name}'"
        
    # Check description
    desc_match = re.search(r"^description:\s*(.+)$", frontmatter_text, re.MULTILINE)
    if not desc_match or not desc_match.group(1).strip():
        return False, "Missing or empty 'description' field in frontmatter"
        
    return True, None

def verify_skills(workspace_root=None):
    if workspace_root is None:
        workspace_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
        
    skills_root = os.path.join(workspace_root, ".agents", "skills")
    if not os.path.isdir(skills_root):
        print(f"ERROR: Skills root directory does not exist: {skills_root}", file=sys.stderr)
        return False

    errors = []
    print(f"Verifying Antigravity skills in {skills_root}...")

    for skill in EXPECTED_SKILLS:
        skill_dir = os.path.join(skills_root, skill)
        if not os.path.isdir(skill_dir):
            errors.append(f"[{skill}] Directory missing: {skill_dir}")
            continue

        skill_md = os.path.join(skill_dir, "SKILL.md")
        if not os.path.isfile(skill_md):
            errors.append(f"[{skill}] SKILL.md missing: {skill_md}")
            continue

        try:
            with open(skill_md, "r", encoding="utf-8") as f:
                content = f.read()
            valid, err = verify_frontmatter(content, skill)
            if not valid:
                errors.append(f"[{skill}] SKILL.md frontmatter invalid: {err}")
            elif len(content.strip()) < 100:
                errors.append(f"[{skill}] SKILL.md content is suspiciously short (< 100 chars)")
            else:
                print(f"  ✓ {skill} (SKILL.md valid)")
        except Exception as e:
            errors.append(f"[{skill}] Failed to read SKILL.md: {e}")

        # Check required templates
        if skill in EXPECTED_TEMPLATES:
            for tmpl_rel in EXPECTED_TEMPLATES[skill]:
                tmpl_path = os.path.join(skill_dir, tmpl_rel)
                if not os.path.isfile(tmpl_path):
                    errors.append(f"[{skill}] Required template missing: {tmpl_rel}")
                else:
                    try:
                        with open(tmpl_path, "r", encoding="utf-8") as tf:
                            tmpl_content = tf.read().strip()
                        if len(tmpl_content) < 50:
                            errors.append(f"[{skill}] Template {tmpl_rel} is empty or too short")
                        else:
                            print(f"    ✓ template: {tmpl_rel}")
                    except Exception as e:
                        errors.append(f"[{skill}] Failed to read template {tmpl_rel}: {e}")

    if errors:
        print("\nVerification FAILED with the following errors:", file=sys.stderr)
        for err in errors:
            print(f"  • {err}", file=sys.stderr)
        return False

    print(f"\nAll {len(EXPECTED_SKILLS)} skills and their required templates verified successfully!")
    return True

if __name__ == "__main__":
    success = verify_skills()
    sys.exit(0 if success else 1)
