package flashtanki.server.store

enum class StoreCurrency(val key: String, val displayName: String) {
  RUB("rub", "RUB"),
  USD("usd", "USD");

  companion object {
    private val map: Map<String, StoreCurrency> = values().associateBy(StoreCurrency::key)

    fun get(key: String): StoreCurrency? = map[key]
  }
}

