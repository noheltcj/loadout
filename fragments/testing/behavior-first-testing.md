## Behavior-First Testing

All tests are BDD specs that describe logical paths as human-readable contexts and leaf assertions.

### Current Test Layers

- `e2e` tests are the primary way to exercise the CLI thoroughly through realistic workflows and isolated workspace state
- `unit` tests cover focused pure domain behavior and small helper rules that are easy to get wrong

### Expectations

- Organize specs as nested `given`, `when`, and `it` paths
- Prefer more `e2e` coverage than `unit` coverage
- Use `e2e` specs for command flows such as `init`, `create`, `use`, `add`, `remove`, `list`, and `sync`
- Reserve `unit` specs for composition, validation, hashing, metadata, and other contained domain rules
- Treat parent contexts as reusable fixtures when the same setup and baseline assertions should appear across multiple specs
- Inherit parent assertions automatically in nested branches instead of restating them in every child path
- Let reusable happy-path contexts accept a nested block so deeper cases can build on the same setup without hiding intent
- Keep one contract assertion per `it`
- Keep scenario intent in the spec and move setup-heavy mechanics into helpers or harness code
- Assert contract-level behavior such as exit codes, persisted state, generated files, and meaningful CLI output
- Avoid asserting incidental formatting details unless the wording itself is part of the contract

### Example Path Shape

```text
foo command spec
- it outputs that foo was called
- given a loadout was specified [reusable]
  - it includes output recognizing the target loadout
  - given the specified loadout is invalid [reusable]
    - it outputs that the specified loadout does not exist
    - it exits with result 1
  - given the specified loadout is valid [reusable, yields nested cases]
    - given bar flag passed with value `foobar`
      - it includes output recognizing the use of bar
      - when `foobar` is invalid
        - it outputs an error
        - it does not change any files
        - it exits with result 1
      - when `foobar` is valid
        - ...
```

The important rule is that a child path inherits the setup and assertions of every parent context above it. Reusable contexts should read like product behavior, not helper mechanics.
