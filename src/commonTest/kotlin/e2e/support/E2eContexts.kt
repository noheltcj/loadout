@file:Suppress("ktlint:standard:property-naming")

package e2e.support

import cli.Constants

const val architectFragmentPath = "fragments/loadout-architect.md"
const val firstFragmentPath = "fragments/alpha.md"
const val secondFragmentPath = "fragments/beta.md"
const val thirdFragmentPath = "fragments/gamma.md"

const val firstFragmentContent = "## Alpha Fragment\n\nAlpha guidance"
const val secondFragmentContent = "## Beta Fragment\n\nBeta guidance"
const val thirdFragmentContent = "## Gamma Fragment\n\nGamma guidance"

const val existingStarterFragmentContent = "## Existing Architect\n\nKeep this content."

const val staleWarningLine = "Warning: Current loadout fragments have changed since the last composition."
const val staleResolutionLine = "To synchronize, run 'loadout sync' and restart your agent."

private fun StringBuilder.appendGeneratedFileStatuses(scenario: E2eScenario) {
    Constants.generatedMarkdownFiles.forEach { fileName ->
        append("\n")
        append(fileName)
        append(" exists: ")
        append(scenario.readGeneratedFile(fileName) != null)
    }
}

fun E2eScenario.givenStarterFragmentAlreadyExists(content: String = existingStarterFragmentContent) {
    seedFragment(architectFragmentPath, content)
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
    check(readConfig()?.currentLoadoutName == name) {
        buildString {
            append("Expected current loadout '$name' after setup, but config was ")
            append(readWorkspaceFile(Constants.CONFIG_FILE))
            append("\nCommand output:\n")
            append(useResult.output)
            appendGeneratedFileStatuses(this@givenCurrentLoadoutIsSet)
        }
    }
    Constants.generatedMarkdownFiles.forEach { fileName ->
        check(readGeneratedFile(fileName) != null) {
            buildString {
                append("Expected $fileName to exist after setting current loadout '$name'")
                append("\nCommand output:\n")
                append(useResult.output)
                append("\nConfig:\n")
                append(readWorkspaceFile(Constants.CONFIG_FILE))
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

fun E2eScenario.givenConfigPointsAtDeletedLoadout(name: String = "alpha") {
    givenCurrentLoadoutIsSet(name = name)
    deleteWorkspaceFile("${Constants.LOADOUTS_DIR}/$name.json")
}

fun E2eScenario.givenRepoInitializedInSharedMode() {
    val initResult = runCommand("init")
    check(initResult.exitCode == 0) {
        "Expected setup command 'init shared repo' to succeed, but got exit ${initResult.exitCode}:\n${initResult.output}"
    }
    check(readConfig()?.currentLoadoutName == "default") {
        buildString {
            append("Expected shared init to activate default loadout, but config was ")
            append(readWorkspaceFile(Constants.CONFIG_FILE))
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
    check(readConfig()?.currentLoadoutName == "default") {
        buildString {
            append("Expected local init to activate default loadout, but config was ")
            append(readWorkspaceFile(Constants.CONFIG_FILE))
            append("\nCommand output:\n")
            append(initResult.output)
            append("\nDefault loadout:\n")
            append(readWorkspaceFile("${Constants.LOADOUTS_DIR}/default.json"))
            appendGeneratedFileStatuses(this@givenRepoInitializedInLocalMode)
        }
    }
}
