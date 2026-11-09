package com.woodlands.mobile

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object ImageLoader {
    private val pool = Executors.newFixedThreadPool(6)
    private val main = Handler(Looper.getMainLooper())
    private val memory = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(context: Context, view: ImageView, value: String, fallback: Int) {
        view.setImageResource(fallback)
        view.tag = value
        val cached = memory.get(value)
        if (cached != null) {
            view.setImageBitmap(cached)
            return
        }
        val appContext = context.applicationContext
        pool.execute {
            var bmp = fetch(appContext, value)
            var tries = 0
            while (bmp == null && value.startsWith("http") && tries < 3) {
                Thread.sleep(6000)
                bmp = fetch(appContext, value)
                tries++
            }
            val result = bmp
            if (result != null) {
                memory.put(value, result)
                main.post { if (view.tag == value) view.setImageBitmap(result) }
            }
        }
    }

    private fun fetch(context: Context, value: String): Bitmap? {
        return try {
            val file = if (value.startsWith("local:")) {
                File(value.removePrefix("local:"))
            } else {
                val dir = File(context.filesDir, "image_cache").apply { mkdirs() }
                File(dir, value.hashCode().toString() + ".img")
            }
            if (!file.exists() && value.startsWith("http")) download(value, file)
            if (file.exists() && file.length() > 0) {
                val bmp = decode(file)
                if (bmp == null && !value.startsWith("local:")) file.delete()
                bmp
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun download(url: String, target: File) {
        val temp = File(target.parentFile, target.name + ".part")
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 70000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) WoodlandsMobile")
            conn.inputStream.use { input -> FileOutputStream(temp).use { out -> input.copyTo(out) } }
            temp.renameTo(target)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun decode(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

@Suppress("DEPRECATION")
internal fun MainActivity.pickImage(onPicked: (Uri) -> Unit) {
    imagePickCallback = onPicked
    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
        type = "image/*"
        addCategory(Intent.CATEGORY_OPENABLE)
    }
    startActivityForResult(Intent.createChooser(intent, "Choose a branch photo"), 7001)
}

internal fun MainActivity.prepareImage(uri: Uri, name: String): String? {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 2000 || bounds.outHeight / sample > 2000) sample *= 2
        val bmp = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val scale = 1280f / maxOf(bmp.width, bmp.height)
        val sized = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val dir = File(filesDir, "branch_images").apply { mkdirs() }
        val file = File(dir, "$name.jpg")
        FileOutputStream(file).use { sized.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        return file.absolutePath
    } catch (e: Exception) {
        return null
    }
}

internal fun MainActivity.setProductImage(view: ImageView, value: String) {
    if (value in productImageKeys) {
        view.setImageResource(imageRes(value))
        return
    }
    val path = remoteImagePath(value)
    val url = when {
        path.startsWith("http") -> path
        path.startsWith("/") -> ApiClient.WEB_BASE_URL + path.replace(" ", "%20")
        else -> ""
    }
    if (url.isBlank()) {
        view.setImageResource(R.drawable.app_logo_transparent)
        return
    }
    ImageLoader.load(this, view, url, R.drawable.app_logo_transparent)
}