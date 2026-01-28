package flashtanki.server.ipc

import kotlinx.coroutines.flow.SharedFlow
import org.koin.java.KoinJavaComponent

interface IProcessNetworking {
  val events: SharedFlow<ProcessMessage>

  suspend fun run()
  suspend fun send(message: ProcessMessage)
  suspend fun close()
}

suspend fun ProcessMessage.send(networking: IProcessNetworking) {
  networking.send(this)
}

suspend fun ProcessMessage.send() {
  val networking by KoinJavaComponent.getKoin().inject<IProcessNetworking>()
  send(networking)
}
