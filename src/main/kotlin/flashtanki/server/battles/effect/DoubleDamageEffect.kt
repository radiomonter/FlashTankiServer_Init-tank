package flashtanki.server.battles.effect

import kotlin.time.Duration.Companion.seconds
import flashtanki.server.battles.BattleTank

class DoubleDamageEffect(
  tank: BattleTank,
  val multiplier: Double = 2.0
) : TankEffect(
  tank,
  duration = 55.seconds,
  cooldown = 20.seconds
) {
  override val info: EffectInfo
    get() = EffectInfo(
      id = 3,
      name = "double_damage",
    )
}
