package flashtanki.server.resources

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import flashtanki.server.IResourceManager
import flashtanki.server.ServerIdResource
import flashtanki.server.utils.ResourceUtils
import io.ktor.util.*
import kotlin.io.path.inputStream
import kotlin.io.path.notExists

interface IResourceServer {
  suspend fun run()
  suspend fun stop()
}

class ResourceServer : IResourceServer, KoinComponent {
  private val logger = KotlinLogging.logger { }

  private val resourceManager: IResourceManager by inject()

  private val originalPackName = "original"

  private lateinit var engine: ApplicationEngine

  override suspend fun run() {
    engine = embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
      routing {
        get("/{id1}/{id2}/{id3}/{id4}/{version}/{file}") {
          val resourceId = ResourceUtils.decodeId(
            listOf(
              call.parameters["id1"]!!, call.parameters["id2"]!!, call.parameters["id3"]!!, call.parameters["id4"]!!,
              call.parameters["version"]!!
            )
          )
          val file = call.parameters["file"]!!

          val resource = resourceManager.get("static/$originalPackName/${resourceId.id}/${resourceId.version}/$file")
          if (resource.notExists()) {
            call.response.status(HttpStatusCode.NotFound)
            call.respondText(ContentType.Text.Html) { getNotFoundBody(resourceId, file) }

            logger.debug { "Resource ${resourceId.id}:${resourceId.version}/$file not found" }
            return@get
          }

          val contentType = when (resource.extension) {
            "jpg" -> ContentType.Image.JPEG
            "png" -> ContentType.Image.PNG
            "json" -> ContentType.Application.Json
            "xml" -> ContentType.Application.Xml
            else -> ContentType.Application.OctetStream
          }

          call.respondOutputStream(contentType) { resource.inputStream().copyTo(this) }
          logger.trace { "Sent resource ${resourceId.id}:${resourceId.version}/$file" }
        }

        static("/assets") {
          staticRootFolder = resourceManager.get("assets").toFile()
          files(".")
        }
      }
    }.start()

    logger.info { "Started resource server" }
  }

  private fun getNotFoundBody(resourceId: ServerIdResource, file: String) = """
        <!DOCTYPE html>
        <html>
        <head><title>404 Not Found</title></head>
        <body>
          <h1>404 Not Found</h1>
          <p>The requested resource was not found on this server.</p>
        </body>
        </html>
    """.trimIndent()

  override suspend fun stop() {
    logger.debug { "Stopping Ktor engine..." }
    engine.stop(2000, 3000)

    logger.info { "Stopped resource server" }
  }
}
