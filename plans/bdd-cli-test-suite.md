# Planned E2E Product Spec

This document is the proposed product spec for the CLI as expressed through planned `e2e` tests.

The tests will eventually become the canonical spec. Until then, treat the nested paths below as the intended product behavior even when the current implementation differs.

## How To Read This Spec

- Parent context assertions are automatically included by every nested child path.
- `[reusable]` means the context should be implemented once as a shared fixture and composed across specs.
- `[yields nested cases]` means the reusable context should accept a block for deeper nested assertions.
- Each `it ...` line is one explicit contract assertion.
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
- given init created a new starter fragment
  - it prints guidance for adding the starter fragment manually

given the starter fragment already exists before init [reusable]
- it reports that the starter fragment already exists
- it does not overwrite the existing fragment content
```

### Loadout Targeting

```text
given a loadout was specified [reusable]
- given the specified loadout is invalid [reusable]
  - it outputs that the specified loadout was not found
  - it exits with result 1
- given the specified loadout is valid [reusable, yields nested cases]
  - it uses that loadout name in success output when the command reports the target

given a new loadout name was specified [reusable]
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
given a fragment path was specified [reusable]
- given the specified fragment path does not exist [reusable]
  - it outputs a fragment-not-found error
  - it exits with result 1
- given the specified fragment path exists [reusable, yields nested cases]

given composed files are written to the default output directory [reusable]
- it writes CLAUDE.md
- it writes AGENTS.md

given --std-out was requested [reusable]
- it prints the composed content to stdout
- it does not write CLAUDE.md
- it does not write AGENTS.md

given --output was requested with a custom directory [reusable]
- it writes CLAUDE.md to the requested directory
- it writes AGENTS.md to the requested directory
```

## Command Specs

### `loadout init` Spec

```text
loadout init spec
- given an isolated workspace [reusable]
  - given shared mode was requested
    - it adds `# Loadout CLI`, `.loadout.json`, `CLAUDE.md`, and `AGENTS.md` to .gitignore
    - it does not ignore `.loadouts/` or `fragments/`
    - it creates fragments/loadout-architect.md
    - it creates the default loadout
    - it activates the default loadout
    - it writes CLAUDE.md and AGENTS.md to the default output directory
    - it prints the shared-mode completion note
    - given .gitignore already contains the shared-mode Loadout patterns
      - it reports that .gitignore is already configured for shared mode
      - it does not duplicate the shared-mode patterns
  - given local mode was requested
    - it adds `# Loadout CLI`, `.loadout.json`, `CLAUDE.md`, and `AGENTS.md` to .gitignore
    - it adds `# Loadout configuration (local-only)` to .gitignore
    - it adds `.loadouts/` and `fragments/` to .gitignore
    - it creates the default loadout
    - it activates the default loadout
    - it writes CLAUDE.md and AGENTS.md to the default output directory
    - it prints the local-mode completion note
    - given .gitignore already contains the local-mode Loadout patterns
      - it reports that .gitignore is already configured for local mode
      - it does not duplicate the local-mode patterns
  - given the starter fragment already exists before init [reusable]
    - it reports that the starter fragment already exists
    - it does not overwrite the fragment content
  - given existing loadouts already exist before init [reusable]
    - it does not create a second default loadout
    - given init created a new starter fragment
      - it prints guidance for adding the starter fragment to an existing loadout
```

### `loadout` Status Spec

```text
loadout status spec
- given no current loadout is set [reusable]
  - it outputs that no current loadout is set
  - it exits with result 0
- given a current loadout is set [reusable]
  - it outputs the current loadout name
  - it outputs the fragment count
  - it outputs the composed content length
  - given --verbose was requested
    - it lists the current loadout fragment paths
  - given composing the current loadout fails
    - it outputs the composition error
    - it exits with result 1
  - given the current loadout fragments have changed since the last composition [reusable]
    - it outputs the synchronization warning on stderr
- given the config points at a deleted loadout [reusable]
  - it outputs that the current loadout no longer exists
  - it exits with result 1
```

### `loadout list` Spec

```text
loadout list spec
- given no loadouts exist
  - it outputs the empty-state message
  - it exits with result 0
- given loadouts exist
  - it outputs every loadout name
  - it outputs each loadout's fragment count
  - it outputs each description when present
  - given --verbose was requested
    - it lists fragment paths under each loadout
- given the current loadout fragments have changed since the last composition [reusable]
  - it warns after listing loadouts
- given the config points at a deleted loadout [reusable]
  - it does not emit a misleading synchronization warning for a loadout that no longer exists
```

### `loadout create` Spec

```text
loadout create spec
- given a new loadout name was specified [reusable]
  - it creates the loadout definition
  - it outputs that the loadout was created
  - given --desc was requested
    - it persists the description
    - it outputs the description
  - given one or more --fragment values were requested
    - it persists the fragments in the given order
    - it lists the fragments in success output
  - given --clone was requested with a valid source loadout
    - it copies the source loadout fragments
    - it reuses the source description unless a new --desc was provided
    - given additional --fragment values were requested
      - it appends the additional fragments after the cloned fragments
  - given --clone was requested with an invalid source loadout
    - it outputs that the source loadout was not found
    - it exits with result 1
  - given the requested loadout name already exists [reusable]
    - it does not change the existing loadout definition
  - given the requested loadout name is invalid [reusable]
    - it does not create the loadout definition
  - given one of the requested fragment paths does not exist
    - it outputs a fragment-not-found error
    - it does not create the loadout definition
    - it exits with result 1
```

### `loadout use` Spec

```text
loadout use spec
- given a loadout was specified [reusable]
  - given the specified loadout is invalid [reusable]
    - it does not change the current loadout
  - given the specified loadout is valid [reusable, yields nested cases]
    - it composes that loadout
    - it marks that loadout as current
    - it writes CLAUDE.md and AGENTS.md to the default output directory
    - given --std-out was requested [reusable]
      - it prints the composed content
      - it does not write any output files
      - it does not change the current loadout
    - given --output was requested with a custom directory [reusable]
      - it writes CLAUDE.md and AGENTS.md to the requested directory
      - it marks that loadout as current
    - given composing the specified loadout fails
      - it outputs the composition error
      - it exits with result 1
```

### `loadout sync` Spec

```text
loadout sync spec
- given no current loadout is set [reusable]
  - it outputs that no current loadout is set
  - it exits with result 1
- given a current loadout is set [reusable]
  - given the current loadout is already synchronized
    - it outputs that the current loadout is active and up to date
    - it does not rewrite the output files
  - given the current loadout fragments have changed since the last composition [reusable]
    - it recomposes the current loadout
    - it writes CLAUDE.md and AGENTS.md to the default output directory
    - it updates the stored composition hash
    - it clears the synchronization warning on the next command
  - given --std-out was requested [reusable]
    - it prints the synchronized content
    - it does not write files
    - it does not change the stored composition hash
  - given --output was requested with a custom directory [reusable]
    - it writes CLAUDE.md and AGENTS.md to the requested directory
    - it updates the stored composition hash for the current composition
```

### `loadout add` Spec

```text
loadout add spec
- given a fragment path was specified [reusable]
  - given --to was missing
    - it exits with result 1
    - it outputs parser guidance for the missing --to option
  - given --to referenced an invalid loadout
    - it outputs that the specified loadout was not found
    - it exits with result 1
  - given the specified fragment path does not exist [reusable]
    - it does not change the loadout definition
  - given the specified fragment path exists [reusable, yields nested cases]
    - given the fragment is not already in the target loadout
      - it appends the fragment to the end of the loadout by default
      - it outputs the updated fragment list
      - given --after referenced an existing fragment
        - it inserts the new fragment immediately after that fragment
    - given the fragment is already in the target loadout
      - it outputs a duplicate-fragment error
      - it does not change the loadout definition
      - it exits with result 1
```

### `loadout remove` Spec

```text
loadout remove spec
- given a fragment path was specified [reusable]
  - given --from was missing
    - it exits with result 1
    - it outputs parser guidance for the missing --from option
  - given --from referenced an invalid loadout
    - it outputs that the specified loadout was not found
    - it exits with result 1
  - given the fragment is present in the target loadout
    - it removes the fragment from the loadout
    - it outputs the remaining fragment list
    - given the fragment was the last fragment
      - it reports that the loadout is now empty
  - given the fragment is not present in the target loadout
    - it outputs a fragment-not-in-loadout error
    - it does not change the loadout definition
    - it exits with result 1
```

### CLI Parser Spec

```text
CLI parser spec
- given an invalid subcommand was requested
  - it exits with result 1
  - it outputs the parser error
  - it suggests help or usage
- given a required argument was missing
  - it exits with result 1
  - it outputs usage guidance
- given init received an invalid mode value
  - it exits with result 1
  - it outputs the allowed mode values
```

## Cross-Command Workflow Specs

```text
first-time project workflow spec
- given an isolated workspace [reusable]
  - when init is run in shared mode
    - it creates and activates the default loadout
  - when another loadout is created and then used
    - it switches the workspace to the new loadout cleanly

switching between loadouts workflow spec
- given two valid loadouts exist
  - when one loadout is active and the other is used
    - it rewrites the generated files for the new loadout
    - it records the new current loadout

add then sync workflow spec
- given a current loadout is set [reusable]
  - when a new fragment is added to that loadout
    - it updates the loadout definition
  - when sync is run afterward
    - it rewrites the generated files with the added fragment content

stale fragments then sync workflow spec
- given a current loadout is set [reusable]
  - given the current loadout fragments have changed since the last composition [reusable]
    - it warns on a read-only command such as loadout or loadout list
  - when sync is run
    - it clears the warning on the next command

clone then customize workflow spec
- given a source loadout exists
  - when a new loadout is created with --clone and extra fragments
    - it preserves the source fragments
    - it appends the extra fragments
  - when the cloned loadout is used
    - it generates output from the customized clone
```
