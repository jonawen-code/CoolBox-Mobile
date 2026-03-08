package com.example.coolbox.mobile;

import java.lang.System;

@kotlin.Metadata(mv = {1, 8, 0}, k = 1, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0002\b\u00c7\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0006\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020\tH\u0002J\u000e\u0010\n\u001a\u00020\u00042\u0006\u0010\b\u001a\u00020\tJ\u0016\u0010\u000b\u001a\u00020\f2\u0006\u0010\b\u001a\u00020\t2\u0006\u0010\r\u001a\u00020\u0004R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000e"}, d2 = {"Lcom/example/coolbox/mobile/SettingsManager;", "", "()V", "KEY_SERVER_URL", "", "PREFS_NAME", "getPrefs", "Landroid/content/SharedPreferences;", "context", "Landroid/content/Context;", "getServerUrl", "setServerUrl", "", "url", "app_debug"})
public final class SettingsManager {
    @org.jetbrains.annotations.NotNull
    public static final com.example.coolbox.mobile.SettingsManager INSTANCE = null;
    private static final java.lang.String PREFS_NAME = "coolbox_settings";
    private static final java.lang.String KEY_SERVER_URL = "server_url";
    
    private SettingsManager() {
        super();
    }
    
    private final android.content.SharedPreferences getPrefs(android.content.Context context) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final java.lang.String getServerUrl(@org.jetbrains.annotations.NotNull
    android.content.Context context) {
        return null;
    }
    
    public final void setServerUrl(@org.jetbrains.annotations.NotNull
    android.content.Context context, @org.jetbrains.annotations.NotNull
    java.lang.String url) {
    }
}