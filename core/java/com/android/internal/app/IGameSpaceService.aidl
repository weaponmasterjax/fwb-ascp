package com.android.internal.app;

import com.android.internal.app.IGameSpaceCallback;

interface IGameSpaceService {
    void registerCallback(IGameSpaceCallback callback);
    void unregisterCallback(IGameSpaceCallback callback);
}
