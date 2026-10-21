@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package unit.environment

import data.environment.UserConfigDirectoryConvention
import data.environment.lookUpUserConfigDirectory
import domain.entity.UserConfigDirectory
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

private fun lookUp(
    convention: UserConfigDirectoryConvention,
    environment: Map<String, String>,
): UserConfigDirectory = lookUpUserConfigDirectory(convention) { variable -> environment[variable] }

class UserConfigDirectoryLookupSpec : BehaviorSpec({
    Given("a POSIX environment with only HOME set") {
        val environment = mapOf("HOME" to "/home/developer")

        When("the user config directory is looked up") {
            val configDirectory = lookUp(UserConfigDirectoryConvention.Xdg, environment)

            Then("it uses .config under the home directory") {
                configDirectory shouldBe
                    UserConfigDirectory.Determined(path = "/home/developer/.config", pathInHome = ".config")
            }
        }

        Given("XDG_CONFIG_HOME names an absolute directory under the home directory") {
            val xdgEnvironment = environment + ("XDG_CONFIG_HOME" to "/home/developer/settings/")

            When("the user config directory is looked up") {
                val configDirectory = lookUp(UserConfigDirectoryConvention.Xdg, xdgEnvironment)

                Then("it uses XDG_CONFIG_HOME, relative to the home directory and without a trailing slash") {
                    configDirectory shouldBe
                        UserConfigDirectory.Determined(path = "/home/developer/settings", pathInHome = "settings")
                }
            }
        }

        Given("XDG_CONFIG_HOME names an absolute directory outside the home directory") {
            val xdgEnvironment = environment + ("XDG_CONFIG_HOME" to "/etc/developer-config")

            When("the user config directory is looked up") {
                val configDirectory = lookUp(UserConfigDirectoryConvention.Xdg, xdgEnvironment)

                Then("it uses XDG_CONFIG_HOME without a path in the home directory") {
                    configDirectory shouldBe
                        UserConfigDirectory.Determined(path = "/etc/developer-config", pathInHome = null)
                }
            }
        }

        Given("XDG_CONFIG_HOME is relative") {
            val xdgEnvironment = environment + ("XDG_CONFIG_HOME" to "settings")

            When("the user config directory is looked up") {
                val configDirectory = lookUp(UserConfigDirectoryConvention.Xdg, xdgEnvironment)

                Then("it ignores XDG_CONFIG_HOME, as the XDG specification requires") {
                    configDirectory shouldBe
                        UserConfigDirectory.Determined(path = "/home/developer/.config", pathInHome = ".config")
                }
            }
        }

        Given("XDG_CONFIG_HOME is empty") {
            val xdgEnvironment = environment + ("XDG_CONFIG_HOME" to "")

            When("the user config directory is looked up") {
                val configDirectory = lookUp(UserConfigDirectoryConvention.Xdg, xdgEnvironment)

                Then("it ignores XDG_CONFIG_HOME") {
                    configDirectory shouldBe
                        UserConfigDirectory.Determined(path = "/home/developer/.config", pathInHome = ".config")
                }
            }
        }
    }

    mapOf(
        "a POSIX environment with neither HOME nor XDG_CONFIG_HOME" to emptyMap(),
        "a POSIX environment whose HOME is relative" to mapOf("HOME" to "developer"),
    ).forEach { (description, environment) ->
        Given(description) {
            When("the user config directory is looked up") {
                val configDirectory = lookUp(UserConfigDirectoryConvention.Xdg, environment)

                Then("it names the variables that could locate it") {
                    configDirectory shouldBe UserConfigDirectory.Undeterminable(listOf("XDG_CONFIG_HOME", "HOME"))
                }
            }
        }
    }

    Given("a Windows environment with APPDATA set") {
        val environment = mapOf("APPDATA" to "C:\\Users\\developer\\AppData\\Roaming", "HOME" to "/home/developer")

        When("the user config directory is looked up") {
            val configDirectory = lookUp(UserConfigDirectoryConvention.Windows, environment)

            Then("it uses APPDATA") {
                configDirectory shouldBe
                    UserConfigDirectory.Determined(path = "C:\\Users\\developer\\AppData\\Roaming", pathInHome = null)
            }
        }
    }

    Given("a Windows environment without APPDATA") {
        val environment = mapOf("HOME" to "/home/developer")

        When("the user config directory is looked up") {
            val configDirectory = lookUp(UserConfigDirectoryConvention.Windows, environment)

            Then("it names APPDATA") {
                configDirectory shouldBe UserConfigDirectory.Undeterminable(listOf("APPDATA"))
            }
        }
    }
})
