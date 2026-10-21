package domain.repository

import domain.entity.GitRepositoryLayout
import domain.entity.GitWorktree
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

interface GitRepositoryLayoutRepository {
    fun resolveLayout(): Result<GitRepositoryLayout, LoadoutError>

    fun worktreeExists(
        layout: GitRepositoryLayout.Worktree,
        worktree: GitWorktree,
    ): Boolean
}
