package flashtanki.server.serialization

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import flashtanki.server.ResourceType

class ResourceTypeAdapter {
  @ToJson
  fun toJson(type: ResourceType): Int = type.key

  @FromJson
  fun fromJson(value: Int) = ResourceType.get(value)
}
