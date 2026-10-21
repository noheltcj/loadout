package data.repository

import domain.entity.GitRepositoryLayout
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.FileMutationRepository
import domain.repository.FileQueryRepository
import domain.repository.GitConfigRepository

private const val CONFIG_FILE = "config"

/** Git reads config from the common directory, so every worktree of a repository shares one hooks path. */
class FileBasedGitConfigRepository(
    private val fileQueryRepository: FileQueryRepository,
    private val fileMutationRepository: FileMutationRepository,
) : GitConfigRepository {
    override fun readHooksPath(layout: GitRepositoryLayout.Worktree): Result<String?, LoadoutError> =
        fileQueryRepository
            .readFile(layout.configFilePath)
            .map { content -> parseConfiguredHooksPath(content) }

    override fun writeHooksPath(
        layout: GitRepositoryLayout.Worktree,
        hooksPath: String,
    ): Result<Unit, LoadoutError> =
        fileQueryRepository
            .readFile(layout.configFilePath)
            .map { content -> content.withConfiguredHooksPath(hooksPath) }
            .flatMap { updatedContent -> fileMutationRepository.writeFile(layout.configFilePath, updatedContent) }

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

private val GitRepositoryLayout.Worktree.configFilePath: String
    get() = "$commonDirectory/$CONFIG_FILE"

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
