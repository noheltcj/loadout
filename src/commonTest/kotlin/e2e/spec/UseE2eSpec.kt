@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package e2e.spec

import e2e.support.E2eBehaviorSuite
import e2e.support.ScenarioSeed
import e2e.support.action
import e2e.support.firstFragmentContent
import e2e.support.firstFragmentPath
import e2e.support.givenCurrentLoadoutIsSet
import e2e.support.givenValidLoadout
import e2e.support.secondFragmentContent
import e2e.support.secondFragmentPath
import e2e.support.shouldContainInOutput
import e2e.support.shouldContainInStdout
import e2e.support.shouldHaveCurrentLoadoutName
import e2e.support.shouldHaveExitCode
import e2e.support.shouldHaveGeneratedBody
import e2e.support.shouldHaveGeneratedBodyInDirectory
import e2e.support.shouldHaveGeneratedFiles
import e2e.support.shouldNotHaveGeneratedFiles

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

                then("it does not change the current loadout") {
                    val currentScenario by memoizedAction(
                        "use",
                        "missing",
                        seed = {
                            givenCurrentLoadoutIsSet(
                                name = "current",
                                fragments = listOf(secondFragmentPath to secondFragmentContent)
                            )
                        }
                    )

                    currentScenario.scenario.shouldHaveCurrentLoadoutName("current")
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
                    execution.scenario.shouldHaveCurrentLoadoutName("alpha")
                }

                then("it writes CLAUDE.md and AGENTS.md to the default output directory") {
                    execution.scenario.shouldHaveGeneratedFiles()
                }
            }

            action("loadout use is run with --std-out") {
                val execution by memoizedAction("use", "alpha", "--std-out", seed = validLoadout)

                then("it prints the composed content") {
                    execution.result.shouldContainInStdout(firstFragmentContent)
                }

                then("it does not write CLAUDE.md") {
                    execution.scenario.shouldNotHaveGeneratedFiles()
                }

                then("it does not write AGENTS.md") {
                    execution.scenario.shouldNotHaveGeneratedFiles()
                }
            }

            given("another loadout is currently active") {
                val validLoadoutWithCurrent: ScenarioSeed = {
                    givenCurrentLoadoutIsSet(
                        name = "current",
                        fragments = listOf(secondFragmentPath to secondFragmentContent)
                    )
                    givenValidLoadout(name = "alpha", fragments = listOf(firstFragmentPath to firstFragmentContent))
                }

                action("loadout use is run with --std-out") {
                    val execution by memoizedAction("use", "alpha", "--std-out", seed = validLoadoutWithCurrent)

                    then("it does not change the current loadout") {
                        execution.scenario.shouldHaveCurrentLoadoutName("current")
                        execution.scenario.shouldHaveGeneratedBody(secondFragmentContent)
                    }
                }
            }

            action("loadout use is run with --output and a custom directory") {
                val execution by memoizedExecution(
                    seed = {
                        givenValidLoadout(name = "alpha", fragments = listOf(firstFragmentPath to firstFragmentContent))
                    }
                ) {
                    val outputDirectory = createCustomOutputDirectory()
                    writeWorkspaceFile(".output-dir", outputDirectory)
                    runCommand("use", "alpha", "--output", outputDirectory)
                }

                then("it writes CLAUDE.md to the requested directory") {
                    val outputDirectory = execution.scenario.readWorkspaceFile(".output-dir")!!
                    execution.scenario.shouldHaveGeneratedFiles(directory = outputDirectory)
                    execution.scenario.shouldHaveGeneratedBodyInDirectory(outputDirectory, firstFragmentContent)
                }

                then("it writes AGENTS.md to the requested directory") {
                    val outputDirectory = execution.scenario.readWorkspaceFile(".output-dir")!!
                    execution.scenario.shouldHaveGeneratedFiles(directory = outputDirectory)
                }

                then("it marks that loadout as current") {
                    execution.scenario.shouldHaveCurrentLoadoutName("alpha")
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
