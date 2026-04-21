package bros.parraga.services.repositories.racket

import bros.parraga.domain.RacketDetails
import bros.parraga.domain.RacketSummary
import bros.parraga.services.repositories.racket.dto.CreateRacketRequest
import bros.parraga.services.repositories.racket.dto.CreateRacketStringingRequest
import bros.parraga.services.repositories.racket.dto.UpdateRacketRequest
import bros.parraga.services.repositories.racket.dto.UpdateRacketStringingRequest

interface RacketRepository {
    suspend fun getOwnRackets(ownerUserId: Int): List<RacketSummary>
    suspend fun getOwnRacket(ownerUserId: Int, racketId: Int): RacketDetails
    suspend fun createRacket(ownerUserId: Int, request: CreateRacketRequest): RacketDetails
    suspend fun updateRacket(ownerUserId: Int, racketId: Int, request: UpdateRacketRequest): RacketDetails
    suspend fun deleteRacket(ownerUserId: Int, racketId: Int)
    suspend fun createStringing(ownerUserId: Int, racketId: Int, request: CreateRacketStringingRequest): RacketDetails
    suspend fun updateStringing(
        ownerUserId: Int,
        racketId: Int,
        stringingId: Int,
        request: UpdateRacketStringingRequest
    ): RacketDetails

    suspend fun deleteStringing(ownerUserId: Int, racketId: Int, stringingId: Int)
    suspend fun getPublicRackets(userId: Int): List<RacketSummary>
    suspend fun getPublicRacket(userId: Int, racketId: Int): RacketDetails
}
