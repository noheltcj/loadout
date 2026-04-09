package cli

import com.github.ajalt.clikt.core.CliktCommand
import domain.entity.error.LoadoutError

/**
 * Constructs the list of output file paths for the given directory.
 *
 * @param outputDir The directory where output files should be written
 * @return List of full paths to output files
 */
fun outputPaths(outputDir: String = Constants.DEFAULT_OUTPUT_DIR): List<String> =
    listOf(
        "$outputDir/${Constants.CLAUDE_MD}",
        "$outputDir/${Constants.AGENTS_MD}"
    )

fun CliktCommand.echoError(error: LoadoutError, verbose: Boolean = false) {
    val cause = error.cause
    echo(error.message, err = true)
    if (verbose && cause != null) {
        echo("Cause: ${cause.message}", err = true)
    }
}
