# Loadout CLI Test Plan

This document defines a comprehensive test plan for the Loadout CLI, organized by functional concept. Each test follows a BDD-style naming convention and includes the command to execute, relevant tags, and expected behavior.

## Test Execution Guidelines

**IMPORTANT:** Unless a test explicitly expects specific warnings or errors as part of its expected behavior, the presence of **any unexpected warnings or errors** should be considered a **test failure**. This includes but is not limited to:

- Synchronization warnings
- File system warnings
- State inconsistency messages
- Unexpected error messages
- Stack traces or debug output

Tests should produce clean, expected output only. Any deviation from the expected behavior indicates a problem that needs to be addressed.

---

## Display & Status

### Test: Given no current loadout, when displaying status, then show appropriate message
**Setup:**
```bash
rm -f .loadout.json
```
**Command:** `loadout`
**Tags:** `display`, `status`, `initial_state`
**Expected:** Should display a message indicating no loadout is currently active.

### Test: Given an active loadout, when displaying status, then show current loadout details
**Setup:**
```bash
rm -f .loadouts/test-display.json
loadout create test-display --desc "Display test" --fragment fragments/base.md
loadout use test-display
```
**Command:** `loadout`
**Tags:** `display`, `status`, `active_loadout`
**Expected:** Should display the name of the currently active loadout and its composition metadata.

---

## Listing

### Test: Given no loadouts exist, when listing loadouts, then show empty state
**Setup:**
```bash
rm -rf .loadouts/*.json
```
**Command:** `loadout list`
**Tags:** `listing`, `empty_state`
**Expected:** Should display a message indicating no loadouts are available.
**ISSUE:** ⚠️ PARTIAL FAILURE - Unexpected synchronization warning appears even when no loadouts exist and .loadout.json references a deleted loadout.

### Test: Given multiple loadouts exist, when listing loadouts, then show all available loadouts
**Setup:**
```bash
rm -f .loadouts/list-test-*.json
loadout create list-test-alpha --desc "First test loadout"
loadout create list-test-beta --desc "Second test loadout"
loadout create list-test-gamma --desc "Third test loadout"
```
**Command:** `loadout list`
**Tags:** `listing`, `multiple_loadouts`
**Expected:** Should display a list of all available loadout names with their descriptions.

### Test: Given verbose flag, when listing loadouts, then show detailed information
**Setup:**
```bash
rm -f .loadouts/verbose-test-*.json
loadout create verbose-test-a --desc "Verbose test A" --fragment fragments/base.md
loadout create verbose-test-b --desc "Verbose test B" --fragment fragments/base.md --fragment fragments/coding-style.md
```
**Command:** `loadout list --verbose`
**Tags:** `listing`, `verbose`, `detailed_output`
**Expected:** Should display additional details about each loadout including fragments and metadata.

---

## Creation

### Test: Given a unique name, when creating empty loadout, then loadout is created successfully
**Setup:**
```bash
rm -f .loadouts/test-loadout.json
```
**Command:** `loadout create test-loadout`
**Tags:** `creation`, `empty_loadout`, `happy_path`
**Expected:** Should create a new empty loadout named "test-loadout" and confirm creation.

### Test: Given a unique name and description, when creating loadout, then loadout includes metadata
**Setup:**
```bash
rm -f .loadouts/test-loadout.json
```
**Command:** `loadout create test-loadout --desc "Test description"`
**Tags:** `creation`, `metadata`, `description`
**Expected:** Should create a new loadout with the specified description stored in metadata.

### Test: Given a unique name and fragments, when creating loadout, then loadout includes all fragments
**Setup:**
```bash
rm -f .loadouts/test-loadout.json
```
**Command:** `loadout create test-loadout --fragment fragments/base.md --fragment fragments/coding-style.md`
**Tags:** `creation`, `fragment_management`, `multiple_fragments`
**Expected:** Should create a new loadout containing both specified fragments in the order provided.

### Test: Given an existing loadout, when creating with clone option, then new loadout copies original
**Setup:**
```bash
rm -f .loadouts/test-loadout.json .loadouts/cloned-loadout.json
loadout create test-loadout --desc "Original loadout" --fragment fragments/base.md --fragment fragments/coding-style.md
```
**Command:** `loadout create cloned-loadout --clone test-loadout`
**Tags:** `creation`, `cloning`, `duplication`
**Expected:** Should create a new loadout that is an exact copy of the specified existing loadout.

### Test: Given a duplicate name, when creating loadout, then creation fails with error
**Setup:**
```bash
rm -f .loadouts/test-loadout.json
loadout create test-loadout --desc "Existing loadout"
```
**Command:** `loadout create test-loadout`
**Tags:** `creation`, `error_handling`, `duplicate_name`
**Expected:** Should fail with a clear error message indicating the loadout name already exists.

### Test: Given invalid fragment path, when creating loadout, then creation fails with error
**Setup:**
```bash
rm -f .loadouts/test-loadout.json
rm -f nonexistent/fragment.md
```
**Command:** `loadout create test-loadout --fragment nonexistent/fragment.md`
**Tags:** `creation`, `error_handling`, `invalid_path`
**Expected:** Should fail with a clear error message indicating the fragment path does not exist.
**ISSUE:** ❌ FAILURE - CLI allows creation with nonexistent fragment paths. No validation occurs during creation.

---

## Switching

### Test: Given an existing loadout, when switching to loadout, then loadout becomes active and files are written
**Setup:**
```bash
rm -f .loadouts/test-loadout.json
rm -f CLAUDE.md AGENTS.md
loadout create test-loadout --desc "Switching test" --fragment fragments/base.md
```
**Command:** `loadout use test-loadout`
**Tags:** `switching`, `composition`, `file_writing`
**Expected:** Should activate the specified loadout, compose its fragments, and write CLAUDE.md/AGENTS.md files.

### Test: Given a nonexistent loadout, when switching, then operation fails with error
**Setup:**
```bash
rm -f .loadouts/nonexistent-loadout.json
```
**Command:** `loadout use nonexistent-loadout`
**Tags:** `switching`, `error_handling`, `invalid_name`
**Expected:** Should fail with a clear error message indicating the loadout does not exist.

### Test: Given std-out flag, when switching loadout, then output to console without writing files
**Setup:**
```bash
rm -f .loadouts/test-loadout.json
loadout create test-loadout --desc "Stdout test" --fragment fragments/base.md
```
**Command:** `loadout use test-loadout --std-out`
**Tags:** `switching`, `preview`, `stdout`, `no_side_effects`
**Expected:** Should print the composed output to console without modifying any files on disk.
**ISSUE:** ⚠️ PARTIAL FAILURE - Unexpected synchronization warning appears despite correct behavior (no files written).

### Test: Given output directory override, when switching loadout, then files written to specified location
**Setup:**
```bash
rm -f .loadouts/test-loadout.json
loadout create test-loadout --desc "Output dir test" --fragment fragments/base.md
mkdir -p /tmp/custom-output
rm -f /tmp/custom-output/CLAUDE.md /tmp/custom-output/AGENTS.md
```
**Command:** `loadout use test-loadout --output /tmp/custom-output`
**Tags:** `switching`, `composition`, `output_override`
**Expected:** Should write composed files to the specified directory instead of the default location.

---

## Synchronization

### Test: Given an active loadout with unchanged fragments, when syncing, then confirms already synchronized
**Setup:**
```bash
rm -f .loadouts/sync-test.json
loadout create sync-test --desc "Sync test" --fragment fragments/base.md
loadout use sync-test
```
**Command:** `loadout sync`
**Tags:** `synchronization`, `no_changes`, `idempotent`
**Expected:** Should detect no changes and confirm loadout is already synchronized.

### Test: Given an active loadout with modified fragments, when syncing, then recomposes and updates files
**Setup:**
```bash
rm -f .loadouts/sync-test.json
echo "# Test Fragment\n\nTest content" > /tmp/test-sync-fragment.md
loadout create sync-test --desc "Sync test" --fragment /tmp/test-sync-fragment.md
loadout use sync-test
echo "# Test Fragment\n\nModified content" > /tmp/test-sync-fragment.md
```
**Command:** `loadout sync`
**Tags:** `synchronization`, `fragment_changes`, `recomposition`
**Expected:** Should detect changes in fragments, recompose the loadout, and update output files.
**ISSUE:** ⚠️ PARTIAL FAILURE - After successfully syncing and generating files, shows contradictory warning: "Current loadout fragments have changed since the last composition. To synchronize, run 'loadout sync'". The sync command just ran successfully but then warns to run sync again.

### Test: Given no active loadout, when syncing, then operation fails with error
**Setup:**
```bash
rm -f .loadout.json
```
**Command:** `loadout sync`
**Tags:** `synchronization`, `error_handling`, `no_active_loadout`
**Expected:** Should fail with a clear error message indicating no loadout is currently active.

### Test: Given std-out flag, when syncing, then output to console without writing files
**Setup:**
```bash
rm -f .loadouts/sync-test.json
loadout create sync-test --desc "Sync stdout test" --fragment fragments/base.md
loadout use sync-test
```
**Command:** `loadout sync --std-out`
**Tags:** `synchronization`, `preview`, `stdout`, `no_side_effects`
**Expected:** Should print the synchronized output to console without modifying files.

### Test: Given output directory override, when syncing, then files written to specified location
**Setup:**
```bash
rm -f .loadouts/sync-test.json
loadout create sync-test --desc "Sync output test" --fragment fragments/base.md
loadout use sync-test
mkdir -p /tmp/custom-output
rm -f /tmp/custom-output/CLAUDE.md /tmp/custom-output/AGENTS.md
```
**Command:** `loadout sync --output /tmp/custom-output`
**Tags:** `synchronization`, `output_override`
**Expected:** Should write synchronized files to the specified directory instead of the default location.
**ISSUE:** ❌ FAILURE - Sync with --output flag reports "Nothing to do" and does not write files to the custom output directory. The --output flag appears to be ignored by sync command.

---

## Fragment Addition

### Test: Given a valid fragment path, when adding to active loadout, then fragment is appended
**Setup:**
```bash
rm -f .loadouts/add-test.json
echo "# New Fragment\n\nContent" > /tmp/new-fragment.md
loadout create add-test --desc "Add test" --fragment fragments/base.md
loadout use add-test
```
**Command:** `loadout add /tmp/new-fragment.md --to add-test`
**Tags:** `fragment_management`, `addition`, `active_loadout`
**Expected:** Should add the specified fragment to the end of the current loadout's fragment list.

### Test: Given a target loadout, when adding fragment with --to option, then fragment added to specified loadout
**Setup:**
```bash
rm -f .loadouts/test-loadout.json
echo "# New Fragment\n\nContent" > /tmp/new-fragment.md
loadout create test-loadout --desc "Target loadout" --fragment fragments/base.md
```
**Command:** `loadout add /tmp/new-fragment.md --to test-loadout`
**Tags:** `fragment_management`, `addition`, `target_loadout`
**Expected:** Should add the fragment to the specified loadout regardless of which loadout is active.

### Test: Given an after position, when adding fragment, then fragment inserted at correct position
**Setup:**
```bash
rm -f .loadouts/position-test.json
echo "# New Fragment\n\nContent" > /tmp/new-fragment.md
loadout create position-test --desc "Position test" --fragment fragments/base.md --fragment fragments/coding-style.md
loadout use position-test
```
**Command:** `loadout add /tmp/new-fragment.md --to position-test --after fragments/base.md`
**Tags:** `fragment_management`, `addition`, `ordering`, `insertion`
**Expected:** Should insert the new fragment immediately after the specified existing fragment.

### Test: Given invalid fragment path, when adding, then operation fails with error
**Setup:**
```bash
rm -f .loadouts/error-test.json
rm -f nonexistent/fragment.md
loadout create error-test --desc "Error test"
loadout use error-test
```
**Command:** `loadout add nonexistent/fragment.md --to error-test`
**Tags:** `fragment_management`, `addition`, `error_handling`, `invalid_path`
**Expected:** Should fail with a clear error message indicating the fragment path does not exist.
**ISSUE:** ❌ FAILURE - CLI allows adding nonexistent fragment paths. No validation occurs during add operation.

### Test: Given duplicate fragment, when adding, then operation handles appropriately
**Setup:**
```bash
rm -f .loadouts/dup-test.json
loadout create dup-test --desc "Duplicate test" --fragment fragments/base.md
loadout use dup-test
```
**Command:** `loadout add fragments/base.md --to dup-test`
**Tags:** `fragment_management`, `addition`, `error_handling`, `duplicate`
**Expected:** Should reject the duplicate fragment.
**ISSUE:** ❌ FAILURE - CLI accepts the duplicate fragment instead of displaying an error, but doesn't actually add it to the loadout.

### Test: Given no active loadout and no --to option, when adding fragment, then operation fails with error
**Setup:**
```bash
rm -f .loadout.json
echo "# New Fragment\n\nContent" > /tmp/new-fragment.md
```
**Command:** `loadout add /tmp/new-fragment.md`
**Tags:** `fragment_management`, `addition`, `error_handling`, `no_target`
**Expected:** Should fail with a clear error message indicating no target loadout specified.

---

## Fragment Removal

### Test: Given an existing fragment, when removing from active loadout, then fragment is removed
**Setup:**
```bash
rm -f .loadouts/remove-test.json
loadout create remove-test --desc "Remove test" --fragment fragments/base.md --fragment fragments/coding-style.md
loadout use remove-test
```
**Command:** `loadout remove fragments/base.md --from remove-test`
**Tags:** `fragment_management`, `removal`, `active_loadout`
**Expected:** Should remove the specified fragment from the current loadout's fragment list.

### Test: Given a target loadout, when removing fragment with --from option, then fragment removed from specified loadout
**Setup:**
```bash
rm -f .loadouts/test-loadout.json
loadout create test-loadout --desc "Target removal test" --fragment fragments/base.md --fragment fragments/coding-style.md
```
**Command:** `loadout remove fragments/base.md --from test-loadout`
**Tags:** `fragment_management`, `removal`, `target_loadout`
**Expected:** Should remove the fragment from the specified loadout regardless of which loadout is active.

### Test: Given a nonexistent fragment, when removing, then operation fails with error
**Setup:**
```bash
rm -f .loadouts/error-remove-test.json
loadout create error-remove-test --desc "Error remove test" --fragment fragments/base.md
loadout use error-remove-test
```
**Command:** `loadout remove fragments/nonexistent.md --from error-remove-test`
**Tags:** `fragment_management`, `removal`, `error_handling`, `not_found`
**Expected:** Should fail with a clear error message indicating the fragment is not in the loadout.
**ISSUE:** ❌ FAILURE - CLI claims to have removed a nonexistent fragment with success message "Removed fragment..." but the loadout remains unchanged. No validation or error occurs.

### Test: Given no active loadout and no --from option, when removing fragment, then operation fails with error
**Setup:**
```bash
rm -f .loadout.json
```
**Command:** `loadout remove fragments/base.md`
**Tags:** `fragment_management`, `removal`, `error_handling`, `no_target`
**Expected:** Should fail with a clear error message indicating no target loadout specified.

### Test: Given last fragment in loadout, when removing, then loadout becomes empty
**Setup:**
```bash
rm -f .loadouts/last-fragment-test.json
loadout create last-fragment-test --desc "Last fragment test" --fragment fragments/base.md
loadout use last-fragment-test
```
**Command:** `loadout remove fragments/base.md --from last-fragment-test`
**Tags:** `fragment_management`, `removal`, `empty_result`
**Expected:** Should successfully remove the fragment leaving an empty but valid loadout.

---

## Error Handling & Edge Cases

### Test: Given invalid command, when executing, then show error and suggest help
**Setup:**
```bash
# No setup required - testing error handling
```
**Command:** `loadout invalid-command`
**Tags:** `error_handling`, `invalid_input`, `help_suggestion`
**Expected:** Should fail with error indicating invalid command and suggest using --help.

### Test: Given missing required argument, when executing command, then show error with usage
**Setup:**
```bash
# No setup required - testing error handling
```
**Command:** `loadout create`
**Tags:** `error_handling`, `missing_argument`, `validation`
**Expected:** Should fail with error indicating missing required name argument and show usage.

---

## Integration & Workflows

### Test: Given new project, when creating first loadout with fragments and using it, then complete workflow succeeds
**Setup:**
```bash
rm -f .loadouts/initial-test.json
rm -f CLAUDE.md AGENTS.md
```
**Commands:**
1. `loadout create initial_test.json --desc "My project setup" --fragment fragments/base.md`
2. `loadout use my-project`

**Tags:** `integration`, `workflow`, `complete_flow`, `first_use`
**Expected:** Should successfully create and activate a loadout, writing composed files to disk.

### Test: Given active loadout, when adding fragment and syncing, then changes are reflected
**Setup:**
```bash
rm -f .loadouts/workflow-test.json
loadout create workflow-test --desc "Workflow test" --fragment fragments/base.md
loadout use workflow-test
```
**Commands:**
1. `loadout add fragments/coding-style.md --to workflow-test`
2. `loadout sync`

**Tags:** `integration`, `workflow`, `fragment_management`, `synchronization`
**Expected:** Should add the fragment and recompose, reflecting the new content in output files.

### Test: Given multiple loadouts and loadout is active, when switching to another loadout, should update files
**Setup:**
```bash
rm -f .loadouts/loadout-a.json .loadouts/loadout-b.json
loadout create loadout-a --desc "Loadout A" --fragment fragments/base.md
loadout create loadout-b --desc "Loadout B" --fragment fragments/coding-style.md
loadout use loadout-a
```
**Command:** `loadout use loadout-b`
**Tags:** `integration`, `workflow`, `switching`, `state_management`
**Expected:** Should correctly switch contexts and compose the appropriate fragments for each loadout.

### Test: Given modified fragments, when syncing shows warning, then sync resolves warning
**Setup:**
```bash
rm -f .loadouts/warning-test.json
echo "# Test Fragment\n\nOriginal content" > /tmp/warning-test-fragment.md
loadout create warning-test --desc "Warning test" --fragment /tmp/warning-test-fragment.md
loadout use warning-test
```
**Commands:**
1. `echo "# Test Fragment\n\nModified content" > /tmp/warning-test-fragment.md`
2. `loadout list` (should show warning)
3. `loadout sync`
4. `loadout list` (warning should be gone)

**Tags:** `integration`, `workflow`, `synchronization`, `warning_resolution`
**Expected:** Should detect fragment changes, show warning, allow sync, and clear warning after sync.
