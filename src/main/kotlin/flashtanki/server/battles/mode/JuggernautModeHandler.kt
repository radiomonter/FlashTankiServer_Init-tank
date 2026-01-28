package flashtanki.server.battles.mode

import flashtanki.server.HibernateUtils
import flashtanki.server.battles.*
import flashtanki.server.client.*
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandName
import flashtanki.server.battles.killstreak.*
import flashtanki.server.garage.ServerGarageUserItemHull
import flashtanki.server.garage.ServerGarageUserItemPaint
import flashtanki.server.garage.ServerGarageUserItemResistance
import flashtanki.server.garage.ServerGarageUserItemWeapon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class JuggernautModeHandler(battle: Battle) : BattleModeHandler(battle), KoinComponent {
  companion object {
    fun builder(): BattleModeHandlerBuilder = { battle -> JuggernautModeHandler(battle) }
  }

  private val userRepository by inject<IUserRepository>()

  override val mode: BattleMode get() = BattleMode.Juggernaut
  var bossId: String = ""
  var bossKills: Int = 0

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
    if (bossId == "") {
      bossId = battle.players.get(0).user.username
    }
      // TODO Darl1xVFX juggernaut logic
     /* val JuggernautUserName = (battle.modeHandler as JuggernautModeHandler)
      val user = player.user
      if (player.user.username == JuggernautUserName.bossId) {

    HibernateUtils.createEntityManager().let { entityManager ->

      entityManager.transaction.begin()

      user.items += listOf(
        ServerGarageUserItemWeapon(user, "terminator", modificationIndex = 0),
        ServerGarageUserItemHull(user, "juggernaut", modificationIndex = 0)
      )
      user.equipment.weaponId = "terminator"
      user.equipment.hullId = "juggernaut"

      user.items.forEach { item -> entityManager.persist(item) }

      entityManager.transaction.commit()

      player.changeEquipment(true)

        }
      }*/
  }

  suspend fun addBossKillsAndCheckKillStreak(tankId: String)
  {
    if (bossId != "" && bossId == tankId)
    {
      bossKills++
      when (bossKills)
      {
        1 -> Command(CommandName.KillStreakAchived, 0.toString()).send(battle.players.ready())
        3 -> Command(CommandName.KillStreakAchived, 1.toString()).send(battle.players.ready())
        5 -> Command(CommandName.KillStreakAchived, 2.toString()).send(battle.players.ready())
        7 -> Command(CommandName.KillStreakAchived, 3.toString()).send(battle.players.ready())
        10 -> Command(CommandName.KillStreakAchived, 4.toString()).send(battle.players.ready())
        13 -> Command(CommandName.KillStreakAchived, 5.toString()).send(battle.players.ready())
        15 -> Command(CommandName.KillStreakAchived, 6.toString()).send(battle.players.ready())
        17 -> Command(CommandName.KillStreakAchived, 7.toString()).send(battle.players.ready())
        20 -> Command(CommandName.KillStreakAchived, 8.toString()).send(battle.players.ready())
      }
    }
  }

  override suspend fun playerLeave(player: BattlePlayer) {
    if(player.isSpectator) return

    Command(
      CommandName.BattlePlayerLeaveDm,
      player.user.username
    ).send(battle.players.exclude(player).ready())
	if (bossId == player.user.username)
	{
	   bossId = ""
	}
  }

  override suspend fun initModeModel(player: BattlePlayer) {
    Command(CommandName.InitJgrModel, KillStreaksData(KillStreakParser().parse(player.socket.locale)).toJson()).send(player)
  }
}
