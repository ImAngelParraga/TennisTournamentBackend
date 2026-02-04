rootProject.name = "TennisTournamentBackend"

// Use local TennisTournamentLib if present (for faster iteration and local fixes).
val localTournamentLib = file("../TennisTournamentLib")
if (localTournamentLib.exists()) {
    includeBuild(localTournamentLib) {
        dependencySubstitution {
            substitute(module("com.github.ImAngelParraga:TennisTournamentLib"))
                .using(project(":"))
        }
    }
}
