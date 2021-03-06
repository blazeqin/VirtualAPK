/*
 * Copyright (C) 2017 Beijing Didi Infinity Technology and Development Co.,Ltd. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.didi.virtualapk.internal;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.app.Fragment;
import android.app.Instrumentation;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PersistableBundle;
import android.util.Log;

import com.didi.virtualapk.PluginManager;
import com.didi.virtualapk.delegate.StubActivity;
import com.didi.virtualapk.internal.utils.PluginUtil;
import com.didi.virtualapk.utils.Reflector;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;


/**
 * Created by renyugang on 16/8/10.
 */
public class VAInstrumentation extends Instrumentation implements Handler.Callback {
    public static final String TAG = Constants.TAG_PREFIX + "VAInstrumentation";
    public static final int LAUNCH_ACTIVITY         = 100;

    protected Instrumentation mBase;
    
    protected final ArrayList<WeakReference<Activity>> mActivities = new ArrayList<>();

    protected PluginManager mPluginManager;

    public VAInstrumentation(PluginManager pluginManager, Instrumentation base) {
        this.mPluginManager = pluginManager;
        this.mBase = base;
    }

    @Override
    public ActivityResult execStartActivity(Context who, IBinder contextThread, IBinder token, Activity target, Intent intent, int requestCode) {
        injectIntent(intent);
        return mBase.execStartActivity(who, contextThread, token, target, intent, requestCode);
    }

    @Override
    public ActivityResult execStartActivity(Context who, IBinder contextThread, IBinder token, Activity target, Intent intent, int requestCode, Bundle options) {
        injectIntent(intent);
        return mBase.execStartActivity(who, contextThread, token, target, intent, requestCode, options);
    }

    @Override
    public ActivityResult execStartActivity(Context who, IBinder contextThread, IBinder token, Fragment target, Intent intent, int requestCode, Bundle options) {
        injectIntent(intent);
        return mBase.execStartActivity(who, contextThread, token, target, intent, requestCode, options);
    }

    @Override
    public ActivityResult execStartActivity(Context who, IBinder contextThread, IBinder token, String target, Intent intent, int requestCode, Bundle options) {
        injectIntent(intent);
        return mBase.execStartActivity(who, contextThread, token, target, intent, requestCode, options);
    }

    //修改intent为占坑的activity
  protected void injectIntent(Intent intent) {
        //当component为null时，根据启动Activity时，配置的action，data,category等去已加载的plugin中匹配到确定的Activity
        mPluginManager.getComponentsHandler().transformIntentToExplicitAsNeeded(intent);
        // null component is an implicitly intent
        if (intent.getComponent() != null) {
            Log.i(TAG, String.format("execStartActivity[%s : %s]", intent.getComponent().getPackageName(), intent.getComponent().getClassName()));
            // resolve intent with Stub Activity if needed
            //component不为空时
            this.mPluginManager.getComponentsHandler().markIntentIfNeeded(intent);
        }
    }

    //从AMS调回来时，ActivityThread会调到这里； 换回要启动的类
    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        try {
            cl.loadClass(className);
            Log.i(TAG, String.format("newActivity[%s]", className));
            
        } catch (ClassNotFoundException e) {
            //替换成要启动的类的component
            ComponentName component = PluginUtil.getComponent(intent);
            
            if (component == null) {//用原来的Instrumentation去处理
                return newActivity(mBase.newActivity(cl, className, intent));
            }
    
            String targetClassName = component.getClassName();
            Log.i(TAG, String.format("newActivity[%s : %s/%s]", className, component.getPackageName(), targetClassName));

            //根据packageName，获取对应插件包的信息
            LoadedPlugin plugin = this.mPluginManager.getLoadedPlugin(component);
    
            if (plugin == null) {
                // Not found then goto stub activity.
                boolean debuggable = false;
                try {
                    Context context = this.mPluginManager.getHostContext();
                    debuggable = (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
                } catch (Throwable ex) {
        
                }
    
                if (debuggable) {
                    throw new ActivityNotFoundException("error intent: " + intent.toURI());
                }
                
                Log.i(TAG, "Not found. starting the stub activity: " + StubActivity.class);
                //跳转到对应包名的启动activity里
                return newActivity(mBase.newActivity(cl, StubActivity.class.getName(), intent));
            }
            //正常创建activity，设置intent
            Activity activity = mBase.newActivity(plugin.getClassLoader(), targetClassName, intent);
            activity.setIntent(intent);
    
            // for 4.1+  设置resourcemanager为插件里的资源对象
            Reflector.QuietReflector.with(activity).field("mResources").set(plugin.getResources());
    
            return newActivity(activity);
        }

        return newActivity(mBase.newActivity(cl, className, intent));
    }
    
    @Override
    public Application newApplication(ClassLoader cl, String className, Context context) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return mBase.newApplication(cl, className, context);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        injectActivity(activity);
        mBase.callActivityOnCreate(activity, icicle);
    }
    
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle, PersistableBundle persistentState) {
        injectActivity(activity);
        mBase.callActivityOnCreate(activity, icicle, persistentState);
    }
    
    protected void injectActivity(Activity activity) {
        final Intent intent = activity.getIntent();
        if (PluginUtil.isIntentFromPlugin(intent)) {
            Context base = activity.getBaseContext();
            try {//设置Resources， context， application
                LoadedPlugin plugin = this.mPluginManager.getLoadedPlugin(intent);
                Reflector.with(base).field("mResources").set(plugin.getResources());
                Reflector reflector = Reflector.with(activity);
                reflector.field("mBase").set(plugin.createPluginContext(activity.getBaseContext()));
                reflector.field("mApplication").set(plugin.getApplication());

                // set screenOrientation
                ActivityInfo activityInfo = plugin.getActivityInfo(PluginUtil.getComponent(intent));
                if (activityInfo.screenOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                    activity.setRequestedOrientation(activityInfo.screenOrientation);
                }
    
                // for native activity 又包裹了intent。。。为啥？intent实现了Parcelable接口，可以序列化，传数据的一种形式。
                ComponentName component = PluginUtil.getComponent(intent);
                Intent wrapperIntent = new Intent(intent);
                wrapperIntent.setClassName(component.getPackageName(), component.getClassName());
                activity.setIntent(wrapperIntent);
                
            } catch (Exception e) {
                Log.w(TAG, e);
            }
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        if (msg.what == LAUNCH_ACTIVITY) {//设置主题
            // ActivityClientRecord r
            Object r = msg.obj;
            try {
                Reflector reflector = Reflector.with(r);
                Intent intent = reflector.field("intent").get();
                intent.setExtrasClassLoader(mPluginManager.getHostContext().getClassLoader());
                ActivityInfo activityInfo = reflector.field("activityInfo").get();

                if (PluginUtil.isIntentFromPlugin(intent)) {
                    int theme = PluginUtil.getTheme(mPluginManager.getHostContext(), intent);
                    if (theme != 0) {
                        Log.i(TAG, "resolve theme, current theme:" + activityInfo.theme + "  after :0x" + Integer.toHexString(theme));
                        activityInfo.theme = theme;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, e);
            }
        }

        return false;//只调用一次
    }

    @Override
    public Context getContext() {
        return mBase.getContext();
    }

    @Override
    public Context getTargetContext() {
        return mBase.getTargetContext();
    }

    @Override
    public ComponentName getComponentName() {
        return mBase.getComponentName();
    }

    protected Activity newActivity(Activity activity) {
        synchronized (mActivities) {
            for (int i = mActivities.size() - 1; i >= 0; i--) {
                if (mActivities.get(i).get() == null) {
                    mActivities.remove(i);
                }
            }
            mActivities.add(new WeakReference<>(activity));
        }
        return activity;
    }

    List<WeakReference<Activity>> getActivities() {
        synchronized (mActivities) {
            return new ArrayList<>(mActivities);
        }
    }
}
