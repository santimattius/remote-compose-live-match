import pathlib
import re
import runpy
import unittest

SCRIPTS_DIR = pathlib.Path(__file__).parent
validate_pr = runpy.run_path(str(SCRIPTS_DIR / "validate_pr.py"))
TEMPLATE = (SCRIPTS_DIR.parent / "PULL_REQUEST_TEMPLATE.md").read_text(encoding="utf-8")
COMMITS = [{"commit": {"message": "feat(contract): add example"}}]


def complete_body(issue_number=1):
    body = TEMPLATE.replace(
        "Closes #<!-- approved issue number -->", f"Closes #{issue_number}"
    ).replace("- [ ] New feature", "- [x] New feature")
    body = re.sub(r"- \[ \](?! .*\(`type:)", "- [x]", body)
    body = re.sub(r"<!--.*?-->", "complete", body, flags=re.DOTALL)
    return (
        body.replace("path/to/file", "contract/src/main.kt")
        .replace("Describe the reviewable behavior change.", "Add the contract.")
        .replace("#NNN", "#2")
    )


def valid_event(branch="feat/wu12-example", issue_number=1):
    return {
        "pull_request": {
            "number": 2,
            "body": complete_body(issue_number),
            "additions": 80,
            "deletions": 20,
            "head": {"ref": branch},
            "labels": [{"name": "type:feature"}],
        },
        "repository": {"full_name": "owner/repository"},
    }


def approved_issue(number=1):
    return {
        "number": number,
        "state": "open",
        "labels": [{"name": "status:approved"}],
    }


class ValidatePullRequestTest(unittest.TestCase):
    def test_accepts_complete_wu12_feature(self):
        self.assertEqual([], validate_pr["validate"](valid_event(), approved_issue(), COMMITS))

    def test_accepts_other_approved_issue_for_unrelated_branch(self):
        event = valid_event("ci/pr-governance-bootstrap", issue_number=2)
        self.assertEqual([], validate_pr["validate"](event, approved_issue(2), COMMITS))

    def test_requires_issue_one_for_wu12_chain(self):
        event = valid_event(issue_number=99)
        errors = validate_pr["validate"](event, approved_issue(99), COMMITS)
        self.assertIn("WU12 pull requests must close approved issue #1", errors)

    def test_rejects_any_additional_type_label(self):
        event = valid_event()
        event["pull_request"]["labels"].append({"name": "type:security"})
        errors = validate_pr["validate"](event, approved_issue(), COMMITS)
        self.assertIn("pull request must have exactly one type:* label", errors)

    def test_requires_feature_type_for_wu12_chain(self):
        event = valid_event()
        event["pull_request"]["labels"] = [{"name": "type:bug"}]
        event["pull_request"]["body"] = (
            event["pull_request"]["body"]
            .replace("- [x] New feature", "- [ ] New feature")
            .replace("- [ ] Bug fix", "- [x] Bug fix")
        )
        errors = validate_pr["validate"](event, approved_issue(), COMMITS)
        self.assertIn("WU12 pull requests must use type:feature", errors)

    def test_rejects_empty_summary_changes_and_scope_values(self):
        body = complete_body()
        body = re.sub(r"## Summary.*?## Changes", "## Summary\n\n## Changes", body, flags=re.DOTALL)
        body = re.sub(
            r"## Changes.*?## Test plan", "## Changes\n\n## Test plan", body, flags=re.DOTALL
        ).replace("- Includes: complete", "- Includes:").replace(
            "- Excludes: complete", "- Excludes:"
        )
        event = valid_event()
        event["pull_request"]["body"] = body
        errors = validate_pr["validate"](event, approved_issue(), COMMITS)
        self.assertIn("pull request body is incomplete or still contains placeholders", errors)

    def test_rejects_contributor_rows_moved_outside_checklist(self):
        event = valid_event()
        prefix, _, contributor = event["pull_request"]["body"].partition(
            "## Contributor checklist"
        )
        rows = "\n".join(f"- [x] Arbitrary row {index}." for index in range(7))
        event["pull_request"]["body"] = (
            f"{prefix}{contributor}\n## Contributor checklist\n\n{rows}\n"
        )
        errors = validate_pr["validate"](event, approved_issue(), COMMITS)
        self.assertIn("pull request body is incomplete or still contains placeholders", errors)

    def test_rejects_invalid_governance_and_review_budget(self):
        event = valid_event()
        event["pull_request"].update(
            body=complete_body().replace("- [x] New feature", "- [ ] New feature"),
            additions=401,
            deletions=0,
            head={"ref": "feature/Invalid Branch"},
            labels=[{"name": "type:feature"}, {"name": "type:bug"}],
        )
        commits = [{"commit": {"message": "not conventional\n\nCo-Authored-By: Bot <bot@example.com>"}}]
        errors = validate_pr["validate"](
            event, {"number": 1, "state": "closed", "labels": []}, commits
        )
        expected = (
            "linked issue #1 must be open and carry status:approved",
            "pull request must have exactly one type:* label",
            "exactly one PR type checkbox must be selected",
            "head branch does not follow the repository naming convention",
            "authored text-line budget is 401/400",
            "commit 1 subject is not Conventional Commits: not conventional",
            "commit 1 contains a Co-Authored-By trailer",
        )
        for message in expected:
            self.assertIn(message, errors)
