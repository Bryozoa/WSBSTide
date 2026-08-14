import java.util.Calendar
import java.util.TimeZone

class StandardTideTimeBasis : TideTimeBasis {
    override fun resolve(
        instantMillis: Long,
        station: TideStation,
    ): TideTimeContext {
        val utc = TimeZone.getTimeZone("UTC")
        val cal = Calendar.getInstance(utc).apply { timeInMillis = instantMillis }
        val year = cal.get(Calendar.YEAR)

        val yearStart = Calendar.getInstance(utc).apply {
            clear()
            set(year, Calendar.JANUARY, 1, 0, 0, 0)
        }
        val secondsFromYearEpoch = (instantMillis - yearStart.timeInMillis) / 1000L

        return TideTimeContext(
            year = year,
            yearIndex = year - station.firstYear,
            secondsFromYearEpoch = secondsFromYearEpoch,
        )
    }
}
