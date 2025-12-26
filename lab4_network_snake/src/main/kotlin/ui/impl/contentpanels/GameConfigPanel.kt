package onlinesnake.ui.impl.contentpanels

import onlinesnake.dto.GameConfig
import onlinesnake.ui.*
import java.awt.Color
import java.awt.GridLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

class GameConfigPanel : JPanel() {
    private val widthLabel = JLabel("Width:")
    private val heightLabel = JLabel("Height:")
    private val foodStaticLabel = JLabel("Food:")
    private val stateDelayMsLabel = JLabel("ms:")

    private val widthTextField = JTextField()
    private val heightTextField = JTextField()
    private val foodStaticTextField = JTextField()
    private val stateDelayMsTextField = JTextField()

    private var fieldWidth = 0
    private var fieldHeight = 0
    private var foodStatic = 0
    private var stateDelayMs = 0

    private var fieldWidthVerified = false
    private var fieldHeightVerified = false
    private var foodStaticVerified = false
    private var stateDelayMsVerified = false

    private val widthValidationRulesList:
            MutableList<WidthValidationRule> = mutableListOf()
    private val heightValidationRulesList:
            MutableList<HeightValidationRule> = mutableListOf()
    private val foodStaticValidationRulesList:
            MutableList<FoodStaticValidationRule> = mutableListOf()
    private val stateDelayMsValidationRulesList:
            MutableList<StateDelayMsValidationRule> = mutableListOf()

    private val validationFailListenersList:
            MutableList<ValidationFailListener> = mutableListOf()
    private val validationSuccessListenersList:
            MutableList<ValidationSuccessListener> = mutableListOf()

    init {
        this.layout = GridLayout(2, 4)
    }

    init {
        this.add(widthLabel)
        this.add(heightLabel)
        this.add(foodStaticLabel)
        this.add(stateDelayMsLabel)
        this.add(widthTextField)
        this.add(heightTextField)
        this.add(foodStaticTextField)
        this.add(stateDelayMsTextField)
    }

    init {
        this.widthTextField.addKeyListener(TextFieldKeyAdapter(
            validationRulesList = widthValidationRulesList,
            textField = widthTextField,
            onFailure = {
                fieldWidthVerified = false
                validationFailListenersList.forEach { it.validationFail() }
            },
            onSuccess = { property ->
                fieldWidth = property
                fieldWidthVerified = true
                if (fieldHeightVerified && foodStaticVerified && stateDelayMsVerified) {
                    validationSuccessListenersList.forEach {
                        it.validationSuccess(
                            GameConfig(
                                fieldWidth,
                                fieldHeight,
                                foodStatic,
                                stateDelayMs,
                            )
                        )
                    }
                }
            }
        ))
    }

    init {
        this.heightTextField.addKeyListener(TextFieldKeyAdapter(
            validationRulesList = heightValidationRulesList,
            textField = heightTextField,
            onFailure = {
                fieldHeightVerified = false
                validationFailListenersList.forEach { it.validationFail() }
            },
            onSuccess = { property ->
                fieldHeight = property
                fieldHeightVerified = true
                if (fieldWidthVerified && foodStaticVerified && stateDelayMsVerified) {
                    validationSuccessListenersList.forEach {
                        it.validationSuccess(
                            GameConfig(
                                fieldWidth,
                                fieldHeight,
                                foodStatic,
                                stateDelayMs,
                            )
                        )
                    }
                }
            }
        ))
    }

    init {
        this.foodStaticTextField.addKeyListener(TextFieldKeyAdapter(
            validationRulesList = foodStaticValidationRulesList,
            textField = foodStaticTextField,
            onFailure = {
                foodStaticVerified = false
                validationFailListenersList.forEach { it.validationFail() }
            },
            onSuccess = { property ->
                foodStatic = property
                foodStaticVerified = true
                if (fieldWidthVerified && fieldHeightVerified && stateDelayMsVerified) {
                    validationSuccessListenersList.forEach {
                        it.validationSuccess(
                            GameConfig(
                                fieldWidth,
                                fieldHeight,
                                foodStatic,
                                stateDelayMs,
                            )
                        )
                    }
                }
            }
        ))
    }

    init {
        this.stateDelayMsTextField.addKeyListener(TextFieldKeyAdapter(
            validationRulesList = stateDelayMsValidationRulesList,
            textField = stateDelayMsTextField,
            onFailure = {
                stateDelayMsVerified = false
                validationFailListenersList.forEach { it.validationFail() }
            },
            onSuccess = { property ->
                stateDelayMs = property
                stateDelayMsVerified = true
                if (fieldWidthVerified && fieldHeightVerified && foodStaticVerified) {
                    validationSuccessListenersList.forEach {
                        it.validationSuccess(
                            GameConfig(
                                fieldWidth,
                                fieldHeight,
                                foodStatic,
                                stateDelayMs,
                            )
                        )
                    }
                }
            }
        ))
    }

    fun addWidthValidationRule(validationRule: WidthValidationRule) : Int {
        this.widthValidationRulesList += validationRule
        return this.widthValidationRulesList.size - 1
    }

    fun addHeightValidationRule(validationRule: HeightValidationRule) : Int {
        this.heightValidationRulesList += validationRule
        return this.heightValidationRulesList.size - 1
    }

    fun addFoodStaticValidationRule(validationRule: FoodStaticValidationRule) : Int {
        this.foodStaticValidationRulesList += validationRule
        return this.foodStaticValidationRulesList.size - 1
    }

    fun addStateDelayMsValidationRule(validationRule: StateDelayMsValidationRule) : Int {
        this.stateDelayMsValidationRulesList += validationRule
        return this.stateDelayMsValidationRulesList.size - 1
    }

    fun addValidationFailListener(validationFailListener: ValidationFailListener) : Int {
        this.validationFailListenersList += validationFailListener
        return this.validationFailListenersList.size - 1
    }

    fun addValidationSuccessListener(validationSuccessListener: ValidationSuccessListener) : Int {
        this.validationSuccessListenersList += validationSuccessListener
        return this.validationSuccessListenersList.size - 1
    }
}

private class TextFieldKeyAdapter(
    private val validationRulesList: List<IntValidationRule>,
    private val textField: JTextField,
    private val onSuccess: (property: Int) -> Unit,
    private val onFailure: () -> Unit,
) : KeyAdapter() {
    override fun keyReleased(e: KeyEvent?) {
        super.keyReleased(e)
        val text = textField.text
        val property = text.toIntOrNull()
        if (property == null) {
            validationFailed()
            return
        }
        var validationFlag = true
        validationRulesList.forEach {
            if (!it.validate(property)) {
                validationFlag = false
                return@forEach
            }
        }
        if (validationFlag) {
            validationSucceed(property)
        } else {
            validationFailed()
        }
    }

    private fun validationFailed() {
        textField.background = Color(0xf0, 0x50, 0x30)
        onFailure()
    }

    private fun validationSucceed(property: Int) {
        textField.background = Color.WHITE
        onSuccess(property)
    }
}

//    init {
//        this.layout = GridBagLayout()
//    }
//
//    //-------
//    // LABELS
//    //-------
//    // WIDTH LABEL
//    init {
//        val gbcWidthLabel = GridBagConstraints()
//        gbcWidthLabel.gridx = 0
//        gbcWidthLabel.gridy = 0
//        gbcWidthLabel.gridwidth = 1
//        gbcWidthLabel.gridheight = 1
//        gbcWidthLabel.fill = GridBagConstraints.BOTH
//        gbcWidthLabel.anchor = GridBagConstraints.NORTHWEST
//        gbcWidthLabel.weightx = 25.0
//        gbcWidthLabel.weighty = 50.0
//        this.add(widthLabel, gbcWidthLabel)
//    }
//
//    // HEIGHT LABEL
//    init {
//        val gbcHeightLabel = GridBagConstraints()
//        gbcHeightLabel.gridx = 1
//        gbcHeightLabel.gridy = 0
//        gbcHeightLabel.gridwidth = 1
//        gbcHeightLabel.gridheight = 1
//        gbcHeightLabel.fill = GridBagConstraints.BOTH
//        gbcHeightLabel.anchor = GridBagConstraints.NORTHWEST
//        gbcHeightLabel.weightx = 25.0
//        gbcHeightLabel.weighty = 50.0
//        this.add(heightLabel, gbcHeightLabel)
//    }
//
//    // FOOD STATIC LABEL
//    init {
//        val gbcFoodStaticLabel = GridBagConstraints()
//        gbcFoodStaticLabel.gridx = 2
//        gbcFoodStaticLabel.gridy = 0
//        gbcFoodStaticLabel.gridwidth = 1
//        gbcFoodStaticLabel.gridheight = 1
//        gbcFoodStaticLabel.fill = GridBagConstraints.BOTH
//        gbcFoodStaticLabel.anchor = GridBagConstraints.NORTHWEST
//        gbcFoodStaticLabel.weightx = 25.0
//        gbcFoodStaticLabel.weighty = 50.0
//        this.add(foodStaticLabel, gbcFoodStaticLabel)
//    }
//
//    // STATE DELAY MS LABEL
//    init {
//        val gbcStateDelayMsLabel = GridBagConstraints()
//        gbcStateDelayMsLabel.gridx = 3
//        gbcStateDelayMsLabel.gridy = 0
//        gbcStateDelayMsLabel.gridwidth = 1
//        gbcStateDelayMsLabel.gridheight = 1
//        gbcStateDelayMsLabel.fill = GridBagConstraints.BOTH
//        gbcStateDelayMsLabel.anchor = GridBagConstraints.NORTHWEST
//        gbcStateDelayMsLabel.weightx = 25.0
//        gbcStateDelayMsLabel.weighty = 50.0
//        this.add(stateDelayMsLabel, gbcStateDelayMsLabel)
//    }
//
//    //------------
//    // TEXT FIELDS
//    //------------
//    // WIDTH TEXT FIELD
//    init {
//        val gbcWidthTextField = GridBagConstraints()
//        gbcWidthTextField.gridx = 0
//        gbcWidthTextField.gridy = 1
//        gbcWidthTextField.gridwidth = 1
//        gbcWidthTextField.gridheight = 1
//        gbcWidthTextField.fill = GridBagConstraints.BOTH
//        gbcWidthTextField.anchor = GridBagConstraints.NORTHWEST
//        gbcWidthTextField.weightx = 25.0
//        gbcWidthTextField.weighty = 50.0
//        this.add(widthTextField, gbcWidthTextField)
//    }
//
//    // HEIGHT TEXT FIELD
//    init {
//        val gbcHeightTextField = GridBagConstraints()
//        gbcHeightTextField.gridx = 1
//        gbcHeightTextField.gridy = 1
//        gbcHeightTextField.gridwidth = 1
//        gbcHeightTextField.gridheight = 1
//        gbcHeightTextField.fill = GridBagConstraints.BOTH
//        gbcHeightTextField.anchor = GridBagConstraints.NORTHWEST
//        gbcHeightTextField.weightx = 25.0
//        gbcHeightTextField.weighty = 50.0
//        this.add(heightTextField, gbcHeightTextField)
//    }
//
//    // FOOD STATIC TEXT FIELD
//    init {
//        val gbcFoodStaticTextField = GridBagConstraints()
//        gbcFoodStaticTextField.gridx = 2
//        gbcFoodStaticTextField.gridy = 1
//        gbcFoodStaticTextField.gridwidth = 1
//        gbcFoodStaticTextField.gridheight = 1
//        gbcFoodStaticTextField.fill = GridBagConstraints.BOTH
//        gbcFoodStaticTextField.anchor = GridBagConstraints.NORTHWEST
//        gbcFoodStaticTextField.weightx = 25.0
//        gbcFoodStaticTextField.weighty = 50.0
//        this.add(foodStaticTextField, gbcFoodStaticTextField)
//    }
//
//    // STATE DELAY MS TEXT FIELD
//    init {
//        val gbcStateDelayMsTextField = GridBagConstraints()
//        gbcStateDelayMsTextField.gridx = 3
//        gbcStateDelayMsTextField.gridy = 1
//        gbcStateDelayMsTextField.gridwidth = 1
//        gbcStateDelayMsTextField.gridheight = 1
//        gbcStateDelayMsTextField.fill = GridBagConstraints.BOTH
//        gbcStateDelayMsTextField.anchor = GridBagConstraints.NORTHWEST
//        gbcStateDelayMsTextField.weightx = 25.0
//        gbcStateDelayMsTextField.weighty = 50.0
//        this.add(stateDelayMsTextField, gbcStateDelayMsTextField)
//    }