#!/usr/bin/env python3
"""
tools/test_review_tool.py

Unit tests for tools/review_tool.py.
Verifies sandbox environment initialization, file staging, diff inspection,
review directive extraction (>>), and applying edits back to the workspace.
"""

import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, os.path.abspath(os.path.dirname(__file__)))

from review_tool import (
    DIRECTIVE_PATTERN,
    apply_reviews,
    ensure_reviews_env,
    extract_directives,
    find_repo_root,
    get_diff,
    stage_files,
)


class TestReviewTool(unittest.TestCase):

    def setUp(self):
        self.test_dir = tempfile.mkdtemp(prefix="att_review_test_")
        self.repo_root = Path(self.test_dir)
        # Initialize a git repo in the test directory
        subprocess.run(["git", "init"], cwd=str(self.repo_root), check=True, stdout=subprocess.DEVNULL)
        subprocess.run(["git", "config", "user.name", "TestUser"], cwd=str(self.repo_root), check=True)
        subprocess.run(["git", "config", "user.email", "test@example.com"], cwd=str(self.repo_root), check=True)

        # Create .git/info/exclude
        info_dir = self.repo_root / ".git" / "info"
        info_dir.mkdir(parents=True, exist_ok=True)
        (info_dir / "exclude").write_text("# exclude file\n", encoding="utf-8")

    def tearDown(self):
        shutil.rmtree(self.test_dir, ignore_errors=True)

    def test_ensure_reviews_env_creates_isolated_git_repo(self):
        reviews_dir = ensure_reviews_env(repo_root=self.repo_root)
        self.assertTrue(reviews_dir.exists())
        self.assertTrue((reviews_dir / ".git").exists())

        exclude_content = (self.repo_root / ".git" / "info" / "exclude").read_text(encoding="utf-8")
        self.assertIn(".reviews/", exclude_content)

    def test_stage_files_copies_and_commits_baseline(self):
        # Create a sample source file in repo_root
        src_file = self.repo_root / "src" / "Sample.kt"
        src_file.parent.mkdir(parents=True, exist_ok=True)
        src_file.write_text("class Sample {\n    fun hello() = true\n}\n", encoding="utf-8")

        staged = stage_files([str(src_file)], open_editor=False, repo_root=self.repo_root)
        self.assertEqual(len(staged), 1)
        self.assertEqual(staged[0], "src/Sample.kt")

        staged_target = self.repo_root / ".reviews" / "src" / "Sample.kt"
        self.assertTrue(staged_target.exists())
        self.assertEqual(staged_target.read_text(encoding="utf-8"), src_file.read_text(encoding="utf-8"))

    def test_diff_and_extract_directives(self):
        src_file = self.repo_root / "docs" / "spec.md"
        src_file.parent.mkdir(parents=True, exist_ok=True)
        src_file.write_text("# Title\nLine 1\n", encoding="utf-8")

        stage_files([str(src_file)], open_editor=False, repo_root=self.repo_root)

        # Human edits file in .reviews/ adding a directive
        staged_target = self.repo_root / ".reviews" / "docs" / "spec.md"
        staged_target.write_text(
            "# Title\nLine 1\n// >> Refactor this section to clarify edge cases\nLine 2\n",
            encoding="utf-8",
        )

        diff = get_diff(repo_root=self.repo_root)
        self.assertIn("// >> Refactor this section", diff)

        directives = extract_directives(diff_text=diff, repo_root=self.repo_root)
        self.assertEqual(len(directives), 1)
        self.assertEqual(directives[0]["file"], "docs/spec.md")
        self.assertIn("Refactor this section to clarify edge cases", directives[0]["directive"])

    def test_apply_reviews_copies_back_to_workspace(self):
        src_file = self.repo_root / "app" / "Config.py"
        src_file.parent.mkdir(parents=True, exist_ok=True)
        src_file.write_text("DEBUG = False\n", encoding="utf-8")

        stage_files([str(src_file)], open_editor=False, repo_root=self.repo_root)

        # Human directly changes code in .reviews/
        staged_target = self.repo_root / ".reviews" / "app" / "Config.py"
        staged_target.write_text("DEBUG = True\n", encoding="utf-8")

        applied = apply_reviews(repo_root=self.repo_root, reload_editor=False)
        self.assertEqual(applied, ["app/Config.py"])
        self.assertEqual(src_file.read_text(encoding="utf-8"), "DEBUG = True\n")


if __name__ == "__main__":
    unittest.main()
