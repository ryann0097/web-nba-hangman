"""
tools/quality_gate.py
Reads quality_policy.yml and target/quality_report.json, enforces limits.
Exit code 1 if any metric violates the policy.
"""

import json
import sys
import yaml
from pathlib import Path


def main():
    policy_path = Path("quality_policy.yml")
    report_path = Path("target/quality_report.json")

    if not policy_path.exists():
        print("[quality_gate] FAIL: quality_policy.yml not found")
        sys.exit(1)
    if not report_path.exists():
        print("[quality_gate] FAIL: target/quality_report.json not found")
        sys.exit(1)

    with open(policy_path, "r") as f:
        policy = yaml.safe_load(f)["quality_gate"]
    with open(report_path, "r") as f:
        report = json.load(f)

    passed = True
    cov = report.get("coverage_metrics", {})
    smells = report.get("code_smells_checkstyle", {})
    duplication = report.get("duplication_cpd", {})
    pmd = report.get("pmd_violations", {})

    checks = [
        ("instruction_coverage_pct",
         cov.get("instruction_coverage_pct", 0),
         policy["coverage"]["min_instruction_pct"],
         ">="),
        ("branch_coverage_pct",
         cov.get("branch_coverage_pct", 0),
         policy["coverage"]["min_branch_pct"],
         ">="),
        ("duplicated_blocks",
         duplication.get("total_duplications", 0),
         policy["duplication"]["max_duplicated_blocks"],
         "<="),
    ]

    for name, actual, limit, op in checks:
        ok = actual >= limit if op == ">=" else actual <= limit
        status = "PASS" if ok else "FAIL"
        if not ok:
            passed = False
        print(f"  [{status}] {name}: {actual} (limit: {op} {limit})")

    print()
    if passed:
        print("[quality_gate] All checks PASSED")
    else:
        print("[quality_gate] Some checks FAILED — build blocked")
        sys.exit(1)


if __name__ == "__main__":
    main()
