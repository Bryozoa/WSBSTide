import android.content.Context
import com.example.wsbstide.data.TideJsonParser

class TideRepository(context: Context) {

    private val station: TideStation =
        context.applicationContext.assets
            .open("wsbs-tide-data.json")
            .use { inputStream ->
                TideJsonParser.parse(inputStream)
            }

    init {
        station.constituents.forEach { constituent ->
            require(
                constituent.nodeFactors.size == station.numberOfYears
            ) {
                "${constituent.name}: invalid nodeFactors count"
            }

            require(
                constituent.equilibriumArgumentsRadians.size ==
                        station.numberOfYears
            ) {
                "${constituent.name}: invalid equilibriumArguments count"
            }
        }
    }

    fun getStation(year: Int): TideStation {
        require(year in station.firstYear..station.lastYear) {
            "No tide data available for year $year. " +
                    "Supported range: ${station.firstYear}..${station.lastYear}"
        }

        return station
    }
}