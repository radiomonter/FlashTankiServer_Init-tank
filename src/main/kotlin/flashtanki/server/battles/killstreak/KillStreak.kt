package flashtanki.server.battles.killstreak

data class KillStreak(
    val messageToBoss: String,
    val messageToVictims: String,
    val soundResourceId: Int
)