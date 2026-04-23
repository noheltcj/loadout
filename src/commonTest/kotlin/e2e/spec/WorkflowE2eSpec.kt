@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package e2e.spec

import e2e.support.E2eBehaviorSuite
import e2e.support.ScenarioSeed
import e2e.support.action
import e2e.support.andThen
import e2e.support.firstFragmentContent
import e2e.support.firstFragmentPath
import e2e.support.givenCurrentLoadoutFragmentsHaveChangedSinceLastComposition
import e2e.support.givenCurrentLoadoutIsSet
import e2e.support.givenRepoInitializedInSharedMode
import e2e.support.givenSourceLoadoutExists
import e2e.support.givenTwoValidLoadoutsExist
import e2e.support.secondFragmentContent
import e2e.support.secondFragmentPath
import e2e.support.shouldHaveActiveLoadoutName
import e2e.support.shouldHaveExitCode
import e2e.support.shouldHaveGeneratedBody
import e2e.support.shouldHaveGeneratedFiles
import e2e.support.shouldHaveLoadoutFragments
import e2e.support.shouldHaveRepositoryDefaultLoadoutName
import e2e.support.shouldHaveStaleWarning
import e2e.support.shouldNotHaveStaleWarning
import e2e.support.thirdFragmentContent
import e2e.support.thirdFragmentPath
import io.kotest.matchers.collections.shouldContain

class WorkflowE2eSpec : E2eBehaviorSuite({
    context("workflow spec") {
        given("the workspace has already been initialized in shared mode") {
            val initializedSharedRepo: ScenarioSeed = {
                givenRepoInitializedInSharedMode()
            }
            val initializedSharedRepoWithProjectFragment: ScenarioSeed =
                initializedSharedRepo.andThen {
                    seedFragment(secondFragmentPath, secondFragmentContent)
                }

            then("it starts with the default loadout created and active") {
                val scenario by memoizedScenario(seed = initializedSharedRepo)
                scenario.shouldHaveActiveLoadoutName("default")
                scenario.shouldHaveRepositoryDefaultLoadoutName("default")
            }

            action("loadout create is run for the first project-specific loadout with one or more fragments") {
                val execution by memoizedAction(
                    "create",
                    "project",
                    "--fragment",
                    secondFragmentPath,
                    seed = initializedSharedRepoWithProjectFragment
                )

                then("it creates the requested loadout definition") {
                    execution.scenario.shouldHaveLoadoutFragments("project", listOf(secondFragmentPath))
                }
            }

            given("that new loadout has been created") {
                val projectLoadoutExists: ScenarioSeed =
                    initializedSharedRepoWithProjectFragment.andThen {
                        runCommand("create", "project", "--fragment", secondFragmentPath)
                    }

                action("loadout use is run for that new loadout") {
                    val execution by memoizedAction("use", "project", seed = projectLoadoutExists)

                    then("it switches the workspace to the new loadout cleanly") {
                        execution.scenario.shouldHaveActiveLoadoutName("project")
                    }

                    then("it writes CLAUDE.md, AGENTS.md, and GEMINI.md for that new loadout") {
                        execution.scenario.shouldHaveGeneratedFiles()
                        execution.scenario.shouldHaveGeneratedBody(secondFragmentContent)
                    }
                }
            }
        }

        given("two valid loadouts exist and one loadout is currently active") {
            val activeAlphaWithOtherLoadout: ScenarioSeed = {
                givenTwoValidLoadoutsExist()
                givenCurrentLoadoutIsSet(
                    name = "alpha",
                    description = "Alpha loadout",
                    fragments = listOf(firstFragmentPath to firstFragmentContent)
                )
            }

            action("loadout use is run for the other loadout") {
                val execution by memoizedAction("use", "beta", seed = activeAlphaWithOtherLoadout)

                then("it rewrites CLAUDE.md, AGENTS.md, and GEMINI.md for the new loadout") {
                    execution.scenario.shouldHaveGeneratedFiles()
                    execution.scenario.shouldHaveGeneratedBody(secondFragmentContent)
                }

                then("it records the new current loadout") {
                    execution.scenario.shouldHaveActiveLoadoutName("beta")
                }
            }
        }

        given("a new fragment has been added to the current loadout") {
            val addedFragmentToCurrentLoadout: ScenarioSeed = {
                givenCurrentLoadoutIsSet(name = "alpha")
                seedFragment(secondFragmentPath, secondFragmentContent)
                runCommand("link", secondFragmentPath, "--to", "alpha")
            }

            then("it updates the loadout definition") {
                val scenario by memoizedScenario(seed = addedFragmentToCurrentLoadout)
                scenario.shouldHaveLoadoutFragments("alpha", listOf(firstFragmentPath, secondFragmentPath))
            }

            action("loadout sync is run after that new fragment has been added") {
                val execution by memoizedAction("sync", seed = addedFragmentToCurrentLoadout)

                then("it rewrites CLAUDE.md, AGENTS.md, and GEMINI.md with the added fragment content") {
                    execution.scenario.shouldHaveGeneratedFiles()
                    execution.scenario.shouldHaveGeneratedBody("$firstFragmentContent\n\n$secondFragmentContent")
                }

                then("the next command exits with result 0") {
                    execution.scenario.runCommand("list").shouldHaveExitCode(0)
                }

                then("it clears the synchronization warning on the next command") {
                    execution.scenario.runCommand("list").shouldNotHaveStaleWarning()
                }
            }
        }

        given("the current loadout fragments have changed since the last composition") {
            val staleCurrentLoadout: ScenarioSeed = {
                givenCurrentLoadoutFragmentsHaveChangedSinceLastComposition(name = "alpha")
            }

            action("a read-only command such as loadout list is run") {
                val execution by memoizedAction("list", seed = staleCurrentLoadout)

                then("it warns that the current loadout is not synchronized") {
                    execution.result.shouldHaveStaleWarning()
                }
            }

            action("loadout sync is run") {
                val execution by memoizedAction("sync", seed = staleCurrentLoadout)

                then("the next command exits with result 0") {
                    execution.scenario.runCommand("list").shouldHaveExitCode(0)
                }

                then("it clears the warning on the next command") {
                    execution.scenario.runCommand("list").shouldNotHaveStaleWarning()
                }
            }
        }

        given("the source loadout exists") {
            val sourceLoadoutExists: ScenarioSeed = {
                givenSourceLoadoutExists()
            }
            val sourceLoadoutExistsWithSecondFragment: ScenarioSeed =
                sourceLoadoutExists.andThen {
                    seedFragment(secondFragmentPath, secondFragmentContent)
                }

            action("loadout create is run with --clone and extra fragments") {
                val execution by memoizedAction(
                    "create",
                    "clone",
                    "--clone",
                    "source",
                    "--fragment",
                    secondFragmentPath,
                    seed = sourceLoadoutExistsWithSecondFragment
                )

                then("it preserves the source fragments") {
                    execution.scenario
                        .readLoadout("clone")
                        ?.fragments
                        .orEmpty()
                        .shouldContain(firstFragmentPath)
                }

                then("it appends the extra fragments") {
                    execution.scenario.shouldHaveLoadoutFragments(
                        "clone",
                        listOf(firstFragmentPath, secondFragmentPath)
                    )
                }
            }

            given("the cloned loadout has been created with those extra fragments") {
                val customizedCloneExists: ScenarioSeed =
                    sourceLoadoutExistsWithSecondFragment.andThen {
                        seedFragment(thirdFragmentPath, thirdFragmentContent)
                        runCommand(
                            "create",
                            "clone",
                            "--clone",
                            "source",
                            "--fragment",
                            secondFragmentPath,
                            "--fragment",
                            thirdFragmentPath
                        )
                    }

                action("loadout use is run for the cloned loadout") {
                    val execution by memoizedAction("use", "clone", seed = customizedCloneExists)

                    then("it generates CLAUDE.md, AGENTS.md, and GEMINI.md from the customized clone") {
                        execution.scenario.shouldHaveGeneratedFiles()
                        execution.scenario.shouldHaveGeneratedBody(
                            "$firstFragmentContent\n\n$secondFragmentContent\n\n$thirdFragmentContent"
                        )
                    }
                }
            }
        }
    }
})
