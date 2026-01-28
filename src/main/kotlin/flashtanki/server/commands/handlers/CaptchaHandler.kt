package flashtanki.server.commands.handlers

import flashtanki.server.client.CaptchaLocation
import flashtanki.server.client.UserSocket
import flashtanki.server.commands.CommandHandler
import flashtanki.server.commands.CommandName
import flashtanki.server.commands.ICommandHandler
import flashtanki.server.utils.Captcha

class CaptchaHandler : ICommandHandler {
  private val captchaUpdateLimit = 1 // Максимальное количество обновлений капчи
  private val captchaUpdateInterval = 3000L // Интервал времени в миллисекундах (здесь 3000 мс = 3 секунды)

  @CommandHandler(CommandName.RefreshRegistrationCaptcha)
  suspend fun refreshRegistrationCaptcha(socket: UserSocket) {
    val userIp = socket.remoteAddress.toString()
    val updateTimestamps: MutableList<Long> = socket.captchaUpdateTimestamps.getOrDefault(userIp, mutableListOf())
    val currentTime = System.currentTimeMillis()

    updateTimestamps.removeAll { currentTime - it > captchaUpdateInterval }
    updateTimestamps.add(currentTime)

    userIp.let {
      socket.captchaUpdateTimestamps[it] = updateTimestamps
    }

    if(updateTimestamps.size > captchaUpdateLimit) return

    Captcha().generateAndSendCaptcha(CommandName.UpdateCaptcha, CaptchaLocation.Registration, socket)
  }

  @CommandHandler(CommandName.RefreshLobbyCaptcha)
  suspend fun refreshLobbyCaptcha(socket: UserSocket) {
    Captcha().generateAndSendCaptcha(CommandName.СaptchaUpdated, CaptchaLocation.AccountSettings, socket)
  }
}
