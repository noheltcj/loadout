## Kotlin Feature-Modular Organization

Organize code by behavior first and keep dependency direction explicit.

### Repository Structure

- `cli` parses command arguments, assembles use cases, and renders contract-level output
- `domain` owns canonical entities, validation rules, composition logic, and use cases
- `data` implements repository boundaries, serialization, and filesystem-backed adapters
- `platform` holds the smallest possible target-specific shim code

### Dependency Direction

Keep dependencies flowing inward:

```text
cli -> domain <- data
native/platform -> data -> domain
```

### Expectations

- Keep command parsing thin and push real behavior into domain and use-case code
- Do not leak `Clikt`, filesystem APIs, or platform types into canonical domain models
- When a behavior grows, prefer a feature-oriented package or module slice over enlarging a generic horizontal bucket
