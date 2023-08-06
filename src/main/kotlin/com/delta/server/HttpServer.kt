package com.delta.server

import com.delta.Tilous
import com.delta.PlayerID
import com.google.gson.Gson
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*
import kotlin.reflect.KSuspendFunction1
import kotlin.random.Random



/**
 * Этот класс ответсвеннен за обработку запросов игроков.
 * Он хранит список игроков, обратившихся к серверу с желанием начать игру и, когда число игроков достигает четырёх,
 * он расспределяет очередность игроков и начинает игру.
 *
 * @property [game] Экземпляр игры в которую мы будем играть.
 * @property [broadcast] Функция, которая рассылает данное сообщение всем подключившимся игрокам.
 * Поскольку рассылкой сообщений занимается [WebSocketServer], мы передаём его метод как параметр в этот класс.
 * Это делается в [Server].
 * @property [players] Список игроков, которые пожелали присоединиться к игре.
 */
internal class HttpServer(


    private val game: Tilous,
    private val broadcast: KSuspendFunction1<String, Unit>
) {
    private val players: MutableList<Player> = mutableListOf()

    /**
     * Соответствие игрок --> PlayerID, которое определяет очередность игроков когда игра началась.
     *
     * Обратите внимание на конструкцию `by lazy`. Она означает, что поле [assignedIds] будет инициализировано
     * только при первом обращении. Первый раз когда оно потребует, будет вызвана функция [assignPlayerIds].
     * В частности, это означает, что вы не должны допустить обращения к этой переменной, когда игра ещё не началась
     */
    private val assignedIds: Map<Player, PlayerID> by lazy { assignPlayerIds() }
    private var gameStarted = false

    /**
     * Рассылает всем игрокам текущее состояние игры в формате json.
     */
    private suspend fun broadcastGameState(): Unit {
        broadcast(Gson().toJson(game))
    }

    private suspend fun respondOk(call: ApplicationCall) {
        broadcastGameState()
        call.respondText("Ok", status = HttpStatusCode.OK)
    }

    private suspend fun respondException(call: ApplicationCall, e: Exception) {
        call.respondText("Error: ${e.message}", status = HttpStatusCode.InternalServerError)
    }

    /**
     * Должна создавать регистрировать нового игрока.
     * Нужно проверить, что игра ещё не началась, что игрок с данным [id] ещё не зарегистрирован.
     * Если всё хорошо, нужно создать новый экземпляр [Player] со случайно сгенерированным паролем,
     * добавить его в [players] с помощью метода [addPlayer] и, наконец, вернуть его.
     * Если игра уже началась или игрок с таким id уже есть, выбросить исключение с соответствующим сообщением.
     */
    private fun registerPlayer(id: String): Player {
        fun generateRandomPassword(length: Int = 8): String {
            val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
            return (1..length)
                .map { allowedChars.random() }
                .joinToString("")
        }
        if (gameStarted) {
            throw Exception("Игра уже началась!")
        }

        val existingPlayer = players.find { it.id == id }
        if (existingPlayer != null) {
            throw Exception("Игрок с ID $id уже зарегистрирован.")
        }

        if (!addPlayer(Player(id, generateRandomPassword()))) {
            throw Exception("Не удалось добавить игрока '$id' в игру.")
        }

        return Player(id, generateRandomPassword())
    }


    /**
     * Добавляет [player] в [players], если число игроков ещё не максимально.
     * @return `true` если игрок успешно добавлен, `false` иначе.
     */
    private fun addPlayer(player: Player): Boolean {
        if (players.size < 4) {
            players.add(player)
            return true
        }
        return false
    }
    /**
     * Убирает игрока из [players].
     * @return `true` если удалось это сделать, `false` иначе
     */
    private fun logoutPlayer(id: String, password: String): Boolean {
        // Find the player with the given ID
        val player = players.find { it.id == id }

        // If no player was found, return false
        if (player == null || player.pwd != password) {
            return false
        }

        // Remove the player from the list and return true
        players.remove(player)
        return true
    }

    /**
     * Пытается начать игру: если игра ещё не началась и число игроков достаточно, то
     * устанавливает [gameStarted] в `true`, распределяет очерёдность ходов и рассылает всем
     * игрокам текущее состояние игры.
     */
    private suspend fun tryToStartGame() : Unit {
        // Check if there are enough players to start the game
        if (players.size < 4) {
            return  // Not enough players, so exit the function early
        }

        // If there are exactly 4 players, assign PlayerIDs and start the game
        if (players.size == 4) {

            gameStarted = true // Start the game

        }
    }

    /**
     * Распределяет [PlayerID] между игроками случайным образом.
     * Внимание, эта функция инициализирует [assignedIds], поэтому она должна вызываться только один раз --
     * в [tryToStartGame].
     */
    fun assignPlayerIds(): Map<Player, PlayerID> {
        // Shuffle the players list for randomness
        val shuffledPlayers = players.shuffled()

        // Create a mutable map to hold the associations
        val playerIdMap = mutableMapOf<Player, PlayerID>()

        // Assign PlayerID to each player based on their position in the shuffled list
        shuffledPlayers.forEachIndexed { index, player ->
            val playerId = PlayerID.values()[index]
            playerIdMap[player] = playerId
        }

        // Return the created map
        return playerIdMap.toMap()
    }


    /**
     * Проверяет, является ли запрос от игрока корректным.
     *
     * В [parameters] должно находиться два параметра -- "id" и "pwd".
     * Если игрок с таким параметрами существует в [players], возвращает соответствующий экземплер [Player].
     * Если нет, выбрасывается исключение с сообщением "No such player or credentials are wrong".
     *
     * @param [checkForGameStart] Если установлен в `true`, но игра не началась, нужно выбросить исключение
     * с сообщением "Game is not started yet".
     */
    private fun validatePlayer(parameters: Parameters, checkForGameStart: Boolean = true): Player {
        TODO()
    }

    /**
     * Эта функция проверяет есть ли в [parameters] параметр "server_pwd"
     * и равен ли он установленному паролю от сервера.
     * Если такого нет, выбрасывает исключение с соответствующим сообщением.
     * Пока можно придумать пароль прямо внутри этой функции.
     */
    private fun validateServersPassword(parameters: Parameters) {
        TODO()
    }

    /**
     * Основная сущность сервера. Принимает запросы вида `server_address/do_something?parameters` и обрабатывает их.
     * Все запросы конфигурируются в [routing] с помощью различных точек входа.
     *
     * [get] запросы отвечают тем, которые просто предоставляют информацию с сервера (без изменения состояния)
     *
     * [post] запросы меняют состояния сервера
     */
    private val httpServer = embeddedServer(Netty, host = getIpAddress(), port = 8080) {
        install(ContentNegotiation) {
            gson {
                setPrettyPrinting()
            }
        }

        routing {
            get("/example") {
                val text = call.parameters["message"]
                if (text != null) {
                    call.respondText(
                        "Этот запрос содержит параметр message c текстом $text",
                        status = HttpStatusCode.OK
                    )
                } else {
                    call.respondText("Этот запрос не содержит параметра message")
                }
            }

            /**
             * Обрабатывает запрос на логин игрока.
             * Должен проверить что был передан правильный пароль для сервера с помощью
             * [validateServersPassword], затем найти в `call.parameters` параметр "id".
             * Если такой есть, нужно зарегистрировать игрока с помощью [registerPlayer].
             * Если всё удачно, нужно сделать respondText и отправить экземпляр [Player], который вернул
             * [registerPlayer].
             *
             * В самом конце, нужно попытаться начать игру ([tryToStartGame]).
             */
            post("/login") {
                try {
                    // Validate the server password
                    validateServersPassword(call.parameters)

                    // Extract the "id" parameter
                    val id = call.parameters["id"]
                        ?: throw IllegalArgumentException("Missing 'id' parameter.")

                    // Register a new player
                    val player = registerPlayer(id)

                    // Respond with the newly registered player and password
                    call.respond(HttpStatusCode.OK, mapOf("player" to player, "password" to player.pwd))

                    // Attempt to start the game
                    tryToStartGame()
                } catch (e: Exception) {
                    respondException(call, e)
                }
                Thread.sleep(1000)
            }


            /**
             * Обрабатывает запрос на логин игрока.
             * Аналогично ищем в `call.parameters` параметр "id".
             * Если такой есть, нужно попытаться разлогинить игрока с помощью [logoutPlayer].
             * Если всё удачно, нужно сделать respondText со статусом OK
             * Иначе используйте [respondException]
             */
            post("/logout") {
                try {
                    // Extract the "id" and "password" parameters
                    val id = call.parameters["id"]
                        ?: throw IllegalArgumentException("Missing 'id' parameter.")
                    val password = call.parameters["password"]
                        ?: throw IllegalArgumentException("Missing 'password' parameter.")

                    // Logout the player
                    val success = logoutPlayer(id, password)

                    // If the player was not found or password didn't match, return an error
                    if (!success) {
                        throw Exception("No player with this ID is registered or password is incorrect.")
                    }

                    // Respond with a success message
                    call.respond(HttpStatusCode.OK, "Player logged out successfully.")
                } catch (e: Exception) {
                    respondException(call, e)
                }
            }

            /**
             * Запрос на то, чтобы поставить клетку на поле.
             * Координаты клетки -- параметры "row" и "col".
             * Возвращает BadRequest если что-то не получилось.
             */
            post("/placeCell") {

            }

            /**
             * Запрос на завершение хода.
             * Возвращает BadRequest если что-то не получилось.
             */
            post("/endPlayersTurn") {
                try {

                } catch (e: Exception) {
                    respondException(call, e)
                }
            }

            /**
             * Запрос на playerID.
             * Используется для того, чтобы игрок мог узнать очерёдность своего хода
             */
            get("/playerID") {
                try {
                    val player = validatePlayer(call.parameters)
                    val playerID = assignedIds[player]
                        ?: throw Exception("PlayerID not found for this player.")
                    call.respond(HttpStatusCode.OK, playerID)
                } catch(e: Exception){
                    respondException(call,e)
                }
            }

            get("/getWinner") {
                try {
                    // Retrieve the winner's PlayerID
                    val winnerPlayerID = game.getWinner()  // Assuming this returns PlayerID

                    // If no winner was found, return an appropriate message
                    if (winnerPlayerID == null) {
                        call.respond(HttpStatusCode.OK, "The game ended in a draw.")
                        return@get
                    }

                    // Find the player associated with the winnerPlayerID
                    val winnerPlayer = assignedIds.entries.find { (_, value) -> value == winnerPlayerID }?.key

                    // If the player is not found, return an error (this should be a rare occurrence)
                    if (winnerPlayer == null) {
                        throw Exception("Player with winning PlayerID not found.")
                    }

                    // Respond with the winner's Player.id
                    call.respond(HttpStatusCode.OK, winnerPlayer.id)
                } catch (e: Exception) {
                    respondException(call, e)
                }
            }
        }
    }

    private fun generateRandomId(): String = UUID.randomUUID().toString()

    fun start(wait: Boolean) {
        httpServer.start(wait)
    }
}