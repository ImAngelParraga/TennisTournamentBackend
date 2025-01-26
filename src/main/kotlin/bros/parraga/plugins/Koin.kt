package bros.parraga.plugins

import bros.parraga.services.repositories.TournamentRepository
import bros.parraga.services.repositories.impl.TournamentRepositoryImpl
import io.ktor.server.application.Application
import io.ktor.server.application.install
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
}