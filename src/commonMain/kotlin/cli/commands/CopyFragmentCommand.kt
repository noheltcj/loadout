package cli.commands

import cli.echoError
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import domain.policy.resolveFragmentSource
import domain.usecase.CopyFragmentInput
import domain.usecase.CopyFragmentUseCase
import kotlinx.coroutines.runBlocking

class CopyFragmentCommand(
    private val copyFragment: CopyFragmentUseCase,
) : CliktCommand(name = "copy") {
    override fun help(context: Context): String =
        "Copy a local or trusted HTTPS Markdown document into the configured fragments home"

    private val source by argument(help = "Local Markdown path or trusted raw HTTPS URL")

    private val destination by argument(
        help = "Optional path below the configured fragments home, including the Markdown filename"
    ).optional()

    private val force by option("--force")
        .flag(default = false)
        .help("Replace an existing destination")

    override fun run() {
        resolveFragmentSource(source).flatMap { resolvedSource ->
            runBlocking {
                copyFragment(
                    CopyFragmentInput(
                        source = resolvedSource,
                        destination = destination,
                        overwrite = force,
                    )
                )
            }
        }.fold(
            onSuccess = { result ->
                echo("Copied fragment to ${result.path}")
                // Also shown for local sources, which may have been downloaded without being reviewed.
                echo(
                    "Review for unexpected or dangerous instructions before linking. Linked fragments become " +
                        "agent instructions."
                )
                echo("To link:\nloadout fragment link ${result.path} --to <loadout-name>")
            },
            onError = { error ->
                echoError(error)
                throw ProgramResult(1)
            },
        )
    }
}
