/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.wm;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Slog;
import android.widget.Toast;

import com.android.internal.app.IGameSpaceCallback;
import com.android.internal.app.IGameSpaceService;
import com.android.server.CustomServiceInjector;
import com.android.server.UiThread;
import com.android.server.am.ActivityManagerService;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class GameSpaceService extends IGameSpaceService.Stub {

    private static final String TAG = "GameSpaceService";
    private static final boolean DEBUG = false;

    private static final String GAME_LIST_KEY = "gamespace_game_list";
    private static final String DENIED_LIST_KEY = "gamespace_denied_list";
    private static final String KEY_GAMING_MODE_ACTIVE = "gaming_mode_active";

    private static GameSpaceService sInstance;

    private final Context mContext;
    private final ActivityManagerService mActivityManager;
    private final PackageManager mPackageManager;
    private final CopyOnWriteArrayList<IGameSpaceCallback> mCallbacks = new CopyOnWriteArrayList<>();
    private final Map<String, String> mGameList = Collections.synchronizedMap(new HashMap<>());
    private final Set<String> mDeniedList = Collections.synchronizedSet(new HashSet<>());

    private final HandlerThread mBgThread = new HandlerThread("GameSpaceBg");
    private final Handler mBgHandler;

    private String mCurrentGame;
    private Runnable mPendingBoost;

    private GameSpaceService(Context context, ActivityManagerService am) {
        mContext = context;
        mActivityManager = am;
        mPackageManager = context.getPackageManager();

        mBgThread.start();
        mBgHandler = new Handler(mBgThread.getLooper());

        loadGameList();
        loadDeniedList();
        registerGameListObserver();
        registerPackageReceiver();
    }

    public static void systemReady() {
        if (sInstance == null) {
            Context context = CustomServiceInjector.getCtx();
            ActivityManagerService am = CustomServiceInjector.getAm();
            if (context == null || am == null) {
                Slog.w(TAG, "CustomServiceInjector not ready yet for GameSpaceService");
                return;
            }
            sInstance = new GameSpaceService(context, am);
            ServiceManager.addService("game_space", sInstance);
            Slog.i(TAG, "GameSpaceService initialized");
        }
    }

    public static GameSpaceService get() {
        return sInstance;
    }

    private void loadGameList() {
        String raw = Settings.System.getStringForUser(mContext.getContentResolver(),
                GAME_LIST_KEY, UserHandle.USER_CURRENT);
        Map<String, String> parsed = parseGameList(raw);
        synchronized (mGameList) {
            mGameList.clear();
            mGameList.putAll(parsed);
        }
        mBgHandler.post(() -> {
            if (mCurrentGame != null && isGame(mCurrentGame)) {
                boostGame(isGameInPerfMode(mCurrentGame));
            }
        });
    }

    private void registerGameListObserver() {
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(GAME_LIST_KEY),
                false,
                new ContentObserver(mBgHandler) {
                    @Override
                    public void onChange(boolean selfChange) {
                        loadGameList();
                    }
                },
                UserHandle.USER_ALL
        );

        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(DENIED_LIST_KEY),
                false,
                new ContentObserver(mBgHandler) {
                    @Override
                    public void onChange(boolean selfChange) {
                        loadDeniedList();
                    }
                },
                UserHandle.USER_ALL
        );
    }

    private void loadDeniedList() {
        String raw = Settings.System.getStringForUser(mContext.getContentResolver(),
                DENIED_LIST_KEY, UserHandle.USER_CURRENT);
        Set<String> parsed = parseDeniedList(raw);
        synchronized (mDeniedList) {
            mDeniedList.clear();
            mDeniedList.addAll(parsed);
        }
    }

    private Set<String> parseDeniedList(String raw) {
        Set<String> set = new HashSet<>();
        if (raw == null || raw.isEmpty()) return set;
        for (String pkg : raw.split(";")) {
            String trimmed = pkg.trim();
            if (!trimmed.isEmpty() && trimmed.matches("[a-zA-Z0-9_.]+")) {
                set.add(trimmed);
            }
        }
        return set;
    }

    private void writeDeniedList() {
        StringBuilder sb = new StringBuilder();
        synchronized (mDeniedList) {
            for (String pkg : mDeniedList) {
                if (sb.length() > 0) sb.append(';');
                sb.append(pkg);
            }
        }
        Settings.System.putStringForUser(mContext.getContentResolver(),
                DENIED_LIST_KEY, sb.toString(), UserHandle.USER_CURRENT);
    }

    public boolean isDenied(String packageName) {
        if (packageName == null) return false;
        synchronized (mDeniedList) {
            return mDeniedList.contains(packageName);
        }
    }

    public void addDenied(String packageName) {
        if (packageName == null) return;
        synchronized (mDeniedList) {
            if (!mDeniedList.add(packageName)) return;
        }
        writeDeniedList();
    }

    public void removeDenied(String packageName) {
        if (packageName == null) return;
        synchronized (mDeniedList) {
            if (!mDeniedList.remove(packageName)) return;
        }
        writeDeniedList();
    }

    public boolean isGame(String packageName) {
        if (packageName == null) return false;
        synchronized (mGameList) {
            return mGameList.containsKey(packageName);
        }
    }

    public boolean isGameInPerfMode(String packageName) {
        if (packageName == null) return false;
        synchronized (mGameList) {
            return "2".equals(mGameList.get(packageName));
        }
    }

    private Map<String, String> parseGameList(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) return map;

        for (String entry : raw.split(";")) {
            String[] parts = entry.split("=");
            if (parts.length == 2
                    && parts[0].matches("[a-zA-Z0-9_.]+")
                    && parts[1].matches("\\d+")) {
                map.put(parts[0].trim(), parts[1].trim());
            }
        }
        return map;
    }

    private void updateGameList(String packageName, boolean add) {
        ContentResolver cr = mContext.getContentResolver();
        String raw = Settings.System.getStringForUser(cr, GAME_LIST_KEY, UserHandle.USER_CURRENT);
        Map<String, String> gameMap = parseGameList(raw);

        boolean modified;
        if (add) {
            modified = !"2".equals(gameMap.get(packageName));
            if (modified) gameMap.put(packageName, "2");
        } else {
            modified = gameMap.remove(packageName) != null;
        }

        if (modified) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : gameMap.entrySet()) {
                if (sb.length() > 0) sb.append(';');
                sb.append(e.getKey()).append('=').append(e.getValue());
            }
            Settings.System.putStringForUser(cr, GAME_LIST_KEY, sb.toString(),
                    UserHandle.USER_CURRENT);
            synchronized (mGameList) {
                if (add) mGameList.put(packageName, "2");
                else mGameList.remove(packageName);
            }
        }
    }

    private boolean isAutoDetectEnabled() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                "gamespace_auto_game_detect", 1,
                UserHandle.USER_CURRENT) != 0;
    }

    private void registerPackageReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        filter.addDataScheme("package");

        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String pkg = intent.getData() != null
                        ? intent.getData().getSchemeSpecificPart() : null;
                if (pkg == null) return;

                mBgHandler.post(() -> {
                    String action = intent.getAction();
                    if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
                        if (!isAutoDetectEnabled()) return;
                        if (isDenied(pkg)) return;
                        if (isGame(pkg)) return;
                        if (isGamePackage(pkg)) {
                            String label = getAppLabel(pkg);
                            updateGameList(pkg, true);
                            UiThread.getHandler().post(
                                    () -> Toast.makeText(
                                            mContext,
                                            mContext.getString(com.android.internal.R.string.gamespace_new_game_added, label),
                                            Toast.LENGTH_LONG
                                    ).show());
                        }
                    } else if (Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(action)) {
                        updateGameList(pkg, false);
                    }
                });
            }
        }, filter);
    }

    private boolean isGamePackage(String pkg) {
        try {
            ApplicationInfo info = mPackageManager.getApplicationInfo(
                    pkg, PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA));
            return info.category == ApplicationInfo.CATEGORY_GAME;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private String getAppLabel(String pkg) {
        try {
            return mPackageManager
                    .getApplicationLabel(mPackageManager.getApplicationInfo(
                            pkg, PackageManager.ApplicationInfoFlags.of(0)))
                    .toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private void startOverlay() {
        final String currentGame = mCurrentGame;
        if (currentGame == null) return;

        mBgHandler.post(() -> {
            if (mPendingBoost != null) {
                mBgHandler.removeCallbacks(mPendingBoost);
            }

            if (isGameInPerfMode(currentGame)) {
                mPendingBoost = () -> boostGame(true);
                mBgHandler.postDelayed(mPendingBoost, 500);
            }

            UiThread.getHandler().post(
                    () -> dispatchGameState(true, currentGame));
        });
    }

    private void stopOverlay() {
        mBgHandler.post(() -> {
            if (mPendingBoost != null) {
                mBgHandler.removeCallbacks(mPendingBoost);
                mPendingBoost = null;
            }
            UiThread.getHandler().post(
                    () -> dispatchGameState(false, null));
            boostGame(false);
        });
    }

    private void dispatchGameState(boolean active, String packageName) {
        Settings.Secure.putIntForUser(mContext.getContentResolver(),
                KEY_GAMING_MODE_ACTIVE, active ? 1 : 0, UserHandle.USER_CURRENT);

        for (IGameSpaceCallback callback : mCallbacks) {
            try {
                if (active && packageName != null) {
                    callback.onGameStart(packageName);
                } else {
                    callback.onGameLeave();
                }
            } catch (Exception e) {
                Slog.w(TAG, "Removing dead callback", e);
                mCallbacks.remove(callback);
            }
        }
    }

    private void boostGame(boolean enable) {
        int perfByUser = Settings.System.getIntForUser(
                mContext.getContentResolver(), "power_mode_perf_by_user", 0,
                UserHandle.USER_CURRENT);
        if (perfByUser == 1) return;

        Settings.System.putIntForUser(mContext.getContentResolver(),
                "persist.sys.power_mode_perf", enable ? 1 : 0,
                UserHandle.USER_CURRENT);
        SystemProperties.set("persist.sys.power_mode_perf", enable ? "1" : "0");
    }

    public void onAppFocusChanged(ActivityRecord record, Task task) {
        if (record == null || record.packageName == null) return;

        String packageName = record.packageName;

        mBgHandler.post(() -> {
            boolean gameActive = mCurrentGame != null
                    && mActivityManager.isPackageTopApp(mCurrentGame);

            if (task != null && task.getWindowingMode() == WINDOWING_MODE_FREEFORM && gameActive) {
                if (DEBUG) Slog.d(TAG, "Freeform focused but game still TOP_APP, ignoring.");
                return;
            }

            boolean isGame = isGame(packageName);
            boolean shouldStartOverlay = false;
            boolean shouldStopOverlay = false;

            if (isGame) {
                if (!packageName.equals(mCurrentGame)) {
                    if (mCurrentGame != null) {
                        shouldStopOverlay = true;
                    }
                    mCurrentGame = packageName;
                    shouldStartOverlay = true;
                }
            } else if (mCurrentGame != null) {
                mCurrentGame = null;
                shouldStopOverlay = true;
            }

            if (shouldStopOverlay) stopOverlay();
            if (shouldStartOverlay) startOverlay();
        });
    }

    public void removeTask(Task task, String reason) {
        if (task == null) return;

        mBgHandler.post(() -> {
            ActivityRecord top = task.getTopMostActivity();

            if (mCurrentGame != null && top != null
                    && mCurrentGame.equals(top.packageName)) {
                if (DEBUG) Slog.d(TAG, "removeTask: clearing active game " + mCurrentGame);
                mCurrentGame = null;
                stopOverlay();
            }
        });
    }

    public void onKeyguardChanged(boolean showing) {
        mBgHandler.post(() -> {
            if (mCurrentGame == null) return;

            if (showing) {
                stopOverlay();
            } else {
                startOverlay();
            }
        });
    }

    @Override
    public void registerCallback(IGameSpaceCallback callback) {
        if (callback == null || mCallbacks.contains(callback)) return;

        mCallbacks.add(callback);

        try {
            IBinder binder = callback.asBinder();
            binder.linkToDeath(() -> {
                mCallbacks.remove(callback);
                if (DEBUG) Slog.d(TAG, "Callback died, removed");
            }, 0);
        } catch (RemoteException e) {
            mCallbacks.remove(callback);
        }
    }

    @Override
    public void unregisterCallback(IGameSpaceCallback callback) {
        mCallbacks.remove(callback);
    }
}
