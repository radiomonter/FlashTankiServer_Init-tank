package flashtanki.server.client

import kotlin.collections.Map
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import jakarta.persistence.*
import kotlinx.coroutines.Dispatchers
import kotlin.random.Random
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.security.MessageDigest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import mu.KotlinLogging
import org.hibernate.annotations.Parent
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import flashtanki.server.BonusType
import flashtanki.server.battles.*
import flashtanki.server.HibernateUtils
import flashtanki.server.garage.*
import flashtanki.server.quests.*
import flashtanki.server.serialization.database.BitfieldConverter

@Embeddable
data class UserEquipment(
  @Column(name = "equipment_hull", nullable = false) var hullId: String,
  @Column(name = "equipment_weapon", nullable = false) var weaponId: String,
  @Column(name = "equipment_paint", nullable = false) var paintId: String,
  @Column(name = "equipment_resistance", nullable = false) var resistanceId: String
) {
  @Suppress("JpaAttributeTypeInspection")
  @Parent
  lateinit var user: User // IntelliJ IDEA still shows error for this line.

  @get:Transient
  var hull: ServerGarageUserItemHull
    get() = user.items.single { item -> item.id.itemName == hullId } as ServerGarageUserItemHull
    set(value) {
      hullId = value.id.itemName
    }

  @get:Transient
  var weapon: ServerGarageUserItemWeapon
    get() = user.items.single { item -> item.id.itemName == weaponId } as ServerGarageUserItemWeapon
    set(value) {
      weaponId = value.id.itemName
    }

  @get:Transient
  var paint: ServerGarageUserItemPaint
    get() = (user.items.singleOrNull { it.id.itemName == paintId } ?: user.items.single { it.id.itemName == "green" }) as ServerGarageUserItemPaint
    set(value) {
      paintId = value.id.itemName
    }

  @get:Transient
  var resistance: ServerGarageUserItemResistance
    get() = user.items.single { it -> it.id.itemName == resistanceId } as ServerGarageUserItemResistance
    set(value) {
      resistanceId = value.id.itemName
    }
}

interface IUserRepository {
  suspend fun md5(input: String): String
  suspend fun getUser(id: Int): User?
  suspend fun getUser(username: String): User?
  suspend fun getUserByHash(hash: String): User?
  suspend fun getUserCount(): Long
  suspend fun createUser(username: String, password: String, snId: String): User?
  suspend fun updateUser(user: User)
}

class UserRepository : IUserRepository {
  private val logger = KotlinLogging.logger {}

  private val _entityManagers = ThreadLocal<EntityManager>()

  private val entityManager: EntityManager
    get() = HibernateUtils.createEntityManager()

  override suspend fun md5(input: String): String {
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(input.toByteArray())
    val bigInt = BigInteger(1, digest)
    var hashText = bigInt.toString(16)
    while (hashText.length < 32) {
      hashText = "0$hashText"
    }
    return hashText
  }

  override suspend fun getUser(id: Int): User? = withContext(Dispatchers.IO) {
    val entityManager = entityManager
    entityManager.find(User::class.java, id)
  }

  override suspend fun getUser(username: String): User? = withContext(Dispatchers.IO) {
    val entityManager = entityManager
    entityManager
      .createQuery("FROM User WHERE username = :username", User::class.java)
      .setParameter("username", username)
      .resultList
      .singleOrNull()
  }

  override suspend fun getUserByHash(hash: String): User? = withContext(Dispatchers.IO) {
    val entityManager = entityManager
    entityManager
            .createQuery("FROM User WHERE hash = :hash", User::class.java)
            .setParameter("hash", hash)
            .resultList
            .singleOrNull()
  }

  override suspend fun getUserCount(): Long = withContext(Dispatchers.IO) {
    val entityManager = entityManager
    entityManager
      .createQuery("SELECT COUNT(1) FROM User", Long::class.java)
      .singleResult
  }

  override suspend fun createUser(username: String, password: String, snId: String): User? = withContext(Dispatchers.IO) {
    val entityManager = entityManager
    getUser(username)?.let { return@withContext null }

    entityManager.transaction.begin()

    // TODO(Assasans): Testing only
    val user = User(
      id = 0,
      username = username,
      password = password,
      score = 0,
      crystals = 500,
      permissions = Permissions.Superuser.toBitfield(),
      chatModeratorLevel = 0,
      premium = 0,
      items = mutableListOf(),
      dailyQuests = mutableListOf(),
      hash = md5(username + ":" + password),
      snId = snId,
      currentQuestLevel = 0,
      currentQuestStreak = 0,
      canSkipQuestForFree = false
    )
    user.items += listOf(
      ServerGarageUserItemWeapon(user, "smoky", modificationIndex = 0),
      ServerGarageUserItemHull(user, "hunter", modificationIndex = 0),
      ServerGarageUserItemResistance(user, "zero"),
      ServerGarageUserItemPaint(user, "green"),
      ServerGarageUserItemPaint(user, "holiday")
    )
    user.equipment = UserEquipment(
      hullId = "hunter",
      weaponId = "smoky",
      paintId = "green",
      resistanceId = "zero"
    )
    user.equipment.user = user

    entityManager.persist(user)
    user.items.forEach { item -> entityManager.persist(item) }

    fun addQuest(index: Int, type: KClass<out ServerDailyQuest>, args: Map<String, Any?> = emptyMap()) {
      fun getParameter(name: String) = type.primaryConstructor!!.parameters.single { it.name == name }

      val quest = type.primaryConstructor!!.callBy(mapOf(
        getParameter("id") to 0,
        getParameter("user") to user,
        getParameter("questIndex") to index,
        getParameter("current") to 0,
        getParameter("required") to 2,
        getParameter("new") to true,
        getParameter("completed") to false,
        getParameter("rewards") to mutableListOf<ServerDailyQuestReward>()
      ) + args.mapKeys { (name) -> getParameter(name) })
        quest.rewards += listOf(
                ServerDailyQuestReward(quest, 0, type = ServerDailyRewardType.Crystals, count = Random.nextInt(650, 1650)),
                ServerDailyQuestReward(quest, 1, type = ServerDailyRewardType.Premium, count = Random.nextInt(1, 3))
        )

      entityManager.persist(quest)
      quest.rewards.forEach { reward -> entityManager.persist(reward) }

      user.dailyQuests.add(quest)
    }

    addQuest(0, DeliverFlagQuest::class)
    addQuest(1, KillEnemyQuest::class, mapOf("mode" to BattleMode.Deathmatch))
    addQuest(2, TakeBonusQuest::class, mapOf("bonus" to BonusType.Gold))

    entityManager.transaction.commit()

    logger.debug { "Created user: ${user.username}" }
    logger.debug { "Permissions: ${user.permissions.values().joinToString(", ")}" }

    user
  }

  override suspend fun updateUser(user: User): Unit = withContext(Dispatchers.IO) {
    val entityManager = entityManager
    logger.debug { "Updating user \"${user.username}\" (${user.id})..." }

    entityManager.transaction.begin()
    entityManager.merge(user)
    entityManager.transaction.commit()
  }
}

@Entity
@Table(
  name = "users",
  indexes = [
    Index(name = "idx_users_username", columnList = "username"),
    Index(name = "idx_users_hash", columnList = "hash"),
    Index(name = "idx_users_snId", columnList = "snId")
  ]
)
class User(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Int = 0,

  @Column(nullable = false, unique = true, length = 64) var username: String,
  @Column(nullable = false) var password: String,
  score: Int,
  @Column(nullable = false) var crystals: Int,

  @Convert(converter = BitfieldConverter::class)
  @Column(nullable = false) var permissions: Bitfield<Permissions>,
  @Column(nullable = false) var chatModeratorLevel: Int = 0,
  @Column(nullable = false) var premium: Int = 0,
  @OneToMany(targetEntity = ServerGarageUserItem::class, mappedBy = "id.user")
  val items: MutableList<ServerGarageUserItem>,

  @OneToMany(targetEntity = ServerDailyQuest::class, mappedBy = "user")
  val dailyQuests: MutableList<ServerDailyQuest>,

  @Column(nullable = false, unique = true, length = 64) var hash: String,

  @Column(nullable = false, unique = true, length = 64) var snId: String,

  @Column(nullable = false) var   currentQuestLevel: Int,

  @Column(nullable = false) var currentQuestStreak: Int,

  @Column(nullable = false) var canSkipQuestForFree: Boolean,

  private var mutedUntilMilliseconds: Long? = null,
  private var bannedUntilMilliseconds: Long? = null,
) : KoinComponent {
  @Transient
  protected final var userSubscriptionManager: IUserSubscriptionManager = get()
    private set

  @AttributeOverride(name = "hullId", column = Column(name = "equipment_hull_id"))
  @AttributeOverride(name = "weaponId", column = Column(name = "equipment_weapon_id"))
  @AttributeOverride(name = "paintId", column = Column(name = "equipment_paint_id"))
  @AttributeOverride(name = "resistanceId", column = Column(name = "equipment_resistance_id"))
  @Embedded lateinit var equipment: UserEquipment

  @Transient
  var muteReason: String? = null
    set

  @Transient
  var banReason: String? = null
    set

  @get:Transient final var mutedUntil: Instant?
    get() {
      return when(mutedUntilMilliseconds) {
        null -> null
        -1L  -> Instant.DISTANT_FUTURE
        else -> {
          if (mutedUntilMilliseconds!! != -1L && mutedUntilMilliseconds!! < Clock.System.now().toEpochMilliseconds()) {
            unmute()
            return null
          }

          Instant.fromEpochMilliseconds(mutedUntilMilliseconds!!)
        }
      }
    }
    private set(value) {
      mutedUntilMilliseconds = when(value) {
        null -> null
        Instant.DISTANT_FUTURE -> -1
        else -> value.toEpochMilliseconds()
      }
    }

  @get:Transient final var bannedUntil: Instant?
    get() {
      return when(bannedUntilMilliseconds) {
        null -> null
        -1L  -> Instant.DISTANT_FUTURE
        else -> {
          if (bannedUntilMilliseconds!! != -1L && bannedUntilMilliseconds!! < Clock.System.now().toEpochMilliseconds()) {
            unban()
            return null
          }

          Instant.fromEpochMilliseconds(bannedUntilMilliseconds!!)
        }
      }
    }
    private set(value) {
      bannedUntilMilliseconds = when(value) {
        null -> null
        Instant.DISTANT_FUTURE -> -1
        else -> value.toEpochMilliseconds()
      }
    }

  @Column(nullable = false)
  public var score: Int = score
    set(value) {
      field = value
      userSubscriptionManager.getOrNull(id)?.let { it.rank.value = rank }
    }

  @get:Transient val rank: UserRank
    get() {
      var rank = UserRank.Recruit
      var nextRank: UserRank = rank.nextRank ?: return rank
      while(score >= nextRank.score) {
        rank = nextRank
        nextRank = rank.nextRank ?: return rank
      }
      return rank
    }

  @get:Transient val currentRankScore: Int
    get() {
      val nextRank = rank.nextRank ?: return score
      return nextRank.score - score
    }

  @get:Transient var chatModerator: ChatModeratorLevel
    get() = ChatModeratorLevel.get(chatModeratorLevel) ?: ChatModeratorLevel.None
    set(value) {
      chatModeratorLevel = value.key
    }

  // JPA does not initialize transient fields
  @PostLoad
  final fun postLoad() {
    userSubscriptionManager = get() ?: throw IllegalStateException("UserSubscriptionManager not found.")
  }

  fun hasPremium() : Boolean {
    return (premium > 0.1)
  }

  fun mute(until: Instant, reason: String? = null) {
    muteReason = reason
    mutedUntil = until
  }

  fun setBanInfo(until: Instant, reason: String? = null) {
    banReason = reason
    bannedUntil = until
  }

  fun unmute() {
    muteReason = null
    mutedUntil = null
  }

  fun unban() {
    banReason = null
    bannedUntil = null
  }
}
