package statistic

import entities.Statistic

interface IStatser {
    @Throws(Exception::class)
    fun stats(): Pair<Statistic, Statistic>
}