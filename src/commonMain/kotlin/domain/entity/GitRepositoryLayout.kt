package domain.entity

/**
 * Every worktree of a repository shares one common directory. Domain code treats it as an opaque location that the git
 * repositories resolve git's own files against.
 */
sealed interface GitRepositoryLayout {
    data object NotARepository : GitRepositoryLayout

    sealed interface Worktree : GitRepositoryLayout {
        val commonDirectory: String
        val worktree: GitWorktree
    }

    data class MainWorktree(
        override val commonDirectory: String,
    ) : Worktree {
        override val worktree: GitWorktree
            get() = GitWorktree.Main
    }

    data class LinkedWorktree(
        override val commonDirectory: String,
        override val worktree: GitWorktree.Linked,
    ) : Worktree
}

sealed interface GitWorktree {
    data object Main : GitWorktree

    /** [id] is the worktree's directory name under the common directory's `worktrees/`. */
    data class Linked(
        val id: String,
    ) : GitWorktree
}
