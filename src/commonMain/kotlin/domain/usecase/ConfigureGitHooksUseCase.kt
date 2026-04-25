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
            if (configuredHooksPath != null && configuredHooksPath != hooksDirectoryPath) {
                Result.Success(ConfigureGitHooksResult.AlreadyManagedByExternalTool)
            } else {
                setupHooks(hooksDirectoryPath, hooks)
                    .flatMap { updateConfiguredHooksPath(gitConfigPath, hooksDirectoryPath) }
                    .map { ConfigureGitHooksResult.Configured }
            }
        }
    }

    private fun setupHooks(
        hooksDirectoryPath: String,
        hooks: List<GitHookDefinition>,
    ): Result<Unit, LoadoutError> {
        return fileRepository.createDirectory(hooksDirectoryPath)
            .flatMap {
                hooks.forEach { hook ->
                    fileRepository.writeFile(hook.path, hook.content)
                        .flatMap { fileRepository.setExecutable(hook.path) }
                        .mapError { return@flatMap Result.Error(it) }
                }
                Result.Success(Unit)
            }
    }

    private fun resolveGitConfigPath(): String? =
        when {
            fileRepository.fileExists(".git/config") -> ".git/config"
            fileRepository.fileExists(".git") -> {
                when (val gitFileResult = fileRepository.readFile(".git")) {
                    is Result.Success -> {
                        val gitDirectoryPath = gitFileResult.value.removePrefix("gitdir:").trim()
                        if (gitDirectoryPath.isBlank()) {
                            null
                        } else {
                            "$gitDirectoryPath/config"
                        }
                    }
                    is Result.Error -> null
                }
            }
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

    private fun parseConfiguredHooksPath(configContent: String): String? {
        var inCoreSection = false

        configContent.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()

            if (line.startsWith("[") && line.endsWith("]")) {
                inCoreSection = line.equals("[core]", ignoreCase = true)
                return@forEach
            }

            if (inCoreSection && line.startsWith("hooksPath", ignoreCase = true)) {
                return line.substringAfter('=').trim().ifBlank { null }
            }
        }

        return null
    }

    private fun String.withConfiguredHooksPath(hooksPath: String): String {
        val originalLines = lines()
        val updatedLines = mutableListOf<String>()
        var lineIndex = 0
        var coreSectionFound = false
        var hooksPathWritten = false

        while (lineIndex < originalLines.size) {
            val line = originalLines[lineIndex]
            val trimmedLine = line.trim()

            if (trimmedLine.equals("[core]", ignoreCase = true)) {
                coreSectionFound = true
                updatedLines += line
                lineIndex += 1

                while (lineIndex < originalLines.size) {
                    val sectionLine = originalLines[lineIndex]
                    val trimmedSectionLine = sectionLine.trim()

                    if (trimmedSectionLine.startsWith("[") && trimmedSectionLine.endsWith("]")) {
                        break
                    }

                    if (trimmedSectionLine.startsWith("hooksPath", ignoreCase = true)) {
                        if (!hooksPathWritten) {
                            updatedLines += "\thooksPath = $hooksPath"
                            hooksPathWritten = true
                        }
                    } else {
                        updatedLines += sectionLine
                    }

                    lineIndex += 1
                }

                if (!hooksPathWritten) {
                    updatedLines += "\thooksPath = $hooksPath"
                    hooksPathWritten = true
                }

                continue
            }

            updatedLines += line
            lineIndex += 1
        }

        if (!coreSectionFound) {
            if (updatedLines.isNotEmpty() && updatedLines.last().isNotBlank()) {
                updatedLines += ""
            }
            updatedLines += "[core]"
            updatedLines += "\thooksPath = $hooksPath"
        }

        return updatedLines.joinToString(separator = "\n").let { content ->
            if (content.endsWith("\n")) {
                content
            } else {
                "$content\n"
            }
        }
    }
}
