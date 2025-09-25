package server.speedMonitor

class SpeedMonitor {
    private var totalBytes: Long = 0
    private var currentBytes: Long = 0
    private var startTime = System.currentTimeMillis()
    private var currentTime = startTime

    @Synchronized
    fun addBytes(bytes: Int) {
        totalBytes += bytes
    }

    @Synchronized
    fun reportSpeeds(): Pair<Double, Double> {
        val now = System.currentTimeMillis()
        val elapsed = (now - startTime) / 1000.0
        val interval = (now - currentTime) / 1000.0
        val instantSpeed = if (interval > 0) (totalBytes - currentBytes) / interval else 0.0
        val avgSpeed = if (elapsed > 0) totalBytes / elapsed else 0.0

        currentTime = now
        currentBytes = totalBytes
        return Pair(instantSpeed, avgSpeed)
    }
}
