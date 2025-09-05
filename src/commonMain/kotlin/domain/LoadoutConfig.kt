package domain

import kotlinx.serialization.Serializable

@Serializable
data class LoadoutConfig(
    val outputDirectory: String = ".",
    val fragmentsDirectory: String = "fragments",
    val currentLoadout: String? = null,
    val globalSettings: GlobalSettings = GlobalSettings()
) {
    
    fun validate(): List<String> = buildList {
        if (outputDirectory.isBlank()) add("Output directory cannot be blank")
        if (fragmentsDirectory.isBlank()) add("Fragments directory cannot be blank")
    }
    
    fun withCurrentLoadout(loadoutName: String?): LoadoutConfig = copy(currentLoadout = loadoutName)
}

@Serializable
data class GlobalSettings(
    val dryRunByDefault: Boolean = false,
    val verboseOutput: Boolean = false,
    val outputFormat: OutputFormat = OutputFormat.MARKDOWN,
    val includeMetadata: Boolean = false
)

@Serializable
enum class OutputFormat(val extension: String) {
    MARKDOWN("md"),
    TEXT("txt");
    
    companion object {
        fun fromExtension(extension: String): OutputFormat? = 
            values().find { it.extension.equals(extension, ignoreCase = true) }
    }
}