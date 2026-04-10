@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package e2e.spec

import e2e.support.E2eBehaviorSuite
import e2e.support.action
import e2e.support.shouldContainInOutput
import e2e.support.shouldHaveExitCode
import io.kotest.matchers.string.shouldContain

class ParserE2eSpec : E2eBehaviorSuite({
    context("parser spec") {
        given("the parser receives an invalid subcommand") {
            action("loadout is run with that invalid subcommand") {
                val execution by memoizedAction("wat")

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }

                then("it outputs the parser error") {
                    execution.result.shouldContainInOutput("no such subcommand")
                }

                then("it suggests help or usage") {
                    execution.result.output.shouldContain("Usage:")
                }
            }
        }

        given("the parser receives the removed legacy add subcommand") {
            action("loadout add is run") {
                val execution by memoizedAction("add", "fragments/alpha.md", "--to", "alpha")

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }

                then("it outputs usage guidance for link") {
                    execution.result.output.shouldContain("Usage: loadout link")
                }

                then("it reports that the legacy add invocation is unsupported and points callers toward link") {
                    execution.result.shouldContainInOutput("The 'add' subcommand has been replaced by 'link'")
                }
            }
        }

        given("the parser receives a command without a required argument") {
            action("loadout is run without that required argument") {
                val execution by memoizedAction("use")

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }

                then("it outputs usage guidance") {
                    execution.result.output.shouldContain("Usage:")
                }
            }
        }

        given("the parser receives an invalid mode value") {
            action("loadout init is run with that invalid mode value") {
                val execution by memoizedAction("init", "--mode", "invalid")

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }

                then("it outputs the allowed mode values") {
                    execution.result.output.shouldContain("SHARED")
                    execution.result.output.shouldContain("LOCAL")
                }
            }
        }
    }
})
