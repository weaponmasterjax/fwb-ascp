package com.android.internal.app;

oneway interface IGameSpaceCallback {
    void onGameStart(String packageName);
    void onGameLeave();
}
