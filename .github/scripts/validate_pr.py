#!/usr/bin/env python3
"""Validate repository pull-request governance from a GitHub event payload."""

# pi-lens-ignore: I001 -- active LSP scan confirms this block is sorted.
import http.client
import json
import os
import re
import sys


ISSUE_REFERENCE = re.compile(r"(?im)\b(?:closes|fixes|resolves)\s+#(\d+)\b")
BRANCH_NAME = re.compile(
    r"^(?:feat|fix|chore|docs|style|refactor|perf|test|build|ci|revert)/[a-z0-9._-]+$"
)
CONVENTIONAL_COMMIT = re.compile(
    r"^(?:build|chore|ci|docs|feat|fix|perf|refactor|revert|style|test)"
    r"(?:\([a-z0-9._-]+\))?!?: .+"
)
TYPE_CHECKBOX = re.compile(r"(?im)^- \[x\] .+?\(`(type:[^`]+)`\)\s*$")
TYPE_LABELS = {
    "type:bug",
    "type:feature",
    "type:docs",
    "type:refactor",
    "type:chore",
    "type:breaking-change",
}
REQUIRED_HEADINGS = (
    "## Linked issue",
    "## PR type",
    "## Summary",
    "## Changes",
    "## Test plan",
    "## Chain context",
    "### Chain overview",
    "### Scope",
    "### Autonomy",
    "## Contributor checklist",
)
AUTONOMY_PATTERNS = (
    r"^- \[x\] CI is expected to pass for this PR branch\.$",
    r"^- \[x\] This PR has one deliverable scope\.$",
    r"^- \[x\] This PR can be rolled back without unrelated changes\.$",
    r"^- \[x\] Tests, docs, or manual verification cover this unit\.$",
)
CONTRIBUTOR_PATTERNS = (
    r"^- \[x\] Linked an issue carrying `status:approved`\.$",
    r"^- \[x\] Added exactly one `type:\*` label\.$",
    r"^- \[x\] Ran `shellcheck` for every modified shell script, or none changed\.$",
    r"^- \[x\] Tested affected behavior in its target environment\.$",
    r"^- \[x\] Updated documentation when behavior changed(?:\.|, .+\.)$",
    r"^- \[x\] Used Conventional Commit messages\.$",
    r"^- \[x\] Added no `Co-Authored-By` trailers\.$",
)
REQUIRED_CHAIN_FIELDS = (
    "Chain",
    "Tracker PR",
    "Position",
    "Base",
    "Depends on",
    "Follow-up",
    "Review budget",
    "Starts at",
    "Ends with",
)


def label_names(item):
    return {label["name"] for label in item.get("labels", [])}


def validate(event, issue, commits):
    pull_request = event["pull_request"]
    body = pull_request.get("body") or ""
    head_ref = pull_request.get("head", {}).get("ref", "")
    errors = []

    issue_references = ISSUE_REFERENCE.findall(body)
    if len(issue_references) != 1:
        errors.append("body must contain exactly one closing issue reference")
    else:
        issue_number = issue_references[0]
        approved = "status:approved" in label_names(issue)
        if (
            str(issue.get("number")) != issue_number
            or issue.get("state") != "open"
            or not approved
        ):
            errors.append(
                f"linked issue #{issue_number} must be open and carry status:approved"
            )
        if head_ref.startswith("feat/wu12-") and issue_number != "1":
            errors.append("WU12 pull requests must close approved issue #1")

    pr_type_labels = {
        name for name in label_names(pull_request) if name.startswith("type:")
    }
    if len(pr_type_labels) != 1:
        errors.append("pull request must have exactly one type:* label")

    checked_types = TYPE_CHECKBOX.findall(body)
    if len(checked_types) != 1:
        errors.append("exactly one PR type checkbox must be selected")
    elif checked_types[0] not in TYPE_LABELS:
        errors.append("checked PR type is unsupported")
    elif pr_type_labels and checked_types[0] not in pr_type_labels:
        errors.append("checked PR type must match the type:* label")
    if head_ref.startswith("feat/wu12-") and (
        pr_type_labels != {"type:feature"} or checked_types != ["type:feature"]
    ):
        errors.append("WU12 pull requests must use type:feature")

    missing_headings = [heading for heading in REQUIRED_HEADINGS if heading not in body]
    summary = body.partition("## Summary")[2].partition("## Changes")[0]
    changes = body.partition("## Changes")[2].partition("## Test plan")[0]
    test_plan = body.partition("## Test plan")[2].partition("## Chain context")[0]
    autonomy = body.partition("### Autonomy")[2].partition("## Contributor checklist")[0]
    contributor = body.partition("## Contributor checklist")[2]
    required_content_complete = all(
        (
            re.search(r"(?m)^- \S.+$", summary),
            re.search(r"(?m)^\| `[^`]+` \| \S.+ \|$", changes),
            re.search(r"(?m)^- Includes: \S.+$", body),
            re.search(r"(?m)^- Excludes: \S.+$", body),
        )
    )
    required_checkboxes_complete = (
        len(re.findall(r"(?m)^- \[x\] .+", test_plan)) >= 3
        and all(re.search(pattern, autonomy, re.MULTILINE) for pattern in AUTONOMY_PATTERNS)
        and all(
            re.search(pattern, contributor, re.MULTILINE)
            for pattern in CONTRIBUTOR_PATTERNS
        )
    )
    missing_chain_fields = [
        field
        for field in REQUIRED_CHAIN_FIELDS
        if not re.search(rf"(?m)^\| {re.escape(field)} \| \S.+ \|$", body)
    ]
    placeholders = ("<!--", "#NNN", "path/to/file")
    required_scope = ("- Includes:", "- Excludes:")
    incomplete = (
        missing_headings
        or not required_content_complete
        or not required_checkboxes_complete
        or missing_chain_fields
        or any(placeholder in body for placeholder in placeholders)
        or any(scope_line not in body for scope_line in required_scope)
    )
    if incomplete:
        errors.append("pull request body is incomplete or still contains placeholders")

    if not BRANCH_NAME.fullmatch(head_ref):
        errors.append("head branch does not follow the repository naming convention")

    authored_lines = pull_request.get("additions", 0) + pull_request.get("deletions", 0)
    if authored_lines > 400:
        errors.append(f"authored text-line budget is {authored_lines}/400")

    for index, commit in enumerate(commits, start=1):
        message = commit.get("commit", {}).get("message", "")
        subject = message.splitlines()[0] if message else ""
        if not CONVENTIONAL_COMMIT.fullmatch(subject):
            errors.append(
                f"commit {index} subject is not Conventional Commits: {subject}"
            )
        if re.search(r"(?im)^Co-Authored-By:", message):
            errors.append(f"commit {index} contains a Co-Authored-By trailer")

    return errors


def github_get(token, path):
    connection = http.client.HTTPSConnection("api.github.com", timeout=30)
    try:
        connection.request(
            "GET",
            path,
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {token}",
                "X-GitHub-Api-Version": "2022-11-28",
                "User-Agent": "remote-compose-live-match-pr-ci",
            },
        )
        response = connection.getresponse()
        payload = response.read()
        if response.status != 200:
            raise RuntimeError(f"GitHub API returned HTTP {response.status}")
        return json.loads(payload)
    except (OSError, ValueError) as error:
        raise RuntimeError(f"GitHub API request failed: {error}") from error
    finally:
        connection.close()


def main():
    event_path = os.environ["GITHUB_EVENT_PATH"]
    token = os.environ["GITHUB_TOKEN"]

    try:
        with open(event_path, encoding="utf-8") as event_file:
            event = json.load(event_file)
    except (OSError, ValueError) as error:
        print(f"::error::cannot read GitHub event: {error}", file=sys.stderr)
        return 1

    repository = event["repository"]["full_name"]
    pull_request = event["pull_request"]
    references = ISSUE_REFERENCE.findall(pull_request.get("body") or "")
    try:
        issue = {}
        if len(references) == 1:
            issue = github_get(token, f"/repos/{repository}/issues/{references[0]}")
        commits = github_get(
            token,
            f"/repos/{repository}/pulls/{pull_request['number']}/commits?per_page=100",
        )
    except RuntimeError as error:
        print(f"::error::{error}", file=sys.stderr)
        return 1

    errors = validate(event, issue, commits)
    if errors:
        for error in errors:
            print(f"::error::{error}", file=sys.stderr)
        return 1

    print(
        f"PR #{pull_request['number']} passes governance "
        f"({pull_request['additions'] + pull_request['deletions']}/400 lines)."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
