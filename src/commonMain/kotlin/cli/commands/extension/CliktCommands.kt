package cli.commands.extension

import com.github.ajalt.clikt.core.CliktCommand
import domain.entity.WriteComposedFilesResult

fun CliktCommand.echoFilesGenerated(composedContentLength: Int) {
    echo("Generated files (${composedContentLength} characters):")
    echo("  • CLAUDE.md")
    echo("  • AGENTS.md")
}

fun CliktCommand.echoComposedFilesWriteResult(
    result: WriteComposedFilesResult,
    loadoutName: String,
    composedContentLength: Int,
) {
    when (result) {
        WriteComposedFilesResult.Overwritten -> {
            echoFilesGenerated(composedContentLength)
        }
        WriteComposedFilesResult.AlreadyUpToDate -> {
            echo("Loadout `$loadoutName` is active and up to date. Nothing to do.")
        }
    }
}