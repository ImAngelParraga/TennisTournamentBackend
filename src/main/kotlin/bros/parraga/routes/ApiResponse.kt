package bros.parraga.routes

data class ApiResponse<T>(
    val status: String,
    val data: T? = null,
    val message: String? = null
)
