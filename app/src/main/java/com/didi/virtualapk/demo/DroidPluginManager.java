package com.didi.virtualapk.demo;

import android.app.ActivityThread;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

/**
 * 插件化管理
 */
public class DroidPluginManager {
    private static volatile DroidPluginManager sInstance = null;
    private final Application mApplication;
    private final Context mContext;
    private PositionHandler mPositionHandler;//处理占坑逻辑
    private Instrumentation mInstrumentation;

    public static DroidPluginManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (DroidPluginManager.class) {
                if (sInstance == null) {
                    sInstance = new DroidPluginManager(context);
                }
            }
        }
        return sInstance;
    }

    private DroidPluginManager(Context context) {
        if (context instanceof Application) {
            this.mApplication = (Application) context;
            this.mContext = mApplication.getBaseContext();
        }else{
            //这种方式有问题？？？
            this.mApplication = ActivityThread.currentApplication();
            this.mContext = context;
        }
        mPositionHandler = new PositionHandler(this);
        //hook instrumentation ，也可hook IActivityManager；这里采用前者
        hookInstrumentationAndHandler();
    }

    //handler是用来设置Theme的,这里不作处理
    private void hookInstrumentationAndHandler() {
        try {
            //这里通过aidl获取，后来试试反射
            ActivityThread activityThread = ActivityThread.currentActivityThread();
            Instrumentation origin = activityThread.getInstrumentation();


            //设置新的Instrumentation,然后设置handler
            final DroidInstrumentation instrumentation = new DroidInstrumentation(this, origin);
            Reflecter.with(activityThread).field("mInstrumentation").set(instrumentation);
            Handler mainHandler = Reflecter.with(activityThread).method("getHandler").call();
            Reflecter.with(mainHandler).field("mCallback").set(instrumentation);
            this.mInstrumentation = instrumentation;
            Log.i("DroidPluginManager", "hook instrumentation and handler success");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void init() {
    }

    public PositionHandler getPositionHandler() {
        return mPositionHandler;
    }

    public Context getContext() {
        return mContext;
    }

    public Application getApplication() {
        return mApplication;
    }
}
