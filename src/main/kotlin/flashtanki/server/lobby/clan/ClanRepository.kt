package flashtanki.server.lobby.clan

import jakarta.persistence.*
import mu.KotlinLogging
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import flashtanki.server.HibernateUtils

interface IClanRepository {
   suspend fun createClan(tag: String, name: String, creatorId: String): Clan?
   suspend fun getClanById(id: Int): Clan?
   suspend fun getClanByTag(tag: String): Clan?
   suspend fun getClanByName(name: String): Clan?
   suspend fun getClanByCreatorId(creatorId: String): Clan?
   suspend fun updateClan(clan: Clan): Unit
}

class ClanRepository : IClanRepository {

    private val logger = KotlinLogging.logger {}

    private val entityManager: EntityManager
        get() = HibernateUtils.createEntityManager()

    override suspend fun getClanById(id: Int): Clan? = withContext(Dispatchers.IO) {
        val entityManager = entityManager
        entityManager.find(Clan::class.java, id)
    }

    override suspend fun getClanByName(name: String): Clan? = withContext(Dispatchers.IO) {
        val entityManager = entityManager
        entityManager
            .createQuery("FROM Clan WHERE name = :name", Clan::class.java)
            .setParameter("name", name)
            .resultList
            .singleOrNull()
    }

    override suspend fun getClanByCreatorId(creatorId: String): Clan? = withContext(Dispatchers.IO) {
        val entityManager = entityManager
        entityManager
            .createQuery("FROM Clan WHERE creatorId = :creatorId", Clan::class.java)
            .setParameter("creatorId", creatorId)
            .resultList
            .singleOrNull()
    }

    override suspend fun getClanByTag(tag: String): Clan? = withContext(Dispatchers.IO) {
        val entityManager = entityManager
        entityManager
            .createQuery("FROM Clan WHERE tag = :tag", Clan::class.java)
            .setParameter("tag", tag)
            .resultList
            .singleOrNull()
    }

    override suspend fun createClan(tag: String, name: String, creatorId: String): Clan? = withContext(Dispatchers.IO) {
        val entityManager = entityManager
        getClanByName(name)?.let { return@withContext null }
        getClanByCreatorId(creatorId)?.let { return@withContext null }
        getClanByTag(tag)?.let { return@withContext null }

        entityManager.transaction.begin()

        val clan = Clan(tag = tag, name = name, description = "", creatorId = creatorId)

        entityManager.persist(clan)

        entityManager.transaction.commit()

        logger.debug { "Created clan: ${clan.tag} + ${clan.name}" }

        clan
    }

    override suspend fun updateClan(clan: Clan): Unit = withContext(Dispatchers.IO) {
        val entityManager = entityManager
        logger.debug { "Updating clan \"${clan.tag}\" (${clan.name})..." }

        entityManager.transaction.begin()
        entityManager.merge(clan)
        entityManager.transaction.commit()
    }
}

@Entity
@Table(
    name = "clans",
    indexes = [
        Index(name = "idx_clans_tag", columnList = "tag"),
        Index(name = "idx_clans_name", columnList = "name")
    ]
)
class Clan(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,
    @Column(nullable = false, unique = true, length = 5) var tag: String,
    @Column(nullable = false, unique = true, length = 64) var name: String,
    @Column(nullable = false, unique = true, length = 64) var description: String,
    @Column(nullable = false, unique = true, length = 64) var creatorId: String
) {

}