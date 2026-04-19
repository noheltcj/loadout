@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package e2e.spec

import e2e.support.E2eBehaviorSuite
import e2e.support.ScenarioSeed
import e2e.support.action
import e2e.support.andThen
import e2e.support.firstFragmentContent
import e2e.support.firstFragmentPath
import e2e.support.givenGitRepositoryExists
import e2e.support.shouldContainInOutput
import e2e.support.shouldContainInStdout
import e2e.support.shouldHaveExitCode
import e2e.support.shouldHaveRepoDefaultLoadoutName

class ConfigE2eSpec : E2eBehaviorSuite({
    context("loadout config spec") {
        given("a shared git repository has been initialized") {
            val initializedSharedGitRepository: ScenarioSeed = {
                givenGitRepositoryExists()
                runCommand("init")
            }

            action("loadout config is run without flags") {
                val execution by memoizedAction("config", seed = initializedSharedGitRepository)

                then("it reports the current repo default loadout") {
                    execution.result.shouldContainInStdout("Default loadout: default")
                }

                then("it exits with result 0") {
                    execution.result.shouldHaveExitCode(0)
                }
            }

            given("another valid loadout exists") {
                val anotherValidLoadoutExists: ScenarioSeed =
                    initializedSharedGitRepository.andThen {
                        seedFragment(firstFragmentPath, firstFragmentContent)
                        seedLoadout(name = "alpha", fragments = listOf(firstFragmentPath))
                    }

                action("loadout config is run with --default-loadout and that loadout name") {
                    val execution by memoizedAction(
                        "config",
                        "--default-loadout",
                        "alpha",
                        seed = anotherValidLoadoutExists
                    )

                    then("it updates the repo default loadout selection") {
                        execution.scenario.shouldHaveRepoDefaultLoadoutName("alpha")
                    }

                    then("it reports the new repo default loadout") {
                        execution.result.shouldContainInStdout("Default loadout: alpha")
                    }
                }
            }

            action("loadout config is run with --default-loadout for a missing loadout") {
                val execution by memoizedAction(
                    "config",
                    "--default-loadout",
                    "missing",
                    seed = initializedSharedGitRepository
                )

                then("it outputs the validation error") {
                    execution.result.shouldContainInOutput("Loadout 'missing' not found")
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }

                then("it leaves the repo default loadout unchanged") {
                    execution.scenario.shouldHaveRepoDefaultLoadoutName("default")
                }
            }
        }
    }
})
