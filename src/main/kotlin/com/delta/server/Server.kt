package com.delta.server

import com.delta.GameBoard
import com.delta.Tilous
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch


/**
 * Этот класс хранит два сервера -- [WebSocketServer] and [HttpServer].
 * Всё, что он делает, это создаёт [игру][Tilous] и запускает два сервера параллельно, используя [coroutineScope].
 */
class Server {
    val game : Tilous = Tilous(GameBoard(15))

    private val webSocketServer = WebSocketServer()
    private val httpServer = HttpServer(game, webSocketServer::broadcast)

    suspend fun start() {
        println("My ip address is ${getIpAddress()}")

        coroutineScope {
            launch { httpServer.start(wait = false) }
            launch { webSocketServer.start(wait = true) }
        }
    }
}