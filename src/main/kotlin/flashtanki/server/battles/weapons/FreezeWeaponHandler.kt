package flashtanki.server.battles.weapons

import flashtanki.server.battles.*
import flashtanki.server.battles.mode.DeathmatchModeHandler
import flashtanki.server.client.ChangeTankSpecificationData
import flashtanki.server.client.send
import flashtanki.server.client.toJson
import flashtanki.server.client.weapons.freeze.FireTarget
import flashtanki.server.client.weapons.freeze.StartFire
import flashtanki.server.client.weapons.freeze.StopFire
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandName
import flashtanki.server.garage.ServerGarageUserItemWeapon
import kotlinx.coroutines.delay

class FreezeWeaponHandler(
    player: BattlePlayer,
    weapon: ServerGarageUserItemWeapon
) : WeaponHandler(player, weapon) {
    private var fireStarted = false
    private var temperature = 0.0
    private var originalSpeed: MutableMap<String, Double?> = mutableMapOf()
    private var originalTurnSpeed: MutableMap<String, Double?> = mutableMapOf()
    private var originalTurretRotationSpeed: MutableMap<String, Double?> = mutableMapOf()
    private var speedReduced = mutableMapOf<String, Boolean>()
    private val affectedTanks = mutableSetOf<String>()
    private var operationDone = false

    private suspend fun updateTankSpecifications(targetTank: BattleTank, multiplier: Double) {
        val targetSpecification = ChangeTankSpecificationData.fromPhysics(
            targetTank.hull.modification.physics,
            targetTank.weapon.item.modification.physics
        )
        originalSpeed[targetTank.id] = targetSpecification.speed
        originalTurnSpeed[targetTank.id] = targetSpecification.turnSpeed
        originalTurretRotationSpeed[targetTank.id] = targetSpecification.turretRotationSpeed

        targetSpecification.speed *= multiplier
        targetSpecification.turnSpeed *= multiplier
        targetSpecification.turretRotationSpeed *= multiplier

        Command(
            CommandName.ChangeTankSpecification,
            targetTank.id,
            targetSpecification.toJson()
        ).sendTo(player.battle)
    }

    private suspend fun sendTemperatureUpdate(targetTankId: String, temperature: Double, delayTime: Long = 0) {
        delay(delayTime)
        Command(CommandName.Temperature, targetTankId, temperature.toString()).sendTo(player.battle)
    }

    suspend fun fireStart(startFire: StartFire) {
        if (fireStarted) return
        val tank = player.tank ?: throw Exception("No Tank")
        fireStarted = true
        Command(CommandName.ClientStartFire, tank.id).send(player.battle.players.exclude(player).ready())
    }

    suspend fun fireTarget(target: FireTarget) {
        if (!fireStarted) return
        val sourceTank = player.tank ?: throw Exception("No Tank")

        player.battle.players.mapNotNull { it.tank }
            .filter { target.targets.contains(it.id) && it.state == TankState.Active }
            .forEach { targetTank ->
                if (sourceTank != targetTank && (player.battle.properties[BattleProperty.FriendlyFireEnabled] || player.battle.modeHandler is DeathmatchModeHandler || player.team != targetTank.player.team)
                ) {
                    affectedTanks.add(targetTank.id)
                    val damage = damageCalculator.calculate(sourceTank, targetTank)
                    player.battle.damageProcessor.dealDamage(sourceTank, targetTank, damage.damage, isCritical = damage.isCritical)

                    if (speedReduced[targetTank.id] != true) {
                        updateTankSpecifications(targetTank, 0.3)
                        speedReduced[targetTank.id] = true
                        sendTemperatureUpdate(targetTank.id, -0.8)
                        delay(5000)
                    }
                }
            }
    }

    suspend fun fireStop(stopFire: StopFire) {
        if (!fireStarted) return
        val tank = player.tank ?: throw Exception("No Tank")
        fireStarted = false
        temperature = 0.0
        Command(CommandName.ClientStopFire, tank.id).send(player.battle.players.exclude(player).ready())

        delay(3500)
        affectedTanks.forEach { targetTankId ->
            val targetTank = player.battle.players.mapNotNull { it.tank }.firstOrNull { it.id == targetTankId }
            if (targetTank != null && targetTank.state == TankState.Active) {
                operationDone = true
                if (speedReduced[targetTank.id] == true) {
                    updateTankSpecifications(targetTank, 1.0)
                    speedReduced[targetTank.id] = false
                }
                sendTemperatureUpdates(targetTank.id)
            }
        }
        affectedTanks.clear()
        operationDone = false
    }

    suspend fun Repair() {
        affectedTanks.forEach { targetTankId ->
            val targetTank = player.battle.players.mapNotNull { it.tank }.firstOrNull { it.id == targetTankId }
            sendTemperatureUpdate(targetTankId, 0.0)
            if (targetTank != null && targetTank.state == TankState.Active) {
                operationDone = true
                if (speedReduced[targetTank.id] == true) {
                    updateTankSpecifications(targetTank, 0.0)
                    speedReduced[targetTank.id] = false
                }
            }
        }
        affectedTanks.clear()
    }

    private suspend fun sendTemperatureUpdates(targetTankId: String) {
        sendTemperatureUpdate(targetTankId, -0.6, 500)
        sendTemperatureUpdate(targetTankId, -0.3, 1000)
        sendTemperatureUpdate(targetTankId, temperature, 1500)
    }
}
