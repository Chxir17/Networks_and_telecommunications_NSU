package kudagoInteraction

import entities.Location
import entities.Place
import entities.PlaceDetails
import httpClient
import mapper
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import kotlin.text.replace

class KudagoInteraction {

    fun fetchPlacesNearby(
        location: Location,
        radiusMeters: Int = 2000,
        limit: Int = 15
    ): CompletableFuture<List<Place>> {
        val url = "https://kudago.com/public-api/v1.4/places/?" +
            "lon=${location.lon}&lat=${location.lat}" +
            "&radius=$radiusMeters" +
            "&limit=$limit" +
            "&fields=id,title,coords,categories" +
            "&lang=ru" +
            "&text_format=plain"
        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .timeout(Duration.ofSeconds(20))
            .build()

        return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply { resp ->
            if (resp.statusCode() !in 200..299) {
                throw RuntimeException("KudaGo API error: ${resp.statusCode()} - ${resp.body()}")
            }

            val root = mapper.readTree(resp.body())
            val results = root.path("results")
            val places = mutableListOf<Place>()
            for (node in results) {
                val id = node.path("id").asText()
                val name = node.path("title").asText("")
                val lat = node.path("coords").path("lat").asDouble()
                val lon = node.path("coords").path("lon").asDouble()

                places.add(Place(id, name, lat, lon))
            }

            places
        }
    }


    fun fetchPlaceDetail(placeId: String): CompletableFuture<PlaceDetails> {
        val url = "https://kudago.com/public-api/v1.4/places/$placeId/?fields=id,title,description"

        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .timeout(Duration.ofSeconds(20))
            .build()

        return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenApply { resp ->
                if (resp.statusCode() == 503) {
                    return@thenApply PlaceDetails(
                        placeId = placeId,
                        name = "Unknown place",
                        description = "No details available"
                    )
                }

                if (resp.statusCode() !in 200..299) {
                    throw RuntimeException("KudaGo detail error: ${resp.statusCode()} - ${resp.body()}")
                }

                val node = mapper.readTree(resp.body())
                val id = node.path("id").asText()
                val name = node.path("title").asText("")
                var description = node.path("description").asText(null)
                if (description != null) {
                    description = description.replace(Regex("<[^>]*>"), "").trim()
                }

                PlaceDetails(
                    placeId = id,
                    name = name,
                    description = description
                )
            }
            .exceptionally { _ ->
                PlaceDetails(
                    placeId = placeId,
                    name = "Unknown place",
                    description = "No details available"
                )
            }
    }

}