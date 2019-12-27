package com.didi.virtualapk.demo;

import android.content.ComponentName;
import android.content.Intent;
import android.util.Log;

/**
 * 占位处理
 */
public class PositionHandler {

    private static final String TAG = "PositionHandler";
    private DroidPluginManager manager;
    //占坑activity的信息类
    private PositionActivityInfo mPositionInfo;

    public PositionHandler(DroidPluginManager manager) {
        this.manager = manager;
        mPositionInfo = new PositionActivityInfo(manager.getContext().getPackageName());
    }

    /**
     * 这里进行了多此一举的做法：原本的做法是从插件apk里获取相关信息，然后设置；
     */
    public Intent transformIntent(Intent intent) {
        ComponentName component = new ComponentName(intent.getComponent().getPackageName(), intent.getComponent().getClassName());
        intent.setComponent(component);
        return intent;
    }

    //添加占坑activity
    public void markIntent(Intent intent) {
        if (intent.getComponent() == null) {
            return;
        }
        String targetPackageName = intent.getComponent().getPackageName();
        String targetClassName = intent.getComponent().getClassName();
        intent.putExtra(Constants.KEY_IS_PLUGIN, true);
        intent.putExtra(Constants.KEY_TARGET_ACTIVITY, targetClassName);
        intent.putExtra(Constants.KEY_TARGET_PACKAGE, targetPackageName);
        dispatchStubActivity(intent);
    }

    private void dispatchStubActivity(Intent intent) {
        String targetClassName = intent.getComponent().getClassName();
        String positionActivity = mPositionInfo.getPositionActivity(targetClassName);
        Log.i(TAG, String.format("dispatchStubActivity,[%s -> %s]", targetClassName, positionActivity));
        intent.setClassName(manager.getContext(), positionActivity);
    }
}
