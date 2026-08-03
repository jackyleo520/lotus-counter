package com.lotus.nianfo;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.InputType;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;
import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import android.os.Environment;
import android.widget.Toast;
import org.json.JSONObject;
import java.util.Date;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import androidx.core.content.FileProvider;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 念佛计数器 —— 原生 WebView 外壳
 *
 * 关键点：用 WebViewAssetLoader 把 assets/ 目录通过 https 虚拟域
 * (https://appassets.androidplatform.com/assets/...) 提供给 WebView。
 * 这样页面拥有"真实 https 源"，localStorage / DOM storage 才能稳定持久化，
 * 多账号数据隔离（lotus_ud_<账号>）才不会丢失。
 *
 * 相对路径（如 <script src="sutras_data.js">、url('muyu.png')）会自动解析到
 * 该虚拟域下的 assets/，无需改动任何前端代码。
 */
public class MainActivity extends AppCompatActivity {

    private static final String VIRTUAL_HOST = "appassets.androidplatform.com";
    private static final String BASE_URL = "https://" + VIRTUAL_HOST + "/assets/index.html";
    private static final int REQ_FILE_CHOOSER = 1001;
    private static final int REQ_STORAGE_PERMISSION = 2001;
    private static final int REQ_NOTIFICATION_PERMISSION = 2002;
    private static final String CHANNEL_ID = "nianfo_calendar";
    private static final String CHANNEL_NAME = "日历提醒";

    private WebView webView;
    private WebViewAssetLoader assetLoader;

    // 文件选择回调（导入音频/图片/头像/经文）
    private ValueCallback<Uri> uploadMessage;
    private ValueCallback<Uri[]> uploadMessageArray;
    // 导入音频时：标记当前选择的是音频（需原生复制为私有文件），以及目标计数器索引
    private boolean pendingAudioPick = false;
    private int pendingAudioIndex = 0;
    // 音频选择用途：counter=计数器提示音(按索引存)，alarm=闹钟自定义铃声(固定文件)
    private String pendingAudioPurpose = "counter";
    private static final String FILE_PROVIDER_AUTH = "com.lotus.nianfo.fileprovider";

    // 原生高音量循环告警
    private MediaPlayer alarmPlayer;
    private int originalAlarmVolume = -1;

    // 原生 TTS 语音朗读
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean ttsChineseOk = false;

    // 原生音频播放器（用于播放用户导入的音频）
    private MediaPlayer userAudioPlayer;
    private String currentAudioDataUrl = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        assetLoader = new WebViewAssetLoader.Builder()
                .setDomain(VIRTUAL_HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        // 多账号进度/计数依赖 localStorage，必须开启
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setDefaultTextEncodingName("UTF-8");

        // JS 桥：让前端能用原生能力（重新加载 / 高音量循环告警）
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        webView.setWebViewClient(new WebViewClientCompat() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                // 只拦截虚拟域请求，其余（CDN 等）放行到网络
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // 站内跳转继续在 WebView 内
                return false;
            }
        });

        // 登录页及全站大量使用 alert / confirm / prompt（如删除确认、重命名等），
        // 必须重写 WebChromeClient 的对应方法，否则在 WebView 里会被默认抑制、点击无反应。
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm())
                        .setCancelable(false)
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm())
                        .setNegativeButton(android.R.string.cancel, (dialog, which) -> result.cancel())
                        .setCancelable(false)
                        .show();
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                final EditText input = new EditText(MainActivity.this);
                input.setInputType(InputType.TYPE_CLASS_TEXT);
                if (defaultValue != null) input.setText(defaultValue);
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setView(input)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm(input.getText().toString()))
                        .setNegativeButton(android.R.string.cancel, (dialog, which) -> result.cancel())
                        .setCancelable(false)
                        .show();
                return true;
            }

            // 文件选择：让 WebView 内的 <input type="file"> 能弹出系统选择器
            // （导入音频 / 图片 / 头像 / 经文都依赖它，否则点击"导入"毫无反应）
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                uploadMessageArray = filePathCallback;
                // 标记是否为音频导入：前端 accept 为 audio/* 时，原生把文件复制为私有文件并返回路径
                pendingAudioPick = false;
                try {
                    String[] acceptTypes = fileChooserParams.getAcceptTypes();
                    if (acceptTypes != null) {
                        for (String t : acceptTypes) {
                            if (t != null && t.startsWith("audio")) { pendingAudioPick = true; break; }
                        }
                    }
                } catch (Exception ignored) {}
                try {
                    Intent intent = fileChooserParams.createIntent();
                    startActivityForResult(intent, REQ_FILE_CHOOSER);
                } catch (Exception e) {
                    uploadMessageArray = null;
                    return false;
                }
                return true;
            }

            // 兼容 Android < 5.0 的旧接口
            public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
                uploadMessage = uploadMsg;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(acceptType != null && !acceptType.isEmpty() ? acceptType : "*/*");
                try {
                    startActivityForResult(Intent.createChooser(intent, "选择文件"), REQ_FILE_CHOOSER);
                } catch (Exception e) {
                    uploadMessage = null;
                }
            }
        });

        // 触摸屏幕任意位置 → 停止告警提示音（返回 false 让触摸仍传递给 WebView，正常交互不受影响）
        webView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN && alarmPlayer != null) {
                stopNativeAlarm();
            }
            return false;
        });

        // 申请存储权限：导入音频/图片/头像需要读取手机文件，弹出系统授权弹窗
        requestStoragePermission();

        // 申请通知权限：日历提醒需要发送到通知栏（Android 13+）
        requestNotificationPermission();

        // 初始化系统 TTS，供读经语音朗读使用
        initTTS();

        // 创建日历提醒通知渠道（Android 8.0+ 需要）
        createNotificationChannel();

        webView.loadUrl(BASE_URL);
    }

    // 运行时申请存储权限（Android 13+ 用 READ_MEDIA_*，旧版本用 READ_EXTERNAL_STORAGE）
    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            String[] perms = {
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_VIDEO
            };
            ArrayList<String> need = new ArrayList<>();
            for (String p : perms) {
                if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                    need.add(p);
                }
            }
            if (!need.isEmpty()) {
                ActivityCompat.requestPermissions(this, need.toArray(new String[0]), REQ_STORAGE_PERMISSION);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE_PERMISSION);
            }
        }
    }

    // 运行时申请通知权限（Android 13+ 需要）
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION_PERMISSION);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // 无论授权与否都不阻断使用：系统文件选择器（SAF）本身不依赖该权限即可选文件
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE_CHOOSER) {
            // 音频导入：原生把文件复制为私有文件，再把路径回调给前端（避免 dataURL 撑爆 localStorage）
            if (pendingAudioPick && resultCode == RESULT_OK && data != null && data.getData() != null) {
                pendingAudioPick = false;
                Uri uri = data.getData();
                String purpose = pendingAudioPurpose;
                pendingAudioPurpose = "counter";
                if ("alarm".equals(purpose)) {
                    // 闹钟自定义铃声：复制为固定文件，便于播放时按文件名查找
                    String path = copyUriToAudioFile(uri, -1, "alarm_custom");
                    if (path != null && webView != null) {
                        final String js = "if(typeof onAlarmAudioImported==='function'){onAlarmAudioImported('" + path.replace("'", "\\'") + "');}";
                        webView.post(() -> webView.evaluateJavascript(js, null));
                    }
                    return;
                }
                String path = copyUriToAudioFile(uri, pendingAudioIndex, null);
                int idx = pendingAudioIndex;
                pendingAudioIndex = 0;
                if (path != null && webView != null) {
                    final String js = "if(typeof onAudioImported==='function'){onAudioImported('" + path.replace("'", "\\'") + "'," + idx + ");}";
                    webView.post(() -> webView.evaluateJavascript(js, null));
                }
                return;
            }
            pendingAudioPick = false;
            if (uploadMessageArray != null) {
                uploadMessageArray.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                uploadMessageArray = null;
            } else if (uploadMessage != null) {
                Uri result = (data != null && resultCode == RESULT_OK) ? data.getData() : null;
                uploadMessage.onReceiveValue(result);
                uploadMessage = null;
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    // 把用户选择的音频 uri 复制为应用私有文件，返回绝对路径（前端只存这个短路径）
    // name 非空时按固定文件名存储（如闹钟自定义铃声），否则按计数器索引存储
    private String copyUriToAudioFile(Uri uri, int index, String name) {
        try {
            ContentResolver cr = getContentResolver();
            String ext = "mp3";
            try {
                String mime = cr.getType(uri);
                if (mime != null && mime.contains("/")) {
                    String sub = mime.split("/")[1];
                    if (sub != null && !sub.isEmpty()) ext = sub;
                }
            } catch (Exception ignored) {}
            File dir = new File(getFilesDir(), "audio");
            if (!dir.exists()) dir.mkdirs();
            // 每个计数器单独一个文件，便于覆盖替换；name 指定则用固定文件名
            String fileName = (name != null && !name.isEmpty()) ? (name + "." + ext) : ("imported_" + index + "." + ext);
            File out = new File(dir, fileName);
            InputStream in = cr.openInputStream(uri);
            if (in == null) return null;
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) fos.write(buf, 0, len);
            in.close();
            fos.close();
            return out.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    // 查找用户导入的自定义闹钟铃声文件（alarm_custom.*）
    private String getCustomAlarmPath() {
        try {
            File dir = new File(getFilesDir(), "audio");
            if (!dir.exists()) return null;
            File[] files = dir.listFiles((d, name) -> name.startsWith("alarm_custom."));
            if (files != null && files.length > 0) return files[0].getAbsolutePath();
        } catch (Exception ignored) {}
        return null;
    }

    // 按任意物理按键 → 停止告警提示音
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && alarmPlayer != null) {
            stopNativeAlarm();
        }
        return super.dispatchKeyEvent(event);
    }

    // Activity 级别拦截所有触摸事件：即使 WebView 内部消费了 touch，
    // 这里也能保证屏幕任意位置点击都能停止原生循环告警
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && alarmPlayer != null) {
            stopNativeAlarm();
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else if (webView != null) {
            // 交给前端逐级返回：阅读器/弹窗→关闭，非首页→回首页；返回 true 表示已处理（留住 App）
            webView.evaluateJavascript("window.__appBack ? window.__appBack() : false", value -> {
                boolean handled = "true".equals(value);
                if (!handled) {
                    runOnUiThread(() -> super.onBackPressed());
                }
            });
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        stopNativeAlarm();
        stopUserAudio();
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Exception ignored) {}
            tts = null;
        }
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    // ===== 原生循环告警 =====
    // sound: "alarm" 用内置 alarm.mp3，"yinqing" 用内置 yinqing.mp3（引磬），"custom" 用用户导入的自定义铃声
    // useMediaStream: true 走媒体流(STREAM_MUSIC，跟随系统媒体音量，可用音量键实时调节)；
    //                  false 走闹钟流(STREAM_ALARM，勿扰/静音模式也能可靠响起)
    private void startNativeAlarm(String sound, boolean useMediaStream) {
        stopNativeAlarm();
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        int streamType = useMediaStream ? AudioManager.STREAM_MUSIC : AudioManager.STREAM_ALARM;
        try {
            // 自定义铃声：播放用户导入的 alarm_custom.* 文件（无则回退默认）
            if ("custom".equals(sound)) {
                String customPath = getCustomAlarmPath();
                if (customPath != null) {
                    try {
                        MediaPlayer cmp = new MediaPlayer();
                        cmp.setAudioStreamType(streamType);
                        cmp.setDataSource(customPath);
                        cmp.setLooping(true);
                        cmp.setOnErrorListener((mp1, what, extra) -> { stopNativeAlarm(); return true; });
                        cmp.prepare();
                        cmp.start();
                        alarmPlayer = cmp;
                        return;
                    } catch (Exception ignored) {
                        // 回退到默认闹钟声
                    }
                }
                sound = "alarm";
            }
            // 注意：不再强制拉满系统音量，保持用户当前音量设置，避免提示音过响
            MediaPlayer mp = new MediaPlayer();
            boolean useYinqing = "yinqing".equals(sound);
            try {
                String assetName = useYinqing ? "yinqing.mp3" : "alarm.mp3";
                android.content.res.AssetFileDescriptor afd = getAssets().openFd(assetName);
                mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
            } catch (IOException e) {
                // 内置音频缺失时回退到系统默认闹钟声
                try { mp.release(); } catch (Exception ignored) {}
                mp = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI);
                if (mp == null) return;
            }
            mp.setAudioStreamType(streamType);
            mp.setLooping(true); // 持续循环，直到用户触摸屏幕或按任意物理键
            mp.setOnErrorListener((mp1, what, extra) -> { stopNativeAlarm(); return true; });
            mp.prepare();
            mp.start();
            alarmPlayer = mp;
        } catch (Exception e) {
            // 最终回退：系统默认闹钟声
            try {
                MediaPlayer fb = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI);
                if (fb != null) {
                    if (am != null) fb.setAudioStreamType(streamType);
                    fb.setLooping(true);
                    fb.start();
                    alarmPlayer = fb;
                }
            } catch (Exception ignored) {}
        }
    }

    private void stopNativeAlarm() {
        if (alarmPlayer != null) {
            try { if (alarmPlayer.isPlaying()) alarmPlayer.stop(); } catch (Exception ignored) {}
            try { alarmPlayer.release(); } catch (Exception ignored) {}
            alarmPlayer = null;
        }
        // 恢复之前的闹钟音量
        if (originalAlarmVolume >= 0) {
            try {
                AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                if (am != null) am.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0);
            } catch (Exception ignored) {}
            originalAlarmVolume = -1;
        }
    }

    // 初始化系统 TTS；并检测中文语音是否可用，回调前端
    private void initTTS() {
        try {
            tts = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true;
                    ttsChineseOk = setChineseIfAvailable();
                    try {
                        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                            @Override
                            public void onStart(String utteranceId) {}
                            @Override
                            public void onDone(String utteranceId) {
                                runOnUiThread(() -> {
                                    if (webView != null) {
                                        webView.evaluateJavascript("if(typeof __onTtsDone==='function') __onTtsDone('" + utteranceId + "');", null);
                                    }
                                });
                            }
                            @Override
                            public void onError(String utteranceId) {
                                runOnUiThread(() -> {
                                    if (webView != null) {
                                        webView.evaluateJavascript("if(typeof __onTtsError==='function') __onTtsError('" + utteranceId + "');", null);
                                    }
                                });
                            }
                        });
                    } catch (Exception ignored) {}
                    // 通知前端 TTS 是否可用（重点：是否支持中文语音）
                    final boolean ok = ttsChineseOk;
                    runOnUiThread(() -> {
                        if (webView != null) {
                            webView.evaluateJavascript("if(typeof onTtsReady==='function') onTtsReady(" + ok + ");", null);
                        }
                    });
                } else {
                    ttsReady = false;
                }
            });
        } catch (Exception e) {
            ttsReady = false;
        }
    }

    // 尝试设置中文语言；若系统无中文语音包，遍历可用 voice 找中文 voice，仍失败回退到默认语言
    private boolean setChineseIfAvailable() {
        if (tts == null) return false;
        try {
            int res = tts.setLanguage(Locale.CHINESE);
            if (res == TextToSpeech.LANG_AVAILABLE || res == TextToSpeech.LANG_COUNTRY_AVAILABLE || res == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE) {
                // 明确选一个中文 voice，避免某些引擎选到非中文默认音色导致朗读异常
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        for (Voice v : tts.getVoices()) {
                            Locale l = v.getLocale();
                            if (l != null && "zh".equalsIgnoreCase(l.getLanguage())) {
                                tts.setVoice(v);
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {}
                return true;
            }
        } catch (Exception ignored) {}
        // 回退：遍历 voices 找中文 voice
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                for (Voice v : tts.getVoices()) {
                    Locale l = v.getLocale();
                    if (l != null && ("zh".equalsIgnoreCase(l.getLanguage()) || l.getDisplayLanguage(Locale.ENGLISH).toLowerCase().contains("chinese"))) {
                        tts.setVoice(v);
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        // 再尝试默认语言（至少能出声）
        try {
            int res2 = tts.setLanguage(Locale.getDefault());
            return res2 == TextToSpeech.LANG_AVAILABLE || res2 == TextToSpeech.LANG_COUNTRY_AVAILABLE;
        } catch (Exception ignored) {}
        return false;
    }

    // 播放用户导入的音频（data URL 或真实文件路径）
    private void playUserAudio(String audioDataUrl, boolean loop) {
        stopUserAudio();
        if (audioDataUrl == null || audioDataUrl.isEmpty()) return;
        try {
            MediaPlayer mp = new MediaPlayer();
            if (audioDataUrl.startsWith("data:")) {
                int comma = audioDataUrl.indexOf(',');
                if (comma < 0) return;
                String base64 = audioDataUrl.substring(comma + 1);
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                File tmp = new File(getCacheDir(), "imported_audio_" + UUID.randomUUID().toString() + ".mp3");
                FileOutputStream fos = new FileOutputStream(tmp);
                fos.write(bytes);
                fos.close();
                mp.setDataSource(tmp.getAbsolutePath());
            } else {
                // 绝对文件路径（导入音频复制后的私有文件）或 http/asset 等
                mp.setDataSource(audioDataUrl);
            }
            mp.setLooping(loop);
            mp.setOnCompletionListener(mp1 -> { if (!loop) stopUserAudio(); });
            mp.setOnErrorListener((mp1, what, extra) -> { stopUserAudio(); return true; });
            mp.prepare();
            mp.start();
            userAudioPlayer = mp;
            currentAudioDataUrl = audioDataUrl;
        } catch (Exception e) {
            stopUserAudio();
        }
    }

    private void stopUserAudio() {
        if (userAudioPlayer != null) {
            try { if (userAudioPlayer.isPlaying()) userAudioPlayer.stop(); } catch (Exception ignored) {}
            try { userAudioPlayer.release(); } catch (Exception ignored) {}
            userAudioPlayer = null;
        }
        currentAudioDataUrl = null;
    }

    // 通过 FileProvider 调起系统安装器安装 APK（Android 7+ 必须用 content:// URI）
    private void installApk(File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(this, FILE_PROVIDER_AUTH, apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            toast("安装失败，请前往文件管理器手动安装");
        }
    }

    private void toast(final String msg) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show());
    }

    // 创建通知渠道
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) {
                    NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
                    channel.setDescription("日历提醒与闹钟提示");
                    nm.createNotificationChannel(channel);
                }
            } catch (Exception ignored) {}
        }
    }

    // 显示日历提醒通知
    private void showCalendarNotification(String title, String content) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title != null ? title : "日历提醒")
                    .setContentText(content != null ? content : "")
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(content != null ? content : ""))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);
            nm.notify((int) (System.currentTimeMillis() % 100000), builder.build());
        } catch (Exception e) {
            // 通知失败时静默
        }
    }

    // 振动反馈
    private void doVibrate(long ms) {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(ms);
            }
        } catch (Exception ignored) {}
    }

    // 供前端调用的原生桥
    private class AndroidBridge {
        @JavascriptInterface
        public void reloadApp() {
            runOnUiThread(() -> webView.loadUrl(BASE_URL));
        }

        // 循环播放告警提示音（触摸屏幕或按任意物理键停止）
        // 单参：日历/闹钟提醒，走闹钟流（勿扰/静音也响，保持用户闹钟音量、不强制最大）
        @JavascriptInterface
        public void playAlarm(String sound) {
            runOnUiThread(() -> startNativeAlarm(sound, false));
        }

        // 打坐/念诵倒计时提示音：走媒体流(STREAM_MUSIC)，跟随系统媒体音量，可用音量键实时调节
        @JavascriptInterface
        public void playAlarmMedia(String sound) {
            runOnUiThread(() -> startNativeAlarm(sound, true));
        }

        @JavascriptInterface
        public void stopAlarm() {
            runOnUiThread(() -> stopNativeAlarm());
        }

        // 系统 TTS 语音朗读（读经）
        @JavascriptInterface
        public void speak(String text, float rate, String gender) {
            runOnUiThread(() -> {
                if (!ttsReady || tts == null || text == null || text.isEmpty()) {
                    notifyTtsUnavailable();
                    return;
                }
                // 部分设备中文语音包加载较晚，调用时再尝试一次，避免误判不可用
                if (!ttsChineseOk) {
                    ttsChineseOk = setChineseIfAvailable();
                }
                if (!ttsChineseOk) {
                    notifyTtsUnavailable();
                    return;
                }
                try {
                    tts.setSpeechRate(rate > 0 ? rate : 1.0f);
                    // 按性别选择中文语音；多数设备仅单一中文语音时，改用音高营造男/女声差异
                    final boolean wantMale = "male".equals(gender);
                    Voice selected = null;
                    try {
                        for (Voice v : tts.getVoices()) {
                            if (v == null) continue;
                            String nm = (v.getName() == null) ? "" : v.getName().toLowerCase();
                            String loc = (v.getLocale() != null) ? v.getLocale().toString().toLowerCase() : "";
                            boolean zh = loc.contains("zh") || nm.contains("chinese") || nm.contains("中文");
                            if (!zh) continue;
                            boolean male = nm.contains("male") || nm.contains("男") || nm.contains("yang") || nm.contains("yue");
                            boolean female = nm.contains("female") || nm.contains("女") || nm.contains("ying") || nm.contains("xiaoxiao") || nm.contains("mei");
                            if (wantMale && male) { selected = v; break; }
                            if (!wantMale && female) { selected = v; break; }
                        }
                    } catch (Exception ignored) {}
                    if (selected != null) { try { tts.setVoice(selected); } catch (Exception ignored) {} }
                    // 音高：男声降低、女声略升（KEY_PARAM_PITCH 受主流 TTS 引擎支持）
                    final float pitch = wantMale ? 0.6f : 1.12f;
                    tts.stop();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        Bundle sp = new Bundle();
                        sp.putString("pitch", String.valueOf(pitch));
                        tts.speak(text, TextToSpeech.QUEUE_FLUSH, sp, "sutra_" + System.currentTimeMillis());
                    } else {
                        HashMap<String, String> params = new HashMap<>();
                        params.put("pitch", String.valueOf(pitch));
                        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "sutra_" + System.currentTimeMillis());
                        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
                    }
                } catch (Exception e) {
                    notifyTtsUnavailable();
                }
            });
        }

        // 通知前端：TTS 不可用（多为设备未安装中文语音包）
        private void notifyTtsUnavailable() {
            if (webView != null) {
                webView.evaluateJavascript("if(typeof onTtsUnavailable==='function') onTtsUnavailable();", null);
            }
        }

        // 前端导入音频前，告知原生目标计数器索引
        @JavascriptInterface
        public void setAudioPickIndex(int index) {
            pendingAudioIndex = index;
        }

        // 前端音频选择前，告知原生用途：counter=计数器提示音，alarm=闹钟自定义铃声
        @JavascriptInterface
        public void setAudioPickPurpose(String purpose) {
            pendingAudioPurpose = (purpose != null && !purpose.isEmpty()) ? purpose : "counter";
        }

        // 前端查询：当前 TTS 是否就绪且支持中文（避免回调时机不确定导致漏判）
        @JavascriptInterface
        public boolean isTtsChineseAvailable() {
            return ttsReady && ttsChineseOk;
        }

        @JavascriptInterface
        public void stopSpeak() {
            runOnUiThread(() -> { if (tts != null) { try { tts.stop(); } catch (Exception ignored) {} } });
        }

        // 未安装中文语音包时，打开系统 TTS 语音数据安装界面，引导用户安装
        @JavascriptInterface
        public void installTtsData() {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception ignored) {}
            });
        }

        // 下载任意网页/直链文本内容（绕过 WebView 的 CORS 限制），结果经回调返回前端
        @JavascriptInterface
        public void fetchUrl(final String url) {
            new Thread(() -> {
                HttpURLConnection conn = null;
                try {
                    URL u = new URL(url);
                    conn = (HttpURLConnection) u.openConnection();
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36");
                    conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,text/plain,application/json;q=0.9,*/*;q=0.8");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(20000);
                    conn.setInstanceFollowRedirects(true);
                    conn.connect();
                    int code = conn.getResponseCode();
                    if (code >= 300 && code < 400) {
                        String loc = conn.getHeaderField("Location");
                        if (loc != null && !loc.isEmpty()) {
                            try { conn.disconnect(); } catch (Exception ignored) {}
                            u = new URL(u, loc);
                            conn = (HttpURLConnection) u.openConnection();
                            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36");
                            conn.connect();
                        }
                    }
                    String contentType = conn.getContentType();
                    String charset = "UTF-8";
                    if (contentType != null) {
                        int idx = contentType.toLowerCase().indexOf("charset=");
                        if (idx > 0) charset = contentType.substring(idx + 8).trim().split(";")[0];
                    }
                    InputStream in = conn.getInputStream();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n; long total = 0;
                    while ((n = in.read(buf)) > 0) {
                        baos.write(buf, 0, n);
                        total += n;
                        if (total > 5_000_000) break; // 上限 5MB，避免超大页卡死
                    }
                    in.close();
                    byte[] data = baos.toByteArray();
                    // 未声明 charset 时，扫描 <meta> 嗅探 GBK 系列
                    if (charset == null || charset.isEmpty() || charset.equalsIgnoreCase("UTF-8")) {
                        String head = new String(data, 0, Math.min(data.length, 2048), java.nio.charset.StandardCharsets.ISO_8859_1).toLowerCase();
                        if (head.contains("charset=gbk") || head.contains("charset=gb2312") || head.contains("charset=gb18030")) {
                            charset = "GBK";
                        }
                    }
                    final String body = new String(data, charset);
                    final String safe = JSONObject.quote(body);
                    final int len = body.length();
                    runOnUiThread(() -> webView.evaluateJavascript("window.__lastFetch=" + safe + "; if(typeof window.__onFetchUrlDone==='function'){window.__onFetchUrlDone(true,''," + len + ");}", null));
                } catch (final Exception e) {
                    final String msg = (e != null && e.getMessage() != null) ? e.getMessage() : "下载失败";
                    runOnUiThread(() -> webView.evaluateJavascript("if(typeof window.__onFetchUrlDone==='function'){window.__onFetchUrlDone(false," + JSONObject.quote(msg) + ",0);}", null));
                } finally {
                    if (conn != null) { try { conn.disconnect(); } catch (Exception ignored) {} }
                }
            }).start();
        }

        // 检查更新：下载 APK 并调起系统安装（参数 apkUrl 为可直接下载的 apk 地址）
        @JavascriptInterface
        public void downloadAndInstall(final String apkUrl) {
            runOnUiThread(() -> {
                if (apkUrl == null || apkUrl.isEmpty()) {
                    toast("更新地址无效");
                    return;
                }
                toast("正在下载更新…");
                new Thread(() -> {
                    try {
                        File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                        if (dir == null) dir = getFilesDir();
                        if (!dir.exists()) dir.mkdirs();
                        File apk = new File(dir, "update.apk");
                        URL url = new URL(apkUrl);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(60000);
                        conn.setRequestProperty("Accept", "*/*");
                        conn.connect();
                        InputStream in = conn.getInputStream();
                        FileOutputStream fos = new FileOutputStream(apk);
                        byte[] buf = new byte[8192];
                        int len;
                        long total = 0;
                        while ((len = in.read(buf)) > 0) {
                            fos.write(buf, 0, len);
                            total += len;
                        }
                        fos.close();
                        in.close();
                        conn.disconnect();
                        final long size = total;
                        runOnUiThread(() -> {
                            toast("下载完成，正在打开安装…");
                            installApk(apk);
                        });
                    } catch (Exception e) {
                        final String msg = (e != null && e.getMessage() != null) ? e.getMessage() : "未知错误";
                        runOnUiThread(() -> toast("下载失败：" + msg));
                    }
                }).start();
            });
        }

        // 播放用户导入的音频（data URL）
        @JavascriptInterface
        public void playAudio(String dataUrl, boolean loop) {
            runOnUiThread(() -> playUserAudio(dataUrl, loop));
        }

        @JavascriptInterface
        public void stopAudio() {
            runOnUiThread(() -> stopUserAudio());
        }

        @JavascriptInterface
        public boolean isAudioPlaying() {
            return userAudioPlayer != null && userAudioPlayer.isPlaying();
        }

        // 发送通知到系统通知栏（日历提醒）
        @JavascriptInterface
        public void showNotification(String title, String content) {
            runOnUiThread(() -> showCalendarNotification(title, content));
        }

        // 振动反馈
        @JavascriptInterface
        public void vibrate(long ms) {
            runOnUiThread(() -> doVibrate(ms));
        }

        // 意见反馈：内置 QQ 邮箱 SMTP 发送（授权码方式），后台线程执行，结果回传前端
        @JavascriptInterface
        public void sendFeedback(String name, String contact, String message, String version) {
            final String n = (name == null) ? "" : name;
            final String c = (contact == null) ? "" : contact;
            final String m = (message == null) ? "" : message;
            final String v = (version == null) ? "" : version;
            new Thread(() -> {
                try {
                    sendFeedbackMail(n, c, m, v);
                    runOnUiThread(() -> webView.evaluateJavascript(
                        "if(window.__onFeedbackResult)window.__onFeedbackResult(true,'提交成功，南无阿弥陀佛，感谢您的反馈！');", null));
                } catch (final Exception e) {
                    final String err = (e != null && e.getMessage() != null) ? e.getMessage() : "发送失败";
                    final String arg = org.json.JSONObject.quote(err);
                    runOnUiThread(() -> webView.evaluateJavascript(
                        "if(window.__onFeedbackResult)window.__onFeedbackResult(false," + arg + ");", null));
                }
            }).start();
        }

        private void sendFeedbackMail(String name, String contact, String message, String version) throws Exception {
            final String HOST = "smtp.qq.com";
            final String PORT = "465";
            final String ACCOUNT = "36612255@qq.com";
            // TODO(安全): 下方为 QQ 邮箱授权码。本 GitHub 仓库为公开仓库，APK 可被任何人下载并反编译提取此码，
            // 建议将仓库设为私有，或改用后端转发。当前填入的是用户提供的占位值，请替换为真实 16 位授权码后再构建。
            final String AUTH_CODE = "授权码_0dba";
            Properties props = new Properties();
            props.put("mail.smtp.host", HOST);
            props.put("mail.smtp.port", PORT);
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.port", PORT);
            Session session = Session.getInstance(props, new javax.mail.Authenticator() {
                @Override
                protected javax.mail.PasswordAuthentication getPasswordAuthentication() {
                    return new javax.mail.PasswordAuthentication(ACCOUNT, AUTH_CODE);
                }
            });
            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(ACCOUNT));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(ACCOUNT));
            msg.setSubject("九品莲台 用户反馈 v" + version, "UTF-8");
            String body = "称呼：" + name + "\n联系方式：" + contact + "\n版本：" + version + "\n\n反馈内容：\n" + message;
            msg.setText(body, "UTF-8");
            msg.setSentDate(new Date());
            Transport.send(msg);
        }
    }
}
