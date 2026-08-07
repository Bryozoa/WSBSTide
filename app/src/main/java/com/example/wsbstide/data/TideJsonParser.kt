package com.example.wsbstide.data
import TideConstituent
import TideStation
import org.json.JSONObject
import java.io.InputStream

object TideJsonParser {

    fun parse(input: InputStream): TideStation {
        val root = JSONObject(
            input.bufferedReader().readText()
        )

        val stationJson = root.getJSONObject("station")

        return TideStation(
            name = stationJson.getString("name"),
            units = stationJson.getString("units"),
            datum = stationJson.getDouble("datum"),
            meridianSeconds = stationJson.getInt("meridianSeconds"),
            displayTimeZone = stationJson.getString("displayTimeZone"),
            firstYear = stationJson.getInt("firstYear"),
            numberOfYears = stationJson.getInt("numberOfYears"),
            constituents = parseConstituents(root)
        )
    }

    private fun parseConstituents(
        json: JSONObject
    ): List<TideConstituent> {

        val array = json.getJSONArray("constituents")

        return List(array.length()) { i ->
            val constituent = array.getJSONObject(i)

            TideConstituent(
                name = constituent.getString("name"),
                speedRadiansPerSecond =
                    constituent.getDouble("speedRadiansPerSecond"),
                stationAmplitude =
                    constituent.getDouble("stationAmplitude"),
                stationPhaseRadians =
                    constituent.getDouble("stationPhaseRadians"),
                nodeFactors =
                    parseDoubleList(constituent, "nodeFactors"),
                equilibriumArgumentsRadians =
                    parseDoubleList(
                        constituent,
                        "equilibriumArgumentsRadians"
                    )
            )
        }
    }

    private fun parseDoubleList(
        json: JSONObject,
        key: String
    ): List<Double> {
        val array = json.getJSONArray(key)

        return List(array.length()) { i ->
            array.getDouble(i)
        }
    }
}