package com.example.test

import android.content.Context
import android.net.Uri
import android.view.View
import android.widget.Toast
import java.io.IOException
import java.io.InputStream


object Utils {

    fun getJsonFromAssets(context: Context, fileName: String?): String? {
        val jsonString: String = try {
            val inputStream: InputStream = context.assets.open(fileName!!)
            val size: Int = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            String(buffer, Charsets.UTF_8)
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
        return jsonString
    }
}

fun Context.getMimeType(uri: Uri) = this.contentResolver.getType(uri)
fun Context.showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun View.show() {
    visibility = View.VISIBLE
}

fun View.hide() {
    visibility = View.INVISIBLE
}

fun View.gone() {
    visibility = View.GONE
}