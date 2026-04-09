## Behavior-First Testing

All tests are BDD specs that read as logical paths from seeded state to observable contract behavior.

### Test Layers

- Prefer `e2e` coverage for realistic CLI workflows, isolated workspace state, and cross-layer behavior
- Use `unit` tests for small pure domain rules and contained helper behavior

### BDD Rules

- Organize specs as nested `given`, `when`, and `it` paths
- `given` describes durable preconditions or seeded state
- `when` describes the command or action under test, including relevant flags and arguments
- `it` describes one explicit leaf assertion
- Nested branches inherit only ancestor `given` and `when` context
- Never implicitly inherit ancestor or sibling `it` assertions
- Reusable contexts should expose product-level state, not harness mechanics
- Let reusable contexts yield nested cases when multiple branches share the same setup
- Use an explicit post-step `given` when a later workflow step depends on an earlier command result
- Keep one contract assertion per `it`
- Assert contract-level behavior such as exit codes, persisted state, generated files, and meaningful CLI output
- Avoid asserting incidental formatting details unless the wording itself is part of the contract

### Example Path Shape

```text
foo command spec
- given the target loadout exists [reusable]
  - when foo is run
    - it reports the target loadout name
  - when foo is run with --bar=invalid
    - it outputs a validation error
    - it exits with result 1
  - when foo is run with --bar=valid
    - it writes the expected output files
```

Every leaf path should remain unambiguous when read top to bottom. Reusable contexts should read like product behavior, not helper mechanics.
