/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.graphics.fonts;

import android.app.ActivityThread;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Helper class for managing dynamic system font overrides and configuration changes.
 *
 * @hide
 */
public final class DynamicFontHelper {

    private static final String TAG = "DynamicFontHelper";

    private static final DynamicFontHelper sInstance = new DynamicFontHelper();

    private volatile Resources sResources = null;
    private volatile String sFontFamily = "sans-serif";

    private static final String DEFAULT_FONT_ROOT = "google-sans-flex";

    private static final String[] DEFAULT_FONT_FALLBACKS = {
            "google-sans-flex",
            "google-sans",
            "sans-serif"
    };

    private static final Set<String> OVERRIDE_FONTS = Set.of(
            "google", "sans-serif", "gsf-", "variable", "system-ui", "roboto"
    );

    private static final Set<String> SYS_OVERRIDE_FONTS = Set.of(
            "serif", "monospace"
    );

    private static final Set<String> EXCLUDED_APPS = Set.of(
            "it.subito",
            "tv.arte.plus7",
            "com.google.android.gm"
    );

    private static final Set<String> SYS_OVERRIDE_APPS = Set.of(
            "com.android.settings",
            "com.android.systemui",
            "com.android.launcher3",
            "android"
    );

    private static final Map<String, Integer> WEIGHT_MAP = new ArrayMap<>();
    static {
        WEIGHT_MAP.put("thin", 100);
        WEIGHT_MAP.put("extralight", 200);
        WEIGHT_MAP.put("light", 300);
        WEIGHT_MAP.put("normal", 400);
        WEIGHT_MAP.put("regular", 400);
        WEIGHT_MAP.put("medium", 500);
        WEIGHT_MAP.put("semibold", 600);
        WEIGHT_MAP.put("bold", 700);
        WEIGHT_MAP.put("extrabold", 800);
        WEIGHT_MAP.put("black", 900);

        WEIGHT_MAP.put("variable-display-large-emphasized", 500);
        WEIGHT_MAP.put("variable-display-medium-emphasized", 500);
        WEIGHT_MAP.put("variable-display-small-emphasized", 500);
        WEIGHT_MAP.put("variable-headline-large-emphasized", 500);
        WEIGHT_MAP.put("variable-headline-medium-emphasized", 500);
        WEIGHT_MAP.put("variable-headline-small-emphasized", 500);
        WEIGHT_MAP.put("variable-title-large-emphasized", 500);
        WEIGHT_MAP.put("variable-title-medium-emphasized", 600);
        WEIGHT_MAP.put("variable-title-small-emphasized", 600);
        WEIGHT_MAP.put("variable-label-large-emphasized", 600);
        WEIGHT_MAP.put("variable-label-medium-emphasized", 600);
        WEIGHT_MAP.put("variable-label-small-emphasized", 600);
        WEIGHT_MAP.put("variable-body-large-emphasized", 500);
        WEIGHT_MAP.put("variable-body-medium-emphasized", 500);
        WEIGHT_MAP.put("variable-body-small-emphasized", 500);
        WEIGHT_MAP.put("variable-body-large", 400);
        WEIGHT_MAP.put("variable-body-medium", 400);
        WEIGHT_MAP.put("variable-body-small", 400);
        WEIGHT_MAP.put("variable-label-large", 500);
        WEIGHT_MAP.put("variable-label-medium", 500);
        WEIGHT_MAP.put("variable-label-small", 500);
        WEIGHT_MAP.put("variable-title-large", 400);
        WEIGHT_MAP.put("variable-title-medium", 500);
        WEIGHT_MAP.put("variable-title-small", 500);
        WEIGHT_MAP.put("variable-headline-large", 400);
        WEIGHT_MAP.put("variable-headline-medium", 400);
        WEIGHT_MAP.put("variable-headline-small", 400);
        WEIGHT_MAP.put("variable-display-large", 400);
        WEIGHT_MAP.put("variable-display-medium", 400);
        WEIGHT_MAP.put("variable-display-small", 400);
    }

    public static DynamicFontHelper get() {
        return sInstance;
    }

    private DynamicFontHelper() {}

    public static void onConfigurationChanged(Resources res) {
        get().handleOnConfiguration(res);
    }

    public static Typeface getOverrideTypeface(String fontToOverride) {
        return get().getOverrideTypefaceInternal(fontToOverride);
    }

    public static String getCurrentFontFamily() {
        return get().getCurrentFont();
    }

    private String getCurrentFont() {
        if (sResources == null) return sFontFamily;
        try {
            int configId = sResources.getIdentifier("config_bodyFontFamily", "string", "android");
            if (configId != 0) {
                String currFont = sResources.getString(configId);
                if (!TextUtils.equals(sFontFamily, currFont)) {
                    sFontFamily = currFont;
                    logger("Font changed to: " + sFontFamily);
                }
            }
        } catch (Exception e) {
            logger("getCurrentFont failed: " + e.getMessage());
        }
        return sFontFamily;
    }

    private static boolean containsAny(Set<String> set, String target) {
        for (String pattern : set) {
            if (target.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private Typeface getOverrideTypefaceInternal(String fontToOverride) {
        if (fontToOverride == null) return null;

        String pkgName = getCurrentPackageName();
        if (pkgName == null) return null;

        final boolean isSysPkg = SYS_OVERRIDE_APPS.contains(pkgName);

        if (EXCLUDED_APPS.contains(pkgName) && !isSysPkg) {
            logger("Excluded app, skipping override: " + pkgName);
            return null;
        }

        String currentFont = getCurrentFont();

        if (fontToOverride.matches("^" + Pattern.quote(currentFont) + "(-.*)?$")) {
            logger(fontToOverride + " matches current font root '" + currentFont + "', skipping override!");
            return null;
        }

        // Don't remap variable-* families when the default Google Sans Flex
        // font stack is active; let them pass through so optical sizing and
        // ROND axis values in fonts_customization.xml render correctly.
        if (fontToOverride.contains("variable") && currentFont.contains(DEFAULT_FONT_ROOT)) {
            logger("Default font active, passing through variable family: " + fontToOverride);
            return null;
        }

        boolean override = containsAny(OVERRIDE_FONTS, fontToOverride)
                || (isSysPkg && containsAny(SYS_OVERRIDE_FONTS, fontToOverride));
        if (!override) {
            logger("Not on override list, skipping override: " + fontToOverride);
            return null;
        }

        int adjustment = getFontWeightAdjustment();
        Typeface resolvedBase = resolveDefaultTypeface();
        return TypefaceFactory.create(fontToOverride, resolvedBase, adjustment);
    }

    public static Typeface resolveBaseTypeface() {
        return get().resolveDefaultTypeface();
    }

    private Typeface resolveDefaultTypeface() {
        // First try to resolve the currently active custom font directly
        String currentFont = getCurrentFont();
        if (currentFont != null && !currentFont.isEmpty()) {
            Typeface tf = Typeface.getSystemDefaultTypeface(currentFont);
            if (tf != null && tf != Typeface.DEFAULT) {
                logger("resolveDefaultTypeface: resolved current font '" + currentFont + "'");
                return tf;
            }
        }
        // Fall back through known default font aliases
        for (String family : DEFAULT_FONT_FALLBACKS) {
            Typeface tf = Typeface.getSystemDefaultTypeface(family);
            if (tf != null && tf != Typeface.DEFAULT) {
                logger("resolveDefaultTypeface: resolved via fallback '" + family + "'");
                return tf;
            }
        }
        logger("resolveDefaultTypeface: all fallbacks failed, using DEFAULT");
        return Typeface.DEFAULT;
    }

    private void handleOnConfiguration(Resources res) {
        sResources = res;
        String pkgName = getCurrentPackageName();
        if (pkgName == null || EXCLUDED_APPS.contains(pkgName)) return;
        logger("handleOnConfiguration: Changing default font to: " + Typeface.getFontName());
        Typeface.changeFont();
    }

    private String getCurrentPackageName() {
        try {
            return ActivityThread.currentPackageName();
        } catch (Exception e) {
            logger("getCurrentPackageName failed: " + e.getMessage());
            return null;
        }
    }

    private int getFontWeightAdjustment() {
        try {
            Resources res = sResources;
            if (res == null) return 0;
            Configuration cfg = res.getConfiguration();
            return cfg != null ? cfg.fontWeightAdjustment : 0;
        } catch (Exception e) {
            logger("getFontWeightAdjustment failed: " + e.getMessage());
            return 0;
        }
    }

    private static void logger(String msg) {
        if (SystemProperties.getBoolean("persist.sys.font_debug", false)) {
            Log.d(TAG, msg);
        }
    }

    private static class TypefaceFactory {

        public static Typeface create(String fontToOverride, Typeface base, int fontWeightAdjustment) {
            int weight = resolveWeightByName(fontToOverride);

            if (fontWeightAdjustment != 0) {
                weight = Math.min(1000, Math.max(100, weight + fontWeightAdjustment));
            }

            boolean isItalic = fontToOverride.contains("italic");
            boolean isBold = weight >= 700;

            int style = Typeface.NORMAL;
            if (isBold && isItalic) style = Typeface.BOLD_ITALIC;
            else if (isBold) style = Typeface.BOLD;
            else if (isItalic) style = Typeface.ITALIC;

            if (base == null) base = Typeface.DEFAULT;

            String currentFont = DynamicFontHelper.get().getCurrentFont();
            Typeface named = resolveNamedVariant(currentFont, weight, isItalic);
            if (named != null) {
                logger("TypefaceFactory.create: resolved named variant for '" +
                       currentFont + "' weight=" + weight);
                return named;
            }

            Typeface result = Typeface.create(base, style);
            result = Typeface.create(result, weight, isItalic);

            logger("TypefaceFactory.create: fontToOverride=" + fontToOverride +
                   ", style=" + style +
                   ", weight=" + weight +
                   ", adj=" + fontWeightAdjustment +
                   ", isItalic=" + isItalic +
                   ", success=" + (result != null));

            return result;
        }

        private static Typeface resolveNamedVariant(String fontName, int weight, boolean isItalic) {
            if (fontName == null) return null;

            String[] suffixes;
            if (weight >= 700) {
                suffixes = new String[]{ "-bold", "-extra-bold" };
            } else if (weight >= 600) {
                suffixes = new String[]{ "-semi-bold", "-bold" };
            } else if (weight >= 500) {
                suffixes = new String[]{ "-medium", "-semi-bold" };
            } else {
                suffixes = new String[]{ "", "-light" };
            }

            for (String suffix : suffixes) {
                String candidate = fontName + suffix;
                Typeface tf = Typeface.getSystemDefaultTypeface(candidate);
                if (tf != null && tf != Typeface.DEFAULT) {
                    logger("resolveNamedVariant: matched '" + candidate + "'");
                    return tf;
                }
            }
            return null;
        }

        private static int resolveWeightByName(String familyName) {
            Integer exactMatch = WEIGHT_MAP.get(familyName);
            if (exactMatch != null) {
                return exactMatch;
            }
            for (Map.Entry<String, Integer> entry : WEIGHT_MAP.entrySet()) {
                if (familyName.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
            return 400;
        }
    }
}
