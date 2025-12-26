package onlinesnake.ui.impl.contentpanels

import onlinesnake.ui.AvailableGameSelectedListener
import onlinesnake.ui.dto.AvailableGameDto
import onlinesnake.ui.dto.AvailableGameKey
import java.awt.GridLayout
import javax.swing.JPanel
import javax.swing.JScrollPane

class AvailableGamesPanel(
    private val nicknameAndServerNameProperties: NicknameAndServerNameProperties,
) : JPanel() {
    private val availableScrollPane = JScrollPane()
    private val content = JPanel()
    private val availableGameElementsMap = mutableMapOf<AvailableGameKey, AvailableGameScrollElement>()

    init {
        this.layout = GridLayout(1, 1)
    }

    init {
        this.add(availableScrollPane)
    }

    init {
        content.layout = GridLayout(0, 1)
        availableScrollPane.setViewportView(content)
        availableScrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        availableScrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
    }

    fun addAvailableGame(
        availableGameDto: AvailableGameDto,
        availableGameSelectedListener: AvailableGameSelectedListener
    ) : AvailableGameKey {
        val key = AvailableGameKey()
        val available = AvailableGameScrollElement(
            availableGameDto,
            availableGameSelectedListener,
            nicknameAndServerNameProperties
        )
        availableGameElementsMap[key] = available
        content.add(available)
        revalidate()
        return key
    }

    fun removeAvailableGame(key: AvailableGameKey) {
        val available = availableGameElementsMap.remove(key)
        if (available != null) {
            content.remove(available)
            revalidate()
        }
    }

    fun updateAvailableGame(info: AvailableGameDto, key: AvailableGameKey) {
        availableGameElementsMap[key]?.updateInfo(info)
        revalidate()
    }
}

