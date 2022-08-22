package dev.axolotl.bucket.routes

import com.backblaze.b2.client.exceptions.B2Exception
import dev.axolotl.bucket.config
import dev.axolotl.bucket.getFileChecksum
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import dev.axolotl.bucket.plugins.handleAuthorization
import dev.axolotl.bucket.uploadFile
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * @author Kenox
 */
fun Route.putPackRoute() {
    put {
        if(!handleAuthorization(call))
            return@put

        val multipart = call.receiveMultipart()

        var name: String? = null

        println("Starting upload locally..")

        multipart.forEachPart { part ->
            if(part is PartData.FileItem) {
                name = part.originalFileName!!

                val uploadFolder = File("uploads")

                if(!uploadFolder.exists())
                    uploadFolder.mkdir()

                val file = File("uploads/$name")

                part.streamProvider().use { its ->
                    file.outputStream().buffered().use {
                        its.copyTo(it)
                    }
                }
            }
            part.dispose()
        }

        println("Uploaded file $name locally")

        // Upload to backblaze
        val uploadedFile = File("uploads/$name")
        val uploadedFileHash = getFileChecksum(MessageDigest.getInstance("SHA-1"), uploadedFile)

        call.respondText(uploadedFileHash)

        println("File hash of $name = $uploadedFileHash")

        // Move upload to cache
        val cachedFile = File("cache/$uploadedFileHash.zip")

        if(cachedFile.exists()) {
            println("File $uploadedFileHash.zip is already cached. No need to upload to B2")
            return@put
        }

        withContext(Dispatchers.IO) {
            Files.move(uploadedFile.toPath(), cachedFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        try {
            uploadFile(cachedFile)
        } catch (exception: B2Exception) {
            println("Failed uploading ${cachedFile.name}")
            exception.printStackTrace()
        }
    }
}