package flashtanki.server

import jakarta.persistence.EntityManager
import jakarta.persistence.Persistence

object HibernateUtils {
  private val entityManagerFactory = Persistence.createEntityManagerFactory("flashtanki.server")

  fun createEntityManager(): EntityManager = entityManagerFactory.createEntityManager()
  fun close() = entityManagerFactory.close()
}
