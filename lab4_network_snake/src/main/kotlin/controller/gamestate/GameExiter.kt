package onlinesnake.controller.gamestate

fun interface GameExiter {
    suspend fun exitGame()
}