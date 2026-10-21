package e2e.support

import cli.Constants
import domain.entity.Loadout
import domain.entity.LocalLoadoutState
import domain.entity.OutputFilesWrite
import domain.entity.RepositorySettings
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

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

fun E2eScenario.shouldBeVisibleToGitStatus(
    path: String,
    workingDirectory: String = workspaceRoot,
) {
    untrackedPathsInGitStatus(workingDirectory) shouldContain path
}

fun E2eScenario.shouldBeHiddenFromGitStatus(
    vararg paths: String,
    workingDirectory: String = workspaceRoot,
) {
    untrackedPathsInGitStatus(workingDirectory).shouldNotContainAnyOf(paths.toList())
}

fun E2eScenario.shouldHideDirectoryFromGitStatus(directory: String) {
    untrackedPathsInGitStatus().filter { path -> path.startsWith("$directory/") }.shouldBeEmpty()
}

fun E2eScenario.shouldHaveNoUntrackedFilesInGitStatus() {
    untrackedPathsInGitStatus().shouldBeEmpty()
}

fun E2eScenario.shouldHaveWorktreeExcludeSection(worktreeKey: String) {
    readGitExcludeFile().shouldNotBeNull() shouldContain worktreeExcludeSectionStartMarker(worktreeKey)
}

fun E2eScenario.shouldNotHaveWorktreeExcludeSection(worktreeKey: String) {
    readGitExcludeFile().orEmpty() shouldNotContain worktreeExcludeSectionStartMarker(worktreeKey)
}

fun E2eScenario.shouldListOnlyInWorktreeExcludeSection(
    worktreeKey: String,
    vararg patterns: String,
) {
    readWorktreeExcludePatterns(worktreeKey).shouldContainExactly(*patterns)
}

fun E2eScenario.requireBackupPath(originalPath: String): String =
    listBackupFiles()
        .filter { backupPath ->
            backupPath.removePrefix("${Constants.BACKUPS_DIR}/").substringAfter('/') == "$originalPath.backup"
        }
        .shouldHaveSize(1)
        .single()

fun E2eScenario.requireBackupContent(originalPath: String): String =
    requireWorkspaceFile(requireBackupPath(originalPath))

fun E2eScenario.shouldHaveNoBackups() {
    listBackupFiles().shouldBeEmpty()
}

fun E2eScenario.shouldHaveExecutableWorkspaceFile(relativePath: String) {
    workspaceFileIsExecutable(relativePath) shouldBe true
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
    defaultOutputFilePaths.forEach { fileName ->
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
    fileName: String = claudeFilePath,
) {
    readGeneratedBody(fileName) shouldBe expected
}

fun E2eScenario.requireWorkspaceFile(path: String): String = readWorkspaceFile(path).shouldNotBeNull()

fun E2eScenario.requireRepositorySettings(): RepositorySettings = readRepositorySettings().shouldNotBeNull()

fun E2eScenario.requireLoadout(name: String): Loadout = readLoadout(name).shouldNotBeNull()

val LocalLoadoutState?.activeLoadoutName: String?
    get() = (this as? LocalLoadoutState.Active)?.loadoutName

private val LocalLoadoutState?.lastWrite: OutputFilesWrite?
    get() = (this as? LocalLoadoutState.Active)?.lastWrite

fun E2eScenario.requireLastComposedContentHash(): String =
    readLocalLoadoutState().lastWrite.shouldNotBeNull().composedContentHash

fun E2eScenario.requireGeneratedBody(fileName: String = claudeFilePath): String =
    readGeneratedBody(fileName).shouldNotBeNull()

fun E2eScenario.shouldHaveActiveLoadoutName(expected: String?) {
    readLocalLoadoutState().activeLoadoutName shouldBe expected
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

fun E2eScenario.shouldHaveUnchangedLastComposedContentHash(previousHash: String) {
    readLocalLoadoutState().lastWrite?.composedContentHash shouldBe previousHash
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
