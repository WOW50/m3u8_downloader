package com.vincent.m3u8Downloader.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * @Author: Vincent
 * @CreateAt: 2021/08/25 17:16
 * @Desc: SharedPreferences帮助类
 */
public class SpHelper {

    private static final String NULL_KEY = "NULL_KEY";
    private static final String TAG_NAME = "M3U8PreferenceHelper";

    private static SharedPreferences PREFERENCES;


    public static void init(Context context) {
        PREFERENCES = context.getSharedPreferences(TAG_NAME, Context.MODE_PRIVATE);
    }


    private static String checkKeyNonNull(String key) {
        if (key == null) {
            Log.e(NULL_KEY, "Key is null!!!");
            return NULL_KEY;
        }
        return key;
    }

    private static SharedPreferences.Editor newEditor() {
        return PREFERENCES.edit();
    }

    public static void putBoolean(@NonNull String key, boolean value) {
        newEditor().putBoolean(checkKeyNonNull(key), value).apply();
    }

    public static boolean getBoolean(@NonNull String key, boolean defValue) {
        return PREFERENCES.getBoolean(checkKeyNonNull(key), defValue);
    }

    public static void putInt(@NonNull String key, int value) {
        newEditor().putInt(checkKeyNonNull(key), value).apply();
    }

    public static int getInt(@NonNull String key, int defValue) {
        return PREFERENCES.getInt(checkKeyNonNull(key), defValue);
    }

    public static void putString(@NonNull String key, @Nullable String value) {
        newEditor().putString(checkKeyNonNull(key), value).apply();
    }

    public static String getString(@NonNull String key, @Nullable String defValue) {
        return PREFERENCES.getString(checkKeyNonNull(key), defValue);
    }


}