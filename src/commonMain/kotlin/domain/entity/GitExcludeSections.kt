package domain.entity

/**
 * The sections Loadout manages in git's `info/exclude` file. Lines outside these sections belong to the user.
 *
 * Every worktree shares the file, so each worktree lists the outputs it generates in its own section. The
 * repository section records the mode, which decides which of Loadout's own files stay out of git.
 */
data class GitExcludeSections(
    val repositorySection: RepositoryExcludeSection,
    val worktreeSections: Map<GitWorktree, List<String>>,
)

sealed interface RepositoryExcludeSection {
    data object Absent : RepositoryExcludeSection

    data class Present(
        val mode: RepositoryMode,
        val patterns: List<String>,
    ) : RepositoryExcludeSection
}

sealed interface GitExcludeFileUpdate {
    val excludeFilePath: String

    data class Updated(
        override val excludeFilePath: String,
    ) : GitExcludeFileUpdate

    data class Unchanged(
        override val excludeFilePath: String,
    ) : GitExcludeFileUpdate
}
