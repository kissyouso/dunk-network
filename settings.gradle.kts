plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "RedeStyle"

include("core")
include("core-bukkit")
include("core-bungee")
include("lobby")
include("auth")
include("duels")
include("pvp")
include("bedwars")
include("skywars")
include("academy")
include("event")
include("anticheat")
include("thebridge")

// Os módulos "game" não existem nesta cópia da source (sem pastas/sources).
// Mantidos fora do build para o projeto compilar.
