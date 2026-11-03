package com.morningflow.app.core.data.location.api

import android.Manifest

/** Location permissions required by the data source; request UI remains outside the data layer. */
object LocationPermissionSpec {
    val requiredPermissions: List<String> = listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
}
