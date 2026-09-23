package com.android.systemui.platform;

import android.os.Bundle;

oneway interface IPlatformCallback {
    void onStateChanged(String key, in Bundle state);
}
