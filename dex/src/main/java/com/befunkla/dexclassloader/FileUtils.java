package com.befunkla.dexclassloader;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import dalvik.system.DexClassLoader;

public class FileUtils {

    public static void copyFiles(Context context, String fileName, File desFile) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = context.getApplicationContext().getAssets().open(fileName);
            out = new FileOutputStream(desFile.getAbsolutePath());
            byte[] bytes = new byte[1024];
            int i;
            while ((i = in.read(bytes)) != -1)
                out.write(bytes, 0 , i);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                if (out != null)
                    out.close();
                if (in != null)
                    in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean hasExternalStorage() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }

    public static File getCacheDir(Context context) {
        File cache;
        if (hasExternalStorage()) {
            cache = context.getExternalCacheDir();
        } else {
            cache = context.getCacheDir();
        }
        if (!cache.exists())
            cache.mkdirs();
        return cache;
    }

    public static DexClassLoader getDexClassLoader(Context context ){
        File cacheFile = FileUtils.getCacheDir(context.getApplicationContext());
        String internalPath = cacheFile.getAbsolutePath() + File.separator + "classes.dex";
        File desFile = new File(internalPath);
        try {
            if (!desFile.exists()) {
                desFile.createNewFile();
                FileUtils.copyFiles( context , "classes.dex", desFile);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        DexClassLoader dexClassLoader = new DexClassLoader(internalPath, cacheFile.getAbsolutePath(), null,context.getClassLoader() );

        return dexClassLoader ;
    }
}
