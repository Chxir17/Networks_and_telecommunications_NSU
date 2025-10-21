import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.http.HttpClient
import java.time.Duration
import entities.APIKeys
import geoapifyInteraction.GeoapifyInteraction
import kudagoInteraction.KudagoInteraction
import openWeatherInteraction.OpenWeatherInteraction
import result.GetResult

val httpClient: HttpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build()
val mapper = jacksonObjectMapper()

fun main() {
    try {
        println("Enter location to search:")
        val query = readLine()?.trim()
        if (query.isNullOrBlank()) {
            return
        }

        val geoapifyInteraction = GeoapifyInteraction(APIKeys().GEOAPIFAY_API_KEY)
        val openWeatherInteraction = OpenWeatherInteraction(APIKeys().OPENWEATHER_API_KEY)
        val kudagoInteraction = KudagoInteraction()
        val getResult = GetResult(openWeatherInteraction, geoapifyInteraction, kudagoInteraction)
        getResult.getResult(query)
    } catch (e: Exception) {
        println(e.message)
    }
}
