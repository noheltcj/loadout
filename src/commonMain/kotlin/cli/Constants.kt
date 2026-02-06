package cli

/**
 * Central constants for Loadout CLI file paths and naming conventions.
 */
object Constants {
    /** Directory containing loadout definition files */
    const val LOADOUTS_DIR = ".loadouts"

    /** Directory containing fragment files */
    const val FRAGMENTS_DIR = "fragments"

    /** Configuration file tracking current loadout state */
    const val CONFIG_FILE = ".loadout.json"

    /** Primary output file for Claude Code */
    const val CLAUDE_MD = "CLAUDE.md"

    /** Secondary output file for other AI agents */
    const val AGENTS_MD = "AGENTS.md"

    /** Default output directory for composed .md files */
    const val DEFAULT_OUTPUT_DIR = "."
}
