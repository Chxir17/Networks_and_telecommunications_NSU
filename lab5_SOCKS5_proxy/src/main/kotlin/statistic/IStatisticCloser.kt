package statistic

import java.io.IOException

interface IStatisticCloser : IStatser {
    @Throws(IOException::class)
    fun close()
}