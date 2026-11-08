package com.hassan.btkeyboard;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

@SuppressLint("MissingPermission")
public class MainActivity extends Activity {
    // Makes the whole UI scale with the phone's screen width (small phones, big phones, tablets).
    // Normal phones (360-420 dp wide) stay exactly the same.
    @Override
    protected void attachBaseContext(Context base) {
        Configuration c = new Configuration(base.getResources().getConfiguration());
        float sw = c.smallestScreenWidthDp;
        if (sw <= 0) sw = Math.min(c.screenWidthDp, c.screenHeightDp);
        float f = 1f;
        if (sw > 0 && sw < 360f) {
            f = Math.max(0.8f, sw / 360f);
        } else if (sw > 420f) {
            f = Math.min(1.6f, sw / 420f);
        }
        if (f != 1f && c.densityDpi > 0) {
            c.densityDpi = Math.round(c.densityDpi * f);
            super.attachBaseContext(base.createConfigurationContext(c));
        } else {
            super.attachBaseContext(base);
        }
    }
    private static byte[] bytes(int... v) {
        byte[] o = new byte[v.length];
        for (int i = 0; i < v.length; i++) o[i] = (byte) v[i];
        return o;
    }

    // HID: keyboard (ID 1, 8 bytes) + mouse (ID 2, 4 bytes) + media keys (ID 3, 2 bytes)
    private static final byte[] DESC = bytes(
            0x05, 0x01, 0x09, 0x06, 0xA1, 0x01, 0x85, 0x01,
            0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7, 0x15, 0x00, 0x25, 0x01,
            0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
            0x95, 0x01, 0x75, 0x08, 0x81, 0x01,
            0x95, 0x05, 0x75, 0x01, 0x05, 0x08, 0x19, 0x01, 0x29, 0x05, 0x91, 0x02,
            0x95, 0x01, 0x75, 0x03, 0x91, 0x01,
            0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65,
            0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81, 0x00,
            0xC0,
            0x05, 0x01, 0x09, 0x02, 0xA1, 0x01, 0x85, 0x02, 0x09, 0x01, 0xA1, 0x00,
            0x05, 0x09, 0x19, 0x01, 0x29, 0x03, 0x15, 0x00, 0x25, 0x01,
            0x95, 0x03, 0x75, 0x01, 0x81, 0x02,
            0x95, 0x01, 0x75, 0x05, 0x81, 0x01,
            0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x38,
            0x15, 0x81, 0x25, 0x7F, 0x75, 0x08, 0x95, 0x03, 0x81, 0x06,
            0xC0, 0xC0,
            0x05, 0x0C, 0x09, 0x01, 0xA1, 0x01, 0x85, 0x03,
            0x15, 0x00, 0x26, 0xFF, 0x03, 0x19, 0x00, 0x2A, 0xFF, 0x03,
            0x75, 0x10, 0x95, 0x01, 0x81, 0x00,
            0xC0);

    // themes: bg, key, mod, pressed, accent, green, text, pad, box, hint
    private static final int[][] THEMES = {
            {0xFF121212, 0xFF2C2C2E, 0xFF3F3F46, 0xFF6B6B73, 0xFF0A84FF, 0xFF30A46C,
                    0xFFFFFFFF, 0xFF1C3A4D, 0xFF1E1E22, 0xFF8E8E93},
            {0xFFE5E5EA, 0xFFFFFFFF, 0xFFC7C7CC, 0xFF9A9AA0, 0xFF007AFF, 0xFF34C759,
                    0xFF111111, 0xFFBFD9F2, 0xFFFFFFFF, 0xFF8E8E93},
            {0xFF0B1B2B, 0xFF16324A, 0xFF21486A, 0xFF3A6E9C, 0xFF1EA7FD, 0xFF2BC48A,
                    0xFFEAF6FF, 0xFF0F3A57, 0xFF10283D, 0xFF6F8CA6},
            {0xFF000000, 0xFF1A1A1A, 0xFF2A2A2A, 0xFF555555, 0xFFFF7A00, 0xFF00C853,
                    0xFFFFFFFF, 0xFF161616, 0xFF101010, 0xFF777777},
            {0xFF1A1325, 0xFF2E2347, 0xFF45336B, 0xFF6D55A3, 0xFFA855F7, 0xFF22C55E,
                    0xFFF5EEFF, 0xFF2B1F4A, 0xFF241A38, 0xFF8C7BA8}
    };
    private static final String[] THEME_NAMES = {"Dark", "Light", "Ocean", "AMOLED", "Purple"};
    private int theme = 0;
    private int cBg, cKey, cMod, cPressed, cAccent, cGreen, cText, cPad, cBox, cHint, cStroke;
    private float sens = 1.8f;
    private int typeDelay = 14;
    private boolean liveGboard = false;

    private void loadTheme() {
        theme = getSharedPreferences("ui", MODE_PRIVATE).getInt("theme", 0) % THEMES.length;
        sens = getSharedPreferences("ui", MODE_PRIVATE).getInt("sens", 18) / 10f;
        typeDelay = getSharedPreferences("ui", MODE_PRIVATE).getInt("delay", 14);
        liveGboard = getSharedPreferences("ui", MODE_PRIVATE).getBoolean("live", false);
        int[] t = THEMES[theme];
        cBg = t[0]; cKey = t[1]; cMod = t[2]; cPressed = t[3]; cAccent = t[4];
        cGreen = t[5]; cText = t[6]; cPad = t[7]; cBox = t[8]; cHint = t[9];
        cStroke = (cText & 0x00FFFFFF) | 0x24000000;
    }

    private void applyTheme(int idx) {
        getSharedPreferences("ui", MODE_PRIVATE).edit().putInt("theme", idx).apply();
        recreate();
    }

    private static final int M_CTRL = 0x01, M_SHIFT = 0x02, M_ALT = 0x04, M_WIN = 0x08;

    // char <-> HID key table (US layout)
    private static final HashMap<Character, int[]> CHARS = new HashMap<>();
    private static final HashMap<Integer, Character> REV = new HashMap<>();

    private static void addChar(char c, int mod, int code) {
        CHARS.put(c, new int[]{mod, code});
        REV.put((mod != 0 ? 0x100 : 0) | code, c);
    }

    static {
        for (int i = 0; i < 26; i++) {
            addChar((char) ('a' + i), 0, 0x04 + i);
            addChar((char) ('A' + i), M_SHIFT, 0x04 + i);
        }
        String num = "1234567890";
        String shn = "!@#$%^&*()";
        for (int i = 0; i < 10; i++) {
            int code = (i == 9) ? 0x27 : 0x1E + i;
            addChar(num.charAt(i), 0, code);
            addChar(shn.charAt(i), M_SHIFT, code);
        }
        String[] sym = {"-_", "=+", "[{", "]}", "\\|", ";:", "'\"", "`~", ",<", ".>", "/?"};
        int[] sc = {0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38};
        for (int i = 0; i < sym.length; i++) {
            addChar(sym[i].charAt(0), 0, sc[i]);
            addChar(sym[i].charAt(1), M_SHIFT, sc[i]);
        }
        addChar(' ', 0, 0x2C);
        addChar('\n', 0, 0x28);
        addChar('\t', 0, 0x2B);
    }

    // ---- Bluetooth state lives in statics so the connection survives leaving the app ----
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static final Executor MAIN_EXEC = r -> H.post(r);
    private static Context sApp;
    private static MainActivity sUi;
    private static BluetoothAdapter sAdapter;
    private static BluetoothHidDevice sHid;
    private static BluetoothDevice sHost;
    private static boolean sRegistered = false;
    private static boolean sProxyRequested = false;
    private static int sRetries = 0;

    private static final BluetoothHidDevice.Callback HID_CB = new BluetoothHidDevice.Callback() {
        @Override
        public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean reg) {
            sRegistered = reg;
            if (reg && sHost == null) reconnectLast();
            refreshUi();
        }

        @Override
        public void onGetReport(BluetoothDevice device, byte type, byte id, int bufferSize) {
            try {
                int rid = id & 0xFF;
                int len = rid == 1 ? 8 : (rid == 2 ? 4 : (rid == 3 ? 2 : 0));
                sHid.replyReport(device, type, id, new byte[len]);
            } catch (Throwable ignored) {
            }
        }

        @Override
        public void onSetReport(BluetoothDevice device, byte type, byte id, byte[] data) {
            try {
                sHid.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS);
            } catch (Throwable ignored) {
            }
        }

        @Override
        public void onSetProtocol(BluetoothDevice device, byte protocol) { }

        @Override
        public void onConnectionStateChanged(BluetoothDevice device, int state) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                sHost = device;
                sRetries = 0;
                try {
                    sApp.getSharedPreferences("bt", Context.MODE_PRIVATE).edit()
                            .putString("last", device.getAddress()).apply();
                } catch (Throwable ignored) {
                }
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                if (device.equals(sHost)) sHost = null;
                if (sHid != null && sRetries < 6) {
                    sRetries++;
                    H.postDelayed(MainActivity::reconnectLast, 4000);
                }
            }
            refreshUi();
        }
    };

    private static final BluetoothProfile.ServiceListener PROXY_L =
            new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    if (profile == BluetoothProfile.HID_DEVICE) {
                        sHid = (BluetoothHidDevice) proxy;
                        registerKeyboard();
                    }
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    sHid = null;
                    sRegistered = false;
                    sHost = null;
                    sProxyRequested = false;
                    refreshUi();
                }
            };

    private static void registerKeyboard() {
        try {
            BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
                    "BT Keyboard", "Phone keyboard", "Hassan",
                    BluetoothHidDevice.SUBCLASS1_COMBO, DESC);
            sHid.registerApp(sdp, null, null, MAIN_EXEC, HID_CB);
        } catch (Throwable t) {
            showStatus("HID error: " + t);
        }
    }

    private static void reconnectLast() {
        try {
            if (sHid == null || sHost != null || sAdapter == null || sApp == null) return;
            String addr = sApp.getSharedPreferences("bt", Context.MODE_PRIVATE)
                    .getString("last", null);
            if (addr == null) return;
            sHid.connect(sAdapter.getRemoteDevice(addr));
        } catch (Throwable ignored) {
        }
    }

    private static void shutdownHid() {
        try {
            if (sHid != null) {
                sHid.unregisterApp();
                if (sAdapter != null) {
                    sAdapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, sHid);
                }
            }
        } catch (Throwable ignored) {
        }
        sHid = null;
        sHost = null;
        sRegistered = false;
        sProxyRequested = false;
        refreshUi();
    }

    private static void refreshUi() {
        if (sUi != null) sUi.updateStatus();
    }

    private static void showStatus(String s) {
        if (sUi != null && sUi.status != null) sUi.status.setText(s);
    }

    // Keeps the app process alive so the laptop stays connected
    public static class KeepAliveService extends Service {
        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            if (intent != null && "STOP".equals(intent.getAction())) {
                shutdownHid();
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            }
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel(
                    "btkb", "BT Keyboard", NotificationManager.IMPORTANCE_LOW));
            PendingIntent openPi = PendingIntent.getActivity(this, 0,
                    new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
            Intent stop = new Intent(this, KeepAliveService.class);
            stop.setAction("STOP");
            PendingIntent stopPi = PendingIntent.getService(this, 1, stop,
                    PendingIntent.FLAG_IMMUTABLE);
            Notification.Action act = new Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    "Band karo", stopPi).build();
            Notification n = new Notification.Builder(this, "btkb")
                    .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                    .setContentTitle("BT Keyboard chal raha hai")
                    .setContentText("Laptop se connection bana rahega")
                    .setContentIntent(openPi)
                    .addAction(act)
                    .setOngoing(true)
                    .build();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            } else {
                startForeground(1, n);
            }
            return START_NOT_STICKY;
        }
    }

    // ---- per-screen state ----
    private int mods = 0;
    private int mouseButtons = 0;

    private float lastX, lastY, downX, downY, accX, accY, scrollLast, scrollAcc;
    private long downTime;
    private boolean padMoved;

    private TextView status;
    private View dot;
    private EditText box;
    private Button[] tabBtns;
    private Button sendBtn;
    private LinearLayout boxRow, kb, gboardPage, mousePage, mainRoot;
    private View settingsRoot;
    private boolean settingsOpen = false;
    private int tab = 0;
    private boolean suppress = false;
    private String prev = "";

    private final Runnable syncRun = () -> {
        String cur = box.getText().toString();
        if (!cur.equals(prev)) {
            String was = prev;
            prev = cur;
            syncToLaptop(was, cur);
        }
    };

    private final ArrayDeque<int[]> queue = new ArrayDeque<>();
    private boolean pumping = false;

    private final List<Button> letterButtons = new ArrayList<>();
    private final List<Button> modButtons = new ArrayList<>();
    private final List<K> modKeys = new ArrayList<>();
    private final List<LinearLayout> letterViews = new ArrayList<>();
    private final List<LinearLayout> symViews = new ArrayList<>();
    private boolean symbols = false;
    private Button symToggle;

    static class K {
        final String label;
        final int code;
        final int mod;
        final float w;
        final int fmod;   // modifier always sent with this key (Shift for "@", "#" ...)

        K(String label, int code, int mod, float w, int fmod) {
            this.label = label;
            this.code = code;
            this.mod = mod;
            this.w = w;
            this.fmod = fmod;
        }

        K(String label, int code, int mod, float w) {
            this(label, code, mod, w, 0);
        }
    }

    private static K k(String l, int c) { return new K(l, c, 0, 1f); }
    private static K kw(String l, int c, float w) { return new K(l, c, 0, w); }
    private static K mw(String l, int mod, float w) { return new K(l, 0, mod, w); }
    private static K sp(float w) { return new K("", -1, 0, w); }
    private static K ks(String l, int c) { return new K(l, c, 0, 1f, M_SHIFT); }
    private static K kt(float w) { return new K("?123", -2, 0, w); }

    private static K[] letters(String s) {
        K[] r = new K[s.length()];
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            r[i] = k(String.valueOf(ch), 0x04 + (ch - 'a'));
        }
        return r;
    }

    private static K[] digits() {
        K[] r = new K[10];
        for (int i = 0; i < 10; i++) {
            int n = (i + 1) % 10;
            r[i] = k(String.valueOf(n), n == 0 ? 0x27 : 0x1E + (n - 1));
        }
        return r;
    }

    private static K[] concat(K[] a, K... b) {
        K[] r = new K[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    // ---------------- colour / drawable helpers ----------------

    private static int adj(int c, float f) {
        int a = c & 0xFF000000;
        int r = Math.min(255, Math.max(0, Math.round(((c >> 16) & 0xFF) * f)));
        int g = Math.min(255, Math.max(0, Math.round(((c >> 8) & 0xFF) * f)));
        int b = Math.min(255, Math.max(0, Math.round((c & 0xFF) * f)));
        return a | (r << 16) | (g << 8) | b;
    }

    private static int blend(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private Typeface medium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    // soft page background with a faint accent glow at the top
    private void setPageBg(View v) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{blend(cBg, cAccent, 0.14f), cBg});
        v.setBackground(g);
    }

    // flat rounded fill
    private void setBg(View v, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(10));
        v.setBackground(g);
    }

    // rounded card with a thin border
    private void setCardBg(View v, int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(1), cStroke);
        v.setBackground(g);
    }

    // 3D key look: lighter face on a darker base
    private void setKeyBg(View v, int color) {
        GradientDrawable base = new GradientDrawable();
        base.setColor(adj(color, 0.70f));
        base.setCornerRadius(dp(10));
        GradientDrawable face = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{adj(color, 1.14f), color});
        face.setCornerRadius(dp(10));
        LayerDrawable ld = new LayerDrawable(new Drawable[]{base, face});
        ld.setLayerInset(1, 0, 0, 0, dp(2));
        v.setBackground(ld);
    }

    private void setDotColor(int color) {
        if (dot == null) return;
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        dot.setBackground(g);
    }

    // ---------------- lifecycle ----------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sApp = getApplicationContext();

        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, ex) -> {
            try {
                getSharedPreferences("crash", MODE_PRIVATE).edit()
                        .putString("t", Log.getStackTraceString(ex)).commit();
            } catch (Throwable ignored) {
            }
            if (old != null) old.uncaughtException(t, ex);
        });

        String crash = getSharedPreferences("crash", MODE_PRIVATE).getString("t", null);
        if (crash != null) {
            showCrash(crash);
            return;
        }

        try {
            loadTheme();
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            buildUi();
            sUi = this;
            if (savedInstanceState != null && savedInstanceState.getBoolean("settings", false)) {
                openSettings();
            }
            List<String> need = new ArrayList<>();
            if (Build.VERSION.SDK_INT >= 31
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.POST_NOTIFICATIONS);
            }
            if (!need.isEmpty()) {
                requestPermissions(need.toArray(new String[0]), 1);
            } else {
                initHid();
            }
        } catch (Throwable t) {
            showCrash(Log.getStackTraceString(t));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("settings", settingsOpen);
    }

    @Override
    public void onBackPressed() {
        if (settingsOpen) {
            closeSettings();
        } else {
            super.onBackPressed();
        }
    }

    private boolean hasBtPermission() {
        return Build.VERSION.SDK_INT < 31
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void showCrash(String trace) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundColor(0xFF121212);
        l.setPadding(dp(12), dp(24), dp(12), dp(12));

        TextView head = new TextView(this);
        head.setText("App mein error aaya. Is screen ka screenshot Claude ko bhejo:");
        head.setTextColor(Color.WHITE);
        head.setTextSize(16);
        l.addView(head);

        TextView body = new TextView(this);
        body.setText(trace.length() > 3500 ? trace.substring(0, 3500) : trace);
        body.setTextColor(0xFFFF8A80);
        body.setTextSize(11);
        body.setTextIsSelectable(true);
        ScrollView sv = new ScrollView(this);
        sv.addView(body);
        l.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        Button retry = new Button(this);
        retry.setText("Error saaf karo aur dobara try karo");
        retry.setAllCaps(false);
        retry.setOnClickListener(v -> {
            getSharedPreferences("crash", MODE_PRIVATE).edit().clear().commit();
            recreate();
        });
        l.addView(retry);
        setContentView(l);
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] p, int[] r) {
        super.onRequestPermissionsResult(rc, p, r);
        if (status == null) return;
        if (hasBtPermission()) {
            initHid();
        } else {
            status.setText("Bluetooth (Nearby devices) permission dena zaroori hai");
        }
    }

    // ---------------- main UI ----------------

    private void buildUi() {
        mainRoot = new LinearLayout(this);
        mainRoot.setOrientation(LinearLayout.VERTICAL);
        setPageBg(mainRoot);
        mainRoot.setPadding(dp(6), dp(8), dp(6), dp(8));

        // header: status dot + status text + settings icon
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        dot = new View(this);
        setDotColor(0xFF8E8E93);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(10), dp(10));
        dotLp.setMargins(dp(8), 0, dp(8), 0);
        header.addView(dot, dotLp);
        status = new TextView(this);
        status.setTextColor(cText);
        status.setTextSize(14);
        status.setTypeface(medium());
        status.setMaxLines(2);
        status.setText("Shuru ho raha hai...");
        header.addView(status, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(iconButton("\u2699", v -> openSettings()),
                new LinearLayout.LayoutParams(dp(44), dp(44)));
        mainRoot.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50)));

        // tabs (segmented control)
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(3), dp(3), dp(3), dp(3));
        setCardBg(tabs, cBox, 14);
        String[] tabNames = {"Keys", "Gboard", "Mouse"};
        tabBtns = new Button[3];
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            tabBtns[i] = barButton(tabNames[i], cMod, v -> setTab(idx));
            tabs.addView(tabBtns[i], barLp());
        }
        LinearLayout.LayoutParams tabsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        tabsLp.topMargin = dp(4);
        mainRoot.addView(tabs, tabsLp);

        // text box + paste / clear (Keys and Gboard tabs)
        boxRow = new LinearLayout(this);
        boxRow.setOrientation(LinearLayout.HORIZONTAL);

        box = new EditText(this);
        box.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        box.setHint("Yahan likha nazar aayega. Paste bhi kar sakte ho");
        box.setHintTextColor(cHint);
        box.setTextColor(cText);
        box.setTextSize(16);
        box.setGravity(Gravity.TOP | Gravity.START);
        box.setPadding(dp(12), dp(8), dp(12), dp(8));
        box.setShowSoftInputOnFocus(false);
        setCardBg(box, cBox, 14);
        box.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable e) {
                if (suppress) {
                    prev = e.toString();
                } else if (tab == 1 && !liveGboard) {
                    // manual mode: nothing goes to the laptop until Send is pressed
                } else {
                    // wait until the keyboard has finished editing, then send the change
                    H.removeCallbacks(syncRun);
                    H.postDelayed(syncRun, 70);
                }
            }
        });
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        boxLp.setMargins(dp(2), dp(2), dp(2), dp(2));
        boxRow.addView(box, boxLp);

        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        sLp.setMargins(0, dp(2), 0, dp(2));
        side.addView(barButton("Paste", cMod, v -> pasteText()), sLp);
        side.addView(barButton("Clear", cMod, v -> clearBox()), sLp);
        boxRow.addView(side, new LinearLayout.LayoutParams(
                dp(72), LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams boxRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(66));
        boxRowLp.topMargin = dp(4);
        mainRoot.addView(boxRow, boxRowLp);

        FrameLayout content = new FrameLayout(this);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        contentLp.topMargin = dp(4);
        mainRoot.addView(content, contentLp);

        // ---- page 0: on-screen keyboard ----
        kb = new LinearLayout(this);
        kb.setOrientation(LinearLayout.VERTICAL);
        content.addView(kb, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        List<K[]> topRows = new ArrayList<>();
        topRows.add(new K[]{k("Esc", 0x29), k("F1", 0x3A), k("F2", 0x3B), k("F3", 0x3C),
                k("F4", 0x3D), k("F5", 0x3E), k("F6", 0x3F), k("Del", 0x4C)});
        topRows.add(new K[]{k("F7", 0x40), k("F8", 0x41), k("F9", 0x42), k("F10", 0x43),
                k("F11", 0x44), k("F12", 0x45), k("Home", 0x4A), k("End", 0x4D)});
        topRows.add(new K[]{mw("Ctrl", M_CTRL, 1.3f), mw("Alt", M_ALT, 1.3f),
                mw("Win", M_WIN, 1.3f), k("Tab", 0x2B), k("Caps", 0x39),
                k("\u2190", 0x50), k("\u2193", 0x51), k("\u2191", 0x52), k("\u2192", 0x4F)});
        topRows.add(new K[]{k("`", 0x35), k("-", 0x2D), k("=", 0x2E), k("[", 0x2F),
                k("]", 0x30), k(";", 0x33), k("'", 0x34), k("\\", 0x31), k("/", 0x38)});
        topRows.add(digits());
        for (K[] row : topRows) addKbRow(row);

        // letters (shown normally)
        K[][] letterRows = {
                letters("qwertyuiop"),
                concat(new K[]{sp(0.5f)}, concat(letters("asdfghjkl"), sp(0.5f))),
                concat(new K[]{mw("Shift", M_SHIFT, 1.5f)},
                        concat(letters("zxcvbnm"), kw("\u232B", 0x2A, 1.5f)))
        };
        for (K[] row : letterRows) letterViews.add(addKbRow(row));

        // symbols + extra keys (shown after tapping ?123)
        K[][] symRows = {
                {ks("!", 0x1E), ks("@", 0x1F), ks("#", 0x20), ks("$", 0x21), ks("%", 0x22),
                        ks("^", 0x23), ks("&", 0x24), ks("*", 0x25), ks("(", 0x26),
                        ks(")", 0x27)},
                {ks("_", 0x2D), ks("+", 0x2E), ks("{", 0x2F), ks("}", 0x30), ks("|", 0x31),
                        ks(":", 0x33), ks("\"", 0x34), ks("<", 0x36), ks(">", 0x37),
                        ks("?", 0x38)},
                {ks("~", 0x35), k("PgUp", 0x4B), k("PgDn", 0x4E), k("Ins", 0x49),
                        k("PrtSc", 0x46), k("ScrLk", 0x47), k("Pause", 0x48),
                        k("Menu", 0x65), kw("\u232B", 0x2A, 1.3f)}
        };
        for (K[] row : symRows) {
            LinearLayout v = addKbRow(row);
            v.setVisibility(View.GONE);
            symViews.add(v);
        }

        addKbRow(new K[]{kt(1.3f), k(",", 0x36), kw("Space", 0x2C, 4f), k(".", 0x37),
                kw("Enter", 0x28, 1.5f)});

        // ---- page 1: Gboard (touchpad + Send + Enter) ----
        gboardPage = new LinearLayout(this);
        gboardPage.setOrientation(LinearLayout.VERTICAL);
        gboardPage.addView(buildPadRow(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        gboardPage.addView(buildClickRow(true), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));
        content.addView(gboardPage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // ---- page 2: mouse + shortcuts ----
        mousePage = new LinearLayout(this);
        mousePage.setOrientation(LinearLayout.VERTICAL);
        mousePage.addView(buildPadRow(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        mousePage.addView(buildClickRow(false), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout g1 = shortRow(grid);
        addShortcut(g1, "Screenshot", cGreen, M_WIN, 0x46, 0);
        addShortcut(g1, "Snip", cGreen, M_WIN | M_SHIFT, 0x16, 0);
        addShortcut(g1, "Desktop", cMod, M_WIN, 0x07, 0);
        addShortcut(g1, "Lock", cMod, M_WIN, 0x0F, 0);
        LinearLayout g2 = shortRow(grid);
        addShortcut(g2, "Copy", cMod, M_CTRL, 0x06, 0);
        addShortcut(g2, "Paste", cMod, M_CTRL, 0x19, 0);
        addShortcut(g2, "Cut", cMod, M_CTRL, 0x1B, 0);
        addShortcut(g2, "Undo", cMod, M_CTRL, 0x1D, 0);
        LinearLayout g3 = shortRow(grid);
        addShortcut(g3, "All", cMod, M_CTRL, 0x04, 0);
        addShortcut(g3, "Save", cMod, M_CTRL, 0x16, 0);
        addShortcut(g3, "Alt+Tab", cMod, M_ALT, 0x2B, 0);
        addShortcut(g3, "Task Mgr", cMod, M_CTRL | M_SHIFT, 0x29, 0);
        LinearLayout g4 = shortRow(grid);
        addShortcut(g4, "Vol -", cMod, 0, 0, 0xEA);
        addShortcut(g4, "Vol +", cMod, 0, 0, 0xE9);
        addShortcut(g4, "Mute", cMod, 0, 0, 0xE2);
        addShortcut(g4, "Play/Pause", cMod, 0, 0, 0xCD);
        LinearLayout g5 = shortRow(grid);
        addShortcut(g5, "Lang", cMod, M_WIN, 0x2C, 0);
        addShortcut(g5, "Close", cMod, M_ALT, 0x3D, 0);
        addShortcut(g5, "Start", cMod, M_WIN, 0, 0);
        addShortcut(g5, "Refresh", cMod, 0, 0x3E, 0);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.3f);
        gridLp.topMargin = dp(4);
        mousePage.addView(grid, gridLp);
        content.addView(mousePage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // settings screen sits on top of the main screen
        settingsRoot = buildSettings();
        settingsRoot.setVisibility(View.GONE);
        FrameLayout rootFrame = new FrameLayout(this);
        rootFrame.addView(mainRoot, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        rootFrame.addView(settingsRoot, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(rootFrame);
        setTab(0);
    }

    private LinearLayout addKbRow(K[] row) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (K key : row) {
            if (key.code == -1) {
                r.addView(new View(this), new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.MATCH_PARENT, key.w));
                continue;
            }
            Button b = makeKey(key);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, key.w);
            lp.setMargins(dp(2), dp(2), dp(2), dp(2));
            r.addView(b, lp);
        }
        kb.addView(r, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return r;
    }

    private void toggleSymbols() {
        symbols = !symbols;
        for (LinearLayout r : letterViews) r.setVisibility(symbols ? View.GONE : View.VISIBLE);
        for (LinearLayout r : symViews) r.setVisibility(symbols ? View.VISIBLE : View.GONE);
        symToggle.setText(symbols ? "ABC" : "?123");
    }

    private Button makeToggleKey() {
        final Button b = new Button(this);
        b.setText("?123");
        b.setAllCaps(false);
        b.setTextColor(cText);
        b.setTypeface(medium());
        b.setTextSize(13);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(0, 0, 0, 0);
        b.setStateListAnimator(null);
        setKeyBg(b, cMod);
        b.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            toggleSymbols();
        });
        symToggle = b;
        return b;
    }

    private LinearLayout buildPadRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView pad = new TextView(this);
        pad.setText("TOUCHPAD");
        pad.setTextColor(cHint);
        pad.setTextSize(11);
        pad.setLetterSpacing(0.25f);
        pad.setGravity(Gravity.CENTER);
        setCardBg(pad, cPad, 16);
        pad.setOnTouchListener(padListener());
        LinearLayout.LayoutParams padLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        padLp.setMargins(dp(2), dp(2), dp(2), dp(2));
        row.addView(pad, padLp);
        TextView scrollStrip = new TextView(this);
        scrollStrip.setText("\u2195");
        scrollStrip.setTextColor(cText);
        scrollStrip.setTextSize(22);
        scrollStrip.setGravity(Gravity.CENTER);
        setKeyBg(scrollStrip, cKey);
        scrollStrip.setOnTouchListener(scrollListener());
        LinearLayout.LayoutParams stripLp = new LinearLayout.LayoutParams(
                dp(52), LinearLayout.LayoutParams.MATCH_PARENT);
        stripLp.setMargins(dp(2), dp(2), dp(2), dp(2));
        row.addView(scrollStrip, stripLp);
        return row;
    }

    private LinearLayout buildClickRow(boolean withEnter) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(clickButton("Left Click", 1), barLp());
        row.addView(clickButton("Right Click", 2), barLp());
        if (withEnter) {
            sendBtn = barButton("Send", cGreen, v -> sendBox());
            sendBtn.setTextSize(17);
            row.addView(sendBtn, barLp(1.6f));
            Button enter = barButton("Enter \u21B5", cAccent, v -> tapKey(0x28));
            enter.setTextSize(17);
            row.addView(enter, barLp(1.6f));
        }
        return row;
    }

    private void tapKey(int code) {
        queue.add(new int[]{0, code});
        queue.add(new int[]{0, 0});
        pump();
    }

    private LinearLayout shortRow(LinearLayout parent) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(r, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return r;
    }

    private LinearLayout.LayoutParams barLp() {
        return barLp(1f);
    }

    private LinearLayout.LayoutParams barLp(float w) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, w);
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    private Button barButton(String text, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor((color == cMod || color == cKey) ? cText : Color.WHITE);
        b.setTextSize(14);
        b.setTypeface(medium());
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(4), 0, dp(4), 0);
        b.setStateListAnimator(null);
        setKeyBg(b, color);
        b.setOnClickListener(l);
        return b;
    }

    private Button iconButton(String glyph, View.OnClickListener l) {
        Button b = barButton(glyph, cMod, l);
        b.setTextSize(20);
        return b;
    }

    private void addShortcut(LinearLayout row, String label, int color,
                             final int mod, final int code, final int usage) {
        Button b = barButton(label, color, v -> runShortcut(label, mod, code, usage));
        b.setTextSize(14);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        row.addView(b, lp);
    }

    private void runShortcut(String label, int mod, int code, int usage) {
        if (usage != 0) {
            for (int i = 0; i < 3; i++) queue.add(new int[]{-1, usage});
            queue.add(new int[]{-1, 0});
        } else {
            for (int i = 0; i < 5; i++) queue.add(new int[]{mod, code});
            queue.add(new int[]{0, 0});
        }
        pump();
        if ("Screenshot".equals(label)) {
            Toast.makeText(this, "Screenshot gaya. Laptop: Pictures > Screenshots",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // tabs: 0 = Keys, 1 = Gboard (+touchpad), 2 = Mouse + shortcuts
    private void setTab(int t) {
        tab = t;
        kb.setVisibility(t == 0 ? View.VISIBLE : View.GONE);
        gboardPage.setVisibility(t == 1 ? View.VISIBLE : View.GONE);
        mousePage.setVisibility(t == 2 ? View.VISIBLE : View.GONE);
        boxRow.setVisibility(t == 2 ? View.GONE : View.VISIBLE);
        if (t != 1) {
            H.removeCallbacks(syncRun);
            if (liveGboard) {
                syncRun.run();
            } else {
                prev = box.getText().toString();
            }
        }
        updateSendUi();
        box.setShowSoftInputOnFocus(t == 1);
        final InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (t == 1) {
            box.requestFocus();
            box.post(() -> imm.showSoftInput(box, InputMethodManager.SHOW_IMPLICIT));
        } else {
            imm.hideSoftInputFromWindow(box.getWindowToken(), 0);
            box.clearFocus();
        }
        for (int i = 0; i < tabBtns.length; i++) {
            if (i == t) {
                GradientDrawable g = new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{adj(cAccent, 1.18f), cAccent});
                g.setCornerRadius(dp(11));
                tabBtns[i].setBackground(g);
                tabBtns[i].setTextColor(Color.WHITE);
            } else {
                tabBtns[i].setBackgroundColor(Color.TRANSPARENT);
                tabBtns[i].setTextColor(cText);
            }
        }
    }

    // Send button + hint only matter in Gboard tab with manual (non-live) typing
    private void updateSendUi() {
        boolean manual = (tab == 1 && !liveGboard);
        sendBtn.setVisibility(manual ? View.VISIBLE : View.GONE);
        box.setHint(manual ? "Yahan likho, phir Send dabao"
                : "Yahan likha nazar aayega. Paste bhi kar sakte ho");
    }

    private void sendBox() {
        String text = box.getText().toString();
        if (text.isEmpty()) {
            Toast.makeText(this, "Box khali hai", Toast.LENGTH_SHORT).show();
            return;
        }
        if (sHost == null) {
            Toast.makeText(this, "Pehle laptop connect karo", Toast.LENGTH_SHORT).show();
            return;
        }
        queueText(text);
        H.removeCallbacks(syncRun);
        suppress = true;
        box.setText("");
        suppress = false;
        prev = "";
    }

    // ---------------- settings screen ----------------

    private void openSettings() {
        settingsOpen = true;
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(box.getWindowToken(), 0);
        mainRoot.setVisibility(View.GONE);
        settingsRoot.setVisibility(View.VISIBLE);
    }

    private void closeSettings() {
        settingsOpen = false;
        settingsRoot.setVisibility(View.GONE);
        mainRoot.setVisibility(View.VISIBLE);
        setTab(tab);
    }

    private View buildSettings() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(12), dp(10), dp(12), dp(10));
        setPageBg(page);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(iconButton("\u2190", v -> closeSettings()),
                new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextColor(cText);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(14), 0, 0, 0);
        top.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        page.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col);
        page.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // connection
        col.addView(sectionLabel("CONNECTION"));
        LinearLayout c1 = card();
        c1.addView(settingRow("Laptop se connect karo", "Pair ki hui laptop chuno",
                barButton("Connect", cAccent, v -> chooseHost())));
        c1.addView(divider());
        c1.addView(settingRow("Phone visible karo", "Laptop se pairing ke liye 2 minute",
                barButton("Visible", cAccent, v -> makeVisible())));
        col.addView(c1, cardLp());

        // theme
        col.addView(sectionLabel("THEME"));
        LinearLayout c2 = card();
        LinearLayout sw = new LinearLayout(this);
        sw.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < THEMES.length; i++) {
            final int idx = i;
            LinearLayout one = new LinearLayout(this);
            one.setOrientation(LinearLayout.VERTICAL);
            one.setGravity(Gravity.CENTER_HORIZONTAL);
            View chip = new View(this);
            chip.setBackground(swatch(i, i == theme));
            chip.setOnClickListener(v -> applyTheme(idx));
            one.addView(chip, new LinearLayout.LayoutParams(dp(46), dp(46)));
            TextView nm = new TextView(this);
            nm.setText(THEME_NAMES[i]);
            nm.setTextSize(11);
            nm.setTextColor(i == theme ? cText : cHint);
            nm.setGravity(Gravity.CENTER);
            nm.setPadding(0, dp(6), 0, 0);
            one.addView(nm);
            sw.addView(one, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        c2.addView(sw);
        col.addView(c2, cardLp());

        // mouse speed
        col.addView(sectionLabel("MOUSE"));
        LinearLayout c3 = card();
        LinearLayout speedHead = new LinearLayout(this);
        speedHead.setOrientation(LinearLayout.HORIZONTAL);
        TextView sName = new TextView(this);
        sName.setText("Mouse speed");
        sName.setTextColor(cText);
        sName.setTextSize(15);
        sName.setTypeface(medium());
        speedHead.addView(sName, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final TextView sVal = new TextView(this);
        sVal.setTextColor(cHint);
        sVal.setTextSize(14);
        sVal.setText(String.format(Locale.US, "%.1fx", sens));
        speedHead.addView(sVal);
        c3.addView(speedHead);
        SeekBar sb = new SeekBar(this);
        sb.setMax(35);
        sb.setProgress(Math.round(sens * 10) - 5);
        sb.setProgressTintList(ColorStateList.valueOf(cAccent));
        sb.setThumbTintList(ColorStateList.valueOf(cAccent));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                sens = (p + 5) / 10f;
                sVal.setText(String.format(Locale.US, "%.1fx", sens));
                if (fromUser) {
                    getSharedPreferences("ui", MODE_PRIVATE).edit()
                            .putInt("sens", p + 5).apply();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) { }

            @Override
            public void onStopTrackingTouch(SeekBar s) { }
        });
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sbLp.topMargin = dp(8);
        c3.addView(sb, sbLp);
        col.addView(c3, cardLp());

        // typing
        col.addView(sectionLabel("TYPING"));
        LinearLayout c5 = card();
        Switch liveSw = new Switch(this);
        liveSw.setChecked(liveGboard);
        liveSw.setOnCheckedChangeListener((btn, on) -> {
            liveGboard = on;
            getSharedPreferences("ui", MODE_PRIVATE).edit().putBoolean("live", on).apply();
            updateSendUi();
        });
        c5.addView(settingRow("Gboard live typing",
                "Band: Send dabane par hi laptop par jayega", liveSw));
        c5.addView(divider());
        LinearLayout tHead = new LinearLayout(this);
        tHead.setOrientation(LinearLayout.HORIZONTAL);
        TextView tName = new TextView(this);
        tName.setText("Typing speed (wait)");
        tName.setTextColor(cText);
        tName.setTextSize(15);
        tName.setTypeface(medium());
        tHead.addView(tName, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final TextView tVal = new TextView(this);
        tVal.setTextColor(cHint);
        tVal.setTextSize(14);
        tVal.setText(typeDelay + " ms");
        tHead.addView(tVal);
        c5.addView(tHead);
        TextView tHint = new TextView(this);
        tHint.setText("Agar laptop par harf ulte ya alag lines mein aayein to slider ko "
                + "dayen (zyada ms) karo");
        tHint.setTextColor(cHint);
        tHint.setTextSize(12);
        c5.addView(tHint);
        SeekBar tb = new SeekBar(this);
        tb.setMax(44);
        tb.setProgress(Math.max(0, Math.min(44, typeDelay - 6)));
        tb.setProgressTintList(ColorStateList.valueOf(cAccent));
        tb.setThumbTintList(ColorStateList.valueOf(cAccent));
        tb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                typeDelay = p + 6;
                tVal.setText(typeDelay + " ms");
                if (fromUser) {
                    getSharedPreferences("ui", MODE_PRIVATE).edit()
                            .putInt("delay", typeDelay).apply();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) { }

            @Override
            public void onStopTrackingTouch(SeekBar s) { }
        });
        LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tbLp.topMargin = dp(8);
        c5.addView(tb, tbLp);
        col.addView(c5, cardLp());

        // stop
        col.addView(sectionLabel("BLUETOOTH"));
        LinearLayout c4 = card();
        c4.addView(settingRow("Connection band karo",
                "App band hogi, laptop disconnect ho jayega",
                barButton("Band karo", 0xFFE5484D, v -> stopAll())));
        col.addView(c4, cardLp());

        return page;
    }

    private TextView sectionLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(cHint);
        t.setTextSize(11);
        t.setLetterSpacing(0.12f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(4), dp(18), dp(4), dp(6));
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        setCardBg(c, cBox, 16);
        return c;
    }

    private LinearLayout.LayoutParams cardLp() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private View divider() {
        View d = new View(this);
        d.setBackgroundColor(cStroke);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        lp.setMargins(0, dp(10), 0, dp(10));
        d.setLayoutParams(lp);
        return d;
    }

    private LinearLayout settingRow(String title, String sub, View action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(cText);
        t.setTextSize(15);
        t.setTypeface(medium());
        TextView s = new TextView(this);
        s.setText(sub);
        s.setTextColor(cHint);
        s.setTextSize(12);
        texts.addView(t);
        texts.addView(s);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams alp = (action instanceof Button)
                ? new LinearLayout.LayoutParams(dp(100), dp(42))
                : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        alp.leftMargin = dp(10);
        row.addView(action, alp);
        return row;
    }

    private Drawable swatch(int i, boolean selected) {
        GradientDrawable outer = new GradientDrawable();
        outer.setColor(THEMES[i][0]);
        outer.setCornerRadius(dp(12));
        outer.setStroke(selected ? dp(3) : dp(1), selected ? cAccent : cStroke);
        GradientDrawable inner = new GradientDrawable();
        inner.setShape(GradientDrawable.OVAL);
        inner.setColor(THEMES[i][4]);
        LayerDrawable ld = new LayerDrawable(new Drawable[]{outer, inner});
        ld.setLayerInset(1, dp(13), dp(13), dp(13), dp(13));
        return ld;
    }

    private void stopAll() {
        try {
            Intent i = new Intent(this, KeepAliveService.class);
            i.setAction("STOP");
            startService(i);
        } catch (Throwable ignored) {
        }
        Toast.makeText(this, "Band kar diya", Toast.LENGTH_SHORT).show();
        finish();
    }

    // ---------------- paste / clear ----------------

    private void pasteText() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData cd = cm == null ? null : cm.getPrimaryClip();
        if (cd == null || cd.getItemCount() == 0) {
            Toast.makeText(this, "Clipboard khali hai", Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence t = cd.getItemAt(0).coerceToText(this);
        if (t == null || t.length() == 0) return;
        box.getText().append(t);   // box change triggers typing on the laptop
        box.setSelection(box.getText().length());
    }

    private void clearBox() {
        H.removeCallbacks(syncRun);
        suppress = true;
        box.setText("");
        suppress = false;
        prev = "";
    }

    // ---------------- keyboard keys ----------------

    private Button makeKey(final K key) {
        if (key.code == -2) return makeToggleKey();
        final Button b = new Button(this);
        b.setText(key.label);
        b.setAllCaps(false);
        b.setTextColor(cText);
        b.setTypeface(medium());
        b.setTextSize(key.label.length() > 4 ? 11 : (key.label.length() > 2 ? 13 : (key.label.length() == 2 ? 17 : 20)));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(0, 0, 0, 0);
        b.setStateListAnimator(null);
        setKeyBg(b, key.mod != 0 ? cMod : cKey);

        if (key.mod != 0) {
            modButtons.add(b);
            modKeys.add(key);
        } else if (key.label.length() == 1 && Character.isLetter(key.label.charAt(0))) {
            letterButtons.add(b);
        }

        b.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    if (key.mod == 0) {
                        setKeyBg(b, cPressed);
                        send(mods | key.fmod, key.code);
                        echo(key.code, key.fmod);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (key.mod != 0) {
                        mods ^= key.mod;
                        send(mods, 0);
                        refreshMods();
                    } else {
                        send(mods, 0);
                        setKeyBg(b, cKey);
                        if (mods != 0) {
                            mods = 0;
                            send(0, 0);
                            refreshMods();
                        }
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (key.mod == 0) {
                        send(mods, 0);
                        setKeyBg(b, cKey);
                    }
                    return true;
                default:
                    return true;
            }
        });
        return b;
    }

    // show what is typed with the on-screen keys inside the text box (display only)
    private void echo(int code, int fmod) {
        if ((mods & (M_CTRL | M_ALT | M_WIN)) != 0) return;
        boolean shift = ((mods | fmod) & M_SHIFT) != 0;
        suppress = true;
        try {
            Editable t = box.getText();
            if (code == 0x2A) {
                if (t.length() > 0) t.delete(t.length() - 1, t.length());
            } else if (code == 0x28) {
                t.clear();
            } else {
                Character c = REV.get((shift ? 0x100 : 0) | code);
                if (c != null && c != '\n' && c != '\t') t.append(c);
            }
        } finally {
            suppress = false;
        }
        prev = box.getText().toString();
        box.setSelection(box.getText().length());
    }

    private void refreshMods() {
        for (int i = 0; i < modButtons.size(); i++) {
            boolean on = (mods & modKeys.get(i).mod) != 0;
            setKeyBg(modButtons.get(i), on ? cAccent : cMod);
        }
        boolean shift = (mods & M_SHIFT) != 0;
        for (Button b : letterButtons) {
            String t = b.getText().toString();
            b.setText(shift ? t.toUpperCase() : t.toLowerCase());
        }
    }

    // ---------------- text box -> laptop ----------------

    private int countTypeable(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (CHARS.containsKey(s.charAt(i))) n++;
        }
        return n;
    }

    private void syncToLaptop(String old, String now) {
        int p = 0;
        int mn = Math.min(old.length(), now.length());
        while (p < mn && old.charAt(p) == now.charAt(p)) p++;
        int del = countTypeable(old.substring(p));
        if (del > 0) queueBackspaces(del);
        if (now.length() > p) queueText(now.substring(p));
    }

    private void queueBackspaces(int n) {
        for (int i = 0; i < n; i++) {
            queue.add(new int[]{0, 0x2A});
            queue.add(new int[]{0, 0});
        }
        pump();
    }

    private void queueText(String s) {
        boolean skipped = false;
        for (int i = 0; i < s.length(); i++) {
            int[] kc = CHARS.get(s.charAt(i));
            if (kc == null) {
                skipped = true;
                continue;
            }
            queue.add(new int[]{kc[0], kc[1]});
            queue.add(new int[]{0, 0});
        }
        if (skipped) {
            Toast.makeText(this, "Urdu/khaas huroof laptop par nahi ja sakte",
                    Toast.LENGTH_SHORT).show();
        }
        pump();
    }

    private int retries = 0;

    // sends one report at a time; retries if the Bluetooth stack says "busy"
    private final Runnable pumpRunnable = new Runnable() {
        @Override
        public void run() {
            int[] a = queue.peek();
            if (a == null) {
                pumping = false;
                return;
            }
            boolean ok = (a[0] == -1) ? sendConsumer(a[1]) : send(a[0], a[1]);
            if (ok || ++retries > 8) {
                queue.poll();
                retries = 0;
            }
            H.postDelayed(this, ok ? typeDelay : 10);
        }
    };

    private void pump() {
        if (pumping) return;
        pumping = true;
        queue.addFirst(new int[]{0, 0});   // start clean: release every key first
        H.post(pumpRunnable);
    }

    // ---------------- mouse ----------------

    private Button clickButton(String text, final int mask) {
        final Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(cText);
        b.setTypeface(medium());
        b.setTextSize(15);
        b.setStateListAnimator(null);
        setKeyBg(b, cMod);
        b.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    setKeyBg(b, cAccent);
                    mouseButtons |= mask;
                    sendMouse(0, 0, 0);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    setKeyBg(b, cMod);
                    mouseButtons &= ~mask;
                    sendMouse(0, 0, 0);
                    return true;
                default:
                    return true;
            }
        });
        return b;
    }

    private View.OnTouchListener padListener() {
        return (v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = downX = e.getX();
                    lastY = downY = e.getY();
                    downTime = e.getEventTime();
                    padMoved = false;
                    accX = 0;
                    accY = 0;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    accX += (e.getX() - lastX) * sens;
                    accY += (e.getY() - lastY) * sens;
                    lastX = e.getX();
                    lastY = e.getY();
                    if (Math.abs(lastX - downX) > dp(8) || Math.abs(lastY - downY) > dp(8)) {
                        padMoved = true;
                    }
                    int ix = (int) accX;
                    int iy = (int) accY;
                    if (ix != 0 || iy != 0) {
                        accX -= ix;
                        accY -= iy;
                        moveMouse(ix, iy);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!padMoved && e.getEventTime() - downTime < 250) {
                        mouseButtons |= 1;
                        sendMouse(0, 0, 0);
                        mouseButtons &= ~1;
                        sendMouse(0, 0, 0);
                    }
                    return true;
                default:
                    return true;
            }
        };
    }

    private View.OnTouchListener scrollListener() {
        return (v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    scrollLast = e.getY();
                    scrollAcc = 0;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    scrollAcc += e.getY() - scrollLast;
                    scrollLast = e.getY();
                    int steps = (int) (scrollAcc / dp(14));
                    if (steps != 0) {
                        scrollAcc -= steps * dp(14);
                        int w = Math.max(-127, Math.min(127, -steps));
                        sendMouse(0, 0, w);
                    }
                    return true;
                default:
                    return true;
            }
        };
    }

    private void moveMouse(int dx, int dy) {
        while (dx != 0 || dy != 0) {
            int sx = Math.max(-127, Math.min(127, dx));
            int sy = Math.max(-127, Math.min(127, dy));
            sendMouse(sx, sy, 0);
            dx -= sx;
            dy -= sy;
        }
    }

    private void sendMouse(int x, int y, int wheel) {
        if (sHid == null || sHost == null) return;
        byte[] r = new byte[4];
        r[0] = (byte) mouseButtons;
        r[1] = (byte) x;
        r[2] = (byte) y;
        r[3] = (byte) wheel;
        sHid.sendReport(sHost, 2, r);
    }

    private boolean send(int modifiers, int code) {
        if (sHid == null || sHost == null) return true;
        byte[] r = new byte[8];
        r[0] = (byte) modifiers;
        r[2] = (byte) code;
        return sHid.sendReport(sHost, 1, r);
    }

    private boolean sendConsumer(int usage) {
        if (sHid == null || sHost == null) return true;
        byte[] r = new byte[2];
        r[0] = (byte) (usage & 0xFF);
        r[1] = (byte) ((usage >> 8) & 0xFF);
        return sHid.sendReport(sHost, 3, r);
    }

    // ---------------- Bluetooth ----------------

    private void initHid() {
        startKeepAlive();
        if (sProxyRequested) {
            updateStatus();
            return;
        }
        try {
            BluetoothManager bm = getSystemService(BluetoothManager.class);
            sAdapter = bm == null ? null : bm.getAdapter();
            if (sAdapter == null) {
                status.setText("Is phone mein Bluetooth nahi hai");
                return;
            }
            if (!sAdapter.isEnabled()) {
                status.setText("Pehle Bluetooth on karo, phir app dobara kholo");
                return;
            }
            sProxyRequested = true;
            sAdapter.getProfileProxy(sApp, PROXY_L, BluetoothProfile.HID_DEVICE);
        } catch (Throwable t) {
            status.setText("Bluetooth error: " + t);
        }
    }

    private void startKeepAlive() {
        try {
            Intent i = new Intent(this, KeepAliveService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(i);
            } else {
                startService(i);
            }
        } catch (Throwable ignored) {
        }
    }

    private void updateStatus() {
        if (status == null) return;
        if (sHost != null) {
            status.setText("Connected: " + sHost.getName());
            setDotColor(0xFF34C759);
        } else if (sRegistered) {
            status.setText("Tayyar hai. Settings se laptop chuno");
            setDotColor(0xFFFFB020);
        } else {
            status.setText("Bluetooth keyboard shuru ho raha hai...");
            setDotColor(0xFF8E8E93);
        }
    }

    private void makeVisible() {
        Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        i.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120);
        startActivity(i);
    }

    private void chooseHost() {
        if (sHid == null || sAdapter == null) {
            Toast.makeText(this, "Bluetooth abhi tayyar nahi", Toast.LENGTH_SHORT).show();
            return;
        }
        final List<BluetoothDevice> list = new ArrayList<>(sAdapter.getBondedDevices());
        if (list.isEmpty()) {
            Toast.makeText(this, "Pehle laptop ko Bluetooth se pair karo",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) names[i] = list.get(i).getName();
        new AlertDialog.Builder(this)
                .setTitle("Laptop chuno")
                .setItems(names, (d, i) -> {
                    sRetries = 0;
                    boolean ok = sHid.connect(list.get(i));
                    status.setText(ok ? "Connect ho raha hai..."
                            : "Connect nahi hua, dobara try karo");
                    Toast.makeText(this, ok ? "Connect ho raha hai..." : "Connect nahi hua",
                            Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sUi == this) sUi = null;
        H.removeCallbacks(pumpRunnable);
        H.removeCallbacks(syncRun);
        queue.clear();
        pumping = false;
        // Bluetooth is NOT closed here, so the laptop stays connected
    }
}
