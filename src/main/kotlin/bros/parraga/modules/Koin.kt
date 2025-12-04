package bros.parraga.modules

import bros.parraga.services.repositories.club.ClubRepository
import bros.parraga.services.repositories.club.ClubRepositoryImpl
import bros.parraga.services.repositories.match.MatchRepository
import bros.parraga.services.repositories.match.MatchRepositoryImpl
import bros.parraga.services.repositories.player.PlayerRepository
import bros.parraga.services.repositories.player.PlayerRepositoryImpl
import bros.parraga.services.repositories.tournament.TournamentRepository
import bros.parraga.services.repositories.tournament.TournamentRepositoryImpl
import bros.parraga.services.repositories.user.UserRepository
import bros.parraga.services.repositories.user.UserRepositoryImpl
import io.ktor.server.application.*
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureKoin() {
    install(Koin) {
        slf4jLogger()
        modules(appModule)
    }
}

val appModule = module {
    singleOf(::TournamentRepositoryImpl) bind TournamentRepository::class
    singleOf(::UserRepositoryImpl) { bind<UserRepository>() }
    singleOf(::PlayerRepositoryImpl) bind PlayerRepository::class
    singleOf(::ClubRepositoryImpl) bind ClubRepository::class
    singleOf(::MatchRepositoryImpl) bind MatchRepository::class
}