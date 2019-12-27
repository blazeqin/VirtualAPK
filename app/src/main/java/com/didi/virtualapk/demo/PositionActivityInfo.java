package com.didi.virtualapk.demo;

import java.util.HashMap;

/**
 * 获取插桩activity的信息
 */
public class PositionActivityInfo {
    public static final String STUB_ACTIVITY_STANDARD = "%s.A";

    private String packageName;

    public PositionActivityInfo(String packageName) {
        this.packageName = packageName;
    }

    private HashMap<String, String> mCachePositions = new HashMap<>();

    public String getPositionActivity(String className) {
        String positionActivity = mCachePositions.get(className);
        if (positionActivity != null) {
            return positionActivity;
        }
        positionActivity = String.format(STUB_ACTIVITY_STANDARD, packageName);
        mCachePositions.put(className, positionActivity);
        return positionActivity;
    }
}
