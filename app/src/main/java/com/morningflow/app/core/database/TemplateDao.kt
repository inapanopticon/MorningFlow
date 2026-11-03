package com.morningflow.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Transaction
    @Query("SELECT * FROM templates ORDER BY priority DESC")
    fun observeTemplatesWithModules(): Flow<List<TemplateWithModules>>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun getTemplate(id: String): TemplateEntity?

    @Query("SELECT COUNT(*) FROM templates")
    suspend fun countTemplates(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplate(template: TemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModules(modules: List<ModuleEntity>)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun deleteTemplate(id: String)

    @Query("DELETE FROM modules WHERE templateId = :templateId")
    suspend fun deleteModulesForTemplate(templateId: String)

    @Query("SELECT id FROM templates ORDER BY priority DESC LIMIT 1")
    suspend fun highestPriorityTemplateId(): String?

    @Query("SELECT id FROM templates WHERE isDefault = 1 LIMIT 1")
    suspend fun defaultTemplateId(): String?

    @Query("UPDATE templates SET isDefault = 0")
    suspend fun clearDefault()

    @Query("UPDATE templates SET isDefault = 1 WHERE id = :id")
    suspend fun markDefault(id: String)

    /**
     * Replaces a template's module list atomically so a save can never leave the
     * database with a template whose modules only partially match its new definition.
     */
    @Transaction
    suspend fun saveTemplateWithModules(template: TemplateEntity, modules: List<ModuleEntity>) {
        upsertTemplate(template)
        deleteModulesForTemplate(template.id)
        upsertModules(modules)
    }

    @Transaction
    suspend fun setDefault(id: String) {
        if (getTemplate(id) == null) return
        clearDefault()
        markDefault(id)
    }

    /**
     * Deletes a template and, if it was the default, hands that flag to [fallbackId].
     *
     * [fallbackId] itself is never deleted - it is the template that always exists. The
     * highest-priority survivor inherits instead when the fallback row is missing, which
     * only happens to data that predates that guarantee. One transaction, so the table is
     * never observable with a template list but no default in it.
     */
    @Transaction
    suspend fun deleteTemplateKeepingDefault(id: String, fallbackId: String) {
        if (id == fallbackId) return
        val deleted = getTemplate(id) ?: return
        deleteTemplate(id)
        if (!deleted.isDefault) return
        val heir = getTemplate(fallbackId)?.id ?: highestPriorityTemplateId() ?: return
        markDefault(heir)
    }

    /**
     * Inserts every starter template in one transaction so observers of
     * [observeTemplatesWithModules] see the full seeded set in a single emission
     * instead of one partial list per template inserted.
     */
    @Transaction
    suspend fun seedTemplates(entries: List<Pair<TemplateEntity, List<ModuleEntity>>>) {
        entries.forEach { (template, modules) -> saveTemplateWithModules(template, modules) }
    }
}
