package com.delta

import com.google.gson.Gson

class GameBoard(val size: Int) {
    val board: Array<Array<PlayerID?>> = Array(size) { Array(size) { null } }

    fun isValidCoordinate(x: Int, y: Int): Boolean = (x in 0 until size) and (y in 0 until size)

    /**
     * Allows to write `varName[i, j]` to get cell in position `(i, j)`.
     *
     * Example:
     *
     * ```
     * val board = GameBoard(5)
     * val a = board[1, 1] // return cell in position (1, 1)
     * ```
     *
     * To get cells inside methods of this class, use `this[i, j]`.
     */
    operator fun get(row: Int, col: Int) : PlayerID? = board.getOrNull(row)?.getOrNull(col)

    /**
     * Allows to write `varName[i, j] = ...` to set cell in position (i, j).
     *
     * Example:
     * ```
     * val board = GameBoard(5)
     * board[1, 1] = PLAYER_1
     * ```
     *
     * To set cells inside methods of this class, use `this[i, j] = ...`.
     */

    operator fun set(row: Int, col: Int, player: PlayerID?) {
        if (isValidCoordinate(row, col)) // Works as intended
            board[row][col] = player
    }

    private fun freeCell(raw: Int, col: Int) : Boolean = get(raw, col) == null //Works as intended
    fun getNeighbors(row: Int, col: Int): List<Pair<Int, Int>> {
        return listOf(
            Pair(row - 1, col),
            Pair(row + 1, col),
            Pair(row, col - 1),
            Pair(row, col + 1),
        ).filter { (x, y) -> isValidCoordinate(x, y) }
    }

 /** Returns a list of all Neighbors including corners that are valid.*/
    fun getCornersNeighbors(row: Int, col: Int): List<Pair<Int, Int>> { //Works as intended

        return listOf(
            Pair(row - 1, col),
            Pair(row + 1, col),
            Pair(row, col - 1),
            Pair(row, col + 1),
            Pair(row - 1, col - 1),
            Pair(row + 1, col + 1),
            Pair(row - 1, col + 1),
            Pair(row + 1, col - 1),
        ).filter { (x, y) -> isValidCoordinate(x, y) }
    }


    /**
     * Returns a number of friendly neighbors.
     * */
    fun countFriendlyNeighbors(row: Int, col: Int, player: PlayerID) : Int {
        return getNeighbors(row, col).count { (x, y) -> get(x, y) == player }
    }
    /**
     * Returns a number of all friendly neighbors.
     * */
    fun countFriendlyNeighborsCorners(row: Int, col: Int, player: PlayerID?): Int {
        return getCornersNeighbors(row, col).count { (x, y) -> get(x, y) == player}
    }
    /**
     * Returns a number of enemy neighbors on the borders of this tile.
     * */
    fun countEnemyNeighbors(row: Int, col: Int, player: PlayerID): Int {
        return getCornersNeighbors(row, col).count { (x, y) -> get(x, y) != player && get(x, y) != null}
    }
    /**
     * Returns a boolean if this tile is surrounded by friendly tiles.
     * */

    fun isSurroundedWithFriendly(row: Int, col: Int, player: PlayerID): Boolean {
        // Needed to add PlayerID to this function, WORKS
        return countFriendlyNeighborsCorners(row, col, player) == getCornersNeighbors(row, col).size
    }

    fun countFreeNeighbors(row: Int, col: Int): Int {
        return getNeighbors(row, col).count { (x, y) -> freeCell(x, y) }
    }
    fun isCorner(row: Int, col: Int): Boolean {
        return (row == 0 && col == 0) ||
                (row == 0 && col == size - 1) ||
                (row == size - 1 && col == 0) ||
                (row == size - 1 && col == size - 1)
    }

    fun toJson(): String = Gson().toJson(this)
    fun printMe() {
        board.forEach { row ->
            var str = ""
            row.forEach { col ->
                if (col == null) str += "."
                else str += mapOf(PlayerID.PLAYER_1 to 1, PlayerID.PLAYER_2 to 2, PlayerID.PLAYER_3 to 3, PlayerID.PLAYER_4 to 4)[col]
            }
            println(str)
        }
    }
    companion object {
        fun fromJson(json: String): GameBoard = Gson().fromJson(json, GameBoard::class.java)
    }
}