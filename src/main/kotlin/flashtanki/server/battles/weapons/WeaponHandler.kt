package flashtanki.server.battles.weapons

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import flashtanki.server.battles.BattlePlayer
import flashtanki.server.battles.IDamageCalculator
import flashtanki.server.garage.ServerGarageUserItemWeapon

abstract class WeaponHandler(
  val player: BattlePlayer,
  val item: ServerGarageUserItemWeapon
) : KoinComponent {
  protected val damageCalculator: IDamageCalculator by inject()
}
