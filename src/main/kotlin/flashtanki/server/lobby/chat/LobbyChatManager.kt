package flashtanki.server.lobby.chat

import kotlin.reflect.jvm.jvmName
import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.minutes
import flashtanki.server.ISocketServer
import flashtanki.server.battles.IBattleProcessor
import flashtanki.server.chat.CommandInvocationSource
import flashtanki.server.chat.CommandParseResult
import flashtanki.server.chat.IChatCommandRegistry
import flashtanki.server.client.*
import kotlinx.coroutines.delay
import java.util.*
import kotlinx.coroutines.*
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandName
import flashtanki.server.extensions.truncateLastTo
import org.hibernate.query.sqm.tree.SqmNode.log

interface ILobbyChatManager {
  val messagesBufferSize: Int
  val messages: MutableList<ChatMessage>

  suspend fun startBroadcastYellowMessages()
  suspend fun send(socket: UserSocket, message: ChatMessage)
  suspend fun broadcast(message: ChatMessage)
}

class LobbyChatManager : ILobbyChatManager, KoinComponent {
  private val logger = KotlinLogging.logger { }
  private val lobbyChatManager by inject<ILobbyChatManager>()
  private val battleProcessor by inject<IBattleProcessor>()

    override val messagesBufferSize: Int = 100 // Original server stores last 70 messages
  override val messages: MutableList<ChatMessage> = mutableListOf()
  private val yellowText: MutableList<String> = mutableListOf(		    "Логин и пароль от аккаунта - это ключ от вашего танка. Нельзя вводить их где-то, кроме формы входа в игру. Ежедневно злоумышленники угоняют сотни аккаунтов честных танкистов, из-за их халатного обращения с логином и паролем. Будьте внимательны!",
    "Следите за тем, чтобы ваш аккаунт был надёжно защищён. Не забывайте, что, потеряв аккаунт, вы скорее всего потеряете и почту. А это значит, что злоумышленник получит доступ к вашим персональным данным, платёжным паролям и другой информации, которая может быть в письмах. Будьте внимательнее!",
    "Напоминаем о важности привязки аккаунта к адресу электронной почты. В случае угона, вы сможете восстановить аккаунт самостоятельно. Привяжите e-mail в настройках уже сегодня!",
    "\"Читы\" для любой игры - это всего лишь приманка. Ссылки на \"читы\" ведут к файлам, которые содержат вирусы и могут навредить вашему компьютеру или подсказать злоумышленнику ваш логин и пароль",
    "Обратите внимание на то, что администрация проекта никогда не разыгрывает в чате различные \"подарки\" или \"инвайты\" на тестовый сервер, а также не предлагает принять участие в конкурсах. Для этого используется сайт и алерты. Не верьте мошенникам!",
    "Знаете ли вы, что угнанный у вас аккаунт непременно будет использован для тестов новых читов? Такой аккаунт восстановить будет уже невозможно! Будьте внимательнее - не сообщайте никому свой логин и пароль, не переходите по сомнительным ссылкам в чате",
    "Для максимально комфортной игры рекомендуется отключить программу Punto Switcher, работа которой может вызывать \"лаги\".",
    "Большинство угонов аккаунтов происходит из-за слишком простых паролей. Задумывайтесь над этим, сделайте пароль сложнее, чаще его меняйте! Защитите свой аккаунт от злоумышленников",
    "Сделайте свою игру разнообразнее. Вступите в клан и участвуйте в многочисленных турнирах! Станьте грозным соперником на полях сражений. Всю необходимую информацию о вступлении в клан можно найти в  #battle89875@инструкции@#8.",
    "Танкисты и танкистки, вы пользуйтесь социальной сетью ВКонтакте? Тогда обязательно привяжите свой аккаунт в \"FlashTanki\" к ней! Вы получаете простоту входа в игру и безопасность аккаунта всего за пару минут. Ищите кнопку \"ВКонтакте\" в игровых Настройках.",
    "Танкисты и танкистки, при помощи команды чата /vote НИК_НАРУШИТЕЛЯ вы можете пожаловаться на нарушителя прямо в битве. Ваша жалоба будет отправлена, и модератор придёт на помощь!",
    "Дорогие игроки, не поддавайтесь на обман мошенников, которые пытаются предложить вам платные услуги в \"FlashTanki\". Сотрудники службы технической поддержки НИКОГДА не предлагают платных услуг. За реальные деньги можно купить ТОЛЬКО кристаллы и ТОЛЬКО в самой игре в платежном разделе.",
    "Если у вас возникли проблемы с платежом, либо вы потеряли контроль над своим аккаунтом - обратитесь в нашу службу технической поддержки, написав электронное письмо по адресу helpflashtankionline@gmail.com.",
    "Будьте бдительны - не переходите по подозрительным ссылкам в личных сообщениях и общем чате. Помните, администрация проекта никогда не раздаёт кристаллы через внешние сайты, не спрашивает пароль от аккаунта и почты. Вы можете потерять контроль над своим танком.",
    "Привяжи почту к аккаунту - он будет лучше защищен от потери и взлома, а ты получишь кристальное вознаграждение.",
    "Никому не сообщайте свой пароль, не вводите логин и пароль от игры на сторонних ресурсах, не сообщайте адрес электронной почты, к которому привязан аккаунт. #battle43172@Эта статья@#8 поможет вам узнать, как защититься от мошенников.",
    "В случае угона вашего аккаунта вам поможет эта #battle43173@статья@#8.",
    "Уважаемые игроки! Функций передачи, продажи кристаллов, а также корпусов, красок, пушек и припасов в игре не существует. Не обращайте внимания на предложения бесплатно начислить кристаллы, вы можете потерять свой аккаунт.",
    "Танкисты! Газета ФТ теперь в новом формате - читайте интересные статьи и рассказы в специальном разделе #battle49175@форума@#8.",
    "«FlashTanki» теперь и в Одноклассниках! Интересные конкурсы, арты и видео, впечатления танкистов об игре - всё это вы найдёте в нашей #battle43373@группе@#8.",
    "Ответы на многие игровые вопросы вы можете найти на нашем информационном портале #battle42973@помощи танкистам@#8. Там же можно предложить свои идеи по игре.",
    "Помните, что никаких читов на кристаллы и прочее имущество не существует. Это уловки мошенников, которые хотят украсть ваш аккаунт.")
  private val server by inject<ISocketServer>()
  private val chatCommandRegistry by inject<IChatCommandRegistry>()

  override suspend fun startBroadcastYellowMessages() {
    //TODO(TitanoMachina)
    var text: Int = 0
    while (true) {
      delay(2 * 60 * 1000)
      val message = ChatMessage(
              name = "",
              rang = 0,
              chatPermissions = ChatModeratorLevel.None,
              message = yellowText[text].toString(),
              addressed = false,
              chatPermissionsTo = ChatModeratorLevel.None,
              nameTo = "",
              rangTo = 0,
              system = true,
              yellow = true,
              sourceUserPremium = false,
              targetUserPremium = false
      )
      broadcast(message)
      text++
      if (text == yellowText.size) {
        text = 0
      }
    }
  }

  override suspend fun send(socket: UserSocket, message: ChatMessage) {
    val content = message.message
    if(content.startsWith("/")) {
      logger.debug { "Parsing message as command: $content" }

      val args = chatCommandRegistry.parseArguments(content.drop(1))
      logger.debug { "Parsed arguments: $args" }

      when(val result = chatCommandRegistry.parseCommand(args)) {
        is CommandParseResult.Success          -> {
          logger.debug { "Parsed command: ${result.parsedCommand.command.name}" }

          try {
            chatCommandRegistry.callCommand(socket, result.parsedCommand, CommandInvocationSource.LobbyChat)
          } catch(exception: Exception) {
            logger.error(exception) { "An exception occurred while calling command ${result.parsedCommand.command.name}" }

            val builder = StringBuilder()
            builder.append(exception::class.qualifiedName ?: exception::class.simpleName ?: exception::class.jvmName)
            builder.append(": ")
            builder.append(exception.message ?: exception.localizedMessage)
            builder.append("\n")
            exception.stackTrace.forEach { frame ->
              builder.appendLine("    at $frame")
            }

            //socket.sendChat("An exception occurred while calling command ${result.parsedCommand.command.name}\n$builder")
          }
        }

        is CommandParseResult.UnknownCommand   -> {
          logger.debug { "Unknown command: ${result.commandName}" }
          socket.sendChat("Во время выполнения команды произошла ошибка")
        }

        is CommandParseResult.CommandQuoted    -> {
          logger.debug { "Command name cannot be quoted" }
          socket.sendChat("Имя команды не может быть заключено в кавычки")
        }

        is CommandParseResult.TooFewArguments  -> {
          val missingArguments = result.missingArguments.map { argument -> argument.name }.joinToString(", ")

          logger.debug { "Too few arguments for command '${result.command.name}'. Missing values for: $missingArguments" }
          socket.sendChat("Слишком мало аргументов для команды '${result.command.name}'. Missing values for: $missingArguments")
        }

        is CommandParseResult.TooManyArguments -> {
          logger.debug { "Too many arguments for command '${result.command.name}'. Expected ${result.expected.size}, got: ${result.got.size}" }
          socket.sendChat("Слишком мало аргументов для команды '${result.command.name}'. Expected ${result.expected.size}, got: ${result.got.size}")
        }
      }
        return
    } else if (content.contains("#/battle/")) {
        handleBattleLink(content, socket)
    } else {
        broadcast(message)
    }
  }

    private suspend fun handleBattleLink(content: String, socket: UserSocket) {
        val user = socket.user
                if (user == null) {
                    log.error("No User in socket")
                    return
                }

        val battleLinkPattern = "#/battle/(\\w+)".toRegex()
        val matchResult = battleLinkPattern.find(content)

        if (matchResult != null) {
            val battleId = matchResult.groupValues[1]
            val battle = try {
                battleProcessor.getBattle(battleId)
            } catch (e: Exception) {
                log.error("Error fetching battle $battleId: ${e.message}")
                return
            }

            if (battle == null) {
                log.error("Battle $battleId not found")
                return
            }

            val battleName = battle.title
            try {
                lobbyChatManager.send(socket, ChatMessage(
                    name = "",
                    rang = user.rank.value,
                    chatPermissions = user.chatModerator,
                    message = """<font color="#13ff01">${user.username}: </font><font color="#FFFFFF"><a href='event:$battleId'><u>$battleName</u></a></font>""",
                    system = true
                ))
            } catch (e: Exception) {
                log.error("Error sending message to lobby chat: ${e.message}")
            }
        } else {
            log.warn("Battle link not found in content: $content")
        }
    }

  override suspend fun broadcast(message: ChatMessage) {
    Command(CommandName.SendChatMessageClient, message.toJson()).let { command ->
      server.players
        .filter { player -> player.screen == Screen.BattleSelect || player.screen == Screen.Garage }
        .filter { player -> player.active }
        .forEach { player -> command.send(player) }
    }

    messages.add(message)
    messages.truncateLastTo(messagesBufferSize)
  }
}
