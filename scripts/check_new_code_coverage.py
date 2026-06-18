#!/usr/bin/env python3
"""Compute test coverage on NEW code (lines added vs a base branch) from a JaCoCo report.

Mirrors SonarCloud's "Coverage on New Code" metric: counts both line and
condition (branch) coverage on the lines this branch added under <module>/src/main.

Usage:
    python3 check_new_code_coverage.py [--base master] [--module wallet_system] [--threshold 80]

Prerequisite: the JaCoCo XML report must already exist, i.e. run first:
    (cd <module> && ./mvnw test jacoco:report)

Exits 0 if coverage >= threshold, 1 otherwise (so it can gate a commit/push).
"""
import argparse, os, re, subprocess, sys
import xml.etree.ElementTree as ET


def added_main_lines(base, module):
    """Return {basename.java: set(line numbers added vs base)} under module/src/main."""
    diff = subprocess.run(
        ["git", "diff", f"{base}...HEAD", "--unified=0", "--", f"{module}/src/main"],
        capture_output=True, text=True,
    ).stdout
    added, cur = {}, None
    for line in diff.splitlines():
        if line.startswith("+++ b/"):
            p = line[6:]
            cur = os.path.basename(p) if p.endswith(".java") else None
        elif line.startswith("@@") and cur:
            m = re.search(r"\+(\d+)(?:,(\d+))?", line)
            if m:
                start, cnt = int(m.group(1)), int(m.group(2) or 1)
                added.setdefault(cur, set()).update(range(start, start + cnt))
    return added


def line_coverage(report_path):
    """Return {basename.java: {lineno: (ci, mi, cb, mb)}} from jacoco.xml."""
    tree = ET.parse(report_path)
    cov = {}
    for sf in tree.iter("sourcefile"):
        cov[sf.get("name")] = {
            int(l.get("nr")): (
                int(l.get("ci")), int(l.get("mi")),
                int(l.get("cb", 0)), int(l.get("mb", 0)),
            )
            for l in sf.findall("line")
        }
    return cov


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="master")
    ap.add_argument("--module", default="wallet_system")
    ap.add_argument("--threshold", type=float, default=80.0)
    args = ap.parse_args()

    report = f"{args.module}/target/site/jacoco/jacoco.xml"
    if not os.path.exists(report):
        print(f"ERROR: {report} not found. Run: (cd {args.module} && ./mvnw test jacoco:report)")
        return 2

    added = added_main_lines(args.base, args.module)
    cov = line_coverage(report)
    if not added:
        print(f"No added main lines vs {args.base}; nothing to gate.")
        return 0

    lines_total = lines_cov = br_total = br_cov = 0
    gaps = []
    for fn in sorted(added):
        c = cov.get(fn, {})
        miss, part = [], []
        for ln in sorted(added[fn]):
            if ln not in c:
                continue  # blank/comment/import — not a coverable line
            ci, mi, cb, mb = c[ln]
            lines_total += 1
            lines_cov += 1 if ci > 0 else 0
            br_total += cb + mb
            br_cov += cb
            if ci == 0 and mi > 0:
                miss.append(ln)
            elif mb > 0:
                part.append(f"{ln}({cb}/{cb + mb}br)")
        if miss or part:
            gaps.append(f"  {fn}: uncovered={miss or '-'} partial-branches={part or '-'}")

    combined_total = lines_total + br_total
    combined_cov = lines_cov + br_cov
    pct = 100 * combined_cov / combined_total if combined_total else 100.0

    if gaps:
        print("Uncovered / partially-covered new lines:")
        print("\n".join(gaps))
    print(f"\nNew-code line coverage:   {lines_cov}/{lines_total}"
          f" = {100 * lines_cov / lines_total:.1f}%" if lines_total else "no lines")
    if br_total:
        print(f"New-code branch coverage: {br_cov}/{br_total} = {100 * br_cov / br_total:.1f}%")
    print(f"New-code coverage (combined, ~Sonar): {combined_cov}/{combined_total} = {pct:.1f}%")
    print(f"Threshold: {args.threshold:.1f}%  ->  {'PASS' if pct >= args.threshold else 'FAIL'}")
    return 0 if pct >= args.threshold else 1


if __name__ == "__main__":
    sys.exit(main())