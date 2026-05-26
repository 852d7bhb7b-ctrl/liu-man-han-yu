package com.liumanhanyu.app

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

/**
 * OCR 引擎：识别屏幕截图中的非中文文本块
 * ChineseTextRecognizerOptions 同时支持拉丁字母和中文
 */
object OcrEngine {

    private val recognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )

    fun extractForeignText(bitmap: Bitmap, onResult: (List<Pair<Rect, String>>) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val results = mutableListOf<Pair<Rect, String>>()
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val text = line.text.trim()
                        if (text.isNotEmpty() && text.any { it.isLetter() && it !in '一'..'鿿' }) {
                            line.boundingBox?.let { results.add(Rect(it) to text) }
                        }
                    }
                }
                onResult(results)
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }

    fun close() {
        recognizer.close()
    }
}
