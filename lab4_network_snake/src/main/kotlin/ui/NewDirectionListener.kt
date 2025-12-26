package onlinesnake.ui

import onlinesnake.dto.Direction

fun interface NewDirectionListener {
    fun newDirection(direction: Direction)
}