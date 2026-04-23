package cli

/**
 * Central constants for Loadout CLI file paths and naming conventions.
 */
object Constants {
    /** Directory containing loadout definition files */
    const val LOADOUTS_DIR = ".loadouts"

    /** Directory containing fragment files */
    const val FRAGMENTS_DIR = "fragments"

    /** Repository-owned settings shared through version control */
    const val REPOSITORY_SETTINGS_FILE = ".loadout.json"

    /** Local-only runtime state scoped to a single worktree */
    const val LOCAL_LOADOUT_STATE_FILE = ".loadout.local.json"

    /** Primary output file for Claude Code */
    const val CLAUDE_MD = "CLAUDE.md"

    /** Secondary output file for other AI agents */
    const val AGENTS_MD = "AGENTS.md"

    /** Tertiary output file for Gemini-compatible agents */
    const val GEMINI_MD = "GEMINI.md"

    /** Canonical generated Markdown outputs written by Loadout */
    val generatedMarkdownFiles = listOf(CLAUDE_MD, AGENTS_MD, GEMINI_MD)

    /** Default output directory for composed .md files */
    const val DEFAULT_OUTPUT_DIR = "."
}
