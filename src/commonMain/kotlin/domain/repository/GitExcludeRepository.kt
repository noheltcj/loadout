package domain.repository

import domain.entity.GitExcludeFileUpdate
import domain.entity.GitExcludeSections
import domain.entity.GitRepositoryLayout
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

interface GitExcludeRepository {
    fun loadSections(layout: GitRepositoryLayout.Worktree): Result<GitExcludeSections, LoadoutError>

    /** Keeps every line outside Loadout's sections and writes only when the content changes. */
    fun saveSections(
        layout: GitRepositoryLayout.Worktree,
        sections: GitExcludeSections,
    ): Result<GitExcludeFileUpdate, LoadoutError>
}
