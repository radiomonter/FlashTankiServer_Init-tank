package flashtanki.server.news

import flashtanki.server.client.SocketLocale

data class ServerNewsData(
    val image: String,
    val date: String,
    val header: String,
    val id: String,
    val text: String
) {
    companion object {
        fun fromEntity(entity: NewsEntity, locale: SocketLocale?): ServerNewsData {
            val localeKey = locale?.key?.lowercase()?.substring(0, 2) ?: "en"

            val localized = entity.locales.firstOrNull {
                it.locale.lowercase().startsWith(localeKey)
            } ?: entity.locales.firstOrNull()

            return ServerNewsData(
                image = entity.image,
                date = entity.date,
                header = localized?.header ?: "No title",
                id = entity.id,
                text = (localized?.text ?: "No text")
                    .replace("\r\n", "\n") // Flash fix
            )
        }
    }
}

