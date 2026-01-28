package flashtanki.server.serialization

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import flashtanki.server.garage.GarageItemType

class GarageItemTypeAdapter {
  @ToJson
  fun toJson(type: GarageItemType): Int = type.key

  @FromJson
  fun fromJson(value: Int) = GarageItemType.get(value)
}
