package onlinesnake.ui.impl.contentpanels

import onlinesnake.dto.GameConfig
import onlinesnake.ui.ExitListener
import onlinesnake.ui.NewGameListener
import java.awt.GridLayout
import javax.swing.JButton
import javax.swing.JPanel

class ButtonsPanel(
    private val nicknameAndServerNameProperties: NicknameAndServerNameProperties,
) : JPanel(), ValidationFailListener, ValidationSuccessListener {
    private val exitButton = JButton("Exit")
    private val newGameButton = JButton("New")

    private val newGameListenersList:
            MutableList<NewGameListener> = mutableListOf()
    private val exitListenersList:
            MutableList<ExitListener> = mutableListOf()

    private var verifiedGameConfig: GameConfig = GameConfig(0, 0, 0, 0)

    init {
        this.layout = GridLayout(1, 2)
    }

    init {
        this.add(exitButton)
        this.add(newGameButton)
    }

    init {
        newGameButton.isEnabled = false
    }

    init {
        newGameButton.addActionListener {
            newGameListenersList.forEach { listener ->
                listener.newGame(
                    verifiedGameConfig,
                    nicknameAndServerNameProperties.serverName,
                    nicknameAndServerNameProperties.nickname
                )
            }
        }
    }

    init {
        exitButton.addActionListener {
            exitListenersList.forEach { listener ->
                listener.exit()
            }
        }
    }

    fun addNewGameListener(listener: NewGameListener) {
        this.newGameListenersList += listener
    }

    fun addExitListener(listener: ExitListener) {
        this.exitListenersList += listener
    }

    override fun validationSuccess(gameConfig: GameConfig) {
        verifiedGameConfig = gameConfig
        newGameButton.isEnabled = true
    }

    override fun validationFail() {
        newGameButton.isEnabled = false
    }
}