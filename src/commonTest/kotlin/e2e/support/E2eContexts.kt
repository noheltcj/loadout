@file:Suppress("PropertyName", "ktlint:standard:property-naming")

package e2e.support

import cli.Constants
import domain.entity.RepositorySettings
import domain.policy.formatBackupTimestamp

const val architectFragmentPath = "fragments/loadout-architect.md"
const val firstFragmentPath = "fragments/alpha.md"
const val secondFragmentPath = "fragments/beta.md"
const val thirdFragmentPath = "fragments/gamma.md"
const val repositorySettingsPath = ".loadout.json"
const val hooksDirectoryPath = ".githooks"
const val postCheckoutHookPath = ".githooks/post-checkout"
const val postMergeHookPath = ".githooks/post-merge"
const val gitignorePath = ".gitignore"
const val nestedAgentFilePath = "packages/api/CLAUDE.md"
const val linkedWorktreeName = "feature"

const val claudeFilePath = "CLAUDE.md"
const val agentsFilePath = "AGENTS.md"
const val geminiFilePath = "GEMINI.md"
const val copilotInstructionsFilePath = ".github/copilot-instructions.md"
val defaultOutputFilePaths = listOf(claudeFilePath, agentsFilePath, geminiFilePath)

const val localSourcePath = "source.md"
const val localSourceContent = "## Local guidance\n\nKeep domain boundaries explicit.\n"

const val handWrittenAgentGuidance = "# Team guidance\n\nPrefer small pull requests.\n"
const val outputFileEdit = "\nA local note for this clone only.\n"

const val firstFragmentContent = "## Alpha Fragment\n\nAlpha guidance"
const val secondFragmentContent = "## Beta Fragment\n\nBeta guidance"
const val thirdFragmentContent = "## Gamma Fragment\n\nGamma guidance"

const val staleWarningLine = "Warning: Current loadout fragments have changed since the last composition."
const val staleResolutionLine = "To synchronize, run 'loadout sync' and restart your agent."

fun worktreeExcludeSectionStartMarker(worktreeKey: String): String =
    "# >>> loadout (worktree: $worktreeKey) - managed by Loadout, do not edit"

private fun StringBuilder.appendGeneratedFileStatuses(scenario: E2eScenario) {
    defaultOutputFilePaths.forEach { fileName ->
        append("\n")
        append(fileName)
        append(" exists: ")
        append(scenario.readGeneratedFile(fileName) != null)
    }
}

fun E2eScenario.givenExistingLoadoutsAlreadyExistBeforeInit() {
    seedFragment(firstFragmentPath, firstFragmentContent)
    seedLoadout(
        name = "existing",
        description = "Existing loadout",
        fragments = listOf(firstFragmentPath)
    )
}

fun E2eScenario.givenValidLoadout(
    name: String = "alpha",
    description: String = "",
    fragments: List<Pair<String, String>> = listOf(firstFragmentPath to firstFragmentContent),
) {
    fragments.forEach { (path, content) ->
        seedFragment(path, content)
    }

    seedLoadout(
        name = name,
        description = description,
        fragments = fragments.map { it.first }
    )
}

fun E2eScenario.givenSourceLoadoutExists(
    name: String = "source",
    description: String = "Source loadout",
    fragments: List<Pair<String, String>> = listOf(firstFragmentPath to firstFragmentContent),
) {
    givenValidLoadout(name = name, description = description, fragments = fragments)
}

fun E2eScenario.givenTwoValidLoadoutsExist() {
    givenValidLoadout(
        name = "alpha",
        description = "Alpha loadout",
        fragments = listOf(firstFragmentPath to firstFragmentContent)
    )
    givenValidLoadout(
        name = "beta",
        description = "Beta loadout",
        fragments = listOf(secondFragmentPath to secondFragmentContent)
    )
}

fun E2eScenario.givenTwoLoadoutsExistAndAlphaIsCurrent() {
    givenTwoValidLoadoutsExist()
    requireSetupCommand("use", "alpha")
}

fun E2eScenario.givenHandWrittenAgentFileExists(
    path: String = claudeFilePath,
    content: String = handWrittenAgentGuidance,
) {
    writeWorkspaceFile(path, content)
}

/** Appends [outputFileEdit] to an output file Loadout generated, keeping its header. */
fun E2eScenario.givenOutputFileWasEdited(path: String) {
    val generatedContent = readWorkspaceFile(path)
    check(generatedContent != null) { "Expected $path to exist before editing it" }
    writeWorkspaceFile(path, generatedContent + outputFileEdit)
}

/** Covers the next minute, so a command run during this scenario finds its timestamp folder taken. */
fun E2eScenario.givenBackupFoldersAlreadyExistForTheCurrentTime() {
    val now = currentTimeMillis()
    (-BACKUP_FOLDER_CLOCK_SKEW_SECONDS..BACKUP_FOLDER_WINDOW_SECONDS).forEach { offsetSeconds ->
        createWorkspaceDirectory("${Constants.BACKUPS_DIR}/${formatBackupTimestamp(now + offsetSeconds * 1_000L)}")
    }
}

fun E2eScenario.givenFileExistsOutsideWorkspace(
    relativePath: String,
    content: String,
): String {
    writeHomeFile(relativePath, content)
    return homePath(relativePath)
}

fun E2eScenario.givenCurrentLoadoutIsSet(
    name: String = "alpha",
    description: String = "",
    fragments: List<Pair<String, String>> = listOf(firstFragmentPath to firstFragmentContent),
) {
    givenValidLoadout(name = name, description = description, fragments = fragments)
    val useResult = runCommand("use", name)
    check(useResult.exitCode == 0) {
        "Expected setup command 'use $name' to succeed, but got exit ${useResult.exitCode}:\n${useResult.output}"
    }
    check(readLocalLoadoutState().activeLoadoutName == name) {
        buildString {
            append("Expected active loadout '$name' after setup, but local state was ")
            append(readWorkspaceFile(Constants.LOCAL_LOADOUT_STATE_FILE))
            append("\nCommand output:\n")
            append(useResult.output)
            appendGeneratedFileStatuses(this@givenCurrentLoadoutIsSet)
        }
    }
    defaultOutputFilePaths.forEach { fileName ->
        check(readGeneratedFile(fileName) != null) {
            buildString {
                append("Expected $fileName to exist after setting current loadout '$name'")
                append("\nCommand output:\n")
                append(useResult.output)
                append("\nLocal state:\n")
                append(readWorkspaceFile(Constants.LOCAL_LOADOUT_STATE_FILE))
            }
        }
    }
}

fun E2eScenario.givenCurrentLoadoutFragmentsHaveChangedSinceLastComposition(
    name: String = "alpha",
    updatedContent: String = "## Alpha Fragment\n\nUpdated guidance",
) {
    givenCurrentLoadoutIsSet(name = name)
    seedFragment(firstFragmentPath, updatedContent)
}

fun E2eScenario.givenCurrentLoadoutIsAlreadySynchronized(name: String = "alpha") {
    givenCurrentLoadoutIsSet(name = name)
}

/** Deletes [firstFragmentPath], which the current loadout that [givenCurrentLoadoutIsSet] seeds links. */
fun E2eScenario.givenCurrentLoadoutFragmentWasDeleted() {
    deleteWorkspaceFile(firstFragmentPath)
}

fun E2eScenario.givenLocalStateIsUnreadable() {
    writeWorkspaceFile(Constants.LOCAL_LOADOUT_STATE_FILE, "{ this is not json")
}

fun E2eScenario.givenConfigPointsAtDeletedLoadout(name: String = "alpha") {
    givenCurrentLoadoutIsSet(name = name)
    deleteWorkspaceFile("${Constants.LOADOUTS_DIR}/$name.json")
}

fun E2eScenario.givenRepoInitializedInSharedMode() {
    val initResult = runCommand("init")
    check(initResult.exitCode == 0) {
        "Expected setup command 'init shared repo' to succeed, but got exit ${initResult.exitCode}:\n${initResult.output}"
    }
    check(readLocalLoadoutState().activeLoadoutName == "default") {
        buildString {
            append("Expected shared init to activate default loadout, but local state was ")
            append(readWorkspaceFile(Constants.LOCAL_LOADOUT_STATE_FILE))
            append("\nCommand output:\n")
            append(initResult.output)
            append("\nDefault loadout:\n")
            append(readWorkspaceFile("${Constants.LOADOUTS_DIR}/default.json"))
            appendGeneratedFileStatuses(this@givenRepoInitializedInSharedMode)
        }
    }
}

fun E2eScenario.givenRepoInitializedInLocalMode() {
    val initResult = runCommand("init", "--mode", "local")
    check(initResult.exitCode == 0) {
        "Expected setup command 'init local repo' to succeed, but got exit ${initResult.exitCode}:\n${initResult.output}"
    }
    check(readLocalLoadoutState().activeLoadoutName == "default") {
        buildString {
            append("Expected local init to activate default loadout, but local state was ")
            append(readWorkspaceFile(Constants.LOCAL_LOADOUT_STATE_FILE))
            append("\nCommand output:\n")
            append(initResult.output)
            append("\nDefault loadout:\n")
            append(readWorkspaceFile("${Constants.LOADOUTS_DIR}/default.json"))
            appendGeneratedFileStatuses(this@givenRepoInitializedInLocalMode)
        }
    }
}

fun E2eScenario.givenGitRepositoryExists() {
    initializeGitRepository()
}

fun E2eScenario.givenUninitializedGitRepository() {
    givenGitRepositoryExists()
    writeWorkspaceFile(gitignorePath, "*.log\n")
    writeWorkspaceFile("README.md", "# Project\n")
    commitAllFiles("initial commit")
}

fun E2eScenario.givenInitializedGitRepository() {
    givenUninitializedGitRepository()
    requireSetupCommand("init")
    commitAllFiles("initialize loadout")
}

fun E2eScenario.givenGitRepositoryInitializedInLocalMode() {
    givenUninitializedGitRepository()
    requireSetupCommand("init", "--mode", "local")
}

fun E2eScenario.linkedWorktreePath(name: String = linkedWorktreeName): String = homePath("linked-worktrees/$name")

fun E2eScenario.givenLinkedWorktreeWasAdded(name: String = linkedWorktreeName) {
    requireSetupGit("worktree", "add", "--detach", linkedWorktreePath(name))
}

fun E2eScenario.givenLinkedWorktreeWasInitializedAndThenRemoved(name: String = linkedWorktreeName) {
    requireSetupCommand("init", workingDirectory = linkedWorktreePath(name))
    check(readGitExcludeFile().orEmpty().contains(worktreeExcludeSectionStartMarker("worktrees/$name"))) {
        "Expected init in linked worktree '$name' to add its exclude section, but the exclude file was:\n" +
            readGitExcludeFile()
    }
    requireSetupGit("worktree", "remove", "--force", linkedWorktreePath(name))
}

fun E2eScenario.givenRepositoryDefaultLoadoutIsSet(name: String) {
    requireSetupCommand("config", "--default-loadout", name)
}

fun E2eScenario.givenFragmentsHomeIsConfigured(path: String) {
    requireSetupCommand("config", "--fragments-home", path)
}

fun E2eScenario.givenRepositoryOutputFilesAreSet(vararg outputFiles: String) {
    requireSetupCommand("config", "--output-files", outputFiles.joinToString(","))
}

fun E2eScenario.givenUserOutputFilesAreSet(vararg outputFiles: String) {
    requireSetupCommand("config", "--user", "--output-files", outputFiles.joinToString(","))
}

fun E2eScenario.givenUserSettingsFileIsUnreadable() {
    writeUserSettingsFile("{ this is not json")
}

fun E2eScenario.givenNoUserConfigDirectoryCanBeDetermined() {
    removeUserConfigDirectoryVariables()
}

fun E2eScenario.givenCurrentLoadoutWasSynced() {
    requireSetupCommand("sync")
}

/** Lists [outputFiles] in the repository settings as a hand edit would, without validating them. */
fun E2eScenario.givenRepositorySettingsListOutputFiles(vararg outputFiles: String) {
    editRepositorySettingsByHand { settings -> settings.copy(outputFiles = outputFiles.toList()) }
}

/** Trusts [hosts] in the repository settings as a hand edit would, without validating them. */
fun E2eScenario.givenRepositorySettingsTrustFragmentHosts(vararg hosts: String) {
    editRepositorySettingsByHand { settings -> settings.copy(trustedFragmentSourceHosts = hosts.toList()) }
}

fun E2eScenario.givenFragmentSourceHostIsTrusted(host: String) {
    requireSetupCommand("config", "--trust-fragment-host", host)
}

fun E2eScenario.givenMaximumFragmentCopyBytesIsConfigured(bytes: Int) {
    requireSetupCommand("config", "--maximum-fragment-bytes", bytes.toString())
}

fun E2eScenario.givenDirectoryExistsOutsideWorkspace(relativePath: String): String {
    val absolutePath = homePath(relativePath)
    val result = runExternalCommand("mkdir", "-p", absolutePath)
    check(result.exitCode == 0) {
        "Expected setup 'mkdir -p $absolutePath' to succeed, but got exit ${result.exitCode}:\n${result.output}"
    }
    return absolutePath
}

fun E2eScenario.givenLocalMarkdownSourceExists(
    path: String = localSourcePath,
    content: String = localSourceContent,
) {
    writeWorkspaceFile(path, content)
}

fun E2eScenario.givenSymbolicLinkExists(
    linkPath: String,
    targetPath: String,
) {
    linkPath
        .substringBeforeLast('/', missingDelimiterValue = "")
        .takeIf(String::isNotBlank)
        ?.let(::createWorkspaceDirectory)

    val result = runExternalCommand("ln", "-s", targetPath, workspacePath(linkPath))
    check(result.exitCode == 0) {
        "Expected setup 'ln -s $targetPath $linkPath' to succeed, but got exit ${result.exitCode}:\n${result.output}"
    }
}

private const val BACKUP_FOLDER_CLOCK_SKEW_SECONDS = 2L
private const val BACKUP_FOLDER_WINDOW_SECONDS = 60L

private fun E2eScenario.editRepositorySettingsByHand(edit: (RepositorySettings) -> RepositorySettings) {
    writeRepositorySettings(edit(readRepositorySettings() ?: RepositorySettings()))
}

private fun E2eScenario.requireSetupCommand(
    vararg args: String,
    workingDirectory: String = workspaceRoot,
) {
    val result = runCommand(*args, workingDirectory = workingDirectory)
    check(result.exitCode == 0) {
        "Expected setup command '${args.joinToString(" ")}' to succeed, " +
            "but got exit ${result.exitCode}:\n${result.output}"
    }
}

private fun E2eScenario.requireSetupGit(vararg args: String) {
    val result = runGit(*args)
    check(result.exitCode == 0) {
        "Expected setup 'git ${args.joinToString(" ")}' to succeed, but got exit ${result.exitCode}:\n${result.output}"
    }
}
