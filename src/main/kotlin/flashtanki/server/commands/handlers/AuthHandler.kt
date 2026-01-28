package flashtanki.server.commands.handlers

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import java.math.BigInteger
import java.security.MessageDigest
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import java.awt.Desktop
import java.net.URI
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import flashtanki.server.client.*
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandHandler
import flashtanki.server.commands.CommandName
import flashtanki.server.commands.ICommandHandler
import flashtanki.server.extensions.launchDelayed
import flashtanki.server.invite.IInviteService
import flashtanki.server.utils.Captcha

object AuthHandlerConstants {
  const val InviteRequired = "Invite code is required to log in"

  fun getInviteInvalidUsername(username: String) = "This invite can only be used with the username \"$username\""
}

class AuthHandler : ICommandHandler, KoinComponent {
  private val logger = KotlinLogging.logger { }

  private val userRepository: IUserRepository by inject()
  private val userSubscriptionManager: IUserSubscriptionManager by inject()
  private val inviteService: IInviteService by inject()

  @CommandHandler(CommandName.Login)
  suspend fun login(socket: UserSocket, captcha: String, rememberMe: Boolean, username: String, password: String) {
    val invite = socket.invite
    if(inviteService.enabled) {
      // TODO(Assasans): AuthDenied shows unnecessary "Password incorrect" modal
      if(invite == null) {
        Command(CommandName.ShowAlert, AuthHandlerConstants.InviteRequired).send(socket)
        Command(CommandName.AuthDenied).send(socket)
        return
      }

      invite.username?.let { inviteUsername ->
        if(username == inviteUsername || username.startsWith("${inviteUsername}_")) return@let

        Command(CommandName.ShowAlert, AuthHandlerConstants.getInviteInvalidUsername(inviteUsername)).send(socket)
        Command(CommandName.AuthDenied).send(socket)
        return
      }
    }

    logger.debug { "User login: [ Invite = '${socket.invite?.code}', Username = '$username', Password = '$password', Captcha = '$captcha', Remember = $rememberMe ]" }

    val user = userRepository.getUser(username) ?: return Command(CommandName.AuthDenied).send(socket)
    logger.debug { "Got user from database: ${user.username}" }

    if(inviteService.enabled && invite != null) {
      invite.username = user.username
      invite.updateUsername()
    }
    if(user.password == password) {
      if(user.bannedUntil != null) {
        logger.debug { "User login rejected: user $username is banned" }

        Command(CommandName.ShowAlert, "Вы забанены за нарушение правил игры").send(socket)
        Command(CommandName.AuthDenied).send(socket)
        return
      }
      if (rememberMe) {
         Command(CommandName.SetEntranceHash, user.hash).send(socket)
      }
      logger.debug { "User login allowed" }

      userSubscriptionManager.add(user)
      socket.user = user
      Command(CommandName.AuthAccept).send(socket)
      socket.loadLobby()
    } else {
      logger.debug { "User login rejected: incorrect password" }
      Command(CommandName.AuthDenied).send(socket)
    }
  }

  @CommandHandler(CommandName.LoginByHash)
  suspend fun loginByHash(socket: UserSocket, hash: String) {
    if(inviteService.enabled && socket.invite == null) {
      Command(CommandName.ShowAlert, AuthHandlerConstants.InviteRequired).send(socket)
      Command(CommandName.AuthDenied).send(socket)
      return
    }
    val user = userRepository.getUserByHash(hash) ?: return Command(CommandName.LoginByHashFailed).send(socket)
    userSubscriptionManager.add(user)
    socket.user = user
    Command(CommandName.AuthAccept).send(socket)
    socket.loadLobby()
    logger.debug { "User login by hash: $hash" }
  }

  @CommandHandler(CommandName.ActivateInvite)
  suspend fun activateInvite(socket: UserSocket, code: String) {
    logger.debug { "Fetching invite: $code" }

    val invite = inviteService.getInvite(code)
    if(invite != null) {
      Command(CommandName.InviteValid).send(socket)
    } else {
      Command(CommandName.InviteInvalid).send(socket)
    }

    socket.invite = invite
  }

  @CommandHandler(CommandName.CheckUsernameRegistration)
  suspend fun checkUsernameRegistration(socket: UserSocket, username: String) {
    if(userRepository.getUser(username) != null) {
      // TODO(Assasans): Use "nickname_exist"
      Command(CommandName.CheckUsernameRegistrationClient, "incorrect").send(socket)
      return
    }

    // Pass-through
    Command(CommandName.CheckUsernameRegistrationClient, "not_exist").send(socket)
  }

  @CommandHandler(CommandName.RegisterUser)
  suspend fun registerUser(socket: UserSocket, username: String, password: String, captcha: String) {
    val invite = socket.invite
    if(inviteService.enabled) {
      // TODO(Assasans): "Reigster" button is not disabled after error
      if(invite == null) {
        Command(CommandName.ShowAlert, AuthHandlerConstants.InviteRequired).send(socket)
        return
      }

      invite.username?.let { inviteUsername ->
        if(username == inviteUsername || username.startsWith("${inviteUsername}_")) return@let

        Command(CommandName.ShowAlert, AuthHandlerConstants.getInviteInvalidUsername(inviteUsername)).send(socket)
        return
      }
    }

    val answer = socket.captcha[CaptchaLocation.Registration]
    if(captcha != answer) {
      Command(CommandName.WrongCaptcha).send(socket)
      logger.info { "Entered wrong captcha: $captcha, Right answer: $answer" }

      Captcha().generateAndSendCaptcha(CommandName.UpdateCaptcha, CaptchaLocation.Registration, socket)
      return
    } else {
      logger.info { "Entered captcha $captcha was right answer!" }
    }

    logger.debug { "Register user: [ Invite = '${socket.invite?.code}', Username = '$username', Password = '$password', Captcha = ${if(captcha.isEmpty()) "*none*" else "'${captcha}'"} ]" }

    val user = userRepository.createUser(username, password, userRepository.md5(username))
               ?: TODO("User exists")

    if(inviteService.enabled && invite != null) {
      invite.username = user.username
      invite.updateUsername()
    }

    userSubscriptionManager.add(user)
    socket.user = user
    Command(CommandName.AuthAccept).send(socket)
    socket.loadLobby()
  }

  @CommandHandler(CommandName.SwitchToRegistration)
  suspend fun switchToRegistration(socket: UserSocket) {
    Captcha().generateAndSendCaptcha(CommandName.UpdateCaptcha, CaptchaLocation.Registration, socket)
  }

  @CommandHandler(CommandName.SetLoginData)
  suspend fun setLoginData(socket: UserSocket, isRegister: Boolean) {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
      val uri = URI("http://localhost:7777/oauth/discord/redirect")
      Desktop.getDesktop().browse(uri)
    }
    if (isRegister) {
      Command(CommandName.ExternalModelValidationSuccess).send(socket)
    } else {

    }
  }

  @CommandHandler(CommandName.RegisterViaDiscord)
  suspend fun registerViaDiscord(socket: UserSocket, name: String) {
    val user = userRepository.createUser(name, userRepository.md5(name + name + name + name + name), socket.snId)
      ?: TODO("User exists")

    userSubscriptionManager.add(user)
    socket.user = user
    Command(CommandName.AuthAccept).send(socket)
    socket.loadLobby()
  }
}
