## Testing Guidelines
- Framework: `kotlin.test` (JUnit on JVM target).
- Location: `cli/src/commonTest/kotlin/...` or target‑specific `.../jvmTest/...`.
- Naming: `<Thing>Test.kt`; prefer descriptive test names (backticks allowed).
- Run: `./gradlew :cli:test`.
- Add tests for new flags, subcommands, and edge‑case parsing.