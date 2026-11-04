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
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
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
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executor;

@SuppressLint("MissingPermission")
public class MainActivity extends Activity {

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

    private static final int C_NORMAL = 0xFF2C2C2E;
    private static final int C_MOD = 0xFF3F3F46;
    private static final int C_PRESSED = 0xFF6B6B73;
    private static final int C_ACTIVE = 0xFF0A84FF;
    private static final int C_GREEN = 0xFF30A46C;

    private static final int M_CTRL = 0x01, M_SHIFT = 0x02, M_ALT = 0x04, M_WIN = 0x08;
    private static final float SENS = 1.8f;

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
    private EditText box;
    private Button modeBtn;
    private LinearLayout boxRow, padRow, clickRow, kb;
    private boolean gboardMode = false;
    private boolean suppress = false;
    private String prev = "";

    private final ArrayDeque<int[]> queue = new ArrayDeque<>();
    private boolean pumping = false;

    private final List<Button> letterButtons = new ArrayList<>();
    private final List<Button> modButtons = new ArrayList<>();
    private final List<K> modKeys = new ArrayList<>();

    static class K {
        final String label;
        final int code;
        final int mod;
        final float w;

        K(String label, int code, int mod, float w) {
            this.label = label;
            this.code = code;
            this.mod = mod;
            this.w = w;
        }
    }

    private static K k(String l, int c) { return new K(l, c, 0, 1f); }
    private static K kw(String l, int c, float w) { return new K(l, c, 0, w); }
    private static K mw(String l, int mod, float w) { return new K(l, 0, mod, w); }
    private static K sp(float w) { return new K("", -1, 0, w); }

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
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            buildUi();
            sUi = this;
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

    // ---------------- UI ----------------

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF121212);
        root.setPadding(dp(2), dp(6), dp(2), dp(6));

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(13);
        status.setPadding(dp(8), dp(2), dp(8), dp(2));
        status.setText("Shuru ho raha hai...");
        root.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // top bar
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.addView(barButton("Visible", C_ACTIVE, v -> makeVisible()), barLp());
        bar.addView(barButton("Laptop chuno", C_ACTIVE, v -> chooseHost()), barLp());
        modeBtn = barButton("Gboard", C_GREEN, v -> setMode(!gboardMode));
        bar.addView(modeBtn, barLp());
        root.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));

        // text box + paste / clear
        boxRow = new LinearLayout(this);
        boxRow.setOrientation(LinearLayout.HORIZONTAL);

        box = new EditText(this);
        box.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        box.setHint("Yahan likha nazar aayega. Paste bhi kar sakte ho");
        box.setHintTextColor(0xFF8E8E93);
        box.setTextColor(Color.WHITE);
        box.setTextSize(16);
        box.setGravity(Gravity.TOP | Gravity.START);
        box.setPadding(dp(10), dp(6), dp(10), dp(6));
        box.setShowSoftInputOnFocus(false);
        setBg(box, 0xFF1E1E22);
        box.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable e) {
                String now = e.toString();
                if (!suppress) syncToLaptop(prev, now);
                prev = now;
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
        side.addView(barButton("Paste", C_MOD, v -> pasteText()), sLp);
        side.addView(barButton("Clear", C_MOD, v -> clearBox()), sLp);
        boxRow.addView(side, new LinearLayout.LayoutParams(
                dp(72), LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams boxRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64));
        boxRowLp.topMargin = dp(4);
        root.addView(boxRow, boxRowLp);

        // shortcut strip (scrolls sideways)
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout shortRow = new LinearLayout(this);
        shortRow.setOrientation(LinearLayout.HORIZONTAL);
        addShortcut(shortRow, "Screenshot", C_GREEN, M_WIN, 0x46, 0);
        addShortcut(shortRow, "Snip", C_GREEN, M_WIN | M_SHIFT, 0x16, 0);
        addShortcut(shortRow, "Copy", C_MOD, M_CTRL, 0x06, 0);
        addShortcut(shortRow, "Paste", C_MOD, M_CTRL, 0x19, 0);
        addShortcut(shortRow, "Cut", C_MOD, M_CTRL, 0x1B, 0);
        addShortcut(shortRow, "Undo", C_MOD, M_CTRL, 0x1D, 0);
        addShortcut(shortRow, "All", C_MOD, M_CTRL, 0x04, 0);
        addShortcut(shortRow, "Save", C_MOD, M_CTRL, 0x16, 0);
        addShortcut(shortRow, "Alt+Tab", C_MOD, M_ALT, 0x2B, 0);
        addShortcut(shortRow, "Desktop", C_MOD, M_WIN, 0x07, 0);
        addShortcut(shortRow, "Lock", C_MOD, M_WIN, 0x0F, 0);
        addShortcut(shortRow, "Task Mgr", C_MOD, M_CTRL | M_SHIFT, 0x29, 0);
        addShortcut(shortRow, "Vol -", C_MOD, 0, 0, 0xEA);
        addShortcut(shortRow, "Vol +", C_MOD, 0, 0, 0xE9);
        addShortcut(shortRow, "Mute", C_MOD, 0, 0, 0xE2);
        addShortcut(shortRow, "Play/Pause", C_MOD, 0, 0, 0xCD);
        hs.addView(shortRow);
        LinearLayout.LayoutParams hsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(38));
        hsLp.topMargin = dp(4);
        root.addView(hs, hsLp);

        // touchpad + scroll strip
        padRow = new LinearLayout(this);
        padRow.setOrientation(LinearLayout.HORIZONTAL);
        View pad = new View(this);
        setBg(pad, 0xFF1C3A4D);
        pad.setOnTouchListener(padListener());
        LinearLayout.LayoutParams padLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        padLp.setMargins(dp(2), dp(2), dp(2), dp(2));
        padRow.addView(pad, padLp);
        TextView scrollStrip = new TextView(this);
        scrollStrip.setText("\u2195");
        scrollStrip.setTextColor(Color.WHITE);
        scrollStrip.setTextSize(22);
        scrollStrip.setGravity(Gravity.CENTER);
        setBg(scrollStrip, C_NORMAL);
        scrollStrip.setOnTouchListener(scrollListener());
        LinearLayout.LayoutParams stripLp = new LinearLayout.LayoutParams(
                dp(52), LinearLayout.LayoutParams.MATCH_PARENT);
        stripLp.setMargins(dp(2), dp(2), dp(2), dp(2));
        padRow.addView(scrollStrip, stripLp);
        LinearLayout.LayoutParams padRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.8f);
        padRowLp.topMargin = dp(4);
        root.addView(padRow, padRowLp);

        // click buttons
        clickRow = new LinearLayout(this);
        clickRow.setOrientation(LinearLayout.HORIZONTAL);
        clickRow.addView(clickButton("Left Click", 1), barLp());
        clickRow.addView(clickButton("Right Click", 2), barLp());
        LinearLayout.LayoutParams clickLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        clickLp.topMargin = dp(2);
        root.addView(clickRow, clickLp);

        // keyboard
        kb = new LinearLayout(this);
        kb.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams kbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 4.6f);
        kbLp.topMargin = dp(4);
        root.addView(kb, kbLp);

        List<K[]> rows = new ArrayList<>();
        rows.add(new K[]{k("Esc", 0x29), k("F1", 0x3A), k("F2", 0x3B), k("F3", 0x3C),
                k("F4", 0x3D), k("F5", 0x3E), k("F6", 0x3F), k("Del", 0x4C)});
        rows.add(new K[]{k("F7", 0x40), k("F8", 0x41), k("F9", 0x42), k("F10", 0x43),
                k("F11", 0x44), k("F12", 0x45), k("Home", 0x4A), k("End", 0x4D)});
        rows.add(new K[]{mw("Ctrl", M_CTRL, 1.3f), mw("Alt", M_ALT, 1.3f),
                mw("Win", M_WIN, 1.3f), k("Tab", 0x2B), k("Caps", 0x39),
                k("\u2190", 0x50), k("\u2193", 0x51), k("\u2191", 0x52), k("\u2192", 0x4F)});
        rows.add(new K[]{k("`", 0x35), k("-", 0x2D), k("=", 0x2E), k("[", 0x2F),
                k("]", 0x30), k(";", 0x33), k("'", 0x34), k("\\", 0x31), k("/", 0x38)});
        rows.add(digits());
        rows.add(letters("qwertyuiop"));
        rows.add(concat(new K[]{sp(0.5f)}, concat(letters("asdfghjkl"), sp(0.5f))));
        rows.add(concat(new K[]{mw("Shift", M_SHIFT, 1.5f)},
                concat(letters("zxcvbnm"), kw("\u232B", 0x2A, 1.5f))));
        rows.add(new K[]{k(",", 0x36), kw("Space", 0x2C, 5f), k(".", 0x37),
                kw("Enter", 0x28, 1.6f)});

        for (K[] row : rows) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            for (K key : row) {
                if (key.code < 0 && key.mod == 0) {
                    r.addView(new View(this), new LinearLayout.LayoutParams(0,
                            LinearLayout.LayoutParams.MATCH_PARENT, key.w));
                    continue;
                }
                Button b = makeKey(key);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.MATCH_PARENT, key.w);
                lp.setMargins(dp(1), dp(2), dp(1), dp(2));
                r.addView(b, lp);
            }
            kb.addView(r, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }

        setContentView(root);
    }

    private LinearLayout.LayoutParams barLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    private Button barButton(String text, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(4), 0, dp(4), 0);
        b.setStateListAnimator(null);
        setBg(b, color);
        b.setOnClickListener(l);
        return b;
    }

    private void addShortcut(LinearLayout row, String label, int color,
                             final int mod, final int code, final int usage) {
        Button b = barButton(label, color, v -> runShortcut(label, mod, code, usage));
        b.setTextSize(13);
        b.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT);
        lp.setMargins(dp(2), 0, dp(2), 0);
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

    private void setBg(View v, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(8));
        v.setBackground(g);
    }

    // Gboard mode hides only the on-screen keys; mouse, scroll and shortcuts stay
    private void setMode(boolean gboard) {
        gboardMode = gboard;
        kb.setVisibility(gboard ? View.GONE : View.VISIBLE);
        box.setShowSoftInputOnFocus(gboard);
        modeBtn.setText(gboard ? "Keys" : "Gboard");
        final InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (gboard) {
            box.requestFocus();
            box.post(() -> imm.showSoftInput(box, InputMethodManager.SHOW_IMPLICIT));
        } else {
            imm.hideSoftInputFromWindow(box.getWindowToken(), 0);
            box.clearFocus();
        }
    }

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
        suppress = true;
        box.setText("");
        suppress = false;
        prev = "";
    }

    // ---------------- keyboard keys ----------------

    private Button makeKey(final K key) {
        final Button b = new Button(this);
        b.setText(key.label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(key.label.length() > 2 ? 13 : 18);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(0, 0, 0, 0);
        b.setStateListAnimator(null);
        setBg(b, key.mod != 0 ? C_MOD : C_NORMAL);

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
                        setBg(b, C_PRESSED);
                        send(mods, key.code);
                        echo(key.code);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (key.mod != 0) {
                        mods ^= key.mod;
                        send(mods, 0);
                        refreshMods();
                    } else {
                        send(mods, 0);
                        setBg(b, C_NORMAL);
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
                        setBg(b, C_NORMAL);
                    }
                    return true;
                default:
                    return true;
            }
        });
        return b;
    }

    // show what is typed with the on-screen keys inside the text box (display only)
    private void echo(int code) {
        if ((mods & (M_CTRL | M_ALT | M_WIN)) != 0) return;
        boolean shift = (mods & M_SHIFT) != 0;
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
            setBg(modButtons.get(i), on ? C_ACTIVE : C_MOD);
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

    private final Runnable pumpRunnable = new Runnable() {
        @Override
        public void run() {
            int[] a = queue.poll();
            if (a == null) {
                pumping = false;
                return;
            }
            if (a[0] == -1) {
                sendConsumer(a[1]);
            } else {
                send(a[0], a[1]);
            }
            H.postDelayed(this, 6);
        }
    };

    private void pump() {
        if (pumping) return;
        pumping = true;
        H.post(pumpRunnable);
    }

    // ---------------- mouse ----------------

    private Button clickButton(String text, final int mask) {
        final Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setStateListAnimator(null);
        setBg(b, C_MOD);
        b.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    setBg(b, C_ACTIVE);
                    mouseButtons |= mask;
                    sendMouse(0, 0, 0);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    setBg(b, C_MOD);
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
                    accX += (e.getX() - lastX) * SENS;
                    accY += (e.getY() - lastY) * SENS;
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

    private void send(int modifiers, int code) {
        if (sHid == null || sHost == null) return;
        byte[] r = new byte[8];
        r[0] = (byte) modifiers;
        r[2] = (byte) code;
        sHid.sendReport(sHost, 1, r);
    }

    private void sendConsumer(int usage) {
        if (sHid == null || sHost == null) return;
        byte[] r = new byte[2];
        r[0] = (byte) (usage & 0xFF);
        r[1] = (byte) ((usage >> 8) & 0xFF);
        sHid.sendReport(sHost, 3, r);
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
        } else if (sRegistered) {
            status.setText("Tayyar hai. 'Laptop chuno' dabao");
        } else {
            status.setText("Bluetooth keyboard shuru ho raha hai...");
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
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sUi == this) sUi = null;
        H.removeCallbacks(pumpRunnable);
        queue.clear();
        pumping = false;
        // Bluetooth is NOT closed here, so the laptop stays connected
    }
}
