package com.v16studio.serviceloop.ui

import android.content.Context
import com.v16studio.serviceloop.domain.DueServiceDateFilter
import com.v16studio.serviceloop.domain.DueServiceVisitFilter
import com.v16studio.serviceloop.domain.FollowUpDateFilter
import com.v16studio.serviceloop.domain.FollowUpStatusFilter
import com.v16studio.serviceloop.domain.VisitDateFilter
import com.v16studio.serviceloop.domain.VisitStatusFilter

/** Presentation-only filter memory. It is deliberately outside Room and backup/business data. */
internal class UiFilterPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun dueDate(default: DueServiceDateFilter): DueServiceDateFilter = read("due.date", default)
    fun dueVisit(default: DueServiceVisitFilter): DueServiceVisitFilter = read("due.visit", default)
    fun visitDate(default: VisitDateFilter): VisitDateFilter = read("visits.date", default)
    fun visitStatus(default: VisitStatusFilter): VisitStatusFilter = read("visits.status", default)
    fun followUpDate(default: FollowUpDateFilter): FollowUpDateFilter = read("followUps.date", default)
    fun followUpStatus(default: FollowUpStatusFilter): FollowUpStatusFilter = read("followUps.status", default)
    fun showOneTimeCustomers(): Boolean = preferences.getBoolean("register.showOneTime", false)

    fun saveDueDate(value: DueServiceDateFilter) = preferences.edit().putString("due.date", value.name).apply()
    fun saveDueVisit(value: DueServiceVisitFilter) = preferences.edit().putString("due.visit", value.name).apply()
    fun saveVisitDate(value: VisitDateFilter) = preferences.edit().putString("visits.date", value.name).apply()
    fun saveVisitStatus(value: VisitStatusFilter) = preferences.edit().putString("visits.status", value.name).apply()
    fun saveFollowUpDate(value: FollowUpDateFilter) = preferences.edit().putString("followUps.date", value.name).apply()
    fun saveFollowUpStatus(value: FollowUpStatusFilter) = preferences.edit().putString("followUps.status", value.name).apply()
    fun saveShowOneTimeCustomers(value: Boolean) = preferences.edit().putBoolean("register.showOneTime", value).apply()

    private inline fun <reified T : Enum<T>> read(key: String, default: T): T =
        preferences.getString(key, null)?.let { saved -> enumValues<T>().firstOrNull { it.name == saved } } ?: default

    private companion object {
        const val FILE_NAME = "serviceloop_ui_filter_preferences"
    }
}
