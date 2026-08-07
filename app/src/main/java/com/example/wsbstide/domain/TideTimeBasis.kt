data class TideTimeContext(
    val year: Int,
    val yearIndex: Int,
    val secondsFromYearEpoch: Long,
)

interface TideTimeBasis {
    fun resolve(
        instantMillis: Long,
        station: TideStation,
    ): TideTimeContext
}