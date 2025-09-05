## Coding Style & Naming Conventions
- Kotlin style, 4‑space indentation, 120‑char soft wrap.
- Types: `PascalCase`; functions/props: `camelCase`; constants: `UPPER_SNAKE_CASE`.
- Keep CLI parsing thin under `cli/`; composition logic remains platform‑agnostic.
- If configured, run formatters/linters: `./gradlew ktlintFormat detekt`.