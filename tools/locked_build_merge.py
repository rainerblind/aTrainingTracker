#!/usr/bin/env python3
"""
locked_build_merge.py

Wrapper providing POSIX flock-based mutual exclusion (REQ-PRO-041.2, TST-PRO-041.2).
Thin compatibility alias to parallel_sprint_lock.py.
"""

from parallel_sprint_lock import *

if __name__ == "__main__":
    main()
