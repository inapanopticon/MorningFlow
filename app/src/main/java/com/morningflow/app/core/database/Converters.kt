package com.morningflow.app.core.database

import androidx.room.TypeConverter
import com.morningflow.app.core.model.Behaviour
import com.morningflow.app.core.model.BehaviourKey
import com.morningflow.app.core.model.BehaviourMode
import com.morningflow.app.core.model.CompletionKind
import com.morningflow.app.core.model.ModuleResult
import com.morningflow.app.core.model.ModuleType
import com.morningflow.app.core.model.SessionStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

private const val LIST_DELIMITER = ""
private const val ENTRY_DELIMITER = ""

private fun String.splitOrEmpty(delimiter: String): List<String> =
    if (isEmpty()) emptyList() else split(delimiter)

/**
 * Room stores primitives only, so each richer type used by an entity needs an
 * explicit, reversible encoding. Behaviours keep their identity, mode and lock state;
 * their narration and duration live with [BehaviourKey] and are never persisted.
 */
class Converters {
    @TypeConverter
    fun fromInstant(value: Instant): Long = value.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long): Instant = Instant.ofEpochMilli(value)

    @TypeConverter
    fun fromLocalTime(value: LocalTime): String = value.toString()

    @TypeConverter
    fun toLocalTime(value: String): LocalTime = LocalTime.parse(value)

    @TypeConverter
    fun fromDayOfWeekSet(value: Set<DayOfWeek>): String =
        value.joinToString(LIST_DELIMITER) { it.name }

    @TypeConverter
    fun toDayOfWeekSet(value: String): Set<DayOfWeek> =
        value.splitOrEmpty(LIST_DELIMITER).map(DayOfWeek::valueOf).toSet()

    @TypeConverter
    fun fromModuleType(value: ModuleType): String = value.name

    @TypeConverter
    fun toModuleType(value: String): ModuleType = ModuleType.valueOf(value)

    @TypeConverter
    fun fromSessionStatus(value: SessionStatus): String = value.name

    @TypeConverter
    fun toSessionStatus(value: String): SessionStatus = SessionStatus.valueOf(value)

    @TypeConverter
    fun fromBehaviourList(value: List<Behaviour>): String =
        value.joinToString(ENTRY_DELIMITER) { behaviour ->
            listOf(
                behaviour.id,
                behaviour.key.name,
                behaviour.mode.name,
                if (behaviour.locked) "1" else "0",
            ).joinToString(LIST_DELIMITER)
        }

    @TypeConverter
    fun toBehaviourList(value: String): List<Behaviour> =
        value.splitOrEmpty(ENTRY_DELIMITER).mapNotNull { entry ->
            val fields = entry.split(LIST_DELIMITER)
            // A step this version no longer knows (such as a retired one) is dropped
            // rather than failing the whole template.
            val key = BehaviourKey.entries.firstOrNull { it.name == fields[1] } ?: return@mapNotNull null
            val mode = when (fields[2]) {
                // Version 1 stored enabled as 1/0. Preserve those rows during migration.
                "1" -> Behaviour.defaultModeFor(key)
                "0" -> BehaviourMode.OFF
                else -> BehaviourMode.valueOf(fields[2])
            }
            Behaviour(
                id = fields[0],
                key = key,
                mode = mode,
                locked = fields[3] == "1",
            )
        }

    @TypeConverter
    fun fromModuleResultList(value: List<ModuleResult>): String =
        value.joinToString(ENTRY_DELIMITER) {
            "${it.moduleType.name}$LIST_DELIMITER${it.completionKind.name}"
        }

    @TypeConverter
    fun toModuleResultList(value: String): List<ModuleResult> =
        value.splitOrEmpty(ENTRY_DELIMITER).map { entry ->
            val (type, kind) = entry.split(LIST_DELIMITER)
            ModuleResult(ModuleType.valueOf(type), CompletionKind.valueOf(kind))
        }
}
