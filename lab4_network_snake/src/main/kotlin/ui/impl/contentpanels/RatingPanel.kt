package onlinesnake.ui.impl.contentpanels

import arrow.core.sort
import onlinesnake.ui.dto.Player
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import java.awt.Insets
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JViewport

class RatingPanel : JPanel() {
    private val gridBagInsets = Insets(2, 2, 2, 2)
    private val ratingLabel = JLabel("Rating")
    private val contentPanel = JPanel()
    init {
        contentPanel.layout = GridLayout(0, 1)
    }
    private val ratingScrollPane = JScrollPane(contentPanel)

    init {
        ratingLabel.horizontalAlignment = JLabel.CENTER
    }

    init {
        this.layout = GridBagLayout()
    }

    init {
        val gbcRatingLabel = GridBagConstraints()
        gbcRatingLabel.gridx = 0
        gbcRatingLabel.gridy = 0
        gbcRatingLabel.gridwidth = 1
        gbcRatingLabel.gridheight = 1
        gbcRatingLabel.fill = GridBagConstraints.BOTH
        gbcRatingLabel.anchor = GridBagConstraints.NORTHWEST
        gbcRatingLabel.weightx = 100.0
        gbcRatingLabel.weighty = 5.0
        gbcRatingLabel.insets = gridBagInsets
        this.add(ratingLabel, gbcRatingLabel)
    }

    init {
        val gbcRatingScrollPane = GridBagConstraints()
        gbcRatingScrollPane.gridx = 0
        gbcRatingScrollPane.gridy = 1
        gbcRatingScrollPane.gridwidth = 1
        gbcRatingScrollPane.gridheight = 1
        gbcRatingScrollPane.fill = GridBagConstraints.BOTH
        gbcRatingScrollPane.anchor = GridBagConstraints.NORTHWEST
        gbcRatingScrollPane.weightx = 100.0
        gbcRatingScrollPane.weighty = 95.0
        gbcRatingScrollPane.insets = gridBagInsets
        this.add(ratingScrollPane, gbcRatingScrollPane)
    }

    fun updateRatings(playersList: List<Player>) {
        contentPanel.removeAll()
        val sortedPlayersList = playersList.sortedByDescending { it.score }
        for (player in sortedPlayersList) {
            val playerLabel = JLabel("${player.name}: ${player.score}" +
                    if (player.isMe) { " <- You" } else { "" } +
                    if (player.isMaster) { " <- Master" } else ""
            )
            contentPanel.add(playerLabel)
        }
        revalidate()
    }
}