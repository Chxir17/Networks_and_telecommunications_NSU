package openWeatherInteraction
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import entities.Location
import entities.Weather
import httpClient
import mapper

class OpenWeatherInteraction (
    private val apiKey: String
) {

    private fun getFullUrl(lat: Double, lon: Double): String {
        return "https://api.openweathermap.org/data/2.5/weather?lat=${lat}&lon=${lon}&appid=${this.apiKey}&units=metric&lang=ru"
    }

    fun fetchWeather(location: Location): CompletableFuture<Weather> {
        val url = getFullUrl(location.lat, location.lon)
        val req = HttpRequest
            .newBuilder()
            .uri(URI.create(url))
            .GET()
            .timeout(Duration.ofSeconds(20))
            .build()
        return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply {
            resp -> if (resp.statusCode() !in 200..299) {
                    throw RuntimeException("Weather API error: ${resp.statusCode()} - ${resp.body()}")
                }
                val node = mapper.readTree(resp.body())
                val desc = node.path("openWeatherInteraction").firstOrNull()?.path("description")?.asText() ?: ""
                val temp = node.path("main").path("temp").asDouble(Double.NaN)
                Weather(desc, temp)
            }
    }
}