package com.jooshin.diary.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DiaryEntry::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun diaryDao(): DiaryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v1 -> v2 : 시작~종료(기간) 일정 지원. 기존 기록은 그대로 유지됩니다. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN endDateEpochDay INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE entries ADD COLUMN endTimeMinutes INTEGER NOT NULL DEFAULT -1")
            }
        }

        /** v2 -> v3 : 공유(동기화)용 필드 추가 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN uid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE entries ADD COLUMN deletedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE entries ADD COLUMN authorNick TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "diary.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    // 주의: fallbackToDestructiveMigration()을 쓰지 않는다.
                    // 그 옵션은 버전이 바뀌었는데 맞는 Migration이 없으면 조용히 DB를 통째로 지우고 새로 만든다.
                    // 앞으로 DB 구조를 바꿀 때는 반드시 위처럼 MIGRATION_x_y를 추가해서 기록이 보존되게 해야 한다.
                    .build().also { INSTANCE = it }
            }
    }
}
