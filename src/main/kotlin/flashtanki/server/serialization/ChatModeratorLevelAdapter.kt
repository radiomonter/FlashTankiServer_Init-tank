package flashtanki.server.serialization

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import flashtanki.server.client.ChatModeratorLevel

class ChatModeratorLevelAdapter {
  @ToJson
  fun toJson(level: ChatModeratorLevel): Int = level.key

  @FromJson
  fun fromJson(value: Int) = ChatModeratorLevel.get(value)
}
