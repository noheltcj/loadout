## Test Execution

The CLI test suite has two layers: `e2e` and `unit`.

### Execution Guidance

- Run the full test suite with the repo's Gradle test entrypoints
- Run focused specs while iterating, but return to the full suite before considering the work complete
- Prefer `e2e` coverage when behavior crosses command parsing, repository state, composition, and file output
- Prefer `unit` coverage only when the scenario is a small pure rule that does not need the full CLI harness
- Build the `e2e` harness around reusable logical contexts, not one-off setup scripts embedded in each spec
- Keep a shared inventory of reusable contexts for workspace state, target loadouts, current loadouts, fragment paths, sync state, and output modes
- Let shared contexts own both their setup and their automatically included assertions so child specs only add the delta they care about

### Environment Rules

- `e2e` tests must run in isolated temporary workspaces with seeded fragments, loadouts, and config state
- `e2e` tests must never point at a developer's real workspace or `~/.loadout`
- `unit` tests should stay fast, deterministic, and focused on one rule at a time
- Reusable `e2e` contexts should compose cleanly so a spec can read like a logical path from root context to final assertion
- Output assertions should focus on observable contract behavior, including exit codes, generated files, persisted config, and meaningful stdout or stderr
