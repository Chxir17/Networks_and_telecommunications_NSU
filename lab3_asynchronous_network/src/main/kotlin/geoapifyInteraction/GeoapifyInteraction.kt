package geoapifyInteraction

import entities.Location
import httpClient
import mapper
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

class GeoapifyInteraction(val apiKey: String) {

    fun searchLocations(query: String): CompletableFuture<List<Location>> {
        val q = URLEncoder.encode(query, "UTF-8")
        val url = "https://api.geoapify.com/v1/geocode/search?text=$q&format=json&apiKey=${apiKey}"
        val req = HttpRequest
            .newBuilder()
            .uri(URI.create(url))
            .GET()
            .timeout(Duration.ofSeconds(20))
            .build()
        return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply { resp ->
            if (resp.statusCode() !in 200..299) {
                throw RuntimeException("Geoapify geocode error: ${resp.statusCode()} - ${resp.body()}")
            }
            val root = mapper.readTree(resp.body())
            val arr = root.path("results")
            val list = mutableListOf<Location>()
            for (node in arr) {
                val name = node.path("formatted").asText("")
                val lat = node.path("lat").asDouble()
                val lon = node.path("lon").asDouble()
                list.add(Location(name, lat, lon))
            }
            list
        }
    }
}