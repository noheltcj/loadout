package domain.entity

import kotlinx.serialization.Serializable

@Serializable
data class LoadoutConfig(
    val fragmentsDirectory: String = "fragments",
    val currentLoadoutName: String? = null,
    val globalSettings: GlobalSettings = GlobalSettings()
)

