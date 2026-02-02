package flashtanki.server.news

import jakarta.persistence.EntityManager

public class NewsRepository(
        private val em: EntityManager
) {
    fun getAll(): List<NewsEntity> =
            em.createQuery(
            "SELECT n FROM NewsEntity n LEFT JOIN FETCH n.locales ORDER BY n.date DESC",
    NewsEntity::class.java
        ).resultList
}

