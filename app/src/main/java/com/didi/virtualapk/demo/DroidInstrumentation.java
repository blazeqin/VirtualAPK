package com.didi.virtualapk.demo;


import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PersistableBundle;
import android.util.Log;

/**
 * instrumentation 的代理类
 * 只是修改部分方法，所以要继承Instrumentation
 * 同时也想实现handler方法，用来修改theme
 */
class DroidInstrumentation extends Instrumentation implements Handler.Callback {
    private DroidPluginManager manager;
    private Instrumentation origin;
    private static final String TAG = "DroidInstrumentation";

    public DroidInstrumentation(DroidPluginManager manager, Instrumentation origin) {
        this.manager = manager;
        this.origin = origin;
    }

    @Override
    public boolean handleMessage(Message msg) {
        Log.i(TAG, "handleMessage: " + msg.obj);
        return false;
    }

    //启动前activity
//    @Override
//    public ActivityResult execStartActivity(Context who, IBinder contextThread, IBinder token, Activity target, Intent intent, int requestCode) {
//        Log.i(TAG, "execStartActivity, args= 6");
//        injectIntent(intent);
//        return origin.execStartActivity(who, contextThread, token, target, intent, requestCode);
//    }

    public ActivityResult execStartActivity(Context who, IBinder contextThread, IBinder token, Activity target, Intent intent, int requestCode, Bundle options) {
        Log.i(TAG, "execStartActivity, args= 7 Activity target");
        injectIntent(intent);
//        origin.execStartActivity(who, contextThread, token, target, intent, requestCode, options);
        try {//使用反射调用execStartActivity方法，因为hide方法调不到
            return Reflecter.with(origin).method("execStartActivity", Context.class, IBinder.class, IBinder.class, Activity.class, Intent.class,
                    int.class, Bundle.class).call(who, contextThread, token, target, intent, requestCode, options);
        } catch (Reflecter.ReflecterException e) {
            e.printStackTrace();
        }
        return null;
    }

//    @Override
//    public ActivityResult execStartActivity(Context who, IBinder contextThread, IBinder token, Fragment target, Intent intent, int requestCode, Bundle options) {
//        Log.i(TAG, "execStartActivity, args= 7 Fragment target");
//        injectIntent(intent);
//        return origin.execStartActivity(who, contextThread, token, target, intent, requestCode, options);
//    }

//    @Override
//    public ActivityResult execStartActivity(Context who, IBinder contextThread, IBinder token, String target, Intent intent, int requestCode, Bundle options) {
//        Log.i(TAG, "execStartActivity, args= 7 String target");
//        injectIntent(intent);
//        return origin.execStartActivity(who, contextThread, token, target, intent, requestCode, options);
//    }

    //添加占坑的activity
    private void injectIntent(Intent intent) {
        //根据component是否为空
        ComponentName component = intent.getComponent();
        //这里是想从插件apk里获取信息，只代理插件的类；不过这里的demo代理了所有的类，宿主类也在其中
        if (component == null || component.getPackageName().equals(manager.getContext().getPackageName())) {
            manager.getPositionHandler().transformIntent(intent);
        }
        if (intent.getComponent() != null) {
            Log.i(TAG, String.format("execStartActivity[%s : %s]", intent.getComponent().getPackageName(), intent.getComponent().getClassName()));
            //然后插桩
            manager.getPositionHandler().markIntent(intent);
        }
    }
    //创建activity

    //从AMS调回来时，ActivityThread会调到这里； 换回要启动的类
    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        Log.i(TAG, "new activity, args= 3");
        try {
            cl.loadClass(className);
        } catch (ClassNotFoundException e) {
            if (intent == null) {
                return origin.newActivity(cl, className, intent);
            }
            boolean isPlugin = intent.getBooleanExtra(Constants.KEY_IS_PLUGIN, false);
            if (isPlugin) {
                ComponentName component = new ComponentName(intent.getStringExtra(Constants.KEY_TARGET_PACKAGE),
                        intent.getStringExtra(Constants.KEY_TARGET_ACTIVITY));
                Log.i(TAG, String.format("newActivity[%s : %s/%s]", className, component.getPackageName(), component.getClassName()));

                //create activity , set intent,  set resources
                //cl应该换掉，classname换了，intent还是原来的
                Activity activity = origin.newActivity(cl, component.getClassName(), intent);
                activity.setIntent(intent);//为什么要保留原来的intent。。。在oncreate中会用到
                return activity;
            }
        }
        return origin.newActivity(cl, className, intent);
    }

//    @Override
//    public Activity newActivity(Class<?> clazz, Context context, IBinder token, Application application, Intent intent, ActivityInfo info, CharSequence title, Activity parent, String id, Object lastNonConfigurationInstance) throws InstantiationException, IllegalAccessException {
//        Log.i(TAG, "new activity, args= 10");
//        return origin.newActivity(clazz, context, token, application, intent, info, title, parent, id, lastNonConfigurationInstance);
//    }

    //启动后activity
    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        injectActivity(activity);
        origin.callActivityOnCreate(activity, icicle);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle, PersistableBundle persistentState) {
        injectActivity(activity);
        origin.callActivityOnCreate(activity, icicle, persistentState);
    }

    private void injectActivity(Activity activity) {
        Intent intent = activity.getIntent();
        if (intent.getBooleanExtra(Constants.KEY_IS_PLUGIN, false)) {
            try {
                //set resources, context, application, screen orientation
                Reflecter.with(activity).field("mBase").set(manager.getContext());
                Reflecter.with(activity).field("mApplication").set(manager.getApplication());
                //wrap intent
                ComponentName component = new ComponentName(intent.getStringExtra(Constants.KEY_TARGET_PACKAGE), intent.getStringExtra(Constants.KEY_TARGET_ACTIVITY));
                Intent wrapIntent = new Intent(intent);
                wrapIntent.setClassName(component.getPackageName(), component.getClassName());
                activity.setIntent(wrapIntent);
                Log.i(TAG, "inject activity success: "+component.getClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
