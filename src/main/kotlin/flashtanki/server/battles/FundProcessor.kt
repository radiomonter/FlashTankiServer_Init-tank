package flashtanki.server.battles

import flashtanki.server.battles.mode.BattleModeHandler
import flashtanki.server.battles.mode.TeamModeHandler
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandName
import org.koin.core.component.KoinComponent
import kotlin.random.Random

interface IFundProcessor {
  val battle: Battle
  var fund: Int

  suspend fun updateFund()
  suspend fun calculateFund(battle: Battle, mode: BattleModeHandler): MutableList<Pair<String, Double>>
}

class FundProcessor(
  override val battle: Battle
) : IFundProcessor, KoinComponent {
  override var fund: Int = 0
  private var randomGoldFund: Int = Random.nextInt(100, 300)
  private var goldFund: Int = 0

  override suspend fun updateFund() {
    Command(CommandName.ChangeFund, fund.toString()).sendTo(battle)
    goldFund += fund
    if (goldFund >= randomGoldFund) {
      battle.spawnGoldBonus()
      randomGoldFund = Random.nextInt(100, 300) + fund
      goldFund = 0
    }
  }

  override suspend fun calculateFund(battle: Battle, mode: BattleModeHandler): MutableList<Pair<String, Double>> {
    val playerPrizeList = mutableListOf<Pair<String, Double>>()

    if (battle.players.isEmpty()) {
      return playerPrizeList
    }

    if (mode is TeamModeHandler) {
      val redTeam = battle.players.users().filter { it.team == BattleTeam.Red }
      val blueTeam = battle.players.users().filter { it.team == BattleTeam.Blue }

      if (redTeam.isEmpty() || blueTeam.isEmpty()) {
        return playerPrizeList
      }

      val redTeamScore = mode.teamScores[BattleTeam.Red] ?: 0
      val blueTeamScore = mode.teamScores[BattleTeam.Blue] ?: 0

      val totalTeamScores = redTeamScore + blueTeamScore

      val redPercentage = if (totalTeamScores > 0) (redTeamScore.toDouble() / totalTeamScores).coerceAtLeast(0.2) else 0.5
      val bluePercentage = if (totalTeamScores > 0) (blueTeamScore.toDouble() / totalTeamScores).coerceAtLeast(0.2) else 0.5

      val redTeamFund = redPercentage * fund
      val blueTeamFund = bluePercentage * fund

      val totalScoreRed = redTeam.sumOf { it.score }
      val totalScoreBlue = blueTeam.sumOf { it.score }

      playerPrizeList.addAll(redTeam.map { player ->
        val teamPrize = if (totalScoreRed > 0 && player.kills > 0) (player.score.toDouble() / totalScoreRed) * redTeamFund else 0.0
        Pair(player.user.username, teamPrize.coerceIn(0.0, Double.MAX_VALUE))
      })

      playerPrizeList.addAll(blueTeam.map { player ->
        val teamPrize = if (totalScoreBlue > 0 && player.kills > 0) (player.score.toDouble() / totalScoreBlue) * blueTeamFund else 0.0
        Pair(player.user.username, teamPrize.coerceIn(0.0, Double.MAX_VALUE))
      })

    } else {
      val totalDestroyed = battle.players.sumOf { it.kills }
      if (totalDestroyed == 0) {
        return playerPrizeList
      }

      val sortedPlayers = battle.players.sortedByDescending { it.kills }

      playerPrizeList.addAll(sortedPlayers.map { player ->
        val prize = if (fund > 0) fund * (player.kills.toDouble() / totalDestroyed) else 0.0
        Pair(player.user.username, prize.coerceIn(0.0, Double.MAX_VALUE))
      })
    }
    return playerPrizeList
  }
}