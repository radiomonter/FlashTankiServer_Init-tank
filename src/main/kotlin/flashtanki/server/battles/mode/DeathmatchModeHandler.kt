package flashtanki.server.battles.mode

import flashtanki.server.battles.*
import flashtanki.server.client.*
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandName

class DeathmatchModeHandler(battle: Battle) : BattleModeHandler(battle) {
  companion object {
    fun builder(): BattleModeHandlerBuilder = { battle -> DeathmatchModeHandler(battle) }
  }

  override val mode: BattleMode get() = BattleMode.Deathmatch

  override suspend fun playerJoin(player: BattlePlayer) {
    val players = battle.players.users().toStatisticsUsers()

    Command(
      CommandName.InitDmStatistics,
      InitDmStatisticsData(users = players).toJson()
    ).send(player)

    if(player.isSpectator) return

    Command(
      CommandName.BattlePlayerJoinDm,
      BattlePlayerJoinDmData(
        id = player.user.username,
        players = players
      ).toJson()
    ).send(battle.players.exclude(player).ready())
  }

  override suspend fun playerLeave(player: BattlePlayer) {
    if(player.isSpectator) return

    Command(
      CommandName.BattlePlayerLeaveDm,
      player.user.username
    ).send(battle.players.exclude(player).ready())
  }

  override suspend fun initModeModel(player: BattlePlayer) {
    Command(CommandName.InitDmModel).send(player)
  }
}
