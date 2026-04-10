## Loadout Architect

You are working in a repository that uses Loadout CLI to manage composable agent guidance.

### Managing Loadouts

Use these commands to manage repo loadouts:
- `loadout` displays current loadout status
- `loadout list` lists available loadouts
- `loadout use <name>` activates a loadout
- `loadout create <name> --desc "Description"` creates a loadout
- `loadout remove <name>` removes a loadout
- `loadout link <fragment-path> --to <loadout>` links a fragment into a loadout
- `loadout unlink <fragment-path> --from <loadout>` unlinks a fragment from a loadout
- `loadout sync` recomposes generated markdown after fragment changes

### Canonical Fragment Taxonomy

This repository treats the nested `fragments/` taxonomy as canonical:
- `fragments/universal/` holds repo-wide architecture, modeling, and process guidance
- `fragments/kotlin/` holds Kotlin and Kotlin Multiplatform architecture guidance
- `fragments/testing/` holds BDD testing strategy and execution guidance

When adding new fragment content:
- Keep each fragment focused on one concern
- Prefer nested paths inside the existing taxonomy over adding new flat top-level files
- Store repo-owned fragments in `fragments/`
- Store personal fragments in `~/.loadout/fragments/`

### Working Expectations

- Treat the CLI as a BDD-tested product and preserve that testing posture when changing behavior
- Prefer loadouts that include architecture, modeling, and testing context together
- Keep `e2e` coverage strong for realistic CLI workflows and use `unit` coverage for focused logic only
- Update `plans/` when deferring substantial implementation work for a later session
- Run `loadout sync` after modifying fragment content
