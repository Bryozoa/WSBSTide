data class TideConstituent(
    val name: String,
    val speedRadiansPerSecond: Double,
    val stationAmplitude: Double,
    val stationPhaseRadians: Double,
    val nodeFactors: List<Double>,
    val equilibriumArgumentsRadians: List<Double>,
)