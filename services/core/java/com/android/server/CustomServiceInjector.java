/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server;

import android.content.Context;

import com.android.server.am.ActivityManagerService;
import com.android.server.pm.PackageManagerService;
import com.android.server.wm.ActivityTaskManagerService;
import com.android.server.wm.WindowManagerService;

/**
 * Dependency injector providing system services to custom features.
 */
public class CustomServiceInjector {
    private static CustomServiceInjector sInstance = null;

    private Context mContext;
    private ActivityManagerService mActivityManagerService;
    private WindowManagerService mWindowManagerService;
    private PackageManagerService mPackageManagerService;

    protected CustomServiceInjector() {
    }

    public static synchronized CustomServiceInjector get() {
        if (sInstance == null) {
            sInstance = new CustomServiceInjector();
        }
        return sInstance;
    }

    public static synchronized CustomServiceInjector init(Context context) {
        CustomServiceInjector injector = get();
        injector.mContext = context;
        return injector;
    }

    public static void injectActivityManagerService(ActivityManagerService ams) {
        get().mActivityManagerService = ams;
    }

    public static void injectWindowManagerService(WindowManagerService wms) {
        get().mWindowManagerService = wms;
    }

    public static void injectPackageManagerService(PackageManagerService pm) {
        get().mPackageManagerService = pm;
    }

    public void setCtx(Context context) {
        mContext = context;
    }

    public void setActivityManagerService(ActivityManagerService ams) {
        mActivityManagerService = ams;
    }

    public void setWindowManagerService(WindowManagerService wms) {
        mWindowManagerService = wms;
    }

    public void setPackageManagerService(PackageManagerService pm) {
        mPackageManagerService = pm;
    }

    public Context getContext() {
        return mContext;
    }

    public ActivityManagerService getActivityManagerService() {
        return mActivityManagerService;
    }

    public WindowManagerService getWindowManagerService() {
        return mWindowManagerService;
    }

    public PackageManagerService getPackageManagerService() {
        return mPackageManagerService;
    }

    public ActivityTaskManagerService getActivityTaskManagerService() {
        return mActivityManagerService != null ? mActivityManagerService.mActivityTaskManager : null;
    }

    public static Context getCtx() {
        return get().getContext();
    }

    public static ActivityManagerService getAm() {
        return get().getActivityManagerService();
    }

    public static WindowManagerService getWm() {
        return get().getWindowManagerService();
    }

    public static PackageManagerService getPm() {
        return get().getPackageManagerService();
    }

    public static ActivityTaskManagerService getAtm() {
        return get().getActivityTaskManagerService();
    }

    public static void earlySystemReady() {
        try {
            com.android.server.wm.GameSpaceService.systemReady();
        } catch (Throwable e) {
            android.util.Slog.e("CustomServiceInjector", "Failed to start GameSpaceService", e);
        }
    }

    public static void systemReady() {
        try {
            com.android.server.obscura.ObscuraService.systemReady();
        } catch (Throwable e) {
            android.util.Slog.e("CustomServiceInjector", "Failed to start ObscuraService", e);
        }
    }
}
