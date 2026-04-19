## Context Composition

Nested test contexts are semantic contracts, not visual grouping.

### Composition Rules

- Every `given` must establish or refine durable state for all descendant branches
- Do not use `given` as a label-only namespace when it does not change setup
- Child setup should read as `parent state + delta`, not as a fresh world rebuild
- Prefer a named reusable seed for the parent state, then derive narrower child seeds from it
- Keep setup in `given` seeds and keep `when` focused on the action under test
- If a `when` block creates the state named by its parent `given`, the setup is misplaced

### Review Checks

- If removing the parent `given` would not change the child seed, the hierarchy is probably lying about setup
- If a child branch restates ancestor setup inline instead of composing from the parent seed, treat that as a harness smell
- If a nested context needs the parent state, compose directly from the parent reusable context such as `parentSeed.andThen { ... }`

### Example

```text
good:
given initialized repo
- seed = initializedRepo
- given extra loadout exists
  - seed = initializedRepo.andThen { seedLoadout(...) }

bad:
given initialized repo
- given extra loadout exists
  - seed = {
      initialize repo from scratch
      seedLoadout(...)
    }
```
