package onlinesnake.ui.impl.contentpanels

import java.awt.GridLayout
import javax.swing.JLabel
import javax.swing.JPanel

class CurrentGameInfoPanel : JPanel() {
    private val ownerPrefix = "Owner: "
    private val sizePrefix = "Field size: "
    private val foodPrefix = "Food on field: "
    private val stateOrderPrefix = "State order: "
    private val ownerLabel = JLabel()
    private val sizeLabel = JLabel()
    private val foodLabel = JLabel()
    private val stateOrderLabel = JLabel()
    init {
        this.layout = GridLayout(1, 4)
        this.add(ownerLabel)
        this.add(sizeLabel)
        this.add(foodLabel)
        this.add(stateOrderLabel)
    }

    fun updateCurrentGameInfo(fieldWidth: Int, fieldHeight: Int, foodOnField: Int, ownerName: String, stateOrder: Int) {
        ownerLabel.text = ownerPrefix + ownerName
        sizeLabel.text = sizePrefix + "${fieldWidth}x${fieldHeight}"
        foodLabel.text = foodPrefix + foodOnField
        stateOrderLabel.text = stateOrderPrefix + stateOrder
    }

    fun clearCurrentGameInfo() {
        ownerLabel.text = ""
        sizeLabel.text = ""
        foodLabel.text = ""
        stateOrderLabel.text = ""
    }
}