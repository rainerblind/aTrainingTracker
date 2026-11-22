#!/usr/bin/env python3
"""Unit tests for tools/jira_created_vs_resolved.py."""

from datetime import date
import json
from pathlib import Path
import sys
import unittest

TOOLS_DIR = Path(__file__).resolve().parent
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

import jira_created_vs_resolved as jcr  # noqa: E402


def _make_raw(
    key: str,
    created: str,
    resolved: str | None = None,
    issue_type: str = "Bug",
    status_name: str = "Zu erledigen",
    category: str = "Zu erledigen",
    sp: float | None = 2.0,
) -> dict:
    return {
        "key": key,
        "fields": {
            "created": created,
            "resolutiondate": resolved,
            "issuetype": {"name": issue_type},
            "status": {
                "name": status_name,
                "statusCategory": {"name": category},
            },
            "customfield_10016": sp,
        },
    }


class TestJiraCreatedVsResolved(unittest.TestCase):
    def setUp(self) -> None:
        self.raw_issues = [
            _make_raw(
                "ATT-1",
                "2026-11-10T09:00:00.000+0100",
                "2026-11-11T14:00:00.000+0100",
                "Feature",
                "Erledigt",
                "Fertig",
                3.0,
            ),
            _make_raw(
                "ATT-2",
                "2026-11-10T10:00:00.000+0100",
                None,
                "Bug",
                "Final Review (Human)",
                "In Arbeit",
                2.0,
            ),
            _make_raw(
                "ATT-3",
                "2026-11-12T08:30:00.000+0100",
                None,
                "Verbesserung",
                "Zu erledigen",
                "Zu erledigen",
                1.0,
            ),
            _make_raw(
                "ATT-4",
                "2026-11-12T18:00:00.000+0100",
                "2026-11-12T19:00:00.000+0100",
                "Bug",
                "Erledigt",
                "Fertig",
                None,
            ),
        ]
        self.issues = jcr.normalize_issues(self.raw_issues)

    def test_daily_series_fills_gaps_and_tracks_open_backlog(self) -> None:
        series = jcr.build_daily_series(self.issues, end_date=date(2026, 11, 13))
        self.assertEqual(
            [row["date"] for row in series],
            ["2026-11-10", "2026-11-11", "2026-11-12", "2026-11-13"],
        )
        # 2026-11-10: +2 created, +0 resolved => open 2
        self.assertEqual(series[0]["cum_created"], 2.0)
        self.assertEqual(series[0]["cum_resolved"], 0.0)
        self.assertEqual(series[0]["open_backlog"], 2.0)
        # 2026-11-11: +0 created, +1 resolved => open 1
        self.assertEqual(series[1]["cum_created"], 2.0)
        self.assertEqual(series[1]["cum_resolved"], 1.0)
        self.assertEqual(series[1]["open_backlog"], 1.0)
        # 2026-11-12: +2 created, +1 resolved => cum 4 / 2, open 2
        self.assertEqual(series[2]["cum_created"], 4.0)
        self.assertEqual(series[2]["cum_resolved"], 2.0)
        self.assertEqual(series[2]["open_backlog"], 2.0)
        # 2026-11-13: no changes, carries totals forward
        self.assertEqual(series[3]["open_backlog"], 2.0)

    def test_type_filter_and_story_points_mode(self) -> None:
        bug_counts = jcr.build_daily_series(
            self.issues, end_date=date(2026, 11, 12), issue_type_filter="Bug"
        )
        self.assertEqual(bug_counts[-1]["cum_created"], 2.0)
        self.assertEqual(bug_counts[-1]["cum_resolved"], 1.0)

        sp_series = jcr.build_daily_series(
            self.issues, end_date=date(2026, 11, 12), use_story_points=True
        )
        self.assertEqual(sp_series[-1]["cum_created"], 6.0)
        self.assertEqual(sp_series[-1]["cum_resolved"], 3.0)
        self.assertEqual(sp_series[-1]["open_backlog"], 3.0)

    def test_summary_splits_active_wip_and_todo_open(self) -> None:
        summary = jcr.build_summary(self.issues, today_str="2026-11-12")
        self.assertEqual(summary["total_created"], 4)
        self.assertEqual(summary["total_resolved"], 2)
        self.assertEqual(summary["total_open"], 2)
        self.assertEqual(summary["active_wip"], 1)
        self.assertEqual(summary["todo_open"], 1)
        self.assertEqual(summary["created_today"], 2)
        self.assertEqual(summary["resolved_today"], 1)

    def test_render_html_escapes_script_injection_and_embeds_payload(self) -> None:
        payload = jcr.build_report_payload(self.issues, today=date(2026, 11, 12))
        payload["summary"]["by_status"]["</script><bad>"] = 1
        rendered = jcr.render_html(payload)
        self.assertNotIn("</script><bad>", rendered)
        self.assertIn("\\u003c/script>", rendered)
        # Confirm payload JSON shape is present
        self.assertIn('"total_created": 4', rendered)
        self.assertIn('"Bug"', rendered)


if __name__ == "__main__":
    unittest.main()
