package e2e.support

import cli.Constants
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

private const val GITIGNORE_PATH = ".gitignore"
private const val LOCAL_RUNTIME_STATE_GITIGNORE_HEADER = "# Loadout local runtime state"
private const val REPOSITORY_SETTINGS_GITIGNORE_HEADER = "# Loadout repository settings"
private const val LOCAL_ONLY_DEFINITIONS_GITIGNORE_HEADER = "# Loadout local-only definitions"

private val sharedModeGitignorePatterns =
    listOf(LOCAL_RUNTIME_STATE_GITIGNORE_HEADER, Constants.LOCAL_LOADOUT_STATE_FILE) + Constants.generatedMarkdownFiles
private val repositorySettingsGitignorePatterns =
    listOf(REPOSITORY_SETTINGS_GITIGNORE_HEADER, Constants.REPOSITORY_SETTINGS_FILE)
private val localOnlyGitignorePatterns =
    listOf(
        LOCAL_ONLY_DEFINITIONS_GITIGNORE_HEADER,
        "${Constants.LOADOUTS_DIR}/",
        "${Constants.FRAGMENTS_DIR}/",
    )

fun CommandResult.shouldHaveExitCode(expected: Int) {
    exitCode shouldBe expected
}

fun CommandResult.shouldContainInStdout(vararg snippets: String) {
    snippets.forEach { snippet ->
        stdout.shouldContain(snippet)
    }
}

fun CommandResult.shouldContainInStderr(vararg snippets: String) {
    snippets.forEach { snippet ->
        stderr.shouldContain(snippet)
    }
}

fun CommandResult.shouldContainInOutput(vararg snippets: String) {
    snippets.forEach { snippet ->
        output.shouldContain(snippet)
    }
}

fun CommandResult.shouldHaveNoUnexpectedStderr() {
    stderr shouldBe ""
}

fun CommandResult.shouldHaveStaleWarning() {
    shouldContainInStderr(staleWarningLine, staleResolutionLine)
}

fun CommandResult.shouldNotHaveStaleWarning() {
    stderr.shouldNotContain(staleWarningLine)
    stderr.shouldNotContain(staleResolutionLine)
}

fun E2eScenario.shouldHaveGitignoreEntries(vararg snippets: String) {
    val gitignore = readWorkspaceFile(GITIGNORE_PATH).shouldNotBeNull()
    snippets.forEach { snippet ->
        gitignore.shouldContain(snippet)
    }
}

fun E2eScenario.shouldNotHaveGitignoreEntries(vararg snippets: String) {
    val gitignore = readWorkspaceFile(GITIGNORE_PATH).shouldNotBeNull()
    snippets.forEach { snippet ->
        gitignore.shouldNotContain(snippet)
    }
}

fun E2eScenario.shouldContainLoadoutGitignorePatterns() {
    shouldHaveGitignoreEntries(*sharedModeGitignorePatterns.toTypedArray())
}

fun E2eScenario.shouldContainLocalOnlyGitignorePatterns() {
    shouldHaveGitignoreEntries(*localOnlyGitignorePatterns.toTypedArray())
}

fun E2eScenario.shouldContainRepositorySettingsGitignorePatterns() {
    shouldHaveGitignoreEntries(*repositorySettingsGitignorePatterns.toTypedArray())
}

fun E2eScenario.shouldNotIgnoreRepoManagedLoadoutFiles() {
    shouldNotHaveGitignoreEntries(
        "${Constants.LOADOUTS_DIR}/",
        "${Constants.FRAGMENTS_DIR}/",
        Constants.REPOSITORY_SETTINGS_FILE,
    )
}

fun E2eScenario.shouldHaveExecutableWorkspaceFile(relativePath: String) {
    workspaceFileIsExecutable(relativePath) shouldBe true
}

fun String.shouldContainExactlyOnce(snippet: String) {
    split(snippet).size.minus(1) shouldBe 1
}

fun E2eScenario.shouldContainSharedModeGitignorePatternsExactlyOnce() {
    val gitignore = readWorkspaceFile(GITIGNORE_PATH).shouldNotBeNull()
    sharedModeGitignorePatterns.forEach(gitignore::shouldContainExactlyOnce)
}

fun E2eScenario.shouldContainLocalModeGitignorePatternsExactlyOnce() {
    val gitignore = readWorkspaceFile(GITIGNORE_PATH).shouldNotBeNull()
    (sharedModeGitignorePatterns + repositorySettingsGitignorePatterns + localOnlyGitignorePatterns)
        .forEach(gitignore::shouldContainExactlyOnce)
}

private fun E2eScenario.readGeneratedMarkdownFile(
    fileName: String,
    directory: String?,
): String? =
    if (directory == null) {
        readGeneratedFile(fileName)
    } else {
        readGeneratedFileFromDirectory(directory, fileName)
    }

private fun E2eScenario.forEachGeneratedMarkdownFile(
    directory: String?,
    assertion: (String?) -> Unit,
) {
    Constants.generatedMarkdownFiles.forEach { fileName ->
        assertion(readGeneratedMarkdownFile(fileName, directory))
    }
}

fun E2eScenario.shouldHaveGeneratedFiles(directory: String? = null) {
    forEachGeneratedMarkdownFile(directory) { generatedFile ->
        generatedFile.shouldNotBeNull()
    }
}

fun E2eScenario.shouldNotHaveGeneratedFiles(directory: String? = null) {
    forEachGeneratedMarkdownFile(directory) { generatedFile ->
        generatedFile.shouldBeNull()
    }
}

fun E2eScenario.shouldHaveGeneratedBody(
    expected: String,
    fileName: String = Constants.CLAUDE_MD,
) {
    readGeneratedBody(fileName) shouldBe expected
}

fun E2eScenario.requireWorkspaceFile(path: String): String = readWorkspaceFile(path).shouldNotBeNull()

fun E2eScenario.requireLocalLoadoutState() = readLocalLoadoutState().shouldNotBeNull()

fun E2eScenario.requireLastComposedContentHash(): String =
    requireLocalLoadoutState().lastComposedContentHash.shouldNotBeNull()

fun E2eScenario.requireGeneratedBody(fileName: String = Constants.CLAUDE_MD): String =
    readGeneratedBody(fileName).shouldNotBeNull()

fun E2eScenario.shouldHaveGeneratedBodyInDirectory(
    directory: String,
    expected: String,
    fileName: String = Constants.CLAUDE_MD,
) {
    readGeneratedBodyFromDirectory(directory, fileName) shouldBe expected
}

fun E2eScenario.shouldHaveActiveLoadoutName(expected: String?) {
    readLocalLoadoutState()?.activeLoadoutName shouldBe expected
}

fun E2eScenario.shouldHaveRepositoryDefaultLoadoutName(expected: String?) {
    readRepositorySettings()?.defaultLoadoutName shouldBe expected
}

fun E2eScenario.shouldHaveGitLocalConfig(
    key: String,
    expected: String?,
    workingDirectory: String? = null,
) {
    readGitLocalConfig(key, workingDirectory = workingDirectory ?: workspaceRoot) shouldBe expected
}

fun E2eScenario.shouldHaveLastComposedContentHash() {
    readLocalLoadoutState()?.lastComposedContentHash.shouldNotBeNull()
}

fun E2eScenario.shouldHaveUnchangedLastComposedContentHash(previousHash: String?) {
    readLocalLoadoutState()?.lastComposedContentHash shouldBe previousHash
}

fun E2eScenario.shouldHaveLoadoutFragments(
    loadoutName: String,
    expected: List<String>,
) {
    readLoadout(loadoutName)?.fragments.shouldNotBeNull().shouldContainExactly(expected)
}

fun E2eScenario.shouldContainLoadoutFragmentsInOrder(
    loadoutName: String,
    expected: List<String>,
) {
    readLoadout(loadoutName)?.fragments.shouldNotBeNull().shouldContainInOrder(expected)
}
