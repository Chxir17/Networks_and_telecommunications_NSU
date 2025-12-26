package onlinesnake.ui.impl.contentpanels

import java.awt.GridLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

class NicknameAndServerNamePropertiesPanel : JPanel(), NicknameAndServerNameProperties {
    private val nickNameLabel = JLabel("Nickname:")
    private val serverNameLabel = JLabel("Server name:")

    private val nickNameTextField = JTextField("")
    private val serverNameTextField = JTextField("")

    override val nickname
        get() = nickNameTextField.text!!
    override val serverName
        get() = serverNameTextField.text!!

    init {
        this.layout = GridLayout(2, 2)
        this.add(nickNameLabel)
        this.add(serverNameLabel)
        this.add(nickNameTextField)
        this.add(serverNameTextField)
    }
}