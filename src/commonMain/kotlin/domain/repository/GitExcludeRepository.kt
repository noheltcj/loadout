package domain.repository

import domain.entity.GitExcludeFileUpdate
import domain.entity.GitExcludeSections
import domain.entity.GitRepositoryLayout
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

interface GitExcludeRepository {
    fun loadSections(layout: GitRepositoryLayout.Worktree): Result<GitExcludeSections, LoadoutError>

    /** Replaces Loadout's sections with [sections], keeps every other line, and writes only when the content changes. */
    fun saveSections(
        layout: GitRepositoryLayout.Worktree,
        sections: GitExcludeSections,
    ): Result<GitExcludeFileUpdate, LoadoutError>
}
