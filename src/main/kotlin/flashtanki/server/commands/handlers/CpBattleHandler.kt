package flashtanki.server.commands.handlers

import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import flashtanki.server.battles.BattleTeam
import flashtanki.server.battles.TankState
import flashtanki.server.client.UserSocket
import flashtanki.server.battles.mode.ControlPointsModeHandler
import flashtanki.server.commands.CommandHandler
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandName
import flashtanki.server.commands.ICommandHandler

class CpBattleHandler : ICommandHandler, KoinComponent {
    private val logger = KotlinLogging.logger { }

    @CommandHandler(CommandName.TankCapturingPointServer)
    suspend fun tankCapturingPoint(socket: UserSocket, pointId: Int) {
        val player = socket.battlePlayer ?: throw Exception("No BattlePlayer")
        val tank = player.tank ?: throw Exception("No Tank")
        val battle = player.battle

        if(tank.state != TankState.SemiActive && tank.state !== TankState.Active) return

        val handler = battle.modeHandler
        if(handler !is ControlPointsModeHandler) throw IllegalStateException("Battle mode handler must be ${ControlPointsModeHandler::class.simpleName}")

        handler.tankCapturingPoint(tank, pointId)
        logger.debug("Start capturing point point: ${pointId}")
    }

    @CommandHandler(CommandName.TankLeaveCapturingPointServer)
    suspend fun tankLeaveCapturingPoint(socket: UserSocket, pointId: Int) {
        val player = socket.battlePlayer ?: throw Exception("No BattlePlayer")
        val tank = player.tank ?: throw Exception("No Tank")
        val battle = player.battle

        if(tank.state != TankState.SemiActive && tank.state !== TankState.Active) return

        val handler = battle.modeHandler
        if(handler !is ControlPointsModeHandler) throw IllegalStateException("Battle mode handler must be ${ControlPointsModeHandler::class.simpleName}")

        handler.tankLeaveCapturingPoint(tank, pointId)
        logger.debug("Leave capturing point point: ${pointId}")
    }

    fun getPointId(point: Int) : String {
        when (point) {
            0 -> return "A".toString()
            1 -> return "B".toString()
            2 -> return "C".toString()
            3 -> return "D".toString()
            4 -> return "E".toString()
            5 -> return "F".toString()
            6 -> return "G".toString()
            else -> return "A".toString()
        }
        return "A".toString()
    }

}