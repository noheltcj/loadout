# Repository Guidelines

## Project Structure & Module Organization
- Source: `cli/src/{commonMain,jvmMain,nativeMain}/kotlin` (Kotlin Multiplatform CLI).
- Tests: `cli/src/{commonTest,jvmTest}/kotlin`.
- Build output: `cli/build/install/loadout-cli/bin/loadout`.
- Docs: `README.md` (overview), `AGENTS.md` (this guide).

## Build, Test, and Development Commands
- `./gradlew :cli:installDist`: Build a runnable CLI distribution.
- `./cli/build/install/loadout-cli/bin/loadout --help`: Run the built binary.
- `./gradlew tasks --all`: Discover available Gradle tasks.

## Coding Style & Naming Conventions
- Kotlin style, 4‑space indentation, 120‑char soft wrap.
- Types: `PascalCase`; functions/props: `camelCase`; constants: `UPPER_SNAKE_CASE`.
- Keep CLI parsing thin under `cli/`; composition logic remains platform‑agnostic.
- If configured, run formatters/linters: `./gradlew ktlintFormat detekt`.

## Testing Guidelines
- Framework: `kotlin.test` (JUnit on JVM target).
- Location: `cli/src/commonTest/kotlin/...` or target‑specific `.../jvmTest/...`.
- Naming: `<Thing>Test.kt`; prefer descriptive test names (backticks allowed).
- Run: `./gradlew :cli:test`.
- Add tests for new flags, subcommands, and edge‑case parsing.

## Commit & Pull Request Guidelines
- Never, ever, do anything with git other than read-only commands without an explicit request to do so.

## Security & Configuration
- Do not commit secrets or API tokens.
- Treat generated `AGENTS.md`/`CLAUDE.md` as build artifacts; add to `.gitignore`.

