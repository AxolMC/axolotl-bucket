package dev.axolotl.bucket.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import dev.axolotl.bucket.config
import dev.axolotl.bucket.routes.getModFolderRoute
import dev.axolotl.bucket.routes.getPackRoute
import dev.axolotl.bucket.routes.pingServiceRoute
import dev.axolotl.bucket.routes.putPackRoute

fun Application.configureRouting() {
    routing {
        route("/v1") {
            pingServiceRoute()
            route("/modfolder") {
                getModFolderRoute()
            }
            route("/pack") {
                putPackRoute()
                getPackRoute()
            }
        }
    }
}

suspend fun handleAuthorization(call: ApplicationCall): Boolean {
    val key: String? = call.request.header("X-API-Key")

    if(key == null || key != config.apiKey)  {
        call.respondText("API Key invalid", ContentType.Text.Plain, HttpStatusCode.BadRequest)
        return false
    }

    return true
}
