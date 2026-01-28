package flashtanki.server.battles.bonus

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import flashtanki.server.ServerMapBonusPoint
import kotlinx.coroutines.delay
import flashtanki.server.extensions.launchDelayed
import flashtanki.server.*
import flashtanki.server.battles.Battle
import flashtanki.server.battles.BattleTank
import flashtanki.server.battles.sendTo
import flashtanki.server.commands.Command
import flashtanki.server.garage.*
import flashtanki.server.client.*
import flashtanki.server.commands.CommandName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.inject
import flashtanki.server.math.Quaternion
import flashtanki.server.math.Vector3
import org.koin.core.component.KoinComponent

class BattleContainerBonus(battle: Battle, id: Int, position: Vector3, rotation: Quaternion, bonusPoint: ServerMapBonusPoint, siren:String="Скоро будет сброшен золотой ящик") :
  BattleBonus(battle, id, position, rotation), KoinComponent {
  override val type: BonusType = BonusType.Container
  val goldBoxSiren: String = siren
  val bonusPoint = bonusPoint
  val userRepository: IUserRepository by inject()

  override suspend fun spawn() {
    Command(CommandName.SpawnGold, goldBoxSiren, 490113.toString()).sendTo(battle)
	Command(CommandName.AddOneGoldRegion, position.x.toString(), position.y.toString(), position.z.toString(), id.toString()).sendTo(battle)
    battle.droppedGoldIds.add(id.toString())
    val spawnBonusJob = battle.coroutineScope.launchDelayed(30.seconds) {
        super.spawn()
	}
    battle.goldBoxesIntervals.add(spawnBonusJob)
  }

  override suspend fun activate(tank: BattleTank) {
    val user = tank.player.user
    val itemId = "lootbox"
    val entityManager = HibernateUtils.createEntityManager()
    var currentItem = user.items.singleOrNull { userItem -> userItem.marketItem.id == itemId }
    Command(CommandName.RemoveOneGoldRegion, id.toString()).sendTo(battle)
    Command(CommandName.TakeGold, tank.id, false.toString()).sendTo(battle)
    battle.droppedGoldIds.remove(id.toString())
	battle.droppedGoldBoxes.remove(bonusPoint)
    if (battle.unusedGoldBoxes.isNotEmpty()) {
      battle.spawnGoldBonus()
      battle.unusedGoldBoxes.removeAt(0)
    }
    if (currentItem == null) {
      entityManager.transaction.begin()
      currentItem = ServerGarageUserItemLootbox(user, itemId, 1)
      user.items.add(currentItem)
      entityManager.persist(currentItem)
      userRepository.updateUser(user)
      entityManager.transaction.commit()
    } else {
      entityManager.transaction.begin()
      val supplyItem = currentItem as ServerGarageUserItemLootbox
      supplyItem.count += 1

      withContext(Dispatchers.IO) {
        entityManager
                .createQuery("UPDATE ServerGarageUserItemLootbox SET count = :count WHERE id = :id")
                .setParameter("count", supplyItem.count)
                .setParameter("id", supplyItem.id)
                .executeUpdate()
      }
      entityManager.transaction.commit()
    }
  }
}
