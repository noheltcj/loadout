# BDD CLI Test Suite Handoff

This document is for the next implementation session. The fragment rewrite is complete, but the actual test harness and behavioral fixes are still deferred.

## Goal

Add a BDD-first automated test suite for the CLI without changing the public command surface.

The intended test stack is:
- Kotest KMP for shared BDD specs in `src/commonTest/kotlin`
- A reusable in-process CLI harness for the broad scenario suite
- A small native smoke layer in platform test source sets for real adapter coverage

## Current Repo Facts

- `build.gradle.kts` currently defines native targets and exposes native test tasks such as `macosArm64Test`, `macosX64Test`, `linuxX64Test`, `mingwX64Test`, and `allTests`
- The repo does not currently contain real test source files or test dependencies
- The fragment taxonomy has been upgraded and the committed default loadout now assumes a BDD-first testing culture with `e2e` and `unit` layers
- This session intentionally did not modify CLI code, Gradle test setup, or README examples

## Intended Implementation Shape

1. Add Kotest KMP dependencies and configure `commonTest`
2. Build a CLI harness that can:
   - construct the full `LoadoutCli` command tree with test doubles or isolated repositories
   - invoke commands with args
   - capture stdout, stderr, and exit status
   - seed temporary workspace state for fragments, `.loadouts`, and `.loadout.json`
3. Write broad BDD specs in `src/commonTest/kotlin` for the CLI contract
4. Add a small native smoke layer in platform test source sets for real filesystem and environment adapter coverage

## Test Layers

- `e2e`
  - primary coverage layer
  - exercises realistic CLI workflows end to end in isolated temp workspaces
  - should cover command flows, generated files, persisted config, and error behavior
- `unit`
  - secondary coverage layer
  - targets small pure rules such as validation, composition, hashing, metadata handling, and output-path behavior

## Start With These Scenarios

- `loadout` status with and without an active loadout
- `loadout list` empty state, populated state, and verbose output
- `loadout create` happy path, duplicate names, clone behavior, and invalid fragment paths
- `loadout use` default output, `--std-out`, and `--output`
- `loadout sync` unchanged, changed fragments, no current loadout, `--std-out`, and `--output`
- `loadout add` happy path, insertion after a fragment, duplicate fragments, and missing fragment paths
- `loadout remove` happy path, removing last fragment, and removing a fragment that is not present

## Known Behavior Gaps Already Documented

`docs/test-plan.md` already records concrete failures that the implementation should address while landing the suite:

- stale sync warnings appearing when they should not
- `create` allowing nonexistent fragment paths
- `add` allowing nonexistent fragment paths
- `add` accepting duplicate fragments while reporting success
- `remove` reporting success for fragments that were never present
- `sync --output` reporting nothing to do instead of writing files to the requested directory
- contradictory warning output immediately after a successful sync

## Relevant Files

- `build.gradle.kts`
- `docs/test-plan.md`
- `src/commonMain/kotlin/cli/`
- `src/commonMain/kotlin/domain/`
- `src/commonMain/kotlin/data/`
- `src/nativeMain/kotlin/data/repository/`

## Acceptance Criteria

- The repo has a real BDD test suite with `e2e` and `unit` coverage
- The suite is runnable through Gradle
- The documented CLI behavior gaps are covered by tests and resolved in implementation
- The public CLI flags and command names remain unchanged unless a new explicit plan says otherwise
