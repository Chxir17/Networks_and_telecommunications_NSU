package usersTable

import java.time.Instant

data class TableLine(
    val ip: String,
    var currentTime: Instant = Instant.now()
)