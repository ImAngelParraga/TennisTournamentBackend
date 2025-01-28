package bros.parraga.routes

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val status: String,
    val data: T? = null,
    val message: String? = null
)
