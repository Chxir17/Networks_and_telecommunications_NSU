package org.argumentsParser
import org.config.IConfig


interface IParser {
    fun parseConfigFile(path: String = "config.cfg") : IConfig
    fun parseArgs(args: Array<String>) : IConfig
}