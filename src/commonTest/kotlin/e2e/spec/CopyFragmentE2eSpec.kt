@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package e2e.spec

import e2e.support.E2eBehaviorSuite
import e2e.support.ScenarioSeed
import e2e.support.action
import e2e.support.andThen
import e2e.support.givenDirectoryExistsOutsideWorkspace
import e2e.support.givenFragmentSourceHostIsTrusted
import e2e.support.givenFragmentsHomeIsConfigured
import e2e.support.givenLocalMarkdownSourceExists
import e2e.support.givenMaximumFragmentCopyBytesIsConfigured
import e2e.support.givenSymbolicLinkExists
import e2e.support.localSourceContent
import e2e.support.localSourcePath
import e2e.support.requireLoadout
import e2e.support.shouldContainInOutput
import e2e.support.shouldContainInStdout
import e2e.support.shouldHaveExitCode
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class CopyFragmentE2eSpec : E2eBehaviorSuite({
    context("loadout fragment copy spec") {
        given("a local Markdown source exists in the workspace") {
            val localMarkdownSourceExists: ScenarioSeed = {
                givenLocalMarkdownSourceExists()
            }

            action("loadout fragment copy is run without a destination") {
                val execution by memoizedAction(
                    "fragment",
                    "copy",
                    localSourcePath,
                    seed = localMarkdownSourceExists,
                )

                then("it copies the source under the default fragments home") {
                    execution.scenario.readWorkspaceFile("fragments/source.md") shouldBe localSourceContent
                }

                then("it reports the copied repository-relative path") {
                    execution.result.shouldContainInStdout("Copied fragment to fragments/source.md")
                }

                then("it tells the user to inspect the copy before linking it") {
                    execution.result.shouldContainInStdout(
                        "Review for unexpected or dangerous instructions before linking."
                    )
                }
            }

            action("loadout fragment copy is run with a nested destination") {
                val execution by memoizedAction(
                    "fragment",
                    "copy",
                    localSourcePath,
                    "universal/renamed.md",
                    seed = localMarkdownSourceExists,
                )

                then("it creates the requested subdirectory and filename") {
                    execution.scenario.readWorkspaceFile("fragments/universal/renamed.md") shouldBe localSourceContent
                }
            }

            action("loadout fragment copy is run with a destination that escapes the fragments home") {
                val execution by memoizedAction(
                    "fragment",
                    "copy",
                    localSourcePath,
                    "../escaped.md",
                    seed = localMarkdownSourceExists,
                )

                then("it outputs the destination validation error") {
                    execution.result.shouldContainInOutput("safe relative Markdown path")
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }

                then("it writes nothing outside the fragments home") {
                    execution.scenario.readWorkspaceFile("escaped.md").shouldBeNull()
                }
            }

            given("the destination already exists") {
                val destinationAlreadyExists: ScenarioSeed =
                    localMarkdownSourceExists.andThen {
                        writeWorkspaceFile("fragments/source.md", "## Existing\n")
                    }

                action("loadout fragment copy is run without --force") {
                    val execution by memoizedAction(
                        "fragment",
                        "copy",
                        localSourcePath,
                        seed = destinationAlreadyExists,
                    )

                    then("it outputs that the destination already exists") {
                        execution.result.shouldContainInOutput("destination already exists")
                    }

                    then("it exits with result 1") {
                        execution.result.shouldHaveExitCode(1)
                    }

                    then("it preserves the existing destination content") {
                        execution.scenario.readWorkspaceFile("fragments/source.md") shouldBe "## Existing\n"
                    }
                }

                action("loadout fragment copy is run with --force") {
                    val execution by memoizedAction(
                        "fragment",
                        "copy",
                        localSourcePath,
                        "--force",
                        seed = destinationAlreadyExists,
                    )

                    then("it replaces the destination content") {
                        execution.scenario.readWorkspaceFile("fragments/source.md") shouldBe localSourceContent
                    }

                    then("it exits with result 0") {
                        execution.result.shouldHaveExitCode(0)
                    }
                }
            }

            given("the destination is a symbolic link pointing outside the fragments home") {
                val destinationIsEscapingSymbolicLink: ScenarioSeed =
                    localMarkdownSourceExists.andThen {
                        writeWorkspaceFile("outside/planted.md", "## Planted\n")
                        givenSymbolicLinkExists(
                            linkPath = "fragments/source.md",
                            targetPath = workspacePath("outside/planted.md"),
                        )
                    }

                action("loadout fragment copy is run with --force") {
                    val execution by memoizedAction(
                        "fragment",
                        "copy",
                        localSourcePath,
                        "--force",
                        seed = destinationIsEscapingSymbolicLink,
                    )

                    then("it outputs the containment validation error") {
                        execution.result.shouldContainInOutput("must remain inside the configured fragments home")
                    }

                    then("it exits with result 1") {
                        execution.result.shouldHaveExitCode(1)
                    }

                    then("it does not write through the symbolic link") {
                        execution.scenario.readWorkspaceFile("outside/planted.md") shouldBe "## Planted\n"
                    }
                }
            }

            given("the fragments home is a symbolic link resolving outside the repository") {
                val fragmentsHomeEscapesRepository: ScenarioSeed =
                    localMarkdownSourceExists.andThen {
                        givenFragmentsHomeIsConfigured("linked-home")
                        val outsideDirectory = givenDirectoryExistsOutsideWorkspace("outside-home")
                        givenSymbolicLinkExists(linkPath = "linked-home", targetPath = outsideDirectory)
                    }

                action("loadout fragment copy is run") {
                    val execution by memoizedAction(
                        "fragment",
                        "copy",
                        localSourcePath,
                        "a/b/x.md",
                        seed = fragmentsHomeEscapesRepository,
                    )

                    then("it outputs the containment validation error") {
                        execution.result.shouldContainInOutput("must remain inside the configured fragments home")
                    }

                    then("it exits with result 1") {
                        execution.result.shouldHaveExitCode(1)
                    }

                    then("it creates no directories through the escaping fragments-home link") {
                        execution.scenario.pathExists(execution.scenario.homePath("outside-home/a")) shouldBe false
                    }
                }
            }

            given("the repository is initialized") {
                val initializedRepository: ScenarioSeed =
                    localMarkdownSourceExists.andThen {
                        runCommand("init")
                    }

                action("loadout fragment copy is run") {
                    val execution by memoizedAction(
                        "fragment",
                        "copy",
                        localSourcePath,
                        seed = initializedRepository,
                    )

                    then("it leaves the default loadout without fragments") {
                        execution.scenario.requireLoadout("default").fragments.shouldBeEmpty()
                    }
                }
            }

            given("the repository uses a custom fragments home") {
                val customFragmentsHome: ScenarioSeed =
                    localMarkdownSourceExists.andThen {
                        givenFragmentsHomeIsConfigured("guidance/library")
                    }

                action("loadout fragment copy is run with a nested destination") {
                    val execution by memoizedAction(
                        "fragment",
                        "copy",
                        localSourcePath,
                        "testing/copied.md",
                        seed = customFragmentsHome,
                    )

                    then("it writes below the configured fragments home") {
                        execution.scenario.readWorkspaceFile("guidance/library/testing/copied.md") shouldBe
                            localSourceContent
                    }

                    then("it does not write below the default fragments home") {
                        execution.scenario.readWorkspaceFile("fragments/testing/copied.md").shouldBeNull()
                    }
                }
            }
        }

        given("a local source is larger than the configured copy limit") {
            val oversizedSourceExists: ScenarioSeed = {
                givenMaximumFragmentCopyBytesIsConfigured(1_000)
                writeWorkspaceFile("large.md", "a".repeat(1_001))
            }

            action("loadout fragment copy is run") {
                val execution by memoizedAction(
                    "fragment",
                    "copy",
                    "large.md",
                    seed = oversizedSourceExists,
                )

                then("it reports the configured limit") {
                    execution.result.shouldContainInOutput("1000 bytes")
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }

                then("it creates no destination file") {
                    execution.scenario.readWorkspaceFile("fragments/large.md").shouldBeNull()
                }
            }
        }

        given("a local Markdown source is a symbolic link to a device") {
            val deviceLinkSourceExists: ScenarioSeed = {
                givenSymbolicLinkExists(linkPath = "device.md", targetPath = "/dev/zero")
            }

            action("loadout fragment copy is run") {
                val execution by memoizedAction(
                    "fragment",
                    "copy",
                    "device.md",
                    seed = deviceLinkSourceExists,
                )

                then("it rejects the source as not a regular file") {
                    execution.result.shouldContainInOutput("Source must be a regular file")
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }
            }
        }

        given("a local source that is not a Markdown file exists") {
            val nonMarkdownSourceExists: ScenarioSeed = {
                writeWorkspaceFile("notes.txt", "## Not Markdown\n")
            }

            action("loadout fragment copy is run with an explicit Markdown destination") {
                val execution by memoizedAction(
                    "fragment",
                    "copy",
                    "notes.txt",
                    "notes.md",
                    seed = nonMarkdownSourceExists,
                )

                then("it rejects the source extension") {
                    execution.result.shouldContainInOutput("Local source must be a Markdown file")
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }
            }
        }

        given("the allowlist trusts a host other than the requested one") {
            val unrelatedHostTrusted: ScenarioSeed = {
                givenFragmentSourceHostIsTrusted("example.com")
            }

            action("loadout fragment copy is run with an https source on an untrusted host") {
                val execution by memoizedAction(
                    "fragment",
                    "copy",
                    "https://raw.githubusercontent.com/noheltcj/loadout/refs/heads/main/fragments/universal/" +
                        "uses-unambiguous-modeling.md",
                    seed = unrelatedHostTrusted,
                )

                then("it reports that the requested host is not trusted") {
                    execution.result.shouldContainInOutput(
                        "Fragment source host 'raw.githubusercontent.com' is not trusted"
                    )
                }

                then("it names the command that would trust the host") {
                    execution.result.shouldContainInOutput(
                        "loadout config --trust-fragment-host raw.githubusercontent.com"
                    )
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }
            }
        }

        given("a local Markdown source is empty") {
            val emptySourceExists: ScenarioSeed = {
                writeWorkspaceFile("empty.md", "   \n")
            }

            action("loadout fragment copy is run") {
                val execution by memoizedAction(
                    "fragment",
                    "copy",
                    "empty.md",
                    seed = emptySourceExists,
                )

                then("it rejects the empty document") {
                    execution.result.shouldContainInOutput("Document must not be empty")
                }

                then("it creates no destination file") {
                    execution.scenario.readWorkspaceFile("fragments/empty.md").shouldBeNull()
                }
            }
        }
    }
})
