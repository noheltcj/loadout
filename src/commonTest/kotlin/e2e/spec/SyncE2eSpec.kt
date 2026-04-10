@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package e2e.spec

import cli.Constants
import domain.entity.LoadoutConfig
import e2e.support.E2eBehaviorSuite
import e2e.support.ScenarioSeed
import e2e.support.action
import e2e.support.firstFragmentContent
import e2e.support.firstFragmentPath
import e2e.support.givenConfigPointsAtDeletedLoadout
import e2e.support.givenCurrentLoadoutFragmentsHaveChangedSinceLastComposition
import e2e.support.givenCurrentLoadoutIsAlreadySynchronized
import e2e.support.shouldContainInOutput
import e2e.support.shouldContainInStdout
import e2e.support.shouldHaveCurrentLoadoutName
import e2e.support.shouldHaveExitCode
import e2e.support.shouldHaveGeneratedBody
import e2e.support.shouldHaveGeneratedBodyInDirectory
import e2e.support.shouldHaveGeneratedFiles
import e2e.support.shouldHaveStaleWarning
import e2e.support.shouldHaveUnchangedCompositionHash
import e2e.support.shouldNotHaveStaleWarning
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain

class SyncE2eSpec : E2eBehaviorSuite({
    context("loadout sync spec") {
        given("no current loadout is set") {
            action("loadout sync is run") {
                val execution by memoizedAction("sync")

                then("it outputs that no current loadout is set") {
                    execution.result.shouldContainInOutput("No current loadout set.")
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }
            }
        }

        given("the config points at a deleted loadout") {
            action("loadout sync is run") {
                val execution by memoizedAction(
                    "sync",
                    seed = {
                        givenConfigPointsAtDeletedLoadout(name = "alpha")
                    }
                )

                then("it outputs that the current loadout no longer exists") {
                    execution.result.shouldContainInOutput("Loadout 'alpha' not found")
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }
            }
        }

        given("composing the current loadout fails") {
            val brokenCurrentLoadout: ScenarioSeed = {
                seedLoadout(name = "broken", fragments = listOf(firstFragmentPath))
                writeConfig(LoadoutConfig(currentLoadoutName = "broken", compositionHash = null))
            }

            action("loadout sync is run") {
                val execution by memoizedAction("sync", seed = brokenCurrentLoadout)

                then("it outputs the composition error") {
                    execution.result.shouldContainInOutput("Fragment not found: $firstFragmentPath")
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }
            }
        }

        given("the current loadout is already synchronized") {
            val currentLoadoutIsAlreadySynchronized: ScenarioSeed = {
                givenCurrentLoadoutIsAlreadySynchronized(name = "alpha")
            }

            action("loadout sync is run") {
                val execution by memoizedExecution(seed = currentLoadoutIsAlreadySynchronized) {
                    writeWorkspaceFile("scratch/before-claude.txt", readGeneratedFile(Constants.CLAUDE_MD).orEmpty())
                    runCommand("sync")
                }

                then("it outputs that the current loadout is active and up to date") {
                    execution.result.shouldContainInStdout("Loadout `alpha` is active and up to date. Nothing to do.")
                }

                then("it does not rewrite the output files") {
                    execution.scenario.readGeneratedFile(Constants.CLAUDE_MD) shouldBe
                        execution.scenario.readWorkspaceFile("scratch/before-claude.txt")
                }
            }

            action("one of the output files is manually deleted") {
                val execution by memoizedExecution(seed = currentLoadoutIsAlreadySynchronized) {
                    deleteWorkspaceFile(Constants.CLAUDE_MD)
                    runCommand("sync")
                }

                then("it regenerates the missing file") {
                    execution.scenario.readGeneratedFile(Constants.CLAUDE_MD).shouldNotBeNull()
                }

                then("it outputs that it rewrote the files") {
                    execution.result.shouldContainInStdout("Generated files")
                    execution.result.stdout.shouldNotContain("is active and up to date. Nothing to do.")
                }
            }

            action("loadout sync is run with --std-out") {
                val execution by memoizedExecution(seed = currentLoadoutIsAlreadySynchronized) {
                    writeWorkspaceFile("scratch/before-claude.txt", readGeneratedFile(Constants.CLAUDE_MD).orEmpty())
                    writeWorkspaceFile("scratch/before-hash.txt", readConfig()?.compositionHash ?: "<null>")
                    runCommand("sync", "--std-out")
                }

                then("it prints the current composed content") {
                    execution.result.shouldContainInStdout(firstFragmentContent)
                }

                then("it does not write files") {
                    execution.scenario.readGeneratedFile(Constants.CLAUDE_MD) shouldBe
                        execution.scenario.readWorkspaceFile("scratch/before-claude.txt")
                }

                then("it does not change the stored composition hash") {
                    val previousHash =
                        execution.scenario.readWorkspaceFile("scratch/before-hash.txt")?.takeUnless { it == "<null>" }
                    execution.scenario.shouldHaveUnchangedCompositionHash(previousHash)
                }
            }

            action("loadout sync is run with --output and a custom directory") {
                val execution by memoizedExecution(seed = currentLoadoutIsAlreadySynchronized) {
                    writeWorkspaceFile("scratch/before-hash.txt", readConfig()?.compositionHash ?: "<null>")
                    val outputDirectory = createCustomOutputDirectory()
                    writeWorkspaceFile("scratch/output-dir.txt", outputDirectory)
                    runCommand("sync", "--output", outputDirectory)
                }

                then("it writes CLAUDE.md to the requested directory") {
                    val outputDirectory = execution.scenario.readWorkspaceFile("scratch/output-dir.txt")!!
                    execution.scenario.shouldHaveGeneratedFiles(directory = outputDirectory)
                    execution.scenario.shouldHaveGeneratedBodyInDirectory(outputDirectory, firstFragmentContent)
                }

                then("it writes AGENTS.md to the requested directory") {
                    val outputDirectory = execution.scenario.readWorkspaceFile("scratch/output-dir.txt")!!
                    execution.scenario.shouldHaveGeneratedFiles(directory = outputDirectory)
                }

                then("it leaves the stored composition hash unchanged") {
                    val previousHash =
                        execution.scenario.readWorkspaceFile("scratch/before-hash.txt")?.takeUnless { it == "<null>" }
                    execution.scenario.shouldHaveUnchangedCompositionHash(previousHash)
                }
            }
        }

        given("the current loadout fragments have changed since the last composition") {
            val updatedContent = "## Alpha Fragment\n\nUpdated guidance"

            action("loadout sync is run") {
                val execution by memoizedExecution(
                    seed = {
                        givenCurrentLoadoutFragmentsHaveChangedSinceLastComposition(
                            name = "alpha",
                            updatedContent = updatedContent
                        )
                    }
                ) {
                    writeWorkspaceFile("scratch/before-hash.txt", readConfig()?.compositionHash ?: "<null>")
                    runCommand("sync")
                }

                then("it recomposes the current loadout") {
                    execution.scenario.shouldHaveGeneratedBody(updatedContent)
                }

                then("it writes CLAUDE.md and AGENTS.md to the default output directory") {
                    execution.scenario.shouldHaveGeneratedFiles()
                }

                then("it updates the stored composition hash") {
                    val previousHash =
                        execution.scenario.readWorkspaceFile("scratch/before-hash.txt")?.takeUnless { it == "<null>" }
                    execution.scenario.readConfig()?.compositionHash shouldNotBe previousHash
                }

                then("it clears the synchronization warning on the next command") {
                    execution.scenario.runCommand("list").shouldNotHaveStaleWarning()
                }
            }

            action("loadout sync is run with --std-out") {
                val execution by memoizedExecution(
                    seed = {
                        givenCurrentLoadoutFragmentsHaveChangedSinceLastComposition(
                            name = "alpha",
                            updatedContent = updatedContent
                        )
                    }
                ) {
                    writeWorkspaceFile("scratch/before-hash.txt", readConfig()?.compositionHash ?: "<null>")
                    runCommand("sync", "--std-out")
                }

                then("it prints the recomposed content") {
                    execution.result.shouldContainInStdout(updatedContent)
                }

                then("it does not write files") {
                    execution.scenario.shouldHaveGeneratedBody(firstFragmentContent)
                }

                then("it does not change the stored composition hash") {
                    val previousHash =
                        execution.scenario.readWorkspaceFile("scratch/before-hash.txt")?.takeUnless { it == "<null>" }
                    execution.scenario.shouldHaveUnchangedCompositionHash(previousHash)
                }

                then("it leaves the synchronization warning in place on the next command") {
                    execution.scenario.runCommand("list").shouldHaveStaleWarning()
                }
            }

            action("loadout sync is run with --output and a custom directory") {
                val execution by memoizedExecution(
                    seed = {
                        givenCurrentLoadoutFragmentsHaveChangedSinceLastComposition(
                            name = "alpha",
                            updatedContent = updatedContent
                        )
                    }
                ) {
                    writeWorkspaceFile("scratch/before-hash.txt", readConfig()?.compositionHash ?: "<null>")
                    val outputDirectory = createCustomOutputDirectory()
                    writeWorkspaceFile("scratch/output-dir.txt", outputDirectory)
                    runCommand("sync", "--output", outputDirectory)
                }

                then("it writes CLAUDE.md to the requested directory") {
                    val outputDirectory = execution.scenario.readWorkspaceFile("scratch/output-dir.txt")!!
                    execution.scenario.shouldHaveGeneratedFiles(directory = outputDirectory)
                    execution.scenario.shouldHaveGeneratedBodyInDirectory(outputDirectory, updatedContent)
                }

                then("it writes AGENTS.md to the requested directory") {
                    val outputDirectory = execution.scenario.readWorkspaceFile("scratch/output-dir.txt")!!
                    execution.scenario.shouldHaveGeneratedFiles(directory = outputDirectory)
                }

                then("it updates the stored composition hash for the current composition") {
                    val previousHash =
                        execution.scenario.readWorkspaceFile("scratch/before-hash.txt")?.takeUnless { it == "<null>" }
                    execution.scenario.readConfig()?.compositionHash shouldNotBe previousHash
                    execution.scenario.shouldHaveCurrentLoadoutName("alpha")
                }

                then("it clears the synchronization warning on the next command") {
                    execution.scenario.runCommand("list").shouldNotHaveStaleWarning()
                }
            }
        }
    }
})
