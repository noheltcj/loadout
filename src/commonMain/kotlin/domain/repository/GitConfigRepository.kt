package domain.repository

import domain.entity.GitRepositoryLayout
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

interface GitConfigRepository {
    /** The configured `core.hooksPath`, or null when the repository does not set one. */
    fun readHooksPath(layout: GitRepositoryLayout.Worktree): Result<String?, LoadoutError>

    fun writeHooksPath(
        layout: GitRepositoryLayout.Worktree,
        hooksPath: String,
    ): Result<Unit, LoadoutError>
}
