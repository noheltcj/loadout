@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package e2e.spec

import e2e.support.E2eBehaviorSuite
import e2e.support.ScenarioSeed
import e2e.support.action
import e2e.support.andThen
import e2e.support.claudeFilePath
import e2e.support.firstFragmentContent
import e2e.support.firstFragmentPath
import e2e.support.givenConfigPointsAtDeletedLoadout
import e2e.support.givenCurrentLoadoutFragmentWasDeleted
import e2e.support.givenCurrentLoadoutFragmentsHaveChangedSinceLastComposition
import e2e.support.givenCurrentLoadoutIsAlreadySynchronized
import e2e.support.givenCurrentLoadoutIsSet
import e2e.support.givenRepositorySettingsTrustFragmentHosts
import e2e.support.requireGeneratedBody
import e2e.support.requireLastComposedContentHash
import e2e.support.shouldContainInOutput
import e2e.support.shouldContainInStderr
import e2e.support.shouldContainInStdout
import e2e.support.shouldHaveExitCode
import e2e.support.shouldHaveGeneratedBody
import e2e.support.shouldHaveGeneratedFiles
import e2e.support.shouldHaveStaleWarning
import e2e.support.shouldHaveUnchangedLastComposedContentHash
import e2e.support.shouldNotHaveStaleWarning
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain

private const val INVALID_TRUSTED_HOST = "169.254.169.254"
private const val INVALID_TRUSTED_HOST_WARNING =
    "Warning: Invalid trusted fragment host '$INVALID_TRUSTED_HOST' was removed."

private data class SynchronizedLoadoutCapture(
    val generatedBody: String,
    val lastComposedContentHash: String,
)

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
                givenCurrentLoadoutIsSet(name = "alpha")
                givenCurrentLoadoutFragmentWasDeleted()
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
                val execution by memoizedCapturedExecution(
                    seed = currentLoadoutIsAlreadySynchronized,
                    capture = {
                        requireGeneratedBody()
                    },
                ) { _ ->
                    runCommand("sync")
                }

                then("it outputs that the current loadout is active and up to date") {
                    execution.result.shouldContainInStdout("Loadout `alpha` is active and up to date. Nothing to do.")
                }

                then("it does not rewrite the output files") {
                    execution.scenario.requireGeneratedBody() shouldBe execution.captured
                }
            }

            action("one of the output files is manually deleted") {
                val execution by memoizedExecution(seed = currentLoadoutIsAlreadySynchronized) {
                    deleteWorkspaceFile(claudeFilePath)
                    runCommand("sync")
                }

                then("it regenerates the missing file and restores the full generated set") {
                    execution.scenario.shouldHaveGeneratedFiles()
                }

                then("it reports that it rewrote the files") {
                    execution.result.shouldContainInStdout("Generated files")
                }

                then("it does not report the up-to-date message") {
                    execution.result.stdout.shouldNotContain("is active and up to date. Nothing to do.")
                }
            }

            action("loadout sync is run with --std-out") {
                val execution by memoizedCapturedExecution(
                    seed = currentLoadoutIsAlreadySynchronized,
                    capture = {
                        SynchronizedLoadoutCapture(
                            generatedBody = requireGeneratedBody(),
                            lastComposedContentHash = requireLastComposedContentHash(),
                        )
                    },
                ) { _ ->
                    runCommand("sync", "--std-out")
                }

                then("it prints the current composed content") {
                    execution.result.shouldContainInStdout(firstFragmentContent)
                }

                then("it does not write files") {
                    execution.scenario.requireGeneratedBody() shouldBe execution.captured.generatedBody
                }

                then("it does not change the stored composition hash") {
                    execution.scenario.shouldHaveUnchangedLastComposedContentHash(
                        execution.captured.lastComposedContentHash
                    )
                }
            }

            given("the repository settings were edited by hand to trust an invalid host") {
                val invalidTrustedHost: ScenarioSeed =
                    currentLoadoutIsAlreadySynchronized.andThen {
                        givenRepositorySettingsTrustFragmentHosts(INVALID_TRUSTED_HOST)
                    }

                action("loadout sync is run") {
                    val execution by memoizedAction("sync", seed = invalidTrustedHost)

                    then("it warns that the invalid trusted host was removed") {
                        execution.result.shouldContainInStderr(INVALID_TRUSTED_HOST_WARNING)
                    }

                    then("it exits with result 0") {
                        execution.result.shouldHaveExitCode(0)
                    }
                }

                action("loadout sync is run with --std-out") {
                    val execution by memoizedAction("sync", "--std-out", seed = invalidTrustedHost)

                    then("it prints no repository settings warning") {
                        execution.result.stderr shouldNotContain INVALID_TRUSTED_HOST_WARNING
                    }
                }
            }
        }

        given("the current loadout fragments have changed since the last composition") {
            val updatedContent = "## Alpha Fragment\n\nUpdated guidance"

            action("loadout sync is run") {
                val execution by memoizedCapturedExecution(
                    seed = {
                        givenCurrentLoadoutFragmentsHaveChangedSinceLastComposition(
                            name = "alpha",
                            updatedContent = updatedContent
                        )
                    },
                    capture = {
                        requireLastComposedContentHash()
                    },
                ) { _ ->
                    runCommand("sync")
                }

                then("it recomposes the current loadout") {
                    execution.scenario.shouldHaveGeneratedBody(updatedContent)
                }

                then("it writes CLAUDE.md, AGENTS.md, and GEMINI.md") {
                    execution.scenario.shouldHaveGeneratedFiles()
                }

                then("it updates the stored composition hash") {
                    execution.scenario.requireLastComposedContentHash() shouldNotBe execution.captured
                }

                then("the next command exits with result 0") {
                    execution.scenario.runCommand("list").shouldHaveExitCode(0)
                }

                then("it clears the synchronization warning on the next command") {
                    execution.scenario.runCommand("list").shouldNotHaveStaleWarning()
                }
            }

            action("loadout sync is run with --std-out") {
                val execution by memoizedCapturedExecution(
                    seed = {
                        givenCurrentLoadoutFragmentsHaveChangedSinceLastComposition(
                            name = "alpha",
                            updatedContent = updatedContent
                        )
                    },
                    capture = {
                        requireLastComposedContentHash()
                    },
                ) { _ ->
                    runCommand("sync", "--std-out")
                }

                then("it prints the recomposed content") {
                    execution.result.shouldContainInStdout(updatedContent)
                }

                then("it does not write files") {
                    execution.scenario.shouldHaveGeneratedBody(firstFragmentContent)
                }

                then("it does not change the stored composition hash") {
                    execution.scenario.shouldHaveUnchangedLastComposedContentHash(execution.captured)
                }

                then("the next command exits with result 0") {
                    execution.scenario.runCommand("list").shouldHaveExitCode(0)
                }

                then("it leaves the synchronization warning in place on the next command") {
                    execution.scenario.runCommand("list").shouldHaveStaleWarning()
                }
            }
        }
    }
})
