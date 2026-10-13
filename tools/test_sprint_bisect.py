import unittest
from unittest.mock import patch, MagicMock
import os
import sys

# Add project root to sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from tools.sprint_bisect import find_sprint_milestones, APP_PACKAGE, MAIN_ACTIVITY, APK_PATH


class TestSprintBisect(unittest.TestCase):

    @patch("tools.sprint_bisect.run_git")
    def test_find_sprint_milestones_parsing(self, mock_run_git):
        fake_log = (
            "0773a9f7|2026-10-09|docs: author Sprint Review & Retrospective for Sprint 2026-41.5 (ATT-2867)\n"
            "813e94ca|2026-10-09|docs: author Sprint Review & Retrospective for Sprint 2026-41.4 (ATT-2764)\n"
            "702743c1|2026-10-08|docs: author Sprint Review & Retrospective for 2026-41.3 (ATT-2660)\n"
            "a29799c3|2026-10-03|Merge tag 'V4.9.38__262' into develop\n"
        )
        mock_run_git.return_value = fake_log

        milestones = find_sprint_milestones(limit=10)
        self.assertEqual(len(milestones), 4)

        self.assertEqual(milestones[0]["sprint"], "Sprint 2026-41.5")
        self.assertEqual(milestones[0]["sha"], "0773a9f7")
        self.assertEqual(milestones[0]["type"], "sprint")

        self.assertEqual(milestones[1]["sprint"], "Sprint 2026-41.4")
        self.assertEqual(milestones[2]["sprint"], "Sprint 2026-41.3")
        self.assertEqual(milestones[3]["sprint"], "V4.9.38")
        self.assertEqual(milestones[3]["type"], "release")

    @patch("tools.sprint_bisect.run_git")
    def test_find_sprint_milestones_deduplication(self, mock_run_git):
        fake_log = (
            "11111111|2026-10-09|docs: author Sprint Review & Retrospective for Sprint 2026-41.5\n"
            "22222222|2026-10-09|docs(retro): Sprint 2026-41.5 details\n"
        )
        mock_run_git.return_value = fake_log
        milestones = find_sprint_milestones(limit=10)
        self.assertEqual(len(milestones), 1)
        self.assertEqual(milestones[0]["sha"], "11111111")

    def test_package_and_activity_constants(self):
        self.assertEqual(APP_PACKAGE, "com.atrainingtracker")
        self.assertEqual(MAIN_ACTIVITY, "de.rainerblind.trainingtracker.activities.MainActivityWithNavigation")
        self.assertTrue(APK_PATH.endswith(".apk"))


if __name__ == "__main__":
    unittest.main()
