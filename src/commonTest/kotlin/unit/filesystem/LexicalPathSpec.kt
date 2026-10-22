@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package unit.filesystem

import data.filesystem.normalizePathLexically
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class LexicalPathSpec : BehaviorSpec({
    Given("an absolute path with parent segments") {
        val path = "/repo/.git/worktrees/feature/../.."

        When("the path is normalized lexically") {
            val normalized = normalizePathLexically(path)

            Then("it resolves the parent segments") {
                normalized shouldBe "/repo/.git"
            }
        }

        Given("the path is moved under a Windows drive root with Windows separators") {
            val windowsPath = "C:" + path.replace('/', '\\')

            When("the path is normalized lexically") {
                val normalized = normalizePathLexically(windowsPath)

                Then("it keeps the drive root") {
                    normalized shouldBe "C:/repo/.git"
                }
            }
        }
    }

    Given("a relative path whose parent segments climb above its start") {
        val path = "./../.git/modules/api"

        When("the path is normalized lexically") {
            val normalized = normalizePathLexically(path)

            Then("it keeps the parent segments it cannot resolve") {
                normalized shouldBe "../.git/modules/api"
            }
        }
    }

    Given("an absolute path that climbs above the filesystem root") {
        val path = "/../repo"

        When("the path is normalized lexically") {
            val normalized = normalizePathLexically(path)

            Then("it never climbs above the root") {
                normalized shouldBe "/repo"
            }
        }
    }
})
