package dev.axolotl.bucket.routes

import dev.axolotl.bucket.config
import dev.axolotl.bucket.downloadFile
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import dev.axolotl.bucket.plugins.handleAuthorization
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * @author Kenox
 */
fun Route.getModFolderRoute() {
    get {
        if(!handleAuthorization(call))
            return@get

        val file = File("modfolder.zip")

        val lastModified = file.lastModified() + TimeUnit.SECONDS.toMillis(config.cachePeriod.toLong())

        if(System.currentTimeMillis() >= lastModified) {
            file.delete()
        }

        if(file.exists()) {
            call.respondFile(file)

            println("Action completed")
            return@get
        }

        println("Download ${file.name} ...")
        downloadFile(file.name, file)

        call.respondFile(file)

        println("Action completed")
    }
}