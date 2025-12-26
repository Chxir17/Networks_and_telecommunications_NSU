package onlinesnake.ui.impl

import onlinesnake.dto.Direction
import onlinesnake.ui.*
import onlinesnake.ui.dto.AvailableGameDto
import onlinesnake.ui.dto.AvailableGameKey
import onlinesnake.ui.dto.UpdateGameDto
import onlinesnake.ui.impl.GameUIPanel
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.FutureTask
import javax.swing.JFrame
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

class GameFrame : JFrame(), GameUI {
    private val mainFrameName = "OnlineSnake"
    private val gameUIPanel = GameUIPanel()
    private val applicationCloseListenersList: MutableList<ApplicationCloseListener> = mutableListOf()
    private val newDirectionListenersList = mutableListOf<NewDirectionListener>()

    init {
        this.title = mainFrameName
        this.contentPane.add(gameUIPanel)
        this.defaultCloseOperation = EXIT_ON_CLOSE
        this.pack()
        this.setLocationRelativeTo(null)
        this.size = Dimension(400, 300)
    }

    init {
        addWindowListener(object: WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                super.windowClosing(e)
                applicationCloseListenersList.forEach { it.onClose() }
            }
        })
    }

    init {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .addKeyEventDispatcher {
                val keyCode = it!!.keyCode
                val direction = when (keyCode) {
                    KeyEvent.VK_LEFT, KeyEvent.VK_A -> Direction.LEFT
                    KeyEvent.VK_DOWN, KeyEvent.VK_S -> Direction.DOWN
                    KeyEvent.VK_RIGHT, KeyEvent.VK_D -> Direction.RIGHT
                    KeyEvent.VK_UP, KeyEvent.VK_W -> Direction.UP
                    else -> null
                }
                if (direction != null) {
                    println("HERERERERERER DIRE ${newDirectionListenersList.size}")
                    newDirectionListenersList.forEach { listener -> listener.newDirection(direction) }
                }
                false
            }
    }

    override fun start() {
        this.isVisible = true
    }

    override fun updateField(updateGameDto: UpdateGameDto) =
        SwingUtilities.invokeLater {
            gameUIPanel.updateField(updateGameDto)
        }

    override fun addNewGameListener(listener: NewGameListener) =
        SwingUtilities.invokeLater {
            gameUIPanel.addNewGameListener(listener)
        }

    override fun addExitListener(listener: ExitListener) =
        SwingUtilities.invokeLater {
            gameUIPanel.addExitListener(listener)
        }

    override fun addWidthValidationRule(validationRule: WidthValidationRule) =
        SwingUtilities.invokeLater {
            gameUIPanel.addWidthValidationRule(validationRule)
        }

    override fun addHeightValidationRule(validationRule: HeightValidationRule) =
        SwingUtilities.invokeLater {
            gameUIPanel.addHeightValidationRule(validationRule)
        }

    override fun addFoodStaticValidationRule(validationRule: FoodStaticValidationRule) =
        SwingUtilities.invokeLater {
            gameUIPanel.addFoodStaticValidationRule(validationRule)
        }

    override fun addStateDelayMsValidationRule(validationRule: StateDelayMsValidationRule) =
        SwingUtilities.invokeLater {
            gameUIPanel.addStateDelayMsValidationRule(validationRule)
        }

    override fun addApplicationCloseListener(listener: ApplicationCloseListener) {
        SwingUtilities.invokeLater {
            this.applicationCloseListenersList += listener
        }
    }

    override fun addAvailableGame(availableGameDto: AvailableGameDto, selectedListener: AvailableGameSelectedListener): AvailableGameKey {
        val task = FutureTask {
            this.gameUIPanel.addAvailableGame(availableGameDto, selectedListener)
        }
        SwingUtilities.invokeLater(task)
        return task.get()
    }

    override fun removeAvailableGame(key: AvailableGameKey) {
        SwingUtilities.invokeLater {
            this.gameUIPanel.removeAvailableGame(key)
        }
    }

    override fun updateAvailableGame(availableGameDto: AvailableGameDto, key: AvailableGameKey) {
        SwingUtilities.invokeLater {
            this.gameUIPanel.updateAvailableGame(availableGameDto, key)
        }
    }

    override fun addNewDirectionListener(listener: NewDirectionListener) {
        SwingUtilities.invokeLater {
            this.newDirectionListenersList += listener
        }
    }

    override fun showError(title: String, message: String) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE)
    }
}