package bros.parraga.services.repositories.racket

import bros.parraga.domain.RacketDetails
import bros.parraga.services.repositories.racket.dto.CreateRacketStringingRequest
import bros.parraga.services.repositories.racket.dto.UpdateRacketRequest
import bros.parraga.services.repositories.racket.dto.UpdateRacketStringingRequest

interface RacketRepository {
    suspend fun getPublicRacket(publicToken: String): RacketDetails
    suspend fun createStringing(userId: Int, request: CreateRacketStringingRequest): RacketDetails
    suspend fun updateRacket(userId: Int, publicToken: String, request: UpdateRacketRequest): RacketDetails
    suspend fun updateStringing(userId: Int, stringingId: Int, request: UpdateRacketStringingRequest): RacketDetails
    suspend fun deleteStringing(userId: Int, stringingId: Int)
}
