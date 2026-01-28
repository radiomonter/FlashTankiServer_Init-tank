package flashtanki.server.store

import flashtanki.server.utils.toClientLocalizedString
import flashtanki.server.client.SocketLocale

interface IStoreItemConverter {
  fun toClientCategory(category: ServerStoreCategory): StoreCategory
  fun toClientItem(item: ServerStoreItem, locale: SocketLocale?): StoreItem
}

class StoreItemConverter : IStoreItemConverter {
  override fun toClientCategory(category: ServerStoreCategory): StoreCategory {
    return StoreCategory(
      id = category.category_id.toString(),
      category_id = category.id,
      header_text = category.title.localized.toClientLocalizedString(),
      description = category.description.localized.toClientLocalizedString()
    )
  }

  override fun toClientItem(item: ServerStoreItem, locale: SocketLocale?): StoreItem {
    if(item.crystals == null && item.premium == null && item.promocode == null && item.clan_license == null && item.lootboxs == null && item.goldboxes == null) throw IllegalStateException("Item ${item.id} is neither a crystal nor a premium package")
    if(item.crystals != null && item.premium != null && item.promocode != null && item.clan_license != null && item.lootboxs != null && item.goldboxes != null) throw IllegalStateException("Item ${item.id} cannot be both a crystal and a premium package")

    return StoreItem(
      category_id = item.category.id,
      item_id = item.id,
      properties = StoreItemProperties(
        price = if (locale == SocketLocale.Russian) item.price[StoreCurrency.RUB]!! else item.price[StoreCurrency.USD]!!,
        currency = if (locale == SocketLocale.Russian) StoreCurrency.RUB.displayName else StoreCurrency.USD.displayName,

        crystals = item.crystals?.base,
        bonusCrystals = item.crystals?.bonus,

        premiumDuration = item.premium?.base
      )
    )
  }
}
