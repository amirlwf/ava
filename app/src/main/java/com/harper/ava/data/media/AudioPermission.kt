package com.harper.ava.data.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The only runtime permission Ava needs: audio-media access.
 * Android 13+ uses READ_MEDIA_AUDIO; older versions fall back to
 * READ_EXTERNAL_STORAGE (declared with maxSdkVersion 32 in the manifest).
 */
object AudioPermission {

    fun permissionName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, permissionName()) ==
            PackageManager.PERMISSION_GRANTED
}
