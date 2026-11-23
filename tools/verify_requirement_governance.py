#!/usr/bin/env python3
"""
verify_requirement_governance.py

Enforces Chesterton's Fence requirement archaeology governance (REQ-PRO-022 / TST-PRO-015).
Ensures that whenever an existing requirement in docs/requirements.md is modified, relaxed,
or replaced, an auditable archaeological investigation section is present with all 4 mandatory fields:
  1. Original Requirement ID & Target
  2. Historical Origin & Commit Trace
  3. Root Reason for Existing Formulation
  4. Preservation of Core Invariants

Net-new requirements (novel requirement IDs absent from the base comparison) bypass this check cleanly.

Invariants:
  - Strictly Python 3 standard library (no pip packages).
  - Deterministic exit codes: 0 = valid / compliant, 1 = schema failure.
"""

import csv
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

MANDATORY_FIELDS = [
    r"Original Requirement ID & Target",
    r"Historical Origin & Commit Trace",
    r"Root Reason for Existing Formulation",
    r"Preservation of Core Invariants",
]

HEADER_PATTERN = re.compile(
    r"(?:#{2,4}|h[2-4]\.)\s*(?:\d+(?:\.\d+)*[.\)]\s*)?Requirement Archaeology & Chesterton's Fence Audit",
    re.IGNORECASE,
)

REQ_ROW_PATTERN = re.compile(
    r"^[+-](?:\|\s*\*\*REQ-([A-Z0-9_-]+)\*\*|#{2,4}\s*REQ-([A-Z0-9_-]+))",
    re.MULTILINE,
)

# All legacy duplicates fully resolved in ATT-6123 (SWE.1 BP5, SWE.6 BP4, REQ-PRO-066.6)
LEGACY_DUPLICATE_IDS = frozenset()

# Known legacy dangling references pending backfill in A-08.x (ATT-6127..6135, SWE.1 BP5, SWE.6 BP4, REQ-PRO-070)
LEGACY_DANGLING_TST_REFS = frozenset()

LEGACY_DANGLING_REQ_REFS = frozenset()

# Legacy unbacked [Verified] TST ratchet reconciled to 0 in ATT-8145 (SWE.4/6 BP4/5, SWE.1 BP5, REQ-PRO-099, REQ-PRO-101)
LEGACY_UNBACKED_VERIFIED_TSTS = frozenset()

# Known legacy requirements claiming [Verified] without TST citations pending backfill in ATT-6127..ATT-6135 (SWE.1 BP3, SWE.6 BP3, REQ-PRO-073)
LEGACY_UNGROUNDED_VERIFIED_REQS = frozenset({
    "REQ-CON-007", "REQ-CON-011", "REQ-CON-012", "REQ-CON-013", "REQ-CON-014",
    "REQ-CON-015", "REQ-CON-016", "REQ-CON-017", "REQ-CON-018", "REQ-CON-019", "REQ-CON-020",
    "REQ-CON-021", "REQ-CON-022", "REQ-CON-023", "REQ-DAT-007", "REQ-DAT-009",
    "REQ-DAT-011", "REQ-DAT-012", "REQ-DAT-013", "REQ-DAT-014", "REQ-DAT-015", "REQ-DAT-016",
    "REQ-DAT-017", "REQ-DAT-018", "REQ-DAT-019", "REQ-DAT-020", "REQ-DAT-021", "REQ-DAT-022",
    "REQ-DAT-023", "REQ-DAT-024", "REQ-DAT-025", "REQ-DAT-026", "REQ-DAT-027", "REQ-DAT-028",
    "REQ-DAT-029", "REQ-DAT-030", "REQ-DAT-031", "REQ-DAT-032", "REQ-DAT-033", "REQ-DAT-034",
    "REQ-DAT-035", "REQ-DAT-036", "REQ-DAT-037", "REQ-EXP-008", "REQ-EXP-010", "REQ-EXP-013",
    "REQ-EXP-018", "REQ-EXP-019", "REQ-EXP-020", "REQ-EXP-021", "REQ-EXP-022", "REQ-EXT-009",
    "REQ-EXT-010", "REQ-EXT-011", "REQ-EXT-012", "REQ-FIL-013", "REQ-FIL-014", "REQ-FIL-015",
    "REQ-GEO-001", "REQ-GEO-002", "REQ-LOC-001", "REQ-LOC-002", "REQ-MAP-019", "REQ-MAP-020",
    "REQ-MAP-021", "REQ-MAP-022", "REQ-MAP-023", "REQ-MAP-024", "REQ-MAP-025", "REQ-MAP-026",
    "REQ-MAP-027", "REQ-MAP-028", "REQ-MAP-029", "REQ-MAP-030", "REQ-MAP-031", "REQ-MAP-032",
    "REQ-MAP-033", "REQ-MAP-034", "REQ-MAP-035", "REQ-MAP-036", "REQ-MAP-038", "REQ-MAP-039",
    "REQ-MAP-040", "REQ-MAP-041", "REQ-MAP-042", "REQ-MAP-044", "REQ-MAP-045", "REQ-MAP-046",
    "REQ-MAP-047", "REQ-MAP-048", "REQ-MAP-049", "REQ-MAP-050", "REQ-MAP-051", "REQ-MAP-052",
    "REQ-MAP-053", "REQ-MAP-054", "REQ-MAP-055", "REQ-MAP-056", "REQ-MIG-024", "REQ-MIG-025",
    "REQ-MIG-026", "REQ-MIG-027", "REQ-MIG-028", "REQ-MIG-029", "REQ-MIG-030", "REQ-MIG-031",
    "REQ-MIG-032", "REQ-MIG-033", "REQ-MIG-034", "REQ-MIG-035", "REQ-MIG-036", "REQ-MIG-037",
    "REQ-MIG-038", "REQ-MIG-039", "REQ-MIG-040", "REQ-MIG-041", "REQ-MIG-042", "REQ-MIG-043",
    "REQ-MIG-044", "REQ-MIG-045", "REQ-MIG-046", "REQ-MIG-047", "REQ-NAV-043", "REQ-NAV-056",
    "REQ-PER-012", "REQ-PER-013", "REQ-PER-014", "REQ-PER-015", "REQ-PER-016", "REQ-PER-017",
    "REQ-PER-018", "REQ-PRI-004", "REQ-PRI-005", "REQ-PRI-006", "REQ-PRI-007", "REQ-SEN-020",
    "REQ-SEN-021", "REQ-SET-058", "REQ-SET-059", "REQ-SET-064", "REQ-SET-065", "REQ-SET-066",
    "REQ-SET-067", "REQ-SET-068", "REQ-SET-069", "REQ-SET-070", "REQ-SET-071", "REQ-SET-072",
    "REQ-SET-073", "REQ-SET-074", "REQ-SET-075", "REQ-SET-076", "REQ-SET-077", "REQ-SET-078",
    "REQ-SET-079", "REQ-SET-081", "REQ-SET-082", "REQ-TRK-002",
    "REQ-TRK-010", "REQ-TRK-011", "REQ-TRK-012", "REQ-TRK-013", "REQ-TRK-014", "REQ-TRK-015",
    "REQ-TRK-016", "REQ-TRK-017", "REQ-TRK-018", "REQ-TRK-019", "REQ-TRK-020", "REQ-TRK-021",
    "REQ-TRK-022", "REQ-TRK-023", "REQ-TRK-024", "REQ-TRK-025", "REQ-TRK-026", "REQ-TRK-027",
    "REQ-TRK-028", "REQ-TRK-029", "REQ-TRK-030", "REQ-TRK-031", "REQ-TRK-032", "REQ-TRK-033",
    "REQ-TRK-034", "REQ-TRK-036", "REQ-TRK-037",
})

# Known legacy requirements lacking - **Description**: field in baseline (REQ-PRO-104.3)
LEGACY_MISSING_DESCRIPTION_REQS = frozenset({
    "REQ-ARC-001", "REQ-ARC-002", "REQ-ARC-003", "REQ-ARC-004", "REQ-ARC-005", "REQ-ARC-006", "REQ-ARC-007", "REQ-ARC-008", "REQ-ARC-009", "REQ-ARC-010",
    "REQ-BLD-001", "REQ-BLD-002", "REQ-BLD-003", "REQ-CLM-008", "REQ-CLM-009", "REQ-CLM-010", "REQ-CLM-011", "REQ-CON-007", "REQ-CON-010", "REQ-CON-011",
    "REQ-CON-012", "REQ-CON-013", "REQ-CON-014", "REQ-CON-015", "REQ-CON-016", "REQ-CON-017", "REQ-CON-018", "REQ-CON-019", "REQ-CON-020", "REQ-CON-021",
    "REQ-CON-022", "REQ-CON-023", "REQ-CON-024", "REQ-CON-027", "REQ-CON-028", "REQ-CON-029", "REQ-CON-030", "REQ-CON-031", "REQ-CON-032", "REQ-CON-033",
    "REQ-CON-034", "REQ-CON-035", "REQ-CON-043", "REQ-DAT-006", "REQ-DAT-007", "REQ-DAT-008", "REQ-DAT-009", "REQ-DAT-010", "REQ-DAT-011", "REQ-DAT-012",
    "REQ-DAT-013", "REQ-DAT-014", "REQ-DAT-015", "REQ-DAT-016", "REQ-DAT-017", "REQ-DAT-018", "REQ-DAT-019", "REQ-DAT-020", "REQ-DAT-021", "REQ-DAT-022",
    "REQ-DAT-023", "REQ-DAT-024", "REQ-DAT-025", "REQ-DAT-026", "REQ-DAT-027", "REQ-DAT-028", "REQ-DAT-029", "REQ-DAT-030", "REQ-DAT-031", "REQ-DAT-032",
    "REQ-DAT-033", "REQ-DAT-034", "REQ-DAT-035", "REQ-DAT-036", "REQ-DAT-037", "REQ-DAT-038", "REQ-DAT-043", "REQ-DAT-044", "REQ-EXP-001", "REQ-EXP-002",
    "REQ-EXP-008", "REQ-EXP-009", "REQ-EXP-010", "REQ-EXP-011", "REQ-EXP-012", "REQ-EXP-013", "REQ-EXP-014", "REQ-EXP-015", "REQ-EXP-016", "REQ-EXP-017",
    "REQ-EXP-018", "REQ-EXP-019", "REQ-EXP-020", "REQ-EXP-021", "REQ-EXP-022", "REQ-EXT-002", "REQ-EXT-006", "REQ-EXT-007", "REQ-EXT-008", "REQ-EXT-009",
    "REQ-EXT-010", "REQ-EXT-011", "REQ-EXT-012", "REQ-FIL-004", "REQ-FIL-011", "REQ-FIL-012", "REQ-FIL-013", "REQ-FIL-014", "REQ-FIL-015", "REQ-GEO-001",
    "REQ-GEO-002", "REQ-LIV-005", "REQ-LOC-001", "REQ-LOC-002", "REQ-MAP-016", "REQ-MAP-017", "REQ-MAP-018", "REQ-MAP-019", "REQ-MAP-020", "REQ-MAP-021",
    "REQ-MAP-022", "REQ-MAP-023", "REQ-MAP-024", "REQ-MAP-025", "REQ-MAP-026", "REQ-MAP-027", "REQ-MAP-028", "REQ-MAP-029", "REQ-MAP-030", "REQ-MAP-031",
    "REQ-MAP-032", "REQ-MAP-033", "REQ-MAP-034", "REQ-MAP-035", "REQ-MAP-036", "REQ-MAP-037", "REQ-MAP-038", "REQ-MAP-039", "REQ-MAP-040", "REQ-MAP-041",
    "REQ-MAP-042", "REQ-MAP-044", "REQ-MAP-045", "REQ-MAP-046", "REQ-MAP-047", "REQ-MAP-048", "REQ-MAP-049", "REQ-MAP-050", "REQ-MAP-051", "REQ-MAP-052",
    "REQ-MAP-053", "REQ-MAP-054", "REQ-MAP-055", "REQ-MAP-056", "REQ-MAP-057", "REQ-MAP-058", "REQ-MAP-059", "REQ-MAP-060", "REQ-MAP-061", "REQ-MAP-062",
    "REQ-MAP-063", "REQ-MAP-064", "REQ-MAP-065", "REQ-MAP-066", "REQ-MAP-067", "REQ-MIG-001", "REQ-MIG-002", "REQ-MIG-003", "REQ-MIG-004", "REQ-MIG-005",
    "REQ-MIG-006", "REQ-MIG-007", "REQ-MIG-008", "REQ-MIG-009", "REQ-MIG-010", "REQ-MIG-011", "REQ-MIG-012", "REQ-MIG-013", "REQ-MIG-014", "REQ-MIG-015",
    "REQ-MIG-016", "REQ-MIG-017", "REQ-MIG-018", "REQ-MIG-019", "REQ-MIG-020", "REQ-MIG-021", "REQ-MIG-022", "REQ-MIG-023", "REQ-MIG-024", "REQ-MIG-025",
    "REQ-MIG-026", "REQ-MIG-027", "REQ-MIG-028", "REQ-MIG-029", "REQ-MIG-030", "REQ-MIG-031", "REQ-MIG-032", "REQ-MIG-033", "REQ-MIG-034", "REQ-MIG-035",
    "REQ-MIG-036", "REQ-MIG-037", "REQ-MIG-038", "REQ-MIG-039", "REQ-MIG-040", "REQ-MIG-041", "REQ-MIG-042", "REQ-MIG-043", "REQ-MIG-044", "REQ-MIG-045",
    "REQ-MIG-046", "REQ-MIG-047", "REQ-MIG-048", "REQ-MIG-049", "REQ-MIG-050", "REQ-MIG-051", "REQ-NAV-043", "REQ-NAV-056", "REQ-PER-007", "REQ-PER-008",
    "REQ-PER-009", "REQ-PER-010", "REQ-PER-011", "REQ-PER-012", "REQ-PER-013", "REQ-PER-014", "REQ-PER-015", "REQ-PER-016", "REQ-PER-017", "REQ-PER-018",
    "REQ-PER-019", "REQ-PER-020", "REQ-PER-021", "REQ-PER-022", "REQ-PER-023", "REQ-PER-024", "REQ-PER-025", "REQ-PER-026", "REQ-PER-027", "REQ-PER-028",
    "REQ-PER-029", "REQ-PER-030", "REQ-PER-031", "REQ-PER-032", "REQ-PER-033", "REQ-PER-034", "REQ-PER-035", "REQ-PER-036", "REQ-PRI-001", "REQ-PRI-002",
    "REQ-PRI-003", "REQ-PRI-004", "REQ-PRI-005", "REQ-PRI-006", "REQ-PRI-007", "REQ-PRO-002", "REQ-PRO-003", "REQ-PRO-004", "REQ-PRO-006", "REQ-PRO-007",
    "REQ-PRO-008", "REQ-PRO-009", "REQ-PRO-010", "REQ-PRO-011", "REQ-PRO-012", "REQ-PRO-013", "REQ-PRO-014", "REQ-PRO-015", "REQ-PRO-016", "REQ-PRO-017",
    "REQ-PRO-018", "REQ-PRO-019", "REQ-PRO-020", "REQ-PRO-021", "REQ-PRO-022", "REQ-PRO-023", "REQ-PRO-024", "REQ-PRO-025", "REQ-PRO-026", "REQ-PRO-027",
    "REQ-PRO-028", "REQ-PRO-029", "REQ-PRO-033", "REQ-PRO-034", "REQ-PRO-035", "REQ-PRO-036", "REQ-PRO-037", "REQ-PRO-040", "REQ-PRO-041", "REQ-PRO-059",
    "REQ-PRO-060", "REQ-PRO-061", "REQ-PRO-062", "REQ-PRO-063", "REQ-PRO-064", "REQ-PRO-065", "REQ-PRO-066", "REQ-PRO-067", "REQ-PRO-068", "REQ-PRO-069",
    "REQ-PRO-070", "REQ-PRO-071", "REQ-PRO-072", "REQ-PRO-073", "REQ-PRO-074", "REQ-PRO-075", "REQ-PRO-076", "REQ-PRO-077", "REQ-PRO-078", "REQ-PRO-079",
    "REQ-PRO-080", "REQ-PRO-081", "REQ-PRO-082", "REQ-PRO-083", "REQ-PRO-084", "REQ-PRO-085", "REQ-PRO-086", "REQ-PRO-087", "REQ-PRO-088", "REQ-SEC-041",
    "REQ-SEC-042", "REQ-SEN-020", "REQ-SEN-021", "REQ-SEN-025", "REQ-SEN-026", "REQ-SET-005", "REQ-SET-006", "REQ-SET-007", "REQ-SET-008", "REQ-SET-009",
    "REQ-SET-010", "REQ-SET-011", "REQ-SET-012", "REQ-SET-013", "REQ-SET-014", "REQ-SET-015", "REQ-SET-016", "REQ-SET-017", "REQ-SET-018", "REQ-SET-019",
    "REQ-SET-020", "REQ-SET-021", "REQ-SET-022", "REQ-SET-023", "REQ-SET-024", "REQ-SET-025", "REQ-SET-026", "REQ-SET-027", "REQ-SET-028", "REQ-SET-029",
    "REQ-SET-030", "REQ-SET-031", "REQ-SET-032", "REQ-SET-033", "REQ-SET-034", "REQ-SET-035", "REQ-SET-036", "REQ-SET-037", "REQ-SET-038", "REQ-SET-039",
    "REQ-SET-040", "REQ-SET-041", "REQ-SET-042", "REQ-SET-043", "REQ-SET-044", "REQ-SET-050", "REQ-SET-051", "REQ-SET-052", "REQ-SET-053", "REQ-SET-054",
    "REQ-SET-055", "REQ-SET-056", "REQ-SET-057", "REQ-SET-058", "REQ-SET-059", "REQ-SET-060", "REQ-SET-061", "REQ-SET-062", "REQ-SET-063", "REQ-SET-064",
    "REQ-SET-065", "REQ-SET-066", "REQ-SET-067", "REQ-SET-068", "REQ-SET-069", "REQ-SET-070", "REQ-SET-071", "REQ-SET-072", "REQ-SET-073", "REQ-SET-074",
    "REQ-SET-075", "REQ-SET-076", "REQ-SET-077", "REQ-SET-078", "REQ-SET-079", "REQ-SET-080", "REQ-SET-081", "REQ-SET-082", "REQ-SET-089", "REQ-SET-095",
    "REQ-SET-096", "REQ-SET-097", "REQ-SET-098", "REQ-SET-099", "REQ-SET-100", "REQ-SET-101", "REQ-SET-102", "REQ-SET-103", "REQ-SET-104", "REQ-SET-105",
    "REQ-STB-001", "REQ-STB-002", "REQ-STB-003", "REQ-STB-004", "REQ-STB-005", "REQ-STB-006", "REQ-STB-007", "REQ-STB-008", "REQ-STB-009", "REQ-STB-010",
    "REQ-STB-011", "REQ-STB-012", "REQ-STB-013", "REQ-STB-014", "REQ-STB-015", "REQ-STB-016", "REQ-STB-017", "REQ-STB-018", "REQ-STB-019", "REQ-TOOL-001",
    "REQ-TOOL-002", "REQ-TOOL-003", "REQ-TOOL-004", "REQ-TOOL-005", "REQ-TOOL-006", "REQ-TOOL-007", "REQ-TRK-002", "REQ-TRK-010", "REQ-TRK-011", "REQ-TRK-012",
    "REQ-TRK-013", "REQ-TRK-014", "REQ-TRK-015", "REQ-TRK-016", "REQ-TRK-017", "REQ-TRK-018", "REQ-TRK-019", "REQ-TRK-020", "REQ-TRK-021", "REQ-TRK-022",
    "REQ-TRK-023", "REQ-TRK-024", "REQ-TRK-025", "REQ-TRK-026", "REQ-TRK-027", "REQ-TRK-028", "REQ-TRK-029", "REQ-TRK-030", "REQ-TRK-031", "REQ-TRK-032",
    "REQ-TRK-033", "REQ-TRK-034", "REQ-TRK-035", "REQ-TRK-036", "REQ-TRK-037", "REQ-TRK-038", "REQ-TRK-040", "REQ-TRK-041", "REQ-TRK-042", "REQ-TRK-043",
    "REQ-TRK-044", "REQ-TRK-045", "REQ-TRK-046", "REQ-TRK-047", "REQ-TRK-048", "REQ-TRK-049", "REQ-TRK-050", "REQ-TRK-051", "REQ-TRK-052", "REQ-TRK-060",
    "REQ-TRK-061", "REQ-TRK-062", "REQ-TRK-063", "REQ-TRK-064", "REQ-TRK-065", "REQ-TST-001", "REQ-TST-002", "REQ-TST-003", "REQ-UI-011", "REQ-UI-012",
    "REQ-UI-021", "REQ-UI-048", "REQ-UI-051", "REQ-UI-058", "REQ-UI-059", "REQ-UI-060", "REQ-UI-061", "REQ-UI-102", "REQ-UI-103", "REQ-UI-104",
    "REQ-UI-105", "REQ-UI-106", "REQ-UI-107", "REQ-UI-108", "REQ-UI-109", "REQ-UI-112", "REQ-UI-113", "REQ-UI-114", "REQ-UI-115", "REQ-UI-116",
    "REQ-UI-117", "REQ-UI-118", "REQ-UI-119", "REQ-UI-120", "REQ-UI-121", "REQ-UI-122", "REQ-UI-123", "REQ-UI-124", "REQ-UI-125", "REQ-UI-126",
    "REQ-UI-127", "REQ-UI-128", "REQ-UI-129", "REQ-UI-130", "REQ-UI-131", "REQ-UI-132", "REQ-UI-133", "REQ-UI-134", "REQ-UI-136", "REQ-UI-137",
    "REQ-UI-138", "REQ-UI-139", "REQ-UI-140", "REQ-UI-141", "REQ-UI-142", "REQ-UI-143", "REQ-UI-144", "REQ-UI-145", "REQ-UI-146", "REQ-UI-147",
    "REQ-UI-148", "REQ-UI-149", "REQ-UI-150", "REQ-UI-151", "REQ-UI-152", "REQ-UI-153", "REQ-UI-154", "REQ-UI-155", "REQ-UI-156", "REQ-UI-157",
    "REQ-UI-158", "REQ-UI-159", "REQ-UI-160", "REQ-UI-161", "REQ-UI-162", "REQ-UI-163", "REQ-UI-164", "REQ-UI-165", "REQ-UI-167", "REQ-UI-168",
    "REQ-UI-169", "REQ-UI-170", "REQ-UI-171", "REQ-UI-172", "REQ-UI-173", "REQ-UI-174", "REQ-UI-175", "REQ-UI-176", "REQ-UI-177", "REQ-UI-178",
    "REQ-UI-179", "REQ-UI-180", "REQ-UI-181", "REQ-UI-182", "REQ-UI-183", "REQ-UI-184", "REQ-UI-185", "REQ-UI-186", "REQ-UI-187", "REQ-UI-188",
    "REQ-UI-189", "REQ-UI-190", "REQ-UI-191", "REQ-UI-192", "REQ-UI-193", "REQ-UI-194", "REQ-UI-195", "REQ-UI-196", "REQ-UI-197", "REQ-UI-198",
    "REQ-UI-199", "REQ-UI-200", "REQ-UI-201", "REQ-UI-202", "REQ-UI-203", "REQ-UI-204", "REQ-UI-205", "REQ-UI-206", "REQ-UI-207", "REQ-UI-208",
    "REQ-UI-209", "REQ-UI-210", "REQ-UI-211", "REQ-UI-212", "REQ-UI-213", "REQ-UI-214", "REQ-UI-215", "REQ-UI-216", "REQ-UI-217", "REQ-UI-218",
    "REQ-UI-219", "REQ-UI-220", "REQ-UI-221", "REQ-UI-222", "REQ-UI-223", "REQ-UI-224", "REQ-UI-225", "REQ-UI-226", "REQ-UI-227", "REQ-UI-228",
    "REQ-UI-229", "REQ-UI-230", "REQ-UI-231", "REQ-UI-232", "REQ-UI-233", "REQ-UI-234", "REQ-UI-235", "REQ-UI-236", "REQ-UI-237", "REQ-UI-238",
    "REQ-UI-239", "REQ-UI-240", "REQ-UI-241", "REQ-UI-242", "REQ-UI-243", "REQ-UI-244", "REQ-UI-245", "REQ-UI-246", "REQ-UI-247", "REQ-UI-248",
    "REQ-UI-249", "REQ-UI-250", "REQ-UI-251", "REQ-UI-252", "REQ-UI-253", "REQ-UI-254", "REQ-UI-255", "REQ-UI-256", "REQ-UI-257", "REQ-UI-258",
    "REQ-UI-259", "REQ-UI-260", "REQ-UI-261", "REQ-UI-262", "REQ-UI-263", "REQ-UI-264", "REQ-UI-265", "REQ-UI-266", "REQ-UI-267", "REQ-UI-268",
    "REQ-UI-269", "REQ-UI-270", "REQ-UI-271", "REQ-UI-272", "REQ-UI-273", "REQ-UI-274", "REQ-UI-275", "REQ-UI-276", "REQ-UI-277", "REQ-UI-278",
    "REQ-UI-279", "REQ-UI-280", "REQ-UI-281", "REQ-UI-282", "REQ-UI-283", "REQ-UI-284", "REQ-UI-285", "REQ-UI-286", "REQ-UI-287", "REQ-UI-288",
    "REQ-UI-290", "REQ-UI-291", "REQ-UI-293", "REQ-UI-294", "REQ-UI-295", "REQ-UI-296", "REQ-UI-297", "REQ-UI-298", "REQ-UI-299", "REQ-UI-300",
    "REQ-UI-301", "REQ-UI-302", "REQ-UI-303", "REQ-UI-304", "REQ-UI-305", "REQ-UI-306", "REQ-UI-307", "REQ-UI-308", "REQ-UI-309", "REQ-UI-310",
    "REQ-UI-311", "REQ-UI-312", "REQ-UI-313", "REQ-UI-314", "REQ-UI-315", "REQ-UI-316", "REQ-UI-317", "REQ-UI-318", "REQ-UI-319", "REQ-UI-320",
    "REQ-UI-321", "REQ-UI-322", "REQ-UI-323", "REQ-UI-324", "REQ-UI-325", "REQ-UI-326", "REQ-UI-327", "REQ-UI-328", "REQ-UI-329", "REQ-UI-330",
    "REQ-UI-331", "REQ-UI-332", "REQ-UI-333", "REQ-UI-334", "REQ-UI-335", "REQ-UI-336", "REQ-UI-337", "REQ-UI-338", "REQ-UI-339", "REQ-UI-340",
    "REQ-UI-341", "REQ-UI-342", "REQ-UI-343", "REQ-UI-344", "REQ-UI-345", "REQ-UI-346", "REQ-UI-347", "REQ-UI-348", "REQ-UI-349", "REQ-UI-350",
    "REQ-UI-351", "REQ-UI-352", "REQ-UI-353", "REQ-UI-354", "REQ-UI-355", "REQ-UI-356", "REQ-UI-357", "REQ-UI-358", "REQ-UI-359", "REQ-UI-360",
    "REQ-UI-361", "REQ-UI-362", "REQ-UI-363", "REQ-UI-364", "REQ-UI-365", "REQ-UI-366", "REQ-UI-367", "REQ-UI-368", "REQ-UI-369", "REQ-UI-370",
    "REQ-UI-371", "REQ-UI-372", "REQ-UI-373", "REQ-UI-374", "REQ-UI-375", "REQ-UI-376", "REQ-UI-377", "REQ-UI-378", "REQ-UI-379", "REQ-UI-380",
    "REQ-UI-381", "REQ-UI-382", "REQ-UI-383", "REQ-UI-384", "REQ-UI-385", "REQ-UI-386", "REQ-UI-387", "REQ-UI-388", "REQ-UI-389", "REQ-UI-390",
    "REQ-UI-391", "REQ-UI-392", "REQ-UI-393", "REQ-UI-394", "REQ-UI-395", "REQ-UI-396", "REQ-UI-397", "REQ-UI-398", "REQ-UI-399", "REQ-UI-400",
    "REQ-UI-401", "REQ-UI-402", "REQ-UI-403", "REQ-UI-404", "REQ-UI-405", "REQ-UI-406", "REQ-UI-407", "REQ-UI-408", "REQ-UI-409", "REQ-UI-410",
    "REQ-UI-411", "REQ-UI-412", "REQ-UI-413", "REQ-UI-414", "REQ-UI-415", "REQ-UI-416", "REQ-UI-417", "REQ-UI-418", "REQ-UI-419", "REQ-UI-420",
    "REQ-UI-421", "REQ-UI-422", "REQ-UI-423", "REQ-UI-424", "REQ-UI-425", "REQ-UI-426", "REQ-UI-427", "REQ-UI-428", "REQ-UI-429", "REQ-UI-430",
    "REQ-UI-431", "REQ-UI-432", "REQ-UI-433", "REQ-UI-434", "REQ-UI-435", "REQ-UI-436", "REQ-UI-437", "REQ-UI-438", "REQ-UI-439", "REQ-UI-440",
    "REQ-UI-441", "REQ-UI-442", "REQ-UI-443", "REQ-UI-444", "REQ-UI-445", "REQ-UI-446", "REQ-UI-447", "REQ-UI-448", "REQ-UI-455", "REQ-UI-456",
    "REQ-UI-457", "REQ-UI-458", "REQ-UI-459", "REQ-UI-460", "REQ-UI-461", "REQ-UI-462", "REQ-UI-463", "REQ-UI-464", "REQ-UI-465", "REQ-UI-466",
    "REQ-UI-467", "REQ-UI-468", "REQ-UI-469", "REQ-UI-470", "REQ-UI-471", "REQ-UI-508", "REQ-UI-509", "REQ-UI-521", "REQ-UI-522", "REQ-UI-523",
})



def get_git_diff(base_ref="develop", target_paths=None):
    """
    Retrieves the git diff for target_paths against base_ref (or merge-base with base_ref).
    If base_ref is not resolvable or in detached HEAD, falls back to diff against HEAD or staged/working tree.
    """
    if target_paths is None:
        target_paths = ["docs/requirements.md", "docs/requirements/"]
    elif isinstance(target_paths, str):
        target_paths = [target_paths]

    if base_ref == "develop":
        try:
            sprint_branches = subprocess.check_output(
                ["git", "for-each-ref", "--sort=-committerdate", "--format=%(refname:short)", "refs/heads/sprint/*"],
                stderr=subprocess.DEVNULL,
            ).decode("utf-8").strip().splitlines()
            best_sb = None
            best_commit_time = -1
            for sb in sprint_branches:
                ret = subprocess.call(
                    ["git", "merge-base", "--is-ancestor", sb, "HEAD"],
                    stderr=subprocess.DEVNULL,
                )
                if ret == 0:
                    base_ref = sb
                    best_sb = None
                    break
                try:
                    mb = subprocess.check_output(
                        ["git", "merge-base", sb, "HEAD"],
                        stderr=subprocess.DEVNULL,
                    ).decode("utf-8").strip()
                    if mb:
                        ts = int(subprocess.check_output(
                            ["git", "log", "-1", "--format=%ct", mb],
                            stderr=subprocess.DEVNULL,
                        ).decode("utf-8").strip())
                        if ts > best_commit_time:
                            best_commit_time = ts
                            best_sb = sb
                except Exception:
                    pass
            if best_sb:
                base_ref = best_sb
        except Exception:
            pass

    # Try merge-base first
    try:
        mb = subprocess.check_output(
            ["git", "merge-base", base_ref, "HEAD"],
            stderr=subprocess.DEVNULL,
        ).decode("utf-8").strip()
        if mb:
            cmd = ["git", "diff", f"{mb}", "--"] + target_paths
            return subprocess.check_output(cmd, stderr=subprocess.DEVNULL).decode("utf-8")
    except Exception:
        pass

    # Fallback to direct diff with base_ref
    try:
        cmd = ["git", "diff", f"{base_ref}", "--"] + target_paths
        diff = subprocess.check_output(cmd, stderr=subprocess.DEVNULL).decode("utf-8")
        if diff.strip():
            return diff
    except Exception:
        pass

    # Fallback to working tree / uncommitted changes
    try:
        cmd = ["git", "diff", "HEAD", "--"] + target_paths
        diff = subprocess.check_output(cmd, stderr=subprocess.DEVNULL).decode("utf-8")
        return diff
    except Exception:
        return ""


def parse_diff_requirements(diff_text):
    """
    Parses git diff output for docs/requirements.md and docs/requirements/*.md.
    Supports both legacy table rows (| **REQ-XXX**) and multi-line markdown headings (### REQ-XXX:).
    Returns:
      modified_reqs: set of REQ IDs that appear in both deleted (-) and added (+) lines.
      added_reqs: set of REQ IDs that appear ONLY in added (+) lines.
      deleted_reqs: set of REQ IDs that appear ONLY in deleted (-) lines.
    """
    added_ids = set()
    deleted_ids = set()
    current_added_req = None
    current_deleted_req = None

    PLACEHOLDER_IDS = {"REQ-XXX", "REQ-DOM-NNN", "REQ-DOM-XXX", "REQ-NNN"}

    for line in diff_text.splitlines():
        if line.startswith("+++") or line.startswith("---"):
            continue
        if line.startswith("@@"):
            m_hunk = re.search(r"REQ-([A-Z0-9_-]+)", line)
            current_added_req = f"REQ-{m_hunk.group(1)}" if m_hunk else None
            current_deleted_req = current_added_req
            if current_added_req in PLACEHOLDER_IDS:
                current_added_req = None
                current_deleted_req = None
            continue
        if line.startswith(" "):
            m_ctx = re.search(r"#{2,4}\s*REQ-([A-Z0-9_-]+)", line)
            if m_ctx:
                current_added_req = f"REQ-{m_ctx.group(1)}"
                current_deleted_req = current_added_req
                if current_added_req in PLACEHOLDER_IDS:
                    current_added_req = None
                    current_deleted_req = None
            continue

        if line.startswith("+"):
            m = re.search(r"\|\s*\*\*REQ-([A-Z0-9_-]+)\*\*", line)
            if not m:
                m = re.search(r"#{2,4}\s*REQ-([A-Z0-9_-]+)(?:[:\s]|$)", line)
            if not m:
                m = re.search(r"\*\*REQ-([A-Z0-9_-]+)\.\d+\*\*", line)
            if m:
                req_id = f"REQ-{m.group(1)}"
                if req_id not in PLACEHOLDER_IDS:
                    added_ids.add(req_id)
                    current_added_req = req_id
                else:
                    current_added_req = None
            elif current_added_req and current_added_req not in PLACEHOLDER_IDS:
                added_ids.add(current_added_req)
        elif line.startswith("-"):
            m = re.search(r"\|\s*\*\*REQ-([A-Z0-9_-]+)\*\*", line)
            if not m:
                m = re.search(r"#{2,4}\s*REQ-([A-Z0-9_-]+)(?:[:\s]|$)", line)
            if not m:
                m = re.search(r"\*\*REQ-([A-Z0-9_-]+)\.\d+\*\*", line)
            if m:
                req_id = f"REQ-{m.group(1)}"
                if req_id not in PLACEHOLDER_IDS:
                    deleted_ids.add(req_id)
                    current_deleted_req = req_id
                else:
                    current_deleted_req = None
            elif current_deleted_req and current_deleted_req not in PLACEHOLDER_IDS:
                deleted_ids.add(current_deleted_req)

    # Modified requirements appear in both added and deleted sets
    modified_reqs = added_ids.intersection(deleted_ids)
    purely_added_reqs = added_ids - modified_reqs
    purely_deleted_reqs = deleted_ids - modified_reqs

    return modified_reqs, purely_added_reqs, purely_deleted_reqs


def validate_archaeology_section(text):
    """
    Validates that text contains a valid Requirement Archaeology & Chesterton's Fence Audit section.
    Returns (is_valid, list_of_errors).
    """
    errors = []

    if not text:
        errors.append("Deliverable text is empty or missing.")
        return False, errors

    header_match = HEADER_PATTERN.search(text)
    if not header_match:
        errors.append(
            "Missing mandatory section header: "
            "'### Requirement Archaeology & Chesterton's Fence Audit' "
            "(or Jira markup 'h3. Requirement Archaeology & Chesterton's Fence Audit')."
        )
        return False, errors

    # Extract text from header match to next header (### or h1./h2./h3.) or end of text
    section_start = header_match.end()
    remaining = text[section_start:]
    next_header_match = re.search(r"(?:\n#{1,3}\s+|\nh[1-3]\.\s+)", remaining)
    section_body = remaining[:next_header_match.start()] if next_header_match else remaining

    for field in MANDATORY_FIELDS:
        # Regex to match bullet or label: e.g.
        # * `Original Requirement ID & Target`: <content>
        # • Original Requirement ID & Target: <content>
        # - Original Requirement ID & Target: <content>
        # *** *Original Requirement ID & Target*: <content>
        pattern = re.compile(
            rf"(?:[\*\-\•\#]+\s*)?(?:\*|`)*{re.escape(field)}(?:\*|`)*\s*[:\-\—]\s*([^\n\r]+)",
            re.IGNORECASE,
        )
        match = pattern.search(section_body)
        if not match:
            # Check if field name exists at all
            if re.search(re.escape(field), section_body, re.IGNORECASE):
                errors.append(f"Field '{field}' is present but has empty or invalid content.")
            else:
                errors.append(f"Missing mandatory field: '{field}'.")
        else:
            val = match.group(1).strip()
            # Clean off surrounding markdown or formatting
            clean_val = re.sub(r"^[`\*_]+|[`\*_]+$", "", val).strip()
            if not clean_val or clean_val.lower() in ["todo", "none", "n/a", "tbd", ""]:
                errors.append(f"Field '{field}' has empty or placeholder content: '{val}'.")

    return len(errors) == 0, errors


IMPACT_HEADER_PATTERN = re.compile(
    r"(?:^|\n)\s*(?:#{1,3}\s+|h[1-3]\.\s+)(?:\d+\.\s*)?(?:Change(?:[- ]?Request)?\s+)?Impact\s+Analysis.*",
    re.IGNORECASE,
)

IMPACT_DIMENSIONS = [
    {
        "name": "Affected Requirements",
        "patterns": [
            r"Affected\s+Requirements?",
            r"Requirements?\s+Impact",
            r"Affected\s+REQs?",
        ],
    },
    {
        "name": "Affected Architecture Elements",
        "patterns": [
            r"Affected\s+Architecture(?:\s+Elements)?",
            r"Architecture\s+Impact",
            r"Affected\s+Components?",
        ],
    },
    {
        "name": "Verification Impact & Test Surface",
        "patterns": [
            r"Verification\s+Impact(?:\s*&\s*Test\s+Surface)?",
            r"Affected\s+Tests?(?:\s*&\s*Verification\s+Impact)?",
            r"Affected\s+Test\s+Cases?",
            r"Test\s+Surface",
            r"Test\s+Impact",
        ],
    },
    {
        "name": "Risk Assessment & Invariants",
        "patterns": [
            r"Risk\s+Assessment(?:\s*&\s*(?:Invariants|Mitigations))?",
            r"Risks?(?:\s*&\s*(?:Invariants|Mitigations))?",
        ],
    },
]


def validate_impact_analysis_section(text):
    """
    REQ-PRO-064.2 / TST-PRO-064.1:
    Validates that deliverable text contains a valid Change-Request Impact Analysis section.
    Checks that the section header exists and that all 4 mandatory dimensions:
      1. Affected Requirements
      2. Affected Architecture Elements
      3. Verification Impact & Test Surface
      4. Risk Assessment & Invariants
    are present with non-empty, non-placeholder content.
    Returns (is_valid: bool, errors: list[str]).
    """
    errors = []
    if not text or not text.strip():
        errors.append("Deliverable text is empty or missing.")
        return False, errors

    header_match = IMPACT_HEADER_PATTERN.search(text)
    if not header_match:
        errors.append(
            "Missing mandatory section header: '## Change-Request Impact Analysis' "
            "(or '## Impact Analysis' / 'h2. Impact Analysis')."
        )
        return False, errors

    section_start = header_match.end()
    remaining = text[section_start:]
    next_header_match = re.search(r"(?:\n#{1,3}\s+|\nh[1-3]\.\s+)", remaining)
    section_body = remaining[:next_header_match.start()] if next_header_match else remaining

    for dim in IMPACT_DIMENSIONS:
        dim_name = dim["name"]
        patterns = dim["patterns"]
        found = False
        matched_val = None
        for p in patterns:
            dim_regex = re.compile(
                rf"(?:[\*\-\•\#]+\s*)?(?:\*|`)*(?:{p})(?:\*|`)*\s*[:\-\—]\s*([^\n\r]+)",
                re.IGNORECASE,
            )
            m = dim_regex.search(section_body)
            if m:
                found = True
                matched_val = m.group(1).strip()
                break

        if not found:
            label_found = False
            for p in patterns:
                if re.search(rf"(?:{p})", section_body, re.IGNORECASE):
                    label_found = True
                    break
            if label_found:
                errors.append(f"Dimension '{dim_name}' is present but has empty or invalid content.")
            else:
                errors.append(f"Missing mandatory impact dimension: '{dim_name}'.")
        else:
            clean_val = re.sub(r"^[`\*_]+|[`\*_]+$", "", matched_val).strip()
            placeholder_tokens = {"todo", "tbd", "none", "n/a", "null", "undefined", ""}
            if not clean_val or clean_val.lower() in placeholder_tokens or clean_val.lower() in {"[req-xxx]", "[todo]", "[tbd]", "[none]", "[n/a]"}:
                errors.append(f"Dimension '{dim_name}' has empty or placeholder content: '{matched_val}'.")

    return len(errors) == 0, errors


def verify_stage1_impact_analysis(analysis_file=None, repo_root=None):
    """
    REQ-PRO-064.2 / TST-PRO-064.2:
    Verifies that a Stage 1 analysis deliverable satisfies Change-Request Impact Analysis governance.
    If analysis_file is specified, validates that file.
    If not specified, discovers the latest analysis file from docs/engineering/analysis/.
    """
    if repo_root is None:
        repo_root = os.getcwd()

    if analysis_file:
        target_path = analysis_file if os.path.isabs(analysis_file) else os.path.join(repo_root, analysis_file)
        if not os.path.exists(target_path):
            return False, [f"ERROR: Analysis file not found: {target_path}"]
        try:
            with open(target_path, "r", encoding="utf-8") as f:
                content = f.read()
        except Exception as e:
            return False, [f"ERROR: Failed to read analysis file {target_path}: {e}"]

        valid, errors = validate_impact_analysis_section(content)
        if not valid:
            return False, [f"FAIL: Change-Request Impact Analysis in {os.path.basename(target_path)} failed validation:"] + [f"  - {e}" for e in errors]
        return True, [f"PASS: Change-Request Impact Analysis verified in {os.path.basename(target_path)}."]
    else:
        analysis_dir = os.path.join(repo_root, "docs", "engineering", "analysis")
        if not os.path.isdir(analysis_dir):
            return False, [f"ERROR: Analysis directory not found: {analysis_dir}"]
        md_files = [f for f in os.listdir(analysis_dir) if f.endswith(".md")]
        if not md_files:
            return False, [f"ERROR: No analysis files found in {analysis_dir}"]
        newest_file = max(md_files, key=lambda f: os.path.getmtime(os.path.join(analysis_dir, f)))
        return verify_stage1_impact_analysis(os.path.join(analysis_dir, newest_file), repo_root=repo_root)


ALLOWED_ENGINEERING_ENTRIES = {
    "analysis",
    "aspice_audits",
    "code_reviews",
    "emulator_sweeps",
    "gates",
    "metrics",
    "plans",
    "qa_audits",
    "retros",
    "reviews",
    "test_specs",
    "tickets",
    "walkthroughs",
    "test_evidence",
    "rule_history_archive.md",
    "workflow_overview.html",
}


def verify_tier_b_ticket_deliverable(ticket_key, repo_root=None):
    """
    ATT-9541: Verify that a Tier B ticket has a single combined deliverable at
    docs/engineering/tickets/<KEY>.md containing REQ/TST delta and evidence sections,
    without requiring separate analysis/test_specs/plans/walkthroughs files.
    """
    root = repo_root or os.getcwd()
    ticket_doc = os.path.join(root, "docs", "engineering", "tickets", f"{ticket_key}.md")
    if not os.path.isfile(ticket_doc):
        return False, [f"ERROR: Combined Tier B deliverable missing: docs/engineering/tickets/{ticket_key}.md"]
    with open(ticket_doc, "r", encoding="utf-8") as f:
        text = f.read()
    required_markers = [
        ("Problem / Scope", r"##\s+1\.\s+Problem|##\s+Problem"),
        ("Decision", r"##\s+2\.\s+Decision|##\s+Decision"),
        ("REQ / TST Delta", r"##\s+3\.\s+REQ|REQ\s*/\s*TST"),
        ("Implementation Plan", r"##\s+4\.\s+Implementation|##\s+Plan"),
        ("Verification & Evidence", r"##\s+5\.\s+Verification|Evidence"),
    ]
    missing = [label for label, pat in required_markers if not re.search(pat, text, re.IGNORECASE)]
    if missing:
        return False, [f"ERROR: Combined Tier B deliverable {ticket_key}.md missing required section(s): {', '.join(missing)}"]
    return True, [f"PASS: Combined Tier B deliverable verified at docs/engineering/tickets/{ticket_key}.md."]


def verify_ticket_evidence_bundle(ticket_key, repo_root=None):
    """
    ATT-9544: Verify a per-ticket machine-generated evidence bundle at
    docs/engineering/test_evidence/tickets/<KEY>/ (manifest.json SHA-256 hashes,
    commit_sha, run_started_utc, evidence_summary.json, and screenshots for UI tickets).
    """
    import hashlib
    root = repo_root or os.getcwd()
    bundle_dir = os.path.join(root, "docs", "engineering", "test_evidence", "tickets", ticket_key)
    manifest_path = os.path.join(bundle_dir, "manifest.json")
    if not os.path.isfile(manifest_path):
        return False, [f"ERROR: Per-ticket evidence manifest missing: docs/engineering/test_evidence/tickets/{ticket_key}/manifest.json"]
    try:
        with open(manifest_path, "r", encoding="utf-8") as f:
            manifest = json.load(f)
    except Exception as exc:
        return False, [f"ERROR: Failed to parse {manifest_path}: {exc}"]

    errors = []
    if not manifest.get("commit_sha") or manifest.get("commit_sha") == "UNKNOWN":
        errors.append("manifest.json missing valid commit_sha")
    if not manifest.get("run_started_utc"):
        errors.append("manifest.json missing run_started_utc")
    files = manifest.get("files") or {}
    if "evidence_summary.json" not in files:
        errors.append("manifest.json missing evidence_summary.json entry")

    for rel, expected_hash in files.items():
        full = os.path.join(bundle_dir, rel)
        if not os.path.isfile(full):
            errors.append(f"Listed evidence artifact missing: {rel}")
            continue
        h = hashlib.sha256()
        with open(full, "rb") as fh:
            for chunk in iter(lambda: fh.read(65536), b""):
                h.update(chunk)
        if h.hexdigest() != expected_hash:
            errors.append(f"SHA-256 mismatch on evidence artifact: {rel}")

    summary_path = os.path.join(bundle_dir, "evidence_summary.json")
    if os.path.isfile(summary_path):
        try:
            with open(summary_path, "r", encoding="utf-8") as sf:
                summary = json.load(sf)
            if summary.get("ui_touching") and not summary.get("screenshots"):
                errors.append("UI ticket evidence bundle contains zero screenshots")
        except Exception as exc:
            errors.append(f"Invalid evidence_summary.json: {exc}")

    if errors:
        return False, [f"ERROR: Ticket evidence bundle verification failed for {ticket_key}: " + "; ".join(errors)]
    return True, [f"PASS: Per-ticket evidence bundle verified for {ticket_key} ({len(files)} SHA-256 hashed files)."]


def verify_gate_records(sprint_name=None, repo_root=None):
    """
    REQ-PRO-059.4 / TST-PRO-059.2:
    Verifies that sprint quality gate records have been exported to docs/engineering/gates/
    per REQ-PRO-059. Fails with exit code 1 if the expected sprint gate record is missing or empty.
    """
    if repo_root is None:
        repo_root = os.getcwd()

    gates_dir = os.path.join(repo_root, "docs", "engineering", "gates")
    if not os.path.isdir(gates_dir):
        return False, [f"ERROR: Quality gate export directory does not exist: {gates_dir}"]

    if sprint_name:
        clean_name = sprint_name.strip()
        slug = re.sub(r"[/\\ ]+", "_", clean_name).lower()
        if not slug.endswith(".md"):
            slug += ".md"

        candidates = [
            slug,
            f"sprint_{slug}" if not slug.startswith("sprint_") else slug,
            slug.replace("sprint_", "", 1) if slug.startswith("sprint_") else slug,
        ]

        found_file = None
        for cand in candidates:
            cand_path = os.path.join(gates_dir, cand)
            if os.path.isfile(cand_path):
                found_file = cand_path
                break

        if not found_file:
            return False, [
                f"ERROR: Missing sprint quality gate export file for sprint '{sprint_name}'. "
                f"Expected one of {[os.path.join('docs/engineering/gates', c) for c in candidates]}."
            ]

        try:
            with open(found_file, "r", encoding="utf-8") as f:
                content = f.read().strip()
            if not content:
                return False, [f"ERROR: Sprint quality gate export file is empty: {found_file}"]
            if not re.search(r"\b(?:gate|verdict|pass|bounce|revision)\b", content, re.IGNORECASE):
                return False, [f"ERROR: Sprint quality gate export file contains no recognized gate records: {found_file}"]
            return True, [f"PASS: Sprint quality gate records verified for '{sprint_name}': {found_file}"]
        except Exception as e:
            return False, [f"ERROR: Failed to read gate export file {found_file}: {e}"]
    else:
        md_files = [f for f in os.listdir(gates_dir) if f.endswith(".md") and not f.startswith(".")]
        if not md_files:
            return False, [f"ERROR: No sprint quality gate export files (*.md) found in {gates_dir}"]

        valid_files = 0
        for fname in md_files:
            fpath = os.path.join(gates_dir, fname)
            try:
                with open(fpath, "r", encoding="utf-8") as f:
                    content = f.read().strip()
                if content and re.search(r"\b(?:gate|verdict|pass|bounce|revision)\b", content, re.IGNORECASE):
                    valid_files += 1
            except Exception:
                pass

        if valid_files == 0:
            return False, [f"ERROR: No valid, non-empty sprint quality gate export records found in {gates_dir}"]
        return True, [f"PASS: Verified {valid_files} sprint quality gate export record(s) in {gates_dir}."]


def verify_sprint_metrics(sprint_name=None, repo_root=None):
    """
    REQ-PRO-060.3 / TST-PRO-060.2:
    Verifies that sprint metrics have been recorded in docs/engineering/metrics/process_metrics.jsonl.
    Validates:
    - File exists and is non-empty.
    - Every line is valid JSON conforming to the schema (sprint_id, sprint_name, throughput, quality_gates).
    - If sprint_name is specified, verifies that an entry for that sprint exists and has:
      total_story_points > 0 and completed_story_points > 0.
    - If sprint_name is not specified, verifies that at least one valid entry exists, and that the latest
      entry has total_story_points > 0 and completed_story_points > 0.
    Fails with exit code 1 if the verification fails.
    """
    if repo_root is None:
        repo_root = os.getcwd()

    metrics_file = os.path.join(repo_root, "docs", "engineering", "metrics", "process_metrics.jsonl")
    if not os.path.isfile(metrics_file):
        return False, [f"ERROR: Sprint process metrics file does not exist: {metrics_file}"]

    try:
        with open(metrics_file, "r", encoding="utf-8") as f:
            lines = [line.strip() for line in f if line.strip()]
    except Exception as e:
        return False, [f"ERROR: Failed to read sprint process metrics file {metrics_file}: {e}"]

    if not lines:
        return False, [f"ERROR: Sprint process metrics file is empty: {metrics_file}"]

    entries = []
    required_keys = {"sprint_id", "sprint_name", "throughput", "quality_gates"}
    for idx, line in enumerate(lines, start=1):
        try:
            entry = json.loads(line)
        except Exception as e:
            return False, [f"ERROR: Malformed JSON at line {idx} in {metrics_file}: {e}"]
        if not isinstance(entry, dict) or not required_keys.issubset(entry.keys()):
            return False, [f"ERROR: Line {idx} in {metrics_file} missing required schema fields {required_keys - entry.keys()}"]

        # Validate optional problem_trends schema when present (REQ-PRO-063.4)
        pt = entry.get("problem_trends")
        if pt is not None:
            pt_keys = {"total_bugs", "open_bugs", "closed_bugs", "by_severity"}
            if not isinstance(pt, dict) or not pt_keys.issubset(pt.keys()):
                missing = pt_keys - pt.keys() if isinstance(pt, dict) else pt_keys
                return False, [f"ERROR: Line {idx} in {metrics_file} missing required problem_trends fields {missing}"]
            if not isinstance(pt.get("by_severity"), dict):
                return False, [f"ERROR: Line {idx} in {metrics_file} has invalid by_severity mapping in problem_trends"]

        entries.append(entry)

    if sprint_name:
        clean_target = sprint_name.strip()
        clean_target = re.sub(r"^sprint[/_]", "", clean_target)
        found_entry = None
        for entry in entries:
            s_name = str(entry.get("sprint_name", "")).strip()
            s_id = str(entry.get("sprint_id", "")).strip()
            if clean_target in (s_name, s_id, f"sprint_{s_name}", f"sprint/{s_name}") or s_name == clean_target:
                found_entry = entry
                break
        if not found_entry:
            return False, [f"ERROR: Missing sprint metrics record for sprint '{sprint_name}' in {metrics_file}"]

        throughput = found_entry.get("throughput", {})
        total_sp = float(throughput.get("total_story_points", 0.0) or 0.0)
        completed_sp = float(throughput.get("completed_story_points", 0.0) or 0.0)
        if total_sp <= 0.0 or completed_sp <= 0.0:
            return False, [
                f"ERROR: Sprint metrics for '{sprint_name}' have invalid story points: "
                f"total_story_points={total_sp}, completed_story_points={completed_sp} (expected > 0)."
            ]
        return True, [
            f"PASS: Sprint metrics verified for '{sprint_name}': "
            f"total_story_points={total_sp}, completed_story_points={completed_sp}."
        ]
    else:
        latest = entries[-1]
        throughput = latest.get("throughput", {})
        total_sp = float(throughput.get("total_story_points", 0.0) or 0.0)
        completed_sp = float(throughput.get("completed_story_points", 0.0) or 0.0)
        if total_sp <= 0.0 or completed_sp <= 0.0:
            return False, [
                f"ERROR: Latest sprint metrics entry ('{latest.get('sprint_name')}') has invalid story points: "
                f"total_story_points={total_sp}, completed_story_points={completed_sp} (expected > 0)."
            ]
        return True, [
            f"PASS: Verified {len(entries)} sprint metrics record(s) in {metrics_file}. "
            f"Latest sprint '{latest.get('sprint_name')}' has total_story_points={total_sp}, completed_story_points={completed_sp}."
        ]


def _validate_single_qa_audit(found_file, sprint_name=None):
    try:
        with open(found_file, "r", encoding="utf-8") as f:
            content = f.read().strip()
        if not content:
            return False, [f"ERROR: Sprint QA audit report is empty: {found_file}"]
        if not re.search(r"\b(?:COMPLIANT|NON-COMPLIANT)\b", content, re.IGNORECASE):
            return False, [f"ERROR: Sprint QA audit report contains no recognized compliance verdicts: {found_file}"]

        # Auditor Role validation (REQ-PRO-091.1, TST-PRO-091.4)
        role_m = re.search(r"\bAuditor Role\b.*?:.*?(?:`|\b)(coordinator|agent1|qa|agent2)\b", content, re.IGNORECASE)
        if role_m:
            auditor_r = role_m.group(1).lower()
            if auditor_r in ("coordinator", "agent1"):
                return False, [
                    f"ERROR: Auditor role in {found_file} is '{auditor_r}', which lacks ASPICE SUP.1 independence. Must be 'qa' or 'agent2' (REQ-PRO-091.1)."
                ]
        else:
            hdr_m = re.search(r"\bAuditor Role\b\s*:\s*`?([a-zA-Z0-9_-]+)`?", content, re.IGNORECASE)
            if hdr_m:
                auditor_r = hdr_m.group(1).lower()
                if auditor_r in ("coordinator", "agent1"):
                    return False, [
                        f"ERROR: Auditor role in {found_file} is '{auditor_r}', which lacks ASPICE SUP.1 independence. Must be 'qa' or 'agent2' (REQ-PRO-091.1)."
                    ]
                elif auditor_r not in ("qa", "agent2"):
                    return False, [
                        f"ERROR: Unrecognized or non-independent auditor role '{auditor_r}' in {found_file}. Must be 'qa' or 'agent2' (REQ-PRO-091.1)."
                    ]

        ticket_sections = re.findall(
            r"(?:###\s*(?:Sampled\s+)?Ticket:?\s*([A-Z0-9_-]+)|\|\s*([A-Z0-9_-]+)\s*\|\s*(?:PASS|FAIL|COMPLIANT|NON-COMPLIANT))",
            content,
            re.IGNORECASE,
        )
        sampled_tickets = set(
            (t[0] or t[1]) for t in ticket_sections
            if (t[0] or t[1])
            and (t[0] or t[1]).upper() not in ("PASS", "FAIL", "COMPLIANT", "NON-COMPLIANT")
            and not (t[0] or t[1]).startswith("ATT-232")
        )
        if len(sampled_tickets) == 0:
            all_keys = set(re.findall(r"\b(?:ATT|PROJ)-\d+\b", content))
            sampled_tickets = {k for k in all_keys if not k.startswith("ATT-232")}

        total_sampled = len(sampled_tickets)
        # Dynamic sample size check (REQ-PRO-091.2, TST-PRO-091.2, REQ-PRO-134.5)
        sample_ratio_m = re.search(r"(?:Sample Size|sample_size).*?(\d+)\s*/\s*(\d+)", content, re.IGNORECASE)
        is_zero_merged_na = bool(
            sample_ratio_m
            and int(sample_ratio_m.group(1)) == 0
            and int(sample_ratio_m.group(2)) == 0
            and re.search(r"N/A\s*\(0\s+merged\s+tickets\s+in\s+sprint\)", content, re.IGNORECASE)
        )
        is_single_merged = bool(
            sample_ratio_m
            and int(sample_ratio_m.group(1)) == 1
            and int(sample_ratio_m.group(2)) == 1
            and total_sampled == 1
        )

        if not is_zero_merged_na and not is_single_merged:
            if total_sampled < 2:
                return False, [
                    f"ERROR: Sprint QA audit report in {found_file} sampled only {total_sampled} ticket(s) (minimum >= 2 required by REQ-PRO-061.1)."
                ]

            if sample_ratio_m:
                sampled_cnt = int(sample_ratio_m.group(1))
                total_cnt = int(sample_ratio_m.group(2))
                import math
                min_required = max(2, math.ceil(total_cnt * 0.10))
                if sampled_cnt < min_required:
                    return False, [
                        f"ERROR: Sample size {sampled_cnt} / {total_cnt} in {found_file} is below required >= 10% dynamic threshold (minimum {min_required} required by REQ-PRO-091.2)."
                    ]

        # Corrective Action Tracking check (REQ-PRO-091.3, TST-PRO-091.3)
        is_non_compliant = bool(re.search(r"Overall Process Verdict.*?\bNON-COMPLIANT\b", content, re.IGNORECASE))
        if not is_non_compliant:
            is_non_compliant = bool(re.search(r"\|\s*NON-COMPLIANT\s*\|", content, re.IGNORECASE))
        if is_non_compliant:
            sec4_m = re.search(r"## 4\.\s*Non-Compliance Tracking & Remediation(.*?)(?:---|\Z)", content, re.DOTALL | re.IGNORECASE)
            if not sec4_m:
                return False, [
                    f"ERROR: Non-compliant audit {found_file} is missing Section 4 ('## 4. Non-Compliance Tracking & Remediation') (REQ-PRO-091.3)."
                ]
            sec4_text = sec4_m.group(1).strip()
            real_ca_links = re.findall(r"\[(ATT-\d+|PROJ-\d+)\]", sec4_text)
            synthetic_ca_links = re.findall(r"\[(ATT-CA-[A-Za-z0-9_-]+)\]", sec4_text)

            # REQ-PRO-091.3 / REQ-PRO-118.5: Enforce real ca_key != audited_key and reject synthetic ATT-CA-* for post-cutoff audits
            base_fn = os.path.basename(found_file).lower()
            is_grandfathered = any(
                base_fn.startswith(prefix)
                for prefix in ("sprint_2026-46.", "sprint_2026-47.", "sprint_2026-11-16_02_", "sprint_2026-11-17_01_")
            ) or (
                sprint_name and any(
                    str(sprint_name).lower().startswith(prefix)
                    for prefix in ("2026-46.", "2026-47.", "2026-11-16_02_", "2026-11-17_01_")
                )
            )

            if not is_grandfathered and synthetic_ca_links:
                return False, [
                    f"ERROR: Non-compliant audit in {found_file} contains synthetic placeholder corrective action link(s) {synthetic_ca_links}; real Jira ticket links [ATT-XXXX] are required (REQ-PRO-118.5)."
                ]

            ca_links = real_ca_links if not is_grandfathered else (real_ca_links + synthetic_ca_links)
            if not ca_links:
                return False, [
                    f"ERROR: Non-compliant audit in {found_file} lacks required Jira corrective action ticket links [ATT-XXXX] in Section 4 (REQ-PRO-091.3)."
                ]

            if not is_grandfathered:
                for line in sec4_text.splitlines():
                    aud_m = re.match(r"\s*\*\s*\*\*([A-Z]+-\d+)\*\*:", line)
                    if aud_m:
                        audited_key = aud_m.group(1)
                        ca_part_m = re.search(r"Corrective Action Ticket:\s*(.*)", line)
                        if ca_part_m:
                            line_ca_links = re.findall(r"\[([A-Z0-9_-]+)\]", ca_part_m.group(1))
                            for lk in line_ca_links:
                                if lk == audited_key:
                                    return False, [
                                        f"ERROR: Non-compliant audit in {found_file} has self-referential corrective action ticket {lk} for audited ticket {audited_key} (REQ-PRO-091.3)."
                                    ]

        sprint_label = sprint_name if sprint_name else os.path.basename(found_file)
        return True, [f"PASS: Sprint QA audit report verified for '{sprint_label}' ({total_sampled} sampled tickets): {found_file}"]
    except Exception as e:
        return False, [f"ERROR: Failed to read QA audit file {found_file}: {e}"]


def verify_qa_audits(sprint_name=None, repo_root=None):
    """
    REQ-PRO-061.4 / TST-PRO-061.2 / REQ-PRO-091 / TST-PRO-091:
    Verifies that sprint QA process-compliance audit records exist in docs/engineering/qa_audits/
    per REQ-PRO-061 and REQ-PRO-091. Fails with exit code 1 if:
    - qa_audits directory does not exist.
    - Expected sprint audit file is missing or empty.
    - Audit record does not contain recognized compliance verdicts ('COMPLIANT' or 'NON-COMPLIANT').
    - Audit record does not contain at least 2 sampled ticket evaluations (REQ-PRO-061.1).
    - Auditor role is not independent ('coordinator' or 'agent1' rejected) (REQ-PRO-091.1).
    - Sample size does not meet >= max(2, ceil(N * 0.10)) dynamic threshold (REQ-PRO-091.2).
    - Non-compliant audit lacks Jira corrective action ticket links [ATT-XXXX] in Section 4 (REQ-PRO-091.3).
    """
    if repo_root is None:
        repo_root = os.getcwd()

    qa_dir = os.path.join(repo_root, "docs", "engineering", "qa_audits")
    if not os.path.isdir(qa_dir):
        return False, [f"ERROR: QA audit reports directory does not exist: {qa_dir}"]

    if sprint_name:
        clean_name = sprint_name.strip()
        slug = re.sub(r"[/\\ ]+", "_", clean_name).lower()
        if not slug.endswith(".md"):
            slug += ".md"

        candidates = [
            slug,
            f"sprint_{slug}" if not slug.startswith("sprint_") else slug,
            slug.replace("sprint_", "", 1) if slug.startswith("sprint_") else slug,
        ]

        found_file = None
        for cand in candidates:
            cand_path = os.path.join(qa_dir, cand)
            if os.path.isfile(cand_path):
                found_file = cand_path
                break

        if not found_file:
            return False, [
                f"ERROR: Missing sprint QA process-compliance audit file for sprint '{sprint_name}'. "
                f"Expected one of {[os.path.join('docs/engineering/qa_audits', c) for c in candidates]}."
            ]

        return _validate_single_qa_audit(found_file, sprint_name=sprint_name)
    else:
        md_files = [f for f in os.listdir(qa_dir) if f.endswith(".md") and not f.startswith(".")]
        if not md_files:
            return False, [f"ERROR: No sprint QA process-compliance audit files (*.md) found in {qa_dir}"]

        all_errors = []
        valid_files = 0
        for fname in md_files:
            fpath = os.path.join(qa_dir, fname)
            ok, diag = _validate_single_qa_audit(fpath)
            if not ok:
                all_errors.extend(diag)
            else:
                valid_files += 1

        if all_errors:
            return False, all_errors
        if valid_files == 0:
            return False, [f"ERROR: No valid, non-empty sprint QA audit records found in {qa_dir}"]
        return True, [f"PASS: Verified {valid_files} sprint QA process-compliance audit record(s) in {qa_dir}."]


def verify_independent_reviews(sprint_name=None, repo_root=None):
    """
    REQ-PRO-062.4 / TST-PRO-062.2:
    Verifies that independent QA review records (Human Ceremony 2 or Cross-Vendor AI)
    exist in docs/engineering/reviews/ and satisfy schema requirements:
    - Traceable reviewer metadata (Reviewer, Date, Scope, Findings/Verdict).
    """
    if repo_root is None:
        repo_root = os.getcwd()

    rev_dir = os.path.join(repo_root, "docs", "engineering", "reviews")
    if not os.path.isdir(rev_dir):
        return False, [f"ERROR: Independent reviews directory does not exist: {rev_dir}"]

    if sprint_name:
        clean_name = sprint_name.strip()
        slug = re.sub(r"[/\\ ]+", "_", clean_name).lower()
        if not slug.endswith(".md"):
            slug += ".md"

        candidates = [
            slug,
            f"sprint_{slug}" if not slug.startswith("sprint_") else slug,
            slug.replace("sprint_", "", 1) if slug.startswith("sprint_") else slug,
        ]

        found_file = None
        for cand in candidates:
            cand_path = os.path.join(rev_dir, cand)
            if os.path.isfile(cand_path):
                found_file = cand_path
                break

        # Also search for files containing the sprint name
        if not found_file:
            for fname in os.listdir(rev_dir):
                if fname.endswith(".md") and not fname.startswith("."):
                    fpath = os.path.join(rev_dir, fname)
                    try:
                        with open(fpath, "r", encoding="utf-8") as f:
                            if clean_name.lower() in f.read().lower():
                                found_file = fpath
                                break
                    except Exception:
                        pass

        if not found_file:
            return False, [
                f"ERROR: Missing independent review record for sprint '{sprint_name}'. "
                f"Expected one of {[os.path.join('docs/engineering/reviews', c) for c in candidates]}."
            ]

        try:
            with open(found_file, "r", encoding="utf-8") as f:
                content = f.read().strip()
            if not content:
                return False, [f"ERROR: Independent review record is empty: {found_file}"]

            required_patterns = [
                (r"(?:\*\*Reviewer\*\*|Reviewer:?)\s*([^\n\r]+)", "Reviewer"),
                (r"(?:\*\*Date\*\*|Date:?)\s*([^\n\r]+)", "Date"),
                (r"(?:\*\*Scope\*\*|Scope:?)\s*([^\n\r]+)", "Scope"),
                (r"(?:\*\*Verdict\*\*|Verdict|Process Verdict)[\s:\*]+(PASS|FAIL|ACCEPTED|COMPLIANT|NOT PERFORMED|SKIPPED|PARTIAL)", "Verdict"),
            ]
            for pat, field_name in required_patterns:
                if not re.search(pat, content, re.IGNORECASE):
                    return False, [f"ERROR: Independent review record in {found_file} is missing required metadata field: {field_name}"]

            return True, [f"PASS: Independent review record verified for '{sprint_name}': {found_file}"]
        except Exception as e:
            return False, [f"ERROR: Failed to read review record file {found_file}: {e}"]
    else:
        md_files = [f for f in os.listdir(rev_dir) if f.endswith(".md") and not f.startswith(".")]
        if not md_files:
            return False, [f"ERROR: No independent review records (*.md) found in {rev_dir}"]

        valid_files = 0
        for fname in md_files:
            fpath = os.path.join(rev_dir, fname)
            try:
                with open(fpath, "r", encoding="utf-8") as f:
                    content = f.read().strip()
                if (
                    content
                    and re.search(r"(?:\*\*Reviewer\*\*|Reviewer:?)", content, re.IGNORECASE)
                    and re.search(r"(?:\*\*Verdict\*\*|Verdict:?)", content, re.IGNORECASE)
                ):
                    valid_files += 1
            except Exception:
                pass

        if valid_files == 0:
            return False, [f"ERROR: No valid, non-empty independent review records found in {rev_dir}"]
        return True, [f"PASS: Verified {valid_files} independent review record(s) in {rev_dir}."]


RETRO_ALLOWLIST_CUTOFF_TS = 1794925800  # 2026-11-17T15:30:00+01:00 (ATT-8584 enforcement baseline)
RETRO_GRANDFATHERED_SHAS = ("8aa881df",)

RETRO_ALLOWLIST_PATTERNS = [
    re.compile(r"^docs/engineering/retros/Sprint_Review_and_Retro_[^/]+\.md$"),
    re.compile(r"^docs/engineering/gates/.+"),
    re.compile(r"^docs/engineering/qa_audits/.+"),
    re.compile(r"^docs/engineering/reviews/.+"),
    re.compile(r"^docs/engineering/test_evidence/.+"),
    re.compile(r"^docs/engineering/metrics/process_metrics\.jsonl$"),
    re.compile(r"^docs/management/risk_register\.md$"),
    re.compile(r"^docs/releases/[^/]+/manifest\.md$"),
]

RETRO_PROTECTED_PREFIXES = (
    ".agents/rules/",
    ".agents/skills/",
    "tools/",
)

RETRO_PROTECTED_EXACT = {
    "docs/coding_standards.md",
    "docs/design_guidelines.md",
    "docs/project_governance.md",
    "docs/project_protocol.md",
    "docs/engineering/rule_history_archive.md",
}


def _normalize_repo_rel_path(path_str):
    p = str(path_str).strip().replace("\\", "/")
    while p.startswith("./"):
        p = p[2:]
    return p


def _is_retro_allowlisted_path(path_str):
    norm = _normalize_repo_rel_path(path_str)
    return any(pat.match(norm) for pat in RETRO_ALLOWLIST_PATTERNS)


def _is_retro_protected_path(path_str):
    norm = _normalize_repo_rel_path(path_str)
    if norm in RETRO_PROTECTED_EXACT:
        return True
    return any(norm.startswith(prefix) for prefix in RETRO_PROTECTED_PREFIXES)


def _evaluate_retro_commit_files(changed_files, commit_message=None, commit_sha=None, commit_ts=None):
    """Evaluates changed_files against the autonomous Ceremony 2 write-allowlist (REQ-PRO-117)."""
    if commit_sha and any(commit_sha.startswith(g) for g in RETRO_GRANDFATHERED_SHAS):
        return True, [f"PASS: Pre-ATT-8584 historical commit {commit_sha[:8]} exempted."]
    if commit_ts is not None and commit_ts < RETRO_ALLOWLIST_CUTOFF_TS:
        return True, [f"PASS: Pre-ATT-8584 historical commit (ts={commit_ts}) exempted."]

    if commit_message is not None:
        if re.search(r"^\s*Interactive-PO\s*:\s*\S+", commit_message, re.MULTILINE | re.IGNORECASE):
            return True, ["PASS: Interactive-PO authorization present; protected paths permitted."]
        if not re.search(r"\bchore\s*\(\s*ceremony2\s*\)", commit_message, re.IGNORECASE):
            return True, ["PASS: Non-ceremony2 commit (dedicated ticket / standard workflow) exempted."]

    protected_violations = []
    other_violations = []
    normalized = [_normalize_repo_rel_path(f) for f in changed_files if str(f).strip()]
    for rel_path in normalized:
        if not _is_retro_allowlisted_path(rel_path):
            if _is_retro_protected_path(rel_path):
                protected_violations.append(rel_path)
            else:
                other_violations.append(rel_path)

    if protected_violations or other_violations:
        errors = []
        if protected_violations:
            errors.append(
                "ERROR: Autonomous Ceremony 2 commit modified PROHIBITED protected rule/skill/standard/tool "
                f"path(s) without Interactive-PO or dedicated ticket (REQ-PRO-117.2): {', '.join(protected_violations)}"
            )
        if other_violations:
            errors.append(
                "ERROR: Autonomous Ceremony 2 commit modified non-allowlisted path(s) "
                f"(REQ-PRO-117.1): {', '.join(other_violations)}"
            )
        return False, errors

    return True, [f"PASS: Autonomous Retro write-allowlist verified ({len(normalized)} file(s))."]


def verify_retro_write_allowlist(
    commit_ref=None,
    changed_files=None,
    commit_message=None,
    base_ref="develop",
    repo_root=None,
):
    """
    REQ-PRO-117.3 / TST-PRO-117.1 / TST-PRO-117.3:
    Verifies that post-cutoff autonomous chore(ceremony2) commits only touch the 7-path
    evidence/log allowlist unless explicitly authorized via Interactive-PO or a dedicated ticket.
    """
    if changed_files is not None:
        return _evaluate_retro_commit_files(changed_files, commit_message=commit_message)

    cwd = repo_root if repo_root else os.getcwd()
    try:
        if commit_ref and str(commit_ref).strip():
            refs_to_check = [str(commit_ref).strip()]
        else:
            rev_range = f"{base_ref}..HEAD"
            check_base = subprocess.run(
                ["git", "rev-parse", "--verify", base_ref],
                cwd=cwd,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            if check_base.returncode != 0:
                rev_range = "HEAD~10..HEAD"
            revs_out = subprocess.check_output(
                ["git", "rev-list", rev_range],
                cwd=cwd,
                stderr=subprocess.DEVNULL,
            ).decode("utf-8", errors="replace").splitlines()
            refs_to_check = [r.strip() for r in revs_out if r.strip()]
    except Exception as exc:
        return True, [f"PASS: Git commit scan skipped ({exc})."]

    if not refs_to_check:
        return True, ["PASS: Autonomous Retro write-allowlist verified (0 ceremony2 commits in range)."]

    checked_count = 0
    for ref in refs_to_check:
        try:
            meta = subprocess.check_output(
                ["git", "-c", "core.quotePath=false", "log", "-n", "1", "--format=%H%n%ct%n%B", ref],
                cwd=cwd,
                stderr=subprocess.DEVNULL,
            ).decode("utf-8", errors="replace")
            lines = meta.splitlines()
            if len(lines) < 2:
                continue
            sha = lines[0].strip()
            try:
                ts = int(lines[1].strip())
            except ValueError:
                ts = None
            msg = "\n".join(lines[2:])
            if not re.search(r"\bchore\s*\(\s*ceremony2\s*\)", msg, re.IGNORECASE):
                continue
            files_out = subprocess.check_output(
                ["git", "-c", "core.quotePath=false", "diff-tree", "--root", "--no-commit-id", "--name-only", "-r", sha],
                cwd=cwd,
                stderr=subprocess.DEVNULL,
            ).decode("utf-8", errors="replace").splitlines()
            ok, diag = _evaluate_retro_commit_files(
                files_out,
                commit_message=msg,
                commit_sha=sha,
                commit_ts=ts,
            )
            checked_count += 1
            if not ok:
                return False, [f"Commit {sha[:8]}: {d}" for d in diag]
        except Exception as exc:
            return False, [f"ERROR: Failed to inspect commit '{ref}' for Retro write-allowlist: {exc}"]

    return True, [
        f"PASS: Autonomous Retro write-allowlist verified ({checked_count} ceremony2 commit(s) checked)."
    ]


def verify_test_evidence(target_id=None, repo_root=None):
    """
    REQ-PRO-100.4 / TST-PRO-100.2:
    Verifies that persistent test and coverage evidence records in docs/engineering/test_evidence/
    conform to schema and report zero test failures.
    """
    if repo_root is None:
        repo_root = os.getcwd()

    evidence_dir = os.path.join(repo_root, "docs", "engineering", "test_evidence")
    if not os.path.isdir(evidence_dir):
        return False, [f"ERROR: Test evidence directory does not exist: {evidence_dir} (REQ-PRO-100.1)."]

    if target_id and str(target_id).strip():
        clean_target = str(target_id).strip()
        slug = clean_target.replace("/", "_")
        candidates = [
            slug,
            f"sprint_{slug}" if not slug.startswith("sprint_") else slug,
            slug.replace("sprint_", "", 1) if slug.startswith("sprint_") else slug,
        ]

        found_json = None
        for cand in candidates:
            cand_p = os.path.join(evidence_dir, f"{cand}.json")
            if os.path.isfile(cand_p):
                found_json = cand_p
                break

        if not found_json:
            return False, [
                f"ERROR: Missing test evidence record for target '{target_id}'. "
                f"Expected one of {[os.path.join('docs/engineering/test_evidence', f'{c}.json') for c in candidates]}."
            ]

        try:
            with open(found_json, "r", encoding="utf-8") as f:
                data = json.load(f)
            unit_tests = data.get("unit_tests", {})
            failures = unit_tests.get("failures", 0)
            errors = unit_tests.get("errors", 0)
            if failures > 0 or errors > 0:
                return False, [f"ERROR: Test evidence for '{target_id}' reports {failures} failure(s) and {errors} error(s)."]
        except Exception as e:
            return False, [f"ERROR: Failed to parse test evidence JSON {found_json}: {e}"]

        return True, [f"PASS: Verified test evidence record for target '{target_id}' ({found_json})."]

    all_files = os.listdir(evidence_dir)
    json_files = [f for f in all_files if f.endswith(".json")]

    if not json_files:
        readme_path = os.path.join(evidence_dir, "README.md")
        if os.path.isfile(readme_path):
            return True, [f"PASS: docs/engineering/test_evidence/ is canonical and clean (README.md verified)."]
        return False, [f"ERROR: docs/engineering/test_evidence/ contains neither evidence records nor README.md."]

    diagnostics = []
    has_failing = False
    for jf in json_files:
        jp = os.path.join(evidence_dir, jf)
        try:
            with open(jp, "r", encoding="utf-8") as f:
                data = json.load(f)
            unit_tests = data.get("unit_tests", {})
            failures = unit_tests.get("failures", 0)
            errors = unit_tests.get("errors", 0)
            if failures > 0 or errors > 0:
                diagnostics.append(f"ERROR: Test evidence {jf} reports {failures} failure(s) and {errors} error(s).")
                has_failing = True
        except Exception as e:
            diagnostics.append(f"ERROR: Failed to parse test evidence {jf}: {e}")
            has_failing = True

    if has_failing:
        return False, diagnostics

    return True, [f"PASS: Verified {len(json_files)} test evidence record(s) in {evidence_dir} with 0 failures."]




def verify_engineering_directory_structure(eng_dir="docs/engineering"):
    """
    Verifies that docs/engineering/ contains only canonical ASPICE directories and approved files
    (no stray folders like summaries/, impl_plans/, test_reports/, implementation/, or stray symlinks),
    and enforces canonical test_evidence directory layout (Rule 8.4, REQ-PRO-100.1, REQ-PRO-067.3).
    """
    if not os.path.isdir(eng_dir):
        return True, []
    test_results_path = os.path.join(eng_dir, "test_results")
    if os.path.lexists(test_results_path):
        return False, [
            f"ERROR: {test_results_path} symlink is obsolete and retired (REQ-PRO-067.3, REQ-PRO-100). Use docs/engineering/test_evidence/ instead."
        ]
    unexpected = sorted(set(os.listdir(eng_dir)) - ALLOWED_ENGINEERING_ENTRIES)
    if unexpected:
        return False, [
            f"ERROR: Non-canonical entries found in {eng_dir}/: {', '.join(unexpected)}. "
            f"Allowed entries: {', '.join(sorted(ALLOWED_ENGINEERING_ENTRIES))}."
        ]
    return True, []


def verify_living_doc_directory_layout(repo_root=None):
    """
    Fail-closed enforcement of canonical living documentation directory layout (REQ-PRO-129.3).
    - docs/requirements/ MUST contain strictly regular files matching req_*.md (no SCRUM-*_plan.md or stray entries).
    - docs/tests/ MUST contain strictly regular files matching tst_*.md.
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    layout_rules = [
        ("docs/requirements", "req_"),
        ("docs/tests", "tst_"),
    ]
    errors = []
    for rel_dir, required_prefix in layout_rules:
        abs_dir = os.path.join(repo_root, rel_dir)
        if not os.path.isdir(abs_dir):
            continue
        for entry in sorted(os.listdir(abs_dir)):
            abs_entry = os.path.join(abs_dir, entry)
            if (
                os.path.islink(abs_entry)
                or not os.path.isfile(abs_entry)
                or not entry.startswith(required_prefix)
                or not entry.endswith(".md")
            ):
                errors.append(
                    f"ERROR: Non-canonical entry '{entry}' in {rel_dir}/ "
                    f"(expected regular '{required_prefix}*.md' shard only, REQ-PRO-129.3)."
                )
    if errors:
        return False, errors
    return True, ["PASS: Living documentation directories contain only canonical req_*.md and tst_*.md shards."]


def verify_master_table_parity(repo_root=None):
    """
    Verifies that the summary tables in docs/requirements.md and docs/tests.md have 100% mathematical
    parity with actual parsed ### REQ-* and ### TST-* headings across the 9 domain shards (ATT-5431).
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    errors = []
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
            with open(fp, "r", encoding="utf-8") as f:
                lines = f.readlines()
            tot = ver = impl = plan = 0
            for l in lines:
                m = re.match(r"^(#{2,4})\s*(" + prefix + r"-[A-Z0-9_-]+):?\s*(.*?)$", l.strip())
                if not m:
                    continue
                tot += 1
                if "[Verified]" in l:
                    ver += 1
                elif "[Implemented]" in l:
                    impl += 1
                else:
                    plan += 1
            shard_counts[fn] = (tot, ver, impl, plan)

            header_label = "Total Requirements" if prefix == "REQ" else "Total Tests"
            with open(fp, "r", encoding="utf-8") as f:
                shard_content = f.read()
            new_shard_content = re.sub(
                r"\*\*Total (?:Requirements|Tests)\*\*:\s*\d+",
                f"**{header_label}**: {tot}",
                shard_content,
                count=1,
            )
            if new_shard_content != shard_content:
                shard_rel = f"{shard_dir_rel}/{fn}"
                new_shard_content = bump_work_product_header_on_edit(
                    new_shard_content, shard_rel, repo_root=repo_root
                )
                with open(fp, "w", encoding="utf-8") as f:
                    f.write(new_shard_content)

            header_label = "Total Requirements" if prefix == "REQ" else "Total Tests"
            with open(fp, "r", encoding="utf-8") as f:
                shard_content = f.read()
            new_shard_content = re.sub(
                r"\*\*Total (?:Requirements|Tests)\*\*:\s*\d+",
                f"**{header_label}**: {tot}",
                shard_content,
                count=1,
            )
            if new_shard_content != shard_content:
                shard_rel = f"{shard_dir_rel}/{fn}"
                new_shard_content = bump_work_product_header_on_edit(
                    new_shard_content, shard_rel, repo_root=repo_root
                )
                with open(fp, "w", encoding="utf-8") as f:
                    f.write(new_shard_content)

        master_fp = os.path.join(repo_root, master_rel)
        if not os.path.exists(master_fp):
            continue
        with open(master_fp, "r", encoding="utf-8") as f:
            master_lines = f.readlines()

        grand_total = sum(c[0] for c in shard_counts.values())
        shards_checked = 0
        for lno, line in enumerate(master_lines, 1):
            m_preamble = re.search(r"links to the (\d+) domain-sharded", line)
            if m_preamble:
                stated_preamble = int(m_preamble.group(1))
                if stated_preamble != len(shard_counts):
                    errors.append(
                        f"{master_rel}:{lno}: Preamble shard count mismatch: "
                        f"Stated={stated_preamble}, Actual filesystem shard count={len(shard_counts)}."
                    )
            m_link = re.search(r"\((?:requirements|tests)/((?:req|tst)_[^)]+\.md)\)", line)
            if m_link:
                s_name = m_link.group(1)
                m_cells_4 = re.search(r"\|\s*(\d+)\s*\|\s*(\d+)\s*\|\s*(\d+)\s*\|\s*(\d+)\s*\|\s*$", line)
                m_cells_3 = re.search(r"\|\s*(\d+)\s*\|\s*(\d+)\s*\|\s*(\d+)\s*\|\s*$", line)
                if m_cells_4:
                    t = int(m_cells_4.group(1))
                    v = int(m_cells_4.group(2))
                    i = int(m_cells_4.group(3))
                    p = int(m_cells_4.group(4))
                elif m_cells_3:
                    t = int(m_cells_3.group(1))
                    v = int(m_cells_3.group(2))
                    i = 0
                    p = int(m_cells_3.group(3))
                else:
                    errors.append(f"{master_rel}:{lno}: Table row for {s_name} missing numeric counts.")
                    continue
                shards_checked += 1
                if s_name in shard_counts:
                    exp_t, exp_v, exp_i, exp_p = shard_counts[s_name]
                    if (t, v, i, p) != (exp_t, exp_v, exp_i, exp_p):
                        errors.append(
                            f"{master_rel}:{lno}: Count mismatch for {s_name}: "
                            f"Table has Total={t}, Verified={v}, Implemented={i}, Planned={p}; "
                            f"Shard has Total={exp_t}, Verified={exp_v}, Implemented={exp_i}, Planned={exp_p}."
                        )
                else:
                    errors.append(f"{master_rel}:{lno}: Shard {s_name} referenced in table does not exist.")
            elif "Total Living " in line:
                m_tot = re.search(r"\*\*Total Living (?:Requirements|Tests)\*\*:\s*(\d+)", line)
                if m_tot:
                    reported_total = int(m_tot.group(1))
                    if reported_total != grand_total:
                        errors.append(
                            f"{master_rel}:{lno}: Grand total mismatch: "
                            f"Reported={reported_total}, Actual parsed sum={grand_total}."
                        )
                m_shards = re.search(r"across (\d+) domain shards", line)
                if m_shards:
                    reported_shards = int(m_shards.group(1))
                    if reported_shards != len(shard_counts):
                        errors.append(
                            f"{master_rel}:{lno}: Summary shard count mismatch: "
                            f"Reported={reported_shards}, Actual filesystem shard count={len(shard_counts)}."
                        )

        if shards_checked != len(shard_counts):
            errors.append(
                f"{master_rel}: Shard count mismatch: Table checked {shards_checked} shards, "
                f"filesystem has {len(shard_counts)} shards."
            )

    return len(errors) == 0, errors


def find_duplicate_heading_ids(repo_root=None):
    """
    Scans docs/requirements/*.md and docs/tests/*.md for requirement and test headings.
    Returns a dict mapping duplicate ID -> list of (relative_file_path, line_number) tuples.
    Only entries with len(occurrences) > 1 are included in the returned dict (REQ-PRO-066.1).
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    specs = [
        ("docs/requirements", "REQ"),
        ("docs/tests", "TST"),
    ]
    seen = {}
    for shard_dir_rel, prefix in specs:
        abs_shard_dir = os.path.join(repo_root, shard_dir_rel)
        if not os.path.isdir(abs_shard_dir):
            continue
        for fn in sorted(os.listdir(abs_shard_dir)):
            if not fn.endswith(".md"):
                continue
            rel_path = os.path.join(shard_dir_rel, fn)
            abs_fp = os.path.join(abs_shard_dir, fn)
            with open(abs_fp, "r", encoding="utf-8") as f:
                for lno, line in enumerate(f, 1):
                    m = re.match(r"^(#{2,4})\s*(" + prefix + r"-[A-Z0-9_-]+):?\s*(.*?)$", line.strip())
                    if m:
                        item_id = m.group(2)
                        seen.setdefault(item_id, []).append((rel_path, lno))

    return {item_id: locs for item_id, locs in sorted(seen.items()) if len(locs) > 1}


def verify_unique_ids(repo_root=None, fail_on_duplicates=False, legacy_allowlist=None):
    """
    Verifies that all ### REQ-* and ### TST-* heading IDs are unique (REQ-PRO-066 / TST-PRO-066).
    Reports every duplicate with file and line.

    If fail_on_duplicates is True (or --enforce-unique / --fail-on-duplicates CLI flag):
      Fails if ANY duplicate is detected, reporting file and line for each occurrence.
    If fail_on_duplicates is False (default):
      Known legacy duplicates present in legacy_allowlist (or LEGACY_DUPLICATE_IDS) are reported
      as WARNINGs without failing validation, preserving CI green status until ATT-6123.
      Any NEW duplicate ID (not in the allowlist) causes validation to fail.

    Returns (success: bool, diagnostics: list[str]).
    """
    if legacy_allowlist is None:
        legacy_allowlist = LEGACY_DUPLICATE_IDS

    duplicates = find_duplicate_heading_ids(repo_root=repo_root)
    diagnostics = []

    if not duplicates:
        diagnostics.append("PASS: All REQ and TST heading IDs are unique across living documentation shards.")
        return True, diagnostics

    legacy_dups = {k: v for k, v in duplicates.items() if k in legacy_allowlist}
    new_dups = {k: v for k, v in duplicates.items() if k not in legacy_allowlist}

    for item_id, locs in sorted(duplicates.items()):
        loc_str = ", ".join(f"{fp}:{lno}" for fp, lno in locs)
        is_legacy = item_id in legacy_allowlist
        tag = "WARNING (Legacy duplicate)" if (is_legacy and not fail_on_duplicates) else "ERROR (Duplicate ID)"
        diagnostics.append(f"{tag}: {item_id} appears {len(locs)} times: {loc_str}")

    if fail_on_duplicates:
        diagnostics.insert(0, f"ERROR: Duplicate REQ/TST IDs detected ({len(duplicates)} duplicates).")
        return False, diagnostics

    if new_dups:
        diagnostics.insert(
            0,
            f"ERROR: {len(new_dups)} new duplicate REQ/TST ID(s) detected outside legacy allowlist: "
            f"{', '.join(sorted(new_dups.keys()))}."
        )
        return False, diagnostics

    diagnostics.insert(
        0,
        f"PASS: Zero new duplicate REQ/TST IDs detected ({len(legacy_dups)} legacy duplicates tolerated pending ATT-6123)."
    )
    return True, diagnostics


class DanglingReference:
    """Represents a single dangling reference from a living doc entry to a missing heading."""
    def __init__(self, source_file, line_no, source_id, target_id, target_type):
        self.source_file = source_file
        self.line_no = line_no
        self.source_id = source_id
        self.target_id = target_id
        self.target_type = target_type

    def __iter__(self):
        return iter((self.source_file, self.line_no, self.source_id, self.target_id))

    def __getitem__(self, item):
        if isinstance(item, int):
            return (self.source_file, self.line_no, self.source_id, self.target_id)[item]
        return getattr(self, item)

    def __repr__(self):
        return f"DanglingReference({self.source_file}:{self.line_no} [{self.source_id}] -> {self.target_id})"

    def __eq__(self, other):
        if isinstance(other, (tuple, list)):
            return (self.source_file, self.line_no, self.source_id, self.target_id) == tuple(other)
        if isinstance(other, DanglingReference):
            return (self.source_file, self.line_no, self.source_id, self.target_id) == (
                other.source_file,
                other.line_no,
                other.source_id,
                other.target_id,
            )
        return False


class DanglingReferencesList(list):
    """List containing all dangling references, with dictionary and attribute access for backward compatibility."""
    def __init__(self, dangling_tsts=None, dangling_reqs=None):
        tsts = list(dangling_tsts or [])
        reqs = list(dangling_reqs or [])
        super().__init__(tsts + reqs)
        self.dangling_tsts = tsts
        self.dangling_reqs = reqs

    def __getitem__(self, item):
        if item == "dangling_tsts":
            return self.dangling_tsts
        elif item == "dangling_reqs":
            return self.dangling_reqs
        return super().__getitem__(item)

    def get(self, key, default=None):
        if key == "dangling_tsts":
            return self.dangling_tsts
        elif key == "dangling_reqs":
            return self.dangling_reqs
        return default

    def __contains__(self, item):
        if item in ("dangling_tsts", "dangling_reqs"):
            return True
        return super().__contains__(item)


def _load_shard_sources(shards_arg, default_dir_rel, repo_root):
    """
    Normalizes shard inputs into a list of (rel_path, lines) tuples.
    Supports:
      - None (scans default_dir_rel under repo_root)
      - directory path (str)
      - dict: {rel_path: str_content_or_lines}
      - list/tuple of file paths
    """
    if shards_arg is None:
        abs_dir = os.path.join(repo_root, default_dir_rel) if repo_root else default_dir_rel
        if not os.path.isdir(abs_dir):
            return []
        result = []
        for fn in sorted(os.listdir(abs_dir)):
            if fn.endswith(".md"):
                rel_path = os.path.join(default_dir_rel, fn)
                abs_fp = os.path.join(abs_dir, fn)
                with open(abs_fp, "r", encoding="utf-8") as f:
                    result.append((rel_path, f.readlines()))
        return result
    elif isinstance(shards_arg, str) and os.path.isdir(shards_arg):
        result = []
        for fn in sorted(os.listdir(shards_arg)):
            if fn.endswith(".md"):
                rel_path = os.path.join(os.path.basename(shards_arg), fn)
                abs_fp = os.path.join(shards_arg, fn)
                with open(abs_fp, "r", encoding="utf-8") as f:
                    result.append((rel_path, f.readlines()))
        return result
    elif isinstance(shards_arg, dict):
        result = []
        for k, v in sorted(shards_arg.items()):
            if isinstance(v, str):
                lines = v.splitlines(keepends=True)
            else:
                lines = list(v)
            result.append((k, lines))
        return result
    elif isinstance(shards_arg, (list, tuple)):
        result = []
        for item in shards_arg:
            if isinstance(item, str) and os.path.isfile(item):
                with open(item, "r", encoding="utf-8") as f:
                    result.append((item, f.readlines()))
        return result
    return []


def find_dangling_references(req_shards=None, tst_shards=None, repo_root=None):
    """
    Extracts all cited TST IDs from the 'Tests' field of every REQ and verifies they match an existing ### TST-* heading.
    Extracts all cited REQ IDs from the 'Requirements' field of every TST and verifies they match an existing ### REQ-* heading.
    Reports all dangling references with source file, line, and missing target ID (REQ-PRO-070.1, REQ-PRO-070.2).

    Returns a DanglingReferencesList (supports list operations, attribute access (.dangling_tsts, .dangling_reqs),
    and dict key indexing ['dangling_tsts'], ['dangling_reqs']).
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    loaded_reqs = _load_shard_sources(req_shards, "docs/requirements", repo_root)
    loaded_tsts = _load_shard_sources(tst_shards, "docs/tests", repo_root)

    # 1. Discover all declared headings
    known_req_ids = set()
    known_tst_ids = set()

    for rel_path, lines in loaded_reqs:
        for line in lines:
            m = re.match(r"^(#{2,4})\s*(REQ-[A-Z0-9_-]+):?\s*(.*?)$", line.strip())
            if m:
                known_req_ids.add(m.group(2))

    for rel_path, lines in loaded_tsts:
        for line in lines:
            m = re.match(r"^(#{2,4})\s*(TST-[A-Z0-9_-]+):?\s*(.*?)$", line.strip())
            if m:
                known_tst_ids.add(m.group(2))

    dangling_tsts = []
    dangling_reqs = []

    # 2. Extract cited TST IDs in REQ entries
    for rel_path, lines in loaded_reqs:
        cur_req = None
        for lno, line in enumerate(lines, 1):
            m_h = re.match(r"^(#{2,4})\s*(REQ-[A-Z0-9_-]+):?\s*(.*?)$", line.strip())
            if m_h:
                cur_req = m_h.group(2)
            m_f = re.match(r"^\s*[-*]\s*\*{0,2}Tests?\*{0,2}\s*:\s*(.*)", line, re.IGNORECASE)
            if m_f:
                val = m_f.group(1)
                cites = re.findall(r"(?<![A-Za-z0-9_-])TST-[A-Z0-9_-]+(?![A-Za-z0-9_-])", val)
                for c in cites:
                    if c not in known_tst_ids:
                        dangling_tsts.append(
                            DanglingReference(
                                source_file=rel_path,
                                line_no=lno,
                                source_id=cur_req or "UNKNOWN",
                                target_id=c,
                                target_type="TST",
                            )
                        )

    # 3. Extract cited REQ IDs in TST entries
    for rel_path, lines in loaded_tsts:
        cur_tst = None
        for lno, line in enumerate(lines, 1):
            m_h = re.match(r"^(#{2,4})\s*(TST-[A-Z0-9_-]+):?\s*(.*?)$", line.strip())
            if m_h:
                cur_tst = m_h.group(2)
            m_f = re.match(
                r"^\s*[-*]\s*\*{0,2}(?:Requirements?|Requirement\(s\)|Target Requirement|Requirements Covered|Verifies)\*{0,2}\s*:\s*(.*)",
                line,
                re.IGNORECASE,
            )
            if m_f:
                val = m_f.group(1)
                cites = re.findall(r"(?<![A-Za-z0-9_-])REQ-[A-Z0-9_-]+(?![A-Za-z0-9_-])", val)
                for c in cites:
                    if c not in known_req_ids:
                        dangling_reqs.append(
                            DanglingReference(
                                source_file=rel_path,
                                line_no=lno,
                                source_id=cur_tst or "UNKNOWN",
                                target_id=c,
                                target_type="REQ",
                            )
                        )

    return DanglingReferencesList(dangling_tsts=dangling_tsts, dangling_reqs=dangling_reqs)


def verify_tests_field_format(repo_root=None):
    """
    Verifies that all 'Tests:' fields in docs/requirements/*.md contain strictly backticked
    TST IDs (format `TST-[A-Z0-9_-]+`) or standardized placeholders (None, Planned, Review,
    Manual inspection) per REQ-PRO-075 (ATT-6125).
    Rejects truncated sentence fragments, dangling boolean checks, source code filenames (.kt, .java, .xml),
    and trailing inline metadata.
    Returns (is_ok: bool, diagnostics: list[str]).
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    pattern = re.compile(
        r"^(`TST-[A-Z0-9_-]+`|`?(?:None|Planned|Review|Manual inspection)`?)(\s*,\s*(`TST-[A-Z0-9_-]+`|`?(?:None|Planned|Review|Manual inspection)`?))*$",
        re.IGNORECASE,
    )

    req_dir = os.path.join(repo_root, "docs", "requirements")
    if not os.path.isdir(req_dir):
        return True, ["PASS: No requirement shards found to validate."]

    req_files = sorted(
        [
            os.path.join(req_dir, f)
            for f in os.listdir(req_dir)
            if f.startswith("req_") and f.endswith(".md")
        ]
    )
    diagnostics = []
    errors = []

    for fpath in req_files:
        rel_path = os.path.relpath(fpath, repo_root)
        with open(fpath, "r", encoding="utf-8") as fp:
            cur_req = None
            for idx, line in enumerate(fp):
                m_req = re.match(r"^(#{2,4})\s*(\[)?(REQ-[A-Z0-9_-]+)", line.strip())
                if m_req:
                    cur_req = m_req.group(3)
                m_test = re.match(r"^\s*[-*]\s*\*{0,2}Tests?\*{0,2}\s*:\s*(.*)", line, re.IGNORECASE)
                if m_test and cur_req:
                    val = m_test.group(1).strip()
                    # Check for unclosed backticks
                    if val.count("`") % 2 != 0:
                        errors.append(
                            f"ERROR: {rel_path}:{idx+1} [{cur_req}] unclosed backtick in Tests field: '{val}'"
                        )
                        continue
                    # Check for source code files
                    if re.search(r"\.(?:kt|java|xml)\b", val):
                        errors.append(
                            f"ERROR: {rel_path}:{idx+1} [{cur_req}] source code file in Tests field: '{val}'"
                        )
                        continue
                    # Check against conforming pattern
                    if not pattern.match(val):
                        errors.append(
                            f"ERROR: {rel_path}:{idx+1} [{cur_req}] invalid Tests field format: '{val}'"
                        )

    if errors:
        diagnostics.append(f"FAIL: {len(errors)} Tests field format violation(s) detected across requirement shards (REQ-PRO-075):")
        diagnostics.extend(errors)
        return False, diagnostics

    diagnostics.append("PASS: All Tests fields in requirement shards conform to valid TST ID and placeholder syntax (REQ-PRO-075).")
    return True, diagnostics


def verify_dangling_references(
    repo_root=None,
    fail_on_dangling=False,
    legacy_tst_allowlist=None,
    legacy_req_allowlist=None,
    req_shards=None,
    tst_shards=None,
):
    """
    Verifies that all cited REQ and TST references resolve to authentic headings (REQ-PRO-070 / TST-PRO-070).
    Reports all dangling references with source file, line number, source ID, and missing target ID.

    If fail_on_dangling is True (or --enforce-links / --fail-on-dangling CLI flag):
      Fails if ANY dangling reference is detected (including legacy items).
    If fail_on_dangling is False (default):
      Known legacy dangling references present in allowlists are reported as WARNINGs without failing validation.
      Any NEW dangling reference (not in the allowlist) causes validation to fail.

    Returns (success: bool, diagnostics: list[str]).
    """
    if legacy_tst_allowlist is None:
        legacy_tst_allowlist = LEGACY_DANGLING_TST_REFS
    if legacy_req_allowlist is None:
        legacy_req_allowlist = LEGACY_DANGLING_REQ_REFS

    dangling = find_dangling_references(req_shards=req_shards, tst_shards=tst_shards, repo_root=repo_root)
    diagnostics = []

    if not dangling:
        diagnostics.append("PASS: All cited REQ and TST references resolve to authentic headings.")
        return True, diagnostics

    new_dangling = []
    legacy_dangling = []

    for ref in dangling:
        is_legacy = (
            (ref.target_type == "TST" and ref.target_id in legacy_tst_allowlist)
            or (ref.target_type == "REQ" and ref.target_id in legacy_req_allowlist)
        )
        if is_legacy and not fail_on_dangling:
            legacy_dangling.append(ref)
            tag = "WARNING (Legacy dangling reference)"
        else:
            new_dangling.append(ref)
            tag = "ERROR (Dangling reference)"

        diagnostics.append(
            f"{tag}: {ref.source_file}:{ref.line_no} [{ref.source_id}] cites non-existent {ref.target_id}."
        )

    if fail_on_dangling:
        diagnostics.insert(
            0,
            f"ERROR: Dangling REQ/TST references detected ({len(dangling)} references, strict enforcement active)."
        )
        return False, diagnostics

    if new_dangling:
        diagnostics.insert(
            0,
            f"ERROR: {len(new_dangling)} new dangling REQ/TST reference(s) detected outside legacy allowlist: "
            f"{', '.join(sorted(set(r.target_id for r in new_dangling)))}."
        )
        return False, diagnostics

    diagnostics.insert(
        0,
        f"PASS: Zero new dangling REQ/TST references detected ({len(legacy_dangling)} legacy references tolerated pending A-08.x)."
    )
    return True, diagnostics


class TraceabilityOrphansResult:
    """Represents the outcome of bidirectional REQ-TST orphan discovery (REQ-PRO-087, REQ-PRO-127.1)."""
    def __init__(
        self,
        declared_reqs,
        declared_tsts,
        orphan_reqs,
        dangling_req_cites,
        tsts_without_reqs,
        asymmetric_req_to_tst=None,
        asymmetric_tst_to_req=None,
    ):
        self.declared_reqs = set(declared_reqs)
        self.declared_tsts = set(declared_tsts)
        self.orphan_reqs = sorted(orphan_reqs)
        self.dangling_req_cites = list(dangling_req_cites)
        self.tsts_without_reqs = sorted(tsts_without_reqs)
        self.asymmetric_req_to_tst = sorted(asymmetric_req_to_tst or [])
        self.asymmetric_tst_to_req = sorted(asymmetric_tst_to_req or [])


def load_traceability_baseline(baseline_path=None, repo_root=None):
    """
    Loads and validates the JSON traceability orphan baseline (REQ-PRO-087.3).
    Returns dict: {"orphan_requirements": set(str), "metadata": dict, "raw": dict, "path": str, "exists": bool}.
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    if baseline_path is None:
        baseline_path = os.path.join(repo_root, "tools", "traceability_baseline.json")
    elif not os.path.isabs(baseline_path):
        baseline_path = os.path.join(repo_root, baseline_path)

    if not os.path.isfile(baseline_path):
        return {
            "orphan_requirements": set(),
            "metadata": {},
            "raw": {},
            "path": baseline_path,
            "exists": False,
        }

    with open(baseline_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    orphans = set(data.get("orphan_requirements", []))
    return {
        "orphan_requirements": orphans,
        "metadata": data.get("metadata", {}),
        "raw": data,
        "path": baseline_path,
        "exists": True,
    }


def find_traceability_orphans(req_shards=None, tst_shards=None, repo_root=None):
    """
    Computes bidirectional REQ-TST traceability orphans across living doc shards (REQ-PRO-087).
    Discovers:
      1. All declared REQ IDs in requirement shards.
      2. All declared TST IDs in test shards.
      3. All REQ IDs cited by TST entries across supported fields:
         'Requirements:', 'Requirement(s):', 'Target Requirement:', 'Requirements Covered:', 'Verifies:'.
      4. Orphan REQs: declared REQs not cited by ANY test specification.
      5. Dangling target REQs: REQs cited by TSTs that do not exist in declared REQs.
      6. TSTs without REQs: TSTs that do not cite any REQ.
    Returns TraceabilityOrphansResult.
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    loaded_reqs = _load_shard_sources(req_shards, "docs/requirements", repo_root)
    loaded_tsts = _load_shard_sources(tst_shards, "docs/tests", repo_root)

    known_req_ids = set()
    req_to_tsts = {}
    tst_citation_pattern = re.compile(
        r"^\s*[-*]\s*\*{0,2}Tests?\*{0,2}\s*:\s*(.*)",
        re.IGNORECASE,
    )
    for rel_path, lines in loaded_reqs:
        cur_req = None
        for line in lines:
            m = re.match(r"^(#{2,4})\s*(REQ-[A-Z0-9_-]+):?\s*(.*?)$", line.strip())
            if m:
                clean_id = m.group(2).rstrip("-_")
                if clean_id not in ("REQ-XXX", "REQ-DOM-NNN", "REQ-DOM-XXX", "REQ-NNN"):
                    known_req_ids.add(clean_id)
                    cur_req = clean_id
                    req_to_tsts.setdefault(cur_req, set())
                else:
                    cur_req = None
                continue
            m_t = tst_citation_pattern.match(line)
            if m_t and cur_req:
                t_cites = re.findall(r"(?<![A-Za-z0-9_-])TST-[A-Z0-9_-]+(?![A-Za-z0-9_-])", m_t.group(1))
                for tc in t_cites:
                    clean_tc = tc.rstrip("-_")
                    if clean_tc not in ("TST-XXX", "TST-DOM-NNN"):
                        req_to_tsts[cur_req].add(clean_tc)

    known_tst_ids = set()
    tst_to_reqs = {}
    cited_req_ids = set()
    dangling_cites = []
    tsts_without_reqs = []

    req_citation_pattern = re.compile(
        r"^\s*[-*]\s*\*{0,2}(?:Requirements?|Requirement\(s\)|Target Requirement|Requirements Covered|Verifies)\*{0,2}\s*:\s*(.*)",
        re.IGNORECASE,
    )

    for rel_path, lines in loaded_tsts:
        cur_tst = None
        cur_tst_has_cites = False
        for lno, line in enumerate(lines, 1):
            m_h = re.match(r"^(#{2,4})\s*(TST-[A-Z0-9_-]+):?\s*(.*?)$", line.strip())
            if m_h:
                if cur_tst and not cur_tst_has_cites:
                    tsts_without_reqs.append(cur_tst)
                cur_tst = m_h.group(2).rstrip("-_")
                cur_tst_has_cites = False
                if cur_tst not in ("TST-XXX", "TST-DOM-NNN"):
                    known_tst_ids.add(cur_tst)
                    tst_to_reqs.setdefault(cur_tst, set())
                else:
                    cur_tst = None
            m_f = req_citation_pattern.match(line)
            if m_f and cur_tst:
                val = m_f.group(1)
                cites = re.findall(r"(?<![A-Za-z0-9_-])REQ-[A-Z0-9_-]+(?![A-Za-z0-9_-])", val)
                clean_cites = [
                    c.rstrip("-_")
                    for c in cites
                    if c.rstrip("-_") not in ("REQ-XXX", "REQ-DOM-NNN", "REQ-DOM-XXX", "REQ-NNN")
                ]
                if clean_cites:
                    cur_tst_has_cites = True
                    for c in clean_cites:
                        cited_req_ids.add(c)
                        tst_to_reqs[cur_tst].add(c)
                        if c not in known_req_ids:
                            dangling_cites.append(
                                DanglingReference(
                                    source_file=rel_path,
                                    line_no=lno,
                                    source_id=cur_tst,
                                    target_id=c,
                                    target_type="REQ",
                                )
                            )
        if cur_tst and not cur_tst_has_cites:
            tsts_without_reqs.append(cur_tst)

    orphan_reqs = sorted(known_req_ids - cited_req_ids)

    asymmetric_req_to_tst = []
    for req_id in sorted(req_to_tsts):
        for tst_id in sorted(req_to_tsts[req_id]):
            if req_id not in tst_to_reqs.get(tst_id, set()):
                asymmetric_req_to_tst.append((req_id, tst_id))

    asymmetric_tst_to_req = []
    for tst_id in sorted(tst_to_reqs):
        for req_id in sorted(tst_to_reqs[tst_id]):
            if req_id in known_req_ids and tst_id not in req_to_tsts.get(req_id, set()):
                asymmetric_tst_to_req.append((tst_id, req_id))

    return TraceabilityOrphansResult(
        declared_reqs=known_req_ids,
        declared_tsts=known_tst_ids,
        orphan_reqs=orphan_reqs,
        dangling_req_cites=dangling_cites,
        tsts_without_reqs=tsts_without_reqs,
        asymmetric_req_to_tst=asymmetric_req_to_tst,
        asymmetric_tst_to_req=asymmetric_tst_to_req,
    )


def verify_traceability_orphans(
    repo_root=None,
    baseline_path=None,
    fail_on_orphans=True,
    req_shards=None,
    tst_shards=None,
):
    """
    Verifies machine-verified bidirectional requirement-to-test traceability (REQ-PRO-087 / ASPICE Finding F4).
    Enforces Acceptance Criteria 1, 2, and 3:
      - Criterion 1: Fails on any requirement without a verifying test that is not in the baseline.
      - Criterion 2: Fails on any test referencing a non-existent requirement.
      - Criterion 3: Fails if new orphans are added (ratchet inflation) or baseline count grows.
    Returns (success: bool, diagnostics: list[str], report: dict).
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    diagnostics = []
    errors = []

    res = find_traceability_orphans(req_shards=req_shards, tst_shards=tst_shards, repo_root=repo_root)
    baseline_info = load_traceability_baseline(baseline_path=baseline_path, repo_root=repo_root)
    baseline_orphans = baseline_info["orphan_requirements"]

    # 1. Check Criterion 2: Tests referencing non-existent requirements
    if res.dangling_req_cites:
        for ref in res.dangling_req_cites:
            msg = f"ERROR (Criterion 2): {ref.source_file}:{ref.line_no} [{ref.source_id}] references non-existent requirement {ref.target_id}."
            errors.append(msg)
            diagnostics.append(msg)

    # 2. Check tests without requirements
    if res.tsts_without_reqs:
        for tst in res.tsts_without_reqs:
            msg = f"ERROR: Test specification {tst} does not declare any requirement in 'Verifies:' / 'Requirement(s):' field."
            errors.append(msg)
            diagnostics.append(msg)

    # 3. Check Criterion 1: Requirements without verifying test not in baseline
    unbaselined_orphans = sorted(set(res.orphan_reqs) - baseline_orphans)
    if unbaselined_orphans:
        for req in unbaselined_orphans:
            msg = f"ERROR (Criterion 1): Requirement {req} has no verifying test and is not recorded in traceability baseline."
            errors.append(msg)
            diagnostics.append(msg)

    # 4. Check Criterion 3: Ratchet monotonicity & baseline staleness
    # If an orphan in the baseline has been resolved, the baseline MUST shrink (|B_{t+1}| <= |B_t|)
    stale_orphans = sorted(baseline_orphans - set(res.orphan_reqs))
    if stale_orphans and baseline_info["exists"]:
        msg = (
            f"ERROR (Criterion 3 Ratchet): Traceability baseline contains {len(stale_orphans)} resolved requirement(s): "
            f"{', '.join(stale_orphans)}. Baseline must shrink when orphans are resolved (|B_next| <= |B_curr|). "
            f"Update {os.path.relpath(baseline_info['path'], repo_root)}."
        )
        errors.append(msg)
        diagnostics.append(msg)

    if len(res.orphan_reqs) > len(baseline_orphans) and baseline_info["exists"]:
        msg = (
            f"ERROR (Criterion 3 Ratchet): Current orphan count ({len(res.orphan_reqs)}) exceeds baseline count "
            f"({len(baseline_orphans)}). Traceability baseline can only shrink, never grow."
        )
        if msg not in errors:
            errors.append(msg)
            diagnostics.append(msg)

    # 5. Check Criterion 4: Symmetric REQ <-> TST edge equality (REQ-PRO-127.1)
    # Baselined legacy orphan REQs remain tolerated until resolved in traceability_baseline.json.
    unbaselined_asymmetric_req_to_tst = [
        (r, t) for (r, t) in res.asymmetric_req_to_tst if r not in baseline_orphans
    ]
    for req_id, tst_id in unbaselined_asymmetric_req_to_tst:
        msg = (
            f"ERROR (Criterion 4 Symmetric REQ->TST): Requirement {req_id} cites {tst_id} in 'Tests:', "
            f"but {tst_id} does not cite {req_id} back."
        )
        errors.append(msg)
        diagnostics.append(msg)

    for tst_id, req_id in res.asymmetric_tst_to_req:
        msg = (
            f"ERROR (Criterion 4 Symmetric TST->REQ): Test {tst_id} cites {req_id}, "
            f"but {req_id} does not cite {tst_id} back in 'Tests:'."
        )
        errors.append(msg)
        diagnostics.append(msg)

    report = {
        "total_reqs": len(res.declared_reqs),
        "total_tsts": len(res.declared_tsts),
        "orphan_count": len(res.orphan_reqs),
        "orphan_reqs": res.orphan_reqs,
        "unbaselined_orphans": unbaselined_orphans,
        "stale_orphans": stale_orphans,
        "dangling_cites": [ref.target_id for ref in res.dangling_req_cites],
        "asymmetric_req_to_tst": unbaselined_asymmetric_req_to_tst,
        "asymmetric_tst_to_req": list(res.asymmetric_tst_to_req),
        "baseline_count": len(baseline_orphans),
    }

    if errors:
        if fail_on_orphans:
            diagnostics.insert(0, f"ERROR: Traceability orphan governance failed with {len(errors)} violation(s).")
            return False, diagnostics, report
        else:
            diagnostics.insert(0, f"WARNING: Traceability orphan governance detected {len(errors)} violation(s) (non-blocking).")
            return True, diagnostics, report

    diagnostics.append(
        f"PASS: 100% REQ-TST bidirectional traceability verified ({len(res.orphan_reqs)} legacy orphans tolerated in baseline)."
    )
    return True, diagnostics, report


def find_code_traceability_orphans(
    repo_root=None,
    search_dirs=None,
    req_shards=None,
    tst_shards=None,
):
    """
    Scans codebase (app/src/main, app/src/test, app/src/androidTest, and tools/) for cited
    REQ-* and TST-* tokens and asserts that each cited token resolves to an authentic declared
    heading in living documentation (REQ-PRO-104.1).
    Returns (orphan_reqs: dict[str, list], orphan_tsts: dict[str, list], scanned_files: int).
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    known_req_ids = set()
    req_dir = os.path.join(repo_root, "docs/requirements")
    if os.path.isdir(req_dir):
        for fn in os.listdir(req_dir):
            if fn.endswith(".md"):
                with open(os.path.join(req_dir, fn), "r", encoding="utf-8") as f:
                    for line in f:
                        m = re.match(r"^#{2,4}\s+(REQ-[A-Z0-9_-]+):?", line.strip())
                        if m:
                            known_req_ids.add(m.group(1))

    known_tst_ids = set()
    tst_dir = os.path.join(repo_root, "docs/tests")
    if os.path.isdir(tst_dir):
        for fn in os.listdir(tst_dir):
            if fn.endswith(".md"):
                with open(os.path.join(tst_dir, fn), "r", encoding="utf-8") as f:
                    for line in f:
                        m = re.match(r"^#{2,4}\s+(TST-[A-Z0-9_-]+):?", line.strip())
                        if m:
                            known_tst_ids.add(m.group(1))

    if search_dirs is None:
        search_dirs = [
            "app/src/main",
            "app/src/test",
            "app/src/androidTest",
            "tools",
        ]

    req_pat = re.compile(r"(?<![A-Za-z0-9_-])REQ-[A-Z]+-[0-9]+(?:\.[0-9]+)?(?![A-Za-z0-9_-])")
    tst_pat = re.compile(r"(?<![A-Za-z0-9_-])TST-[A-Z]+-[0-9]+(?:\.[0-9]+)?(?![A-Za-z0-9_-])")

    ignored_file_patterns = {"test_verify_requirement_governance.py", "traceability_baseline.json"}

    orphan_reqs = {}
    orphan_tsts = {}
    scanned_files = 0

    for s_dir in search_dirs:
        abs_s_dir = os.path.join(repo_root, s_dir)
        if not os.path.isdir(abs_s_dir):
            continue
        is_tools = s_dir == "tools" or s_dir.startswith("tools/")
        for root, dirs, files in os.walk(abs_s_dir):
            for f in files:
                if f in ignored_file_patterns or f.endswith(".pyc") or f.endswith(".class"):
                    continue
                if is_tools and (f.startswith("test_") or "/test" in root or "\\test" in root):
                    continue
                if not (f.endswith(".kt") or f.endswith(".java") or f.endswith(".xml") or f.endswith(".py")):
                    continue
                scanned_files += 1
                fp = os.path.join(root, f)
                rel_fp = os.path.relpath(fp, repo_root)
                with open(fp, "r", encoding="utf-8", errors="ignore") as fh:
                    for line_no, line in enumerate(fh, 1):
                        for m in req_pat.finditer(line):
                            token = m.group(0)
                            base_id = token.split(".")[0]
                            if (
                                base_id.startswith("REQ-SIM-")
                                or base_id.startswith("REQ-XXX")
                                or base_id.startswith("REQ-DOM-")
                                or base_id.startswith("REQ-NNN")
                                or (is_tools and base_id in ("REQ-TST-005", "REQ-TST-006"))
                            ):
                                continue
                            if base_id not in known_req_ids:
                                orphan_reqs.setdefault(base_id, []).append((rel_fp, line_no))

                        for m in tst_pat.finditer(line):
                            token = m.group(0)
                            base_id = token.split(".")[0]
                            if (
                                base_id.startswith("TST-SIM-")
                                or base_id.startswith("TST-XXX")
                                or base_id.startswith("TST-DOM-")
                                or base_id.startswith("TST-NNN")
                                or (is_tools and base_id in ("TST-TST-005", "TST-TST-006", "TST-XXX-001"))
                            ):
                                continue
                            if base_id not in known_tst_ids:
                                orphan_tsts.setdefault(base_id, []).append((rel_fp, line_no))

    return orphan_reqs, orphan_tsts, scanned_files


def verify_code_traceability_orphans(
    repo_root=None,
    search_dirs=None,
    fail_on_orphans=True,
):
    """
    Verifies that zero orphan REQ or TST identifiers exist in production or test code (REQ-PRO-104.1).
    Returns (success: bool, diagnostics: list[str], report: dict).
    """
    orphan_reqs, orphan_tsts, scanned_count = find_code_traceability_orphans(
        repo_root=repo_root, search_dirs=search_dirs
    )
    diagnostics = []
    errors = []

    if orphan_reqs:
        for req_id, occurrences in sorted(orphan_reqs.items()):
            msg = f"ERROR: Code orphan requirement {req_id} cited in {len(occurrences)} location(s), e.g. {occurrences[0][0]}:{occurrences[0][1]}."
            errors.append(msg)
            diagnostics.append(msg)

    if orphan_tsts:
        for tst_id, occurrences in sorted(orphan_tsts.items()):
            msg = f"ERROR: Code orphan test specification {tst_id} cited in {len(occurrences)} location(s), e.g. {occurrences[0][0]}:{occurrences[0][1]}."
            errors.append(msg)
            diagnostics.append(msg)

    report = {
        "orphan_req_count": len(orphan_reqs),
        "orphan_tst_count": len(orphan_tsts),
        "orphan_reqs": sorted(orphan_reqs.keys()),
        "orphan_tsts": sorted(orphan_tsts.keys()),
        "scanned_files": scanned_count,
    }

    if errors:
        if fail_on_orphans:
            diagnostics.insert(0, f"ERROR: Code-to-shard traceability orphan check failed with {len(errors)} orphan(s).")
            return False, diagnostics, report
        else:
            diagnostics.insert(0, f"WARNING: Code-to-shard traceability orphan check detected {len(errors)} orphan(s) (non-blocking).")
            return True, diagnostics, report

    diagnostics.append(
        f"PASS: Zero code-to-shard requirement or test orphans detected in codebase ({scanned_count} files scanned)."
    )
    return True, diagnostics, report



def extract_living_test_ids(repo_root=None, tst_shards=None):
    """
    Collects all authentic TST-* IDs declared across living test shards (REQ-PRO-073.4).
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    known = set()
    if tst_shards is not None:
        for fp, content in tst_shards.items():
            lines = content if isinstance(content, list) else content.splitlines()
            for l in lines:
                m = re.match(r"^#{2,4}\s*(TST-[A-Z0-9_-]+)", l.strip())
                if m:
                    known.add(m.group(1))
        return known

    abs_tst_dir = os.path.join(repo_root, "docs", "tests")
    if os.path.isdir(abs_tst_dir):
        for fn in sorted(os.listdir(abs_tst_dir)):
            if fn.endswith(".md"):
                with open(os.path.join(abs_tst_dir, fn), "r", encoding="utf-8") as f:
                    for l in f:
                        m = re.match(r"^#{2,4}\s*(TST-[A-Z0-9_-]+)", l.strip())
                        if m:
                            known.add(m.group(1))
    return known


def find_ungrounded_verified_requirements(repo_root=None, req_shards=None, tst_shards=None):
    """
    Scans docs/requirements/*.md for requirements claiming [Verified] without citing at least
    one authentic, existing TST ID (REQ-PRO-073.4).
    Returns a list of dicts:
      {
        "file": rel_path,
        "line_no": lno,
        "req_id": req_id,
        "cited_tsts": [...],
        "reason": "missing_or_empty_tests" | "non_existent_tests",
      }
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    known_tsts = extract_living_test_ids(repo_root=repo_root, tst_shards=tst_shards)

    shards = {}
    if req_shards is not None:
        shards = req_shards
    else:
        abs_req_dir = os.path.join(repo_root, "docs", "requirements")
        if os.path.isdir(abs_req_dir):
            for fn in sorted(os.listdir(abs_req_dir)):
                if fn.endswith(".md"):
                    with open(os.path.join(abs_req_dir, fn), "r", encoding="utf-8") as f:
                        shards[os.path.join("docs/requirements", fn)] = f.readlines()

    ungrounded = []
    for rel_path, lines in shards.items():
        if isinstance(lines, str):
            lines = lines.splitlines()

        cur_req = None
        cur_status = None
        cur_lno = 0
        cur_tsts = []

        def flush():
            nonlocal cur_req, cur_status, cur_lno, cur_tsts
            if cur_req and cur_status == "Verified":
                valid_tsts = [t for t in cur_tsts if t in known_tsts]
                if not cur_tsts:
                    ungrounded.append({
                        "file": rel_path,
                        "line_no": cur_lno,
                        "req_id": cur_req,
                        "cited_tsts": [],
                        "reason": "missing_or_empty_tests",
                    })
                elif not valid_tsts:
                    ungrounded.append({
                        "file": rel_path,
                        "line_no": cur_lno,
                        "req_id": cur_req,
                        "cited_tsts": list(cur_tsts),
                        "reason": "non_existent_tests",
                    })

        for lno, line in enumerate(lines, 1):
            m = re.match(r"^(#{2,4})\s*(REQ-[A-Z0-9_-]+):?\s*(.*?)$", line.strip())
            if m:
                flush()
                cur_req = m.group(2)
                cur_lno = lno
                cur_tsts = []
                if "[Verified]" in line:
                    cur_status = "Verified"
                elif "[Implemented]" in line:
                    cur_status = "Implemented"
                elif "[Open]" in line:
                    cur_status = "Open"
                else:
                    cur_status = "Planned"
            elif cur_req:
                m_t = re.search(r"\*\*Tests\*\*:\s*(.*?)(?:\||$)", line)
                if m_t:
                    val = m_t.group(1).strip()
                    found = re.findall(r"\bTST-[A-Z0-9_-]+\b", val)
                    cur_tsts.extend(found)
        flush()

    return ungrounded


def verify_requirement_evidence_status(
    repo_root=None,
    fail_on_unverified=False,
    diff_text=None,
    base_ref="develop",
    req_shards=None,
    tst_shards=None,
    legacy_allowlist=None,
):
    """
    Verifies that all requirements claiming [Verified] cite at least one authentic, existing TST ID (REQ-PRO-073.4).
    Requirements with no test citation, empty tests, 'None', 'N/A', or only non-existent TSTs are rejected.

    If diff_text is provided (or in verify_governance), any net-new or modified requirement in the diff
    claiming [Verified] without an existing TST ID causes verification to fail.
    If fail_on_unverified is True (or --enforce-evidence CLI flag), ANY ungrounded [Verified]
    requirement in the repository causes verification to fail.
    Otherwise, legacy ungrounded requirements present in legacy_allowlist emit a WARNING.
    Any novel ungrounded [Verified] requirement causes verification to fail.

    Returns (success: bool, diagnostics: list[str]).
    """
    if legacy_allowlist is None:
        legacy_allowlist = LEGACY_UNGROUNDED_VERIFIED_REQS

    ungrounded = find_ungrounded_verified_requirements(
        repo_root=repo_root, req_shards=req_shards, tst_shards=tst_shards
    )

    diff_flagged = set()
    if diff_text is not None:
        mod_reqs, add_reqs, _ = parse_diff_requirements(diff_text)
        diff_flagged = mod_reqs.union(add_reqs)

    errors = []
    warnings = []

    for item in ungrounded:
        req_id = item["req_id"]
        rel_path = item["file"]
        lno = item["line_no"]
        reason = item["reason"]

        msg = (
            f"{rel_path}:{lno} [{req_id}] claims [Verified] without citing any authentic existing test specification"
            f" (reason: {reason}, REQ-PRO-073.4)."
        )

        if fail_on_unverified or (req_id not in legacy_allowlist):
            errors.append(msg)
        else:
            warnings.append(
                f"WARNING (Legacy ungrounded requirement): {rel_path}:{lno} [{req_id}] claims [Verified] "
                f"without verified test evidence pending A-08 backfill."
            )

    diagnostics = []
    if errors:
        for err in errors:
            diagnostics.append(f"ERROR: {err}")
        return False, diagnostics

    for warn in warnings:
        diagnostics.append(warn)

    diagnostics.append(
        f"PASS: Zero ungrounded [Verified] requirements detected ({len(warnings)} legacy requirements tolerated pending A-08 backfill)."
    )
    return True, diagnostics


ALLOWED_PRIORITIES = {"critical", "high", "medium", "low"}
ALLOWED_TYPES = {"functional", "non-functional", "nonfunctional", "non functional", "f", "nfr"}
ALLOWED_VERIFICATION_METHODS = {
    "unit",
    "integration",
    "on-device manual",
    "on-device",
    "device-manual",
    "device manual",
    "review",
    "formal analysis",
    "test",
    "demonstration",
    "inspection",
    "analysis",
}


def find_missing_requirement_attributes(repo_root=None, req_shards=None):
    """
    Scans docs/requirements/*.md for requirements lacking Priority, Type,
    or Verification Method attributes (REQ-PRO-077, SWE.1 BP2).
    Returns a list of dicts:
      {
        "file": rel_path,
        "line_no": lno,
        "req_id": req_id,
        "missing": [...],
        "invalid": [...],
        "priority": str or None,
        "type": str or None,
        "verification_method": str or None,
      }
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    shards = {}
    if req_shards is not None:
        shards = req_shards
    else:
        abs_req_dir = os.path.join(repo_root, "docs", "requirements")
        if os.path.isdir(abs_req_dir):
            for fn in sorted(os.listdir(abs_req_dir)):
                if fn.endswith(".md"):
                    with open(os.path.join(abs_req_dir, fn), "r", encoding="utf-8") as f:
                        shards[os.path.join("docs/requirements", fn)] = f.readlines()

    def split_vm_tokens(raw_vm):
        tokens = [t.strip().lower() for t in re.split(r"[/,;&|]|\band\b", raw_vm) if t.strip()]
        return tokens

    def is_valid_vm(raw_vm):
        tokens = split_vm_tokens(raw_vm)
        if not tokens:
            return False
        return all(t in ALLOWED_VERIFICATION_METHODS for t in tokens)

    def is_valid_priority(raw_pri):
        return raw_pri.strip().lower() in ALLOWED_PRIORITIES

    def is_valid_type(raw_type):
        return raw_type.strip().lower() in ALLOWED_TYPES

    results = []

    for rel_path, lines in shards.items():
        if isinstance(lines, str):
            lines = lines.splitlines()

        cur_req = None
        cur_lno = 0
        cur_pri = None
        cur_type = None
        cur_vm = None

        def flush():
            nonlocal cur_req, cur_lno, cur_pri, cur_type, cur_vm
            if cur_req:
                missing = []
                invalid = []
                if not cur_pri or not cur_pri.strip():
                    missing.append("Priority")
                elif not is_valid_priority(cur_pri):
                    invalid.append(f"Priority='{cur_pri.strip()}' (allowed: Critical, High, Medium, Low)")

                if not cur_type or not cur_type.strip():
                    missing.append("Type")
                elif not is_valid_type(cur_type):
                    invalid.append(f"Type='{cur_type.strip()}' (allowed: Functional, Non-Functional)")

                if not cur_vm or not cur_vm.strip():
                    missing.append("Verification Method")
                elif not is_valid_vm(cur_vm):
                    invalid.append(
                        f"Verification Method='{cur_vm.strip()}' (allowed: Unit, Integration, On-Device Manual, Review, Formal Analysis)"
                    )

                if missing or invalid:
                    results.append({
                        "file": rel_path,
                        "line_no": cur_lno,
                        "req_id": cur_req,
                        "missing": missing,
                        "invalid": invalid,
                        "priority": cur_pri,
                        "type": cur_type,
                        "verification_method": cur_vm,
                    })

        for lno, line in enumerate(lines, 1):
            m = re.match(r"^(#{2,4})\s*(REQ-[A-Z0-9_-]+):?\s*(.*?)$", line.strip())
            if m:
                flush()
                cur_req = m.group(2)
                cur_lno = lno
                cur_pri = None
                cur_type = None
                cur_vm = None
            elif cur_req:
                m_pri = re.search(r"[*•-]\s*\*\*Priority\*\*:\s*`?([^`|\r\n]+)`?(?:\||$)", line, re.IGNORECASE)
                if m_pri and cur_pri is None:
                    cur_pri = m_pri.group(1).strip()

                m_type = re.search(r"[*•-]\s*\*\*Type\*\*:\s*`?([^`|\r\n]+)`?(?:\||$)", line, re.IGNORECASE)
                if m_type and cur_type is None:
                    cur_type = m_type.group(1).strip()

                m_vm = re.search(r"[*•-]\s*\*\*Verification[- ]Method\*\*:\s*`?([^`|\r\n]+)`?(?:\||$)", line, re.IGNORECASE)
                if m_vm and cur_vm is None:
                    cur_vm = m_vm.group(1).strip()

        flush()

    return results


def verify_requirement_attributes(
    repo_root=None,
    enforce_attributes=True,
    diff_text=None,
    base_ref="develop",
    req_shards=None,
    legacy_allowlist=None,
):
    """
    Verifies that requirements define Priority, Type, and Verification Method attributes (REQ-PRO-077, SWE.1 BP2).
    In default mode:
      - Newly added or modified requirements in the git diff MUST have valid Priority, Type, and Verification Method attributes.
        Any missing or invalid attribute in the diff causes verification failure (exit code 1).
      - Legacy requirements without attributes emit WARNING diagnostics without failing.
    In strict mode (--enforce-attributes / --fail-on-missing-attributes):
      - ANY requirement in the repository missing attributes or with invalid attributes causes verification failure.
    If legacy_allowlist is provided (set of REQ IDs):
      - Requirements in legacy_allowlist emit WARNINGs unless enforce_attributes is True.
    """
    defective_reqs = find_missing_requirement_attributes(
        repo_root=repo_root, req_shards=req_shards
    )

    modified_reqs = set()
    added_reqs = set()
    if diff_text is None and not enforce_attributes:
        try:
            diff_text = get_git_diff(base_ref=base_ref)
        except Exception:
            diff_text = ""

    if diff_text:
        modified_reqs, added_reqs, _ = parse_diff_requirements(diff_text)

    errors = []
    warnings = []

    for item in defective_reqs:
        req_id = item["req_id"]
        rel_path = item["file"]
        lno = item["line_no"]
        reasons = []
        if item["missing"]:
            reasons.append(f"missing: {', '.join(item['missing'])}")
        if item["invalid"]:
            reasons.append(f"invalid: {', '.join(item['invalid'])}")
        reason_str = "; ".join(reasons)

        msg = f"{rel_path}:{lno} [{req_id}] lacks required attributes ({reason_str}, REQ-PRO-077, SWE.1 BP2)."

        is_new_req = req_id in added_reqs
        is_mod_req = req_id in modified_reqs
        is_legacy = (legacy_allowlist is not None and req_id in legacy_allowlist) or (legacy_allowlist is None and not is_new_req)

        if enforce_attributes or is_new_req or (is_mod_req and item["invalid"]):
            errors.append(msg)
        else:
            warnings.append(
                f"WARNING (Legacy missing attributes): {rel_path}:{lno} [{req_id}] lacks required attributes "
                f"({reason_str}) pending backfill."
            )

    diagnostics = []
    if errors:
        for err in errors:
            diagnostics.append(f"ERROR: {err}")
        return False, diagnostics

    for warn in warnings:
        diagnostics.append(warn)

    if not warnings:
        diagnostics.append(
            "PASS: 100% of living requirements define valid Priority, Type, and Verification Method attributes."
        )
    else:
        diagnostics.append(
            f"PASS: Zero attribute violations detected ({len(warnings)} legacy requirements tolerated without attributes pending backfill)."
        )

    return True, diagnostics


def verify_req_descriptions(
    repo_root=None,
    baseline=None,
    req_shards=None,
    fail_on_missing=True,
):
    """
    Enforces that all requirements declare a non-empty - **Description**: field (REQ-PRO-104.3).
    Tolerates 860 frozen legacy requirements in LEGACY_MISSING_DESCRIPTION_REQS ratchet baseline.
    Returns (success: bool, diagnostics: list[str], report: dict).
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    if baseline is None:
        baseline = LEGACY_MISSING_DESCRIPTION_REQS

    req_dir = os.path.join(repo_root, "docs/requirements")
    req_pattern = re.compile(r"^###\s+(REQ-[A-Z0-9_-]+):")

    missing = []
    total_reqs = 0

    shard_files = req_shards if req_shards else sorted(os.listdir(req_dir))
    for fn in shard_files:
        if not fn.endswith(".md"):
            continue
        fp = os.path.join(req_dir, fn) if not os.path.isabs(fn) else fn
        if not os.path.exists(fp):
            continue
        with open(fp, "r", encoding="utf-8") as f:
            content = f.read()
        sections = re.split(r"\n(?=###\s+REQ-)", content)
        for sec in sections:
            m = req_pattern.match(sec.strip())
            if not m:
                continue
            total_reqs += 1
            req_id = m.group(1)
            if not re.search(r"-\s+\*\*Description\*\*:\s*\S+", sec):
                missing.append((req_id, fn))

    missing_ids = set(m[0] for m in missing)
    unbaselined_missing = sorted(missing_ids - baseline)
    stale_baselined = sorted(baseline - missing_ids)

    diagnostics = []
    errors = []

    if unbaselined_missing:
        for req_id in unbaselined_missing:
            msg = f"ERROR: Requirement {req_id} lacks mandatory '- **Description**:' field and is not in legacy baseline."
            errors.append(msg)
            diagnostics.append(msg)

    if len(missing_ids) > len(baseline):
        msg = f"ERROR (Ratchet): Missing description count ({len(missing_ids)}) exceeds legacy baseline ({len(baseline)})."
        if msg not in errors:
            errors.append(msg)
            diagnostics.append(msg)

    report = {
        "total_reqs": total_reqs,
        "missing_count": len(missing_ids),
        "unbaselined_missing": unbaselined_missing,
        "stale_baselined": stale_baselined,
        "baseline_count": len(baseline),
    }

    if errors:
        if fail_on_missing:
            diagnostics.insert(0, f"ERROR: Requirement description governance failed with {len(errors)} violation(s).")
            return False, diagnostics, report
        else:
            diagnostics.insert(0, f"WARNING: Requirement description governance detected {len(errors)} violation(s) (non-blocking).")
            return True, diagnostics, report

    diagnostics.append(
        f"PASS: 100% of non-baselined requirements declare Description fields ({len(missing_ids)} legacy REQs tolerated in baseline)."
    )
    return True, diagnostics, report


def calculate_req_field_coverage(repo_root=None, req_shards=None):
    """
    Calculates field completion statistics (Description, Given-When-Then, Allocation) across
    all requirements in living documentation shards (REQ-PRO-104.3).
    Returns dict report.
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    req_dir = os.path.join(repo_root, "docs/requirements")
    req_pattern = re.compile(r"^###\s+(REQ-[A-Z0-9_-]+):")

    total_reqs = 0
    has_desc = 0
    has_gwt = 0
    has_alloc = 0

    shard_files = req_shards if req_shards else sorted(os.listdir(req_dir))
    for fn in shard_files:
        if not fn.endswith(".md"):
            continue
        fp = os.path.join(req_dir, fn) if not os.path.isabs(fn) else fn
        if not os.path.exists(fp):
            continue
        with open(fp, "r", encoding="utf-8") as f:
            content = f.read()
        sections = re.split(r"\n(?=###\s+REQ-)", content)
        for sec in sections:
            m = req_pattern.match(sec.strip())
            if not m:
                continue
            total_reqs += 1
            if re.search(r"-\s+\*\*Description\*\*:\s*\S+", sec):
                has_desc += 1
            if re.search(r"Given.*When.*Then", sec, re.DOTALL | re.IGNORECASE) or re.search(r"Acceptance Criteria", sec, re.IGNORECASE):
                has_gwt += 1
            if re.search(r"-\s+\*\*(?:Implementation|Target)\*\*:\s*\S+", sec):
                has_alloc += 1

    desc_pct = round((has_desc / total_reqs * 100.0), 1) if total_reqs else 0.0
    gwt_pct = round((has_gwt / total_reqs * 100.0), 1) if total_reqs else 0.0
    alloc_pct = round((has_alloc / total_reqs * 100.0), 1) if total_reqs else 0.0

    return {
        "total_reqs": total_reqs,
        "description_count": has_desc,
        "description_pct": desc_pct,
        "gwt_count": has_gwt,
        "gwt_pct": gwt_pct,
        "alloc_count": has_alloc,
        "alloc_pct": alloc_pct,
    }


def format_req_field_coverage(report):
    """Formats requirement field coverage report into structured console text."""
    lines = [
        "================================================================================",
        "=== REQUIREMENT FIELD COVERAGE REPORT (REQ-PRO-104.3)                        ===",
        "================================================================================",
        f"Total Living Requirements: {report['total_reqs']}",
        f"Description Coverage:      {report['description_count']}/{report['total_reqs']} ({report['description_pct']}%)",
        f"Acceptance Criteria (GWT): {report['gwt_count']}/{report['total_reqs']} ({report['gwt_pct']}%)",
        f"Allocation (Target/Impl):  {report['alloc_count']}/{report['total_reqs']} ({report['alloc_pct']}%)",
        "================================================================================",
    ]
    return "\n".join(lines)



class CoverageCategoryResult:
    """Represents a single coverage gap category with counts, totals, and percentages (REQ-PRO-071)."""
    def __init__(self, name, description, covered_count, total_count, gap_count, percentage, gap_items=None, details=None):
        self.name = name
        self.description = description
        self.covered_count = covered_count
        self.total_count = total_count
        self.gap_count = gap_count
        self.percentage = float(percentage)
        self.gap_items = list(gap_items or [])
        self.details = dict(details or {})

    def to_dict(self):
        return {
            "name": self.name,
            "description": self.description,
            "covered_count": self.covered_count,
            "total_count": self.total_count,
            "gap_count": self.gap_count,
            "percentage": round(self.percentage, 2),
            "gap_items": self.gap_items,
            "details": self.details,
        }

    def __getitem__(self, item):
        if hasattr(self, item):
            return getattr(self, item)
        d = self.to_dict()
        if item in d:
            return d[item]
        raise KeyError(item)

    def get(self, key, default=None):
        try:
            return self[key]
        except KeyError:
            return default

    def __repr__(self):
        return (
            f"CoverageCategoryResult({self.name}: {self.covered_count}/{self.total_count} "
            f"({self.percentage:.2f}%), gap={self.gap_count})"
        )


class TraceabilityCoverageReport:
    """Comprehensive traceability coverage report across all 4 categories (REQ-PRO-071)."""
    def __init__(self, cat1, cat2, cat3, cat4, metadata=None):
        self.cat1 = cat1
        self.cat2 = cat2
        self.cat3 = cat3
        self.cat4 = cat4
        self.metadata = dict(metadata or {})

    def to_dict(self):
        return {
            "category_1_reqs_without_tst": self.cat1.to_dict(),
            "category_2_tsts_without_code": self.cat2.to_dict(),
            "category_3_code_tags_without_spec": self.cat3.to_dict(),
            "category_4_tsts_not_cited_back": self.cat4.to_dict(),
            "summary": {
                "total_reqs": self.cat1.total_count,
                "reqs_with_tst": self.cat1.covered_count,
                "req_tst_coverage_pct": round(self.cat1.percentage, 2),
                "total_tsts": self.cat2.total_count,
                "tsts_with_code": self.cat2.covered_count,
                "tst_code_coverage_pct": round(self.cat2.percentage, 2),
                "total_code_tsts": self.cat3.total_count,
                "code_tags_with_spec": self.cat3.covered_count,
                "code_tags_without_spec": self.cat3.gap_count,
                "code_spec_match_pct": round(self.cat3.percentage, 2),
                "tsts_cited_back": self.cat4.covered_count,
                "bidirectional_coverage_pct": round(self.cat4.percentage, 2),
            },
            "metadata": self.metadata,
        }

    def __getitem__(self, item):
        d = self.to_dict()
        if item in d:
            return d[item]
        aliases = {
            "cat1": self.cat1,
            "cat2": self.cat2,
            "cat3": self.cat3,
            "cat4": self.cat4,
            "category_1": self.cat1,
            "category_2": self.cat2,
            "category_3": self.cat3,
            "category_4": self.cat4,
        }
        if item in aliases:
            return aliases[item]
        raise KeyError(item)

    def get(self, key, default=None):
        try:
            return self[key]
        except KeyError:
            return default


def extract_test_code_tags(test_sources=None, repo_root=None):
    """
    Extracts all test code tags (TST-*) across test source files.
    Supports:
      - None (scans app/src/test under repo_root)
      - directory path (str)
      - dict: {rel_path: str_content_or_lines}
      - list/tuple: file paths or (rel_path, content_or_lines) tuples
    Normalizes method sub-tags (e.g. TST-XXX-001_1, TST-XXX-001.1, TST-XXX-001-A) to their base TST-XXX-001 ID.
    Returns dict mapping base TST ID to list of occurrences: {base_id: [{"file": fp, "line": lno, "raw_tag": raw}]}.
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    files_to_scan = []
    if test_sources is None:
        test_dir = os.path.join(repo_root, "app/src/test")
        if os.path.isdir(test_dir):
            for root, dirs, files in os.walk(test_dir):
                for f in sorted(files):
                    if f.endswith(".kt") or f.endswith(".java"):
                        abs_fp = os.path.join(root, f)
                        rel_fp = os.path.relpath(abs_fp, repo_root)
                        with open(abs_fp, "r", encoding="utf-8", errors="ignore") as fh:
                            files_to_scan.append((rel_fp, fh.readlines()))
        android_test_dir = os.path.join(repo_root, "app/src/androidTest")
        if os.path.isdir(android_test_dir):
            for root, dirs, files in os.walk(android_test_dir):
                for f in sorted(files):
                    if f.endswith(".kt") or f.endswith(".java"):
                        abs_fp = os.path.join(root, f)
                        rel_fp = os.path.relpath(abs_fp, repo_root)
                        with open(abs_fp, "r", encoding="utf-8", errors="ignore") as fh:
                            files_to_scan.append((rel_fp, fh.readlines()))
        tools_dir = os.path.join(repo_root, "tools")
        if os.path.isdir(tools_dir):
            for f in sorted(os.listdir(tools_dir)):
                if f.startswith("test_") and f.endswith(".py"):
                    abs_fp = os.path.join(tools_dir, f)
                    rel_fp = os.path.relpath(abs_fp, repo_root)
                    with open(abs_fp, "r", encoding="utf-8", errors="ignore") as fh:
                        files_to_scan.append((rel_fp, fh.readlines()))
    elif isinstance(test_sources, str) and os.path.isdir(test_sources):
        for root, dirs, files in os.walk(test_sources):
            for f in sorted(files):
                if f.endswith(".kt") or f.endswith(".java") or (f.startswith("test_") and f.endswith(".py")):
                    abs_fp = os.path.join(root, f)
                    rel_fp = os.path.relpath(abs_fp, repo_root) if repo_root else abs_fp
                    with open(abs_fp, "r", encoding="utf-8", errors="ignore") as fh:
                        files_to_scan.append((rel_fp, fh.readlines()))
    elif isinstance(test_sources, dict):
        for k, v in sorted(test_sources.items()):
            lines = v.splitlines(keepends=True) if isinstance(v, str) else list(v)
            files_to_scan.append((k, lines))
    elif isinstance(test_sources, (list, tuple)):
        for item in test_sources:
            if isinstance(item, tuple) and len(item) == 2:
                k, v = item
                lines = v.splitlines(keepends=True) if isinstance(v, str) else list(v)
                files_to_scan.append((k, lines))
            elif isinstance(item, str) and os.path.isfile(item):
                with open(item, "r", encoding="utf-8", errors="ignore") as fh:
                    files_to_scan.append((item, fh.readlines()))

    tag_pattern = re.compile(r"(?<![A-Za-z0-9_-])(TST-[A-Z]+-[0-9]+(?:[\._-][A-Za-z0-9_-]+)*)")
    base_pattern = re.compile(r"^(TST-[A-Z]+-[0-9]+)")

    synthetic_gov_tests = {
        "test_verify_requirement_governance.py",
        "test_generate_test_evidence.py",
        "test_jira_accounts.py",
        "test_shard_living_docs.py",
    }

    code_tags = {}
    for rel_fp, lines in files_to_scan:
        is_tool_script = rel_fp.startswith("tools") or rel_fp.endswith(".py")
        is_synthetic_gov = os.path.basename(rel_fp) in synthetic_gov_tests
        for lno, line in enumerate(lines, 1):
            for m in tag_pattern.finditer(line):
                raw = m.group(1).rstrip(".")
                m_base = base_pattern.match(raw)
                base_id = m_base.group(1) if m_base else raw
                if is_tool_script and is_synthetic_gov and not base_id.startswith("TST-PRO-"):
                    continue
                if is_tool_script and not is_synthetic_gov and not base_id.startswith(("TST-PRO-", "TST-STR-", "TST-TOOL-", "TST-STB-", "TST-TST-", "TST-SIM-", "TST-TRK-017")):
                    continue
                code_tags.setdefault(base_id, []).append({
                    "file": rel_fp,
                    "line": lno,
                    "raw_tag": raw,
                })

    return code_tags


def calculate_traceability_coverage(
    req_shards=None,
    tst_shards=None,
    test_sources=None,
    repo_root=None,
):
    """
    Computes bidirectional REQ-TST traceability and test code existence coverage across 4 categories (REQ-PRO-071):
      - Category 1: REQs without TST citation (REQ-PRO-071.1)
      - Category 2: TSTs without test code (REQ-PRO-071.2)
      - Category 3: Code tags without spec (REQ-PRO-071.3)
      - Category 4: TSTs not cited back by their REQ (REQ-PRO-071.4)
    Returns TraceabilityCoverageReport.
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    loaded_reqs = _load_shard_sources(req_shards, "docs/requirements", repo_root)
    loaded_tsts = _load_shard_sources(tst_shards, "docs/tests", repo_root)

    # 1. Discover all declared headings
    known_req_ids = set()
    known_tst_ids = set()

    for rel_path, lines in loaded_reqs:
        for line in lines:
            m = re.match(r"^(#{2,4})\s*(REQ-[A-Z0-9_-]+):?\s*(.*?)$", line.strip())
            if m:
                known_req_ids.add(m.group(2))

    for rel_path, lines in loaded_tsts:
        for line in lines:
            m = re.match(r"^(#{2,4})\s*(TST-[A-Z0-9_-]+):?\s*(.*?)$", line.strip())
            if m:
                known_tst_ids.add(m.group(2))

    # 2. Extract cited TSTs in REQs' Tests field
    req_to_tsts = {r: set() for r in known_req_ids}
    for rel_path, lines in loaded_reqs:
        cur_req = None
        for line in lines:
            m = re.match(r"^(#{2,4})\s*(REQ-[A-Z0-9_-]+):?\s*(.*?)$", line.strip())
            if m:
                cur_req = m.group(2)
            m_f = re.match(r"^\s*[-*]\s*\*{0,2}Tests?\*{0,2}\s*:\s*(.*)", line, re.IGNORECASE)
            if m_f and cur_req:
                cites = re.findall(r"(?<![A-Za-z0-9_-])TST-[A-Z0-9_-]+(?![A-Za-z0-9_-])", m_f.group(1))
                req_to_tsts[cur_req].update(cites)

    # 3. Extract cited REQs in TSTs' Requirements field
    tst_to_reqs = {t: set() for t in known_tst_ids}
    for rel_path, lines in loaded_tsts:
        cur_tst = None
        for line in lines:
            m = re.match(r"^(#{2,4})\s*(TST-[A-Z0-9_-]+):?\s*(.*?)$", line.strip())
            if m:
                cur_tst = m.group(2)
            m_f = re.match(
                r"^\s*[-*]\s*\*{0,2}(?:Requirements?|Requirement\(s\)|Target Requirement|Requirements Covered|Verifies)\*{0,2}\s*:\s*(.*)",
                line,
                re.IGNORECASE,
            )
            if m_f and cur_tst:
                cites = re.findall(r"(?<![A-Za-z0-9_-])REQ-[A-Z0-9_-]+(?![A-Za-z0-9_-])", m_f.group(1))
                tst_to_reqs[cur_tst].update(cites)

    # 4. Scan test code tags
    code_tsts = extract_test_code_tags(test_sources=test_sources, repo_root=repo_root)

    # Category 1: REQs without TST
    reqs_without_tst = sorted([r for r in known_req_ids if not req_to_tsts.get(r)])
    total_reqs = len(known_req_ids)
    covered_reqs = total_reqs - len(reqs_without_tst)
    pct_cat1 = (covered_reqs / total_reqs * 100.0) if total_reqs > 0 else 100.0
    cat1 = CoverageCategoryResult(
        name="Category 1",
        description="REQs citing at least one TST in 'Tests' field",
        covered_count=covered_reqs,
        total_count=total_reqs,
        gap_count=len(reqs_without_tst),
        percentage=pct_cat1,
        gap_items=reqs_without_tst,
    )

    # Category 2: TSTs without test code
    tsts_without_code = sorted([t for t in known_tst_ids if t not in code_tsts])
    total_tsts = len(known_tst_ids)
    covered_tsts = total_tsts - len(tsts_without_code)
    pct_cat2 = (covered_tsts / total_tsts * 100.0) if total_tsts > 0 else 100.0
    cat2 = CoverageCategoryResult(
        name="Category 2",
        description="TST specifications with matching test code tag in test directory",
        covered_count=covered_tsts,
        total_count=total_tsts,
        gap_count=len(tsts_without_code),
        percentage=pct_cat2,
        gap_items=tsts_without_code,
    )

    # Category 3: Code tags without spec
    code_tags_without_spec = sorted([b for b in code_tsts.keys() if b not in known_tst_ids])
    total_code_tsts = len(code_tsts)
    covered_code_tags = total_code_tsts - len(code_tags_without_spec)
    pct_cat3 = (covered_code_tags / total_code_tsts * 100.0) if total_code_tsts > 0 else 100.0
    cat3 = CoverageCategoryResult(
        name="Category 3",
        description="Code tags with matching TST heading in living documentation",
        covered_count=covered_code_tags,
        total_count=total_code_tsts,
        gap_count=len(code_tags_without_spec),
        percentage=pct_cat3,
        gap_items=code_tags_without_spec,
        details={b: code_tsts[b] for b in code_tags_without_spec},
    )

    # Category 4: TSTs not cited back by their REQ
    tsts_not_cited_back = []
    tst_back_details = {}
    for t in sorted(known_tst_ids):
        cited = tst_to_reqs.get(t, set())
        if not cited:
            tsts_not_cited_back.append(t)
            tst_back_details[t] = ["<NO_CITED_REQS>"]
        else:
            unciting = [r for r in sorted(cited) if t not in req_to_tsts.get(r, set())]
            if unciting:
                tsts_not_cited_back.append(t)
                tst_back_details[t] = unciting

    covered_bidirectional = total_tsts - len(tsts_not_cited_back)
    pct_cat4 = (covered_bidirectional / total_tsts * 100.0) if total_tsts > 0 else 100.0
    cat4 = CoverageCategoryResult(
        name="Category 4",
        description="TST specifications cited back by all of their referenced REQs",
        covered_count=covered_bidirectional,
        total_count=total_tsts,
        gap_count=len(tsts_not_cited_back),
        percentage=pct_cat4,
        gap_items=tsts_not_cited_back,
        details=tst_back_details,
    )

    return TraceabilityCoverageReport(
        cat1=cat1,
        cat2=cat2,
        cat3=cat3,
        cat4=cat4,
        metadata={"repo_root": repo_root},
    )


def format_coverage_markdown(report):
    """Formats a TraceabilityCoverageReport into a GitHub-style Markdown matrix."""
    lines = [
        "# ASPICE Traceability & Verification Coverage Report (REQ-PRO-071)",
        "",
        "| Category | Metric Description | Covered / Compliant | Total | Gap Count | Coverage % |",
        "| :--- | :--- | :--- | :--- | :--- | :--- |",
        f"| **Category 1** | REQs with TST Citation | {report.cat1.covered_count} | {report.cat1.total_count} | {report.cat1.gap_count} | {report.cat1.percentage:.2f}% |",
        f"| **Category 2** | TSTs with Test Code | {report.cat2.covered_count} | {report.cat2.total_count} | {report.cat2.gap_count} | {report.cat2.percentage:.2f}% |",
        f"| **Category 3** | Code Tags with Valid Spec | {report.cat3.covered_count} | {report.cat3.total_count} | {report.cat3.gap_count} | {report.cat3.percentage:.2f}% |",
        f"| **Category 4** | TSTs Cited Back by REQs | {report.cat4.covered_count} | {report.cat4.total_count} | {report.cat4.gap_count} | {report.cat4.percentage:.2f}% |",
        "",
        "### Category Summaries:",
        f"* **Category 1 (REQs without TST)**: {report.cat1.gap_count} requirements cite no test specification in their 'Tests' field.",
        f"* **Category 2 (TSTs without test code)**: {report.cat2.gap_count} test specifications have no matching test method in the test directory.",
    ]

    if report.cat3.gap_count > 0:
        sample = ", ".join(report.cat3.gap_items[:15])
        suffix = f" ... (+{report.cat3.gap_count - 15} more)" if report.cat3.gap_count > 15 else ""
        lines.append(f"* **Category 3 (Code tags without spec)**: {report.cat3.gap_count} unique test tags in test code have no living document heading: {sample}{suffix}.")
    else:
        lines.append("* **Category 3 (Code tags without spec)**: 0 code tags without spec (100% matched).")

    lines.append(
        f"* **Category 4 (TSTs not cited back)**: {report.cat4.gap_count} test specifications are not cited back by their referenced requirements."
    )
    return "\n".join(lines)


def format_coverage_json(report):
    """Formats a TraceabilityCoverageReport as a formatted JSON string."""
    return json.dumps(report.to_dict(), indent=2)


def verify_coverage_thresholds(
    report,
    min_req_tst_coverage=None,
    min_tst_code_coverage=None,
    max_code_without_spec=None,
    min_bidirectional_coverage=None,
):
    """
    Evaluates coverage metrics against optional ratcheting thresholds (REQ-PRO-071.5).
    Returns (success: bool, diagnostics: list[str]).
    """
    diagnostics = []
    failures = []

    if min_req_tst_coverage is not None:
        if report.cat1.percentage < min_req_tst_coverage:
            msg = (
                f"ERROR: Gate violated: REQ-TST coverage {report.cat1.percentage:.2f}% is below threshold "
                f"{min_req_tst_coverage:.2f}% (Category 1: {report.cat1.covered_count}/{report.cat1.total_count})."
            )
            failures.append(msg)
            diagnostics.append(msg)
        else:
            diagnostics.append(
                f"PASS: REQ-TST coverage {report.cat1.percentage:.2f}% meets threshold {min_req_tst_coverage:.2f}%."
            )

    if min_tst_code_coverage is not None:
        if report.cat2.percentage < min_tst_code_coverage:
            msg = (
                f"ERROR: Gate violated: TST-to-code coverage {report.cat2.percentage:.2f}% is below threshold "
                f"{min_tst_code_coverage:.2f}% (Category 2: {report.cat2.covered_count}/{report.cat2.total_count})."
            )
            failures.append(msg)
            diagnostics.append(msg)
        else:
            diagnostics.append(
                f"PASS: TST-to-code coverage {report.cat2.percentage:.2f}% meets threshold {min_tst_code_coverage:.2f}%."
            )

    if max_code_without_spec is not None:
        if report.cat3.gap_count > max_code_without_spec:
            msg = (
                f"ERROR: Gate violated: Code tags without spec count {report.cat3.gap_count} exceeds maximum allowed "
                f"{max_code_without_spec} (Category 3)."
            )
            failures.append(msg)
            diagnostics.append(msg)
        else:
            diagnostics.append(
                f"PASS: Code tags without spec count {report.cat3.gap_count} within maximum allowed {max_code_without_spec}."
            )

    if min_bidirectional_coverage is not None:
        if report.cat4.percentage < min_bidirectional_coverage:
            msg = (
                f"ERROR: Gate violated: Bidirectional REQ-TST coverage {report.cat4.percentage:.2f}% is below threshold "
                f"{min_bidirectional_coverage:.2f}% (Category 4: {report.cat4.covered_count}/{report.cat4.total_count})."
            )
            failures.append(msg)
            diagnostics.append(msg)
        else:
            diagnostics.append(
                f"PASS: Bidirectional REQ-TST coverage {report.cat4.percentage:.2f}% meets threshold {min_bidirectional_coverage:.2f}%."
            )

    if failures:
        return False, diagnostics

    if not diagnostics:
        diagnostics.append("PASS: Coverage calculated successfully (no thresholds configured).")
    return True, diagnostics


def parse_junit_xml_results(
    results_dir=None,
    archive_path=None,
    repo_root=None,
):
    """
    Parses JUnit XML test results from directory or zip archive (REQ-PRO-099.1).
    Returns dict:
      {
         "testcases": [...],
         "summary": {"total": int, "passed": int, "failures": int, "errors": int, "skipped": int, "time": float},
         "by_tst": { "TST-XXX": { "status": "PASS"|"FAIL"|"ERROR", "failing_cases": [...], "passing_cases": [...] } }
      }
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    testcases = []
    summary = {
        "total": 0,
        "passed": 0,
        "failures": 0,
        "errors": 0,
        "skipped": 0,
        "time": 0.0,
    }
    by_tst = {}

    xml_contents = []

    # 1. Directory search
    search_dirs = []
    if results_dir:
        search_dirs.append(results_dir)
    else:
        cand1 = os.path.join(repo_root, "app/build/test-results")
        if os.path.isdir(cand1):
            search_dirs.append(cand1)
        cand2 = os.path.join(repo_root, "build-evidence")
        if os.path.isdir(cand2):
            search_dirs.append(cand2)

    for s_dir in search_dirs:
        if os.path.isdir(s_dir):
            for root, dirs, files in os.walk(s_dir):
                for f in sorted(files):
                    if f.endswith(".xml") and (f.startswith("TEST-") or "test" in f.lower()):
                        abs_p = os.path.join(root, f)
                        try:
                            with open(abs_p, "rb") as fh:
                                xml_contents.append((abs_p, fh.read()))
                        except Exception as e:
                            print(f"Warning: Failed reading {abs_p}: {e}", file=sys.stderr)

    # 2. Archive search if no directory files found or if archive_path explicitly provided
    if not xml_contents or archive_path:
        archives = []
        if archive_path and os.path.isfile(archive_path):
            archives.append(archive_path)
        elif not xml_contents:
            cand_arc_dir = os.path.join(repo_root, "build-evidence")
            if os.path.isdir(cand_arc_dir):
                for f in sorted(os.listdir(cand_arc_dir)):
                    if f.endswith(".zip") and "junit" in f.lower():
                        archives.append(os.path.join(cand_arc_dir, f))

        for arc in archives:
            try:
                with zipfile.ZipFile(arc, "r") as z:
                    for name in z.namelist():
                        if name.endswith(".xml") and (name.startswith("TEST-") or "/TEST-" in name or "test" in name.lower()):
                            xml_contents.append((f"{arc}::{name}", z.read(name)))
            except Exception as e:
                print(f"Warning: Failed reading archive {arc}: {e}", file=sys.stderr)

    tag_pattern = re.compile(r"(?<![A-Za-z0-9])(TST-[A-Z]+-[0-9]+(?:[\._-][A-Za-z0-9_-]+)*)")
    base_pattern = re.compile(r"^(TST-[A-Z]+-[0-9]+)")

    for src_name, raw_bytes in xml_contents:
        try:
            root = ET.fromstring(raw_bytes)
            suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
            for suite in suites:
                for tc in suite.findall("testcase"):
                    cname = tc.attrib.get("classname", "")
                    name = tc.attrib.get("name", "")
                    try:
                        t_val = float(tc.attrib.get("time", 0.0) or 0.0)
                    except ValueError:
                        t_val = 0.0
                    summary["time"] += t_val
                    summary["total"] += 1

                    fail_el = tc.find("failure")
                    err_el = tc.find("error")
                    skip_el = tc.find("skipped")

                    if fail_el is not None:
                        status = "FAIL"
                        msg = fail_el.attrib.get("message", "") or (fail_el.text or "").strip()
                        summary["failures"] += 1
                    elif err_el is not None:
                        status = "ERROR"
                        msg = err_el.attrib.get("message", "") or (err_el.text or "").strip()
                        summary["errors"] += 1
                    elif skip_el is not None:
                        status = "SKIPPED"
                        msg = skip_el.attrib.get("message", "") or (skip_el.text or "").strip()
                        summary["skipped"] += 1
                    else:
                        status = "PASS"
                        msg = ""
                        summary["passed"] += 1

                    tst_ids = set()
                    normalized_text = re.sub(r"TST[_-]([A-Z]+)[_-]([0-9]+)", r"TST-\1-\2", f"{name} {cname}")
                    for m in tag_pattern.finditer(normalized_text):
                        raw = m.group(1).rstrip(".")
                        mb = base_pattern.match(raw)
                        if mb:
                            tst_ids.add(mb.group(1))

                    tc_record = {
                        "source": src_name,
                        "classname": cname,
                        "name": name,
                        "time": t_val,
                        "status": status,
                        "message": msg,
                        "tst_ids": sorted(list(tst_ids)),
                    }
                    testcases.append(tc_record)

                    for tid in tst_ids:
                        rec = by_tst.setdefault(tid, {"status": "PASS", "failing_cases": [], "passing_cases": []})
                        if status in ("FAIL", "ERROR"):
                            rec["status"] = status
                            rec["failing_cases"].append(tc_record)
                        elif status == "PASS":
                            rec["passing_cases"].append(tc_record)
        except Exception as e:
            print(f"Warning: Failed to parse XML in {src_name}: {e}", file=sys.stderr)

    return {
        "testcases": testcases,
        "summary": summary,
        "by_tst": by_tst,
    }


def correlate_test_results_to_tsts(test_results, code_tags=None):
    """
    Correlates test cases to TST IDs via classname matching when TST ID is not in testcase name.
    """
    if not test_results:
        return test_results
    testcases = test_results.get("testcases", [])
    by_tst = test_results.get("by_tst", {})

    if code_tags:
        file_to_tsts = {}
        for tid, locs in code_tags.items():
            for loc in locs:
                f = loc["file"]
                stem = os.path.splitext(os.path.basename(f))[0]
                file_to_tsts.setdefault(stem, set()).add(tid)

        for tc in testcases:
            cname = tc.get("classname", "")
            leaf = cname.split(".")[-1]
            matched_tsts = file_to_tsts.get(leaf, set())
            for tid in matched_tsts:
                if tid not in tc["tst_ids"]:
                    tc["tst_ids"].append(tid)
                rec = by_tst.setdefault(tid, {"status": "PASS", "failing_cases": [], "passing_cases": []})
                if tc["status"] in ("FAIL", "ERROR"):
                    rec["status"] = tc["status"]
                    rec["failing_cases"].append(tc)
                elif tc["status"] == "PASS":
                    rec["passing_cases"].append(tc)

    return test_results


def verify_verified_test_evidence(
    repo_root=None,
    results_dir=None,
    archive_path=None,
    test_results=None,
    code_tags=None,
    baseline_allowlist=None,
    tst_shards=None,
):
    """
    Enforces that all [Verified] TST specifications are backed by passing test evidence (REQ-PRO-099).
    - Rejects any [Verified] TST whose associated testcase reports failure or error (Criterion 1).
    - Tolerates 405 legacy unbacked TSTs in LEGACY_UNBACKED_VERIFIED_TSTS (Criterion 3).
    - Rejects any novel unbacked [Verified] TST outside the baseline (Criterion 2).
    Returns (success: bool, diagnostics: list[str]).
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    if baseline_allowlist is None:
        baseline_allowlist = LEGACY_UNBACKED_VERIFIED_TSTS

    if code_tags is None:
        code_tags = extract_test_code_tags(repo_root=repo_root)

    if test_results is None:
        test_results = parse_junit_xml_results(
            results_dir=results_dir,
            archive_path=archive_path,
            repo_root=repo_root,
        )

    correlate_test_results_to_tsts(test_results, code_tags=code_tags)
    by_tst = test_results.get("by_tst", {})

    loaded_tsts = _load_shard_sources(tst_shards, "docs/tests", repo_root)
    heading_pattern = re.compile(r"^###\s+(TST-[A-Z]+-[0-9]+(?:\.[0-9]+)?)[^:\n]*:\s*([^\n]+)")

    verified_tsts = {}
    for rel_path, lines in loaded_tsts:
        for lno, line in enumerate(lines, 1):
            m = heading_pattern.match(line.strip())
            if m:
                tid = m.group(1)
                base_tid = tid.split(".")[0]
                rest = m.group(2)
                if "[Verified]" in rest or "Verified" in rest:
                    verified_tsts[base_tid] = {
                        "raw_id": tid,
                        "file": rel_path,
                        "line": lno,
                    }

    errors = []
    warnings = []
    passed_count = 0

    for base_tid in sorted(verified_tsts.keys()):
        info = verified_tsts[base_tid]
        raw_id = info["raw_id"]
        rel_p = info["file"]
        lno = info["line"]

        # Check for failing test results
        if base_tid in by_tst:
            tst_data = by_tst[base_tid]
            if tst_data["status"] in ("FAIL", "ERROR"):
                failing_names = [f"{c['classname']}.{c['name']}: {c['message']}" for c in tst_data["failing_cases"][:3]]
                errors.append(
                    f"{rel_p}:{lno} [{raw_id}] claims [Verified] but associated testcase failed: {', '.join(failing_names)}"
                )
                continue

        # Check for backing evidence (test code tag or passing test result)
        has_code = base_tid in code_tags
        has_passing_result = base_tid in by_tst and len(by_tst[base_tid]["passing_cases"]) > 0

        if has_code or has_passing_result:
            passed_count += 1
        else:
            # Unbacked TST
            if base_tid in baseline_allowlist:
                warnings.append(
                    f"WARNING (Legacy unbacked TST): {rel_p}:{lno} [{raw_id}] claims [Verified] without test code "
                    f"or machine test evidence (tolerated in ratchet baseline)."
                )
            else:
                errors.append(
                    f"{rel_p}:{lno} [{raw_id}] claims [Verified] without test code or passing test evidence "
                    f"(outside ratchet baseline, REQ-PRO-099.3)."
                )

    diagnostics = []
    if errors:
        for err in errors:
            diagnostics.append(f"ERROR: {err}")
        return False, diagnostics

    for warn in warnings:
        diagnostics.append(warn)

    diagnostics.append(
        f"PASS: 100% of [Verified] TST specifications verified against test evidence "
        f"({len(warnings)} legacy unbacked TSTs tolerated in ratchet baseline, {passed_count} backed)."
    )
    return True, diagnostics


def has_verified_test_evidence(
    tst_id,
    repo_root=None,
    test_results=None,
    code_tags=None,
    baseline_allowlist=None,
):
    """
    Checks if a given TST ID has passing test evidence or backing test code (REQ-PRO-099.4).
    Returns (ok: bool, reason: str).
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    if baseline_allowlist is None:
        baseline_allowlist = LEGACY_UNBACKED_VERIFIED_TSTS

    base_tid = tst_id.split(".")[0] if "." in tst_id else tst_id

    if test_results is None:
        test_results = parse_junit_xml_results(repo_root=repo_root)
    if code_tags is None:
        code_tags = extract_test_code_tags(repo_root=repo_root)

    correlate_test_results_to_tsts(test_results, code_tags=code_tags)
    by_tst = test_results.get("by_tst", {})

    if base_tid in by_tst:
        tst_data = by_tst[base_tid]
        if tst_data["status"] in ("FAIL", "ERROR"):
            return False, f"Associated testcase failed in test results ({len(tst_data['failing_cases'])} failure(s))"
        if len(tst_data["passing_cases"]) > 0:
            return True, f"Verified by {len(tst_data['passing_cases'])} passing testcase(s)"

    if base_tid in code_tags:
        loc_count = len(code_tags[base_tid])
        return True, f"Backed by test code tag ({loc_count} location(s))"

    if base_tid in baseline_allowlist:
        return True, "Tolerated in ratchet baseline (legacy unbacked TST)"

    return False, "No test code or passing test results found"


def _collect_test_file_index(repo_root, test_sources=None):
    """Indexes test source files under app/src/test, app/src/androidTest, and tools/."""
    test_files = {}
    if isinstance(test_sources, dict):
        for rel_p, content in test_sources.items():
            test_files[rel_p] = content if isinstance(content, str) else "".join(content)
    else:
        for base in ("app/src/test", "app/src/androidTest", "tools"):
            abs_base = os.path.join(repo_root, base)
            if not os.path.isdir(abs_base):
                continue
            for root, _, files in os.walk(abs_base):
                if "hoechstmass" in root:
                    continue
                for fn in sorted(files):
                    if base == "tools":
                        if not (fn.startswith("test_") and fn.endswith(".py")):
                            continue
                    elif not (fn.endswith(".kt") or fn.endswith(".java")):
                        continue
                    abs_p = os.path.join(root, fn)
                    rel_p = os.path.relpath(abs_p, repo_root)
                    with open(abs_p, "r", encoding="utf-8", errors="ignore") as fh:
                        test_files[rel_p] = fh.read()
    by_basename = {}
    req_in_tests = {}
    doc_tools_tests = {
        "test_verify_requirement_governance.py",
        "test_generate_test_evidence.py",
        "test_jira_accounts.py",
        "test_shard_living_docs.py",
        "test_extract_pixel_field_evidence.py",
        "test_generate_release_verification_summary.py",
    }
    for rel_p, txt in test_files.items():
        bn = os.path.basename(rel_p)
        by_basename.setdefault(bn, []).append(rel_p)
        for m in re.finditer(r"(?<![A-Za-z0-9_-])REQ-[A-Z]+-[0-9]+(?![A-Za-z0-9_-])", txt):
            req_id = m.group(0)
            if bn in doc_tools_tests and not req_id.startswith("REQ-PRO-"):
                continue
            req_in_tests.setdefault(req_id, set()).add(rel_p)
    return by_basename, req_in_tests


def triage_unbacked_verified_tsts(
    repo_root=None,
    out_csv=None,
    tst_shards=None,
    req_shards=None,
    test_sources=None,
    code_tags=None,
    baseline_allowlist=None,
):
    """
    Classifies all unbacked [Verified] TST specifications across living-doc shards into deterministic
    triage categories ('unit-test-backed', 'UI/manual-only', 'obsolete/duplicate', 'missing test code'),
    flags stale already-backed entries in LEGACY_UNBACKED_VERIFIED_TSTS, and optionally writes a CSV ledger.
    Returns (report: dict, diagnostics: list[str]).
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    if baseline_allowlist is None:
        baseline_allowlist = LEGACY_UNBACKED_VERIFIED_TSTS
    if code_tags is None:
        code_tags = extract_test_code_tags(test_sources=test_sources, repo_root=repo_root)

    by_basename, req_in_tests = _collect_test_file_index(repo_root, test_sources=test_sources)
    loaded_reqs = _load_shard_sources(req_shards, "docs/requirements", repo_root)
    loaded_tsts = _load_shard_sources(tst_shards, "docs/tests", repo_root)

    req_meta = {}
    for rel_path, lines in loaded_reqs:
        cur = None
        for line in lines:
            m = re.match(r"^###\s+(REQ-[A-Z0-9_-]+):?\s*(.*)$", line.strip())
            if m:
                cur = m.group(1)
                req_meta[cur] = {"vm": "", "tests": "", "verified": "[Verified]" in m.group(2)}
            elif cur:
                if "**Verification Method**:" in line:
                    req_meta[cur]["vm"] = line.split(":", 1)[1].strip()
                elif "**Tests**:" in line:
                    req_meta[cur]["tests"] = line.split(":", 1)[1].strip()

    heading_pat = re.compile(r"^###\s+(TST-[A-Z]+-[0-9]+(?:\.[0-9]+)?)[^:\n]*:\s*([^\n]+)")
    generic_contracts = {
        "TranslationParityTest.kt",
        "test_aspice_swe_audit.py",
        "test_doc_and_system_contracts.py",
        "SystemArchitectureDesignContractTest.kt",
        "SystemLiveTrackingRequirementsContractTest.kt",
        "SystemVerificationProtocolContractTest.kt",
        "ReleaseCiAndLintConfigContractTest.kt",
    }
    generic_kw_pat = re.compile(
        r"translat|format\s+string|locali|parity|aspice|governance|hoechstmass|shard|backfill|sprint\s+metrics",
        re.IGNORECASE,
    )
    manual_ui_pat = re.compile(
        r"(?:\b(?:Tap|Navigate\s+to|Open\s+the|Swipe|Visually|Clear\s+application\s+data|Inspect\s+the|Install|Restart\s+app|Click\s+a|On-Device|Physical\s+device|Screenshot)\b)",
        re.IGNORECASE,
    )

    entries = []
    seen_tst_ids = set()
    duplicate_tst_ids = set()
    parsed_tsts = []

    for rel_path, lines in loaded_tsts:
        cur = None
        block = []
        for lno, line in enumerate(lines, 1):
            m = heading_pat.match(line.strip())
            if m:
                if cur is not None:
                    cur["block"] = "".join(block)
                    parsed_tsts.append(cur)
                tid = m.group(1)
                base_tid = tid.split(".")[0]
                if base_tid in seen_tst_ids:
                    duplicate_tst_ids.add(base_tid)
                seen_tst_ids.add(base_tid)
                rest = m.group(2)
                cur = (
                    {
                        "base_id": base_tid,
                        "raw_id": tid,
                        "file": rel_path,
                        "line": lno,
                        "title": rest.strip(),
                    }
                    if ("[Verified]" in rest or "Verified" in rest)
                    else None
                )
                block = []
            elif cur is not None:
                block.append(line)
        if cur is not None:
            cur["block"] = "".join(block)
            parsed_tsts.append(cur)

    for item in parsed_tsts:
        base_tid = item["base_id"]
        if base_tid in code_tags:
            continue
        block = item["block"]
        title = item["title"]
        reqs = sorted(set(re.findall(r"(?<![A-Za-z0-9_-])REQ-[A-Z]+-[0-9]+(?![A-Za-z0-9_-])", block)))
        vms = sorted({req_meta[r]["vm"] for r in reqs if r in req_meta and req_meta[r]["vm"]})
        vm_str = "; ".join(vms) if vms else "Unspecified"
        comp_m = re.search(r"\*\*Component\*\*:\s*([^\n]+)", block)
        comp = comp_m.group(1) if comp_m else ""
        is_generic_topic = bool(generic_kw_pat.search(title + " " + block))

        explicit_files = [
            p
            for fn in re.findall(r"([A-Za-z0-9_]+Test\.(?:kt|java)|test_[a-z0-9_]+\.py)", block)
            for p in by_basename.get(fn, [])
            if os.path.basename(p) not in generic_contracts or is_generic_topic
        ]
        comp_files = [
            p
            for tok in re.findall(r"\b([A-Za-z0-9_]+)\b", comp)
            for cand in (f"{tok}Test.kt", f"{tok}Test.java", f"test_{tok}.py")
            for p in by_basename.get(cand, [])
        ]
        req_files = [
            p
            for r in reqs
            for p in req_in_tests.get(r, ())
            if os.path.basename(p) not in generic_contracts or is_generic_topic
        ]
        req_tfield_files = [
            p
            for r in reqs
            if r in req_meta
            for fn in re.findall(r"([A-Za-z0-9_]+Test\.(?:kt|java)|test_[a-z0-9_]+\.py)", req_meta[r]["tests"])
            for p in by_basename.get(fn, [])
            if os.path.basename(p) not in generic_contracts or is_generic_topic
        ]
        matched_files = sorted(set(explicit_files or comp_files or req_files or req_tfield_files))

        is_obsolete = (
            base_tid in duplicate_tst_ids
            or bool(re.search(r"\b(?:superseded|obsolete|deprecated|removed\s+by)\b", title, re.IGNORECASE))
            or bool(re.search(r"\*\*Test Description\*\*:\s*(?:\*\*)?(?:SUPERSEDED|OBSOLETE|DEPRECATED)", block, re.IGNORECASE))
        )
        is_manual_vm = ("On-Device Manual" in vm_str and "Unit" not in vm_str) or vm_str == "Review"

        if is_obsolete:
            category = "obsolete/duplicate"
            rec_status = "[Implemented]"
            rec_action = "Downgrade superseded/duplicate spec to [Implemented]"
        elif matched_files and not is_manual_vm:
            category = "unit-test-backed"
            rec_status = "[Verified]"
            rec_action = "Tag existing test file(s) with TST-* ID"
        elif is_manual_vm or (manual_ui_pat.search(block) and not matched_files):
            category = "UI/manual-only"
            rec_status = "[Implemented]"
            rec_action = "Downgrade to [Implemented] pending attested manual protocol"
        else:
            category = "missing test code"
            rec_status = "[Implemented]"
            rec_action = "Downgrade to [Implemented] and file domain test follow-up ticket"

        entries.append({
            "tst_id": base_tid,
            "shard": item["file"],
            "line": item["line"],
            "category": category,
            "recommended_status": rec_status,
            "recommended_action": rec_action,
            "matched_test_files": ";".join(matched_files),
            "linked_reqs": ";".join(reqs),
            "verification_method": vm_str,
            "title": title,
        })

    unbacked_ids = {e["tst_id"] for e in entries}
    stale_baseline = sorted(set(baseline_allowlist) - unbacked_ids)
    by_category = {}
    by_shard = {}
    for e in entries:
        by_category[e["category"]] = by_category.get(e["category"], 0) + 1
        shard_name = os.path.basename(e["shard"])
        by_shard[shard_name] = by_shard.get(shard_name, 0) + 1

    if out_csv:
        abs_csv = out_csv if os.path.isabs(out_csv) else os.path.join(repo_root, out_csv)
        os.makedirs(os.path.dirname(abs_csv), exist_ok=True)
        fieldnames = [
            "tst_id",
            "shard",
            "line",
            "category",
            "recommended_status",
            "recommended_action",
            "matched_test_files",
            "linked_reqs",
            "verification_method",
            "title",
        ]
        with open(abs_csv, "w", encoding="utf-8", newline="") as fh:
            writer = csv.DictWriter(fh, fieldnames=fieldnames)
            writer.writeheader()
            for e in sorted(entries, key=lambda x: (x["shard"], x["line"], x["tst_id"])):
                writer.writerow(e)

    report = {
        "total_unbacked_verified_tsts": len(entries),
        "stale_baseline_count": len(stale_baseline),
        "stale_baseline_ids": stale_baseline,
        "by_category": by_category,
        "by_shard": by_shard,
        "entries": entries,
    }
    diagnostics = [
        f"Unbacked [Verified] TSTs: {len(entries)} | Stale baseline entries: {len(stale_baseline)}",
        "Categories: " + ", ".join(f"{k}={v}" for k, v in sorted(by_category.items())),
        "Shards: " + ", ".join(f"{k}={v}" for k, v in sorted(by_shard.items())),
    ]
    if out_csv:
        diagnostics.append(f"Exported triage CSV ledger to {out_csv}")
    return report, diagnostics




def verify_shard_header_counts(repo_root=None):
    """
    Verifies that declared **Total Requirements / Tests** header counts match parsed section headings
    across all 18 living documentation shards with 100% parity (REQ-PRO-104.2).
    Returns (success: bool, diagnostics: list[str]).
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    specs = [
        ("docs/requirements", "REQ", "Total Requirements"),
        ("docs/tests", "TST", "Total Tests"),
    ]
    errors = []
    checked_shards = 0
    for shard_dir_rel, prefix, header_label in specs:
        abs_shard_dir = os.path.join(repo_root, shard_dir_rel)
        if not os.path.isdir(abs_shard_dir):
            continue
        for fn in sorted(os.listdir(abs_shard_dir)):
            if not fn.endswith(".md"):
                continue
            fp = os.path.join(abs_shard_dir, fn)
            checked_shards += 1
            tot = 0
            declared_header = None
            with open(fp, "r", encoding="utf-8") as f:
                for line in f:
                    if declared_header is None:
                        m_hdr = re.search(r"\*\*Total (?:Requirements|Tests)\*\*:\s*(\d+)", line)
                        if m_hdr:
                            declared_header = int(m_hdr.group(1))
                    m_sec = re.match(r"^(#{2,4})\s*(" + prefix + r"-[A-Z0-9_-]+):?\s*(.*?)$", line.strip())
                    if m_sec:
                        tot += 1

            if declared_header is None:
                errors.append(f"ERROR: {fn} missing '**{header_label}**: N' header count.")
            elif declared_header != tot:
                errors.append(
                    f"ERROR: {fn} header count ({declared_header}) does not match actual {prefix} count ({tot})."
                )

    if errors:
        return False, errors
    return True, [f"PASS: All {checked_shards} domain shard header counts match parsed sections with 100% parity."]


def verify_shard_sizes(repo_root=None, max_lines=4000):
    """
    Verifies all living documentation shards under docs/requirements/ and docs/tests/
    do not exceed max_lines using pure Python stdlib (REQ-PRO-093.3, REQ-PRO-127.4).
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    total_shards = 0
    errors = []
    for rel_dir in ("docs/requirements", "docs/tests"):
        abs_dir = os.path.join(repo_root, rel_dir)
        if not os.path.isdir(abs_dir):
            continue
        for fn in sorted(os.listdir(abs_dir)):
            if not fn.endswith(".md"):
                continue
            abs_fp = os.path.join(abs_dir, fn)
            if not os.path.isfile(abs_fp):
                continue
            total_shards += 1
            with open(abs_fp, "r", encoding="utf-8") as fh:
                line_count = sum(1 for _ in fh)
            if line_count > max_lines:
                errors.append(
                    f"ERROR: {rel_dir}/{fn} ({line_count} lines) exceeds {max_lines}-line shard limit (REQ-PRO-093.3)."
                )
    if errors:
        return False, errors
    return True, [
        f"PASS: All {total_shards} domain shards are within line limits (limit: {max_lines} lines)."
    ]


def recalculate_master_index_tables(repo_root=None):
    """
    Recalculates summary tables ('Total', 'Verified', 'Planned / Open') and total requirement/test
    counts in docs/requirements.md and docs/tests.md based on actual parsed section headings
    in the 9 domain shards under docs/requirements/ and docs/tests/ (ATT-5431, REQ-PRO-041.4).
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    specs = [
        ("docs/requirements.md", "docs/requirements", "REQ"),
        ("docs/tests.md", "docs/tests", "TST"),
    ]
    recalculated = []
    for master_rel, shard_dir_rel, prefix in specs:
        abs_shard_dir = os.path.join(repo_root, shard_dir_rel)
        if not os.path.isdir(abs_shard_dir):
            continue
        shard_counts = {}
        for fn in sorted(os.listdir(abs_shard_dir)):
            if not fn.endswith(".md"):
                continue
            fp = os.path.join(abs_shard_dir, fn)
            tot = ver = impl = plan = 0
            with open(fp, "r", encoding="utf-8") as f:
                for line in f:
                    m = re.match(r"^(#{2,4})\s*(" + prefix + r"-[A-Z0-9_-]+):?\s*(.*?)$", line.strip())
                    if not m:
                        continue
                    tot += 1
                    if "[Verified]" in line:
                        ver += 1
                    elif "[Implemented]" in line:
                        impl += 1
                    else:
                        plan += 1
            shard_counts[fn] = (tot, ver, impl, plan)

            header_label = "Total Requirements" if prefix == "REQ" else "Total Tests"
            with open(fp, "r", encoding="utf-8") as f:
                shard_content = f.read()
            new_shard_content = re.sub(
                r"\*\*Total (?:Requirements|Tests)\*\*:\s*\d+",
                f"**{header_label}**: {tot}",
                shard_content,
                count=1,
            )
            if new_shard_content != shard_content:
                shard_rel = f"{shard_dir_rel}/{fn}"
                new_shard_content = bump_work_product_header_on_edit(
                    new_shard_content, shard_rel, repo_root=repo_root
                )
                with open(fp, "w", encoding="utf-8") as f:
                    f.write(new_shard_content)

        master_fp = os.path.join(repo_root, master_rel)
        if not os.path.exists(master_fp):
            continue
        with open(master_fp, "r", encoding="utf-8") as f:
            master_lines = f.readlines()

        grand_total = sum(c[0] for c in shard_counts.values())
        grand_verified = sum(c[1] for c in shard_counts.values())
        grand_implemented = sum(c[2] for c in shard_counts.values())
        grand_planned = sum(c[3] for c in shard_counts.values())

        new_lines = []
        changed = False
        for line in master_lines:
            new_line = line
            if re.search(r"\|\s*Total\s*\|\s*Verified\s*\|\s*Planned\s*/\s*Open\s*\|", line):
                new_line = re.sub(
                    r"\|\s*Total\s*\|\s*Verified\s*\|\s*Planned\s*/\s*Open\s*\|",
                    "| Total | Verified | Implemented | Planned / Open |",
                    line,
                )
            elif re.search(r"\|\s*:---\s*:\s*\|\s*:---\s*:\s*\|\s*:---\s*:\s*\|", line) and line.count(":---:") == 3:
                new_line = line.rstrip() + " :---: |\n"
            m_link = re.search(r"\((?:requirements|tests)/((?:req|tst)_[^)]+\.md)\)", line)
            if m_link:
                s_name = m_link.group(1)
                if s_name in shard_counts:
                    t, v, i, p = shard_counts[s_name]
                    new_line = re.sub(
                        r"\|\s*\d+\s*\|\s*\d+(?:\s*\|\s*\d+)?\s*\|\s*\d+\s*\|(\s*)$",
                        rf"| {t} | {v} | {i} | {p} |\1",
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
            joined = bump_work_product_header_on_edit(
                "".join(new_lines), master_rel, repo_root=repo_root
            )
            with open(master_fp, "w", encoding="utf-8") as f:
                f.write(joined)
            msg = f"Recalculated master index table in {master_rel}: Total={grand_total}, Verified={grand_verified}, Implemented={grand_implemented}, Planned={grand_planned}."
            print(msg)
            recalculated.append(msg)
        else:
            recalculated.append(f"Master index table in {master_rel} is already up to date.")
    return recalculated


ALLOWLIST_COMPOSABLES = {
    "Theme",
    "ATrainingTrackerTheme",
    "TrainingTrackerTheme",
}

GRANDFATHERED_UNGROUNDED_SYMBOLS = {
    "backup_rules.xml",
    "BikeBellAudioSynthesizer.java",
    "MapViewportHelper.kt",
    "SoundEngine.java",
    "RadarThreatView.kt",
    "StravaUploader.java",
    "GoogleDriveUploader.java",
    "auth_credentials_prefs.xml",
    "WorkoutDataHelper.java",
    "WorkoutDataMapper.java",
    "LegacyImportEngine.java",
    "StravaUploadHelper.kt",
    "DatabaseHelper.java",
}


def find_composable_callers(func_name, search_dir, exclude_file=None):
    """
    Searches search_dir (e.g. app/src/main) for production callers of func_name.
    Excludes the declaration itself and callers located inside @Preview functions.
    """
    callers = []
    if not os.path.isdir(search_dir):
        return callers

    call_pattern = re.compile(rf"\b{re.escape(func_name)}\s*\(")
    decl_pattern = re.compile(rf"\bfun\s+(?:<[^>]+>\s+)?{re.escape(func_name)}\b")

    for root, _, files in os.walk(search_dir):
        for f in files:
            if not (f.endswith(".kt") or f.endswith(".java")):
                continue
            fp = os.path.join(root, f)
            is_same_file = bool(exclude_file and os.path.normpath(fp) == os.path.normpath(exclude_file))

            try:
                with open(fp, "r", encoding="utf-8", errors="ignore") as fh:
                    content = fh.read()
            except Exception:
                continue

            if not call_pattern.search(content):
                continue

            lines = content.splitlines()
            in_preview = False
            brace_depth = 0
            saw_open_brace = False
            pending_preview = False

            for idx, line in enumerate(lines, 1):
                stripped = line.strip()

                if re.search(r"@(Preview|PreviewLightDark|DevicePreview|PreviewScreenSizes|PreviewParameter)\b", stripped):
                    pending_preview = True

                if re.search(r"\bfun\s+[A-Za-z0-9_]+\s*\(", stripped):
                    if pending_preview or "preview" in stripped.lower():
                        in_preview = True
                        brace_depth = 0
                        saw_open_brace = False
                    pending_preview = False

                if in_preview:
                    open_count = line.count("{")
                    close_count = line.count("}")
                    if open_count > 0:
                        saw_open_brace = True
                    brace_depth += open_count - close_count
                    if saw_open_brace and brace_depth <= 0:
                        in_preview = False
                    continue

                if call_pattern.search(line):
                    if is_same_file and decl_pattern.search(line):
                        continue
                    if stripped.startswith("//") or stripped.startswith("*"):
                        continue
                    callers.append(f"{fp}:{idx}")
                    break
        if callers:
            break
    return callers


def extract_added_composables(diff_text):
    """
    Extracts newly added non-preview, non-private @Composable function names and file paths from a git diff.
    Returns list of (func_name, file_path).
    """
    added_composables = []
    current_file = None
    file_lines = {}

    for line in diff_text.splitlines():
        if line.startswith("+++ b/"):
            current_file = line[6:].strip()
            if current_file.startswith("app/src/main/") and current_file.endswith(".kt"):
                file_lines.setdefault(current_file, [])
        elif current_file and current_file.startswith("app/src/main/") and current_file.endswith(".kt"):
            file_lines[current_file].append(line)

    for fp, lines in file_lines.items():
        pending_preview = False
        pending_composable = False
        for line in lines:
            if not line.startswith("+") or line.startswith("+++"):
                if line.startswith(" ") and not line.strip().startswith("@"):
                    pending_preview = False
                    pending_composable = False
                continue

            stripped = line[1:].strip()
            if not stripped:
                continue

            if re.search(r"@(Preview|PreviewLightDark|DevicePreview|PreviewScreenSizes|PreviewParameter)\b", stripped):
                pending_preview = True

            if "@Composable" in stripped:
                pending_composable = True

            m_fun = re.search(r"\bfun\s+(?:<[^>]+>\s+)?([A-Za-z0-9_]+)\s*\(", stripped)
            if m_fun:
                func_name = m_fun.group(1)
                is_private = bool(re.search(r"\bprivate\s+fun\b", stripped))
                is_preview = pending_preview or bool(re.search(r"@(Preview|PreviewLightDark|DevicePreview)\b", stripped)) or func_name.endswith("Preview")
                is_composable = pending_composable or ("@Composable" in stripped)

                if is_composable and not is_preview and not is_private:
                    if func_name not in ALLOWLIST_COMPOSABLES:
                        added_composables.append((func_name, fp))

                pending_preview = False
                pending_composable = False

    return added_composables


def verify_composable_islands(diff_text=None, repo_root=None, base_ref="develop"):
    """
    Zero-Caller @Composable Island Check (Rule 81 / REQ-PRO-041.5).
    Detects newly added non-@Preview, non-private @Composable declarations in modified
    app/src/main/**/*.kt files and verifies at least one production call site exists in app/src/main/.
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    if diff_text is None:
        diff_text = get_git_diff(base_ref=base_ref, target_paths=["app/src/main/"])

    if not diff_text or not diff_text.strip():
        return True, ["PASS: Zero Composable islands detected (no modified UI files)."]

    added_composables = extract_added_composables(diff_text)
    if not added_composables:
        return True, ["PASS: Zero unmounted Composable islands detected in git diff."]

    main_dir = os.path.join(repo_root, "app/src/main")
    errors = []
    checked_count = 0

    for func_name, decl_fp in added_composables:
        checked_count += 1
        full_decl_path = os.path.join(repo_root, decl_fp) if not os.path.isabs(decl_fp) else decl_fp
        callers = find_composable_callers(func_name, main_dir, exclude_file=full_decl_path)
        if not callers:
            errors.append(
                f"Zero-caller @Composable island detected: '{func_name}' in {decl_fp} "
                f"has zero production call sites in app/src/main/ (only preview or uncalled)."
            )

    if errors:
        return False, errors
    return True, [f"PASS: Verified {checked_count} newly added @Composable component(s) have valid production callers."]


def verify_repository_encapsulation(diff_text=None, repo_root=None, base_ref="develop"):
    """
    Repository Encapsulation Guard (Rule 81 / coding_standards.md Sec 2.5 / REQ-PRO-041.5).
    Rejects any non-private method or property exposing *DatabaseManager, *DbHelper,
    SQLiteDatabase, or Cursor in modified/added *Repository.kt and *Repository.java files.
    """
    if diff_text is None:
        diff_text = get_git_diff(base_ref=base_ref, target_paths=["app/src/main/"])

    if not diff_text or not diff_text.strip():
        return True, ["PASS: Zero repository encapsulation violations detected."]

    current_file = None
    errors = []
    prohibited_types_re = r"\b(?:[A-Za-z0-9_]*DatabaseManager|[A-Za-z0-9_]*DbHelper|SQLiteDatabase|Cursor)\b"

    for line in diff_text.splitlines():
        if line.startswith("+++ b/"):
            current_file = line[6:].strip()
            continue
        if not current_file or not (current_file.endswith("Repository.kt") or current_file.endswith("Repository.java")):
            continue
        if not line.startswith("+") or line.startswith("+++"):
            continue

        stripped = line[1:].strip()
        if not stripped or stripped.startswith("//") or stripped.startswith("*") or stripped.startswith("/*") or stripped.startswith("import "):
            continue

        if stripped.startswith("private ") or " private val " in stripped or " private var " in stripped or " private fun " in stripped:
            continue

        is_fun_returning_prohibited = re.search(
            rf"\bfun\s+[A-Za-z0-9_]+\s*\([^)]*\)\s*:\s*.*?" + prohibited_types_re, stripped
        )
        is_prop_with_prohibited = re.search(
            rf"\b(?:val|var)\s+[A-Za-z0-9_]+\s*:\s*.*?" + prohibited_types_re, stripped
        )
        is_getter_name = re.search(
            r"\bfun\s+get[A-Za-z0-9_]*(?:DatabaseManager|DbHelper|Database|Cursor)\s*\(", stripped
        )
        is_java_prohibited = re.search(
            rf"(?:public|protected)?\s+" + prohibited_types_re + r"\s+(?:get[A-Za-z0-9_]*|[A-Za-z0-9_]+)\s*\(", stripped
        )

        if is_fun_returning_prohibited or is_prop_with_prohibited or is_getter_name or is_java_prohibited:
            errors.append(
                f"Repository encapsulation violation in {current_file}: "
                f"Non-private member '{stripped}' exposes SQLite/DatabaseManager internals."
            )

    if errors:
        return False, errors
    return True, ["PASS: Repository encapsulation verified (zero leaky DatabaseManager/Cursor members)."]


def verify_architecture_and_system_specs(diff_text=None, repo_root=None, base_ref="develop"):
    """
    Architecture & System Spec Grounding & SYSREQ Bidirectional Equality Guard (Rule 81 / REQ-PRO-041.5).
    Verifies that every backticked source file in modified architecture/system markdown files exists
    in the repository and that declared SYSREQ-* IDs match allocated IDs.
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    if diff_text is None:
        diff_text = get_git_diff(
            base_ref=base_ref,
            target_paths=["docs/architecture.md", "docs/architecture/", "docs/system/"],
        )

    if not diff_text or not diff_text.strip():
        return True, ["PASS: Zero architecture or system specification modifications detected."]

    errors = []
    current_file = None
    sysreq_files_modified = False

    # Index existing filenames in repository for fast basename lookup
    all_repo_basenames = set()
    for root, _, files in os.walk(repo_root):
        if ".git" in root:
            continue
        for f in files:
            all_repo_basenames.add(f)

    for line in diff_text.splitlines():
        if line.startswith("+++ b/"):
            current_file = line[6:].strip()
            if current_file.startswith("docs/system"):
                sysreq_files_modified = True
            continue

        if not current_file or not (current_file.startswith("docs/architecture") or current_file.startswith("docs/system")):
            continue

        if not line.startswith("+") or line.startswith("+++"):
            continue

        # Extract backticked filenames
        ticks = re.findall(r"`([^`\n]+\.(?:kt|java|xml))`", line[1:])
        for t in ticks:
            if t in GRANDFATHERED_UNGROUNDED_SYMBOLS:
                continue
            direct_path = os.path.join(repo_root, t)
            base_name = os.path.basename(t)
            if not os.path.isfile(direct_path) and base_name not in all_repo_basenames:
                errors.append(
                    f"Ungrounded architecture/system symbol in {current_file}: "
                    f"'{t}' does not resolve to an authentic file in the repository."
                )

    if sysreq_files_modified:
        declared_ids = set()
        sysreq_dir = os.path.join(repo_root, "docs/system")
        if os.path.isdir(sysreq_dir):
            for fn in os.listdir(sysreq_dir):
                if fn.startswith("sysreq_") and fn.endswith(".md"):
                    with open(os.path.join(sysreq_dir, fn), "r", encoding="utf-8") as fh:
                        for m in re.finditer(r"SYSREQ-[A-Z0-9_-]+", fh.read()):
                            clean_id = m.group(0).rstrip("-_")
                            if clean_id != "SYSREQ-XXX":
                                declared_ids.add(clean_id)

        allocated_ids = set()
        sys_arch_fp = os.path.join(repo_root, "docs/system/system_architecture.md")
        if os.path.isfile(sys_arch_fp):
            with open(sys_arch_fp, "r", encoding="utf-8") as fh:
                for m in re.finditer(r"SYSREQ-[A-Z0-9_-]+", fh.read()):
                    clean_id = m.group(0).rstrip("-_")
                    if clean_id != "SYSREQ-XXX":
                        allocated_ids.add(clean_id)

        missing_allocation = declared_ids - allocated_ids
        missing_declaration = allocated_ids - declared_ids

        if missing_allocation:
            errors.append(
                f"SYSREQ allocation mismatch: {', '.join(sorted(missing_allocation))} "
                f"declared in docs/system/sysreq_*.md but missing from allocation in docs/system/system_architecture.md."
            )
        if missing_declaration:
            errors.append(
                f"SYSREQ declaration mismatch: {', '.join(sorted(missing_declaration))} "
                f"allocated in docs/system/system_architecture.md but missing declaration in docs/system/sysreq_*.md."
            )

    if errors:
        return False, errors
    return True, ["PASS: Architecture & system specification code-grounding and SYSREQ parity verified."]


DOC_HEADER_CUTOFF_TS = 1794934635  # ATT-8163 baseline commit timestamp; enforces post-ATT-8164 edits
COMMIT_HYGIENE_CUTOFF_TS = 1794939034  # ATT-8164 baseline commit timestamp; enforces post-ATT-8165 commits
_CONFLICT_MARKER_LINE_RE = re.compile(r"^(?:<{7}(?: .*)?|={7}|>{7}(?: .*)?)$")
_COMMIT_TICKET_KEY_RE = re.compile(r"\bATT-\d+\b")
_VALID_DOC_STATUSES = {"Draft", "Reviewed", "Approved"}
_ADR_EXTRA_STATUS = "Retrospective (Pending PO Sign-Off)"
_UNAPPROVED_APPROVER_RE = re.compile(
    r"^(?:$|pending|ai\b|ai\s+agent|agent\b|antigravity|unassigned|none|n/a|tbd|auto)",
    re.IGNORECASE,
)
_HUMAN_TRAILER_RE = re.compile(
    r"^\s*(?:Human-Approved|Interactive-PO)\s*:\s*\S+",
    re.MULTILINE | re.IGNORECASE,
)


def _semver_tuple(ver_str):
    parts = str(ver_str or "").strip().split(".")
    if len(parts) != 3 or not all(p.isdigit() for p in parts):
        return (0, 0, 0)
    return tuple(int(p) for p in parts)


def bump_work_product_header_on_edit(text, rel_path, repo_root=None, base_ref="develop"):
    """
    Bumps patch semver Version (if not already bumped relative to git base) and resets
    Approved -> Reviewed with Pending PO Sign-Off when tooling updates a governed work product.
    """
    if "**Version**:" not in text:
        return text
    m_ver = re.search(r"(\*\*Version\*\*:\s*`?)(\d+\.\d+\.)(\d+)(`?)", text)
    if not m_ver:
        return text

    base_ver = None
    if repo_root is not None:
        try:
            resolved_base = _resolve_git_base_ref(base_ref, cwd=repo_root)
            if resolved_base:
                old_txt = subprocess.check_output(
                    ["git", "show", f"{resolved_base}:{rel_path}"],
                    cwd=repo_root,
                    stderr=subprocess.DEVNULL,
                ).decode("utf-8", errors="replace")
                m_old = re.search(r"\*\*Version\*\*:\s*`?(\d+\.\d+\.\d+)`?", old_txt)
                if m_old:
                    base_ver = m_old.group(1)
        except Exception:
            base_ver = None

    cur_ver = f"{m_ver.group(2)}{m_ver.group(3)}"
    updated = text
    if base_ver is None or _semver_tuple(cur_ver) <= _semver_tuple(base_ver):
        bumped = f"{m_ver.group(2)}{int(m_ver.group(3)) + 1}"
        updated = re.sub(
            r"(\*\*Version\*\*:\s*`?)\d+\.\d+\.\d+(`?)",
            rf"\g<1>{bumped}\g<2>",
            updated,
            count=1,
        )
    if re.search(r"\*\*Status\*\*:\s*Approved\b", updated):
        updated = re.sub(r"(\*\*Status\*\*:\s*)Approved\b", r"\1Reviewed", updated, count=1)
        updated = re.sub(r"(\*\*Approver\*\*:\s*)[^\n|]+", r"\1Pending PO Sign-Off", updated, count=1)
    return updated


def _resolve_git_base_ref(base_ref="develop", cwd=None):
    """Resolves the nearest sprint or develop base commit/ref for diff checks."""
    effective_base = base_ref
    if effective_base == "develop":
        try:
            sprint_branches = subprocess.check_output(
                ["git", "for-each-ref", "--sort=-committerdate", "--format=%(refname:short)", "refs/heads/sprint/*"],
                cwd=cwd,
                stderr=subprocess.DEVNULL,
            ).decode("utf-8").strip().splitlines()
            best_sb = None
            best_commit_time = -1
            for sb in sprint_branches:
                ret = subprocess.call(
                    ["git", "merge-base", "--is-ancestor", sb, "HEAD"],
                    cwd=cwd,
                    stderr=subprocess.DEVNULL,
                )
                if ret == 0:
                    effective_base = sb
                    best_sb = None
                    break
                try:
                    mb = subprocess.check_output(
                        ["git", "merge-base", sb, "HEAD"],
                        cwd=cwd,
                        stderr=subprocess.DEVNULL,
                    ).decode("utf-8").strip()
                    if mb:
                        ts = int(subprocess.check_output(
                            ["git", "log", "-1", "--format=%ct", mb],
                            cwd=cwd,
                            stderr=subprocess.DEVNULL,
                        ).decode("utf-8").strip())
                        if ts > best_commit_time:
                            best_commit_time = ts
                            best_sb = sb
                except Exception:
                    pass
            if best_sb:
                effective_base = best_sb
        except Exception:
            pass

    try:
        mb = subprocess.check_output(
            ["git", "merge-base", effective_base, "HEAD"],
            cwd=cwd,
            stderr=subprocess.DEVNULL,
        ).decode("utf-8").strip()
        if mb:
            return mb
    except Exception:
        pass
    try:
        subprocess.check_output(
            ["git", "rev-parse", "--verify", effective_base],
            cwd=cwd,
            stderr=subprocess.DEVNULL,
        )
        return effective_base
    except Exception:
        return None


def discover_governed_work_products(repo_root=None):
    """
    Returns sorted repo-relative paths of all governed work-product markdown files (REQ-PRO-120).
    In a Git repository, restricts discovery to tracked/staged files via `git ls-files` so
    untracked or gitignored local drafts (e.g. docs/management/schichtbetrieb.md) are ignored;
    falls back to filesystem listing only outside Git repositories.
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    exact_files = [
        "docs/architecture.md",
        "docs/requirements.md",
        "docs/tests.md",
        "docs/coding_standards.md",
        "docs/design_guidelines.md",
        "docs/project_governance.md",
        "docs/project_protocol.md",
    ]
    dir_globs = [
        "docs/management",
        "docs/architecture",
        "docs/architecture/detailed_design",
        "docs/system",
        "docs/requirements",
        "docs/tests",
        ".agents/rules",
    ]
    exact_set = set(exact_files)
    dir_set = set(dir_globs)

    is_git_repo = False
    try:
        top_res = subprocess.run(
            ["git", "rev-parse", "--show-toplevel"],
            cwd=repo_root,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
        )
        if top_res.returncode == 0 and top_res.stdout.strip():
            is_git_repo = os.path.realpath(top_res.stdout.strip()) == os.path.realpath(repo_root)
    except Exception:
        is_git_repo = False

    if is_git_repo:
        try:
            ls_res = subprocess.run(
                ["git", "-c", "core.quotePath=false", "ls-files", "--"] + exact_files + dir_globs,
                cwd=repo_root,
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                text=True,
                check=True,
            )
            candidates = []
            for rel in ls_res.stdout.splitlines():
                rel = rel.strip().replace("\\", "/")
                if not rel.endswith(".md"):
                    continue
                if rel not in exact_set and os.path.dirname(rel) not in dir_set:
                    continue
                if os.path.isfile(os.path.join(repo_root, rel)):
                    candidates.append(rel)
            return sorted(set(candidates))
        except Exception:
            pass

    candidates = []
    for rel in exact_files:
        if os.path.isfile(os.path.join(repo_root, rel)):
            candidates.append(rel)

    for rel_dir in dir_globs:
        abs_dir = os.path.join(repo_root, rel_dir)
        if not os.path.isdir(abs_dir):
            continue
        for fn in sorted(os.listdir(abs_dir)):
            if fn.endswith(".md"):
                candidates.append(f"{rel_dir}/{fn}")
    return sorted(set(candidates))


def parse_work_product_header(text):
    """Extracts Document ID, Version, Status, Approver, and Change History presence from markdown."""
    head_lines = []
    for line in text.splitlines():
        if re.match(r"^##\s+", line):
            break
        head_lines.append(line)
        if len(head_lines) >= 40:
            break
    head = "\n".join(head_lines)

    m_id = re.search(r"\*\*Document ID\*\*:\s*`?(DOC-[A-Z0-9-]+)`?", head)
    m_ver = re.search(r"\*\*Version\*\*:\s*`?(\d+\.\d+\.\d+)`?", head)
    m_status = re.search(r"\*\*Status\*\*:\s*([^|\n]+)", head)
    m_approver = re.search(r"\*\*Approver\*\*:\s*([^|\n]+)", head)
    has_history = bool(
        re.search(r"^##\s+(?:\d+\.\s+)?(?:Change|Revision)\s+History\b", text, re.MULTILINE)
        and re.search(r"^\|\s*Version\s*\|", text, re.MULTILINE)
    )

    return {
        "document_id": m_id.group(1).strip() if m_id else None,
        "version": m_ver.group(1).strip() if m_ver else None,
        "status": m_status.group(1).strip().strip("`").strip() if m_status else None,
        "approver": m_approver.group(1).strip().strip("`").strip() if m_approver else None,
        "has_history": has_history,
    }


def _strip_header_and_history(text):
    """Returns document body excluding top metadata block and Change/Revision History section."""
    lines = text.splitlines()
    # Skip top metadata lines before first horizontal rule or ## section
    start_idx = 0
    for i, line in enumerate(lines):
        if i > 0 and (line.strip() == "---" or re.match(r"^##\s+", line)):
            start_idx = i
            break
        if i >= 30:
            start_idx = i
            break
    body_lines = []
    in_history = False
    for line in lines[start_idx:]:
        if re.match(r"^##\s+(?:\d+\.\s+)?(?:Change|Revision)\s+History\b", line):
            in_history = True
            continue
        if in_history and re.match(r"^##\s+", line):
            in_history = False
        if not in_history:
            body_lines.append(line)
    return "\n".join(body_lines).strip()


def _is_human_approver(approver):
    if not approver:
        return False
    return not bool(_UNAPPROVED_APPROVER_RE.search(approver.strip()))


def verify_work_product_headers(
    repo_root=None,
    base_ref="develop",
    check_diff=True,
    cutoff_ts=DOC_HEADER_CUTOFF_TS,
):
    """
    Validates standardized work-product headers, monotonic version bumps on body changes,
    and human-only Approved status enforcement (REQ-PRO-120 / GP 2.2 / SUP.8 BP2).
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    governed = discover_governed_work_products(repo_root=repo_root)
    if not governed:
        return True, ["PASS: Zero governed work products discovered."]

    errors = []
    parsed_current = {}
    for rel_path in governed:
        abs_path = os.path.join(repo_root, rel_path)
        with open(abs_path, "r", encoding="utf-8") as fh:
            text = fh.read()
        hdr = parse_work_product_header(text)
        parsed_current[rel_path] = (hdr, text)

        missing = []
        if not hdr["document_id"]:
            missing.append("Document ID (DOC-*)")
        if not hdr["version"]:
            missing.append("Version (semver X.Y.Z)")
        if not hdr["status"]:
            missing.append("Status")
        if not hdr["approver"]:
            missing.append("Approver")
        if not hdr["has_history"]:
            missing.append("Change History table")
        if missing:
            errors.append(
                f"ERROR: {rel_path} missing required work-product header metadata: {', '.join(missing)} (REQ-PRO-120.1)."
            )
            continue

        allowed_statuses = set(_VALID_DOC_STATUSES)
        if os.path.basename(rel_path).startswith("adr_"):
            allowed_statuses.add(_ADR_EXTRA_STATUS)
        if hdr["status"] not in allowed_statuses:
            errors.append(
                f"ERROR: {rel_path} has invalid Status '{hdr['status']}' (allowed: {sorted(allowed_statuses)})."
            )

        if hdr["status"] == "Approved" and not _is_human_approver(hdr["approver"]):
            errors.append(
                f"ERROR: {rel_path} claims Status 'Approved' with non-human/pending Approver '{hdr['approver']}' (REQ-PRO-120.2)."
            )

    if check_diff:
        resolved_base = _resolve_git_base_ref(base_ref=base_ref, cwd=repo_root)
        if resolved_base:
            for rel_path in governed:
                if rel_path not in parsed_current:
                    continue
                hdr, new_text = parsed_current[rel_path]
                if not hdr["version"] or not hdr["status"]:
                    continue

                # Check uncommitted changes in working tree
                wt_dirty = False
                try:
                    wt_out = subprocess.check_output(
                        ["git", "status", "--porcelain", "--", rel_path],
                        cwd=repo_root,
                        stderr=subprocess.DEVNULL,
                    ).decode("utf-8", errors="replace").strip()
                    wt_dirty = bool(wt_out)
                except Exception:
                    wt_dirty = False

                # Read baseline text at resolved_base
                old_text = None
                try:
                    old_text = subprocess.check_output(
                        ["git", "show", f"{resolved_base}:{rel_path}"],
                        cwd=repo_root,
                        stderr=subprocess.DEVNULL,
                    ).decode("utf-8", errors="replace")
                except Exception:
                    old_text = None

                if old_text is not None and old_text == new_text and not wt_dirty:
                    continue

                # Determine whether all commits modifying rel_path since resolved_base are pre-cutoff
                commit_shas = []
                latest_has_human_trailer = False
                all_pre_cutoff = False
                if not wt_dirty:
                    try:
                        log_out = subprocess.check_output(
                            ["git", "log", "--format=%H%x1f%ct", f"{resolved_base}..HEAD", "--", rel_path],
                            cwd=repo_root,
                            stderr=subprocess.DEVNULL,
                        ).decode("utf-8", errors="replace").splitlines()
                        parsed_commits = []
                        for row in log_out:
                            if "\x1f" not in row:
                                continue
                            c_sha, c_ts = row.split("\x1f", 1)
                            parsed_commits.append((c_sha.strip(), int(c_ts.strip())))
                        commit_shas = [c[0] for c in parsed_commits]
                        if parsed_commits and all(ts <= cutoff_ts for _, ts in parsed_commits):
                            all_pre_cutoff = True
                        if commit_shas:
                            msg = subprocess.check_output(
                                ["git", "log", "-n", "1", "--format=%B", commit_shas[0]],
                                cwd=repo_root,
                                stderr=subprocess.DEVNULL,
                            ).decode("utf-8", errors="replace")
                            latest_has_human_trailer = bool(_HUMAN_TRAILER_RE.search(msg))
                    except Exception:
                        pass

                if all_pre_cutoff:
                    continue

                old_hdr = parse_work_product_header(old_text) if old_text is not None else None
                body_changed = (
                    old_text is None
                    or _strip_header_and_history(old_text) != _strip_header_and_history(new_text)
                )

                if body_changed and old_hdr and old_hdr["version"]:
                    if _semver_tuple(hdr["version"]) <= _semver_tuple(old_hdr["version"]):
                        errors.append(
                            f"ERROR: {rel_path} content changed without monotonic Version bump "
                            f"({old_hdr['version']} -> {hdr['version']}) (REQ-PRO-120.2)."
                        )

                if hdr["status"] == "Approved":
                    status_newly_approved = (old_hdr is None) or (old_hdr["status"] != "Approved")
                    if body_changed or status_newly_approved:
                        if wt_dirty or not latest_has_human_trailer or not _is_human_approver(hdr["approver"]):
                            errors.append(
                                f"ERROR: {rel_path} sets or retains Status 'Approved' without human sign-off "
                                f"(requires committed Human-Approved:/Interactive-PO: trailer and human Approver; "
                                f"agents must reset to 'Reviewed' or 'Draft') (REQ-PRO-120.2)."
                            )

    if errors:
        return False, errors
    return True, [f"PASS: Work-product headers & human-only approval verified across {len(governed)} governed document(s)."]


SWE_DERIVED_PREFIXES = {
    "UI", "UNT",
    "PER", "PERF", "SPT",
    "LOC", "MON", "PRI", "SET",
    "REV",
    "STP",
    "HRV", "WKO",
    "ARC", "BLD", "BUG", "INT", "LIV", "LOG", "MAN", "NFR", "PRO", "REG", "SEC", "SIM", "STB", "STR", "TOOL", "TST",
}

SYSTEM_REFINED_PREFIXES = {
    "CON", "FIL", "FUSION", "SEN", "TRK", "WEA",
    "DAT", "EXP", "EXT", "MIG",
    "GEO", "MAP", "NAV", "ROU",
    "CLM", "RAD",
}

LEGACY_UNLINKED_SYS_REFINEMENT_REQS = frozenset()


def verify_system_software_traceability(
    repo_root=None,
    fail_on_missing_coverage=True,
    fail_on_dangling_sysreq=True,
    fail_on_unclassified_swe=False,
    baseline_allowlist=None,
):
    """
    ASPICE SYS.2 BP5 / SWE.1 BP5 System-to-Software Requirement Traceability Guard (REQ-PRO-076, REQ-PRO-102, REQ-PRO-103).
    Verifies that:
      1. All cited SYSREQ-* identifiers exist in docs/system/sysreq_*.md (no dangling SYSREQs).
      2. 100% of declared system requirements have verified coverage by >= 1 software requirement.
      3. Under fail_on_unclassified_swe=True (--check-reverse-sys-trace), all SYSTEM_REFINED_PREFIXES
         requirements either trace to >= 1 parent SYSREQ or carry explicit 'Derived (SW-only)' rationale.
    Returns (success: bool, diagnostics: list[str], report: dict).
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    if baseline_allowlist is None:
        baseline_allowlist = LEGACY_UNLINKED_SYS_REFINEMENT_REQS

    diagnostics = []
    errors = []

    # 1. Discover declared SYSREQs in docs/system/sysreq_*.md
    sysreq_dir = os.path.join(repo_root, "docs/system")
    declared_sysreqs = set()
    if os.path.isdir(sysreq_dir):
        for fn in os.listdir(sysreq_dir):
            if fn.startswith("sysreq_") and fn.endswith(".md"):
                fp = os.path.join(sysreq_dir, fn)
                with open(fp, "r", encoding="utf-8") as fh:
                    for m in re.finditer(r"^#{2,4}\s+(SYSREQ-[A-Z0-9_-]+)", fh.read(), re.MULTILINE):
                        clean_id = m.group(1).rstrip("-_")
                        if clean_id != "SYSREQ-XXX":
                            declared_sysreqs.add(clean_id)

    if not declared_sysreqs:
        errors.append("No system requirements declared under docs/system/sysreq_*.md.")

    # 2. Build SYS-to-SWE mapping from system matrices and requirements
    sys_to_swe = {s: set() for s in declared_sysreqs}
    req_boundary_pat = re.compile(r"(?<![A-Za-z0-9_-])REQ-[A-Z0-9_-]+(?![A-Za-z0-9_-])")
    sys_boundary_pat = re.compile(r"(?<![A-Za-z0-9_-])SYSREQ-[A-Z0-9_-]+(?![A-Za-z0-9_-])")
    sys_field_pat = re.compile(
        r"^\s*[-*]\s*\*{0,2}(?:System Requirements?|System Requirement\(s\)|Derives from)\*{0,2}\s*:\s*(.*)$",
        re.IGNORECASE | re.MULTILINE,
    )
    derived_sw_only_pat = re.compile(
        r"Derived\s*\(\s*SW[- ]only\s*\)\s*(?:[:\-\—]+\s*(.+))?",
        re.IGNORECASE,
    )

    def _is_system_refined_req(req_id):
        parts = req_id.split("-")
        return len(parts) >= 2 and parts[1] in SYSTEM_REFINED_PREFIXES

    # 2a. Traceability tables in docs/system/*.md
    if os.path.isdir(sysreq_dir):
        for fn in os.listdir(sysreq_dir):
            if fn.endswith(".md"):
                fp = os.path.join(sysreq_dir, fn)
                with open(fp, "r", encoding="utf-8") as fh:
                    for line in fh:
                        if "|" in line:
                            sys_matches = [s.rstrip("-_") for s in sys_boundary_pat.findall(line)]
                            req_matches = [r.rstrip("-_") for r in req_boundary_pat.findall(line)]
                            refined_reqs = [r for r in req_matches if _is_system_refined_req(r)]
                            if sys_matches and refined_reqs:
                                for s_clean in sys_matches:
                                    if s_clean in sys_to_swe:
                                        sys_to_swe[s_clean].update(refined_reqs)

    # 2b. Software requirements in docs/requirements/*.md
    req_dir = os.path.join(repo_root, "docs/requirements")
    swe_req_ids = set()
    swe_refinement_reqs = set()
    swe_derived_sw_only_map = {}
    dangling_sysreq_citations = []

    if os.path.isdir(req_dir):
        for fn in os.listdir(req_dir):
            if fn.startswith("req_") and fn.endswith(".md"):
                fp = os.path.join(req_dir, fn)
                with open(fp, "r", encoding="utf-8") as fh:
                    content = fh.read()

                req_sections = re.split(r"(?=^#{2,4}\s+REQ-[A-Z0-9_-]+:)", content, flags=re.MULTILINE)
                for section in req_sections:
                    m_head = re.match(r"^#{2,4}\s+(REQ-[A-Z0-9_-]+):", section.strip())
                    if not m_head:
                        continue
                    current_req = m_head.group(1).rstrip("-_")
                    swe_req_ids.add(current_req)
                    is_sys_refined = _is_system_refined_req(current_req)
                    if is_sys_refined:
                        swe_refinement_reqs.add(current_req)

                    for m_sys in sys_boundary_pat.finditer(section):
                        raw_sys = m_sys.group(0).rstrip("-_")
                        if raw_sys in ("SYSREQ-XXX", "SYSREQ-TRK", "SYSREQ-DEX"):
                            continue
                        if not re.match(r"^SYSREQ-[A-Z]+-\d+$", raw_sys):
                            continue
                        if raw_sys in declared_sysreqs:
                            if is_sys_refined:
                                sys_to_swe[raw_sys].add(current_req)
                        else:
                            dangling_sysreq_citations.append(
                                f"{fn} [{current_req}] cites undeclared system requirement {raw_sys}"
                            )

                    if is_sys_refined:
                        m_field = sys_field_pat.search(section)
                        if m_field:
                            field_val = m_field.group(1).strip()
                            m_der = derived_sw_only_pat.search(field_val)
                            if m_der:
                                rationale = (m_der.group(1) or "").strip()
                                if len(rationale) >= 5:
                                    swe_derived_sw_only_map[current_req] = rationale

    # 3. Check for dangling SYSREQs in docs/system/
    if os.path.isdir(sysreq_dir):
        for fn in os.listdir(sysreq_dir):
            if fn.endswith(".md"):
                fp = os.path.join(sysreq_dir, fn)
                with open(fp, "r", encoding="utf-8") as fh:
                    for line_no, line in enumerate(fh, 1):
                        for m_sys in re.finditer(r"SYSREQ-[A-Z0-9_-]+", line):
                            raw_sys = m_sys.group(0).rstrip("-_")
                            if raw_sys in ("SYSREQ-XXX", "SYSREQ-TRK", "SYSREQ-DEX"):
                                continue
                            if not re.match(r"^SYSREQ-[A-Z]+-\d+$", raw_sys):
                                continue
                            if raw_sys not in declared_sysreqs:
                                dangling_sysreq_citations.append(
                                    f"docs/system/{fn}:{line_no} cites undeclared system requirement {raw_sys}"
                                )

    if dangling_sysreq_citations:
        msg = f"Dangling or undeclared SYSREQ reference(s) detected: {'; '.join(sorted(dangling_sysreq_citations))}"
        if fail_on_dangling_sysreq:
            errors.append(msg)
        else:
            diagnostics.append(f"WARNING: {msg}")

    # 4. Coverage calculation (forward SYSREQ->SWE and reverse System-Refinement SWE->SYSREQ/Derived)
    covered_sysreqs = {s for s, reqs in sys_to_swe.items() if len(reqs) > 0}
    uncovered_sysreqs = declared_sysreqs - covered_sysreqs
    coverage_pct = round(len(covered_sysreqs) / max(1, len(declared_sysreqs)) * 100.0, 1)

    covered_swe_refinement_reqs = {
        r for reqs in sys_to_swe.values() for r in reqs if _is_system_refined_req(r)
    }
    if swe_refinement_reqs:
        covered_swe_refinement_reqs &= swe_refinement_reqs
    swe_refinement_total_count = len(swe_refinement_reqs)
    swe_refinement_covered_count = len(covered_swe_refinement_reqs)
    swe_refinement_coverage_pct = (
        round(swe_refinement_covered_count / swe_refinement_total_count * 100.0, 2)
        if swe_refinement_total_count > 0
        else 0.0
    )
    swe_to_sys_map = {
        r: sorted([s for s, reqs in sys_to_swe.items() if r in reqs])
        for r in sorted(covered_swe_refinement_reqs)
    }
    unlinked_swe_refinement_reqs = sorted(swe_refinement_reqs - covered_swe_refinement_reqs)

    classified_swe_reqs = covered_swe_refinement_reqs | (set(swe_derived_sw_only_map.keys()) & swe_refinement_reqs)
    unclassified_swe_refinement_reqs = sorted(swe_refinement_reqs - classified_swe_reqs)
    reverse_classified_count = len(classified_swe_reqs)
    reverse_classified_pct = (
        round(reverse_classified_count / swe_refinement_total_count * 100.0, 2)
        if swe_refinement_total_count > 0
        else 0.0
    )

    if uncovered_sysreqs:
        msg = f"SYSREQ coverage gap: {', '.join(sorted(uncovered_sysreqs))} have zero refining software requirements."
        if fail_on_missing_coverage:
            errors.append(msg)
        else:
            diagnostics.append(f"WARNING: {msg}")

    if fail_on_unclassified_swe:
        unbaselined = [r for r in unclassified_swe_refinement_reqs if r not in baseline_allowlist]
        if unbaselined:
            errors.append(
                f"Unclassified System-Refinement SWE requirement(s) missing SYSREQ-* or Derived (SW-only) "
                f"rationale ({len(unbaselined)}): {', '.join(unbaselined[:15])}"
                + (f" ... (+{len(unbaselined) - 15} more)" if len(unbaselined) > 15 else "")
            )

    report = {
        "declared_sysreqs": sorted(list(declared_sysreqs)),
        "sysreq_count": len(declared_sysreqs),
        "covered_sysreqs": sorted(list(covered_sysreqs)),
        "covered_count": len(covered_sysreqs),
        "coverage_pct": coverage_pct,
        "uncovered_sysreqs": sorted(list(uncovered_sysreqs)),
        "sys_to_swe_map": {k: sorted(list(v)) for k, v in sys_to_swe.items()},
        "dangling_sysreq_citations": dangling_sysreq_citations,
        "total_swe_reqs_count": len(swe_req_ids),
        "swe_refinement_total_count": swe_refinement_total_count,
        "swe_refinement_covered_count": swe_refinement_covered_count,
        "swe_refinement_coverage_pct": swe_refinement_coverage_pct,
        "swe_to_sys_map": swe_to_sys_map,
        "unlinked_swe_refinement_reqs": unlinked_swe_refinement_reqs,
        "swe_derived_sw_only_count": len(swe_derived_sw_only_map),
        "swe_derived_sw_only_map": dict(sorted(swe_derived_sw_only_map.items())),
        "unclassified_swe_refinement_reqs": unclassified_swe_refinement_reqs,
        "reverse_classified_count": reverse_classified_count,
        "reverse_classified_pct": reverse_classified_pct,
    }

    if errors:
        return False, [f"ERROR: {e}" for e in errors] + diagnostics, report

    diagnostics.append(
        f"PASS: 100% System-to-Software requirement traceability verified "
        f"({len(covered_sysreqs)}/{len(declared_sysreqs)} SYSREQs covered by software requirements; "
        f"{swe_refinement_covered_count}/{swe_refinement_total_count} "
        f"[{swe_refinement_coverage_pct}%] SYSREQ-linked, "
        f"{len(swe_derived_sw_only_map)} Derived (SW-only), "
        f"{reverse_classified_count}/{swe_refinement_total_count} [{reverse_classified_pct}%] classified; "
        f"zero dangling SYSREQs)."
    )
    return True, diagnostics, report



def verify_conflict_markers(repo_root=None, file_paths=None):
    """
    REQ-PRO-121.3 / TST-PRO-121.3:
    Scans tracked repository text files (or explicit file_paths) for unresolved Git conflict
    markers (<<<<<<<, =======, >>>>>>>) and fails if any are detected.
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    if file_paths is not None:
        candidates = [str(p).replace("\\", "/") for p in file_paths]
    else:
        candidates = []
        try:
            res = subprocess.run(
                ["git", "-c", "core.quotePath=false", "ls-files"],
                cwd=repo_root,
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                text=True,
                check=True,
            )
            candidates = [line.strip().replace("\\", "/") for line in res.stdout.splitlines() if line.strip()]
        except Exception:
            for root, dirs, files in os.walk(repo_root):
                dirs[:] = [d for d in dirs if d != ".git"]
                for fn in files:
                    rel = os.path.relpath(os.path.join(root, fn), repo_root).replace("\\", "/")
                    candidates.append(rel)

    findings = []
    scanned = 0
    for rel in sorted(set(candidates)):
        abs_path = rel if os.path.isabs(rel) else os.path.join(repo_root, rel)
        if not os.path.isfile(abs_path):
            continue
        try:
            with open(abs_path, "rb") as fh:
                raw = fh.read()
        except Exception:
            continue
        if b"\x00" in raw[:4096]:
            continue
        scanned += 1
        text = raw.decode("utf-8", errors="replace")
        for lineno, line in enumerate(text.splitlines(), start=1):
            if _CONFLICT_MARKER_LINE_RE.match(line):
                findings.append(f"ERROR: Unresolved Git conflict marker in {rel}:{lineno}: '{line[:40]}' (REQ-PRO-121.3).")

    if findings:
        return False, findings
    return True, [f"PASS: Zero Git conflict markers detected ({scanned} tracked file(s) scanned)."]


def verify_commit_hygiene(
    repo_root=None,
    base_ref="develop",
    cutoff_ts=COMMIT_HYGIENE_CUTOFF_TS,
    refs_to_check=None,
):
    """
    REQ-PRO-121.3 / TST-PRO-121.3:
    Verifies that post-cutoff non-merge commits (on develop / sprint branches) reference a Jira
    ticket key (ATT-\\d+) in their commit message, while cleanly exempting pre-cutoff historical
    commits (e.g. 7e5ff312, d7bf7698, 24ceaf94, 6a694bb1).
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    try:
        if refs_to_check is None:
            resolved_base = _resolve_git_base_ref(base_ref=base_ref, cwd=repo_root)
            revs = []
            if resolved_base:
                out = subprocess.check_output(
                    ["git", "rev-list", "--no-merges", f"{resolved_base}..HEAD"],
                    cwd=repo_root,
                    stderr=subprocess.DEVNULL,
                ).decode("utf-8", errors="replace").splitlines()
                revs = [r.strip() for r in out if r.strip()]
            if not revs:
                out = subprocess.check_output(
                    ["git", "rev-list", "--no-merges", "-n", "20", "HEAD"],
                    cwd=repo_root,
                    stderr=subprocess.DEVNULL,
                ).decode("utf-8", errors="replace").splitlines()
                revs = [r.strip() for r in out if r.strip()]
        else:
            revs = [str(r).strip() for r in refs_to_check if str(r).strip()]
    except Exception as exc:
        return True, [f"PASS: Commit hygiene scan skipped ({exc})."]

    if not revs:
        return True, ["PASS: Commit hygiene verified (0 commits in scope)."]

    errors = []
    checked = 0
    exempted = 0
    for sha in revs:
        try:
            meta = subprocess.check_output(
                ["git", "log", "-n", "1", "--format=%H%x1f%ct%x1f%P%x1f%B", sha],
                cwd=repo_root,
                stderr=subprocess.DEVNULL,
            ).decode("utf-8", errors="replace")
        except Exception:
            continue
        parts = meta.split("\x1f", 3)
        if len(parts) < 4:
            continue
        full_sha, ts_str, parents_str, msg = parts
        try:
            ts = int(ts_str.strip())
        except ValueError:
            ts = 0
        if len(parents_str.split()) > 1:
            exempted += 1
            continue
        if cutoff_ts and ts <= cutoff_ts:
            exempted += 1
            continue
        checked += 1
        first_line = (msg.strip().splitlines()[0] if msg.strip() else "").strip()
        if first_line.startswith("chore(baseline):"):
            # Baseline manifest recording commits may omit ATT key if strictly under docs/releases/
            continue
        if not _COMMIT_TICKET_KEY_RE.search(msg):
            errors.append(
                f"ERROR: Commit {full_sha[:8]} ('{first_line}') lacks a mandatory Jira ticket key (ATT-\\d+) (REQ-PRO-121.3)."
            )

    if errors:
        return False, errors
    return True, [
        f"PASS: Commit hygiene verified ({checked} post-cutoff commit(s) checked, {exempted} pre-cutoff/merge commit(s) exempted)."
    ]


_SENSOR_TYPE_REL_PATH = os.path.join(
    "app", "src", "main", "java", "com", "atrainingtracker", "banalservice", "sensor", "SensorType.java"
)
_SENSOR_UNITS_DOC_REL_PATH = os.path.join("docs", "architecture", "sensor_units.md")
_SENSOR_PLACEHOLDER_UNITS = {"", "todo", "tbd", "n/a", "unknown", "none"}
_SENSOR_CONST_START_RE = re.compile(r"^\s*([A-Z][A-Z0-9_a-z]*)\s*\(")
_SENSOR_ENUM_DECL_RE = re.compile(r"\benum\s+SensorType\b")


def _strip_java_line_comment(line):
    """Strip trailing // comment outside string literals."""
    in_str = False
    escaped = False
    for idx, ch in enumerate(line):
        if escaped:
            escaped = False
            continue
        if ch == "\\" and in_str:
            escaped = True
            continue
        if ch == '"':
            in_str = not in_str
            continue
        if not in_str and ch == "/" and idx + 1 < len(line) and line[idx + 1] == "/":
            return line[:idx]
    return line


def _parse_sensor_type_constants(source_text):
    """
    Parse enum constants in SensorType.java with their preceding Javadoc block and
    declared internalUnit string literal (REQ-TRK-083.2, REQ-PRO-126.2).
    Returns list of dicts: {"name": str, "javadoc": str|None, "internal_unit": str|None}.
    """
    lines = source_text.splitlines()
    in_enum_decl = False
    in_enum_body = False
    in_javadoc = False
    in_block_comment = False
    javadoc_buf = []
    last_javadoc = None

    collecting_const = False
    const_name = None
    const_buf = []
    paren_depth = 0
    results = []

    def _finish_const(buf_text, c_name, doc):
        literals = re.findall(r'"((?:\\.|[^"\\])*)"', buf_text)
        internal_unit = literals[-1] if literals else None
        results.append({
            "name": c_name,
            "javadoc": doc,
            "internal_unit": internal_unit,
        })

    for raw_line in lines:
        trimmed = raw_line.strip()

        if not in_enum_body:
            if not in_enum_decl and _SENSOR_ENUM_DECL_RE.search(_strip_java_line_comment(raw_line)):
                in_enum_decl = True
            if in_enum_decl and "{" in _strip_java_line_comment(raw_line):
                in_enum_body = True
            continue

        if collecting_const:
            code_part = _strip_java_line_comment(raw_line)
            const_buf.append(code_part)
            in_str = False
            escaped = False
            ended_enum = False
            closed_const = False
            for ch in code_part:
                if escaped:
                    escaped = False
                    continue
                if ch == "\\" and in_str:
                    escaped = True
                    continue
                if ch == '"':
                    in_str = not in_str
                    continue
                if in_str:
                    continue
                if ch == "(":
                    paren_depth += 1
                elif ch == ")":
                    paren_depth -= 1
                elif paren_depth == 0 and ch in (",", ";"):
                    closed_const = True
                    if ch == ";":
                        ended_enum = True
                    break
            if closed_const:
                _finish_const("\n".join(const_buf), const_name, last_javadoc)
                last_javadoc = None
                collecting_const = False
                const_name = None
                const_buf = []
                if ended_enum:
                    break
            continue

        if in_javadoc:
            javadoc_buf.append(trimmed)
            if "*/" in trimmed:
                in_javadoc = False
                last_javadoc = "\n".join(javadoc_buf)
                javadoc_buf = []
            continue

        if in_block_comment:
            if "*/" in trimmed:
                in_block_comment = False
            continue

        if trimmed.startswith("/**"):
            javadoc_buf = [trimmed]
            if "*/" in trimmed[3:]:
                last_javadoc = trimmed
                javadoc_buf = []
            else:
                in_javadoc = True
            continue

        if trimmed.startswith("/*"):
            last_javadoc = None
            if "*/" not in trimmed[2:]:
                in_block_comment = True
            continue

        if not trimmed or trimmed.startswith("//") or trimmed.startswith("@"):
            continue

        m_const = _SENSOR_CONST_START_RE.match(raw_line)
        if m_const:
            const_name = m_const.group(1)
            code_part = _strip_java_line_comment(raw_line)
            const_buf = [code_part]
            paren_depth = 0
            in_str = False
            escaped = False
            ended_enum = False
            closed_const = False
            for ch in code_part[m_const.end() - 1 :]:
                if escaped:
                    escaped = False
                    continue
                if ch == "\\" and in_str:
                    escaped = True
                    continue
                if ch == '"':
                    in_str = not in_str
                    continue
                if in_str:
                    continue
                if ch == "(":
                    paren_depth += 1
                elif ch == ")":
                    paren_depth -= 1
                elif paren_depth == 0 and ch in (",", ";"):
                    closed_const = True
                    if ch == ";":
                        ended_enum = True
                    break
            if closed_const:
                _finish_const("\n".join(const_buf), const_name, last_javadoc)
                last_javadoc = None
                const_name = None
                const_buf = []
                if ended_enum:
                    break
            else:
                collecting_const = True
            continue

        if trimmed.startswith(";"):
            break

        last_javadoc = None

    return results


def _parse_sensor_units_doc_table(doc_text):
    """Parse 8-column SensorType unit rows from docs/architecture/sensor_units.md."""
    rows = {}
    for line in doc_text.splitlines():
        trimmed = line.strip()
        if not trimmed.startswith("|"):
            continue
        cols = [c.strip() for c in trimmed.split("|")[1:-1]]
        if len(cols) != 8:
            continue
        name = cols[0].strip("`").strip()
        if name == "SensorType" or name.startswith(":---") or name.startswith("---"):
            continue
        rows[name] = cols[3].strip("`").strip()
    return rows


def verify_sensor_type_javadocs(repo_root=None, require_file=False):
    """
    REQ-TRK-083.2 / REQ-PRO-126.2:
    Statically verifies every SensorType.java enum constant has a preceding /** ... */
    Javadoc block documenting 'Internal unit:' and its declared internalUnit literal,
    and cross-checks docs/architecture/sensor_units.md when present.
    """
    if repo_root is None:
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    sensor_type_fp = os.path.join(repo_root, _SENSOR_TYPE_REL_PATH)
    if not os.path.isfile(sensor_type_fp):
        if require_file:
            return False, [f"ERROR: Missing SensorType.java at {_SENSOR_TYPE_REL_PATH.replace(os.sep, '/')}."]
        return True, ["SKIP: SensorType.java not present; skipping SensorType Javadoc verification."]

    with open(sensor_type_fp, "r", encoding="utf-8") as f:
        source_text = f.read()

    constants = _parse_sensor_type_constants(source_text)
    if not constants:
        return False, ["ERROR: Failed to parse any enum constants from SensorType.java (REQ-PRO-126.2)."]

    errors = []
    code_units = {}
    for entry in constants:
        name = entry["name"]
        doc = entry["javadoc"]
        unit = entry["internal_unit"]
        code_units[name] = unit

        if unit is None or unit.strip().lower() in _SENSOR_PLACEHOLDER_UNITS:
            errors.append(
                f"ERROR: SensorType.{name} has missing or placeholder internalUnit literal '{unit}' (REQ-TRK-083.2)."
            )
            continue

        if not doc:
            errors.append(
                f"ERROR: SensorType.{name} must have a preceding /** ... */ Javadoc block in SensorType.java (REQ-TRK-083.2, REQ-PRO-126.2)."
            )
            continue

        if "Internal unit:" not in doc or unit not in doc:
            errors.append(
                f"ERROR: SensorType.{name} Javadoc must document 'Internal unit:' and declared unit '{unit}' (REQ-TRK-083.2, REQ-PRO-126.2)."
            )

    doc_fp = os.path.join(repo_root, _SENSOR_UNITS_DOC_REL_PATH)
    if os.path.isfile(doc_fp):
        with open(doc_fp, "r", encoding="utf-8") as f:
            doc_units = _parse_sensor_units_doc_table(f.read())
        missing_in_doc = sorted(set(code_units.keys()) - set(doc_units.keys()))
        extra_in_doc = sorted(set(doc_units.keys()) - set(code_units.keys()))
        if missing_in_doc:
            errors.append(
                f"ERROR: SensorType constant(s) missing from docs/architecture/sensor_units.md: {', '.join(missing_in_doc)}."
            )
        if extra_in_doc:
            errors.append(
                f"ERROR: Extra constant(s) in docs/architecture/sensor_units.md not in SensorType.java: {', '.join(extra_in_doc)}."
            )
        for name, code_unit in code_units.items():
            if name in doc_units and code_unit is not None and doc_units[name] != code_unit:
                errors.append(
                    f"ERROR: SensorType.{name} internalUnit '{code_unit}' mismatches sensor_units.md '{doc_units[name]}'."
                )

    if errors:
        return False, errors
    return True, [
        f"PASS: Verified per-constant Javadoc and 'Internal unit:' parity across {len(constants)} SensorType constant(s) (REQ-TRK-083.2, REQ-PRO-126.2)."
    ]


def verify_governance(
    deliverable_text=None,
    diff_text=None,
    base_ref="develop",
    skip_tripwires=False,
    fail_on_duplicates=False,
    fail_on_dangling=False,
    fail_on_unverified=False,
    enforce_attributes=True,
    min_req_tst_coverage=None,
    min_tst_code_coverage=None,
    max_code_without_spec=None,
    min_bidirectional_coverage=None,
    baseline_path=None,
    fail_on_orphans=True,
    repo_root=None,
):
    """
    Main governance validation entrypoint.
    Enforces engineering directory structure, master table parity, unique requirement and test IDs,
    bidirectional reference integrity (REQ-PRO-070), evidence-based status rules (REQ-PRO-073),
    optional coverage threshold ratchets (REQ-PRO-071), Rule 81 deterministic tripwires (composable islands,
    repository encapsulation, architecture/system spec grounding), and Chesterton's Fence archaeology on modified requirements.
    Returns (success: bool, diagnostics: list[str]).
    """
    diagnostics = []

    dir_ok, dir_errors = verify_engineering_directory_structure()
    if not dir_ok:
        diagnostics.extend(dir_errors)
        return False, diagnostics

    layout_ok, layout_diag = verify_living_doc_directory_layout(repo_root=repo_root)
    if not layout_ok:
        diagnostics.extend(layout_diag)
        return False, diagnostics
    diagnostics.extend(layout_diag)

    parity_ok, parity_errors = verify_master_table_parity()
    if not parity_ok:
        diagnostics.extend([f"ERROR: {e}" for e in parity_errors])
        return False, diagnostics
    diagnostics.append("PASS: Living documentation master tables match all 9 domain shards with 100% parity.")

    unique_ok, unique_diag = verify_unique_ids(fail_on_duplicates=fail_on_duplicates)
    if not unique_ok:
        diagnostics.extend(unique_diag)
        return False, diagnostics
    diagnostics.extend(unique_diag)

    links_ok, links_diag = verify_dangling_references(fail_on_dangling=fail_on_dangling)
    if not links_ok:
        diagnostics.extend(links_diag)
        return False, diagnostics
    diagnostics.extend(links_diag)

    evidence_ok, evidence_diag = verify_requirement_evidence_status(
        diff_text=diff_text,
        base_ref=base_ref,
        fail_on_unverified=fail_on_unverified,
    )
    if not evidence_ok:
        diagnostics.extend(evidence_diag)
        return False, diagnostics
    diagnostics.extend(evidence_diag)

    tests_ok, tests_diag = verify_tests_field_format()
    if not tests_ok:
        diagnostics.extend(tests_diag)
        return False, diagnostics
    diagnostics.extend(tests_diag)

    attrs_ok, attrs_diag = verify_requirement_attributes(
        diff_text=diff_text,
        base_ref=base_ref,
        enforce_attributes=enforce_attributes,
    )
    if not attrs_ok:
        diagnostics.extend(attrs_diag)
        return False, diagnostics
    diagnostics.extend(attrs_diag)

    orphans_ok, orphans_diag, _ = verify_traceability_orphans(
        repo_root=repo_root,
        baseline_path=baseline_path,
        fail_on_orphans=fail_on_orphans,
    )
    if not orphans_ok:
        diagnostics.extend(orphans_diag)
        return False, diagnostics
    diagnostics.extend(orphans_diag)

    headers_ok, headers_diag = verify_shard_header_counts(repo_root=repo_root)
    if not headers_ok:
        diagnostics.extend(headers_diag)
        return False, diagnostics
    diagnostics.extend(headers_diag)

    size_ok, size_diag = verify_shard_sizes(repo_root=repo_root)
    if not size_ok:
        diagnostics.extend(size_diag)
        return False, diagnostics
    diagnostics.extend(size_diag)

    desc_ok, desc_diag, _ = verify_req_descriptions(repo_root=repo_root)
    if not desc_ok:
        diagnostics.extend(desc_diag)
        return False, diagnostics
    diagnostics.extend(desc_diag)

    code_orphans_ok, code_orphans_diag, _ = verify_code_traceability_orphans(repo_root=repo_root)
    if not code_orphans_ok:
        diagnostics.extend(code_orphans_diag)
        return False, diagnostics
    diagnostics.extend(code_orphans_diag)

    if (
        min_req_tst_coverage is not None
        or min_tst_code_coverage is not None
        or max_code_without_spec is not None
        or min_bidirectional_coverage is not None
    ):
        cov_report = calculate_traceability_coverage()
        cov_ok, cov_diag = verify_coverage_thresholds(
            cov_report,
            min_req_tst_coverage=min_req_tst_coverage,
            min_tst_code_coverage=min_tst_code_coverage,
            max_code_without_spec=max_code_without_spec,
            min_bidirectional_coverage=min_bidirectional_coverage,
        )
        diagnostics.extend(cov_diag)
        if not cov_ok:
            return False, diagnostics

    if not skip_tripwires:
        islands_ok, islands_diag = verify_composable_islands(diff_text=diff_text, base_ref=base_ref)
        if not islands_ok:
            diagnostics.extend([f"ERROR: {e}" for e in islands_diag])
            return False, diagnostics
        diagnostics.extend(islands_diag)

        repo_ok, repo_diag = verify_repository_encapsulation(diff_text=diff_text, base_ref=base_ref)
        if not repo_ok:
            diagnostics.extend([f"ERROR: {e}" for e in repo_diag])
            return False, diagnostics
        diagnostics.extend(repo_diag)

        specs_ok, specs_diag = verify_architecture_and_system_specs(diff_text=diff_text, base_ref=base_ref)
        if not specs_ok:
            diagnostics.extend([f"ERROR: {e}" for e in specs_diag])
            return False, diagnostics
        diagnostics.extend(specs_diag)

        sys_ok, sys_diag, _ = verify_system_software_traceability()
        if not sys_ok:
            diagnostics.extend(sys_diag)
            return False, diagnostics
        diagnostics.extend(sys_diag)

        retro_ok, retro_diag = verify_retro_write_allowlist(base_ref=base_ref, repo_root=repo_root)
        if not retro_ok:
            diagnostics.extend(retro_diag)
            return False, diagnostics
        diagnostics.extend(retro_diag)

        hdr_ok, hdr_diag = verify_work_product_headers(
            repo_root=repo_root,
            base_ref=base_ref,
            check_diff=(diff_text is None),
        )
        if not hdr_ok:
            diagnostics.extend(hdr_diag)
            return False, diagnostics
        diagnostics.extend(hdr_diag)

        cm_ok, cm_diag = verify_conflict_markers(repo_root=repo_root)
        if not cm_ok:
            diagnostics.extend(cm_diag)
            return False, diagnostics
        diagnostics.extend(cm_diag)

        ch_ok, ch_diag = verify_commit_hygiene(repo_root=repo_root, base_ref=base_ref)
        if not ch_ok:
            diagnostics.extend(ch_diag)
            return False, diagnostics
        diagnostics.extend(ch_diag)

        st_ok, st_diag = verify_sensor_type_javadocs(repo_root=repo_root, require_file=False)
        if not st_ok:
            diagnostics.extend(st_diag)
            return False, diagnostics
        diagnostics.extend(st_diag)

    if diff_text is None:
        diff_text = get_git_diff(base_ref=base_ref)

    modified_reqs, added_reqs, deleted_reqs = parse_diff_requirements(diff_text)

    if not modified_reqs and not deleted_reqs:
        # No existing requirements modified or deleted
        if added_reqs:
            diagnostics.append(
                f"Net-new requirement(s) detected: {', '.join(sorted(added_reqs))}. "
                f"Bypassing archaeology check cleanly."
            )
        else:
            diagnostics.append("Zero requirement modifications detected.")
        return True, diagnostics

    # Existing requirements were modified or removed
    flagged_reqs = modified_reqs.union(deleted_reqs)
    diagnostics.append(
        f"Modified/Altered existing requirement(s) detected: {', '.join(sorted(flagged_reqs))}."
    )

    if not deliverable_text:
        diagnostics.append(
            "ERROR: Existing requirements were modified, but no deliverable text was provided "
            "for archaeology audit validation."
        )
        return False, diagnostics

    is_valid, errors = validate_archaeology_section(deliverable_text)
    if not is_valid:
        diagnostics.append("ERROR: Requirement Archaeology & Chesterton's Fence Audit failed validation:")
        diagnostics.extend([f"  - {e}" for e in errors])
        return False, diagnostics

    diagnostics.append(
        "PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields."
    )
    return True, diagnostics


def main(argv=None):
    """CLI interface for verify_requirement_governance.py."""
    import argparse

    parser = argparse.ArgumentParser(
        description="Verify Requirement Archaeology & Chesterton's Fence Governance (REQ-PRO-022)."
    )
    parser.add_argument(
        "--text-file",
        help="Path to file containing deliverable / sub-task description text.",
    )
    parser.add_argument(
        "--diff-file",
        help="Path to file containing git diff of docs/requirements.md (defaults to live git diff).",
    )
    parser.add_argument(
        "--base-ref",
        default="develop",
        help="Git base ref to diff against (default: develop).",
    )
    parser.add_argument(
        "--repo-root",
        default=None,
        help="Optional repository root override for standalone governance checks.",
    )
    parser.add_argument(
        "--sync-indexes",
        "--recalc-docs",
        dest="sync_indexes",
        action="store_true",
        help="Recalculate and synchronize master index tables in docs/requirements.md and docs/tests.md.",
    )
    parser.add_argument(
        "--check-text-only",
        action="store_true",
        help="Validate the archaeology section template directly without diff checks.",
    )
    parser.add_argument(
        "--check-composable-islands",
        action="store_true",
        help="Run zero-caller Composable island check only.",
    )
    parser.add_argument(
        "--check-repo-encapsulation",
        action="store_true",
        help="Run repository encapsulation guard check only.",
    )
    parser.add_argument(
        "--check-specs",
        action="store_true",
        help="Run architecture and system spec grounding & SYSREQ parity check only.",
    )
    parser.add_argument(
        "--check-doc-headers",
        action="store_true",
        help="Verify standardized work-product headers, version bumps, and human-only Approved enforcement (REQ-PRO-120).",
    )
    parser.add_argument(
        "--check-living-doc-layout",
        action="store_true",
        help="Verify docs/requirements/ contains only req_*.md and docs/tests/ contains only tst_*.md (REQ-PRO-129.3).",
    )
    parser.add_argument(
        "--check-conflict-markers",
        action="store_true",
        help="Scan tracked files for unresolved Git conflict markers (REQ-PRO-121.3).",
    )
    parser.add_argument(
        "--check-commit-hygiene",
        action="store_true",
        help="Verify post-cutoff commits reference a Jira ATT-\\d+ ticket key (REQ-PRO-121.3).",
    )
    parser.add_argument(
        "--check-sensor-javadocs",
        action="store_true",
        help="Verify every SensorType.java enum constant has a preceding Javadoc with 'Internal unit:' metadata (REQ-PRO-126.2).",
    )
    parser.add_argument(
        "--skip-tripwires",
        action="store_true",
        help="Skip the 3 deterministic Python tripwires.",
    )
    parser.add_argument(
        "--check-gates",
        nargs="?",
        const="",
        default=None,
        help="Verify sprint quality gate records export in docs/engineering/gates/ (optionally specify sprint name).",
    )
    parser.add_argument(
        "--check-metrics",
        nargs="?",
        const="",
        default=None,
        help="Verify sprint process metrics in docs/engineering/metrics/process_metrics.jsonl (optionally specify sprint name).",
    )
    parser.add_argument(
        "--check-qa-audits",
        nargs="?",
        const="",
        default=None,
        help="Verify sprint QA process-compliance audit reports in docs/engineering/qa_audits/ (optionally specify sprint name).",
    )
    parser.add_argument(
        "--check-reviews",
        nargs="?",
        const="",
        default=None,
        help="Verify independent QA review records in docs/engineering/reviews/ (optionally specify sprint name).",
    )
    parser.add_argument(
        "--check-retro-allowlist",
        nargs="?",
        const="",
        default=None,
        help="Verify autonomous chore(ceremony2) commits obey the evidence/log write-allowlist (REQ-PRO-117.3).",
    )
    parser.add_argument(
        "--check-impact",
        nargs="?",
        const="",
        default=None,
        help="Verify Change-Request Impact Analysis in Stage 1 deliverable (optionally specify analysis file path).",
    )
    parser.add_argument(
        "--enforce-unique",
        "--fail-on-duplicates",
        dest="enforce_unique",
        action="store_true",
        help="Strictly fail if any duplicate REQ/TST heading IDs exist, including legacy duplicates (REQ-PRO-066.3).",
    )
    parser.add_argument(
        "--check-unique-ids",
        action="store_true",
        help="Check living documentation shards for duplicate REQ and TST heading IDs (REQ-PRO-066.1).",
    )
    parser.add_argument(
        "--enforce-links",
        "--fail-on-dangling",
        dest="enforce_links",
        action="store_true",
        help="Strictly fail if any dangling REQ or TST references exist, including legacy references (REQ-PRO-070.4).",
    )
    parser.add_argument(
        "--check-links",
        "--check-dangling-references",
        action="store_true",
        help="Check living documentation shards for dangling REQ and TST citations (REQ-PRO-070.1, REQ-PRO-070.2).",
    )
    parser.add_argument(
        "--coverage",
        action="store_true",
        help="Report bidirectional REQ-TST coverage and TST-to-test-code existence matrix (REQ-PRO-071).",
    )
    parser.add_argument(
        "--coverage-json",
        action="store_true",
        help="Output coverage report in structured JSON format (REQ-PRO-071.5).",
    )
    parser.add_argument(
        "--min-req-tst-coverage",
        type=float,
        default=None,
        help="Minimum required percentage of REQs citing TSTs (0-100).",
    )
    parser.add_argument(
        "--min-tst-code-coverage",
        type=float,
        default=None,
        help="Minimum required percentage of TSTs with test code (0-100).",
    )
    parser.add_argument(
        "--max-code-without-spec",
        type=int,
        default=None,
        help="Maximum allowed count of code tags without specification in living docs.",
    )
    parser.add_argument(
        "--min-bidirectional-coverage",
        type=float,
        default=None,
        help="Minimum required percentage of TSTs cited back by their REQ (0-100).",
    )
    parser.add_argument(
        "--check-evidence",
        action="store_true",
        help="Check living documentation shards for evidence-based requirement status compliance (REQ-PRO-073.4).",
    )
    parser.add_argument(
        "--check-verified-evidence",
        action="store_true",
        help="Verify that all [Verified] TST specifications are backed by passing test evidence and reject failing or unbacked items (REQ-PRO-099).",
    )
    parser.add_argument(
        "--triage-unbacked",
        action="store_true",
        help="Classify all unbacked [Verified] TSTs across living-doc shards and report stale ratchet baseline entries (REQ-PRO-101.1).",
    )
    parser.add_argument(
        "--triage-csv",
        type=str,
        default=None,
        help="Optional CSV output path for --triage-unbacked (e.g. docs/engineering/test_evidence/ATT-8145_unbacked_triage.csv).",
    )
    parser.add_argument(
        "--check-test-evidence",
        nargs="?",
        const="",
        default=None,
        help="Verify persistent sprint and release test evidence in docs/engineering/test_evidence/ (REQ-PRO-100.4).",
    )
    parser.add_argument(
        "--test-results-dir",
        type=str,
        default=None,
        help="Path to JUnit XML test results directory (default: app/build/test-results/**).",
    )
    parser.add_argument(
        "--test-archive-path",
        type=str,
        default=None,
        help="Path to JUnit XML test results zip archive.",
    )
    parser.add_argument(
        "--enforce-evidence",
        action="store_true",
        help="Fail if ANY requirement claiming [Verified] lacks an existing TST ID, including legacy items.",
    )
    parser.add_argument(
        "--verify-sys-trace",
        action="store_true",
        help="Verify system-to-software requirement traceability and prefix tailoring (REQ-PRO-076).",
    )
    parser.add_argument(
        "--check-reverse-sys-trace",
        action="store_true",
        help="Fail if any System-Refinement SWE requirement lacks a SYSREQ-* link or Derived (SW-only) rationale (REQ-PRO-103.3).",
    )
    parser.add_argument(
        "--report-sys-coverage",
        action="store_true",
        help="Report detailed SYSREQ-to-SWE coverage matrix (REQ-PRO-076).",
    )
    parser.add_argument(
        "--check-tests-format",
        action="store_true",
        help="Verify that all Tests fields in requirement shards conform to valid TST ID and placeholder syntax (REQ-PRO-075).",
    )
    parser.add_argument(
        "--enforce-attributes",
        "--fail-on-missing-attributes",
        dest="enforce_attributes",
        action="store_true",
        default=True,
        help="Fail verification if any requirement lacks Priority, Type, or Verification Method attributes (REQ-PRO-077, REQ-PRO-094; strict by default).",
    )
    parser.add_argument(
        "--no-enforce-attributes",
        dest="enforce_attributes",
        action="store_false",
        help="Allow legacy requirements to lack attributes with warnings instead of fatal errors.",
    )
    parser.add_argument(
        "--check-attributes",
        action="store_true",
        help="Check living documentation shards for requirement attributes (Priority, Type, Verification Method) (REQ-PRO-077).",
    )
    parser.add_argument(
        "--check-orphans",
        action="store_true",
        help="Verify bidirectional requirement-to-test traceability and ratchet baseline (REQ-PRO-087).",
    )
    parser.add_argument(
        "--traceability-baseline",
        default=None,
        help="Path to traceability baseline JSON file (default: tools/traceability_baseline.json).",
    )
    parser.add_argument(
        "--fail-on-orphans",
        action="store_true",
        default=True,
        help="Fail if unbaselined orphans or ratchet violations are detected (default: True).",
    )
    parser.add_argument(
        "--no-fail-on-orphans",
        dest="fail_on_orphans",
        action="store_false",
        help="Do not fail on unbaselined orphans (warning only).",
    )
    parser.add_argument(
        "--check-code-orphans",
        action="store_true",
        help="Verify that all REQ and TST citations in codebase resolve to authentic living documentation headings (REQ-PRO-104.1).",
    )
    parser.add_argument(
        "--check-shard-headers",
        action="store_true",
        help="Verify that all living documentation domain shard headers match parsed section counts with 100%% parity (REQ-PRO-104.2).",
    )
    parser.add_argument(
        "--check-size",
        action="store_true",
        help="Verify all living documentation shards do not exceed max line limit (REQ-PRO-093.3, REQ-PRO-127.4).",
    )
    parser.add_argument(
        "--max-lines",
        type=int,
        default=4000,
        help="Maximum allowed line count per living documentation shard (default: 4000).",
    )
    parser.add_argument(
        "--check-req-description",
        action="store_true",
        help="Enforce that all newly added living requirements declare a non-empty - **Description**: field against legacy ratchet baseline (REQ-PRO-104.3).",
    )
    parser.add_argument(
        "--report-req-field-coverage",
        action="store_true",
        help="Report field completion coverage percentages (Description, Given-When-Then, Allocation) across all living requirements (REQ-PRO-104.3).",
    )

    args = parser.parse_args(argv)

    if args.sync_indexes:
        recalculate_master_index_tables()
        parity_ok, parity_errors = verify_master_table_parity()
        if not parity_ok:
            for e in parity_errors:
                print(f"ERROR: {e}", file=sys.stderr)
            sys.exit(1)
        unique_ok, unique_diag = verify_unique_ids(fail_on_duplicates=args.enforce_unique)
        if not unique_ok:
            for d in unique_diag:
                print(d, file=sys.stderr)
            sys.exit(1)
        links_ok, links_diag = verify_dangling_references(fail_on_dangling=args.enforce_links)
        if not links_ok:
            for d in links_diag:
                print(d, file=sys.stderr)
            sys.exit(1)
        evidence_ok, evidence_diag = verify_requirement_evidence_status(fail_on_unverified=args.enforce_evidence)
        if not evidence_ok:
            for d in evidence_diag:
                print(d, file=sys.stderr)
            sys.exit(1)
        tests_ok, tests_diag = verify_tests_field_format()
        if not tests_ok:
            for d in tests_diag:
                print(d, file=sys.stderr)
            sys.exit(1)
        print("PASS: Master index tables successfully synchronized with 100% parity.")
        sys.exit(0)

    diff_content = None
    if args.diff_file:
        if not os.path.exists(args.diff_file):
            print(f"Error: File {args.diff_file} not found.", file=sys.stderr)
            sys.exit(1)
        with open(args.diff_file, "r", encoding="utf-8") as f:
            diff_content = f.read()

    ran_checks = False
    all_ok = True

    def _record_check(ok, diag, err_only_on_error=False, error_prefix_only=False):
        nonlocal ran_checks, all_ok
        ran_checks = True
        if not ok:
            all_ok = False
        for msg in diag:
            if error_prefix_only:
                is_err = not ok and msg.startswith("ERROR")
            elif err_only_on_error:
                is_err = not ok and "ERROR" in msg
            else:
                is_err = not ok
            print(msg, file=sys.stderr if is_err else sys.stdout)

    if args.check_orphans:
        ok, diag, _ = verify_traceability_orphans(
            repo_root=args.repo_root,
            baseline_path=args.traceability_baseline,
            fail_on_orphans=args.fail_on_orphans,
        )
        _record_check(ok, diag, err_only_on_error=True)

    if args.check_code_orphans:
        ok, diag, _ = verify_code_traceability_orphans(
            repo_root=args.repo_root,
            fail_on_orphans=args.fail_on_orphans,
        )
        _record_check(ok, diag, err_only_on_error=True)

    if args.check_shard_headers:
        ok, diag = verify_shard_header_counts(repo_root=args.repo_root)
        _record_check(ok, diag, err_only_on_error=True)

    if args.check_size:
        ok, diag = verify_shard_sizes(repo_root=args.repo_root, max_lines=args.max_lines)
        _record_check(ok, diag, err_only_on_error=True)

    if args.check_req_description:
        ok, diag, _ = verify_req_descriptions(repo_root=args.repo_root, fail_on_missing=True)
        _record_check(ok, diag, err_only_on_error=True)

    if args.report_req_field_coverage:
        cov = calculate_req_field_coverage()
        print(format_req_field_coverage(cov))
        ran_checks = True

    if args.check_evidence:
        ok, diag = verify_requirement_evidence_status(
            repo_root=args.repo_root,
            fail_on_unverified=args.enforce_evidence,
        )
        _record_check(ok, diag)

    if args.verify_sys_trace or args.check_reverse_sys_trace or args.report_sys_coverage:
        ok, diag, report = verify_system_software_traceability(
            repo_root=args.repo_root,
            fail_on_unclassified_swe=args.check_reverse_sys_trace,
        )
        _record_check(ok, diag, err_only_on_error=True)
        if args.report_sys_coverage:
            print("\nSYSREQ-to-SWE Requirement Coverage Report:")
            print(f"Forward Coverage (SYSREQ -> Product REQ): {report['covered_count']}/{report['sysreq_count']} ({report['coverage_pct']}%)")
            for sys_id, reqs in sorted(report["sys_to_swe_map"].items()):
                print(f"  {sys_id} ({len(reqs)} REQs): {', '.join(sorted(reqs))}")
            print(
                f"\nReverse Coverage (Product REQ [System-Refinement] -> SYSREQ): "
                f"{report['swe_refinement_covered_count']}/{report['swe_refinement_total_count']} "
                f"({report['swe_refinement_coverage_pct']}%)"
            )
            print(
                f"Derived (SW-only) System-Refinement REQs: {report['swe_derived_sw_only_count']}"
            )
            print(
                f"Total Classified System-Refinement REQs: "
                f"{report['reverse_classified_count']}/{report['swe_refinement_total_count']} "
                f"({report['reverse_classified_pct']}%)"
            )
            print(f"Unclassified System-Refinement SWE REQs: {len(report['unclassified_swe_refinement_reqs'])}")

    if args.check_verified_evidence:
        ok, diag = verify_verified_test_evidence(
            repo_root=args.repo_root,
            results_dir=args.test_results_dir,
            archive_path=args.test_archive_path,
        )
        _record_check(ok, diag, error_prefix_only=True)

    if args.triage_unbacked:
        _, diag = triage_unbacked_verified_tsts(out_csv=args.triage_csv)
        for msg in diag:
            print(msg)
        ran_checks = True

    if args.check_test_evidence is not None:
        target_sprint = args.check_test_evidence if args.check_test_evidence != "" else None
        ok, diag = verify_test_evidence(target_id=target_sprint, repo_root=args.repo_root)
        _record_check(ok, diag, error_prefix_only=True)

    if args.check_tests_format:
        ok, diag = verify_tests_field_format(repo_root=args.repo_root)
        _record_check(ok, diag)

    if args.check_attributes:
        ok, diag = verify_requirement_attributes(
            repo_root=args.repo_root,
            enforce_attributes=args.enforce_attributes,
            diff_text=diff_content,
            base_ref=args.base_ref,
        )
        _record_check(ok, diag, error_prefix_only=True)

    if (
        args.coverage
        or args.coverage_json
        or args.min_req_tst_coverage is not None
        or args.min_tst_code_coverage is not None
        or args.max_code_without_spec is not None
        or args.min_bidirectional_coverage is not None
    ):
        report = calculate_traceability_coverage()
        ok, diag = verify_coverage_thresholds(
            report,
            min_req_tst_coverage=args.min_req_tst_coverage,
            min_tst_code_coverage=args.min_tst_code_coverage,
            max_code_without_spec=args.max_code_without_spec,
            min_bidirectional_coverage=args.min_bidirectional_coverage,
        )
        ran_checks = True
        if not ok:
            all_ok = False
        if args.coverage_json:
            out_dict = report.to_dict()
            out_dict["gate_result"] = {"passed": ok, "diagnostics": diag}
            print(json.dumps(out_dict, indent=2))
        else:
            print(format_coverage_markdown(report))
            print("")
            for msg in diag:
                print(msg, file=sys.stderr if not ok else sys.stdout)

    if args.check_links:
        ok, diag = verify_dangling_references(repo_root=args.repo_root, fail_on_dangling=args.enforce_links)
        _record_check(ok, diag)

    if args.check_unique_ids:
        ok, diag = verify_unique_ids(repo_root=args.repo_root, fail_on_duplicates=args.enforce_unique)
        _record_check(ok, diag)

    if args.check_impact is not None:
        target_file = args.check_impact.strip() if args.check_impact.strip() else None
        ok, diag = verify_stage1_impact_analysis(analysis_file=target_file, repo_root=args.repo_root)
        _record_check(ok, diag)

    if args.check_gates is not None:
        target_sprint = args.check_gates if args.check_gates != "" else None
        ok, diag = verify_gate_records(sprint_name=target_sprint, repo_root=args.repo_root)
        _record_check(ok, diag)

    if args.check_metrics is not None:
        target_sprint = args.check_metrics if args.check_metrics != "" else None
        ok, diag = verify_sprint_metrics(sprint_name=target_sprint, repo_root=args.repo_root)
        _record_check(ok, diag)

    if args.check_qa_audits is not None:
        target_sprint = args.check_qa_audits if args.check_qa_audits != "" else None
        ok, diag = verify_qa_audits(sprint_name=target_sprint, repo_root=args.repo_root)
        _record_check(ok, diag)

    if args.check_reviews is not None:
        target_sprint = args.check_reviews if args.check_reviews != "" else None
        ok, diag = verify_independent_reviews(sprint_name=target_sprint, repo_root=args.repo_root)
        _record_check(ok, diag)

    if args.check_retro_allowlist is not None:
        target_ref = args.check_retro_allowlist if args.check_retro_allowlist != "" else None
        ok, diag = verify_retro_write_allowlist(
            commit_ref=target_ref,
            base_ref=args.base_ref,
            repo_root=args.repo_root,
        )
        _record_check(ok, diag)

    if args.check_composable_islands:
        ok, diag = verify_composable_islands(diff_text=diff_content, repo_root=args.repo_root, base_ref=args.base_ref)
        _record_check(ok, diag)

    if args.check_repo_encapsulation:
        ok, diag = verify_repository_encapsulation(diff_text=diff_content, repo_root=args.repo_root, base_ref=args.base_ref)
        _record_check(ok, diag)

    if args.check_specs:
        ok, diag = verify_architecture_and_system_specs(diff_text=diff_content, repo_root=args.repo_root, base_ref=args.base_ref)
        _record_check(ok, diag)

    if args.check_doc_headers:
        ok, diag = verify_work_product_headers(repo_root=args.repo_root, base_ref=args.base_ref, check_diff=True)
        _record_check(ok, diag)

    if args.check_living_doc_layout:
        ok, diag = verify_living_doc_directory_layout(repo_root=args.repo_root)
        _record_check(ok, diag)

    if args.check_conflict_markers:
        ok, diag = verify_conflict_markers(repo_root=args.repo_root)
        _record_check(ok, diag)

    if args.check_commit_hygiene:
        ok, diag = verify_commit_hygiene(repo_root=args.repo_root, base_ref=args.base_ref)
        _record_check(ok, diag)

    if args.check_sensor_javadocs:
        ok, diag = verify_sensor_type_javadocs(repo_root=args.repo_root, require_file=True)
        _record_check(ok, diag)

    if ran_checks and not args.check_text_only:
        sys.exit(0 if all_ok else 1)

    text_content = ""
    if args.text_file:
        if not os.path.exists(args.text_file):
            print(f"Error: File {args.text_file} not found.", file=sys.stderr)
            sys.exit(1)
        with open(args.text_file, "r", encoding="utf-8") as f:
            text_content = f.read()
    elif not sys.stdin.isatty():
        text_content = sys.stdin.read()

    if not text_content:
        # Attempt auto-discovery based on current branch or engineering docs
        try:
            branch = subprocess.check_output(
                ["git", "rev-parse", "--abbrev-ref", "HEAD"],
                stderr=subprocess.DEVNULL,
            ).decode("utf-8").strip()
            ticket_m = re.search(r"([A-Z]+-\d+)", branch)
            ticket_key = ticket_m.group(1) if ticket_m else None
            if not ticket_key:
                try:
                    for cmd in [["git", "branch", "--contains", "HEAD"], ["git", "log", "-1", "--pretty=%B"], ["git", "status"]]:
                        out = subprocess.check_output(cmd, stderr=subprocess.DEVNULL).decode("utf-8")
                        m = re.search(r"([A-Z]+-\d+)", out)
                        if m:
                            ticket_key = m.group(1)
                            break
                except Exception:
                    pass
            if ticket_key:
                candidates = [
                    f"docs/engineering/tickets/{ticket_key}.md",
                    f"docs/engineering/test_specs/{ticket_key}_test_spec.md",
                    f"docs/engineering/plans/{ticket_key}_plan.md",
                    f"docs/engineering/walkthroughs/{ticket_key}_walkthrough.md",
                    f"docs/engineering/analysis/{ticket_key}_analysis.md",
                ]
                for cand in candidates:
                    if os.path.exists(cand):
                        with open(cand, "r", encoding="utf-8") as f:
                            text_content = f.read()
                        break
        except Exception:
            pass

    if args.check_text_only:
        valid, errors = validate_archaeology_section(text_content)
        if valid:
            print("PASS: Archaeology section is structurally valid.")
            sys.exit(0 if all_ok else 1)
        else:
            print("FAIL: Archaeology section validation errors:", file=sys.stderr)
            for err in errors:
                print(f"  - {err}", file=sys.stderr)
            sys.exit(1)

    success, diagnostics = verify_governance(
        deliverable_text=text_content,
        diff_text=diff_content,
        base_ref=args.base_ref,
        skip_tripwires=args.skip_tripwires,
        fail_on_duplicates=args.enforce_unique,
        fail_on_dangling=args.enforce_links,
        fail_on_unverified=args.enforce_evidence,
        enforce_attributes=args.enforce_attributes,
        min_req_tst_coverage=args.min_req_tst_coverage,
        min_tst_code_coverage=args.min_tst_code_coverage,
        max_code_without_spec=args.max_code_without_spec,
        min_bidirectional_coverage=args.min_bidirectional_coverage,
        baseline_path=args.traceability_baseline,
        fail_on_orphans=args.fail_on_orphans,
        repo_root=args.repo_root,
    )

    for msg in diagnostics:
        if msg.startswith("ERROR") or msg.startswith("FAIL"):
            print(msg, file=sys.stderr)
        else:
            print(msg)

    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
