package bros.parraga.modules

import bros.parraga.db.DatabaseFactory
import io.ktor.server.application.Application

fun Application.configureDatabase() {
    DatabaseFactory.init()
}