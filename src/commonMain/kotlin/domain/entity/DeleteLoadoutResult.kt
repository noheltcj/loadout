package domain.entity

data class DeleteLoadoutResult(
    val loadoutName: String,
    val clearedCurrentLoadout: Boolean,
)
