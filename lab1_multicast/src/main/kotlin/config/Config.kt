package config

data class Config(
    val ip: String,
    val port: Int,
    val reportIntervalSec: Long
)
