package flashtanki.server.store

import com.squareup.moshi.Json
import flashtanki.server.utils.LocalizedString

data class ServerStoreCategory(
  @Json val category_id: Int,
  @Json val title: LocalizedString,
  @Json val description: LocalizedString
) {
  lateinit var id: String
  lateinit var items: MutableList<ServerStoreItem>
}

data class ServerStoreItem(
  val price: Map<StoreCurrency, Double>,

  // One of the following
  val crystals: CrystalsPackage? = null,
  val premium: PremiumPackage? = null,
  val promocode: PromocodePackage? = null,
  val clan_license: ClanLicense? = null,
  val lootboxs: LootBoxs? = null,
  val goldboxes: GoldBoxes? = null

) {
  lateinit var id: String
  lateinit var category: ServerStoreCategory

  data class CrystalsPackage(
    @Json val base: Int,
    @Json val bonus: Int = 0
  )

  data class PremiumPackage(
    @Json val base: Int
  )

  data class PromocodePackage(
    @Json val base: Int
  )
  data class ClanLicense(
    @Json val base: Int
  )
  data class LootBoxs(
    @Json val base: Int
  )
  data class GoldBoxes(
    @Json val base: Int
  )
}
