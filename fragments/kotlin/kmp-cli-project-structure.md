## Kotlin Multiplatform CLI Project Structure

This repository is a Kotlin Multiplatform CLI with shared domain logic and native execution targets.

### Canonical Layout

- `src/commonMain/kotlin/cli/` contains command definitions, CLI wiring, and output helpers
- `src/commonMain/kotlin/domain/` contains canonical entities, repository interfaces, services, and use cases
- `src/commonMain/kotlin/data/` contains shared serialization and repository implementations that do not depend on target-specific APIs
- `src/nativeMain/kotlin/` contains native entrypoints and native repository wiring
- `src/*Main/kotlin/data/platform/` contains tiny platform-specific shims only

### Test Layout

- `src/commonTest/kotlin/` is the canonical home for shared BDD specs
- `src/*Test/kotlin/` is reserved for platform-native smoke coverage that must exercise real adapters

### Repo Guidance

- `.loadouts/` stores committed loadout definitions
- `fragments/` stores the canonical nested fragment taxonomy
- `plans/` stores handoff documents for deferred implementation work
- `docs/` captures broader reference material such as CLI behavior inventories and test scenarios
