@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package e2e.spec

import e2e.support.E2eBehaviorSuite
import e2e.support.ScenarioSeed
import e2e.support.action
import e2e.support.andThen
import e2e.support.firstFragmentContent
import e2e.support.firstFragmentPath
import e2e.support.givenCurrentLoadoutIsSet
import e2e.support.givenValidLoadout
import e2e.support.secondFragmentContent
import e2e.support.secondFragmentPath
import e2e.support.shouldContainInOutput
import e2e.support.shouldContainInStdout
import e2e.support.shouldHaveActiveLoadoutName
import e2e.support.shouldHaveExitCode
import e2e.support.shouldHaveGeneratedBody
import e2e.support.shouldHaveGeneratedBodyInDirectory
import e2e.support.shouldHaveGeneratedFiles
import e2e.support.shouldNotHaveGeneratedFiles
import io.kotest.matchers.nulls.shouldBeNull

private data class OutputDirectoryCapture(
    val outputDirectory: String,
)

class UseE2eSpec : E2eBehaviorSuite({
    context("loadout use spec") {
        given("the specified loadout is invalid") {
            action("loadout use is run") {
                val execution by memoizedAction("use", "missing")

                then("it outputs that the specified loadout was not found") {
                    execution.result.shouldContainInOutput("Loadout 'missing' not found")
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }
            }

            given("another loadout is currently active") {
                val invalidLoadoutWithCurrent: ScenarioSeed = {
                    givenCurrentLoadoutIsSet(
                        name = "current",
                        fragments = listOf(secondFragmentPath to secondFragmentContent)
                    )
                }

                action("loadout use is run") {
                    val execution by memoizedAction(
                        "use",
                        "missing",
                        seed = invalidLoadoutWithCurrent
                    )

                    then("it does not change the current loadout") {
                        execution.scenario.shouldHaveActiveLoadoutName("current")
                    }
                }
            }
        }

        given("the specified loadout is valid") {
            val validLoadout: ScenarioSeed = {
                givenValidLoadout(name = "alpha", fragments = listOf(firstFragmentPath to firstFragmentContent))
            }

            action("loadout use is run") {
                val execution by memoizedAction("use", "alpha", seed = validLoadout)

                then("it composes that loadout") {
                    execution.scenario.shouldHaveGeneratedBody(firstFragmentContent)
                }

                then("it marks that loadout as current") {
                    execution.scenario.shouldHaveActiveLoadoutName("alpha")
                }

                then("it does not create repository settings") {
                    execution.scenario.readRepositorySettings().shouldBeNull()
                }

                then("it writes CLAUDE.md, AGENTS.md, and GEMINI.md to the default output directory") {
                    execution.scenario.shouldHaveGeneratedFiles()
                }
            }

            action("loadout use is run with --std-out") {
                val execution by memoizedAction("use", "alpha", "--std-out", seed = validLoadout)

                then("it prints the composed content") {
                    execution.result.shouldContainInStdout(firstFragmentContent)
                }

                then("it does not write CLAUDE.md, AGENTS.md, or GEMINI.md") {
                    execution.scenario.shouldNotHaveGeneratedFiles()
                }
            }

            given("another loadout is currently active") {
                val validLoadoutWithCurrent: ScenarioSeed =
                    validLoadout.andThen {
                        givenCurrentLoadoutIsSet(
                            name = "current",
                            fragments = listOf(secondFragmentPath to secondFragmentContent)
                        )
                    }

                action("loadout use is run with --std-out") {
                    val execution by memoizedAction("use", "alpha", "--std-out", seed = validLoadoutWithCurrent)

                    then("it does not change the current loadout") {
                        execution.scenario.shouldHaveActiveLoadoutName("current")
                    }

                    then("it leaves the generated body unchanged") {
                        execution.scenario.shouldHaveGeneratedBody(secondFragmentContent)
                    }
                }
            }

            action("loadout use is run with --output and a custom directory") {
                val execution by memoizedCapturedExecution(
                    seed = validLoadout,
                    capture = {
                        OutputDirectoryCapture(outputDirectory = createCustomOutputDirectory())
                    },
                ) { captured ->
                    runCommand("use", "alpha", "--output", captured.outputDirectory)
                }

                then("it writes CLAUDE.md, AGENTS.md, and GEMINI.md to the requested directory") {
                    execution.scenario.shouldHaveGeneratedFiles(directory = execution.captured.outputDirectory)
                }

                then("it writes the expected composed body to the requested directory") {
                    execution.scenario.shouldHaveGeneratedBodyInDirectory(
                        execution.captured.outputDirectory,
                        firstFragmentContent,
                    )
                }

                then("it marks that loadout as current") {
                    execution.scenario.shouldHaveActiveLoadoutName("alpha")
                }

                then("it does not write repository settings") {
                    execution.scenario.readRepositorySettings().shouldBeNull()
                }
            }
        }

        given("composing the specified loadout fails") {
            action("loadout use is run") {
                val execution by memoizedAction(
                    "use",
                    "broken",
                    seed = {
                        seedLoadout(name = "broken", fragments = listOf(firstFragmentPath))
                    }
                )

                then("it outputs the composition error") {
                    execution.result.shouldContainInOutput("Fragment not found: $firstFragmentPath")
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }
            }
        }
    }
})
