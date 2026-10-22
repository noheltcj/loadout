package data.repository

import data.filesystem.normalizePathLexically
import domain.entity.GitRepositoryLayout
import domain.entity.GitWorktree
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.isAbsolutePath
import domain.repository.FileQueryRepository
import domain.repository.GitRepositoryLayoutRepository

private const val GIT_ENTRY = ".git"
private const val GIT_DIRECTORY_PREFIX = "gitdir:"
private const val COMMON_DIRECTORY_FILE = "commondir"
private const val LINKED_WORKTREES_DIRECTORY = "worktrees"

class FileBasedGitRepositoryLayoutRepository(
    private val fileQueryRepository: FileQueryRepository,
) : GitRepositoryLayoutRepository {
    override fun resolveLayout(): Result<GitRepositoryLayout, LoadoutError> =
        when {
            !fileQueryRepository.fileExists(GIT_ENTRY) ->
                Result.Success(GitRepositoryLayout.NotARepository)

            fileQueryRepository.isRegularFile(GIT_ENTRY) ->
                fileQueryRepository
                    .readFile(GIT_ENTRY)
                    .flatMap(::parseGitDirectory)
                    .flatMap(::resolveGitDirectory)

            else ->
                Result.Success(GitRepositoryLayout.MainWorktree(commonDirectory = GIT_ENTRY))
        }

    override fun worktreeExists(
        layout: GitRepositoryLayout.Worktree,
        worktree: GitWorktree,
    ): Boolean =
        when (worktree) {
            GitWorktree.Main -> true

            // Git deletes a linked worktree's administrative directory when it removes the worktree.
            is GitWorktree.Linked ->
                fileQueryRepository.fileExists("${layout.commonDirectory}/$LINKED_WORKTREES_DIRECTORY/${worktree.id}")
        }

    private fun resolveGitDirectory(gitDirectory: String): Result<GitRepositoryLayout, LoadoutError> {
        val commonDirectoryFile = "$gitDirectory/$COMMON_DIRECTORY_FILE"

        return when {
            !fileQueryRepository.fileExists(gitDirectory) ->
                Result.Error(
                    LoadoutError.ConfigurationError("$GIT_ENTRY points at a missing git directory: $gitDirectory")
                )

            fileQueryRepository.fileExists(commonDirectoryFile) ->
                fileQueryRepository.readFile(commonDirectoryFile).map { commonDirectory ->
                    GitRepositoryLayout.LinkedWorktree(
                        commonDirectory = resolveGitPath(base = gitDirectory, path = commonDirectory.trim()),
                        worktree = GitWorktree.Linked(id = gitDirectory.substringAfterLast('/')),
                    )
                }

            // Submodules and `--separate-git-dir` checkouts have no commondir file: the git directory is shared.
            else ->
                Result.Success(GitRepositoryLayout.MainWorktree(commonDirectory = gitDirectory))
        }
    }
}

private fun parseGitDirectory(gitFileContent: String): Result<String, LoadoutError> =
    gitFileContent
        .lineSequence()
        .firstOrNull()
        ?.trim()
        ?.takeIf { line -> line.startsWith(GIT_DIRECTORY_PREFIX) }
        ?.removePrefix(GIT_DIRECTORY_PREFIX)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { gitDirectory -> Result.Success(resolveGitPath(base = ".", path = gitDirectory)) }
        ?: Result.Error(LoadoutError.ConfigurationError("$GIT_ENTRY is a file without a '$GIT_DIRECTORY_PREFIX' line"))

private fun resolveGitPath(
    base: String,
    path: String,
): String = normalizePathLexically(if (isAbsolutePath(path)) path else "$base/$path")
