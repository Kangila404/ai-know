package org.aiknow.android.data

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** System photo picker supplies a scoped URI; no library-wide photo permission is needed. */
suspend fun recoveryPng(context:Context,uri:Uri):ByteArray=withContext(Dispatchers.IO) {
    val bitmap=if(Build.VERSION.SDK_INT>=28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver,uri)){decoder,info,_->
            require(info.size.width in 1..4096&&info.size.height in 1..4096){"4096×4096 이하 이미지를 선택해 주세요."}
            decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        context.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,options)}
        require(options.outWidth in 1..4096&&options.outHeight in 1..4096){"4096×4096 이하 이미지를 선택해 주세요."}
        requireNotNull(context.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it)})
    }
    try {
        val output=ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.PNG,100,output))
        output.toByteArray().also{require(it.size<=10*1024*1024){"PNG 변환 후 10MB 이하 이미지를 선택해 주세요."}}
    } finally {bitmap.recycle()}
}
