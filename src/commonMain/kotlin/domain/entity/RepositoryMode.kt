package domain.entity

/** Whether a repository shares its Loadout definitions with the team or keeps them in this clone. */
enum class RepositoryMode {
    Shared,
    Local,
}
