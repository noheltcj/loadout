## Test Execution

The CLI test suite has two layers: `e2e` and `unit`.

### Execution Guidance

- Run focused specs while iterating, then return to the repo's full Gradle test entrypoints before considering the work complete
- Prefer `e2e` coverage when behavior crosses command parsing, repository state, composition, and file output
- Prefer `unit` coverage only when the scenario is a small pure rule that does not need the full CLI harness

### Harness Guidance

- Build the `e2e` harness around reusable logical contexts, not one-off setup scripts embedded in each spec
- Keep a shared inventory of reusable contexts for workspace state, target loadouts, current loadouts, fragment paths, sync state, and output modes
- Let shared contexts own setup and helper composition, not hidden assertions
- Reusable contexts should compose into one unambiguous path from root state to leaf assertion

### Environment Rules

- `e2e` tests must run in isolated temporary workspaces with seeded fragments, loadouts, and config state
- `e2e` tests must never point at a developer's real workspace or `~/.loadout`
- `unit` tests should stay fast, deterministic, and focused on one rule at a time
- Output assertions should focus on observable contract behavior, including exit codes, generated files, persisted config, and meaningful stdout or stderr
