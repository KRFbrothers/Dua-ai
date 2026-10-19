# Dua AI repository instructions

## Load the project skill

For engineering work in this repository, read
`.agents/skills/dua-ai-engineering/SKILL.md` before planning or editing. It holds
Fareed's project workflow, UI constraints, verification, release and maintenance
instructions. Follow current user and higher-priority instructions first.

Read the file directly even if `dua-ai-engineering` is absent from the host's
skill catalog or `$` picker. Loading repository instructions this way does not
register a skill in the host catalog. Do not create another copy to make it show
up there.

Locate the repository root with `git rev-parse --show-toplevel`, then check the
exact path. Generic searches can skip the hidden `.agents` directory; if a file
search is needed, include hidden files, for example:

```bash
rg --files --hidden .agents/skills -g SKILL.md
```

If that path is absent, inspect the current branch/ref and checkout age. An
existing cloud task can retain a snapshot from before the skill was merged.
Preserve uncommitted work; do not reset, switch branches or rewrite the task's
checkout just to obtain instructions. If an authenticated repository reader is
available, read this exact path from a verified ref of `KRFbrothers/Dua-ai`,
report the ref used, and account for differences from the working checkout.
Only report the skill unavailable after the exact-path check and any available
repository-file fallback fail. If necessary, continue unaffected work with
applicable instructions and state the remaining limitation.

## Verification status

Follow the skill's task-specific checks. A sandbox error opening Gradle's local
sockets is an execution-environment blocker, not a failed app test. Retry through
the tool's supported permission mechanism when authorized. A cancelled or denied
retry leaves verification incomplete; report it honestly and use CI or another
permitted environment. Do not bypass restrictions or report unrun checks as
passing.

## Git handoff

Work on a focused branch and open/update a PR. Fareed merges; do not push directly
to main, merge the PR or force-push. Keep the skill's canonical path unchanged
when maintaining it.
