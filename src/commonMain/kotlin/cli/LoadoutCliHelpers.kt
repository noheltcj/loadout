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
    Constants.generatedMarkdownFiles.map { fileName -> "$outputDir/$fileName" }

fun CliktCommand.echoError(
    error: LoadoutError,
    verbose: Boolean = false,
) {
    val cause = error.cause
    echo(error.message, err = true)
    error.remediation()?.let { remediation -> echo(remediation, err = true) }
    if (verbose && cause != null) {
        echo("Cause: ${cause.message}", err = true)
    }
}

private fun LoadoutError.remediation(): String? =
    when (this) {
        is LoadoutError.UntrustedFragmentSourceHost -> "Trust it with 'loadout config --trust-fragment-host $host'."
        is LoadoutError.RepairableRepositorySettingsError -> "Repair repository settings with 'loadout config'."
        else -> null
    }
