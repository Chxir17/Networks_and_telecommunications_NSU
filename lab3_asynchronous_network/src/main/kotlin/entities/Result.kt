package entities

data class Result(
    val location: Location,
    val weather: Weather,
    val places: List<PlaceDetails>
)
