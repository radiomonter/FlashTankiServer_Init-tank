package flashtanki.server.garage.lootbox

import com.squareup.moshi.Json
import java.math.BigInteger
import org.koin.core.component.KoinComponent
import kotlin.random.Random
import flashtanki.server.HibernateUtils
import flashtanki.server.client.*
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandName
import flashtanki.server.garage.*
import flashtanki.server.utils.LocalizedString
import jakarta.persistence.EntityNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.inject

data class Prize(
    val name: LocalizedString,
    val rarity: String,
    val preview: Int,
    val id: String
)

class LootboxPrizeService : KoinComponent {
    private val prizes = listOf(
        Prize(name = LocalizedString(mapOf(SocketLocale.English to "3500 crystals pack", SocketLocale.Russian to "Пакет 3500 кристаллов")), rarity = "COMMON", preview = 60285, id = "crystals_3500"),
        Prize(name = LocalizedString(mapOf(SocketLocale.English to "125 Double Damage set", SocketLocale.Russian to "Набор 125 двойного урона")), rarity = "COMMON", preview = 71623, id = "doubledamage_125"),
        Prize(name = LocalizedString(mapOf(SocketLocale.English to "125 Dobule Armor set", SocketLocale.Russian to "Набор 125 повышенной защиты")), rarity = "COMMON", preview = 824173, id = "armor_125"),
        Prize(name = LocalizedString(mapOf(SocketLocale.English to "125 Speed Boosts set", SocketLocale.Russian to "Набор 125 ускорений")), rarity = "COMMON", preview = 153187, id = "n2o_125"),
        Prize(name = LocalizedString(mapOf(SocketLocale.English to "125 Mines set", SocketLocale.Russian to "Набор 125 мин")), rarity = "COMMON", preview = 504646, id = "mine_125"),
        Prize(name = LocalizedString(mapOf(SocketLocale.English to "10.000 crystals pack", SocketLocale.Russian to "Пакет 10 000 кристаллов")), rarity = "UNCOMMON", preview = 60286, id = "crystals_10000"),
        Prize(name = LocalizedString(mapOf(SocketLocale.English to "125 Repair Kits set", SocketLocale.Russian to "Набор 125 ремкомплектов")), rarity = "UNCOMMON", preview = 716566, id = "health_125"),
        Prize(name = LocalizedString(mapOf(SocketLocale.English to "100 of All Supplies pack", SocketLocale.Russian to "Комплект 100 всех припасов")), rarity = "UNCOMMON", preview = 60287, id = "allsupplies_100"),
        Prize(name = LocalizedString(mapOf(SocketLocale.English to "Moonwalker paint", SocketLocale.Russian to "Краска Луноход")), rarity = "EPIC", preview = 342553, id = "paint_moonwalker"),
        Prize(name = LocalizedString(mapOf(SocketLocale.English to "5 Gold Boxes set", SocketLocale.Russian to "Набор 5 золотых ящиков")), rarity = "UNCOMMON", preview = 60289, id = "goldboxes_5"),
        Prize(name = LocalizedString(mapOf(SocketLocale.English to "25.000 crystals pack", SocketLocale.Russian to "Пакет 25 000 кристаллов")), rarity = "RARE", preview = 60286, id = "crystals_25000"),
        Prize(name = LocalizedString(mapOf(SocketLocale.English to "3 days of Premium", SocketLocale.Russian to "3 дня премиум аккаунта")), rarity = "UNCOMMON", preview = 60288, id = "premiumdays_3"),
        Prize(name = LocalizedString(mapOf(SocketLocale.English to "10 Gold Boxes set", SocketLocale.Russian to "Набор 10 золотых ящиков")), rarity = "RARE", preview = 60289, id = "goldboxes_10"),
        Prize(name = LocalizedString(mapOf(SocketLocale.English to "250 of All Supplies pack", SocketLocale.Russian to "Комплект 250 всех припасов")), rarity = "RARE", preview = 60287, id = "allsupplies_250"),
        Prize(name = LocalizedString(mapOf(SocketLocale.English to "100.000 crystals pack", SocketLocale.Russian to "Пакет 100 000 кристаллов")), rarity = "EPIC", preview = 60286, id = "crystals_100000"),
        Prize(name = LocalizedString(mapOf(SocketLocale.English to "10 days of Premium", SocketLocale.Russian to "10 дней премиум аккаунта")), rarity = "RARE", preview = 60288, id = "premiumdays_10"),
        Prize(name = LocalizedString(mapOf(SocketLocale.English to "300.000 crystals pack", SocketLocale.Russian to "Пакет 300 000 кристаллов")), rarity = "LEGENDARY", preview = 60286, id = "crystals_300000"),
        Prize(name = LocalizedString(mapOf(SocketLocale.English to "Thunder XT", SocketLocale.Russian to "Гром ХТ")), rarity = "EXOTIC", preview = 60290, id = "thunder_xt"),
        Prize(name = LocalizedString(mapOf(SocketLocale.English to "1.000.000 crystals pack", SocketLocale.Russian to "Пакет 1 000 000 кристаллов")), rarity = "EXOTIC", preview = 60286, id = "crystals_1000000")
    )

    private val probabilities = mapOf(
        "COMMON" to 0.50,
        "UNCOMMON" to 0.34,
        "RARE" to 0.10,
        "EPIC" to 0.05,
        "LEGENDARY" to 0.02,
        "EXOTIC" to 0.01
    )

    private val categoryOrder = mapOf(
        "COMMON" to 1,
        "UNCOMMON" to 2,
        "RARE" to 3,
        "EPIC" to 4,
        "LEGENDARY" to 5,
        "EXOTIC" to 6
    )

    private val prizeOrder = mapOf(
        "crystals_3500" to 1,
        "doubledamage_125" to 2,
        "armor_125" to 3,
        "n2o_125" to 4,
        "mine_125" to 5,
        "crystals_10000" to 7,
        "health_125" to 8,
        "allsupplies_100" to 9,
        "goldboxes_5" to 10,
        "premiumdays_3" to 11,
        "crystals_25000" to 12,
        "goldboxes_10" to 13,
        "allsupplies_250" to 14,
        "premiumdays_10" to 15,
        "paint_moonwalker" to 16,
        "crystals_100000" to 17,
        "paint_legendary" to 18,
        "crystals_300000" to 19,
        "thunder_xt" to 20,
        "crystals_1000000" to 21
    )

    private val userRepository: IUserRepository by inject()

    private val excludedPrizeRegex = Regex("^paint_.+|.+_xt$")

    suspend fun getRandomReward(socket: UserSocket, count: Int): List<LootboxPrize> {
        require(count <= prizes.size) { "Requested count exceeds available elements." }

        val user = socket.user ?: throw Exception("No User")
        val selectedPrizes = mutableListOf<Prize>()
        val prizeCounts = mutableMapOf<String, Int>()
        val random = Random.Default
        var lastSelectedPrize: Prize? = null
        val locale = socket.locale ?: SocketLocale.English
        val entityManager = HibernateUtils.createEntityManager()
        try {
            while (selectedPrizes.size < count) {
                val isDuplicate = random.nextDouble() < 0.10
                val isTriplicate = random.nextDouble() < 0.05
                val filteredPrizes = if ((isDuplicate || isTriplicate) && selectedPrizes.isNotEmpty()) {
                    selectedPrizes.filter {
                        (prizeCounts[it.id] ?: 0 < 3 || (isTriplicate && prizeCounts[it.id] ?: 0 < 3)) &&
                                !excludedPrizeRegex.containsMatchIn(it.id) &&
                                it != lastSelectedPrize
                    }
                } else {
                    val rarity = selectRarity()
                    prizes.filter {
                        it.rarity == rarity && (prizeCounts[it.id] ?: 0) < 3 &&
                                !selectedPrizes.any { selectedPrize -> selectedPrize.id == it.id } &&
                                !excludedPrizeRegex.containsMatchIn(it.id)
                    }
                }

                if (filteredPrizes.isNotEmpty()) {
                    val selectedPrize = filteredPrizes[random.nextInt(filteredPrizes.size)]
                    selectedPrizes.add(selectedPrize)
                    prizeCounts[selectedPrize.id] = (prizeCounts[selectedPrize.id] ?: 0) + 1
                    lastSelectedPrize = selectedPrize
                }
            }

            for (prize in selectedPrizes) {
                val id = prize.id
                val data = id.split("_")
                entityManager.transaction.begin()
                try {
                    if (data[0].contains("crystals")) {
                        val amount: BigInteger = data[1].toBigInteger()
                        user.crystals += amount.toInt()
                        socket.updateCrystals()
                        userRepository.updateUser(user)
                    } else if (data[0].contains("premiumdays")) {
                        val amount: BigInteger = data[1].toBigInteger()
                        socket.addPremiumAccount(amount.toInt())
                    } else if (data[0].contains("health") || data[0].contains("armor") || data[0].contains("doubledamage") || data[0].contains("n2o") || data[0].contains("mine") || data[0].contains("goldboxes")) {
                        val itemId = data[0].replace("double", "double_").replace("goldboxes", "gold")
                        var currentItem = user.items.singleOrNull { userItem -> userItem.marketItem.id == itemId }

                        val count = data[1].toInt()
                        if (currentItem == null) {
                            currentItem = ServerGarageUserItemSupply(user, itemId, count)
                            user.items.add(currentItem)
                            entityManager.persist(currentItem)
                            userRepository.updateUser(user)
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

                            socket.battlePlayer?.let { battlePlayer ->
                                Command(
                                    CommandName.SetItemCount,
                                    supplyItem.marketItem.id,
                                    supplyItem.count.toString()
                                ).send(battlePlayer)
                            }
                        }
                    } else if (data[0].contains("paint")) {
                        val itemId = data[1]
                        var currentItem = user.items.singleOrNull { userItem -> userItem.marketItem.id == itemId }
                        if (currentItem == null) {
                            currentItem = ServerGarageUserItemPaint(user, itemId)
                            user.items.add(currentItem)
                            entityManager.persist(currentItem)
                            userRepository.updateUser(user)
                        }
                    } else if (data[0].contains("allsupplies")) {
                        val supplies = listOf("health", "armor", "double_damage", "n2o", "mine")
                        val count = data[1].toInt()
                        for (itemId in supplies) {
                            var currentItem =
                                user.items.singleOrNull { userItem -> userItem.marketItem.id == itemId }
                            if (currentItem == null) {
                                currentItem = ServerGarageUserItemSupply(user, itemId, count)
                                user.items.add(currentItem)
                                entityManager.persist(currentItem)
                                userRepository.updateUser(user)
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

                                socket.battlePlayer?.let { battlePlayer ->
                                    Command(
                                        CommandName.SetItemCount,
                                        supplyItem.marketItem.id,
                                        supplyItem.count.toString()
                                    ).send(battlePlayer)
                                }
                            }
                        }
                    }
                } catch (e: EntityNotFoundException) {
                    println("EntityNotFoundException: ${e.message}")
                } catch (e: Exception) {
                    println("Exception: ${e.message}")
                }
                entityManager.transaction.commit()
            }

            selectedPrizes.sortWith(compareBy { prizeOrder[it.id] ?: Int.MAX_VALUE })
        } catch (e: Exception) {
            entityManager.transaction.rollback()
            throw e
        } finally {
            entityManager.entityManagerFactory.cache.evictAll()
            entityManager.close()
        }
        if (socket.screen == Screen.Garage) {
            Command(CommandName.UnloadGarage).send(socket)

            socket.loadGarageResources()
            socket.initGarage()
        }
        return selectedPrizes.map { prize ->
            LootboxPrize(
                category = prize.rarity,
                count = 1,
                preview = prize.preview,
                name = prize.name.get(locale)
            )
        }.sortedWith(compareBy { categoryOrder[it.category] })
    }

    private fun selectRarity(): String {
        val rand = Random.nextDouble()
        var cumulativeProbability = 0.0
        for ((rarity, probability) in probabilities) {
            cumulativeProbability += probability
            if (rand < cumulativeProbability) {
                return rarity
            }
        }
        return probabilities.keys.last()
    }
}

data class LootboxPrize(
    @Json(name = "category") val category: String,
    @Json(name = "count") val count: Int,
    @Json(name = "preview") val preview: Int,
    @Json(name = "name") val name: String
)
