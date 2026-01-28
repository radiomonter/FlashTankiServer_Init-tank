package flashtanki.server.garage

enum class GarageItemType(val key: Int, val categoryKey: String) {
  Weapon(1, "weapon"),
  Hull(2, "armor"),
  Paint(3, "paint"),
  Supply(4, "inventory"),
  Subscription(5, "special"),
  Kit(6, "kit"),
  Present(7, "special"),
  GivenPresents(9, "given_presents"),
  Resistance(10, "resistance"),
  Lootboxes(11, "special");

  companion object {
    private val map = values().associateBy(GarageItemType::key)

    fun get(key: Int) = map[key]
  }
}
