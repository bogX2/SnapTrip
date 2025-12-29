package com.example.snaptrip.utils


    import android.graphics.Bitmap
    import com.google.mlkit.vision.common.InputImage
    import com.google.mlkit.vision.label.ImageLabeling
    import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
    import kotlinx.coroutines.tasks.await

    object ImageLabelingHelper {

        // Funzione sospesa per non bloccare la UI
        suspend fun getLabels(bitmap: Bitmap): List<String> {
            // Usa il modello di base (on-device, offline)
            val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)

            return try {
                val labels = labeler.process(image).await()
                // Filtra per confidenza (es. > 70%) e prendi i primi 3
                labels.filter { it.confidence > 0.7f }
                    .sortedByDescending { it.confidence }
                    .take(3)
                    .map { it.text }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
