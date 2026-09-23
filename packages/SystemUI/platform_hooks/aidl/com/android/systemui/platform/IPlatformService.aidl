package com.android.systemui.platform;

import android.os.Bundle;
import com.android.systemui.platform.IPlatformCallback;

interface IPlatformService {

    void toggle(String feature);

    void setEnabled(String feature, boolean enabled);

    void setValue(String feature, int value);

    void performAction(String feature, String param);

    Bundle getState(String feature);

    Bundle getAllStates();

    String[] getSupportedFeatures();

    void registerCallback(IPlatformCallback callback);
    void unregisterCallback(IPlatformCallback callback);
}
