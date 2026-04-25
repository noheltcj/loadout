package domain.usecase

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.FileRepository

data class GitHookDefinition(
    val path: String,
    val content: String,
)

sealed interface ConfigureGitHooksResult {
    data object Configured : ConfigureGitHooksResult
    data object AlreadyManagedByExternalTool : ConfigureGitHooksResult
    data object GitNotInitialized : ConfigureGitHooksResult
}

class ConfigureGitHooksUseCase(
    private val fileRepository: FileRepository,
) {
    operator fun invoke(
        hooksDirectoryPath: String,
        hooks: List<GitHookDefinition>,
    ): Result<ConfigureGitHooksResult, LoadoutError> {
        val gitConfigPath = resolveGitConfigPath() ?: return Result.Success(ConfigureGitHooksResult.GitNotInitialized)

        return readConfiguredHooksPath(gitConfigPath).flatMap { configuredHooksPath ->
            configuredHooksPath
                ?.takeUnless { it == hooksDirectoryPath }
                ?.let { Result.Success(ConfigureGitHooksResult.AlreadyManagedByExternalTool) }
                ?: setupHooks(hooksDirectoryPath, hooks)
                    .flatMap { updateConfiguredHooksPath(gitConfigPath, hooksDirectoryPath) }
                    .map { ConfigureGitHooksResult.Configured }
        }
    }

    private fun setupHooks(
        hooksDirectoryPath: String,
        hooks: List<GitHookDefinition>,
    ): Result<Unit, LoadoutError> =
        fileRepository.createDirectory(hooksDirectoryPath)
            .flatMap { hooks.installAll() }

    private fun List<GitHookDefinition>.installAll(): Result<Unit, LoadoutError> {
        val initialResult: Result<Unit, LoadoutError> = Result.Success(Unit)

        return fold(initialResult) { result, hook ->
            result.flatMap {
                fileRepository.writeFile(hook.path, hook.content)
                    .flatMap { fileRepository.setExecutable(hook.path) }
            }
        }
    }

    private fun resolveGitConfigPath(): String? =
        when {
            fileRepository.fileExists(".git/config") -> ".git/config"
            fileRepository.fileExists(".git") ->
                fileRepository.readFile(".git").fold(
                    onSuccess = { gitFileContent -> gitFileContent.gitDirectoryConfigPath() },
                    onError = { null },
                )
            else -> null
        }

    private fun readConfiguredHooksPath(gitConfigPath: String): Result<String?, LoadoutError> =
        fileRepository
            .readFile(gitConfigPath)
            .map { content -> parseConfiguredHooksPath(content) }

    private fun updateConfiguredHooksPath(
        gitConfigPath: String,
        hooksPath: String,
    ): Result<Unit, LoadoutError> =
        fileRepository
            .readFile(gitConfigPath)
            .map { content -> content.withConfiguredHooksPath(hooksPath) }
            .flatMap { updatedContent -> fileRepository.writeFile(gitConfigPath, updatedContent) }

    private fun parseConfiguredHooksPath(configContent: String): String? =
        configContent
            .lineSequence()
            .fold(GitConfigHooksPathParseState()) { state, rawLine ->
                state.next(rawLine)
            }
            .hooksPath
            ?.value

    private fun String.withConfiguredHooksPath(hooksPath: String): String {
        val rewriteState =
            lines()
                .fold(GitConfigRewriteState()) { state, line ->
                    state.append(line, hooksPath)
                }
                .finish(hooksPath)

        return rewriteState.lines
            .joinToString(separator = "\n")
            .withTrailingNewline()
    }
}

private fun String.gitDirectoryConfigPath(): String? =
    removePrefix("gitdir:")
        .trim()
        .ifBlank { null }
        ?.let { gitDirectoryPath -> "$gitDirectoryPath/config" }

private fun String.isSectionHeader(): Boolean = startsWith("[") && endsWith("]")

private fun String.isCoreSectionHeader(): Boolean = equals("[core]", ignoreCase = true)

private fun String.isHooksPathSetting(): Boolean =
    contains("=") && substringBefore("=").trim().equals("hooksPath", ignoreCase = true)

private fun String.gitConfigValue(): String? =
    substringAfter("=")
        .trim()
        .ifBlank { null }

private fun String.withTrailingNewline(): String =
    if (endsWith("\n")) {
        this
    } else {
        "$this\n"
    }

private fun hooksPathLine(hooksPath: String): String = "\thooksPath = $hooksPath"

private data class ParsedGitHooksPath(
    val value: String?,
)

private data class GitConfigHooksPathParseState(
    val inCoreSection: Boolean = false,
    val hooksPath: ParsedGitHooksPath? = null,
) {
    fun next(rawLine: String): GitConfigHooksPathParseState {
        if (hooksPath != null) {
            return this
        }

        val line = rawLine.trim()

        return when {
            line.isSectionHeader() ->
                copy(inCoreSection = line.isCoreSectionHeader())

            inCoreSection && line.isHooksPathSetting() ->
                copy(hooksPath = ParsedGitHooksPath(line.gitConfigValue()))

            else ->
                this
        }
    }
}

private data class GitConfigRewriteState(
    val lines: List<String> = emptyList(),
    val inCoreSection: Boolean = false,
    val coreSectionFound: Boolean = false,
    val hooksPathWritten: Boolean = false,
) {
    fun append(
        rawLine: String,
        hooksPath: String,
    ): GitConfigRewriteState {
        val line = rawLine.trim()

        return when {
            line.isSectionHeader() ->
                startSection(rawLine, hooksPath)

            inCoreSection && line.isHooksPathSetting() ->
                writeHooksPathIfNeeded(hooksPath)

            else ->
                copy(lines = lines + rawLine)
        }
    }

    fun finish(hooksPath: String): GitConfigRewriteState =
        when {
            inCoreSection && !hooksPathWritten ->
                writeHooksPath(hooksPath)

            !coreSectionFound ->
                appendCoreSection(hooksPath)

            else ->
                this
        }

    private fun startSection(
        rawLine: String,
        hooksPath: String,
    ): GitConfigRewriteState {
        val stateWithClosedCoreSection =
            if (inCoreSection && !hooksPathWritten) {
                writeHooksPath(hooksPath)
            } else {
                this
            }
        val enteringCoreSection = rawLine.trim().isCoreSectionHeader()

        return stateWithClosedCoreSection.copy(
            lines = stateWithClosedCoreSection.lines + rawLine,
            inCoreSection = enteringCoreSection,
            coreSectionFound = stateWithClosedCoreSection.coreSectionFound || enteringCoreSection,
        )
    }

    private fun writeHooksPathIfNeeded(hooksPath: String): GitConfigRewriteState =
        if (hooksPathWritten) {
            this
        } else {
            writeHooksPath(hooksPath)
        }

    private fun writeHooksPath(hooksPath: String): GitConfigRewriteState =
        copy(
            lines = lines + hooksPathLine(hooksPath),
            hooksPathWritten = true,
        )

    private fun appendCoreSection(hooksPath: String): GitConfigRewriteState {
        val spacedLines =
            if (lines.isNotEmpty() && lines.last().isNotBlank()) {
                lines + ""
            } else {
                lines
            }

        return copy(
            lines = spacedLines + "[core]" + hooksPathLine(hooksPath),
            inCoreSection = true,
            coreSectionFound = true,
            hooksPathWritten = true,
        )
    }
}
