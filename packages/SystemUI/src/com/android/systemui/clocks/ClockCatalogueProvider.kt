/*
 * Copyright (C) 2026 The DescartesOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.clocks

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

class ClockCatalogueProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        requireNotNull(context).enforceCallingOrSelfPermission(
            "android.permission.CUSTOMIZE_SYSTEM_UI", "Clock catalogue")
        if (method != "clock_layouts") return null
        return Bundle().apply { putIntArray("layouts", ClockStyle.galleryLayouts()) }
    }

    override fun getType(uri: Uri): String? = null
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?,
        selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
