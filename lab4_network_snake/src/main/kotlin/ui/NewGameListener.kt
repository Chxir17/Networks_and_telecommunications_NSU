package onlinesnake.ui

import onlinesnake.dto.GameConfig

fun interface NewGameListener {
    fun newGame(gameConfig: GameConfig, gameName: String, playerName: String)
}