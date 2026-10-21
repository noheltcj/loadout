package domain.usecase

import domain.entity.GitExcludeFileUpdate
import domain.entity.GitExcludeSections
import domain.entity.GitRepositoryLayout
import domain.entity.GitWorktree
import domain.entity.LoadoutFileLocations
import domain.entity.RepositoryExcludeSection
import domain.entity.RepositoryMode
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.OutputFilePath
import domain.repository.GitExcludeRepository
import domain.repository.GitRepositoryLayoutRepository

sealed interface RepositoryModeSelection {
    /** Falls back to shared mode when the exclude file records none. */
    data object Recorded : RepositoryModeSelection

    data class Requested(
        val mode: RepositoryMode,
    ) : RepositoryModeSelection
}

sealed interface ConfigureGitIgnoreRulesResult {
    data object NotAGitRepository : ConfigureGitIgnoreRulesResult

    data class Configured(
        val excludeFilePath: String,
    ) : ConfigureGitIgnoreRulesResult

    data class AlreadyConfigured(
        val excludeFilePath: String,
    ) : ConfigureGitIgnoreRulesResult

    /** Ignore rules never fail a command: callers warn and carry on. */
    data class Failed(
        val error: LoadoutError,
    ) : ConfigureGitIgnoreRulesResult
}

/**
 * Keeps Loadout's per-clone files out of git through `info/exclude`, so the team's `.gitignore` is never edited.
 */
class ConfigureGitIgnoreRulesUseCase(
    private val gitRepositoryLayoutRepository: GitRepositoryLayoutRepository,
    private val gitExcludeRepository: GitExcludeRepository,
    private val getRepositorySettings: GetRepairableRepositorySettingsUseCase,
    private val loadoutFileLocations: LoadoutFileLocations,
) {
    operator fun invoke(
        outputFiles: List<OutputFilePath>,
        modeSelection: RepositoryModeSelection,
    ): ConfigureGitIgnoreRulesResult =
        gitRepositoryLayoutRepository
            .resolveLayout()
            .flatMap { layout ->
                when (layout) {
                    GitRepositoryLayout.NotARepository ->
                        Result.Success(ConfigureGitIgnoreRulesResult.NotAGitRepository)

                    is GitRepositoryLayout.Worktree ->
                        configureExcludeFile(layout, outputFiles, modeSelection)
                }
            }
            .fold(
                onSuccess = { result -> result },
                onError = { error -> ConfigureGitIgnoreRulesResult.Failed(error) },
            )

    private fun configureExcludeFile(
        layout: GitRepositoryLayout.Worktree,
        outputFiles: List<OutputFilePath>,
        modeSelection: RepositoryModeSelection,
    ): Result<ConfigureGitIgnoreRulesResult, LoadoutError> =
        gitExcludeRepository
            .loadSections(layout)
            .flatMap { recordedSections ->
                repositorySection(modeSelection.modeFor(recordedSections.repositorySection))
                    .map { repositorySection ->
                        GitExcludeSections(
                            repositorySection = repositorySection,
                            worktreeSections =
                            reconcileWorktreeSections(
                                recordedSections = recordedSections.worktreeSections,
                                liveWorktrees = findLiveWorktrees(layout, recordedSections.worktreeSections.keys),
                                currentWorktree = layout.worktree,
                                outputFiles = outputFiles,
                            ),
                        )
                    }
            }
            .flatMap { sections -> gitExcludeRepository.saveSections(layout, sections) }
            .map { update ->
                when (update) {
                    is GitExcludeFileUpdate.Updated ->
                        ConfigureGitIgnoreRulesResult.Configured(update.excludeFilePath)

                    is GitExcludeFileUpdate.Unchanged ->
                        ConfigureGitIgnoreRulesResult.AlreadyConfigured(update.excludeFilePath)
                }
            }

    private fun findLiveWorktrees(
        layout: GitRepositoryLayout.Worktree,
        worktrees: Set<GitWorktree>,
    ): Set<GitWorktree> =
        worktrees.filter { worktree -> gitRepositoryLayoutRepository.worktreeExists(layout, worktree) }.toSet()

    private fun repositorySection(mode: RepositoryMode): Result<RepositoryExcludeSection, LoadoutError> {
        val sharedModePatterns =
            listOf(
                anchoredFilePattern(loadoutFileLocations.localStateFile),
                anchoredDirectoryPattern(loadoutFileLocations.backupsDirectory),
            )

        return when (mode) {
            RepositoryMode.Shared ->
                Result.Success(RepositoryExcludeSection.Present(mode, sharedModePatterns))

            // Local mode also keeps the files a shared repository would commit out of git.
            RepositoryMode.Local ->
                getRepositorySettings().map { repair ->
                    RepositoryExcludeSection.Present(
                        mode = mode,
                        patterns =
                        sharedModePatterns +
                            anchoredFilePattern(loadoutFileLocations.repositorySettingsFile) +
                            anchoredDirectoryPattern(loadoutFileLocations.loadoutsDirectory) +
                            anchoredDirectoryPattern(repair.settings.fragmentsHome),
                    )
                }
        }
    }
}

private fun reconcileWorktreeSections(
    recordedSections: Map<GitWorktree, List<String>>,
    liveWorktrees: Set<GitWorktree>,
    currentWorktree: GitWorktree,
    outputFiles: List<OutputFilePath>,
): Map<GitWorktree, List<String>> =
    recordedSections.filterKeys { worktree -> worktree in liveWorktrees } +
        (currentWorktree to outputFiles.map { outputFile -> anchoredFilePattern(outputFile.value) })

private fun RepositoryModeSelection.modeFor(recordedSection: RepositoryExcludeSection): RepositoryMode =
    when (this) {
        is RepositoryModeSelection.Requested -> mode
        RepositoryModeSelection.Recorded ->
            when (recordedSection) {
                RepositoryExcludeSection.Absent -> RepositoryMode.Shared
                is RepositoryExcludeSection.Present -> recordedSection.mode
            }
    }

// A leading slash anchors a pattern to the worktree root, so nested files with the same name stay visible.
// Loadout's paths use only [A-Za-z0-9._-] segments, so none of them needs gitignore escaping.
private fun anchoredFilePattern(path: String): String = "/$path"

private fun anchoredDirectoryPattern(path: String): String = "/$path/"
