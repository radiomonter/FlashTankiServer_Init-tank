package flashtanki.server.commands.handlers

import kotlin.time.Duration.Companion.milliseconds
import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import flashtanki.server.HibernateUtils
import flashtanki.server.battles.BattleProperty
import flashtanki.server.battles.effect.*
import flashtanki.server.client.UserSocket
import kotlin.time.Duration.Companion.seconds
import java.util.*
import flashtanki.server.client.send
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.*
import flashtanki.server.client.*
import flashtanki.server.battles.*
import flashtanki.server.commands.CommandName
import flashtanki.server.commands.ICommandHandler
import flashtanki.server.garage.ServerGarageUserItemSupply
import flashtanki.server.extensions.launchDelayed

class BattleSupplyHandler : ICommandHandler, KoinComponent {
    private val logger = KotlinLogging.logger { }

    @CommandHandler(CommandName.ActivateItem)
    suspend fun activateItem(socket: UserSocket, item: String) {
        val user = socket.user ?: throw Exception("No User")
        val player = socket.battlePlayer ?: throw Exception("No BattlePlayer")
        val tank = player.tank ?: throw Exception("No Tank")

        val effect = when(item) {
            "health"        -> RepairKitEffect(tank)
            "armor"         -> DoubleArmorEffect(tank)
            "double_damage" -> DoubleDamageEffect(tank)
            "n2o"           -> NitroEffect(tank)
            "mine"          -> MineEffect(tank)
            "gold"          -> DropGoldBoxEffect(tank)
            else            -> throw Exception("Unknown item: $item")
        }

        // Apply dependent cooldowns only if not in Parkour mode
        if (!player.battle.properties[BattleProperty.ParkourMode] || player.battle.properties[BattleProperty.SuppliesCooldownEnabled]) {
            if (effect.info.name == "health") {
                Command(
                    CommandName.ActivateDependedCooldown,
                    "mine",
                    15000.toString()
                ).send(tank)
                Command(
                    CommandName.ActivateDependedCooldown,
                    "armor",
                    15000.toString()
                ).send(tank)
                Command(
                    CommandName.ActivateDependedCooldown,
                    "double_damage",
                    15000.toString()
                ).send(tank)
            }
            else if (effect.info.name == "armor") {
                Command(
                    CommandName.ActivateDependedCooldown,
                    "double_damage",
                    15000.toString()
                ).send(tank)
                Command(
                    CommandName.ActivateDependedCooldown,
                    "n2o",
                    15000.toString()
                ).send(tank)
            }
            else if (effect.info.name == "double_damage") {
                Command(
                    CommandName.ActivateDependedCooldown,
                    "armor",
                    15000.toString()
                ).send(tank)
                Command(
                    CommandName.ActivateDependedCooldown,
                    "n2o",
                    15000.toString()
                ).send(tank)
            }
            else if (effect.info.name == "n2o") {
                Command(
                    CommandName.ActivateDependedCooldown,
                    "armor",
                    15000.toString()
                ).send(tank)
                Command(
                    CommandName.ActivateDependedCooldown,
                    "double_damage",
                    15000.toString()
                ).send(tank)
            }
            else if (effect.info.name == "mine") {
                Command(
                    CommandName.ActivateDependedCooldown,
                    "health",
                    15000.toString()
                ).send(tank)
                Command(
                    CommandName.ActivateDependedCooldown,
                    "armor",
                    15000.toString()
                ).send(tank)
                Command(
                    CommandName.ActivateDependedCooldown,
                    "double_damage",
                    15000.toString()
                ).send(tank)
            }
            else if (effect.info.name == "gold") {
                Command(
                    CommandName.ActivateDependedCooldown,
                    "gold",
                    5000.toString()
                ).send(tank)
            }
        }

        tank.effects.add(effect)
        effect.run()

        var slotBlockTime = 0.milliseconds
        if (effect.duration != null) slotBlockTime += effect.duration
        if (player.battle.properties[BattleProperty.SuppliesCooldownEnabled] && effect.cooldown != null) slotBlockTime += effect.cooldown

        val supplyItem = user.items.singleOrNull { userItem ->
            userItem is ServerGarageUserItemSupply && userItem.marketItem.id == item
        } as? ServerGarageUserItemSupply

        supplyItem?.let {
            if (it.count > 0) {
                it.count -= 1

                val entityManager = HibernateUtils.createEntityManager()
                try {
                    entityManager.transaction.begin()
                    entityManager.merge(it)
                    entityManager.transaction.commit()
                } catch (error: Exception) {
                    entityManager.transaction.rollback()
                    throw Exception("Error while updating supply item count", error)
                } finally {
                    entityManager.close()
                }
            }
        }

        Command(
            CommandName.ClientActivateItem,
            effect.info.name,
            slotBlockTime.inWholeMilliseconds.toString(),
            true.toString() // Decrement item count in HUD (visual)
        ).send(socket)

        val cdwn = effect.cooldown ?: 3.seconds
        if (item != "gold" && (!player.battle.properties[BattleProperty.ParkourMode] || player.battle.properties[BattleProperty.SuppliesCooldownEnabled])) {
            var job: Job = tank.coroutineScope.launchDelayed(effect.duration ?: 3.seconds) {
                Command(
                    CommandName.ActivateDependedCooldown,
                    effect.info.name,
                    cdwn.inWholeMilliseconds.toString()
                ).send(tank)
            }
            if (tank.state == TankState.Dead) {
                job.cancel()
            }
        }
    }

    /*@CommandHandler(CommandName.ActivateUltimate)
    suspend fun activateUltimate(socket: UserSocket) {
        val user = socket.user ?: throw Exception("No User")
        val player = socket.battlePlayer ?: throw Exception("No BattlePlayer")
        val tank = player.tank ?: throw Exception("No Tank")
        val battle = player.battle
        val coroutineScope = CoroutineScope(player.coroutineScope.coroutineContext + SupervisorJob())

        try {
            Command(CommandName.ActivateUltimate2, user.username, "10013694").sendTo(battle)

            coroutineScope.launch {
                tank.effects.forEach { effect ->
                    effect.deactivate()
                }
                tank.effects.clear()
            }.join()

            coroutineScope.cancel()
            
			val effectss = listOf(RepairKitEffect(tank), DoubleArmorEffect(tank), DoubleDamageEffect(tank), NitroEffect(tank), MineEffect(tank))
			for (effectt in effectss) {
			    tank.effects.add(effectt)
                effectt.run()
			}

            player.ultimateCharge = 0
            tank.updateUltimateCharge()
        } finally {
            coroutineScope.cancel()
        }
    }*/

    @CommandHandler(CommandName.TryActivateBonus)
    suspend fun tryActivateBonus(socket: UserSocket, key: String) {
        val user = socket.user ?: throw Exception("No User")
        val player = socket.battlePlayer ?: throw Exception("No BattlePlayer")
        val tank = player.tank ?: throw Exception("No Tank")
        val battle = player.battle

        val type = key.substringBeforeLast("_")
        val id = key.substringAfterLast("_").toInt()

        val bonus = battle.bonusProcessor.bonuses[id]
        if (bonus == null) {
            logger.warn { "Attempt to activate missing bonus: $type@$id" }
            return
        }

        if (bonus.type.bonusKey != type) {
            logger.warn { "Attempt to activate bonus ($id) with wrong type. Actual: ${bonus.type}, received $type" }
        }

        battle.bonusProcessor.activate(bonus, tank)
    }
}
