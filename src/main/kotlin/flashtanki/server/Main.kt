  @file:Suppress("PLUGIN_IS_NOT_ENABLED")

  package flashtanki.server

  import com.github.ajalt.clikt.core.CliktCommand
  import com.github.ajalt.clikt.parameters.options.option
  import com.squareup.moshi.Moshi
  import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
  import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
  import flashtanki.server.api.IApiServer
  import flashtanki.server.api.WebApiServer
  import flashtanki.server.battles.BattleProcessor
  import flashtanki.server.battles.DamageCalculator
  import flashtanki.server.battles.IBattleProcessor
  import flashtanki.server.battles.IDamageCalculator
  import flashtanki.server.battles.map.IMapRegistry
  import flashtanki.server.battles.map.MapRegistry
  import flashtanki.server.chat.ChatCommandRegistry
  import flashtanki.server.chat.IChatCommandRegistry
  import flashtanki.server.client.*
  import flashtanki.server.commands.CommandRegistry
  import flashtanki.server.commands.ICommandRegistry
  import flashtanki.server.extensions.cast
  import flashtanki.server.garage.GarageItemConverter
  import flashtanki.server.garage.GarageMarketRegistry
  import flashtanki.server.garage.IGarageItemConverter
  import flashtanki.server.garage.IGarageMarketRegistry
  import flashtanki.server.invite.IInviteRepository
  import flashtanki.server.invite.IInviteService
  import flashtanki.server.invite.InviteRepository
  import flashtanki.server.invite.InviteService
  import flashtanki.server.ipc.IProcessNetworking
  import flashtanki.server.ipc.NullNetworking
  import flashtanki.server.ipc.ProcessMessage
  import flashtanki.server.ipc.WebSocketNetworking
  import flashtanki.server.lobby.chat.ILobbyChatManager
  import flashtanki.server.lobby.chat.LobbyChatManager
  import flashtanki.server.lobby.clan.ClanRepository
  import flashtanki.server.lobby.clan.IClanRepository
  import flashtanki.server.quests.IQuestConverter
  import flashtanki.server.quests.IRandomQuestService
  import flashtanki.server.quests.QuestConverter
  import flashtanki.server.quests.RandomQuestService
  import flashtanki.server.resources.IResourceServer
  import flashtanki.server.resources.ResourceServer
  import flashtanki.server.serialization.*
  import flashtanki.server.store.IStoreItemConverter
  import flashtanki.server.store.IStoreRegistry
  import flashtanki.server.store.StoreItemConverter
  import flashtanki.server.store.StoreRegistry
  import io.ktor.network.selector.*
  import io.ktor.network.sockets.*
  import io.ktor.utils.io.*
  import kotlinx.coroutines.*
  import kotlinx.coroutines.CancellationException
  import kotlinx.serialization.Serializable
  import kotlinx.serialization.decodeFromString
  import kotlinx.serialization.json.Json
  import mu.KotlinLogging
  import org.koin.core.context.startKoin
  import org.koin.core.logger.Level
  import org.koin.dsl.module
  import org.koin.logger.SLF4JLogger
  import org.reflections.Reflections
  import org.reflections.scanners.Scanners
  import java.io.ByteArrayOutputStream
  import java.io.File
  import java.nio.file.Paths
  import kotlin.io.path.absolute
  import kotlin.reflect.KClass

  suspend fun ByteReadChannel.readAvailable(): ByteArray {
    val data = ByteArrayOutputStream()
    val temp = ByteArray(1024)
    // while(!isClosedForRead) {
    val read = readAvailable(temp)
    if(read > 0) {
      data.write(temp, 0, read)
    }
    // }

    return data.toByteArray()
  }

  interface IPromoCodeService {
    val promocodes: MutableList<PromoCode>
    val blackList: MutableList<String>

    suspend fun initPromoCodes()
    suspend fun checkPromoCode(promo: String): Boolean
    suspend fun removePromoCode(promo: String)
    suspend fun getPrizesForPromo(promo: String): List<PrizeType>?
  }

  class PromoCodeService(private val resourceManager: ResourceManager) : IPromoCodeService {
    override var promocodes: MutableList<PromoCode> = mutableListOf()
    override var blackList: MutableList<String> = mutableListOf()
    private val logger = KotlinLogging.logger {}

    override suspend fun initPromoCodes() {
      logger.debug { "Initing promocodes..." }
      val items = readPromoCodesFromFile()
      val blackListFile = File(resourceManager.get("promocodes/blacklist.json").toString())

      if (blackListFile.exists()) {
        blackListFile.forEachLine { line ->
          blackList.add(line.trim())
        }
      }

      for (item in items) {
        if (!blackList.contains(item.code)) {
          promocodes.add(item)
          logger.debug { "Inited promocode! Promocode: ${item.code}, ${item.types}" }
        }
      }

      logger.debug { "All promocodes inited!" }
    }

    private fun readPromoCodesFromFile(): List<PromoCode> {
      val jsonFile = File(resourceManager.get("promocodes/promocodes.json").toString())
      val jsonString = jsonFile.bufferedReader().use { it.readText() }

      return try {
        Json.decodeFromString<List<PromoCode>>(jsonString)
      } catch (e: Exception) {
        logger.error(e) { "Error parsing JSON for promo codes." }
        emptyList()
      }
    }

    override suspend fun checkPromoCode(promo: String): Boolean {
      return promocodes.any { it.code == promo }
    }

    override suspend fun removePromoCode(promo: String) {
      promocodes.removeIf { it.code == promo }
      blackList.add(promo)
      File(resourceManager.get("promocodes/blacklist.json").toString()).bufferedWriter().use { out ->
        out.write("$promo\n")
      }
    }

    override suspend fun getPrizesForPromo(promo: String): List<PrizeType>? {
      val promoCode = promocodes.find { it.code == promo }
      return promoCode?.types
    }
  }


  interface ISocketServer {
    val players: MutableList<UserSocket>

    suspend fun run(scope: CoroutineScope)
    suspend fun stop()
  }

  class SocketServer : ISocketServer {
    private val logger = KotlinLogging.logger { }

    override val players: MutableList<UserSocket> = mutableListOf()

    private lateinit var server: ServerSocket

    private var acceptJob: Job? = null

    override suspend fun run(scope: CoroutineScope) {
      server = aSocket(ActorSelectorManager(Dispatchers.IO))
        .tcp()
        .bind(InetSocketAddress("0.0.0.0", 2351))

      logger.info { "Started TCP server on ${server.localAddress}" }

      acceptJob = scope.launch {
        try {
          val coroutineScope = CoroutineScope(scope.coroutineContext + SupervisorJob())

          while(true) {
            val tcpSocket = server.accept()
            val socket = UserSocket(coroutineContext, tcpSocket)
            players.add(socket)

            println("Socket accepted: ${socket.remoteAddress}")

            coroutineScope.launch { socket.handle() }
          }
        } catch(exception: CancellationException) {
          logger.debug { "Client accept job cancelled" }
        } catch(exception: Exception) {
          logger.error(exception) { "Exception in client accept loop" }
        }
      }
    }

    override suspend fun stop() {
      // TODO(Assasans): Hack to prevent ConcurrentModificationException
      players.toList().forEach { player -> player.deactivate() }
      acceptJob?.cancel()
      withContext(Dispatchers.IO) { server.close() }

      logger.info { "Stopped game server" }
    }
  }

  fun main(args: Array<String>) = object : CliktCommand() {
    val ipcUrl by option("--ipc-url", help = "IPC server URL")

    override fun run() = runBlocking {
      val logger = KotlinLogging.logger { }

      logger.info { "Hello!" }
      logger.info { "Root path: ${Paths.get("").absolute()}" }

      val module = module {
        single<IProcessNetworking> {
          when(val url = ipcUrl) {
            null -> NullNetworking()
            else -> WebSocketNetworking(url)
          }
        }
        single<ISocketServer> { SocketServer() }
        single<IPromoCodeService> { PromoCodeService(ResourceManager()) }
        single<IResourceServer> { ResourceServer() }
        single<IApiServer> { WebApiServer() }
        single<ICommandRegistry> { CommandRegistry() }
        single<IBattleProcessor> { BattleProcessor() }
        single<IResourceManager> { ResourceManager() }
        single<IGarageItemConverter> { GarageItemConverter() }
        single<IResourceConverter> { ResourceConverter() }
        single<IGarageMarketRegistry> { GarageMarketRegistry() }
        single<IMapRegistry> { MapRegistry() }
        single<IStoreRegistry> { StoreRegistry() }
        single<IStoreItemConverter> { StoreItemConverter() }
        single<ILobbyChatManager> { LobbyChatManager() }
        single<IChatCommandRegistry> { ChatCommandRegistry() }
        single<IDamageCalculator> { DamageCalculator() }
        single<IQuestConverter> { QuestConverter() }
        single<IRandomQuestService> { RandomQuestService() }
        single<IUserRepository> { UserRepository() }
        single<IClanRepository> { ClanRepository() }
        single<IUserSubscriptionManager> { UserSubscriptionManager() }
        single<IInviteService> { InviteService(enabled = false) }
        single<IInviteRepository> { InviteRepository() }
        single {
          Moshi.Builder()
            .add(
              PolymorphicJsonAdapterFactory.of(ProcessMessage::class.java, "_").let {
                var factory = it
                val reflections = Reflections("flashtanki.server")

                reflections.get(Scanners.SubTypes.of(ProcessMessage::class.java).asClass<ProcessMessage>()).forEach { type ->
                  val messageType = type.kotlin.cast<KClass<ProcessMessage>>()
                  val name = messageType.simpleName ?: throw IllegalStateException("$messageType has no simple name")

                  factory = factory.withSubtype(messageType.java, name.removeSuffix("Message"))
                  logger.debug { "Registered IPC message: $name" }
                }

                factory
              }
            )
            .add(
              PolymorphicJsonAdapterFactory.of(WeaponVisual::class.java, "\$type")
                .withSubtype(SmokyVisual::class.java, "smoky")
                .withSubtype(RailgunVisual::class.java, "railgun")
                .withSubtype(Railgun_XTVisual::class.java, "railgun_xt")
                .withSubtype(Railgun_TERMINATORVisual::class.java, "railgun_terminator")
                .withSubtype(ThunderVisual::class.java, "thunder")
                .withSubtype(FlamethrowerVisual::class.java, "flamethrower")
                .withSubtype(FreezeVisual::class.java, "freeze")
                .withSubtype(IsidaVisual::class.java, "isida")
                .withSubtype(TwinsVisual::class.java, "twins")
                .withSubtype(ShaftVisual::class.java, "shaft")
                .withSubtype(RicochetVisual::class.java, "ricochet")
            )
            .add(BattleDataJsonAdapterFactory())
            .add(LocalizedStringAdapterFactory())
            .add(ClientLocalizedStringAdapterFactory())
            .add(KotlinJsonAdapterFactory())
            .add(GarageItemTypeAdapter())
            .add(ResourceTypeAdapter())
            .add(ServerMapThemeAdapter())
            .add(BattleTeamAdapter())
            .add(BattleModeAdapter())
            .add(IsidaFireModeAdapter())
            .add(BonusTypeMapAdapter())
            .add(SkyboxSideAdapter())
            .add(EquipmentConstraintsModeAdapter())
            .add(ChatModeratorLevelAdapter())
            .add(SocketLocaleAdapter())
            .add(StoreCurrencyAdapter())
            .add(ScreenAdapter())
            .add(SerializeNull.JSON_ADAPTER_FACTORY)
            .build()
        }
      }

      startKoin {
        logger(SLF4JLogger(Level.ERROR))

        modules(module)
      }

      val server = Server()

      server.run()
    }
  }.main(args)
