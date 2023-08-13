package com.delta

import com.google.gson.Gson
import java.util.*
import kotlin.math.max

class Tilous(private val board: GameBoard) {
    private val playersResources = PlayerID.values().associateWith { 1 }.toMutableMap()
    private val playersStates = PlayerID.values().associateWith { PlayerState.PLAYING }.toMutableMap()

    private val nextPlayersMap: Map<PlayerID, PlayerID> = mapOf() //TODO change it!
    var currentPlayer = PlayerID.PLAYER_1
        private set
    var gameIsOver = false
        private set

    init {
        board.set(0, 0, PlayerID.PLAYER_1)
        board.set(0, board.size - 1, PlayerID.PLAYER_2)
        board.set(board.size - 1, 0, PlayerID.PLAYER_4)
        board.set(board.size - 1, board.size - 1, PlayerID.PLAYER_3)
    }

    // Info about board
    fun getCell(row: Int, col: Int): PlayerID? = board[row, col]
    fun getBoardSize(): Int = board.size

    // Info about players
    fun getPlayerResources() = playersResources.toMap()
    fun getPlayerStates() = playersStates.toMap()
    fun isInGame(): List<PlayerID> = playersStates.filter { it.value == PlayerState.PLAYING }.keys.toList()
    fun getNextPlayer(): PlayerID {
        val currentIndex = isInGame().indexOf(currentPlayer)
        val nextIndex = (currentIndex + 1) % isInGame().size
        return isInGame()[nextIndex]
    }

    fun getWinner(): PlayerID? {
        val playingPlayers = playersStates.filterValues { it == PlayerState.PLAYING }.keys.toList()
        val lostPlayers = playersStates.filterValues { it == PlayerState.LOST }.keys.toList()

        return when {
            playingPlayers.isEmpty() -> null // No player is playing, no winner
            playingPlayers.size == 1 -> {
                // Only one player remaining, that player wins
                val winner = playingPlayers[0]
                playersStates[winner] = PlayerState.WON
                gameIsOver = true
                winner
            }

            else -> {
                // Check if all players except one have lost
                val remainingPlayers = playingPlayers - lostPlayers
                if (remainingPlayers.size == 1) {
                    val winner = remainingPlayers[0]
                    playersStates[winner] = PlayerState.WON
                    gameIsOver = true
                    winner
                } else {
                    null // More than one player remaining, no winner yet
                }
            }
        }
    }

    fun addResources(player: PlayerID): Int {
        return 1 + countProductiveCells(player)
    }

    // Game checks and info
    fun isValidCellToPlace(row: Int, col: Int, player: PlayerID): Boolean {
        if (
            player != currentPlayer ||
            board.countFriendlyNeighbors(row, col, player) == 0 ||
            getCell(row, col) == player
            ) {
            return false
        } else if (getPlayerResources()[player] == 0) {
            return false
        } else if (getCell(row, col) == null) {
            return true
        } else {
            if (getEnemyDefense(row, col) <= getPlayerResources()[player]!!) {
                return true
            } else {
                return false
            }
        }
    }

    private fun getEnemyDefense(row: Int, col: Int): Int {
        val enemy = getCell(row, col) ?: return 1
        val enemyForce = board.countFriendlyNeighborsCorners(row, col, enemy)
        return (1 + max(0, enemyForce - 2))
    }

    fun isProductive(row: Int, col: Int, player: PlayerID): Boolean {
        val cellOwner = getCell(row, col)
        return player == cellOwner &&
                cellOwner.let { board.countFriendlyNeighbors(row, col, it) } == 1
    }

    fun isProductive(row: Int, col: Int): Boolean {
        val cellOwner = getCell(row, col)
        return cellOwner?.let { board.countFriendlyNeighbors(row, col, it) } == 1
    }

    fun isSuperStable(row: Int, col: Int): Boolean {
        val curr = getCell(row, col) ?: return false
        return (row == 0 || row == getBoardSize() - 1) && (col == 0 || col == getBoardSize() - 1)
                || board.countFriendlyNeighborsCorners(row, col, curr) == 8
    }

    fun getDefencePoints(row: Int, col: Int): Int {
        val curr = getCell(row, col) ?: return 1
        return Math.max(0, board.countFriendlyNeighborsCorners(row, col, curr) - 2) + 1
    }

    // What's the condition? -George
    private fun countCellsWithCondition(condition: (Int, Int) -> Boolean): Int = TODO()
    fun countProductiveCells(player: PlayerID): Int {
        var totalProductiveCells = 0
        for (x in 0 until board.size) {
            for (y in 0 until board.size) {
                if (isProductive(x, y, player)) {
                    totalProductiveCells++
                }
            }
        }
        return totalProductiveCells
    }

    fun countFreeCells(): Int {
        var totalFreeCells = 0
        for (x in 0 until board.size) {
            for (y in 0 until board.size) {
                if (getCell(x, y) == null) {
                    totalFreeCells++
                }
            }
        }
        return totalFreeCells
    }

    fun countFriendlyCells(player: PlayerID): Int {
        var totalFriendlyCells = 0
        for (x in 0 until board.size) {
            for (y in 0 until board.size) {
                if (getCell(x, y) == player) {
                    totalFriendlyCells++
                }
            }
        }
        return totalFriendlyCells
    }

    fun countFriendlyNeighboursLisa(row: Int, col: Int, player: PlayerID): Int {
        var friendlyNeighbours = 0
        if ((row != 0) && (getCell(row - 1, col) == player))
            friendlyNeighbours += 1
        if ((row != (board.size - 1)) && (getCell(row + 1, col) == player))
            friendlyNeighbours += 1
        if ((col != 0) && (getCell(row, col - 1) == player))
            friendlyNeighbours += 1
        if ((col != (board.size - 1)) && (getCell(row, col + 1) == player))
            friendlyNeighbours += 1
        return friendlyNeighbours
    }

    fun getSuperStableCells(player: PlayerID): List<Pair<Int, Int>> = TODO("Later...")
    fun getUnstableCells(player: PlayerID): List<Pair<Int, Int>> = TODO("Later...")
    fun getStableCells(player: PlayerID): List<Pair<Int, Int>> = TODO("Later...")

    /**
     * This is a main function!
     *
     * Check if game is over
     * If not -- check if player can place a cell here
     * Don't forget about resources
     * If everything is ok, place the cell AND update everything:
     * player's resources, board state and so on.
     * Use already implemented methods.
     *
     *
     * @return 'true' is the cell was successfully placed, 'false' if not
     */
    fun placeCell(row: Int, col: Int, player: PlayerID): Boolean {
        if (player != currentPlayer)
            return false
        if (isValidCellToPlace(row, col, player) == false)
            return false
        if (getCell(row, col) == null) {
            board.set(row, col, player)
            playersResources[player] = playersResources[player]!! - 1
            checkEndGameCondition()
            return true
        } else {
            val defence = getEnemyDefense(row, col)
            board.set(row, col, player)
            playersResources[player] = playersResources[player]!! - defence
            removeUnstableCells()
            checkEndGameCondition()
            return true
        }
    }

    /**
     * Another main function!
     *
     * Handles request of a player to finish theirs turn.
     * We have to check here if the game was over,
     * update resources and
     * change current player
     *
     * @return 'true' if succeeded
     */
    fun finishPlayersTurn(player: PlayerID): Boolean {
        if (currentPlayer == player) {
            currentPlayer = getNextPlayer()
            val res = playersResources[player] ?: 0
            playersResources[player] = res + countProductiveCells(player) + 1
            return true
        } else {
            return false
        }
    }

    // Internal game actions
    fun removeUnstableCells() {
        val bsz = getBoardSize()
        val markers = GameBoard(bsz)

        for (row in 0 until getBoardSize()) {
            for (col in 0 until getBoardSize()) {
                val baseOwner = getCell(row, col)
                if (baseOwner == null || !isSuperStable(row, col)) continue

                val queue: LinkedList<Pair<Int, Int>> = LinkedList()
                markers[row, col] = baseOwner
                queue.add(Pair(row, col))
                while (queue.size > 0) {
                    val (currRow, currCol) = queue.first()
                    queue.remove()

                    // Checking adjacent elements
                    for (ai in -1..1) {
                        for (aj in -1..1) {
                            if ((ai + aj) % 2 == 0) continue
                            val adjRow = currRow + ai
                            val adjCol = currCol + aj

                            if (markers[adjRow, adjCol] != baseOwner && getCell(adjRow, adjCol) == baseOwner) {
                                markers[adjRow, adjCol] = baseOwner
                                queue.add(Pair(adjRow, adjCol))
                            }
                        }
                    }
                }
            }
        }

        //board.printMe()
        //println("")

        for (i in 0 until getBoardSize()) {
            for (j in 0 until getBoardSize()) {
                if (markers[i, j] == null) board.set(i, j, null);
            }
        }
        //board.printMe()

    }

    private fun updatePlayerStates(): Nothing = TODO()

    fun toJson() = Gson().toJson(this)

    private fun checkEndGameCondition(): Unit {
        val n1 = countFriendlyCells(PlayerID.PLAYER_1)
        val n2 = countFriendlyCells(PlayerID.PLAYER_2)
        val n3 = countFriendlyCells(PlayerID.PLAYER_3)
        val n4 = countFriendlyCells(PlayerID.PLAYER_4)

        for (player in playersStates.keys) {
            if (countFriendlyCells(player) == 0) {
                playersStates[player] = PlayerState.LOST
            }
        }

        if ((n1 != 0) && (n2 == 0) && (n3 == 0) && (n4 == 0))
            gameIsOver = true
        if ((n1 == 0) && (n2 != 0) && (n3 == 0) && (n4 == 0))
            gameIsOver = true
        if ((n1 == 0) && (n2 == 0) && (n3 != 0) && (n4 == 0))
            gameIsOver = true
        if ((n1 == 0) && (n2 == 0) && (n3 == 0) && (n4 != 0))
            gameIsOver = true
    }
}
