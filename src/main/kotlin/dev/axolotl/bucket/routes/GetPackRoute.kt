package dev.axolotl.bucket.routes

import com.backblaze.b2.client.exceptions.B2Exception
import dev.axolotl.bucket.b2Client
import dev.axolotl.bucket.bucket
import dev.axolotl.bucket.downloadFile
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

/**
 * @author Kenox
 */
fun Route.getPackRoute() {
    get {
        val fileHash: String? = call.request.queryParameters["hash"]

        if(fileHash == null) {
            call.respondText("Need file hash", ContentType.Text.Plain, HttpStatusCode.BadRequest)
            return@get
        }

        try {
            val fileName = "$fileHash.zip"

            val cacheFolder = File("cache")

            if(!cacheFolder.exists())
                cacheFolder.mkdir()

            val cachedFile = File("cache/$fileName")

            if(!cachedFile.exists()) {
                val fileResult = b2Client.getFileInfoByName(bucket.bucketName, "packs/$fileName")

                if(!fileResult.isUpload) {
                    call.respondText("File is not listed as uploaded", ContentType.Text.Plain, HttpStatusCode.InternalServerError)
                    return@get
                }

                println("Download $fileName ...")
                downloadFile(fileName, cachedFile)
            } else {
                println("$fileName is already cached")
            }

            call.respondFile(cachedFile)

            println("Action completed")
        } catch (e: B2Exception) {
            call.respondText("World not found", ContentType.Text.Plain, HttpStatusCode.NotFound)
        }
    }
}