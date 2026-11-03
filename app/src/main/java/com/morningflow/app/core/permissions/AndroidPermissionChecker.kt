package com.morningflow.app.core.permissions

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** Production permission checker shared by feature data sources. */
class AndroidPermissionChecker(context: Context) : PermissionChecker {
    private val appContext = context.applicationContext

    override fun hasAll(permissions: Collection<String>): Boolean =
        permissions.all { permission ->
            ContextCompat.checkSelfPermission(appContext, permission) ==
                PackageManager.PERMISSION_GRANTED
        }
}
