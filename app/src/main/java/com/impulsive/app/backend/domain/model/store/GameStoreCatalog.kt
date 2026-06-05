package com.impulsive.app.backend.domain.model.store

/** Access state for a game in the store. */
enum class GameAccess { LOCKED, OWNED, RENTED }

/** A purchasable/rentable game slot. */
data class StoreGame(
    val id: String,
    val displayName: String,
    val buyPrice: Int,
    val rentPrice: Int,
    val defaultOwned: Boolean,
)

object GameStoreCatalog {
    const val RentPlays = 5

    // Control Points rule:
    // Award 50 Control Points only after 2 game wins in a row.
    // The streak is global across all games. A loss resets it.
    // Do not restore per-win or per-loss Control Points without explicit founder approval.
    const val TwoWinStreakControlPoints = 50

    val games: List<StoreGame> = listOf(
        StoreGame("REFLEX_OVERRIDE", "Reflex Override", buyPrice = 800, rentPrice = 300, defaultOwned = true),
        StoreGame("BLOCK_CASCADE", "Block Cascade", buyPrice = 1200, rentPrice = 500, defaultOwned = true),
        StoreGame("DOPAMINE_RUNNER", "Dopamine Redirect Runner", buyPrice = 1500, rentPrice = 500, defaultOwned = true),
        StoreGame("LOCKED_SLOT_1", "Coming soon", buyPrice = 1000, rentPrice = 400, defaultOwned = false),
        StoreGame("LOCKED_SLOT_2", "Coming soon", buyPrice = 1200, rentPrice = 500, defaultOwned = false),
        StoreGame("LOCKED_SLOT_3", "Coming soon", buyPrice = 1500, rentPrice = 600, defaultOwned = false),
        StoreGame("LOCKED_SLOT_4", "Coming soon", buyPrice = 1800, rentPrice = 700, defaultOwned = false),
        StoreGame("LOCKED_SLOT_5", "Coming soon", buyPrice = 2000, rentPrice = 800, defaultOwned = false),
    )

    fun byId(id: String): StoreGame? = games.firstOrNull { it.id == id }
}
