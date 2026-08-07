class TideCalculator(
    private val timeBasis: TideTimeBasis,
) {
    fun heightAt(
        instantMillis: Long,
        station: TideStation,
    ): Double {
        val time = timeBasis.resolve(
            instantMillis = instantMillis,
            station = station,
        )

        var height = station.datum

        for (constituent in station.constituents) {
            val nodeFactor =
                constituent.nodeFactors[time.yearIndex]

            val equilibriumArgument =
                constituent.equilibriumArgumentsRadians[
                    time.yearIndex
                ]

            val phase =
                constituent.speedRadiansPerSecond *
                        (
                                time.secondsFromYearEpoch +
                                        station.meridianSeconds
                                ) +
                        equilibriumArgument -
                        constituent.stationPhaseRadians

            height +=
                constituent.stationAmplitude *
                        nodeFactor *
                        kotlin.math.cos(phase)
        }

        return height
    }
}