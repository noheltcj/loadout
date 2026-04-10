package e2e

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.names.DuplicateTestNameMode
import io.kotest.core.test.AssertionMode

class ProjectConfig : AbstractProjectConfig() {
    override val assertionMode = AssertionMode.Error
    override val duplicateTestNameMode = DuplicateTestNameMode.Error
}
