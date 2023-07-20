package com.delta.server

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.util.concurrent.ConcurrentHashMap


/**
 * Этот сервер отвечает за то, чтобы рассылать сообщения игрокам (точнее всем кто к нему подключился).
 * @property [connections] Текущие соединения с этим сервером.
 * @property [webSocketServer] Сервер. Входящие сообщения могут быть использованы, например, для чата.
 * @property [broadcast] Метод, который рассылает сообщение всем подключившимся игрокам.
 * Основное применение -- передать текущее состояние игры.
 */
internal class WebSocketServer {
    /**
     * ConcurrentHashMap -- структрура, которая хранит элементы вида (ключ, значение).
     * Слово Hash в данном случае означает, что эти элементы хранятся определённым образом,
     * который ускоряет время поиска нужных элементов.
     * Concurrent обозначает, что эта таблица хорошо работает в условиях мультипоточности
     */
    private val connections = ConcurrentHashMap<String, WebSocketSession>()

    private val webSocketServer = embeddedServer(Netty, host = getIpAddress(), port = 8081) {
        install(WebSockets)

        routing {
            webSocket("/game") {
                try {
                    val id = call.parameters["id"] ?: error("Missing id parameter")
                    connections[id] = this
                    try {
                        // Читаем входящие сообщение в эту сессию
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                println("Received message from $id: $text")
                            }
                        }
                    } finally {
                        connections.remove(id)
                    }
                } catch (e: Exception) {
                    send(e.message.toString())
                }
            }
        }
    }

    suspend fun broadcast(message: String) = connections.values.forEach { it.send(message) }

    fun start(wait: Boolean) = webSocketServer.start(wait)

}

