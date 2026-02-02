package flashtanki.server.news

import flashtanki.server.client.SocketLocale

class NewsLoader(
    private val newsRepository: NewsRepository
) {
    fun loadNews(locale: SocketLocale?): List<ServerNewsData> {
        val entities = newsRepository.getAll()

        return entities.map { entity ->
            ServerNewsData.fromEntity(entity, locale)
        }
    }
}


