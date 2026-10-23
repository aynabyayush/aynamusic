package com.ayush.aynamusic.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Size
import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import com.ayush.aynamusic.data.model.LocalArtwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads embedded album art from on-device audio without pre-generating thumbnails. */
class LocalArtworkFetcher(
    private val data: LocalArtwork,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val context = options.context
        val bitmap = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(data.audioUri, Size(512, 512), null)
            } else {
                null
            }
        }.getOrNull() ?: extractEmbedded(data.audioUri.toString(), context)

        bitmap?.let {
            DrawableResult(
                drawable = it.toDrawable(context.resources),
                isSampled = true,
                dataSource = DataSource.DISK
            )
        }
    }

    private fun extractEmbedded(uri: String, context: android.content.Context): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, android.net.Uri.parse(uri))
            val bytes = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 768 || bounds.outHeight / sample > 768) {
                sample *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    class Factory : Fetcher.Factory<LocalArtwork> {
        override fun create(data: LocalArtwork, options: Options, imageLoader: ImageLoader): Fetcher =
            LocalArtworkFetcher(data, options)
    }
}
