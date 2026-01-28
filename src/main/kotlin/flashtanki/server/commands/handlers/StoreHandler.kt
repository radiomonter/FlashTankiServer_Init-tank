package flashtanki.server.commands.handlers

import com.squareup.moshi.Moshi
import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandHandler
import flashtanki.server.commands.CommandName
import flashtanki.server.commands.ICommandHandler
import flashtanki.server.store.*
import flashtanki.server.*
import flashtanki.server.client.*
import flashtanki.server.garage.ServerGarageUserItemLootbox
import flashtanki.server.garage.ServerGarageUserItemSupply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class StoreHandler : ICommandHandler, KoinComponent {
  private val logger = KotlinLogging.logger { }

  private val storeRegistry: IStoreRegistry by inject()
  private val storeItemConverter: IStoreItemConverter by inject()
  private val promoCodeService: IPromoCodeService by inject()
  private val userRepository: IUserRepository by inject()

  private fun checkUser(socket: UserSocket): User {
    return socket.user ?: throw Exception("No User")
  }

  @CommandHandler(CommandName.OpenStore)
  suspend fun openStore(socket: UserSocket) {
    val categories = storeRegistry.categories.values.sortedBy { it.category_id }
    val clientCategories = categories.map(storeItemConverter::toClientCategory)
    val clientItems = categories.flatMap { it.items }.map { item -> storeItemConverter.toClientItem(item, socket.locale ?: throw Exception("No Locale")) }

    Command(
      CommandName.ClientOpenStore,
      OpenStoreWrapperData(
        data = OpenStoreData(
          categories = clientCategories,
          items = clientItems
        ).toJson()
      ).toJson()
    ).send(socket)
  }

  @CommandHandler(CommandName.TryActivatePromocode)
  suspend fun tryActivatePromocode(socket: UserSocket, promocode: String) {
    val user = checkUser(socket)
    val prizes = promoCodeService.getPrizesForPromo(promocode)

    if (prizes != null) {
      prizes.forEach { prize ->
        when (prize.type) {
          "crystal" -> {
            val quantity = prize.quantity
            user.crystals += quantity
            socket.updateCrystals()
            Command(
              CommandName.StorePaymentSuccess,
              quantity.toString(),
              0.toString(),
              0.toString(),
              getLocaleValue(socket.locale)
            ).send(socket)
          }
          "premium" -> {
            val quantity = prize.quantity
            socket.addPremiumAccount(quantity)
            userRepository.updateUser(user)
          }
          "gold" -> {
            val quantity = prize.quantity
            val entityManager = HibernateUtils.createEntityManager()
            try {
              entityManager.transaction.begin()

              val existingItem = user.items.singleOrNull { it.marketItem.id == "gold" }
              if (existingItem != null) {
                val supplyItem = existingItem as ServerGarageUserItemSupply
                supplyItem.count += quantity

                withContext(Dispatchers.IO) {
                  entityManager
                    .createQuery("UPDATE ServerGarageUserItemSupply SET count = :count WHERE id = :id")
                    .setParameter("count", supplyItem.count)
                    .setParameter("id", supplyItem.id)
                    .executeUpdate()
                }

                socket.battlePlayer?.let { battlePlayer ->
                  Command(
                    CommandName.SetItemCount,
                    supplyItem.marketItem.id,
                    supplyItem.count.toString()
                  ).send(battlePlayer)
                }
              } else {
                val newItem = ServerGarageUserItemSupply(user, "gold", quantity)
                user.items.add(newItem)
                entityManager.persist(newItem)
              }

              entityManager.transaction.commit()
            } catch (e: Exception) {
              if (entityManager.transaction.isActive) {
                entityManager.transaction.rollback()
              }
              logger.error { "Error updating user gold: ${e.message}" }
            } finally {
              if (entityManager.isOpen) {
                entityManager.close()
              }
            }
          }
          "supplies" -> {
            val quantity = prize.quantity

              val entityManager = HibernateUtils.createEntityManager()
              try {
                entityManager.transaction.begin()

                val existingItems = listOf("health", "armor", "double_damage", "n2o", "mine")
                for (itemId in existingItems) {
                  val existingItem = user.items.singleOrNull { it.marketItem.id == itemId }
                  if (existingItem != null) {
                    val supplyItem = existingItem as ServerGarageUserItemSupply
                    supplyItem.count += quantity
                    entityManager.merge(supplyItem)

                    withContext(Dispatchers.IO) {
                      entityManager
                        .createQuery("UPDATE ServerGarageUserItemSupply SET count = :count WHERE id = :id")
                        .setParameter("count", supplyItem.count)
                        .setParameter("id", supplyItem.id)
                        .executeUpdate()
                    }

                    socket.battlePlayer?.let { battlePlayer ->
                      Command(
                        CommandName.SetItemCount,
                        supplyItem.marketItem.id,
                        supplyItem.count.toString()
                      ).send(battlePlayer)
                    }
                  } else {
                    val newItem = ServerGarageUserItemSupply(user, itemId, quantity)
                    entityManager.persist(newItem)
                    user.items.add(newItem)
                  }
                }

                entityManager.transaction.commit()
              } catch (e: Exception) {
                if (entityManager.transaction.isActive) {
                  entityManager.transaction.rollback()
                }
                logger.error { "Error updating user supplies: ${e.message}" }
              } finally {
                if (entityManager.isOpen) {
                  entityManager.close()
                }
              }
            }
          "lootbox" -> {
            val quantity = prize.quantity
            val entityManager = HibernateUtils.createEntityManager()
            try {
              entityManager.transaction.begin()

              val existingItem = user.items.singleOrNull { it.marketItem.id == "lootbox" }
              if (existingItem != null) {
                val supplyItem = existingItem as ServerGarageUserItemLootbox
                supplyItem.count += quantity
                entityManager.merge(supplyItem)
              } else {
                val newItem = ServerGarageUserItemLootbox(user, "lootbox", quantity)
                user.items.add(newItem)
                entityManager.persist(newItem)
              }

              entityManager.transaction.commit()
            } catch (e: Exception) {
              entityManager.transaction.rollback()
              logger.error { "Error updating user lootbox: ${e.message}" }
            } finally {
              entityManager.close()
            }

            if(socket.screen == Screen.Garage) {
              Command(CommandName.UnloadGarage).send(socket)

              socket.loadGarageResources()
              socket.initGarage()
            }

            userRepository.updateUser(user)
          }
          else -> {
          logger.error { "Issue error: $prizes" }
          }
        }
      }

      Command(CommandName.ActivatePromocodeSuccessfully).send(socket)
      promoCodeService.removePromoCode(promocode)
      logger.debug { "Player ${user.username} activated promocode $promocode" }
    } else {
      Command(CommandName.ActivatePromocodeFailed).send(socket)
      logger.debug { "Player ${user.username} failed to activate promocode $promocode" }
    }
  }
  
  @CommandHandler(CommandName.StoreTryBuyItem)
  suspend fun storeTryBuyItem(socket: UserSocket, itemId: String, paymentMethodId: String) {
    val user = checkUser(socket)
    val paymentMethod = StorePaymentMethod.get(paymentMethodId)
      ?: throw IllegalArgumentException("Unknown payment method: $paymentMethodId")

    val item = storeRegistry.categories.values
      .flatMap { it.items }
      .singleOrNull { it.id == itemId } ?: run {
      logger.error { "Unknown item ID: $itemId" }
      return
    }

    Command(
      CommandName.StorePaymentSuccess,
      (item.crystals?.base ?: 0).toString(),
      (item.crystals?.bonus ?: 0).toString(),
      0.toString(),
      getLocaleValue(socket.locale)
    ).send(socket)

    item.crystals?.let {
      user.crystals += it.base + it.bonus
      socket.updateCrystals()
      logger.debug { "Player ${user.username} added crystals (${it.base} + ${it.bonus})" }
    }

    item.premium?.let {
      socket.addPremiumAccount(it.base)
      userRepository.updateUser(user)
      logger.debug { "Player ${user.username} added premium days (${it.base})" }
    }

    if (itemId == "clan_license") {
      Command(CommandName.AddClanLicense).send(socket)
      logger.debug { "Player ${user.username} added clan_license" }
    }

    if (itemId.startsWith("lootbox_pack_")) {
      val count = when (itemId) {
        "lootbox_pack_1" -> 1
        "lootbox_pack_2" -> 3
        "lootbox_pack_3" -> 10
        "lootbox_pack_4" -> 25
        "lootbox_pack_5" -> 50
        "lootbox_pack_6" -> 100
        else -> {
          logger.error { "Unknown lootbox pack ID: $itemId" }
          return
        }
      }

      val entityManager = HibernateUtils.createEntityManager()
      try {
        entityManager.transaction.begin()

        val existingItem = user.items.singleOrNull { it.marketItem.id == "lootbox" }
        if (existingItem != null) {
          val supplyItem = existingItem as ServerGarageUserItemLootbox
          supplyItem.count += count
          entityManager.merge(supplyItem)
        } else {
          val newItem = ServerGarageUserItemLootbox(user, "lootbox", count)
          user.items.add(newItem)
          entityManager.persist(newItem)
        }

        entityManager.transaction.commit()
      } catch (e: Exception) {
        entityManager.transaction.rollback()
        logger.error { "Error updating user lootbox: ${e.message}" }
      } finally {
        entityManager.close()
      }

      if(socket.screen == Screen.Garage) {
        Command(CommandName.UnloadGarage).send(socket)

        socket.loadGarageResources()
        socket.initGarage()
      }

      userRepository.updateUser(user)
    } else if (itemId.startsWith("gold_boxes_pack_")) {
      val goldCount = when (itemId) {
        "gold_boxes_pack_1" -> 1
        "gold_boxes_pack_2" -> 10
        "gold_boxes_pack_3" -> 50
        else -> {
          logger.error { "Unknown goldboxes pack ID: $itemId" }
          return
        }
      }

      val entityManager = HibernateUtils.createEntityManager()
      try {
        entityManager.transaction.begin()

        val existingItem = user.items.singleOrNull { it.marketItem.id == "gold" }
        if (existingItem != null) {
          val supplyItem = existingItem as ServerGarageUserItemSupply
          supplyItem.count += goldCount

          withContext(Dispatchers.IO) {
            entityManager
              .createQuery("UPDATE ServerGarageUserItemSupply SET count = :count WHERE id = :id")
              .setParameter("count", supplyItem.count)
              .setParameter("id", supplyItem.id)
              .executeUpdate()
          }

          socket.battlePlayer?.let { battlePlayer ->
            Command(
              CommandName.SetItemCount,
              supplyItem.marketItem.id,
              supplyItem.count.toString()
            ).send(battlePlayer)
          }
        } else {
          val newItem = ServerGarageUserItemSupply(user, "gold", goldCount)
          user.items.add(newItem)
          entityManager.persist(newItem)
        }

        entityManager.transaction.commit()
      } catch (e: Exception) {
        if (entityManager.transaction.isActive) {
          entityManager.transaction.rollback()
        }
        logger.error { "Error updating user gold: ${e.message}" }
      } finally {
        if (entityManager.isOpen) {
          entityManager.close()
        }
      }
    } else {
      logger.error { "Unknown pack ID: $itemId" }
    }

    logger.debug { "Player ${user.username} bought ${item.id} with payment method: $paymentMethod" }
  }


  private fun getLocaleValue(locale: SocketLocale?): String {
    return when (locale) {
      SocketLocale.Russian -> "124221"
      SocketLocale.English -> "123444"
      SocketLocale.Portuguese -> "143111"
      else -> throw IllegalArgumentException("Unsupported or null locale: $locale")
    }
  }
}
