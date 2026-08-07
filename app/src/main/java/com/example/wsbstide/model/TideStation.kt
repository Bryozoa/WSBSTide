data class TideStation(
    val name: String,
    val units: String,
    val datum: Double,
    val meridianSeconds: Int,
    val displayTimeZone: String,
    val firstYear: Int,
    val numberOfYears: Int,
    val constituents: List<TideConstituent>,
){
    val lastYear: Int
        get() = firstYear + numberOfYears - 1
}