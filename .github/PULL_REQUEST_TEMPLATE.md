## Linked issue

Closes #<!-- approved issue number -->

## PR type

Select exactly one and add the matching `type:*` label.

- [ ] Bug fix (`type:bug`)
- [ ] New feature (`type:feature`)
- [ ] Documentation only (`type:docs`)
- [ ] Code refactoring (`type:refactor`)
- [ ] Maintenance/tooling (`type:chore`)
- [ ] Breaking change (`type:breaking-change`)

## Summary

- <!-- Summarize the change. -->

## Changes

| File | Change |
| --- | --- |
| `path/to/file` | Describe the reviewable behavior change. |

## Test plan

- [ ] Focused automated checks pass.
- [ ] Applicable regression suite passes.
- [ ] Runtime or manual evidence is recorded, or marked not applicable.

## Chain context

| Field | Value |
| --- | --- |
| Chain | <!-- chain name --> |
| Tracker PR | <!-- PR number or Not needed --> |
| Position | <!-- N of total --> |
| Base | `<!-- target branch -->` |
| Depends on | <!-- prior PR/issue or None --> |
| Follow-up | <!-- next PR or None --> |
| Review budget | <!-- changed lines --> / 400 |
| Starts at | <!-- branch, PR, or state --> |
| Ends with | <!-- standalone result --> |

### Chain overview

```text
main
 └── #NNN Previous PR
      └── 📍 #NNN This PR
           └── #NNN Next PR
```

### Scope

- Includes: <!-- included behavior -->
- Excludes: <!-- excluded behavior -->

### Autonomy

- [ ] CI is expected to pass for this PR branch.
- [ ] This PR has one deliverable scope.
- [ ] This PR can be rolled back without unrelated changes.
- [ ] Tests, docs, or manual verification cover this unit.

## Contributor checklist

- [ ] Linked an issue carrying `status:approved`.
- [ ] Added exactly one `type:*` label.
- [ ] Ran `shellcheck` for every modified shell script, or none changed.
- [ ] Tested affected behavior in its target environment.
- [ ] Updated documentation when behavior changed.
- [ ] Used Conventional Commit messages.
- [ ] Added no `Co-Authored-By` trailers.
