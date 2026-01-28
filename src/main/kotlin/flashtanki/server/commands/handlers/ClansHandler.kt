package flashtanki.server.commands.handlers

import flashtanki.server.commands.*
import flashtanki.server.client.*
import flashtanki.server.lobby.clan.*
import org.koin.core.component.inject
import org.koin.core.component.KoinComponent

class ClansHandler : ICommandHandler, KoinComponent {

    private val clanRepository: IClanRepository by inject()

    @CommandHandler(CommandName.ShowClanServer)
    suspend fun showClanServer(socket: UserSocket) {
        Command(CommandName.ShowNotInClan).send(socket)
    }

    @CommandHandler(CommandName.ClanValidateTag)
    suspend fun clanValidateTag(socket: UserSocket, tag: String) {
        if(clanRepository.getClanByTag(tag) != null) {
            Command(CommandName.СlanTagExist).send(socket)
            return
        }
        Command(CommandName.СlanTagNotExist).send(socket)
    }

    @CommandHandler(CommandName.ClanValidateName)
    suspend fun clanValidateName(socket: UserSocket, name: String) {
        if(clanRepository.getClanByName(name) != null) {
            Command(CommandName.СlanNameExist).send(socket)
            return
        }
        Command(CommandName.СlanNameNotExist).send(socket)
    }

    @CommandHandler(CommandName.CheckClanName)
    suspend fun CheckClanName(socket: UserSocket, name: String) {
        socket.sendChat("[CheckClanName] Not implemented yet")
    }

    @CommandHandler(CommandName.RejectAll)
    suspend fun RejectAll(socket: UserSocket, username: String) {
        socket.sendChat("[RejectAll] Not implemented yet")
    }

    @CommandHandler(CommandName.ClanCreateServer)
    suspend fun clanCreate(socket: UserSocket, name: String, tag: String) {
        val selfUser = socket.user ?: throw Exception("No User")
        val clan = clanRepository.createClan(tag, name, selfUser.username)
        if (clan == null) {
            socket.sendChat("Failed to create clan: a clan with the same name, tag, or creator already exists.")
            return
        }
        Command(CommandName.ShowForeignClan, clan.creatorId, clan.description, clan.name, clan.tag).send(socket)
    }
	
	@CommandHandler(CommandName.CheckClanName)
	suspend fun checkClanName(socket: UserSocket, name: String) {
	    if(clanRepository.getClanByTag(name) != null) {
            Command(CommandName.ClanExist).send(socket)
            return
        }
        Command(CommandName.ClanNotExist).send(socket)
	}
	
	@CommandHandler(CommandName.AddInClanByName)
	suspend fun addInClanByName(socket: UserSocket, name: String) {
	   val selfUser = socket.user ?: throw Exception("No User")
	   val clan = clanRepository.getClanByTag(name) ?: throw Exception("No Clan")
	   if (clan != null) {
	       Command(CommandName.AddInClan, name).send(socket)
	   }
	}
}