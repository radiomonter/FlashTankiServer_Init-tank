package flashtanki.server.battles.mode

import flashtanki.server.battles.Battle
import flashtanki.server.battles.BattleMode
import flashtanki.server.battles.BattlePlayer
import flashtanki.server.client.send
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandName

class TeamDeathmatchModeHandler(battle: Battle) : TeamModeHandler(battle) {
  companion object {
    fun builder(): BattleModeHandlerBuilder = { battle -> TeamDeathmatchModeHandler(battle) }
  }

  override val mode: BattleMode get() = BattleMode.TeamDeathmatch

  override suspend fun initModeModel(player: BattlePlayer) {
    Command(CommandName.InitTdmModel).send(player)
  }

  suspend fun updateScores(killer: BattlePlayer, victim: BattlePlayer) {
    if(killer.team != victim.team) {
      val killerTeam = killer.team
      teamScores.merge(killerTeam, 1, Int::plus)
      updateScores()
    }
  }

  suspend fun decreaseScore(player: BattlePlayer) {
    if(teamScores[player.team] != 0) {
      teamScores.merge(player.team, -1) { score, _ -> maxOf(score - 1, 0) }
      updateScores()
    }
  }
}
