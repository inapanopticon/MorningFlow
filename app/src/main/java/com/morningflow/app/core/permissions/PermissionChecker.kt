package com.morningflow.app.core.permissions

/** Reads runtime permission state without owning the permission request UI. */
fun interface PermissionChecker {
    fun hasAll(permissions: Collection<String>): Boolean
}
