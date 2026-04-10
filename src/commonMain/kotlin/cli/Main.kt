package cli

import cli.di.withApplicationScope
import com.github.ajalt.clikt.core.main

fun main(args: Array<String>) {
    withApplicationScope {
        createLoadoutCommand(this).main(args)
    }
}
