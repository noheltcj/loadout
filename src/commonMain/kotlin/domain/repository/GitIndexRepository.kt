package domain.repository

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

/**
 * Reports whether git tracks a worktree path, so Loadout can warn when it removes a file git still knows about.
 */
interface GitIndexRepository {
    /** True when git's index records [path], which is worktree-relative. */
    fun isTracked(path: String): Result<Boolean, LoadoutError>
}
