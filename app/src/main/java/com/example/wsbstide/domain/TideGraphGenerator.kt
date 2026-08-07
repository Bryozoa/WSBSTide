import com.example.wsbstide.model.TidePoint

class TideGraphGenerator(
    private val calculator: TideCalculator,
) {
    fun generate(
        startMillis: Long,
        endMillis: Long,
        stepMinutes: Int,
        station: TideStation,
    ): List<TidePoint> {
        require(stepMinutes > 0)
        require(endMillis > startMillis)

        val result = mutableListOf<TidePoint>()
        val stepMillis = stepMinutes * 60_000L

        var time = startMillis

        while (time <= endMillis) {
            result += TidePoint(
                timestampMillis = time,
                height = calculator.heightAt(time, station),
            )

            time += stepMillis
        }

        return result
    }
}