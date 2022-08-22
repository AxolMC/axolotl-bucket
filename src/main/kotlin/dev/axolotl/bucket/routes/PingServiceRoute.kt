package dev.axolotl.bucket.routes

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * @author Kenox
 */
fun Route.pingServiceRoute() {
    get("/ping") {
        call.respondText("pong")
    }
}