package e2e.support

import cli.Constants
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainInOrder
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

fun E2eScenario.shouldHaveGitignoreEntries(vararg snippets: String) {
    val gitignore = readWorkspaceFile(".gitignore").shouldNotBeNull()
    snippets.forEach { snippet ->
        gitignore.shouldContain(snippet)
    }
}

fun E2eScenario.shouldNotHaveGitignoreEntries(vararg snippets: String) {
    val gitignore = readWorkspaceFile(".gitignore").shouldNotBeNull()
    snippets.forEach { snippet ->
        gitignore.shouldNotContain(snippet)
    }
}

fun E2eScenario.shouldContainLoadoutGitignorePatterns() {
    shouldHaveGitignoreEntries("# Loadout CLI", ".loadout.json", "CLAUDE.md", "AGENTS.md")
}

fun E2eScenario.shouldContainLocalOnlyGitignorePatterns() {
    shouldHaveGitignoreEntries("# Loadout configuration (local-only)", ".loadouts/", "fragments/")
}

fun E2eScenario.shouldNotIgnoreRepoManagedLoadoutFiles() {
    shouldNotHaveGitignoreEntries(".loadouts/", "fragments/")
}

fun String.shouldContainExactlyOnce(snippet: String) {
    split(snippet).size.minus(1) shouldBe 1
}

fun E2eScenario.shouldContainSharedModeGitignorePatternsExactlyOnce() {
    val gitignore = readWorkspaceFile(".gitignore").shouldNotBeNull()
    gitignore.shouldContainExactlyOnce("# Loadout CLI")
    gitignore.shouldContainExactlyOnce(".loadout.json")
    gitignore.shouldContainExactlyOnce("CLAUDE.md")
    gitignore.shouldContainExactlyOnce("AGENTS.md")
}

fun E2eScenario.shouldContainLocalModeGitignorePatternsExactlyOnce() {
    val gitignore = readWorkspaceFile(".gitignore").shouldNotBeNull()
    gitignore.shouldContainExactlyOnce("# Loadout CLI")
    gitignore.shouldContainExactlyOnce(".loadout.json")
    gitignore.shouldContainExactlyOnce("CLAUDE.md")
    gitignore.shouldContainExactlyOnce("AGENTS.md")
    gitignore.shouldContainExactlyOnce("# Loadout configuration (local-only)")
    gitignore.shouldContainExactlyOnce(".loadouts/")
    gitignore.shouldContainExactlyOnce("fragments/")
}

fun E2eScenario.shouldHaveGeneratedFiles(directory: String? = null) {
    val claude =
        if (directory == null) {
            readGeneratedFile(Constants.CLAUDE_MD)
        } else {
            readGeneratedFileFromDirectory(directory, Constants.CLAUDE_MD)
        }
    val agents =
        if (directory == null) {
            readGeneratedFile(Constants.AGENTS_MD)
        } else {
            readGeneratedFileFromDirectory(directory, Constants.AGENTS_MD)
        }

    claude.shouldNotBeNull()
    agents.shouldNotBeNull()
}

fun E2eScenario.shouldNotHaveGeneratedFiles(directory: String? = null) {
    val claude =
        if (directory == null) {
            readGeneratedFile(Constants.CLAUDE_MD)
        } else {
            readGeneratedFileFromDirectory(directory, Constants.CLAUDE_MD)
        }
    val agents =
        if (directory == null) {
            readGeneratedFile(Constants.AGENTS_MD)
        } else {
            readGeneratedFileFromDirectory(directory, Constants.AGENTS_MD)
        }

    claude.shouldBeNull()
    agents.shouldBeNull()
}

fun E2eScenario.shouldHaveGeneratedBody(
    expected: String,
    fileName: String = Constants.CLAUDE_MD,
) {
    readGeneratedBody(fileName) shouldBe expected
}

fun E2eScenario.shouldHaveGeneratedBodyInDirectory(
    directory: String,
    expected: String,
    fileName: String = Constants.CLAUDE_MD,
) {
    readGeneratedBodyFromDirectory(directory, fileName) shouldBe expected
}

fun E2eScenario.shouldHaveCurrentLoadoutName(expected: String?) {
    readConfig()?.currentLoadoutName shouldBe expected
}

fun E2eScenario.shouldHaveCompositionHash() {
    readConfig()?.compositionHash.shouldNotBeNull()
}

fun E2eScenario.shouldHaveUnchangedCompositionHash(previousHash: String?) {
    readConfig()?.compositionHash shouldBe previousHash
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
