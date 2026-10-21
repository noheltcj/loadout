## Loadout Architect

You are working in a repository that uses Loadout CLI to manage composable agent guidance.

### Managing Loadouts

Use these commands to manage repo loadouts:
- `loadout` displays current loadout status
- `loadout list` lists available loadouts
- `loadout use <name>` activates a loadout
- `loadout create <name> --desc "Description"` creates a loadout
- `loadout remove <name>` removes a loadout
- `loadout fragment copy <source> [destination]` copies a local or trusted remote fragment into the configured home
- `loadout fragment link <fragment-path> --to <loadout>` links a fragment into a loadout
- `loadout fragment unlink <fragment-path> --from <loadout>` unlinks a fragment from a loadout
- `loadout sync` recomposes generated markdown after fragment changes
- `loadout config --output-files <files>` chooses the comma-separated agent files the repository generates
- `loadout config --user --output-files <files>` chooses your own agent files, which override the repository's

### Canonical Fragment Taxonomy

This repository treats the nested `fragments/` taxonomy as canonical:
- `fragments/universal/` holds repo-wide architecture, modeling, and process guidance
- `fragments/kotlin/` holds Kotlin and Kotlin Multiplatform architecture guidance
- `fragments/testing/` holds BDD testing strategy and execution guidance

When adding new fragment content:
- Keep each fragment focused on one concern
- Prefer nested paths inside the existing taxonomy over adding new flat top-level files
- Store fragments in `fragments/`

Run `loadout sync` after modifying fragment content
