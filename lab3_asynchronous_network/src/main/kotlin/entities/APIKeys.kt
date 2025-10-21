package entities

data class APIKeys (
    val GRAPH_HOPPER_API_KEY: String = System.getenv("GRAPH_HOPPER_API_KEY")?:"",
    val OPENWEATHER_API_KEY: String = System.getenv("OPENWEATHER_API_KEY")?:"",
    val GEOAPIFAY_API_KEY: String = System.getenv("GEOAPIFAY_API_KEY")?:""
)