package com.didi.virtualapk.demo;

import android.support.annotation.NonNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 使用反射的一个工具类
 */
public class Reflecter {
    protected Class<?> mType;
    protected Object mCaller;
    protected Constructor mConstructor;
    protected Field mField;
    protected Method mMethod;

    public static class ReflecterException extends Exception {
        public ReflecterException(String message) {
            super(message);
        }

        public ReflecterException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static Reflecter with(@NonNull Object caller) throws ReflecterException {
        return on(caller.getClass()).bind(caller);
    }

    public static Reflecter on(Class<?> type) throws ReflecterException {
        Reflecter reflecter = new Reflecter();
        reflecter.mType = type;
        return reflecter;
    }

    public Reflecter bind(Object caller) throws ReflecterException {
        try {
            mCaller = check(caller);
        } catch (Exception e) {
            throw e;
        }
        return this;
    }

    public Reflecter unbind() throws ReflecterException {
        mCaller = null;
        return this;
    }

    protected Object check(Object caller) throws ReflecterException {
        if (caller == null || mType.isInstance(caller)) {
            return caller;
        }
        throw new ReflecterException("caller is not a type of " + mType);
    }

    public Reflecter field(String name) throws ReflecterException {
        try {
            mField = findField(name);
            //绕过检查
            mField.setAccessible(true);
            mConstructor = null;//???
            mMethod = null;//???
        } catch (Throwable throwable) {
            throw new ReflecterException("Ppp", throwable);
        }
        return this;
    }

    public Reflecter method(String name, Class<?>... parametersType) throws ReflecterException {
        try {
            mMethod = findMethod(name, parametersType);
            mMethod.setAccessible(true);
            mConstructor = null;
            mField = null;
        } catch (Throwable throwable) {
            throw new ReflecterException("Ppp", throwable);
        }
        return this;
    }

    private Method findMethod(String name, Class<?>... parametersType) throws NoSuchMethodException {
        try {
            return mType.getDeclaredMethod(name, parametersType);
        } catch (NoSuchMethodException e) {
            throw e;
        }
    }

    //getDeclaredFiled 仅能获取类本身的属性成员（包括私有、共有、保护）
    //getField 仅能获取类(及其父类可以自己测试) public属性成员
    private Field findField(String name) throws NoSuchFieldException {
        try {//从class文件中获取对应的属性
            return mType.getField(name);
        } catch (NoSuchFieldException error) {
            //健壮性，从父类的class文件中去寻找
            for (Class<?> cls = mType; cls!= null ; cls = cls.getSuperclass()) {
                try {
                    return cls.getDeclaredField(name);
                } catch (Exception e) {

                }
            }
            throw error;
        }
    }

    //field use
    public Reflecter set(Object value)  throws ReflecterException{
        return set(mCaller, value);
    }
    public Reflecter set(Object caller, Object value) throws ReflecterException {
//        check
        try {
            mField.set(caller,value);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return this;
    }

    //method use
    public <R> R call(Object... args) throws ReflecterException {
        return callByCaller(mCaller, args);
    }

    private <R> R callByCaller(Object caller, Object... args) throws ReflecterException {
//        check(caller, mMethod, "Method");
        try {
            return (R) mMethod.invoke(caller, args);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }
}
