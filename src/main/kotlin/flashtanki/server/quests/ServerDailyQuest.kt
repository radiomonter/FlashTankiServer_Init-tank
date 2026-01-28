package flashtanki.server.quests

import jakarta.persistence.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import flashtanki.server.HibernateUtils
import flashtanki.server.client.User
import flashtanki.server.extensions.singleOrNullOrThrow
import flashtanki.server.utils.LocalizedString

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Table(
  name = "daily_quests",
  indexes = [
    Index(name = "idx_daily_quests_user", columnList = "user_id")
  ]
)
abstract class ServerDailyQuest(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Int,

  @ManyToOne
  val user: User,
  @Column(nullable = false, name = "questIndex") val index: Int, // INDEX is a reserved word in MariaDB

  @Column(nullable = false) var current: Int,
  @Column(nullable = false) var required: Int,

  @Column(nullable = false) var new: Boolean,
  @Column(nullable = false) var completed: Boolean,

  @OneToMany(targetEntity = ServerDailyQuestReward::class, mappedBy = "id.quest")
  val rewards: MutableList<ServerDailyQuestReward>,
  @Column(nullable = false) var preview: Int
) {
  @get:Transient
  abstract val description: LocalizedString

  suspend fun updateProgress() {
    HibernateUtils.createEntityManager().let { entityManager ->
      entityManager.transaction.begin()

      withContext(Dispatchers.IO) {
        entityManager
          .createQuery("UPDATE ServerDailyQuest SET current = :current, new = :new, completed = :completed WHERE id = :id")
          .setParameter("current", current)
          .setParameter("id", id)
          .setParameter("new", new)
          .setParameter("completed", completed)
          .executeUpdate()
      }

      entityManager.transaction.commit()
      entityManager.close()
    }
  }
}

inline fun <reified T : ServerDailyQuest> User.questOf(predicate: (T) -> Boolean = { true }): T? {
  val quests = dailyQuests.filter { quest -> quest::class == T::class }
  return quests.singleOrNullOrThrow { quest -> predicate(quest as T) } as T?
}
