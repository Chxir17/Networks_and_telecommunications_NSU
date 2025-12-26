package onlinesnake.ui.impl.contentpanels

import onlinesnake.ui.AvailableGameSelectedListener
import onlinesnake.ui.dto.AvailableGameDto
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

class AvailableGameScrollElement(
    availableGameDto: AvailableGameDto,
    private val selectedListener: AvailableGameSelectedListener,
    private val nicknameAndServerNameProperties: NicknameAndServerNameProperties,
) : JPanel() {
    private val gameNameLabel = JLabel(availableGameDto.gameName)
    private val numOfPlayers = JLabel(availableGameDto.numOfPlayers.toString())
    private val gameFieldInfoLabel = JLabel(
        (if (availableGameDto.canJoin) "can join to " else "cannot join to ") +
                "${availableGameDto.gameConfig.foodStatic} food on " +
                "${availableGameDto.gameConfig.height}x" +
                "${availableGameDto.gameConfig.width} " +
                "with ${availableGameDto.gameConfig.stateDelayMs}MS")
    private val joinButton = JButton(">>")

    init {
        joinButton.addActionListener {
            selectedListener.onSelected(nicknameAndServerNameProperties.nickname)
        }
    }

    init {
        this.layout = GridBagLayout()
    }

    init {
        val gbcGameNameLabel = GridBagConstraints()
        gbcGameNameLabel.gridx = 0
        gbcGameNameLabel.gridy = 0
        gbcGameNameLabel.gridwidth = 1
        gbcGameNameLabel.gridheight = 1
        gbcGameNameLabel.fill = GridBagConstraints.BOTH
        gbcGameNameLabel.anchor = GridBagConstraints.NORTHWEST
        gbcGameNameLabel.weightx = 30.0
        gbcGameNameLabel.weighty = 100.0
        this.add(gameNameLabel, gbcGameNameLabel)
    }

    init {
        val gbcNumOfPlayersLabel = GridBagConstraints()
        gbcNumOfPlayersLabel.gridx = 1
        gbcNumOfPlayersLabel.gridy = 0
        gbcNumOfPlayersLabel.gridwidth = 1
        gbcNumOfPlayersLabel.gridheight = 1
        gbcNumOfPlayersLabel.fill = GridBagConstraints.BOTH
        gbcNumOfPlayersLabel.anchor = GridBagConstraints.NORTHWEST
        gbcNumOfPlayersLabel.weightx = 5.0
        gbcNumOfPlayersLabel.weighty = 100.0
        this.add(numOfPlayers, gbcNumOfPlayersLabel)
    }

    init {
        val gbcGameFieldInfoLabel = GridBagConstraints()
        gbcGameFieldInfoLabel.gridx = 2
        gbcGameFieldInfoLabel.gridy = 0
        gbcGameFieldInfoLabel.gridwidth = 1
        gbcGameFieldInfoLabel.gridheight = 1
        gbcGameFieldInfoLabel.fill = GridBagConstraints.BOTH
        gbcGameFieldInfoLabel.anchor = GridBagConstraints.NORTHWEST
        gbcGameFieldInfoLabel.weightx = 55.0
        gbcGameFieldInfoLabel.weighty = 100.0
        this.add(gameFieldInfoLabel, gbcGameFieldInfoLabel)
    }

    init {
        val gbcJoinButton = GridBagConstraints()
        gbcJoinButton.gridx = 3
        gbcJoinButton.gridy = 0
        gbcJoinButton.gridwidth = 1
        gbcJoinButton.gridheight = 1
        gbcJoinButton.fill = GridBagConstraints.BOTH
        gbcJoinButton.anchor = GridBagConstraints.NORTHWEST
        gbcJoinButton.weightx = 10.0
        gbcJoinButton.weighty = 100.0
        this.add(joinButton, gbcJoinButton)
    }

    fun updateInfo(availableGameDto: AvailableGameDto) {
        gameNameLabel.text = availableGameDto.gameName
        numOfPlayers.text = availableGameDto.numOfPlayers.toString()
        gameFieldInfoLabel.text =
            (if (availableGameDto.canJoin) "can join to " else "cannot join to ") +
                "${availableGameDto.gameName} with" +
                "${availableGameDto.gameConfig.foodStatic} food on " +
                "${availableGameDto.gameConfig.height}x" +
                "${availableGameDto.gameConfig.width} " +
                "with ${availableGameDto.gameConfig.stateDelayMs}MS"
    }
}