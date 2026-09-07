#!/usr/bin/env python3
"""Print failing/erroring testcases from Gradle JUnit XML reports (one section per failure)."""
import glob
import sys
import xml.etree.ElementTree as ET

root = sys.argv[1] if len(sys.argv) > 1 else "app/build/test-results"
found = 0
for x in sorted(glob.glob(root + "/**/*.xml", recursive=True)):
    try:
        r = ET.parse(x).getroot()
    except Exception:
        continue
    for tc in r.iter("testcase"):
        for tag in ("failure", "error"):
            n = tc.find(tag)
            if n is None:
                continue
            found += 1
            msg = (n.get("message") or n.text or "").strip()
            print("--- " + str(tc.get("classname")) + " :: " + str(tc.get("name")))
            print(msg[:700])
            print()
print("total failing/erroring cases: " + str(found))
