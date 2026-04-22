package cli.commands

import cli.Constants
import cli.commands.extension.echoComposedFilesWriteResult
import cli.echoError
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import domain.entity.packaging.Result
import domain.repository.FileRepository
import domain.service.LoadoutCompositionService
import domain.service.LoadoutService

class InitCommand(
    private val fileRepository: FileRepository,
    private val loadoutService: LoadoutService,
    private val composeLoadout: LoadoutCompositionService,
    private val defaultOutputPaths: List<String>,
) : CliktCommand(
    name = COMMAND_NAME,
) {
    override fun help(context: Context): String =
        "Initialize Loadout in a project by setting up .gitignore, fragments, and default loadout"

    private val mode by option("--mode", "-m")
        .enum<InitMode>(ignoreCase = true)
        .default(InitMode.SHARED)
        .help("Mode: 'shared' (recommended) for team collaboration, 'local' for local-only configuration")

    override fun run() {
        setupGitignore()
        val fragmentCreated = createStarterFragment()
        configureTrackedHooksIfNeeded()
        setupDefaultLoadoutIfNeeded(fragmentCreated)
    }

    private fun setupGitignore() {
        val existingContent =
            when (val result = fileRepository.readFile(GITIGNORE_PATH)) {
                is Result.Success -> result.value
                is Result.Error -> ""
            }

        val patterns = mode.gitignorePatterns
        val newPatterns =
            patterns.filter { pattern ->
                pattern.isBlank() || !existingContent.contains(pattern.trim())
            }

        if (newPatterns.all { it.isBlank() || existingContent.contains(it.trim()) }) {
            echo("  .gitignore already configured for Loadout (${mode.displayName} mode)")
            return
        }

        val updatedContent =
            buildString {
                if (existingContent.isNotBlank()) {
                    append(existingContent)
                    if (!existingContent.endsWith("\n")) {
                        append("\n")
                    }
                    append("\n")
                }

                append("# Loadout CLI - ${mode.displayName} Mode\n")
                newPatterns.forEach { pattern ->
                    append(pattern)
                    append("\n")
                }
            }

        when (val writeResult = fileRepository.writeFile(GITIGNORE_PATH, updatedContent)) {
            is Result.Success -> {
                echo("  Added Loadout patterns to .gitignore (${mode.displayName} mode)")
            }
            is Result.Error -> {
                echo("Failed to write .gitignore: ${writeResult.error.message}", err = true)
                throw ProgramResult(1)
            }
        }
    }

    private fun createStarterFragment(): Boolean {
        if (fileRepository.fileExists(ARCHITECT_FRAGMENT_PATH)) {
            echo("  Starter fragment already exists at $ARCHITECT_FRAGMENT_PATH")
            return false
        }

        when (val result = fileRepository.createDirectory(Constants.FRAGMENTS_DIR)) {
            is Result.Success -> { /* Directory created or already exists */ }
            is Result.Error -> {
                echo("Failed to create fragments directory: ${result.error.message}", err = true)
                throw ProgramResult(1)
            }
        }

        when (val result = fileRepository.writeFile(ARCHITECT_FRAGMENT_PATH, ARCHITECT_FRAGMENT_CONTENT)) {
            is Result.Success -> {
                echo("  Created starter fragment at $ARCHITECT_FRAGMENT_PATH")
                return true
            }
            is Result.Error -> {
                echo("Failed to create starter fragment: ${result.error.message}", err = true)
                throw ProgramResult(1)
            }
        }
    }

    private fun setupDefaultLoadoutIfNeeded(fragmentCreated: Boolean) {
        val loadouts =
            when (val result = loadoutService.getAllLoadouts()) {
                is Result.Success -> result.value
                is Result.Error -> {
                    echo("Failed to check existing loadouts: ${result.error.message}", err = true)
                    throw ProgramResult(1)
                }
            }

        if (loadouts.isNotEmpty()) {
            configureRepoDefaultForExistingLoadouts(loadouts.map { loadout -> loadout.name })

            if (fragmentCreated) {
                echo("")
                echo("Existing loadouts found. Link the new fragment with:")
                echo("  loadout link $ARCHITECT_FRAGMENT_PATH --to <loadout-name>")
            }

            if (mode == InitMode.SHARED && loadouts.size > 1) {
                echo("Set the repository default loadout with:")
                echo("  loadout config --default-loadout <name>")
            }
            return
        }

        when (
            val createResult =
                loadoutService.createLoadout(
                    name = DEFAULT_LOADOUT_NAME,
                    description = DEFAULT_LOADOUT_DESCRIPTION,
                    fragments = listOf(ARCHITECT_FRAGMENT_PATH)
                )
        ) {
            is Result.Success -> {
                val loadout = createResult.value
                echo("  Created '$DEFAULT_LOADOUT_NAME' loadout with starter fragment")

                when (val composeResult = composeLoadout(loadout)) {
                    is Result.Success -> {
                        val composedOutput = composeResult.value
                        when (
                            val setResult =
                                loadoutService.setCurrentLoadout(
                                    composedOutput,
                                    defaultOutputPaths
                                )
                        ) {
                            is Result.Success -> {
                                if (mode == InitMode.SHARED) {
                                    when (
                                        val repoDefaultResult = loadoutService.setRepositoryDefaultLoadoutName(
                                            DEFAULT_LOADOUT_NAME
                                        )
                                    ) {
                                        is Result.Success -> { /* Repo default seeded */ }
                                        is Result.Error -> {
                                            echoError(repoDefaultResult.error)
                                            throw ProgramResult(1)
                                        }
                                    }
                                }

                                echoComposedFilesWriteResult(
                                    result = setResult.value,
                                    loadoutName = DEFAULT_LOADOUT_NAME,
                                    composedContentLength = composedOutput.content.length
                                )
                            }
                            is Result.Error -> {
                                echo("Failed to activate loadout: ${setResult.error.message}", err = true)
                                throw ProgramResult(1)
                            }
                        }
                    }
                    is Result.Error -> {
                        echo("Failed to compose loadout: ${composeResult.error.message}", err = true)
                        throw ProgramResult(1)
                    }
                }
            }
            is Result.Error -> {
                echo("Failed to create default loadout: ${createResult.error.message}", err = true)
                throw ProgramResult(1)
            }
        }

        echo("")
        echo("Loadout initialized successfully!")
        echo("Note: ${mode.completionNote}")
    }

    private fun configureRepoDefaultForExistingLoadouts(loadoutNames: List<String>) {
        if (mode != InitMode.SHARED) {
            return
        }

        when (loadoutNames.size) {
            0 -> return
            1 -> {
                when (val result = loadoutService.setRepositoryDefaultLoadoutName(loadoutNames.single())) {
                    is Result.Success -> { /* Repo default set */ }
                    is Result.Error -> {
                        echoError(result.error)
                        throw ProgramResult(1)
                    }
                }
            }
            else -> {
                when (val result = loadoutService.setRepositoryDefaultLoadoutName(null)) {
                    is Result.Success -> { /* Repo default intentionally unset */ }
                    is Result.Error -> {
                        echoError(result.error)
                        throw ProgramResult(1)
                    }
                }
            }
        }
    }

    private fun configureTrackedHooksIfNeeded() {
        if (mode != InitMode.SHARED) {
            return
        }

        val gitConfigPath = resolveGitConfigPath() ?: return
        val configuredHooksPath =
            when (val result = readConfiguredHooksPath(gitConfigPath)) {
                is Result.Success -> result.value
                is Result.Error -> {
                    echoError(result.error)
                    throw ProgramResult(1)
                }
            }

        if (configuredHooksPath != null && configuredHooksPath != HOOKS_DIRECTORY_PATH) {
            echo("Git hooks are already managed by core.hooksPath = $configuredHooksPath")
            echo("To use Loadout-managed tracked hooks instead, point core.hooksPath at $HOOKS_DIRECTORY_PATH")
            return
        }

        when (val directoryResult = fileRepository.createDirectory(HOOKS_DIRECTORY_PATH)) {
            is Result.Success -> { /* Hook directory created or already exists */ }
            is Result.Error -> {
                echoError(directoryResult.error)
                throw ProgramResult(1)
            }
        }

        writeHookFile(POST_CHECKOUT_HOOK_PATH, postCheckoutHookScript())
        writeHookFile(POST_MERGE_HOOK_PATH, postMergeHookScript())

        when (val result = updateConfiguredHooksPath(gitConfigPath, HOOKS_DIRECTORY_PATH)) {
            is Result.Success -> { /* Git hook path configured */ }
            is Result.Error -> {
                echoError(result.error)
                throw ProgramResult(1)
            }
        }
    }

    private fun writeHookFile(path: String, content: String) {
        when (val writeResult = fileRepository.writeFile(path, content)) {
            is Result.Success -> { /* Hook file written */ }
            is Result.Error -> {
                echoError(writeResult.error)
                throw ProgramResult(1)
            }
        }

        when (val executableResult = fileRepository.setExecutable(path)) {
            is Result.Success -> { /* Hook file marked executable */ }
            is Result.Error -> {
                echoError(executableResult.error)
                throw ProgramResult(1)
            }
        }
    }

    private fun resolveGitConfigPath(): String? =
        when {
            fileRepository.fileExists(GIT_CONFIG_PATH) -> GIT_CONFIG_PATH
            fileRepository.fileExists(GIT_PATH) -> {
                when (val gitFileResult = fileRepository.readFile(GIT_PATH)) {
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

    private fun readConfiguredHooksPath(gitConfigPath: String): Result<String?, domain.entity.error.LoadoutError> =
        fileRepository
            .readFile(gitConfigPath)
            .map { content -> parseConfiguredHooksPath(content) }

    private fun updateConfiguredHooksPath(
        gitConfigPath: String,
        hooksPath: String,
    ): Result<Unit, domain.entity.error.LoadoutError> =
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

    private fun postCheckoutHookScript(): String =
        $$"""
            #!/bin/sh
            if [ "$3" = "0" ]; then
              exit 0
            fi

            if [ -n "$LOADOUT_BIN" ]; then
                helper_path="$LOADOUT_BIN"
            else
                helper_path="loadout"
                if ! command -v "$helper_path" >/dev/null 2>&1; then
                    # GUI client fallbacks (common absolute install paths)
                    # Currently a bit brittle, but no current maintainers use git GUI clients
                    for p in \
                        "/opt/homebrew/bin/loadout" \
                        "/usr/local/bin/loadout" \
                        "$HOME/.local/bin/loadout"
                    do
                        if [ -x "$p" ]; then
                            helper_path="$p"
                            break
                        fi
                    done
                fi
            fi

            if command -v "$helper_path" >/dev/null 2>&1; then
                "$helper_path" sync --auto >/dev/null || exit 0
            else
                echo "[Loadout] CLI not found. Skipping auto-sync." >&2
                exit 0
            fi
        """.trimIndent() + "\n"

    private fun postMergeHookScript(): String =
        $$"""
            #!/bin/sh

            if [ -n "$LOADOUT_BIN" ]; then
                helper_path="$LOADOUT_BIN"
            else
                helper_path="loadout"
                if ! command -v "$helper_path" >/dev/null 2>&1; then
                    # GUI client fallbacks (common absolute install paths)
                    # Currently a bit brittle, but no current maintainers use git GUI clients
                    for p in \
                        "/opt/homebrew/bin/loadout" \
                        "/usr/local/bin/loadout" \
                        "$HOME/.local/bin/loadout"
                    do
                        if [ -x "$p" ]; then
                            helper_path="$p"
                            break
                        fi
                    done
                fi
            fi

            if command -v "$helper_path" >/dev/null 2>&1; then
                "$helper_path" sync --auto >/dev/null || exit 0
            else
                echo "[Loadout] CLI not found. Skipping auto-sync." >&2
                exit 0
            fi
        """.trimIndent() + "\n"

    companion object {
        private const val COMMAND_NAME = "init"
        private const val GIT_PATH = ".git"
        private const val GIT_CONFIG_PATH = ".git/config"
        private const val GITIGNORE_PATH = ".gitignore"
        private const val HOOKS_DIRECTORY_PATH = ".githooks"
        private const val POST_CHECKOUT_HOOK_PATH = "$HOOKS_DIRECTORY_PATH/post-checkout"
        private const val POST_MERGE_HOOK_PATH = "$HOOKS_DIRECTORY_PATH/post-merge"
        private const val ARCHITECT_FRAGMENT_FILENAME = "loadout-architect.md"
        private const val ARCHITECT_FRAGMENT_PATH = "${Constants.FRAGMENTS_DIR}/$ARCHITECT_FRAGMENT_FILENAME"
        private const val DEFAULT_LOADOUT_NAME = "default"
        private const val DEFAULT_LOADOUT_DESCRIPTION = "Default loadout"

        private val ARCHITECT_FRAGMENT_CONTENT =
            """
            |## Loadout Architect
            |
            |You are working in a project that uses Loadout CLI to manage composable system prompts.
            |
            |### Managing Loadouts
            |
            |Use these commands to manage your loadouts:
            |- `loadout` - Display current loadout status
            |- `loadout list` - List all available loadouts
            |- `loadout use <name>` - Switch to a different loadout
            |- `loadout create <name> --desc "Description"` - Create a new loadout
            |- `loadout remove <name>` - Remove a loadout
            |- `loadout link <fragment-path> --to <loadout>` - Link a fragment into a loadout
            |- `loadout unlink <fragment-path> --from <loadout>` - Unlink a fragment from a loadout
            |- `loadout sync` - Re-compose and synchronize after fragment changes
            |
            |### Fragment Guidelines
            |
            |Fragments are modular markdown files that compose into the final CLAUDE.md, AGENTS.md, and GEMINI.md:
            |- Keep fragments focused on a single concern (coding style, project structure, etc.)
            |- Use clear, concise language that AI agents can follow
            |- Store fragments in `fragments/`
            |
            |### Loadout Guidelines
            |
            |- Create task-specific loadouts (e.g., "engineering", "code-review", "documentation")
            |- Run `loadout sync` after modifying any fragment content
            """.trimMargin()
    }
}

enum class InitMode(
    val displayName: String,
    val completionNote: String,
) {
    SHARED(
        displayName = "Shared",
        completionNote = "In shared mode, loadout configurations are committed and shared with your team."
    ),
    LOCAL(
        displayName = "Local",
        completionNote = "In local mode, loadout configurations are not shared with your team."
    ),
    ;

    val gitignorePatterns: List<String>
        get() =
            when (this) {
                SHARED ->
                    listOf(GITIGNORE_HEADER, Constants.LOCAL_LOADOUT_STATE_FILE) + Constants.generatedMarkdownFiles
                LOCAL ->
                    listOf(GITIGNORE_HEADER, Constants.LOCAL_LOADOUT_STATE_FILE) +
                        Constants.generatedMarkdownFiles +
                        listOf(
                            "",
                            REPOSITORY_SETTINGS_HEADER,
                            Constants.REPOSITORY_SETTINGS_FILE,
                            "",
                            LOCAL_ONLY_GITIGNORE_HEADER,
                            "${Constants.LOADOUTS_DIR}/",
                            "${Constants.FRAGMENTS_DIR}/",
                        )
            }

    companion object {
        private const val GITIGNORE_HEADER = "# Loadout local runtime state"
        private const val REPOSITORY_SETTINGS_HEADER = "# Loadout repository settings"
        private const val LOCAL_ONLY_GITIGNORE_HEADER = "# Loadout local-only definitions"
    }
}
