import java.net.URI
import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class PrepareSegmentationModel : DefaultTask() {
    @get:Input
    abstract val offline: Property<Boolean>

    @get:Input
    abstract val modelUrl: Property<String>

    @get:Input
    abstract val checksum: Property<String>

    @get:OutputFile
    abstract val modelFile: RegularFileProperty

    @TaskAction
    fun prepare() {
        val expected = checksum.get()
        val destination = modelFile.get().asFile
        if (destination.isFile && sha256(destination) == expected) return
        check(!offline.get()) {
            "The verified model ${destination.name} is missing. Run once without --offline to download it."
        }
        destination.parentFile.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        try {
            val connection = URI(modelUrl.get()).toURL().openConnection().apply {
                connectTimeout = 15_000
                readTimeout = 60_000
            }
            connection.getInputStream().use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            check(sha256(temporary) == expected) { "Model checksum mismatch: ${destination.name}" }
            java.nio.file.Files.move(
                temporary.toPath(), destination.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            temporary.delete()
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

tasks.named("preBuild") {
    dependsOn(prepareSegmentationModel, prepareDepthModel)
}
