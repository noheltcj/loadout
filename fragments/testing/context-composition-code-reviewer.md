## Context Composition Review

When reviewing tests, verify that nested contexts tell the truth about setup.

### Review Expectations

- Flag `given` blocks that only group cases without changing seeded state
- Flag child seeds that rebuild ancestor setup instead of composing from the parent context
- Flag `when` blocks that secretly perform setup work needed to make the parent `given` true
- Prefer one comment per broken context path and explain which parent state was skipped or restated

### Severity Guidance

- Treat namespace-only `given` blocks as meaningful when they make the test path ambiguous or misleading
- Raise the issue when a reader could not trust the top-to-bottom path shape to understand the seeded state
- Prioritize cases where broken composition hides important repo state, git state, current loadout state, or prior workflow steps
