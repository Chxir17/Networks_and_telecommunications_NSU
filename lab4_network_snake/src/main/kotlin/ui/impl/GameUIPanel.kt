package onlinesnake.ui.impl

import onlinesnake.ui.*
import onlinesnake.ui.dto.AvailableGameDto
import onlinesnake.ui.dto.AvailableGameKey
import onlinesnake.ui.dto.UpdateGameDto
import onlinesnake.ui.impl.contentpanels.*
import java.awt.Color
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JPanel

class GameUIPanel : JPanel() {
    private val gameFieldPanel = GameFieldPanel()
    private val ratingPanel = RatingPanel()
    private val nicknameAndServerNamePropertiesPanel = NicknameAndServerNamePropertiesPanel()
    private val buttonsPanel = ButtonsPanel(nicknameAndServerNamePropertiesPanel)
    private val gameConfigPanel = GameConfigPanel()
    private val availableGamesPanel = AvailableGamesPanel(nicknameAndServerNamePropertiesPanel)
    private val currentGameInfoPanel = CurrentGameInfoPanel()
    private val gridBagInsets = Insets(2, 2, 2, 2)

    init {
        this.layout = GridBagLayout()
    }

    // Game Field Panel configuration
    init {
        val gbcGameFieldPanel = GridBagConstraints()
        gbcGameFieldPanel.gridx = 0
        gbcGameFieldPanel.gridy = 0
        gbcGameFieldPanel.gridwidth = 1
        gbcGameFieldPanel.gridheight = 5
        gbcGameFieldPanel.fill = GridBagConstraints.BOTH
        gbcGameFieldPanel.anchor = GridBagConstraints.NORTHWEST
        gbcGameFieldPanel.weightx = 70.0
        gbcGameFieldPanel.weighty = 90.0
        gbcGameFieldPanel.ipadx = 0
        gbcGameFieldPanel.ipady = 0
        gbcGameFieldPanel.insets = gridBagInsets
        this.add(gameFieldPanel, gbcGameFieldPanel)
    }

    // Rating Panel configuration
    init {
        val gbcRatingPanel = GridBagConstraints()
        gbcRatingPanel.gridx = 1
        gbcRatingPanel.gridy = 0
        gbcRatingPanel.gridwidth = 1
        gbcRatingPanel.gridheight = 1
        gbcRatingPanel.fill = GridBagConstraints.BOTH
        gbcRatingPanel.anchor = GridBagConstraints.NORTHWEST
        gbcRatingPanel.weightx = 30.0
        gbcRatingPanel.weighty = 43.0
        gbcRatingPanel.ipadx = 0
        gbcRatingPanel.ipady = 0
        gbcRatingPanel.insets = gridBagInsets
        this.add(ratingPanel, gbcRatingPanel)
    }

    // Buttons Panel configuration
    init {
        val gbcButtonsPanel = GridBagConstraints()
        gbcButtonsPanel.gridx = 1
        gbcButtonsPanel.gridy = 1
        gbcButtonsPanel.gridwidth = 1
        gbcButtonsPanel.gridheight = 1
        gbcButtonsPanel.fill = GridBagConstraints.BOTH
        gbcButtonsPanel.anchor = GridBagConstraints.NORTHWEST
        gbcButtonsPanel.weightx = 30.0
        gbcButtonsPanel.weighty = 2.0
        gbcButtonsPanel.ipadx = 0
        gbcButtonsPanel.ipady = 0
        gbcButtonsPanel.insets = gridBagInsets
        this.add(buttonsPanel, gbcButtonsPanel)
    }

    init {
        gameConfigPanel.addValidationFailListener(buttonsPanel)
        gameConfigPanel.addValidationSuccessListener(buttonsPanel)
    }

    // Nickname and Server Name panel
    init {
        val gbcNicknameAndServerNamePropertiesPanel = GridBagConstraints()
        gbcNicknameAndServerNamePropertiesPanel.gridx = 1
        gbcNicknameAndServerNamePropertiesPanel.gridy = 2
        gbcNicknameAndServerNamePropertiesPanel.gridwidth = 1
        gbcNicknameAndServerNamePropertiesPanel.gridheight = 1
        gbcNicknameAndServerNamePropertiesPanel.fill = GridBagConstraints.BOTH
        gbcNicknameAndServerNamePropertiesPanel.anchor = GridBagConstraints.NORTHWEST
        gbcNicknameAndServerNamePropertiesPanel.weightx = 30.0
        gbcNicknameAndServerNamePropertiesPanel.weighty = 7.0
        gbcNicknameAndServerNamePropertiesPanel.insets = gridBagInsets
        this.add(nicknameAndServerNamePropertiesPanel, gbcNicknameAndServerNamePropertiesPanel)
    }

    // Game Config panel
    init {
        val gbcGameConfigPanel = GridBagConstraints()
        gbcGameConfigPanel.gridx = 1
        gbcGameConfigPanel.gridy = 3
        gbcGameConfigPanel.gridwidth = 1
        gbcGameConfigPanel.gridheight = 1
        gbcGameConfigPanel.fill = GridBagConstraints.BOTH
        gbcGameConfigPanel.anchor = GridBagConstraints.NORTHWEST
        gbcGameConfigPanel.weightx = 30.0
        gbcGameConfigPanel.weighty = 8.0
        gbcGameConfigPanel.ipadx = 0
        gbcGameConfigPanel.ipady = 0
        gbcGameConfigPanel.insets = gridBagInsets
        this.add(gameConfigPanel, gbcGameConfigPanel)
    }

    // Available Games list
    init {
        val gbcAvailableGamesPanel = GridBagConstraints()
        gbcAvailableGamesPanel.gridx = 1
        gbcAvailableGamesPanel.gridy = 4
        gbcAvailableGamesPanel.gridwidth = 1
        gbcAvailableGamesPanel.gridheight = 2
        gbcAvailableGamesPanel.fill = GridBagConstraints.BOTH
        gbcAvailableGamesPanel.anchor = GridBagConstraints.NORTHWEST
        gbcAvailableGamesPanel.weightx = 30.0
        gbcAvailableGamesPanel.weighty = 40.0
        gbcAvailableGamesPanel.ipadx = 0
        gbcAvailableGamesPanel.ipady = 0
        gbcAvailableGamesPanel.insets = gridBagInsets
        this.add(availableGamesPanel, gbcAvailableGamesPanel)
    }

    // Current Game Info panel
    init {
        val gbcCurrentGameInfoPanel = GridBagConstraints()
        gbcCurrentGameInfoPanel.gridx = 0
        gbcCurrentGameInfoPanel.gridy = 5
        gbcCurrentGameInfoPanel.gridwidth = 1
        gbcCurrentGameInfoPanel.gridheight = 1
        gbcCurrentGameInfoPanel.fill = GridBagConstraints.BOTH
        gbcCurrentGameInfoPanel.anchor = GridBagConstraints.NORTHWEST
        gbcCurrentGameInfoPanel.weightx = 70.0
        gbcCurrentGameInfoPanel.weighty = 10.0
        gbcCurrentGameInfoPanel.insets = gridBagInsets
        this.add(currentGameInfoPanel, gbcCurrentGameInfoPanel)
    }

//    init {
//        gameFieldPanel.background = Color.BLACK
//        ratingPanel.background = Color.BLUE
//        buttonsPanel.background = Color.ORANGE
//        availableGamesPanel.background = Color.RED
//        currentGameInfoPanel.background = Color.GREEN
//    }

    fun updateField(updateGameDto: UpdateGameDto) {
        this.gameFieldPanel.updateField(
            snakesList = updateGameDto.snakesList,
            foodList = updateGameDto.foodList,
            myID = updateGameDto.myID,
            fieldWidth = updateGameDto.gameConfig.width,
            fieldHeight = updateGameDto.gameConfig.height,
        )
        this.currentGameInfoPanel.updateCurrentGameInfo(
            fieldWidth = updateGameDto.gameConfig.width,
            fieldHeight = updateGameDto.gameConfig.height,
            foodOnField = updateGameDto.foodList.size,
            ownerName = updateGameDto.masterName,
            stateOrder = updateGameDto.stateOrder,
        )
        this.ratingPanel.updateRatings(updateGameDto.players)
    }

    fun addNewGameListener(listener: NewGameListener) {
        buttonsPanel.addNewGameListener(listener)
    }

    fun addExitListener(listener: ExitListener) {
        buttonsPanel.addExitListener(listener)
    }

    fun addWidthValidationRule(validationRule: WidthValidationRule) {
        gameConfigPanel.addWidthValidationRule(validationRule)
    }

    fun addHeightValidationRule(validationRule: HeightValidationRule) {
        gameConfigPanel.addHeightValidationRule(validationRule)
    }

    fun addFoodStaticValidationRule(validationRule: FoodStaticValidationRule) {
        gameConfigPanel.addFoodStaticValidationRule(validationRule)
    }

    fun addStateDelayMsValidationRule(validationRule: StateDelayMsValidationRule) {
        gameConfigPanel.addStateDelayMsValidationRule(validationRule)
    }

    fun addAvailableGame(availableGameDto: AvailableGameDto, selectedListener: AvailableGameSelectedListener): AvailableGameKey {
        return availableGamesPanel.addAvailableGame(availableGameDto, selectedListener)
    }

    fun removeAvailableGame(key: AvailableGameKey) {
        availableGamesPanel.removeAvailableGame(key)
    }

    fun updateAvailableGame(availableGameDto: AvailableGameDto, key: AvailableGameKey) {
        availableGamesPanel.updateAvailableGame(availableGameDto, key)
    }
}
