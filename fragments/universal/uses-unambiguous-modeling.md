## Unambiguous Data Modeling

Make invalid states unrepresentable. Every valid instance of a model should describe one coherent state.

### Rules

- Prefer sealed hierarchies or explicit variants over parallel flags and nullable fields that can disagree
- Each variant should carry exactly the data that exists in that state
- Collapse fields when they are not independently meaningful in practice
- Keep canonical models free of presentation-only labels, copy, or formatting concerns
- Model command outcomes and synchronization state with explicit types rather than loosely related booleans

### Litmus Test

If you can construct a value that the domain would reject as contradictory, redesign the type until you cannot.
