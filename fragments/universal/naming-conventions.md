## Naming Conventions

Names should be explicit and optimized for clarity at the call site.

### Guidelines

- Use full words instead of abbreviations unless an acronym is standard, such as `id`, `url`, or `api`
- Prefer names that read clearly where they are used, not only where they are defined
- Favor strong domain names like `fragmentPath`, `loadoutName`, and `outputPaths` over generic placeholders
- Repository interfaces end in `Repository`
- Domain logic ends in `UseCase`
- Argument carriers introduced for commands or workflows end in `Input`
- Keep canonical domain types free of presentation-oriented naming

### Litmus Test

If a reader cannot infer what a value represents from its type and name at the call site, rename it until the meaning is obvious.
