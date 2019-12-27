package com.didi.virtualapk;

import android.app.Application;
import android.content.Context;

import com.didi.virtualapk.demo.DroidPluginManager;

/**
 * Created by renyugang on 16/8/10.
 */
public class VAApplication extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
//        long start = System.currentTimeMillis();
//        PluginManager.getInstance(base).init();
//        Log.d("ryg", "use time:" + (System.currentTimeMillis() - start));
        DroidPluginManager.getInstance(this).init();//在activity里hook居然有问题；可能是获取的application不对。
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }
}
