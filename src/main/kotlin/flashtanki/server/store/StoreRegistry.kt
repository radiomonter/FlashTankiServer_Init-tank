package flashtanki.server.store

import kotlin.io.path.*
import com.squareup.moshi.Moshi
import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import flashtanki.server.IResourceManager

interface IStoreRegistry {
  val categories: MutableMap<String, ServerStoreCategory>

  suspend fun load()
}

class StoreRegistry : IStoreRegistry, KoinComponent {
  private val logger = KotlinLogging.logger { }

  private val json by inject<Moshi>()
  private val resourceManager by inject<IResourceManager>()

  override val categories: MutableMap<String, ServerStoreCategory> = mutableMapOf()

  override suspend fun load() {
    resourceManager.get("store/items").absolute().forEachDirectoryEntry { categoryEntry ->
      if (!categoryEntry.isDirectory()) return@forEachDirectoryEntry

      logger.debug { "Loading store category ${categoryEntry.name}..." }

      val categoryJsonPath = categoryEntry.resolve("category.json")
      if (!categoryJsonPath.exists()) {
        logger.warn { "Category JSON not found for ${categoryEntry.name}" }
        return@forEachDirectoryEntry
      }

      val category = json.adapter(ServerStoreCategory::class.java)
        .failOnUnknown()
        .fromJson(categoryJsonPath.readText()) ?: run {
        logger.error { "Failed to parse category JSON for ${categoryEntry.name}" }
        return@forEachDirectoryEntry
      }

      category.id = categoryEntry.name
      category.items = mutableListOf()

      categoryEntry.forEachDirectoryEntry { entry ->
        if (entry.name == "category.json" || entry.extension != "json") return@forEachDirectoryEntry

        val item = json.adapter(ServerStoreItem::class.java)
          .failOnUnknown()
          .fromJson(entry.readText()) ?: run {
          logger.error { "Failed to parse item JSON for ${entry.name}" }
          return@forEachDirectoryEntry
        }

        item.id = entry.nameWithoutExtension
        item.category = category

        category.items.add(item)
        logger.debug { "  > Loaded store item ${item.id}" }
      }

      categories[category.id] = category
    }
  }
}
