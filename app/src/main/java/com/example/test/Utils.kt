package com.example.test

import android.content.Context
import android.net.Uri
import android.view.View
import android.widget.Toast


object Utils {

    fun getContentType(context: Context, uri: Uri) = context.contentResolver.getType(uri)
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