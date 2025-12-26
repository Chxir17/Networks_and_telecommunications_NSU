package onlinesnake

import kotlinx.coroutines.runBlocking
import onlinesnake.controller.Controller

fun main() =
    runBlocking {
        System.setProperty("sun.java2d.opengl", "true")
        Controller().start()
    }
