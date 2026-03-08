package com.example.coolbox.mobile.data;

import java.lang.System;

@androidx.room.Database(entities = {com.example.coolbox.mobile.data.FoodEntity.class}, version = 6, exportSchema = false)
@kotlin.Metadata(mv = {1, 8, 0}, k = 1, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\b\'\u0018\u0000 \u00052\u00020\u0001:\u0001\u0005B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0003\u001a\u00020\u0004H&\u00a8\u0006\u0006"}, d2 = {"Lcom/example/coolbox/mobile/data/AppDatabase;", "Landroidx/room/RoomDatabase;", "()V", "foodDao", "Lcom/example/coolbox/mobile/data/FoodDao;", "Companion", "app_debug"})
public abstract class AppDatabase extends androidx.room.RoomDatabase {
    @org.jetbrains.annotations.NotNull
    public static final com.example.coolbox.mobile.data.AppDatabase.Companion Companion = null;
    @kotlin.jvm.Volatile
    private static volatile com.example.coolbox.mobile.data.AppDatabase INSTANCE;
    @org.jetbrains.annotations.NotNull
    private static final androidx.room.migration.Migration MIGRATION_4_5 = null;
    @org.jetbrains.annotations.NotNull
    private static final androidx.room.migration.Migration MIGRATION_5_6 = null;
    
    public AppDatabase() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull
    public abstract com.example.coolbox.mobile.data.FoodDao foodDao();
    
    @kotlin.Metadata(mv = {1, 8, 0}, k = 1, d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0006\u0010\u000b\u001a\u00020\fJ\u000e\u0010\r\u001a\u00020\u00042\u0006\u0010\u000e\u001a\u00020\u000fR\u0010\u0010\u0003\u001a\u0004\u0018\u00010\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\t\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\b\u00a8\u0006\u0010"}, d2 = {"Lcom/example/coolbox/mobile/data/AppDatabase$Companion;", "", "()V", "INSTANCE", "Lcom/example/coolbox/mobile/data/AppDatabase;", "MIGRATION_4_5", "Landroidx/room/migration/Migration;", "getMIGRATION_4_5", "()Landroidx/room/migration/Migration;", "MIGRATION_5_6", "getMIGRATION_5_6", "closeDatabase", "", "getDatabase", "context", "Landroid/content/Context;", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        public final void closeDatabase() {
        }
        
        @org.jetbrains.annotations.NotNull
        public final com.example.coolbox.mobile.data.AppDatabase getDatabase(@org.jetbrains.annotations.NotNull
        android.content.Context context) {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull
        public final androidx.room.migration.Migration getMIGRATION_4_5() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull
        public final androidx.room.migration.Migration getMIGRATION_5_6() {
            return null;
        }
    }
}