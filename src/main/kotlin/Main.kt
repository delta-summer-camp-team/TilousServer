import com.delta.GameBoard
import com.delta.Tilous

fun main(args: Array<String>) {
    val testGame = Tilous(GameBoard(5))
    println(testGame.getBoardSize())
}