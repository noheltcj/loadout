package domain.usecase

import domain.entity.GitRepositoryLayout
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.FileMutationRepository
import domain.repository.GitConfigRepository
import domain.repository.GitRepositoryLayoutRepository

data class GitHookDefinition(
    val path: String,
    val content: String,
)

sealed interface ConfigureGitHooksResult {
    data object Configured : ConfigureGitHooksResult
    data object AlreadyManagedByExternalTool : ConfigureGitHooksResult
    data object NotAGitRepository : ConfigureGitHooksResult
}

class ConfigureGitHooksUseCase(
    private val fileMutationRepository: FileMutationRepository,
    private val gitRepositoryLayoutRepository: GitRepositoryLayoutRepository,
    private val gitConfigRepository: GitConfigRepository,
) {
    operator fun invoke(
        hooksDirectoryPath: String,
        hooks: List<GitHookDefinition>,
    ): Result<ConfigureGitHooksResult, LoadoutError> =
        gitRepositoryLayoutRepository.resolveLayout().flatMap { layout ->
            when (layout) {
                GitRepositoryLayout.NotARepository ->
                    Result.Success(ConfigureGitHooksResult.NotAGitRepository)

                is GitRepositoryLayout.Worktree ->
                    configureHooks(layout, hooksDirectoryPath, hooks)
            }
        }

    private fun configureHooks(
        layout: GitRepositoryLayout.Worktree,
        hooksDirectoryPath: String,
        hooks: List<GitHookDefinition>,
    ): Result<ConfigureGitHooksResult, LoadoutError> =
        gitConfigRepository.readHooksPath(layout).flatMap { configuredHooksPath ->
            configuredHooksPath
                ?.takeUnless { it == hooksDirectoryPath }
                ?.let { Result.Success(ConfigureGitHooksResult.AlreadyManagedByExternalTool) }
                ?: setupHooks(hooksDirectoryPath, hooks)
                    .flatMap { gitConfigRepository.writeHooksPath(layout, hooksDirectoryPath) }
                    .map { ConfigureGitHooksResult.Configured }
        }

    private fun setupHooks(
        hooksDirectoryPath: String,
        hooks: List<GitHookDefinition>,
    ): Result<Unit, LoadoutError> =
        fileMutationRepository.createDirectory(hooksDirectoryPath)
            .flatMap { hooks.installAll() }

    private fun List<GitHookDefinition>.installAll(): Result<Unit, LoadoutError> {
        val initialResult: Result<Unit, LoadoutError> = Result.Success(Unit)

        return fold(initialResult) { result, hook ->
            result.flatMap {
                fileMutationRepository.writeFile(hook.path, hook.content)
                    .flatMap { fileMutationRepository.setExecutable(hook.path) }
            }
        }
    }
}
