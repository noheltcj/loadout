package e2e.support

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.spec.style.scopes.BehaviorSpecGivenContainerScope
import io.kotest.core.spec.style.scopes.BehaviorSpecWhenContainerScope

data class ActionExecution(
    val scenario: E2eScenario,
    val result: CommandResult,
)

data class CapturedActionExecution<T>(
    val scenario: E2eScenario,
    val result: CommandResult,
    val captured: T,
)

open class E2eBehaviorSuite(body: E2eBehaviorSuite.() -> Unit = {}) : BehaviorSpec() {
    private val closeables = mutableListOf<AutoCloseable>()

    init {
        isolationMode = IsolationMode.InstancePerRoot

        afterSpec {
            closeables
                .asReversed()
                .forEach(AutoCloseable::close)
        }

        body()
    }

    fun memoizedScenario(seed: ScenarioSeed = {}): Lazy<E2eScenario> =
        lazy {
            track(E2eScenario.create().apply(seed))
        }

    fun memoizedAction(vararg args: String, seed: ScenarioSeed = {}): Lazy<ActionExecution> =
        lazy {
            createExecution(seed) {
                runCommand(*args)
            }
        }

    fun memoizedExecution(seed: ScenarioSeed = {}, execute: E2eScenario.() -> CommandResult): Lazy<ActionExecution> =
        lazy {
            createExecution(seed, execute)
        }

    fun <T> memoizedCapturedExecution(
        seed: ScenarioSeed = {},
        capture: E2eScenario.() -> T,
        execute: E2eScenario.(T) -> CommandResult,
    ): Lazy<CapturedActionExecution<T>> =
        lazy {
            createCapturedExecution(seed, capture, execute)
        }

    private fun <T : AutoCloseable> track(resource: T): T {
        closeables += resource
        return resource
    }

    private fun createExecution(seed: ScenarioSeed, execute: E2eScenario.() -> CommandResult): ActionExecution {
        val scenario = track(E2eScenario.create().apply(seed))
        return ActionExecution(
            scenario = scenario,
            result = scenario.execute()
        )
    }

    private fun <T> createCapturedExecution(
        seed: ScenarioSeed,
        capture: E2eScenario.() -> T,
        execute: E2eScenario.(T) -> CommandResult,
    ): CapturedActionExecution<T> {
        val scenario = track(E2eScenario.create().apply(seed))
        val captured = scenario.capture()
        return CapturedActionExecution(
            scenario = scenario,
            result = scenario.execute(captured),
            captured = captured,
        )
    }
}

suspend fun BehaviorSpecGivenContainerScope.action(
    name: String,
    test: suspend BehaviorSpecWhenContainerScope.() -> Unit,
) {
    When(name, test)
}
