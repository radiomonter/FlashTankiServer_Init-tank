package flashtanki.server.commands.handlers

import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import flashtanki.server.battles.BattleTeam
import flashtanki.server.battles.TankState
import flashtanki.server.battles.mode.CaptureTheFlagModeHandler
import flashtanki.server.battles.mode.FlagCarryingState
import flashtanki.server.battles.mode.FlagDroppedState
import flashtanki.server.battles.mode.FlagOnPedestalState
import flashtanki.server.battles.opposite
import flashtanki.server.client.UserSocket
import flashtanki.server.client.Vector3Data
import flashtanki.server.client.toVector
import flashtanki.server.commands.CommandHandler
import flashtanki.server.commands.CommandName
import flashtanki.server.commands.ICommandHandler
import flashtanki.server.anticheats.CaptureTheFlagAnticheatModel

class CtfBattleHandler : ICommandHandler, KoinComponent {
  private val logger = KotlinLogging.logger { }
  private val anticheatModel = CaptureTheFlagAnticheatModel()

  @CommandHandler(CommandName.TriggerFlag)
  suspend fun triggerFlag(socket: UserSocket, rawFlagTeam: String) {
    val player = socket.battlePlayer ?: throw Exception("No BattlePlayer")
    val tank = player.tank ?: throw Exception("No Tank")
    val battle = player.battle

    if (tank.state != TankState.SemiActive && tank.state !== TankState.Active) return

    val handler = battle.modeHandler
    if (handler !is CaptureTheFlagModeHandler) throw IllegalStateException("Battle mode handler must be ${CaptureTheFlagModeHandler::class.simpleName}")

    val flagTeam = BattleTeam.get(rawFlagTeam) ?: throw Exception("Invalid flag team: $rawFlagTeam")
    val flag = handler.flags[flagTeam]!! // TODO: Non-null assertion

    logger.debug { "Triggered flag ${flag.team}, state: ${flag::class.simpleName}" }
    if (player.team != flag.team && flag !is FlagCarryingState) {
      handler.captureFlag(flag.team, tank)
      anticheatModel.registerFlagCapture(socket, flag::class.java)

      logger.debug { "Captured ${flag.team} flag by ${player.user.username}" }
    } else if (player.team == flag.team) {
      val enemyFlag = handler.flags[flag.team.opposite]!!
      if (flag is FlagOnPedestalState && enemyFlag is FlagCarryingState && enemyFlag.carrier == tank) {
        handler.deliverFlag(enemyFlag.team, flag.team, tank)
        anticheatModel.checkFlagReturn(socket, flag::class.java)

        logger.debug { "Delivered ${enemyFlag.team} flag -> ${flag.team} pedestal by ${player.user.username}" }
      }

      if (flag is FlagDroppedState) {
        handler.returnFlag(flag.team, tank)
        anticheatModel.checkFlagReturn(socket, flag::class.java)

        logger.debug { "Returned ${flag.team} flag -> ${flag.team} pedestal by ${player.user.username}" }
      }
    }
  }

  @CommandHandler(CommandName.DropFlag)
  suspend fun dropFlag(socket: UserSocket, rawPosition: Vector3Data, isRaycast: Boolean) {
    val player = socket.battlePlayer ?: throw Exception("No BattlePlayer")
    val tank = player.tank ?: throw Exception("No Tank")
    val battle = player.battle

    val handler = battle.modeHandler
    if (handler !is CaptureTheFlagModeHandler) throw IllegalStateException("Battle mode handler must be ${CaptureTheFlagModeHandler::class.simpleName}")

    val position = rawPosition.toVector()

    val flag = handler.flags.values.single { enemyFlag -> enemyFlag is FlagCarryingState && enemyFlag.carrier == tank }
    if (isRaycast) {
      handler.dropFlag(flag.team, tank, position)
    } else {
      handler.returnFlag(flag.team, null)
    }

    anticheatModel.checkFlagReturn(socket, flag::class.java)

    logger.debug { "Dropped ${flag.team} flag by ${player.user.username}" }
  }
}
