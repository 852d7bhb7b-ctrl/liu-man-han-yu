package com.liumanhanyu.app

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

/**
 * OCR 引擎：截图识别非中文文本块
 * 覆盖海报文字、图片内文字、视频字幕等节点文本采集不到的内容
 */
object OcrEngine {

    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    fun extractForeignText(bitmap: Bitmap, onResult: (List<Pair<Rect, String>>) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val results = mutableListOf<Pair<Rect, String>>()
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val text = line.text.trim()
                        if (text.isEmpty()) continue
                        // 至少包含一个非中文的字母字符
                        val hasForeignLetter = text.any {
                            it.isLetter() && it !in '一'..'鿿' && it !in '぀'..'ヿ' && it !in '가'..'힯'
                        }
                        if (hasForeignLetter) {
                            line.boundingBox?.let { results.add(Rect(it) to text) }
                        }
                    }
                }
                onResult(results)
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    fun close() { recognizer.close() }
}
