package flashtanki.server.battles

import flashtanki.server.battles.effect.DoubleArmorEffect
import flashtanki.server.battles.effect.DoubleDamageEffect
import flashtanki.server.battles.effect.TankEffect
import flashtanki.server.battles.mode.TeamModeHandler
import flashtanki.server.battles.mode.DeathmatchModeHandler
import flashtanki.server.battles.weapons.*
import flashtanki.server.client.send
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandName
import flashtanki.server.extensions.singleOrNullOf

enum class DamageType(val id: Int, val key: String) {
  Normal(0, "NORMAL"),
  Critical(1, "CRITICAL"),
  Kill(2, "FATAL"),
  Heal(3, "HEAL");

  companion object {
    private val map = values().associateBy(DamageType::key)

    fun get(key: String) = map[key]
  }
}

interface IDamageProcessor {
  val battle: Battle

  suspend fun dealDamage(source: BattleTank, target: BattleTank, damage: Double, isCritical: Boolean, ignoreSourceEffects: Boolean = false)
  suspend fun dealDamage(target: BattleTank, damage: Double, isCritical: Boolean): DamageType

  suspend fun heal(source: BattleTank, target: BattleTank, heal: Double)
  suspend fun heal(target: BattleTank, heal: Double)
}

class DamageProcessor(
  override val battle: Battle
) : IDamageProcessor {
  override suspend fun dealDamage(
    source: BattleTank,
    target: BattleTank,
    damage: Double,
    isCritical: Boolean,
    ignoreSourceEffects: Boolean
  ) {

    val resistanceProperty = when(source.weapon) {
      is TwinsWeaponHandler      -> "TWINS_RESISTANCE"
      is ThunderWeaponHandler    -> "THUNDER_RESISTANCE"
      is RailgunWeaponHandler    -> "RAILGUN_RESISTANCE"
      is Railgun_XTWeaponHandler -> "RAILGUN_RESISTANCE"
      is ShaftWeaponHandler      -> "SHAFT_RESISTANCE"
      is IsidaWeaponHandler      -> "ISIS_RESISTANCE"
      is FreezeWeaponHandler     -> "FREEZE_RESISTANCE"
      is RicochetWeaponHandler   -> "RICOCHET_RESISTANCE"
      is SmokyWeaponHandler      -> "SMOKY_RESISTANCE"
	  is FlamethrowerWeaponHandler -> "FIREBIRD_RESISTANCE"
      else                       -> null
    }

    val hullArmor = target.resistance.marketItem.properties
      .find { it.property == resistanceProperty }
      ?.value as Double?

    var totalDamage = hullArmor?.let { damage * (1 - it / 100).coerceAtLeast(0.2) } ?: damage

    if(!battle.properties[BattleProperty.DamageEnabled]) return

    var dealDamage = true
    if(battle.modeHandler is TeamModeHandler) {
      if(source.player.team == target.player.team && !battle.properties[BattleProperty.FriendlyFireEnabled]) dealDamage = false
    }
    if(source == target && battle.properties[BattleProperty.SelfDamageEnabled]) dealDamage = true // TODO(Assasans): Check weapon
    if(!dealDamage) return

    if(!ignoreSourceEffects) {
      source.effects.singleOrNullOf<TankEffect, DoubleDamageEffect>()?.let { effect ->
        totalDamage *= effect.multiplier
      }
    }

    target.effects.singleOrNullOf<TankEffect, DoubleArmorEffect>()?.let { effect ->
      totalDamage /= effect.multiplier
    }

    val damageType = dealDamage(target, totalDamage, isCritical)
    if(damageType == DamageType.Kill) {
      target.killBy(source)
    }
	
    if(source != target && (battle.properties[BattleProperty.FriendlyFireEnabled] || (source != target && (battle.modeHandler is DeathmatchModeHandler || source.player.team != target.player.team)))) {
      Command(CommandName.DamageTank, target.id, totalDamage.toString(), damageType.key).send(source)
      }
    }

  override suspend fun dealDamage(target: BattleTank, damage: Double, isCritical: Boolean): DamageType {
    var damageType = if(isCritical) DamageType.Critical else DamageType.Normal

    target.health = (target.health - damage).coerceIn(0.0, target.hull.modification.maxHealth)
    target.updateHealth()
    if(target.health <= 0.0) {
      damageType = DamageType.Kill
    }

    return damageType
  }

  override suspend fun heal(source: BattleTank, target: BattleTank, heal: Double) {
    heal(target, heal)

    Command(CommandName.DamageTank, target.id, heal.toString(), DamageType.Heal.key).send(source)
  }

  override suspend fun heal(target: BattleTank, heal: Double) {
    target.health = (target.health + heal).coerceIn(0.0, target.hull.modification.maxHealth)
    target.updateHealth()
  }
}
