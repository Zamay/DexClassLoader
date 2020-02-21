package com.befunkla.dexclassloader;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.Method;

import dalvik.system.DexClassLoader;

public class MainActivity2 extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        loadDexClass();
    }

    private void loadDexClass() {
        Log.d("debug", "2");
        DexClassLoader dexClassLoader = FileUtils.getDexClassLoader( this ) ;
        Log.d("debug", "dexClassLoader: " + dexClassLoader);
        try {
            Class libClazz = dexClassLoader.loadClass("com.musocal.testfblink.MainActivity");
            Log.d("debug", "libClazz: " + libClazz);
            Method method = libClazz.getDeclaredMethod("a", AppCompatActivity.class);
//            Object obj = (Object)libClazz.newInstance();
            Log.d("debug", "method: " + method);
            method.invoke(null, this);

//            Method method = libClazz.getMethod("b", int.class, int.class);
//            Log.d("debug", "libClazz: " + libClazz);
//            Object obj = (Object)libClazz.newInstance();
//            Log.d("debug", "method: " + method);
//            Log.d("debug", "invoke: " + method.invoke(null, 2, 4));
//            method.invoke(null, 2, 4);
//            Dynamic dynamic = (Dynamic) libClazz.newInstance();
//            dynamic.a(this);
        } catch (Exception e) {
            Log.d("debug", "dexClassLoaderError: ", e);
        }
    }
}
