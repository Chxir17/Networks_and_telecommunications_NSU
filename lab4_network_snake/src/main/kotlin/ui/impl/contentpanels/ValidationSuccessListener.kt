package onlinesnake.ui.impl.contentpanels

import onlinesnake.dto.GameConfig


fun interface ValidationSuccessListener {
    fun validationSuccess(gameConfig: GameConfig)
}