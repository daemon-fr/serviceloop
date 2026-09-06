package com.v16studio.serviceloop.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CustomerEntity::class, SiteEntity::class, EquipmentEntity::class,
        ServicePlanEntity::class, ServiceObligationEntity::class,
        TemplateSnapshotEntity::class, ChecklistItemSnapshotEntity::class,
        WorkingVisitEntity::class, WorkItemEntity::class,
        WorkItemPublicDraftEntity::class, WorkItemPrivateDraftEntity::class,
        WorkingResponseEntity::class, AttachmentEntity::class,
        FollowUpEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ServiceLoopDatabase : RoomDatabase() {
    abstract fun serviceLoopDao(): ServiceLoopDao

    companion object {
        fun open(context: Context): ServiceLoopDatabase = Room.databaseBuilder(
            context.applicationContext,
            ServiceLoopDatabase::class.java,
            "serviceloop.db",
        ).build()
    }
}
