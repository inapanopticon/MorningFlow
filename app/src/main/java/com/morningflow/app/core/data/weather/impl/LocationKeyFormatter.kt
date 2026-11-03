package com.morningflow.app.core.data.weather.impl

import com.morningflow.app.core.model.location.LocationSnapshot
import java.util.Locale

/** Uses rounded coordinates so small approximate-location jitter does not split the cache. */
internal object LocationKeyFormatter {
    fun from(location: LocationSnapshot): String =
        String.format(Locale.US, "%.2f,%.2f", location.latitude, location.longitude)
}
