package result

import entities.Location
import entities.Result
import geoapifyInteraction.GeoapifyInteraction
import kudagoInteraction.KudagoInteraction
import openWeatherInteraction.OpenWeatherInteraction
import java.util.concurrent.CompletableFuture




class GetResult(
    private val openWeatherInteraction: OpenWeatherInteraction,
    private val geoapifyInteraction: GeoapifyInteraction,
    private val kudagoInteraction: KudagoInteraction
) {

    fun getResult(query: String) {
        println("Searching \"$query\"...")
        val locs = try {
            geoapifyInteraction.searchLocations(query).get()
        } catch (ex: Exception) {
            println("Geocoding error: ${ex.message}")
            return
        }

        if (locs.isEmpty()) {
            println("Nothing found")
            return
        }

        locs.forEachIndexed { idx, loc ->
            println("${idx + 1}. ${loc.name} (lat=${loc.lat}, lon=${loc.lon})")
        }

        println("Pick place from (1 to ${locs.size}):")
        val idx = readLine()?.toIntOrNull()
        if (idx == null || idx < 1 || idx > locs.size) {
            println("Unknown index")
            return
        }

        val chosen = locs[idx - 1]
        println("Picked ${chosen.name}. Finding data...")

        val result = try {
            fetchAggregatedData(chosen).get()
        } catch (ex: Exception) {
            println("Error while receiving data: ${ex.message}")
            return
        }
        printResult(result)
    }

    private fun printResult(result: Result) {
        println("\n" + "=".repeat(50))
        println("Results: ${result.location.name}")
        println("=".repeat(50))
        println("Temperature is: ${result.weather.tempC}°C")
        println("\nPlaces nearby:")

        if (result.places.isEmpty()) {
            println("No places found")
        } else {
            result.places.forEachIndexed { i, placeDetail ->
                if (!placeDetail.name.equals("Unknown place")) {
                    println("\n${i + 1}. ${placeDetail.name}")
                    println("Description: ${placeDetail.description ?: "No description"}")
                }
            }
        }
        println("=".repeat(50))
    }

    private fun fetchAggregatedData(location: Location): CompletableFuture<Result> {

        try {

            val weatherF = openWeatherInteraction.fetchWeather(location)
            val placesF = kudagoInteraction.fetchPlacesNearby(location, radiusMeters = 2000, limit = 10)

            return weatherF.thenCombine(placesF) { weather, places -> Pair(weather, places) }
                .thenCompose { (weather, places) ->
                    if (places.isEmpty()) {
                        CompletableFuture.completedFuture(Result(location, weather, emptyList()))
                    } else {
                        val detailFs = places.map { place ->
                            kudagoInteraction.fetchPlaceDetail(place.placeId)
                        }
                        val allOf = CompletableFuture.allOf(*detailFs.toTypedArray())
                        allOf.thenApply {
                            detailFs.map { it.join() }
                        }.thenApply { details ->
                            Result(location, weather, details)
                        }
                    }
                }
        } catch (ex: Exception) {
            throw RuntimeException("Error fetching data: ${ex.message}")
        }
    }
}