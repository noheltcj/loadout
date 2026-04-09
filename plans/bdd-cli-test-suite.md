# Planned E2E Product Spec

This document is the proposed product spec for the CLI as expressed through planned `e2e` tests.

The tests will eventually become the canonical spec. Until then, treat the nested paths below as the intended product behavior even when the current implementation differs.

## How To Read This Spec

- Nested paths inherit only their ancestor `given ...` and `when ...` context.
- A child path never implicitly inherits a parent or sibling `it ...` assertion.
- Use `given ...` for durable preconditions or workspace state.
- Use `when ...` for the command or user action under test, including flags and argument variants.
- `[reusable]` means the context should be implemented once as a shared fixture and composed across specs.
- `[yields nested cases]` means the reusable context should accept a block for deeper nested assertions.
- Each `it ...` line is one explicit leaf contract assertion.
- Workflow specs may summarize an earlier command's result as a later `given ...` once that command has already been walked elsewhere in the suite.
- Reusable contexts should read like product behavior, not harness mechanics.
- Unless a scenario says otherwise, unexpected warnings, stderr noise, or extra output fail the test.

## Reusable Contexts

### Workspace and Initialization

```text
given an isolated workspace [reusable]
- it uses temporary directories only
- it never reads from or writes to a developer's real ~/.loadout
- it starts with no generated CLAUDE.md or AGENTS.md unless a parent context says otherwise

given the repo is initialized in shared mode [reusable, yields nested cases]
- it adds `# Loadout CLI` to .gitignore
- it adds `.loadout.json` to .gitignore
- it adds `CLAUDE.md` to .gitignore
- it adds `AGENTS.md` to .gitignore
- it does not ignore `.loadouts/`
- it does not ignore `fragments/`
- it creates fragments/loadout-architect.md when missing
- it creates and activates the default loadout when no loadouts exist
- given .gitignore already contains the shared-mode Loadout patterns
  - it reports that .gitignore is already configured for shared mode
  - it does not duplicate the shared-mode patterns

given the repo is initialized in local mode [reusable, yields nested cases]
- it adds `# Loadout CLI` to .gitignore
- it adds `.loadout.json` to .gitignore
- it adds `CLAUDE.md` to .gitignore
- it adds `AGENTS.md` to .gitignore
- it adds `# Loadout configuration (local-only)` to .gitignore
- it ignores .loadouts/ and fragments/
- it adds `.loadouts/` to .gitignore
- it adds `fragments/` to .gitignore
- it creates and activates the default loadout when no loadouts exist
- given .gitignore already contains the local-mode Loadout patterns
  - it reports that .gitignore is already configured for local mode
  - it does not duplicate the local-mode patterns

given existing loadouts already exist before init [reusable]
- it does not create another default loadout
- given the starter fragment does not already exist before init
  - it prints guidance for adding the starter fragment manually

given the starter fragment already exists before init [reusable]
- it reports that the starter fragment already exists
- it does not overwrite the existing fragment content
```

### Loadout Targeting

```text
given a command targets a named loadout [reusable]
- given the specified loadout is invalid [reusable]
  - it outputs that the specified loadout was not found
  - it exits with result 1
- given the specified loadout is valid [reusable, yields nested cases]
  - it uses that loadout name in success output when the command reports the target

given a command creates a new named loadout [reusable]
- given the requested loadout name already exists [reusable]
  - it outputs a loadout-already-exists error
  - it exits with result 1
- given the requested loadout name is invalid [reusable]
  - it outputs the validation error
  - it exits with result 1
```

### Current Loadout and Synchronization

```text
given no current loadout is set [reusable]
- it reports that no current loadout is set when the command requires one

given a current loadout is set [reusable, yields nested cases]
- it reads the current loadout from .loadout.json
- given the current loadout fragments have changed since the last composition [reusable]
  - it warns that the current loadout is not synchronized
  - it tells the user to run loadout sync and restart the agent

given the config points at a deleted loadout [reusable]
- it surfaces the deleted-loadout state explicitly instead of pretending the workspace is merely out of sync
```

### Fragments and Output Modes

```text
given a command targets a fragment path [reusable]
- given the specified fragment path does not exist [reusable]
  - it outputs a fragment-not-found error
  - it exits with result 1
- given the specified fragment path exists [reusable, yields nested cases]

when the command writes composed files to the default output directory [reusable]
- it writes CLAUDE.md
- it writes AGENTS.md

when the command is run with --std-out [reusable]
- it prints the composed content to stdout
- it does not write CLAUDE.md
- it does not write AGENTS.md

when the command is run with --output and a custom directory [reusable]
- it writes CLAUDE.md to the requested directory
- it writes AGENTS.md to the requested directory
```

## Command Specs

### `loadout init` Spec

```text
loadout init spec
- given an isolated workspace [reusable]
  - when loadout init is run in shared mode
    - it adds `# Loadout CLI`, `.loadout.json`, `CLAUDE.md`, and `AGENTS.md` to .gitignore
    - it does not ignore `.loadouts/` or `fragments/`
    - it creates fragments/loadout-architect.md
    - it creates the default loadout
    - it activates the default loadout
    - it writes CLAUDE.md and AGENTS.md to the default output directory
    - it prints the shared-mode completion note
  - given .gitignore already contains the shared-mode Loadout patterns
    - when loadout init is run in shared mode
      - it reports that .gitignore is already configured for shared mode
      - it does not duplicate the shared-mode patterns
  - when loadout init is run in local mode
    - it adds `# Loadout CLI`, `.loadout.json`, `CLAUDE.md`, and `AGENTS.md` to .gitignore
    - it adds `# Loadout configuration (local-only)` to .gitignore
    - it adds `.loadouts/` and `fragments/` to .gitignore
    - it creates the default loadout
    - it activates the default loadout
    - it writes CLAUDE.md and AGENTS.md to the default output directory
    - it prints the local-mode completion note
  - given .gitignore already contains the local-mode Loadout patterns
    - when loadout init is run in local mode
      - it reports that .gitignore is already configured for local mode
      - it does not duplicate the local-mode patterns
  - given the starter fragment already exists before init [reusable]
    - when loadout init is run in shared mode
      - it reports that the starter fragment already exists
      - it does not overwrite the fragment content
  - given existing loadouts already exist before init [reusable]
    - when loadout init is run in shared mode
      - it does not create a second default loadout
    - given the starter fragment does not already exist before init
      - when loadout init is run in shared mode
        - it prints guidance for adding the starter fragment to an existing loadout
```

### `loadout` Status Spec

```text
loadout status spec
- given no current loadout is set [reusable]
  - when loadout is run without a subcommand
    - it outputs that no current loadout is set
    - it exits with result 0
- given a current loadout is set [reusable]
  - when loadout is run without a subcommand
    - it outputs the current loadout name
    - it outputs the fragment count
    - it outputs the composed content length
  - given the current loadout has a description
    - when loadout is run without a subcommand
      - it outputs the current loadout description
  - when loadout is run without a subcommand and with --verbose
    - it lists the current loadout fragment paths
  - given composing the current loadout fails
    - when loadout is run without a subcommand
      - it outputs the composition error
      - it exits with result 1
  - given the current loadout fragments have changed since the last composition [reusable]
    - when loadout is run without a subcommand
      - it outputs the synchronization warning on stderr
- given the config points at a deleted loadout [reusable]
  - when loadout is run without a subcommand
    - it outputs that the current loadout no longer exists
    - it exits with result 1
```

### `loadout list` Spec

```text
loadout list spec
- given no loadouts exist
  - when loadout list is run
    - it outputs the empty-state message
    - it exits with result 0
- given loadouts exist
  - when loadout list is run
    - it outputs every loadout name
    - it outputs each loadout's fragment count
    - it outputs each description when present
  - when loadout list is run with --verbose
    - it lists fragment paths under each loadout
- given the current loadout fragments have changed since the last composition [reusable]
  - when loadout list is run
    - it warns after listing loadouts
- given the config points at a deleted loadout [reusable]
  - when loadout list is run
    - it does not emit a misleading synchronization warning for a loadout that no longer exists
```

### `loadout create` Spec

```text
loadout create spec
- given the requested loadout name is valid
  - given the requested loadout name does not already exist
    - when loadout create is run with no --fragment values
      - it creates the loadout definition
      - it creates an empty loadout
      - it outputs that the loadout was created
    - when loadout create is run with --desc
      - it creates the loadout definition
      - it persists the description
      - it outputs that the loadout was created
      - it outputs the description
    - when loadout create is run with one or more --fragment values
      - it creates the loadout definition
      - it persists the fragments in the given order
      - it outputs that the loadout was created
      - it lists the fragments in success output
    - given the requested clone source loadout exists
      - when loadout create is run with --clone and no new --desc
        - it creates the loadout definition
        - it copies the source loadout fragments
        - it reuses the source description
        - it outputs that the loadout was created
      - when loadout create is run with --clone and a new --desc
        - it creates the loadout definition
        - it copies the source loadout fragments
        - it overrides the source description with the new description
        - it outputs that the loadout was created
      - when loadout create is run with --clone and additional --fragment values
        - it creates the loadout definition
        - it copies the source loadout fragments
        - it appends the additional fragments after the cloned fragments
        - it outputs that the loadout was created
    - given the requested clone source loadout does not exist
      - when loadout create is run with --clone
        - it outputs that the source loadout was not found
        - it exits with result 1
    - given one of the requested fragment paths does not exist
      - when loadout create is run with one or more --fragment values
        - it outputs a fragment-not-found error
        - it does not create the loadout definition
        - it exits with result 1
- given the requested loadout name already exists [reusable]
  - when loadout create is run
    - it outputs a loadout-already-exists error
    - it does not change the existing loadout definition
    - it exits with result 1
- given the requested loadout name is invalid [reusable]
  - when loadout create is run
    - it outputs the validation error
    - it does not create the loadout definition
    - it exits with result 1
```

### `loadout use` Spec

```text
loadout use spec
- given the specified loadout is invalid [reusable]
  - when loadout use is run
    - it outputs that the specified loadout was not found
    - it exits with result 1
    - it does not change the current loadout
- given the specified loadout is valid [reusable, yields nested cases]
  - when loadout use is run
    - it composes that loadout
    - it marks that loadout as current
    - it writes CLAUDE.md and AGENTS.md to the default output directory
  - when loadout use is run with --std-out
    - it prints the composed content
    - it does not write any output files
    - it does not change the current loadout
  - when loadout use is run with --output and a custom directory
    - it writes CLAUDE.md and AGENTS.md to the requested directory
    - it marks that loadout as current
  - given composing the specified loadout fails
    - when loadout use is run
      - it outputs the composition error
      - it exits with result 1
```

### `loadout sync` Spec

```text
loadout sync spec
- given no current loadout is set [reusable]
  - when loadout sync is run
    - it outputs that no current loadout is set
    - it exits with result 1
- given the config points at a deleted loadout [reusable]
  - when loadout sync is run
    - it outputs that the current loadout no longer exists
    - it exits with result 1
- given a current loadout is set [reusable]
  - given composing the current loadout fails
    - when loadout sync is run
      - it outputs the composition error
      - it exits with result 1
  - given the current loadout is already synchronized
    - when loadout sync is run
      - it outputs that the current loadout is active and up to date
      - it does not rewrite the output files
    - when loadout sync is run with --std-out
      - it prints the current composed content
      - it does not write files
      - it does not change the stored composition hash
    - when loadout sync is run with --output and a custom directory
      - it writes CLAUDE.md and AGENTS.md to the requested directory
      - it leaves the stored composition hash unchanged
  - given the current loadout fragments have changed since the last composition [reusable]
    - when loadout sync is run
      - it recomposes the current loadout
      - it writes CLAUDE.md and AGENTS.md to the default output directory
      - it updates the stored composition hash
      - it clears the synchronization warning on the next command
    - when loadout sync is run with --std-out
      - it prints the recomposed content
      - it does not write files
      - it does not change the stored composition hash
      - it leaves the synchronization warning in place on the next command
    - when loadout sync is run with --output and a custom directory
      - it writes CLAUDE.md and AGENTS.md to the requested directory
      - it updates the stored composition hash for the current composition
      - it clears the synchronization warning on the next command
```

### `loadout add` Spec

```text
loadout add spec
- when loadout add is run without --to
  - it exits with result 1
  - it outputs parser guidance for the missing --to option
- given the loadout named by --to is invalid
  - when loadout add is run
    - it outputs that the specified loadout was not found
    - it exits with result 1
- given the loadout named by --to is valid and not currently active
  - given the specified fragment path does not exist [reusable]
    - when loadout add is run
      - it outputs a fragment-not-found error
      - it does not change the loadout definition
      - it exits with result 1
  - given the specified fragment path exists [reusable, yields nested cases]
    - given the fragment is not already in the target loadout
      - when loadout add is run without --after
        - it appends the fragment to the end of the loadout
        - it outputs the updated fragment list
        - it does not change the current loadout
      - when loadout add is run with --after referencing an existing fragment
        - it inserts the new fragment immediately after that fragment
        - it outputs the updated fragment list
      - when loadout add is run with --after referencing a fragment that is not in the target loadout
        - it appends the new fragment to the end of the loadout instead of failing
        - it outputs the updated fragment list
    - given the fragment is already in the target loadout
      - when loadout add is run
        - it outputs a duplicate-fragment error
        - it does not change the loadout definition
        - it exits with result 1
- given the loadout named by --to is the current loadout
  - given the specified fragment path exists [reusable]
    - given the fragment is not already in the target loadout
      - when loadout add is run
        - it updates the current loadout definition
        - it outputs the updated fragment list
        - it does not rewrite generated files automatically
        - it causes the next read-only command to warn that the current loadout is not synchronized
```

### `loadout remove` Spec

```text
loadout remove spec
- when loadout remove is run without --from
  - it exits with result 1
  - it outputs parser guidance for the missing --from option
- given the loadout named by --from is invalid
  - when loadout remove is run
    - it outputs that the specified loadout was not found
    - it exits with result 1
- given the loadout named by --from is valid and not currently active
  - given the fragment is present in the target loadout
    - when loadout remove is run
      - it removes the fragment from the loadout
      - it outputs the remaining fragment list
      - it does not change the current loadout
    - given the fragment was the last fragment
      - when loadout remove is run
        - it reports that the loadout is now empty
  - given the fragment is not present in the target loadout
    - when loadout remove is run
      - it outputs a fragment-not-in-loadout error
      - it does not change the loadout definition
      - it exits with result 1
- given the loadout named by --from is the current loadout
  - given the fragment is present in the target loadout
    - when loadout remove is run
      - it updates the current loadout definition
      - it outputs the remaining fragment list
      - it does not rewrite generated files automatically
      - it causes the next read-only command to warn that the current loadout is not synchronized
```

### CLI Parser Spec

```text
CLI parser spec
- when loadout is run with an invalid subcommand
  - it exits with result 1
  - it outputs the parser error
  - it suggests help or usage
- when loadout is run without a required argument
  - it exits with result 1
  - it outputs usage guidance
- when loadout init is run with an invalid --mode value
  - it exits with result 1
  - it outputs the allowed mode values
```

## Cross-Command Workflow Specs

```text
first-time project workflow spec
- given an isolated workspace [reusable]
  - given the workspace has already been initialized in shared mode
    - it starts with the default loadout created and active
    - when loadout create is run for the first project-specific loadout with one or more fragments
      - it creates the requested loadout definition
      - given that new loadout has been created
        - when loadout use is run for that new loadout
          - it switches the workspace to the new loadout cleanly
          - it writes composed files for that new loadout

switching between loadouts workflow spec
- given two valid loadouts exist
  - given one loadout is currently active
    - when loadout use is run for the other loadout
      - it rewrites the generated files for the new loadout
      - it records the new current loadout

add then sync workflow spec
- given a current loadout is set [reusable]
  - given a new fragment has been added to that current loadout
    - it updates the loadout definition
    - when loadout sync is run
      - it rewrites the generated files with the added fragment content
      - it clears the synchronization warning on the next command

stale fragments then sync workflow spec
- given a current loadout is set [reusable]
  - given the current loadout fragments have changed since the last composition [reusable]
    - when a read-only command such as loadout or loadout list is run
      - it warns that the current loadout is not synchronized
    - when loadout sync is run
      - it clears the warning on the next command

clone then customize workflow spec
- given a source loadout exists
  - when loadout create is run with --clone and extra fragments
    - it preserves the source fragments
    - it appends the extra fragments
    - given the cloned loadout has been created with those extra fragments
      - when loadout use is run for the cloned loadout
        - it generates output from the customized clone
```

## Known Current Gaps

- `loadout create` currently does not validate fragment paths before persisting them.
- `loadout add` currently does not validate fragment paths before persisting them.
- `loadout sync --output <dir>` currently short-circuits on a matching hash and skips writing the requested custom output files.
- Successful `loadout sync` runs are currently followed by a contradictory stale-sync warning because the post-command sync check still sees the previous stored hash.
- Read-only commands can currently emit a misleading stale-sync warning when the config points at a deleted loadout.
