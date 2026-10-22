package domain.entity

/** Every worktree shares the exclude file, so each worktree lists the outputs it generates in its own section. */
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
