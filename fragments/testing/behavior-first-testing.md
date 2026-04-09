## Behavior-First Testing

All tests are BDD specs.

### Current Test Layers

- `e2e` tests are the primary way to exercise the CLI thoroughly through realistic workflows and isolated workspace state
- `unit` tests cover focused pure domain behavior and small helper rules that are easy to get wrong

### Expectations

- Organize specs with nested `given`, `when`, and `then` contexts
- Prefer more `e2e` coverage than `unit` coverage
- Use `e2e` specs for command flows such as `init`, `create`, `use`, `add`, `remove`, `list`, and `sync`
- Reserve `unit` specs for composition, validation, hashing, metadata, and other contained domain rules
- Keep one assertion per `then`
- Keep scenario intent in the spec and move setup-heavy mechanics into helpers or harness code
- Assert contract-level behavior such as exit codes, persisted state, generated files, and meaningful CLI output
- Avoid asserting incidental formatting details unless the wording itself is part of the contract
