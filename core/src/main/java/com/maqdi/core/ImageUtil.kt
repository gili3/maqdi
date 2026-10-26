package com.maqdi.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

object ImageUtil {
    private const val MAX_BYTES = 900_000
    private const val MIN_SIDE = 320

    /** يصغّر الصورة إلى 1024 بكسل كحد أقصى ويضغطها JPEG بحجم أقل من 900KB */
    suspend fun compress(ctx: Context, uri: Uri, maxSide: Int = 1024): ByteArray = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw ApiException("تعذّر قراءة الصورة.")
        // يضغط بجودة متناقصة، ثم يصغّر الأبعاد تدريجيًا إن بقي الحجم فوق الحد (قواعد التخزين: 1MB)
        var side = maxSide
        var bytes: ByteArray
        while (true) {
            val scale = side.toFloat() / maxOf(bmp.width, bmp.height)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true,
                )
            } else bmp
            var q = 80
            do {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, q, out)
                bytes = out.toByteArray()
                q -= 10
            } while (bytes.size > MAX_BYTES && q >= 30)
            if (scaled !== bmp) scaled.recycle()
            if (bytes.size <= MAX_BYTES || side <= MIN_SIDE) break
            side = (side * 3 / 4).coerceAtLeast(MIN_SIDE)
        }
        bmp.recycle()
        if (bytes.size > 1_000_000) throw ApiException("الصورة كبيرة جدًا. اختر صورة أخرى.")
        bytes
    }

    /** يرفع الصورة ويرجع مسارها لتُحفظ في المنتج */
    suspend fun uploadProductImage(ctx: Context, storeId: String, uri: Uri): String {
        val bytes = compress(ctx, uri)
        val path = "products/$storeId/${UUID.randomUUID()}/main.jpg"
        val meta = StorageMetadata.Builder().setContentType("image/jpeg").build()
        FirebaseStorage.getInstance().reference.child(path).putBytes(bytes, meta).await()
        return path
    }

    /** صورة غلاف المتجر (من الإدارة): stores/{storeId}/{id}/main.jpg */
    suspend fun uploadStoreImage(ctx: Context, storeId: String, uri: Uri): String {
        val bytes = compress(ctx, uri)
        val path = "stores/$storeId/${UUID.randomUUID()}/main.jpg"
        val meta = StorageMetadata.Builder().setContentType("image/jpeg").build()
        FirebaseStorage.getInstance().reference.child(path).putBytes(bytes, meta).await()
        return path
    }
}
