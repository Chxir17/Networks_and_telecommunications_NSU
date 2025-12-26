package onlinesnake.ui

import onlinesnake.ui.dto.AvailableGameDto
import onlinesnake.ui.dto.AvailableGameKey
import onlinesnake.ui.dto.UpdateGameDto

typealias WidthValidationRule = IntValidationRule
typealias HeightValidationRule = IntValidationRule
typealias FoodStaticValidationRule = IntValidationRule
typealias StateDelayMsValidationRule = IntValidationRule

interface GameUI {
    fun start()
    fun updateField(updateGameDto: UpdateGameDto)
    fun addNewGameListener(listener: NewGameListener)
    fun addExitListener(listener: ExitListener)
    fun addWidthValidationRule(validationRule: WidthValidationRule)
    fun addHeightValidationRule(validationRule: HeightValidationRule)
    fun addFoodStaticValidationRule(validationRule: FoodStaticValidationRule)
    fun addStateDelayMsValidationRule(validationRule: StateDelayMsValidationRule)
    fun addApplicationCloseListener(listener: ApplicationCloseListener)
    fun addAvailableGame(availableGameDto: AvailableGameDto, selectedListener: AvailableGameSelectedListener) : AvailableGameKey
    fun removeAvailableGame(key: AvailableGameKey)
    fun updateAvailableGame(availableGameDto: AvailableGameDto, key: AvailableGameKey)
    fun addNewDirectionListener(listener: NewDirectionListener)
    fun showError(title: String, message: String)
}