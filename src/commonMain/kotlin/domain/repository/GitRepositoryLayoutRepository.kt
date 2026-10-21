package domain.repository

import domain.entity.GitRepositoryLayout
import domain.entity.GitWorktree
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

interface GitRepositoryLayoutRepository {
    fun resolveLayout(): Result<GitRepositoryLayout, LoadoutError>

    /** Whether git still has [worktree]. The main worktree always exists; git forgets a linked one when it is removed. */
    fun worktreeExists(
        layout: GitRepositoryLayout.Worktree,
        worktree: GitWorktree,
    ): Boolean
}
