import java.net.URI
import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class PrepareSegmentationModel : DefaultTask() {
    init {
        modelChecksum.convention("")
        archiveEntry.convention("")
    }

    @get:Input
    abstract val offline: Property<Boolean>

    @get:Input
    abstract val modelUrl: Property<String>

    @get:Input
    abstract val checksum: Property<String>

    @get:Input
    abstract val modelChecksum: Property<String>

    @get:Input
    abstract val archiveEntry: Property<String>

    @get:OutputFile
    abstract val modelFile: RegularFileProperty

    @TaskAction
    fun prepare() {
        val expectedModelChecksum = modelChecksum.orNull?.takeIf(String::isNotEmpty) ?: checksum.get()
        val destination = modelFile.get().asFile
        if (destination.isFile && sha256(destination) == expectedModelChecksum) return
        check(!offline.get()) {
            "The verified model ${destination.name} is missing. Run once without --offline to download it."
        }
        destination.parentFile.mkdirs()
        val downloaded = File(destination.parentFile, "${destination.name}.download")
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        try {
            val connection = URI(modelUrl.get()).toURL().openConnection().apply {
                connectTimeout = 15_000
                readTimeout = 60_000
            }
            connection.getInputStream().use { input ->
                downloaded.outputStream().use { output -> input.copyTo(output) }
            }
            check(sha256(downloaded) == checksum.get()) { "Download checksum mismatch: ${destination.name}" }
            if (archiveEntry.get().isEmpty()) {
                java.nio.file.Files.copy(downloaded.toPath(), temporary.toPath())
            } else {
                extractTarEntry(downloaded, archiveEntry.get(), temporary)
            }
            check(sha256(temporary) == expectedModelChecksum) {
                "Extracted model checksum mismatch: ${destination.name}"
            }
            java.nio.file.Files.move(
                temporary.toPath(), destination.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            downloaded.delete()
            temporary.delete()
        }
    }

    private fun extractTarEntry(archive: File, entryName: String, destination: File) {
        archive.inputStream().buffered().use { input ->
            val header = ByteArray(512)
            var found = false
            while (readBlock(input, header)) {
                if (header.all { it == 0.toByte() }) break
                val name = tarString(header, 0, 100)
                val prefix = tarString(header, 345, 155)
                val path = if (prefix.isEmpty()) name else "$prefix/$name"
                val size = tarOctal(header, 124, 12)
                val type = header[156].toInt().toChar()
                if (path == entryName && type in listOf('\u0000', '0')) {
                    check(!found) { "Duplicate model entry in archive: $entryName" }
                    destination.outputStream().buffered().use { output -> copyExactly(input, output, size) }
                    found = true
                } else {
                    skipExactly(input, size)
                }
                val padding = (512 - size % 512) % 512
                skipExactly(input, padding)
            }
            check(found) { "Model entry not found in archive: $entryName" }
        }
    }

    private fun readBlock(input: java.io.InputStream, block: ByteArray): Boolean {
        var offset = 0
        while (offset < block.size) {
            val count = input.read(block, offset, block.size - offset)
            if (count < 0) {
                check(offset == 0) { "Truncated tar archive" }
                return false
            }
            offset += count
        }
        return true
    }

    private fun tarString(header: ByteArray, offset: Int, length: Int): String =
        header.copyOfRange(offset, offset + length).takeWhile { it != 0.toByte() }
            .toByteArray().toString(Charsets.UTF_8)

    private fun tarOctal(header: ByteArray, offset: Int, length: Int): Long {
        val value = tarString(header, offset, length).trim()
        return if (value.isEmpty()) 0L else value.toLong(8)
    }

    private fun copyExactly(input: java.io.InputStream, output: java.io.OutputStream, size: Long) {
        var remaining = size
        val buffer = ByteArray(8192)
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            check(count > 0) { "Truncated model entry in archive" }
            output.write(buffer, 0, count)
            remaining -= count
        }
    }

    private fun skipExactly(input: java.io.InputStream, size: Long) {
        var remaining = size
        val buffer = ByteArray(8192)
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            check(count > 0) { "Truncated archive entry" }
            remaining -= count
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

run {
val prepareSegmentationModel = tasks.register<PrepareSegmentationModel>("prepareSegmentationModel") {
    offline.set(gradle.startParameter.isOffline)
    modelUrl.set("https://storage.googleapis.com/mediapipe-models/image_segmenter/deeplab_v3/float32/1/deeplab_v3.tflite")
    checksum.set("ff36e24d40547fe9e645e2f4e8745d1876d6e38b332d39a82f0bf0f5d1d561b3")
    modelFile.set(layout.buildDirectory.file("generated/mediapipeAssets/deeplabv3.tflite"))
    outputs.upToDateWhen { false }
}

val prepareDepthModel = tasks.register<PrepareSegmentationModel>("prepareDepthModel") {
    offline.set(gradle.startParameter.isOffline)
    modelUrl.set("https://raw.githubusercontent.com/shubham0204/MiDaS-Android/df0ffb215d42e73a1c47d0bee67c98c3ddd11c6e/app/src/main/assets/depth_model.tflite")
    checksum.set("93d871071edff1218973ce25ee27ce95ccd20450c70a55e1b89efa3f5a772cdd")
    modelFile.set(layout.buildDirectory.file("generated/mediapipeAssets/midas_v21_small.tflite"))
    outputs.upToDateWhen { false }
}

val prepareLandscapeModel = tasks.register<PrepareSegmentationModel>("prepareLandscapeModel") {
    offline.set(gradle.startParameter.isOffline)
    modelUrl.set("https://github.com/sayakpaul/Adventures-in-TensorFlow-Lite/releases/download/v0.8.0/mnv2_ade20k_tflite.tar.gz")
    checksum.set("08e419abedc31fc0840b815bdd46eebc55aefdb96af1db5ec777367ec01d0c6c")
    modelChecksum.set("0acdf37d5db38e681646ce79557c2af516650ecca5377b15c9cb171f751556cb")
    archiveEntry.set("deeplabv3_mnv2_ade20k_train_2018_12_03.tflite")
    modelFile.set(layout.buildDirectory.file("generated/mediapipeAssets/ade20k_deeplabv3.tflite"))
    outputs.upToDateWhen { false }
}

tasks.named("preBuild") {
    dependsOn(prepareSegmentationModel, prepareDepthModel, prepareLandscapeModel)
}
}
