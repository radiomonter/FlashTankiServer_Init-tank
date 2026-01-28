package flashtanki.server.serialization

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import flashtanki.server.SkyboxSide

class SkyboxSideAdapter {
  @ToJson
  fun toJson(type: SkyboxSide): String = type.key

  @FromJson
  fun fromJson(value: String) = SkyboxSide.get(value)
}
