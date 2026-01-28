package flashtanki.server.battles.effect

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import flashtanki.server.battles.BattleTank
import flashtanki.server.battles.DamageType
import flashtanki.server.battles.weapons.FlamethrowerWeaponHandler
import flashtanki.server.battles.weapons.FreezeWeaponHandler
import flashtanki.server.battles.weapons.IsidaWeaponHandler
import flashtanki.server.client.send
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandName
import kotlin.time.Duration.Companion.seconds

class RepairKitEffect(
    tank: BattleTank,
    private var isActive: Boolean = false
) : TankEffect(
    tank,
    duration = 2.seconds,
    cooldown = 20.seconds
) {
    private var totalHealing = 0.0
    private var showVisualEffect = true

    override val info: EffectInfo
        get() = EffectInfo(
            id = 1,
            name = "health"
        )

    override suspend fun activate() {
        val maxHealth = tank.hull.modification.maxHealth
        val totalHealAmount = 4000.0
        val healInterval = 100.0
        val healPerInterval = 200.0

        isActive = true
        val battle = tank.battle
        val damageProcessor = battle.damageProcessor

        if (duration == null) return

        tank.coroutineScope.launch {
            val startTime = Clock.System.now()
            val endTime = startTime + duration

            when(tank.weapon) {
                is FlamethrowerWeaponHandler -> tank.weapon.stopAllOperations()
                is FreezeWeaponHandler       -> tank.weapon.Repair()
            }

            while (Clock.System.now() < endTime && isActive && totalHealing < totalHealAmount) {
                delay(healInterval.toLong())
                val remainingHealAmount = minOf(healPerInterval, totalHealAmount - totalHealing)

                if (tank.health < maxHealth) {
                    damageProcessor.heal(tank, remainingHealAmount)
                    Command(CommandName.DamageTank, tank.id, remainingHealAmount.toString(), DamageType.Heal.key).send(tank)
                    showVisualEffect = true
                } else {
                    showVisualEffect = false
                }

                totalHealing += remainingHealAmount
            }
            deactivateEffect()
        }
    }

    private fun deactivateEffect() {
        isActive = false
        showVisualEffect = true
    }
}
