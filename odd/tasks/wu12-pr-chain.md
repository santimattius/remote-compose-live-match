# WU12 Pull Request Chain
Objective: Publish the completed signed-envelope work as a reviewable GitHub Feature Branch Chain.
Source: `feat/wu12-signed-envelope-integration`. Target: `main`. Approved issue: `#1`.

## Specs
S1. User request: “antes de continuar vayamos armando los PRs”.
S2. Every PR must link approved issue `#1`, use exactly one `type:feature` label, and conform to `.github/PULL_REQUEST_TEMPLATE.md`.
S3. Use the cached Feature Branch Chain strategy: one draft/no-merge tracker targeting `main`; child PR 1 targets the tracker branch; every later child targets its immediate predecessor.
S4. Keep each child at or below 400 authored text-line changes after one honest slicing pass; keep tests and documentation with the behavior they verify and never compress code to meet the budget.
S5. Never stage, commit, print, or publish local private signing keys.
S6. The final child tree must preserve the completed WU12 behavior from `feat/wu12-signed-envelope-integration` while inheriting the GitHub governance files now on `main`.

## Tasks
T1 [S1-S6] explore — Derive and verify one cohesive review slicing plan. Status: done. Commit: N/A.
T2 [S2-S3, S5] inline — Create the tracker branch and draft/no-merge tracker PR. Risk: item 5. Status: in_progress. Commit: pending.
T3 [S4-S6] worker — Build and publish child 1: envelope cryptographic contract. Status: pending. Commit: pending.
T4 [S4-S6] inline — Build and publish child 2: envelope validation policy. Status: pending. Commit: pending.
T5 [S4-S6] inline — Build and publish child 3: JVM signing and external key loading. Status: pending. Commit: pending.
T6 [S4-S6] inline — Build and publish child 4: versioned binary `.rce` codec. Status: pending. Commit: pending.
T7 [S4-S6] inline — Build and publish child 5: Scheduled signed-fixture publication. Status: pending. Commit: pending.
T8 [S4-S6] inline — Build and publish child 6: Live and Finished signed fixtures. Status: pending. Commit: pending.
T9 [S4-S6] inline — Build and publish child 7: Android validation boundary and Tink bridge. Status: pending. Commit: pending.
T10 [S4-S6] inline — Build and publish child 8: host asset and player integration. Status: pending. Commit: pending.
T11 [S4-S6] inline — Build and publish child 9: device evidence and final status. Status: pending. Commit: pending.
T12 [S2-S6] parent — Read back every PR, verify clean immediate-parent diffs, labels, review budgets, and checks. Risk: item 5. Status: pending. Commit: N/A.

## Log
L1. User request: “antes de continuar vayamos armando los PRs”.
L2. User resumed with: “continuemos”.
L3. Governance bootstrap commit `9e4ea84` is on `main`; issue `#1` is open with `status:approved` and `type:feature`.
L4. Existing cohesive commits contain 871, 699, and 651 authored text-line changes, exceeding the 400-line budget. One read-only slicing pass produced nine dependent child units estimated below 400 without modifying or compressing code; no `size:exception` is required.
