package cli

import com.github.ajalt.clikt.core.CliktCommand
import domain.entity.error.LoadoutError

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
