package flashtanki.server.battles.weapons

import flashtanki.server.battles.BattlePlayer
import flashtanki.server.garage.ServerGarageUserItemWeapon

class NullWeaponHandler(
  player: BattlePlayer,
  weapon: ServerGarageUserItemWeapon
) : WeaponHandler(player, weapon)
