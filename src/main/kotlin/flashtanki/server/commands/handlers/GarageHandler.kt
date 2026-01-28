package flashtanki.server.commands.handlers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import flashtanki.server.HibernateUtils
import flashtanki.server.battles.BattleProperty
import java.util.*
import flashtanki.server.client.*
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandHandler
import flashtanki.server.commands.CommandName
import flashtanki.server.commands.ICommandHandler
import flashtanki.server.ISocketServer
import flashtanki.server.garage.*
import flashtanki.server.garage.lootbox.LootboxPrizeService

/*
Switch to garage from battle:
-> switch_garage
<- change_layout_state [GARAGE]
<- unload_battle
-> i_exit_from_battle
<- init_messages
* load garage resources *
<- init_garage_items [{"items":[...]}]
-> get_garage_data
<- init_market [{"items":[...]}]
<- end_layout_switch [garage, garage]
<- init_mounted_item [hunter_m0, 227169]
<- init_mounted_item [railgun_m0, 906685]
<- init_mounted_item [green_m0, 966681]
*/

class GarageHandler : ICommandHandler, KoinComponent {
  private val logger = KotlinLogging.logger { }

  private val marketRegistry by inject<IGarageMarketRegistry>()
  private val socketServer by inject<ISocketServer>()
  private val userRepository by inject<IUserRepository>()

  @CommandHandler(CommandName.TryMountPreviewItem)
  suspend fun tryMountPreviewItem(socket: UserSocket, item: String) {
    Command(CommandName.MountItem, item, false.toString()).send(socket)
  }

  @CommandHandler(CommandName.TryMountItem)
  suspend fun tryMountItem(socket: UserSocket, rawItem: String) {
    val user = socket.user ?: throw Exception("No User")

    val item = rawItem.substringBeforeLast("_")
    val marketItem = marketRegistry.get(item)
    val currentItem = user.items.singleOrNull { userItem -> userItem.marketItem.id == item }

    logger.debug { "Trying to mount ${marketItem.id}..." }

    if(currentItem == null) {
      logger.debug { "Player ${user.username} (${user.id}) tried to mount not owned item: ${marketItem.id}" }
      return
    }

    val entityManager = HibernateUtils.createEntityManager()
    entityManager.transaction.begin()

    when(currentItem) {
      is ServerGarageUserItemWeapon -> user.equipment.weapon = currentItem
      is ServerGarageUserItemHull   -> user.equipment.hull = currentItem
      is ServerGarageUserItemPaint  -> user.equipment.paint = currentItem
      is ServerGarageUserItemResistance  -> user.equipment.resistance = currentItem

      else                          -> {
        logger.debug { "Player ${user.username} (${user.id}) tried to mount invalid item: ${marketItem.id} (${currentItem::class.simpleName})" }
        return
      }
    }

    withContext(Dispatchers.IO) {
      entityManager
        .createQuery("UPDATE User SET equipment = :equipment WHERE id = :id")
        .setParameter("equipment", user.equipment)
        .setParameter("id", user.id)
        .executeUpdate()
    }
    entityManager.transaction.commit()
    entityManager.close()

    val player = socket.battlePlayer
    if(player != null) {
      if(!player.battle.properties[BattleProperty.RearmingEnabled]) {
        logger.warn { "Player ${player.user.username} attempted to change equipment in battle with disabled rearming" }
        return
      }
      when(currentItem) {
        is ServerGarageUserItemWeapon -> socket.weaponDelayMount = 15.minutes.inWholeSeconds.toInt()
        is ServerGarageUserItemHull   -> socket.hullDelayMount = 15.minutes.inWholeSeconds.toInt()
        is ServerGarageUserItemResistance  -> socket.colormapDelayMount = 15.minutes.inWholeSeconds.toInt()

        else                          -> {
          return
        }
      }
      if (socket.weaponDelayMount > 0) {
        val timer1 = Timer()
        val task1 = object : TimerTask() {
          override fun run() {
            socket.weaponDelayMount--

            if (socket.weaponDelayMount <= 0) {
              socket.weaponDelayMount = 0
              timer1.cancel()
            }
          }
        }

        timer1.scheduleAtFixedRate(task1, 0, 1200)
      }
      if (socket.hullDelayMount > 0) {
        val timer2 = Timer()
        val task2 = object : TimerTask() {
          override fun run() {
            socket.hullDelayMount--

            if (socket.hullDelayMount <= 0) {
              socket.hullDelayMount = 0
              timer2.cancel()
            }
          }
        }

        timer2.scheduleAtFixedRate(task2, 0, 1200)
      }
      if (socket.colormapDelayMount > 0) {
        val timer3 = Timer()
        val task3 = object : TimerTask() {
          override fun run() {
            socket.colormapDelayMount--

            if (socket.colormapDelayMount <= 0) {
              socket.colormapDelayMount = 0
              timer3.cancel()
            }
          }
        }

        timer3.scheduleAtFixedRate(task3, 0, 1200)
      }
      player.equipmentChanged = true
    }

    Command(CommandName.MountItem, currentItem.mountName, true.toString()).send(socket)
  }
  
  //TODO(TitanoMachina) код подарков тут
  @CommandHandler(CommandName.PurchasePresent)
  suspend fun purchasePresent(socket: UserSocket, nameTo: String, idPres: String, text: String) {
     val player = if(nameTo != null) socketServer.players.find { it.user?.username == nameTo } else socket
     val user = userRepository.getUser(nameTo) ?: return
     val entityManager = HibernateUtils.createEntityManager()
     if (player?.screen != Screen.Battle) {
       Command(CommandName.ShowPresentsAlert).send(player ?: socket)
     }
    user.items += listOf(ServerGarageUserItemPresent(user, idPres))
  }

  @CommandHandler(CommandName.OpenLootboxServer)
  suspend fun openLootboxServer(socket: UserSocket, count: Int) {
    val selfUser = socket.user ?: throw Exception("No User")
    val lootboxItem = selfUser.items.singleOrNull { userItem ->
      userItem is ServerGarageUserItemLootbox && userItem.marketItem.id == "lootbox"
    } as? ServerGarageUserItemLootbox

    lootboxItem?.let {
      if(it.count > 0) {
        it.count -= count

        val entityManager = HibernateUtils.createEntityManager()
        try {
          entityManager.transaction.begin()
          entityManager.merge(it)
          entityManager.transaction.commit()
        } catch(error: Exception) {
          entityManager.transaction.rollback()
          throw Exception("Error while updating garage item count", error)
        } finally {
          entityManager.close()
        }
      }
    }
    Command(CommandName.OpenLootboxClient, LootboxPrizeService().getRandomReward(socket, count).toJson()).send(socket)
  }
  
  @CommandHandler(CommandName.SetShowGoldAuthor)
  suspend fun setShowGoldAuthor(socket: UserSocket, show: Boolean) {
     socket.showGoldAuthor = show
  }

  // TODO(Assasans): Code repeating
  @CommandHandler(CommandName.TryBuyItem)
  suspend fun tryBuyItem(socket: UserSocket, rawItem: String, count: Int) =
    tryBuyItemInternal(socket, rawItem, count, false)

  private suspend fun tryBuyItemInternal(socket: UserSocket, rawItem: String, count: Int, fromKit: Boolean) {
    val user = socket.user ?: throw Exception("No User")

    if(count < 1) {
      logger.debug { "Player ${user.username} (${user.id}) tried to buy invalid count of items: $count" }
      return
    }

    val entityManager = HibernateUtils.createEntityManager()
    entityManager.transaction.begin()

    val item = rawItem.substringBeforeLast("_")
    val modIndex = rawItem.substringAfterLast("_m").toIntOrNull() ?: -1
    val marketItem = marketRegistry.get(item)
    var currentItem = user.items.singleOrNull { userItem -> userItem.marketItem.id == item }

    var isNewItem = false

    try {
      when(marketItem) {
        is ServerGarageItemWeapon -> {
          if(currentItem == null) {
            currentItem = ServerGarageUserItemWeapon(user, marketItem.id, 0)
            user.items.add(currentItem)
            isNewItem = true

            val price = currentItem.modification.price
            if(user.crystals < price) {
              logger.debug { "Player ${user.username} (${user.id}) tried to buy item: ${marketItem.id} ($price crystals), but does not have enough crystals (user: ${user.crystals} crystals, delta: ${user.crystals - price} crystals)" }
              return
            }
            user.crystals -= price

            logger.debug { "Bought weapon ${marketItem.id} ($price crystals)" }
          }
        }

        is ServerGarageItemHull   -> {
          if(currentItem == null) {
            currentItem = ServerGarageUserItemHull(user, marketItem.id, 0)
            user.items.add(currentItem)
            isNewItem = true

            val price = currentItem.modification.price
            if(user.crystals < price) {
              logger.debug { "Player ${user.username} (${user.id}) tried to buy item: ${marketItem.id} ($price crystals), but does not have enough crystals (user: ${user.crystals} crystals, delta: ${user.crystals - price} crystals)" }
              return
            }
            user.crystals -= price

            logger.debug { "Bought hull ${marketItem.id} ($price crystals)" }
          }
        }

        is ServerGarageItemPaint  -> {
          if(currentItem == null) {
            currentItem = ServerGarageUserItemPaint(user, marketItem.id)
            user.items.add(currentItem)
            isNewItem = true

            val price = currentItem.marketItem.price
            if(user.crystals < price) {
              logger.debug { "Player ${user.username} (${user.id}) tried to buy item: ${marketItem.id} ($price crystals), but does not have enough crystals (user: ${user.crystals} crystals, delta: ${user.crystals - price} crystals)" }
              return
            }
            user.crystals -= price

            logger.debug { "Bought paint ${marketItem.id} ($price crystals)" }
          }
        }

        is ServerGarageItemResistance  -> {
          if(currentItem == null) {
            currentItem = ServerGarageUserItemResistance(user, marketItem.id)
            user.items.add(currentItem)
            isNewItem = true

            val price = currentItem.marketItem.price
            if(user.crystals < price) {
              logger.debug { "Player ${user.username} (${user.id}) tried to buy item: ${marketItem.id} ($price crystals), but does not have enough crystals (user: ${user.crystals} crystals, delta: ${user.crystals - price} crystals)" }
              return
            }
            user.crystals -= price

            logger.debug { "Bought resist ${marketItem.id} ($price crystals)" }
          }
        }

        is ServerGarageItemSupply -> {
          when(marketItem.id) {
            "1000_scores" -> {
              user.score += 1000 * count
              socket.updateScore()

              val price = marketItem.price * count
              if(user.crystals < price) {
                logger.debug { "Player ${user.username} (${user.id}) tried to buy item: ${marketItem.id} ($price crystals), but does not have enough crystals (user: ${user.crystals} crystals, delta: ${user.crystals - price} crystals)" }
                return
              }
              user.crystals -= price

              logger.debug { "Bought ${marketItem.id} (count: $count, ${count * 1000} XP, $price crystals)" }
            }

            else          -> {
              if(currentItem == null) {
                currentItem = ServerGarageUserItemSupply(user, marketItem.id, count)
                user.items.add(currentItem)
                isNewItem = true
              } else {
                val supplyItem = currentItem as ServerGarageUserItemSupply
                supplyItem.count += count

                withContext(Dispatchers.IO) {
                  entityManager
                    .createQuery("UPDATE ServerGarageUserItemSupply SET count = :count WHERE id = :id")
                    .setParameter("count", supplyItem.count)
                    .setParameter("id", supplyItem.id)
                    .executeUpdate()
                }
              }

              val price = currentItem.marketItem.price * count
              if(user.crystals < price) {
                logger.debug { "Player ${user.username} (${user.id}) tried to buy item: ${marketItem.id} ($price crystals), but does not have enough crystals (user: ${user.crystals} crystals, delta: ${user.crystals - price} crystals)" }
                return
              }
              user.crystals -= price

              socket.battlePlayer?.let { battlePlayer ->
                Command(
                  CommandName.SetItemCount,
                  marketItem.id,
                  currentItem.count.toString()
                ).send(battlePlayer)
              }

              logger.debug { "Bought supply ${marketItem.id} (count: $count, $price crystals)" }
            }
          }
        }

        else                      -> {
          logger.warn { "Buying item ${marketItem::class.simpleName} is not implemented" }
        }
      }

      if(isNewItem) {
        if(fromKit && modIndex >= 0 && currentItem is ServerGarageUserItemWithModification)
          currentItem.modificationIndex = modIndex

        entityManager.persist(currentItem)
      }

      if(!isNewItem && currentItem is ServerGarageUserItemWithModification) {
        if(currentItem.modificationIndex < 3) {
          val oldModification = currentItem.modificationIndex
          if (fromKit && modIndex >= 0) currentItem.modificationIndex = modIndex
          else currentItem.modificationIndex++

          entityManager.merge(currentItem)

          val price = currentItem.modification.price
          if(user.crystals < price) {
            logger.debug { "Player ${user.username} (${user.id}) tried to buy item: ${marketItem.id} ($price crystals), but does not have enough crystals (user: ${user.crystals} crystals, delta: ${user.crystals - price} crystals)" }
            return
          }
          user.crystals -= price

          logger.debug { "Upgraded ${marketItem.id} modification: M${oldModification} -> M${currentItem.modificationIndex} ($price crystals)" }
        }
      }

      entityManager.transaction.commit()
      entityManager.entityManagerFactory.cache.evictAll()

      userRepository.updateUser(user)

      Command(
        CommandName.BuyItem,
        BuyItemResponseData(
          itemId = marketItem.id,
          count = if(marketItem is ServerGarageItemSupply) count else 1
        ).toJson()
      ).send(socket)

      socket.updateCrystals()

      tryMountItem(socket, rawItem)
    } catch(error: Exception) {
      entityManager.transaction.rollback()
      throw Exception("Error while processing the purchase", error)
    } finally {
      entityManager.close()
    }
  }

  /*
  SENT    : garage;kitBought;universal_soldier_m0
  SENT    : garage;try_mount_item;railgun_m0
  SENT    : garage;try_mount_item;twins_m0
  SENT    : garage;try_mount_item;flamethrower_m0
  SENT    : garage;try_mount_item;hornet_m0
  RECEIVED: LOBBY;add_crystall;179
  RECEIVED: GARAGE;showCategory;armor
  RECEIVED: GARAGE;select;hornet_m0
  RECEIVED: GARAGE;mount_item;railgun_m0;true
  RECEIVED: GARAGE;mount_item;twins_m0;true
  RECEIVED: GARAGE;mount_item;flamethrower_m0;true
  RECEIVED: GARAGE;mount_item;hornet_m0;true
  */
  @CommandHandler(CommandName.TryBuyKit)
  suspend fun tryBuyKit(socket: UserSocket, rawItem: String) {
    if (socket.user == null) throw Exception("No User")

    val item = rawItem.substringBeforeLast("_")
    val marketItem = marketRegistry.get(item)

    if(marketItem !is ServerGarageItemKit) return

    logger.debug { "Trying to buy kit ${marketItem.id}..." }

    marketItem.kit.items.forEach { kitItem ->
      tryBuyItemInternal(socket, kitItem.id, kitItem.count, true)
    }
  }
}
