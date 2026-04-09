## Test Execution

The CLI test suite has two layers: `e2e` and `unit`.

### Execution Guidance

- Run the full test suite with the repo's Gradle test entrypoints
- Run focused specs while iterating, but return to the full suite before considering the work complete
- Prefer `e2e` coverage when behavior crosses command parsing, repository state, composition, and file output
- Prefer `unit` coverage only when the scenario is a small pure rule that does not need the full CLI harness

### Environment Rules

- `e2e` tests must run in isolated temporary workspaces with seeded fragments, loadouts, and config state
- `e2e` tests must never point at a developer's real workspace or `~/.loadout`
- `unit` tests should stay fast, deterministic, and focused on one rule at a time
