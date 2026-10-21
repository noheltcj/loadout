package cli

/**
 * Central constants for Loadout CLI file paths and naming conventions.
 */
object Constants {
    /** Directory containing loadout definition files */
    const val LOADOUTS_DIR = ".loadouts"

    /** Repository-owned settings shared through version control */
    const val REPOSITORY_SETTINGS_FILE = ".loadout.json"

    /** Local-only runtime state scoped to a single worktree */
    const val LOCAL_LOADOUT_STATE_FILE = ".loadout.local.json"

    /** Copies of content Loadout replaced that belonged to the user; Loadout never deletes them */
    const val BACKUPS_DIR = ".loadout-backups"

    /** Tracked git hooks that keep generated output in sync on checkout and merge */
    const val HOOKS_DIR = ".githooks"
}
