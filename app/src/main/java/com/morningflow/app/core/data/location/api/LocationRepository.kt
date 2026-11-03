package com.morningflow.app.core.data.location.api

import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.model.location.LocationSnapshot
import kotlinx.coroutines.flow.StateFlow

interface LocationRepository {
    val current: StateFlow<LocationSnapshot?>

    suspend fun refreshCurrent(): DataResult<LocationSnapshot>

    suspend fun getCached(): LocationSnapshot?
}
