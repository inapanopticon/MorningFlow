package com.morningflow.app.core.data.calendar

import android.database.MatrixCursor
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import com.morningflow.app.core.data.calendar.impl.AndroidCalendarRepository
import com.morningflow.app.core.data.calendar.impl.CalendarInstancesClient
import com.morningflow.app.core.model.DataResult
import com.morningflow.app.core.permissions.PermissionChecker
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidCalendarRepositoryTest {
    private val now = Instant.parse("2026-09-29T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun readsRemainingVisibleEventsFromNowToNextMidnight() = runTest {
        val client = FakeCalendarInstancesClient(
            cursor = cursorWithRows(
                row("Future", "2026-09-29T12:00:00Z", "2026-09-29T13:00:00Z"),
                row("All day", "2026-09-29T00:00:00Z", "2026-09-30T00:00:00Z", allDay = true),
                row("Current", "2026-09-29T09:00:00Z", "2026-09-29T11:00:00Z"),
                row("Ended", "2026-09-29T08:00:00Z", "2026-09-29T09:00:00Z"),
                row(
                    "Canceled",
                    "2026-09-29T13:00:00Z",
                    "2026-09-29T14:00:00Z",
                    status = CalendarContract.Instances.STATUS_CANCELED,
                ),
                row(
                    "Declined",
                    "2026-09-29T14:00:00Z",
                    "2026-09-29T15:00:00Z",
                    attendeeStatus = CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED,
                ),
            ),
        )

        val result = repository(client).getRemainingTodayEvents()

        assertEquals(
            listOf("All day", "Current", "Future"),
            (result as DataResult.Success).value.map { it.title },
        )
        assertEquals(now.toEpochMilli(), client.beginEpochMillis)
        assertEquals(Instant.parse("2026-09-30T00:00:00Z").toEpochMilli(), client.endEpochMillis)
        assertTrue(client.projection.contains(CalendarContract.Instances.TITLE))
        assertTrue(client.projection.contains(CalendarContract.Instances.BEGIN))
        assertTrue(client.projection.contains(CalendarContract.Instances.END))
        assertTrue(client.projection.contains(CalendarContract.Instances.ALL_DAY))
        assertFalse(client.projection.contains(CalendarContract.Instances.DESCRIPTION))
        assertFalse(client.projection.contains(CalendarContract.Instances.EVENT_LOCATION))
    }

    @Test
    fun noRowsReturnsSuccessWithAnEmptyList() = runTest {
        val result = repository(FakeCalendarInstancesClient(cursor = cursorWithRows()))
            .getRemainingTodayEvents()

        assertTrue(result is DataResult.Success && result.value.isEmpty())
    }

    @Test
    fun permissionDeniedStopsBeforeQueryingProvider() = runTest {
        val client = FakeCalendarInstancesClient(cursor = cursorWithRows())
        val result = repository(client, permissionGranted = false).getRemainingTodayEvents()

        assertEquals(DataResult.Unavailable("PERMISSION_DENIED"), result)
        assertFalse(client.wasQueried)
    }

    @Test
    fun nullProviderCursorBecomesProviderUnavailable() = runTest {
        val result = repository(FakeCalendarInstancesClient(cursor = null)).getRemainingTodayEvents()

        assertEquals(DataResult.Unavailable("PROVIDER_UNAVAILABLE"), result)
    }

    @Test
    fun providerExceptionBecomesQueryFailure() = runTest {
        val client = FakeCalendarInstancesClient(
            cursor = cursorWithRows(),
            error = IllegalStateException("controlled provider failure"),
        )

        val result = repository(client).getRemainingTodayEvents()

        assertEquals(DataResult.Unavailable("QUERY_FAILED"), result)
    }

    private fun repository(
        client: FakeCalendarInstancesClient,
        permissionGranted: Boolean = true,
    ) = AndroidCalendarRepository(
        context = context,
        clock = clock,
        permissionChecker = PermissionChecker { permissions -> permissionGranted },
        instancesClient = client,
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun row(
        title: String,
        startsAt: String,
        endsAt: String,
        allDay: Boolean = false,
        status: Int = CalendarContract.Instances.STATUS_CONFIRMED,
        attendeeStatus: Int = CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED,
    ): Array<Any?> = arrayOf(
        title,
        Instant.parse(startsAt).toEpochMilli(),
        Instant.parse(endsAt).toEpochMilli(),
        if (allDay) 1 else 0,
        status,
        attendeeStatus,
    )

    private fun cursorWithRows(vararg rows: Array<Any?>): MatrixCursor = MatrixCursor(PROJECTION).apply {
        rows.forEach(::addRow)
    }

    private class FakeCalendarInstancesClient(
        private val cursor: MatrixCursor?,
        private val error: Throwable? = null,
    ) : CalendarInstancesClient {
        var wasQueried = false
        lateinit var projection: Array<String>
        var beginEpochMillis: Long = 0L
        var endEpochMillis: Long = 0L

        override fun query(
            projection: Array<String>,
            beginEpochMillis: Long,
            endEpochMillis: Long,
        ): android.database.Cursor? {
            wasQueried = true
            this.projection = projection
            this.beginEpochMillis = beginEpochMillis
            this.endEpochMillis = endEpochMillis
            error?.let { throw it }
            return cursor
        }
    }

    private companion object {
        val PROJECTION = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.STATUS,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
        )
    }
}
