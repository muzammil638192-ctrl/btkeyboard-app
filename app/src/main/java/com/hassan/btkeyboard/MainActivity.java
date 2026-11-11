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
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import android.text.TextUtils;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

@SuppressLint("MissingPermission")
public class MainActivity extends Activity {

    private static final int MP = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WC = LinearLayout.LayoutParams.WRAP_CONTENT;

    // ---------------- language (English / Roman Urdu) ----------------
    private static boolean sEn = true;

    private static String T(String ur, String en) {
        return sEn ? en : ur;
    }

    private static void loadLang(Context c) {
        sEn = !"ur".equals(c.getSharedPreferences("ui", Context.MODE_PRIVATE)
                .getString("lang", "en"));
    }

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
    private boolean urduMode = false;

    private SharedPreferences ui() {
        return getSharedPreferences("ui", MODE_PRIVATE);
    }

    private void loadTheme() {
        SharedPreferences p = ui();
        theme = p.getInt("theme", 0) % THEMES.length;
        sens = p.getInt("sens", 18) / 10f;
        typeDelay = p.getInt("delay", 14);
        liveGboard = p.getBoolean("live", false);
        urduMode = p.getBoolean("urdu", false);
        loadLang(this);
        int[] t = THEMES[theme];
        cBg = t[0]; cKey = t[1]; cMod = t[2]; cPressed = t[3]; cAccent = t[4];
        cGreen = t[5]; cText = t[6]; cPad = t[7]; cBox = t[8]; cHint = t[9];
        cStroke = (cText & 0x00FFFFFF) | 0x24000000;
    }

    private void applyTheme(int idx) {
        ui().edit().putInt("theme", idx).apply();
        recreate();
    }

    private void setLang(String l) {
        ui().edit().putString("lang", l).apply();
        recreate();
    }

    private static final int M_CTRL = 0x01, M_SHIFT = 0x02, M_ALT = 0x04, M_WIN = 0x08;

    // ---- English (US layout): char <-> (modifier, HID keycode) ----
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

    // ---- Urdu: Windows "Urdu" keyboard layout (KBDURDU). Each US key position gives these
    // characters on a laptop whose keyboard language is Urdu: UB = normal, US = with Shift ----
    private static final int[] UC = {
            0x35, 0x1E, 0x1F, 0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x2D, 0x2E,
            0x14, 0x1A, 0x08, 0x15, 0x17, 0x1C, 0x18, 0x0C, 0x12, 0x13,
            0x2F, 0x30,
            0x04, 0x16, 0x07, 0x09, 0x0A, 0x0B, 0x0D, 0x0E, 0x0F,
            0x33, 0x34, 0x31,
            0x1D, 0x1B, 0x06, 0x19, 0x05, 0x11, 0x10,
            0x36, 0x37, 0x38};
    private static final String UB = "`1234567890-="
            + "\u0637\u0635\u06BE\u062F\u0679\u067E\u062A\u0628\u062C\u062D"
            + "]["
            + "\u0645\u0648\u0631\u0646\u0644\u06C1\u0627\u06A9\u06CC"
            + "\u061B'\\"
            + "\u0642\u0641\u06D2\u0633\u0634\u063A\u0639"
            + "\u060C\u06D4/";
    private static final String US = "~!@#$\u066A^\u06D6\u066D)(_+"
            + "\u0638\u0636\u0630\u0688\u062B\u0651\u06C3\u0640\u0686\u062E"
            + "}{"
            + "\u0698\u0632\u0691\u06BA\u06C2\u0621\u0622\u06AF\u064A"
            + ":\"|"
            + "\u200D\u200C\u06D3\u200E\u0624\u0626\u200F"
            + "><\u061F";

    private static final HashMap<Character, int[]> URDU = new HashMap<>();
    private static final HashMap<Integer, Character> UREV = new HashMap<>();

    static {
        for (int i = 0; i < UC.length; i++) {
            URDU.put(UB.charAt(i), new int[]{0, UC[i]});
            URDU.put(US.charAt(i), new int[]{M_SHIFT, UC[i]});
            UREV.put(UC[i], UB.charAt(i));
            UREV.put(0x100 | UC[i], US.charAt(i));
        }
        URDU.put(' ', new int[]{0, 0x2C});
        URDU.put('\n', new int[]{0, 0x28});
        URDU.put('\t', new int[]{0, 0x2B});
        // Arabic-Indic and Urdu digits are typed with the normal digit keys
        for (int i = 0; i < 10; i++) {
            int code = (i == 0) ? 0x27 : 0x1E + (i - 1);
            URDU.put((char) (0x0660 + i), new int[]{0, code});
            URDU.put((char) (0x06F0 + i), new int[]{0, code});
        }
    }

    private static int uIdx(int code) {
        for (int i = 0; i < UC.length; i++) {
            if (UC[i] == code) return i;
        }
        return -1;
    }

    private static String uLabel(char c) {
        switch (c) {
            case '\u200D': return "ZWJ";
            case '\u200C': return "ZWNJ";
            case '\u200E': return "LRM";
            case '\u200F': return "RLM";
            default: return String.valueOf(c);
        }
    }

    private HashMap<Character, int[]> map() {
        return urduMode ? URDU : CHARS;
    }

    private HashMap<Integer, Character> rev() {
        return urduMode ? UREV : REV;
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
    private static boolean sAwake = false;

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
                    resetHid();
                }
            };

    // opens the Bluetooth HID profile (works from the activity or from the background service)
    private static void startHid() {
        try {
            if (sApp == null || sProxyRequested) return;
            if (Build.VERSION.SDK_INT >= 31 && sApp.checkSelfPermission(
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            BluetoothManager bm = sApp.getSystemService(BluetoothManager.class);
            sAdapter = bm == null ? null : bm.getAdapter();
            if (sAdapter == null) {
                showStatus(T("Is phone mein Bluetooth nahi hai", "This phone has no Bluetooth"));
                return;
            }
            if (!sAdapter.isEnabled()) {
                showStatus(T("Bluetooth band hai", "Bluetooth is off"));
                return;
            }
            sProxyRequested = true;
            sAdapter.getProfileProxy(sApp, PROXY_L, BluetoothProfile.HID_DEVICE);
        } catch (Throwable t) {
            showStatus("Bluetooth error: " + t);
        }
    }

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

    // reconnect to the last laptop, or to an already paired computer (no new pairing needed)
    private static void reconnectLast() {
        try {
            if (sHid == null || sHost != null || sAdapter == null || sApp == null) return;
            String addr = sApp.getSharedPreferences("bt", Context.MODE_PRIVATE)
                    .getString("last", null);
            BluetoothDevice d = null;
            if (addr != null) {
                d = sAdapter.getRemoteDevice(addr);
            } else {
                for (BluetoothDevice b : sAdapter.getBondedDevices()) {
                    BluetoothClass bc = b.getBluetoothClass();
                    if (bc != null
                            && bc.getMajorDeviceClass() == BluetoothClass.Device.Major.COMPUTER) {
                        d = b;
                        break;
                    }
                }
            }
            if (d != null) sHid.connect(d);
        } catch (Throwable ignored) {
        }
    }

    private static void resetHid() {
        sHid = null;
        sHost = null;
        sRegistered = false;
        sProxyRequested = false;
        refreshUi();
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
        resetHid();
    }

    private static void refreshUi() {
        if (sUi != null) sUi.updateStatus();
    }

    private static void showStatus(String s) {
        if (sUi != null && sUi.status != null) sUi.status.setText(s);
    }

    private static void rawMouse(int buttons, int x, int y, int wheel) {
        if (sHid == null || sHost == null) return;
        byte[] r = new byte[4];
        r[0] = (byte) buttons;
        r[1] = (byte) x;
        r[2] = (byte) y;
        r[3] = (byte) wheel;
        sHid.sendReport(sHost, 2, r);
    }

    // "keep laptop awake": nudges the mouse by one pixel every 40 seconds
    private static final Runnable AWAKE = new Runnable() {
        @Override
        public void run() {
            if (!sAwake) return;
            rawMouse(0, 1, 0, 0);
            rawMouse(0, -1, 0, 0);
            H.postDelayed(this, 40000);
        }
    };

    private static void setAwake(boolean on) {
        sAwake = on;
        H.removeCallbacks(AWAKE);
        if (on) H.postDelayed(AWAKE, 1000);
    }

    // Keeps the app process alive so the laptop stays connected, and turns the HID profile
    // on again whenever Bluetooth is switched on
    public static class KeepAliveService extends Service {
        private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent i) {
                int st = i.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                if (st == BluetoothAdapter.STATE_ON) {
                    startHid();
                } else if (st == BluetoothAdapter.STATE_OFF) {
                    resetHid();
                }
            }
        };

        @Override
        public void onCreate() {
            super.onCreate();
            try {
                registerReceiver(btReceiver,
                        new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
            } catch (Throwable ignored) {
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            try {
                unregisterReceiver(btReceiver);
            } catch (Throwable ignored) {
            }
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            if (sApp == null) sApp = getApplicationContext();
            loadLang(this);
            if (intent != null && "STOP".equals(intent.getAction())) {
                setAwake(false);
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
                    T("Band karo", "Stop"), stopPi).build();
            Notification n = new Notification.Builder(this, "btkb")
                    .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                    .setContentTitle(T("BT Keyboard chal raha hai", "BT Keyboard is running"))
                    .setContentText(T("Laptop se connection bana rahega",
                            "Your laptop connection stays alive"))
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
    private boolean twoFinger = false;
    private float twoLastY, twoDist;
    private long twoDownTime;

    private TextView status;
    private View dot;
    private EditText box;
    private Button[] tabBtns;
    private Button sendBtn;
    private LinearLayout boxRow, kb, gboardPage, mousePage, mainRoot;
    private View settingsRoot, notesPage, toolsPage;
    private boolean settingsOpen = false;
    private int tab = 0;
    private boolean suppress = false;
    private boolean askedEnable = false;
    private String prev = "";

    private final Runnable syncRun = () -> {
        String cur = box.getText().toString();
        if (!cur.equals(prev)) {
            String was = prev;
            prev = cur;
            syncToLaptop(was, cur);
        }
    };

    // report queue: {mod, code} = key report, {-1, usage} = media key, {-2, ms} = wait
    private final ArrayDeque<int[]> queue = new ArrayDeque<>();
    private boolean pumping = false;

    private final List<Button> letterButtons = new ArrayList<>();
    private final List<Button> modButtons = new ArrayList<>();
    private final List<K> modKeys = new ArrayList<>();
    private final List<Button> urduButtons = new ArrayList<>();
    private final List<K> urduKeys = new ArrayList<>();
    private final List<Button> dynButtons = new ArrayList<>();
    private final List<K> dynKeys = new ArrayList<>();
    private final List<LinearLayout> letterViews = new ArrayList<>();
    private final List<LinearLayout> urduViews = new ArrayList<>();
    private final List<LinearLayout> symViews = new ArrayList<>();
    private boolean symbols = false;
    private Button symToggle, urduToggle;

    static class K {
        final String label;
        final int code;
        final int mod;
        final float w;
        final int fmod;      // modifier always sent with this key (Shift for "@", "#" ...)
        final boolean urdu;  // Urdu letter key

        K(String label, int code, int mod, float w, int fmod, boolean urdu) {
            this.label = label;
            this.code = code;
            this.mod = mod;
            this.w = w;
            this.fmod = fmod;
            this.urdu = urdu;
        }

        K(String label, int code, int mod, float w, int fmod) {
            this(label, code, mod, w, fmod, false);
        }

        K(String label, int code, int mod, float w) {
            this(label, code, mod, w, 0, false);
        }
    }

    private static K k(String l, int c) { return new K(l, c, 0, 1f); }
    private static K kw(String l, int c, float w) { return new K(l, c, 0, w); }
    private static K mw(String l, int mod, float w) { return new K(l, 0, mod, w); }
    private static K sp(float w) { return new K("", -1, 0, w); }
    private static K ks(String l, int c) { return new K(l, c, 0, 1f, M_SHIFT); }
    private static K kt(float w) { return new K("?123", -2, 0, w); }
    private static K ku(float w) { return new K("\u0627\u0631\u062F\u0648", -3, 0, w); }

    private static K uk(int code) {
        int i = uIdx(code);
        return new K(uLabel(UB.charAt(i)), code, 0, 1f, 0, true);
    }

    private static K[] urduRow(int... codes) {
        K[] r = new K[codes.length];
        for (int i = 0; i < codes.length; i++) r[i] = uk(codes[i]);
        return r;
    }

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

    private static boolean isPrintableCode(int code) {
        return (code >= 0x1E && code <= 0x27) || (code >= 0x2D && code <= 0x38);
    }

    // ---------------- small helpers ----------------

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private LinearLayout.LayoutParams lpw(int w, int h, float wt) {
        return new LinearLayout.LayoutParams(w, h, wt);
    }

    private LinearLayout.LayoutParams lpm(int w, int h, float wt, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, wt);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private FrameLayout.LayoutParams flp() {
        return new FrameLayout.LayoutParams(MP, MP);
    }

    private LinearLayout hbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout vbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

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
            if (savedInstanceState != null && savedInstanceState.getBoolean("tut", false)) {
                showTutorial(savedInstanceState.getInt("tutPage", 0));
            } else if (!ui().getBoolean("tutDone", false)) {
                showTutorial(0);
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
        out.putBoolean("tut", tutOpen);
        out.putInt("tutPage", tutPage);
    }

    @Override
    public void onBackPressed() {
        if (tutOpen) {
            hideTutorial(true);
        } else if (settingsOpen) {
            closeSettings();
        } else if (tab == 3 && editingId != -1) {
            closeNote();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (editingId != -1 && noteEdit != null) saveEditingNote();
    }

    private boolean hasBtPermission() {
        return Build.VERSION.SDK_INT < 31
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void showCrash(String trace) {
        LinearLayout l = vbox();
        l.setBackgroundColor(0xFF121212);
        l.setPadding(dp(12), dp(24), dp(12), dp(12));
        TextView head = text(T("App mein error aaya. Is screen ka screenshot Claude ko bhejo:",
                "The app hit an error. Send a screenshot of this screen to Claude:"),
                16, Color.WHITE);
        l.addView(head);
        TextView body = text(trace.length() > 3500 ? trace.substring(0, 3500) : trace,
                11, 0xFFFF8A80);
        body.setTextIsSelectable(true);
        ScrollView sv = new ScrollView(this);
        sv.addView(body);
        l.addView(sv, lpw(MP, 0, 1f));
        Button retry = new Button(this);
        retry.setText(T("Error saaf karo aur dobara try karo", "Clear error and try again"));
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
            status.setText(T("Bluetooth (Nearby devices) permission dena zaroori hai",
                    "Please allow the Nearby devices permission"));
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 77 && status != null) {
            if (res == RESULT_OK) {
                startHid();
            } else {
                status.setText(T("Bluetooth band hai. Settings > Connect dabao",
                        "Bluetooth is off. Tap Settings > Connect"));
            }
        }
    }

    // ---------------- main UI ----------------

    private void buildUi() {
        mainRoot = vbox();
        setPageBg(mainRoot);
        mainRoot.setPadding(dp(6), dp(8), dp(6), dp(8));

        // header: status dot + status text + settings icon
        LinearLayout header = hbox();
        header.setGravity(Gravity.CENTER_VERTICAL);
        dot = new View(this);
        setDotColor(0xFF8E8E93);
        header.addView(dot, lpm(dp(10), dp(10), 0, 8, 0, 8, 0));
        status = text(T("Shuru ho raha hai...", "Starting..."), 14, cText);
        status.setTypeface(medium());
        status.setMaxLines(2);
        header.addView(status, lpw(0, WC, 1f));
        header.addView(iconButton("\u2699", v -> openSettings()), lp(dp(44), dp(44)));
        mainRoot.addView(header, lp(MP, dp(50)));

        // tabs (segmented control)
        LinearLayout tabs = hbox();
        tabs.setPadding(dp(3), dp(3), dp(3), dp(3));
        setCardBg(tabs, cBox, 14);
        String[] tabNames = {"Keys", "Gboard", "Mouse", "Notes", "Tools"};
        tabBtns = new Button[tabNames.length];
        for (int i = 0; i < tabNames.length; i++) {
            final int idx = i;
            tabBtns[i] = barButton(tabNames[i], cMod, v -> setTab(idx));
            tabBtns[i].setTextSize(13);
            tabs.addView(tabBtns[i], barLp());
        }
        mainRoot.addView(tabs, lpm(MP, dp(46), 0, 0, 4, 0, 0));

        // text box + paste / clear (Keys and Gboard tabs)
        boxRow = hbox();
        box = new EditText(this);
        box.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
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
        boxRow.addView(box, lpm(0, MP, 1f, 2, 2, 2, 2));
        LinearLayout side = vbox();
        side.addView(barButton(T("Paste", "Paste"), cMod, v -> pasteText()), lpm(MP, 0, 1f, 0, 2, 0, 2));
        side.addView(barButton(T("Clear", "Clear"), cMod, v -> clearBox()), lpm(MP, 0, 1f, 0, 2, 0, 2));
        boxRow.addView(side, lp(dp(72), MP));
        mainRoot.addView(boxRow, lpm(MP, dp(66), 0, 0, 4, 0, 0));

        FrameLayout content = new FrameLayout(this);
        mainRoot.addView(content, lpm(MP, 0, 1f, 0, 4, 0, 0));

        // ---- page 0: on-screen keyboard ----
        kb = vbox();
        content.addView(kb, flp());
        buildKeyboardRows();

        // ---- page 1: Gboard (touchpad + Send + Enter) ----
        gboardPage = vbox();
        gboardPage.addView(buildPadRow(), lpw(MP, 0, 1f));
        gboardPage.addView(buildClickRow(true), lp(MP, dp(58)));
        content.addView(gboardPage, flp());

        // ---- page 2: mouse + shortcuts ----
        mousePage = vbox();
        mousePage.addView(buildPadRow(), lpw(MP, 0, 1f));
        mousePage.addView(buildClickRow(false), lp(MP, dp(52)));
        LinearLayout grid = vbox();
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
        mousePage.addView(grid, lpm(MP, 0, 1.3f, 0, 4, 0, 0));
        content.addView(mousePage, flp());

        // ---- page 3: notes, page 4: tools ----
        notesPage = buildNotesPage();
        content.addView(notesPage, flp());
        toolsPage = buildToolsPage();
        content.addView(toolsPage, flp());

        // settings + tutorial screens sit on top of the main screen
        settingsRoot = buildSettings();
        settingsRoot.setVisibility(View.GONE);
        tutRoot = vbox();
        setPageBg(tutRoot);
        tutRoot.setPadding(dp(16), dp(14), dp(16), dp(14));
        tutRoot.setClickable(true);   // swallow touches so the main screen under it is not pressed
        tutRoot.setVisibility(View.GONE);
        FrameLayout rootFrame = new FrameLayout(this);
        rootFrame.addView(mainRoot, flp());
        rootFrame.addView(settingsRoot, flp());
        rootFrame.addView(tutRoot, flp());
        setContentView(rootFrame);
        loadNotes();
        refreshNotesList();
        setTab(0);
    }

    private void buildKeyboardRows() {
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

        // English letters
        K[][] letterRows = {
                letters("qwertyuiop"),
                concat(new K[]{sp(0.5f)}, concat(letters("asdfghjkl"), sp(0.5f))),
                concat(new K[]{mw("Shift", M_SHIFT, 1.5f)},
                        concat(letters("zxcvbnm"), kw("\u232B", 0x2A, 1.5f)))
        };
        for (K[] row : letterRows) letterViews.add(addKbRow(row));

        // Urdu letters (same keys, laptop must be on the Urdu keyboard)
        K[][] urduRows = {
                urduRow(0x14, 0x1A, 0x08, 0x15, 0x17, 0x1C, 0x18, 0x0C, 0x12, 0x13),
                concat(new K[]{sp(0.5f)},
                        concat(urduRow(0x04, 0x16, 0x07, 0x09, 0x0A, 0x0B, 0x0D, 0x0E, 0x0F),
                                sp(0.5f))),
                concat(new K[]{mw("Shift", M_SHIFT, 1.5f)},
                        concat(urduRow(0x1D, 0x1B, 0x06, 0x19, 0x05, 0x11, 0x10),
                                kw("\u232B", 0x2A, 1.5f)))
        };
        for (K[] row : urduRows) {
            LinearLayout v = addKbRow(row);
            v.setVisibility(View.GONE);
            urduViews.add(v);
        }

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

        addKbRow(new K[]{kt(1.2f), ku(1.2f), k(",", 0x36), kw("Space", 0x2C, 3.4f),
                k(".", 0x37), kw("Enter", 0x28, 1.4f)});
        updateLayers();
    }

    private LinearLayout addKbRow(K[] row) {
        LinearLayout r = hbox();
        for (K key : row) {
            if (key.code == -1) {
                r.addView(new View(this), lpw(0, MP, key.w));
                continue;
            }
            r.addView(makeKey(key), lpm(0, MP, key.w, 2, 2, 2, 2));
        }
        kb.addView(r, lpw(MP, 0, 1f));
        return r;
    }

    // shows the right letter rows (English / Urdu / symbols) and refreshes key labels
    private void updateLayers() {
        for (LinearLayout r : letterViews) {
            r.setVisibility(!symbols && !urduMode ? View.VISIBLE : View.GONE);
        }
        for (LinearLayout r : urduViews) {
            r.setVisibility(!symbols && urduMode ? View.VISIBLE : View.GONE);
        }
        for (LinearLayout r : symViews) r.setVisibility(symbols ? View.VISIBLE : View.GONE);
        if (symToggle != null) symToggle.setText(symbols ? "ABC" : "?123");
        if (urduToggle != null) urduToggle.setText(urduMode ? "EN" : "\u0627\u0631\u062F\u0648");
        refreshLabels();
    }

    private void toggleSymbols() {
        symbols = !symbols;
        updateLayers();
    }

    private void setUrduMode(boolean on) {
        urduMode = on;
        symbols = false;
        ui().edit().putBoolean("urdu", on).apply();
        updateLayers();
        if (on) {
            Toast.makeText(this, T("Laptop par bhi Urdu keyboard chuno (Mouse tab > Lang)",
                    "Also switch the laptop to Urdu (Mouse tab > Lang)"), Toast.LENGTH_LONG).show();
        }
    }

    private Button toggleKey(final boolean isUrdu) {
        final Button b = new Button(this);
        b.setText(isUrdu ? "\u0627\u0631\u062F\u0648" : "?123");
        b.setAllCaps(false);
        b.setTextColor(cText);
        b.setTypeface(medium());
        b.setTextSize(isUrdu ? 15 : 13);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(0, 0, 0, 0);
        b.setStateListAnimator(null);
        setKeyBg(b, cMod);
        b.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (isUrdu) {
                setUrduMode(!urduMode);
            } else {
                toggleSymbols();
            }
        });
        if (isUrdu) {
            urduToggle = b;
        } else {
            symToggle = b;
        }
        return b;
    }

    private LinearLayout buildPadRow() {
        LinearLayout row = hbox();
        TextView pad = text("TOUCHPAD", 11, cHint);
        pad.setLetterSpacing(0.25f);
        pad.setGravity(Gravity.CENTER);
        setCardBg(pad, cPad, 16);
        pad.setOnTouchListener(padListener());
        row.addView(pad, lpm(0, MP, 1f, 2, 2, 2, 2));
        TextView scrollStrip = text("\u2195", 22, cText);
        scrollStrip.setGravity(Gravity.CENTER);
        setKeyBg(scrollStrip, cKey);
        scrollStrip.setOnTouchListener(scrollListener());
        row.addView(scrollStrip, lpm(dp(52), MP, 0, 2, 2, 2, 2));
        return row;
    }

    private LinearLayout buildClickRow(boolean withEnter) {
        LinearLayout row = hbox();
        row.addView(clickButton(T("Left Click", "Left Click"), 1), barLp());
        row.addView(clickButton(T("Right Click", "Right Click"), 2), barLp());
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

    private void combo(int mod, int code) {
        for (int i = 0; i < 5; i++) queue.add(new int[]{mod, code});
        queue.add(new int[]{0, 0});
        pump();
    }

    private LinearLayout shortRow(LinearLayout parent) {
        LinearLayout r = hbox();
        parent.addView(r, lpw(MP, 0, 1f));
        return r;
    }

    private LinearLayout.LayoutParams barLp() {
        return barLp(1f);
    }

    private LinearLayout.LayoutParams barLp(float w) {
        return lpm(0, MP, w, 2, 0, 2, 0);
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
        row.addView(b, lpm(0, MP, 1f, 2, 2, 2, 2));
    }

    private void runShortcut(String label, int mod, int code, int usage) {
        if (usage != 0) {
            for (int i = 0; i < 3; i++) queue.add(new int[]{-1, usage});
            queue.add(new int[]{-1, 0});
            pump();
        } else {
            combo(mod, code);
        }
        if ("Screenshot".equals(label)) {
            Toast.makeText(this, T("Screenshot gaya. Laptop: Pictures > Screenshots",
                    "Screenshot taken. Laptop: Pictures > Screenshots"), Toast.LENGTH_SHORT).show();
        }
    }

    // tabs: 0 Keys, 1 Gboard (+touchpad), 2 Mouse + shortcuts, 3 Notes, 4 Tools
    private void setTab(int t) {
        if (tab == 3 && t != 3 && editingId != -1) closeNote();
        tab = t;
        kb.setVisibility(t == 0 ? View.VISIBLE : View.GONE);
        gboardPage.setVisibility(t == 1 ? View.VISIBLE : View.GONE);
        mousePage.setVisibility(t == 2 ? View.VISIBLE : View.GONE);
        notesPage.setVisibility(t == 3 ? View.VISIBLE : View.GONE);
        toolsPage.setVisibility(t == 4 ? View.VISIBLE : View.GONE);
        boxRow.setVisibility(t <= 1 ? View.VISIBLE : View.GONE);
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
        box.setHint(manual ? T("Yahan likho, phir Send dabao", "Write here, then tap Send")
                : T("Yahan likha nazar aayega. Paste bhi kar sakte ho",
                "Your text shows here. You can paste too"));
    }

    private void sendBox() {
        String text = box.getText().toString();
        if (text.isEmpty()) {
            Toast.makeText(this, T("Box khali hai", "The box is empty"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (sHost == null) {
            Toast.makeText(this, T("Pehle laptop connect karo", "Connect to your laptop first"),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        queueText(text);
        H.removeCallbacks(syncRun);
        suppress = true;
        box.setText("");
        suppress = false;
        prev = "";
    }

    // ---------------- notes (notepad + snippets) ----------------

    static class Note {
        long id;
        String text;
        long ts;
    }

    private final List<Note> notes = new ArrayList<>();
    private LinearLayout notesListBox, notesListView, noteEditorView;
    private EditText noteSearch, noteEdit;
    private long editingId = -1;

    private void loadNotes() {
        notes.clear();
        try {
            JSONArray a = new JSONArray(
                    getSharedPreferences("notes", MODE_PRIVATE).getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Note n = new Note();
                n.id = o.getLong("id");
                n.text = o.getString("text");
                n.ts = o.optLong("ts", n.id);
                notes.add(n);
            }
        } catch (Throwable ignored) {
        }
    }

    private void saveNotes() {
        try {
            JSONArray a = new JSONArray();
            for (Note n : notes) {
                JSONObject o = new JSONObject();
                o.put("id", n.id);
                o.put("text", n.text);
                o.put("ts", n.ts);
                a.put(o);
            }
            getSharedPreferences("notes", MODE_PRIVATE).edit()
                    .putString("list", a.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    private Note findNote(long id) {
        for (Note n : notes) {
            if (n.id == id) return n;
        }
        return null;
    }

    private EditText styledEdit(String hint, int size) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(cHint);
        e.setTextColor(cText);
        e.setTextSize(size);
        e.setPadding(dp(12), dp(8), dp(12), dp(8));
        setCardBg(e, cBox, 14);
        return e;
    }

    private LinearLayout buildNotesPage() {
        LinearLayout page = vbox();

        notesListView = vbox();
        LinearLayout top = hbox();
        noteSearch = styledEdit(T("Note dhoondo...", "Search notes..."), 15);
        noteSearch.setSingleLine(true);
        noteSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable e) {
                refreshNotesList();
            }
        });
        top.addView(noteSearch, lpm(0, MP, 1f, 2, 0, 4, 0));
        top.addView(barButton(T("+ Naya", "+ New"), cGreen, v -> newNote()), lp(dp(84), MP));
        notesListView.addView(top, lp(MP, dp(46)));
        ScrollView sv = new ScrollView(this);
        notesListBox = vbox();
        notesListBox.setPadding(0, dp(8), 0, dp(8));
        sv.addView(notesListBox);
        notesListView.addView(sv, lpw(MP, 0, 1f));
        page.addView(notesListView, lp(MP, MP));

        noteEditorView = vbox();
        LinearLayout bar = hbox();
        bar.addView(iconButton("\u2190", v -> closeNote()), lpm(dp(44), MP, 0, 2, 0, 2, 0));
        bar.addView(barButton(T("Laptop par type", "Type on laptop"), cGreen,
                v -> typeText(noteEdit.getText().toString())), barLp(1.7f));
        bar.addView(barButton("Copy", cMod, v -> copyNote()), barLp());
        bar.addView(barButton("Delete", 0xFFE5484D, v -> deleteNote()), barLp());
        noteEditorView.addView(bar, lp(MP, dp(46)));
        noteEdit = styledEdit(T("Yahan note likho...", "Write your note here..."), 16);
        noteEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        noteEdit.setGravity(Gravity.TOP | Gravity.START);
        noteEditorView.addView(noteEdit, lpm(MP, 0, 1f, 0, 6, 0, 0));
        noteEditorView.setVisibility(View.GONE);
        page.addView(noteEditorView, lp(MP, MP));
        return page;
    }

    private void refreshNotesList() {
        if (notesListBox == null) return;
        notesListBox.removeAllViews();
        String q = noteSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (int i = notes.size() - 1; i >= 0; i--) {
            Note n = notes.get(i);
            if (!q.isEmpty() && !n.text.toLowerCase(Locale.ROOT).contains(q)) continue;
            notesListBox.addView(noteCard(n), lpm(MP, WC, 0, 0, 0, 0, 8));
            shown++;
        }
        if (shown == 0) {
            TextView e = text(T("Koi note nahi. '+ Naya' dabao. Note ko 'Type' se laptop par likh sakte ho.",
                    "No notes yet. Tap '+ New'. Use 'Type' to write a note on the laptop."),
                    14, cHint);
            e.setPadding(dp(8), dp(24), dp(8), 0);
            e.setGravity(Gravity.CENTER);
            notesListBox.addView(e);
        }
    }

    private View noteCard(final Note n) {
        LinearLayout card = hbox();
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(10), dp(10), dp(10));
        setCardBg(card, cBox, 14);
        LinearLayout texts = vbox();
        String[] parts = n.text.trim().split("\n", 2);
        TextView title = text(parts[0].isEmpty() ? T("(khali note)", "(empty note)") : parts[0],
                15, cText);
        title.setTypeface(medium());
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title);
        if (parts.length > 1 && !parts[1].trim().isEmpty()) {
            TextView body = text(parts[1].trim(), 12, cHint);
            body.setMaxLines(2);
            body.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(body);
        }
        card.addView(texts, lpw(0, WC, 1f));
        card.addView(barButton("Type", cGreen, v -> typeText(n.text)),
                lpm(dp(64), dp(40), 0, 8, 0, 0, 0));
        card.setOnClickListener(v -> openNote(n));
        return card;
    }

    private void newNote() {
        Note n = new Note();
        n.id = System.currentTimeMillis();
        n.text = "";
        n.ts = n.id;
        notes.add(n);
        openNote(n);
    }

    private void openNote(Note n) {
        editingId = n.id;
        noteEdit.setText(n.text);
        noteEdit.setSelection(noteEdit.getText().length());
        notesListView.setVisibility(View.GONE);
        noteEditorView.setVisibility(View.VISIBLE);
        noteEdit.requestFocus();
        final InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        noteEdit.post(() -> imm.showSoftInput(noteEdit, InputMethodManager.SHOW_IMPLICIT));
    }

    private void saveEditingNote() {
        Note n = findNote(editingId);
        if (n == null) return;
        String txt = noteEdit.getText().toString();
        if (!txt.trim().isEmpty()) {
            n.text = txt;
            n.ts = System.currentTimeMillis();
        }
        saveNotes();
    }

    private void leaveEditor() {
        editingId = -1;
        noteEditorView.setVisibility(View.GONE);
        notesListView.setVisibility(View.VISIBLE);
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(noteEdit.getWindowToken(), 0);
        refreshNotesList();
    }

    private void closeNote() {
        if (editingId == -1) return;
        Note n = findNote(editingId);
        if (n != null && noteEdit.getText().toString().trim().isEmpty()) {
            notes.remove(n);
            saveNotes();
        } else {
            saveEditingNote();
        }
        leaveEditor();
    }

    private void deleteNote() {
        new AlertDialog.Builder(this)
                .setTitle(T("Note delete karein?", "Delete this note?"))
                .setPositiveButton(T("Haan", "Yes"), (d, w) -> {
                    Note n = findNote(editingId);
                    if (n != null) notes.remove(n);
                    saveNotes();
                    leaveEditor();
                })
                .setNegativeButton(T("Nahi", "No"), null)
                .show();
    }

    private void copyNote() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("note", noteEdit.getText().toString()));
            Toast.makeText(this, T("Copy ho gaya", "Copied"), Toast.LENGTH_SHORT).show();
        }
    }

    private void typeText(String t) {
        if (t == null || t.isEmpty()) {
            Toast.makeText(this, T("Note khali hai", "The note is empty"),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (sHost == null) {
            Toast.makeText(this, T("Pehle laptop connect karo", "Connect to your laptop first"),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        queueText(t);
    }

    // ---------------- tools ----------------

    private LinearLayout toolRow(LinearLayout parent) {
        LinearLayout r = hbox();
        parent.addView(r, lpm(MP, dp(48), 0, 0, 0, 0, 6));
        return r;
    }

    private void addTool(LinearLayout row, String label, int color, final Runnable action) {
        Button b = barButton(label, color, v -> action.run());
        b.setTextSize(12);
        row.addView(b, lpm(0, MP, 1f, 2, 0, 2, 0));
    }

    private void runCommand(String cmd) {
        if (sHost == null) {
            Toast.makeText(this, T("Pehle laptop connect karo", "Connect to your laptop first"),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (urduMode) {
            Toast.makeText(this, T("Pehle laptop ko English keyboard par karo aur Urdu mode band karo",
                    "Switch the laptop to English and turn Urdu mode off first"),
                    Toast.LENGTH_LONG).show();
            return;
        }
        for (int i = 0; i < 5; i++) queue.add(new int[]{M_WIN, 0x15});
        queue.add(new int[]{0, 0});
        queue.add(new int[]{-2, 700});
        for (int i = 0; i < cmd.length(); i++) {
            int[] kc = CHARS.get(cmd.charAt(i));
            if (kc == null) continue;
            queue.add(new int[]{kc[0], kc[1]});
            queue.add(new int[]{0, 0});
        }
        queue.add(new int[]{0, 0x28});
        queue.add(new int[]{0, 0});
        pump();
    }

    private View buildToolsPage() {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = vbox();
        col.setPadding(dp(2), 0, dp(2), dp(8));
        sv.addView(col);

        LinearLayout c0 = card();
        Switch awake = new Switch(this);
        awake.setChecked(sAwake);
        awake.setOnCheckedChangeListener((b, on) -> setAwake(on));
        c0.addView(settingRow(T("Laptop ko jagata rakho", "Keep laptop awake"),
                T("Har 40 second mein mouse zara sa hilta hai",
                        "Nudges the mouse every 40 seconds"), awake));
        col.addView(c0, lpm(MP, WC, 0, 0, 6, 0, 0));

        col.addView(sectionLabel("PRESENTATION"));
        LinearLayout p1 = toolRow(col);
        addTool(p1, "\u25C0 Prev", cMod, () -> combo(0, 0x50));
        addTool(p1, "Next \u25B6", cAccent, () -> combo(0, 0x4F));
        addTool(p1, "Start F5", cGreen, () -> combo(0, 0x3E));
        addTool(p1, "End Esc", cMod, () -> combo(0, 0x29));
        LinearLayout p2 = toolRow(col);
        addTool(p2, "From slide", cMod, () -> combo(M_SHIFT, 0x3E));
        addTool(p2, "Black", cMod, () -> combo(0, 0x05));
        addTool(p2, "White", cMod, () -> combo(0, 0x1A));
        addTool(p2, "Laser", cMod, () -> combo(M_CTRL, 0x0F));

        col.addView(sectionLabel("WINDOWS"));
        LinearLayout w1 = toolRow(col);
        addTool(w1, "Maximize", cMod, () -> combo(M_WIN, 0x52));
        addTool(w1, "Minimize", cMod, () -> combo(M_WIN, 0x51));
        addTool(w1, "Snap \u25C0", cMod, () -> combo(M_WIN, 0x50));
        addTool(w1, "Snap \u25B6", cMod, () -> combo(M_WIN, 0x4F));
        LinearLayout w2 = toolRow(col);
        addTool(w2, "Task view", cMod, () -> combo(M_WIN, 0x2B));
        addTool(w2, "New desk", cMod, () -> combo(M_WIN | M_CTRL, 0x07));
        addTool(w2, "Desk \u25C0", cMod, () -> combo(M_WIN | M_CTRL, 0x50));
        addTool(w2, "Desk \u25B6", cMod, () -> combo(M_WIN | M_CTRL, 0x4F));

        col.addView(sectionLabel("QUICK LAUNCH"));
        LinearLayout a1 = toolRow(col);
        addTool(a1, "Explorer", cMod, () -> combo(M_WIN, 0x08));
        addTool(a1, "Settings", cMod, () -> combo(M_WIN, 0x0C));
        addTool(a1, "Search", cMod, () -> combo(M_WIN, 0x16));
        addTool(a1, "Emoji", cMod, () -> combo(M_WIN, 0x37));
        LinearLayout a2 = toolRow(col);
        addTool(a2, "Clipboard", cMod, () -> combo(M_WIN, 0x19));
        addTool(a2, "Run", cMod, () -> combo(M_WIN, 0x15));
        addTool(a2, "Notepad", cAccent, () -> runCommand("notepad"));
        addTool(a2, "Calculator", cAccent, () -> runCommand("calc"));
        LinearLayout a3 = toolRow(col);
        addTool(a3, "Chrome", cAccent, () -> runCommand("chrome"));
        addTool(a3, "Edge", cAccent, () -> runCommand("msedge"));
        addTool(a3, "CMD", cAccent, () -> runCommand("cmd"));
        addTool(a3, "Paint", cAccent, () -> runCommand("mspaint"));
        LinearLayout a4 = toolRow(col);
        addTool(a4, "Word", cAccent, () -> runCommand("winword"));
        addTool(a4, "Excel", cAccent, () -> runCommand("excel"));
        addTool(a4, "PowerPoint", cAccent, () -> runCommand("powerpnt"));
        addTool(a4, "Task Mgr", cMod, () -> combo(M_CTRL | M_SHIFT, 0x29));

        col.addView(sectionLabel("BROWSER"));
        LinearLayout b1 = toolRow(col);
        addTool(b1, "Back", cMod, () -> combo(M_ALT, 0x50));
        addTool(b1, "Forward", cMod, () -> combo(M_ALT, 0x4F));
        addTool(b1, "Reload", cMod, () -> combo(0, 0x3E));
        addTool(b1, "Address", cMod, () -> combo(M_CTRL, 0x0F));
        LinearLayout b2 = toolRow(col);
        addTool(b2, "New tab", cMod, () -> combo(M_CTRL, 0x17));
        addTool(b2, "Close tab", cMod, () -> combo(M_CTRL, 0x1A));
        addTool(b2, "Next tab", cMod, () -> combo(M_CTRL, 0x2B));
        addTool(b2, "Reopen", cMod, () -> combo(M_CTRL | M_SHIFT, 0x17));
        LinearLayout b3 = toolRow(col);
        addTool(b3, "Full screen", cMod, () -> combo(0, 0x44));
        addTool(b3, "Zoom +", cMod, () -> combo(M_CTRL, 0x2E));
        addTool(b3, "Zoom -", cMod, () -> combo(M_CTRL, 0x2D));
        addTool(b3, "Find", cMod, () -> combo(M_CTRL, 0x09));
        return sv;
    }

    // ---------------- first-run tutorial ----------------

    private LinearLayout tutRoot;
    private boolean tutOpen = false;
    private int tutPage = 0;

    private String[][] tutPages() {
        return new String[][]{
                {"\u2328", T("BT Keyboard mein khush aamdeed", "Welcome to BT Keyboard"),
                        T("Apne phone ko laptop ka wireless keyboard aur mouse banao. "
                                        + "Internet ya laptop par koi software nahi chahiye.",
                                "Turn your phone into a wireless keyboard and mouse for your "
                                        + "laptop. No internet and no software on the laptop.")},
                {"\u2714", T("Permissions allow karo", "Allow permissions"),
                        T("Jab poochha jaye to Nearby devices aur Notifications allow karo. "
                                        + "App Bluetooth bhi khud on kar sakti hai.",
                                "Allow Nearby devices and Notifications when asked. "
                                        + "The app can also turn Bluetooth on for you.")},
                {"\u2699", T("Laptop se pair karo (sirf ek dafa)", "Pair with your laptop (once)"),
                        T("Gear icon dabao, phir Visible. Windows mein: Settings > Bluetooth & "
                                        + "devices > Add device > Bluetooth, aur apna phone chuno.",
                                "Tap the gear icon, then Visible. On Windows: Settings > Bluetooth "
                                        + "& devices > Add device > Bluetooth, and pick your phone.")},
                {"\u26A1", T("Connect karo", "Connect"),
                        T("Gear icon > Connect > apni laptop chuno. Hara dot matlab connected. "
                                        + "Agli dafa app khud connect ho jaye gi.",
                                "Gear icon > Connect > choose your laptop. A green dot means "
                                        + "connected. Next time the app reconnects by itself.")},
                {"\u270E", T("Kuch bhi type karo", "Type anything"),
                        T("Keys: app ka keyboard. Gboard: phone ke keyboard se likho, phir Send "
                                        + "dabao. Mouse: touchpad, do ungli se scroll aur shortcuts.",
                                "Keys: the app keyboard. Gboard: write with your phone keyboard, "
                                        + "then tap Send. Mouse: touchpad, two-finger scroll and "
                                        + "shortcuts.")},
                {"\u2605", T("Notes aur Tools", "Notes and Tools"),
                        T("Notes phone mein save hote hain. Type dabao to laptop par likh jate "
                                        + "hain. Tools mein presentation remote aur quick launch hai.",
                                "Notes are saved on your phone. Tap Type to write one on the "
                                        + "laptop. Tools has a presentation remote and quick launch.")},
                {"\u0627", T("Urdu typing", "Urdu typing"),
                        T("Windows mein Urdu keyboard add karo. Phir app mein Urdu key dabao aur "
                                        + "laptop ki language Lang se badlo.",
                                "Add the Urdu keyboard in Windows. Then tap the Urdu key in the app "
                                        + "and switch the laptop language with Lang.")},
                {"\u263A", T("Aap tayyar hain!", "You are ready!"),
                        T("Tip: Settings > Band karo sab band kar deta hai. Battery ko background "
                                        + "mein chalne do taake connection bana rahe.",
                                "Tip: Settings > Stop turns everything off. Allow background battery "
                                        + "use so the connection stays alive.")}
        };
    }

    private void showTutorial(int page) {
        tutOpen = true;
        tutPage = page;
        renderTutorial();
        tutRoot.setVisibility(View.VISIBLE);
    }

    private void hideTutorial(boolean done) {
        tutOpen = false;
        tutRoot.setVisibility(View.GONE);
        if (done) ui().edit().putBoolean("tutDone", true).apply();
    }

    private void renderTutorial() {
        tutRoot.removeAllViews();
        final String[][] pages = tutPages();
        final int n = pages.length;
        if (tutPage < 0) tutPage = 0;
        if (tutPage > n - 1) tutPage = n - 1;
        String[] p = pages[tutPage];

        LinearLayout top = hbox();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text((tutPage + 1) + " / " + n, 14, cHint), lpw(0, WC, 1f));
        if (tutPage < n - 1) {
            top.addView(barButton(T("Skip karo", "Skip"), cMod, v -> hideTutorial(true)),
                    lp(dp(96), dp(40)));
        }
        tutRoot.addView(top, lp(MP, dp(44)));

        ScrollView sv = new ScrollView(this);
        LinearLayout col = vbox();
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(4), dp(24), dp(4), dp(8));
        TextView icon = text(p[0], 64, cAccent);
        icon.setGravity(Gravity.CENTER);
        col.addView(icon, lp(MP, WC));
        TextView title = text(p[1], 24, cText);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        col.addView(title, lpm(MP, WC, 0, 0, 14, 0, 10));
        TextView body = text(p[2], 16, cHint);
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(0, 1.2f);
        col.addView(body, lp(MP, WC));
        if (tutPage == 0) {
            LinearLayout langRow = hbox();
            langRow.addView(barButton("English", sEn ? cAccent : cMod, v -> setLang("en")),
                    lpm(0, dp(46), 1f, 4, 0, 4, 0));
            langRow.addView(barButton("Roman Urdu", sEn ? cMod : cAccent, v -> setLang("ur")),
                    lpm(0, dp(46), 1f, 4, 0, 4, 0));
            col.addView(langRow, lpm(MP, WC, 0, 0, 22, 0, 0));
        }
        sv.addView(col);
        tutRoot.addView(sv, lpw(MP, 0, 1f));

        LinearLayout dots = hbox();
        dots.setGravity(Gravity.CENTER);
        for (int i = 0; i < n; i++) {
            View d = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(4));
            g.setColor(i == tutPage ? cAccent : cStroke);
            d.setBackground(g);
            dots.addView(d, lpm(dp(i == tutPage ? 20 : 8), dp(8), 0, 4, 0, 4, 0));
        }
        tutRoot.addView(dots, lp(MP, dp(24)));

        LinearLayout nav = hbox();
        if (tutPage > 0) {
            nav.addView(barButton(T("Peeche", "Back"), cMod, v -> {
                tutPage--;
                renderTutorial();
            }), lpm(0, MP, 1f, 4, 0, 4, 0));
        }
        final boolean last = tutPage == n - 1;
        Button next = barButton(last ? T("Shuru karo", "Get started") : T("Aage", "Next"),
                last ? cGreen : cAccent, v -> {
                    if (last) {
                        hideTutorial(true);
                    } else {
                        tutPage++;
                        renderTutorial();
                    }
                });
        next.setTextSize(16);
        nav.addView(next, lpm(0, MP, 1.4f, 4, 0, 4, 0));
        tutRoot.addView(nav, lpm(MP, dp(52), 0, 0, 10, 0, 0));
    }

    // ---------------- settings screen ----------------

    private Switch urduSwitch;

    private void openSettings() {
        settingsOpen = true;
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(box.getWindowToken(), 0);
        if (urduSwitch != null) urduSwitch.setChecked(urduMode);
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
        LinearLayout page = vbox();
        page.setPadding(dp(12), dp(10), dp(12), dp(10));
        setPageBg(page);

        LinearLayout top = hbox();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(iconButton("\u2190", v -> closeSettings()), lp(dp(44), dp(44)));
        TextView title = text("Settings", 22, cText);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(14), 0, 0, 0);
        top.addView(title, lp(WC, WC));
        page.addView(top, lp(MP, dp(48)));

        ScrollView sv = new ScrollView(this);
        LinearLayout col = vbox();
        sv.addView(col);
        page.addView(sv, lpw(MP, 0, 1f));

        // language
        col.addView(sectionLabel(T("ZABAN / LANGUAGE", "LANGUAGE")));
        LinearLayout cl = card();
        LinearLayout lr = hbox();
        lr.addView(barButton("English", sEn ? cAccent : cMod, v -> setLang("en")),
                lpm(0, dp(44), 1f, 2, 0, 2, 0));
        lr.addView(barButton("Roman Urdu", sEn ? cMod : cAccent, v -> setLang("ur")),
                lpm(0, dp(44), 1f, 2, 0, 2, 0));
        cl.addView(lr);
        col.addView(cl, cardLp());

        // connection
        col.addView(sectionLabel(T("CONNECTION", "CONNECTION")));
        LinearLayout c1 = card();
        c1.addView(settingRow(T("Laptop se connect karo", "Connect to laptop"),
                T("Pair ki hui laptop chuno", "Pick a paired laptop"),
                barButton("Connect", cAccent, v -> chooseHost())));
        c1.addView(divider());
        c1.addView(settingRow(T("Phone visible karo", "Make phone visible"),
                T("Laptop se pairing ke liye 2 minute", "For pairing with the laptop (2 min)"),
                barButton("Visible", cAccent, v -> makeVisible())));
        col.addView(c1, cardLp());

        // keyboard
        col.addView(sectionLabel(T("KEYBOARD", "KEYBOARD")));
        LinearLayout ck = card();
        urduSwitch = new Switch(this);
        urduSwitch.setChecked(urduMode);
        urduSwitch.setOnCheckedChangeListener((b, on) -> {
            if (on != urduMode) setUrduMode(on);
        });
        ck.addView(settingRow(T("Urdu mode", "Urdu mode"),
                T("Laptop par Urdu keyboard chuno (Lang). Keys aur Gboard Urdu likhenge",
                        "Switch the laptop to Urdu (Lang). Keys and Gboard type Urdu"),
                urduSwitch));
        col.addView(ck, cardLp());

        // theme
        col.addView(sectionLabel("THEME"));
        LinearLayout c2 = card();
        LinearLayout sw = hbox();
        for (int i = 0; i < THEMES.length; i++) {
            final int idx = i;
            LinearLayout one = vbox();
            one.setGravity(Gravity.CENTER_HORIZONTAL);
            View chip = new View(this);
            chip.setBackground(swatch(i, i == theme));
            chip.setOnClickListener(v -> applyTheme(idx));
            one.addView(chip, lp(dp(46), dp(46)));
            TextView nm = text(THEME_NAMES[i], 11, i == theme ? cText : cHint);
            nm.setGravity(Gravity.CENTER);
            nm.setPadding(0, dp(6), 0, 0);
            one.addView(nm);
            sw.addView(one, lpw(0, WC, 1f));
        }
        c2.addView(sw);
        col.addView(c2, cardLp());

        // mouse speed
        col.addView(sectionLabel("MOUSE"));
        LinearLayout c3 = card();
        LinearLayout speedHead = hbox();
        TextView sName = text(T("Mouse ki speed", "Mouse speed"), 15, cText);
        sName.setTypeface(medium());
        speedHead.addView(sName, lpw(0, WC, 1f));
        final TextView sVal = text(String.format(Locale.US, "%.1fx", sens), 14, cHint);
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
                if (fromUser) ui().edit().putInt("sens", p + 5).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) { }

            @Override
            public void onStopTrackingTouch(SeekBar s) { }
        });
        c3.addView(sb, lpm(MP, WC, 0, 0, 8, 0, 0));
        col.addView(c3, cardLp());

        // typing
        col.addView(sectionLabel(T("TYPING", "TYPING")));
        LinearLayout c5 = card();
        Switch liveSw = new Switch(this);
        liveSw.setChecked(liveGboard);
        liveSw.setOnCheckedChangeListener((btn, on) -> {
            liveGboard = on;
            ui().edit().putBoolean("live", on).apply();
            updateSendUi();
        });
        c5.addView(settingRow(T("Gboard live typing", "Gboard live typing"),
                T("Band: Send dabane par hi laptop par jayega",
                        "Off: text goes to the laptop only when you tap Send"), liveSw));
        c5.addView(divider());
        LinearLayout tHead = hbox();
        TextView tName = text(T("Typing speed (wait)", "Typing speed (delay)"), 15, cText);
        tName.setTypeface(medium());
        tHead.addView(tName, lpw(0, WC, 1f));
        final TextView tVal = text(typeDelay + " ms", 14, cHint);
        tHead.addView(tVal);
        c5.addView(tHead);
        c5.addView(text(T("Agar laptop par harf ulte ya alag lines mein aayein to slider ko "
                        + "dayen (zyada ms) karo",
                "If letters come out wrong or on separate lines, move the slider right "
                        + "(more ms)"), 12, cHint));
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
                if (fromUser) ui().edit().putInt("delay", typeDelay).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) { }

            @Override
            public void onStopTrackingTouch(SeekBar s) { }
        });
        c5.addView(tb, lpm(MP, WC, 0, 0, 8, 0, 0));
        col.addView(c5, cardLp());

        // help
        col.addView(sectionLabel(T("MADAD / HELP", "HELP")));
        LinearLayout ch = card();
        ch.addView(settingRow(T("Tutorial dobara dekho", "Show the tutorial again"),
                T("Shuru ke steps", "Quick start steps"),
                barButton(T("Dekho", "Open"), cAccent, v -> {
                    closeSettings();
                    showTutorial(0);
                })));
        col.addView(ch, cardLp());

        // stop
        col.addView(sectionLabel("BLUETOOTH"));
        LinearLayout c4 = card();
        c4.addView(settingRow(T("Connection band karo", "Stop everything"),
                T("App band hogi, laptop disconnect ho jayega",
                        "The app closes and the laptop disconnects"),
                barButton(T("Band karo", "Stop"), 0xFFE5484D, v -> stopAll())));
        col.addView(c4, cardLp());
        return page;
    }

    private TextView sectionLabel(String text) {
        TextView t = text(text, 11, cHint);
        t.setLetterSpacing(0.12f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(4), dp(18), dp(4), dp(6));
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = vbox();
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        setCardBg(c, cBox, 16);
        return c;
    }

    private LinearLayout.LayoutParams cardLp() {
        return lp(MP, WC);
    }

    private View divider() {
        View d = new View(this);
        d.setBackgroundColor(cStroke);
        d.setLayoutParams(lpm(MP, dp(1), 0, 0, 10, 0, 10));
        return d;
    }

    private LinearLayout settingRow(String title, String sub, View action) {
        LinearLayout row = hbox();
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout texts = vbox();
        TextView t = text(title, 15, cText);
        t.setTypeface(medium());
        texts.addView(t);
        texts.addView(text(sub, 12, cHint));
        row.addView(texts, lpw(0, WC, 1f));
        LinearLayout.LayoutParams alp = (action instanceof Button)
                ? lpm(dp(100), dp(42), 0, 10, 0, 0, 0)
                : lpm(WC, WC, 0, 10, 0, 0, 0);
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
        Toast.makeText(this, T("Band kar diya", "Stopped"), Toast.LENGTH_SHORT).show();
        finish();
    }

    // ---------------- paste / clear ----------------

    private void pasteText() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData cd = cm == null ? null : cm.getPrimaryClip();
        if (cd == null || cd.getItemCount() == 0) {
            Toast.makeText(this, T("Clipboard khali hai", "Clipboard is empty"),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence t = cd.getItemAt(0).coerceToText(this);
        if (t == null || t.length() == 0) return;
        box.getText().append(t);   // in Keys tab this types on the laptop; in Gboard tab press Send
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
        if (key.code == -2) return toggleKey(false);
        if (key.code == -3) return toggleKey(true);
        final Button b = new Button(this);
        b.setText(key.label);
        b.setAllCaps(false);
        b.setTextColor(cText);
        b.setTypeface(medium());
        b.setTextSize(key.label.length() > 4 ? 11
                : (key.label.length() > 2 ? 13 : (key.label.length() == 2 ? 17 : 20)));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(0, 0, 0, 0);
        b.setStateListAnimator(null);
        setKeyBg(b, key.mod != 0 ? cMod : cKey);

        if (key.urdu) {
            urduButtons.add(b);
            urduKeys.add(key);
        } else if (key.mod != 0) {
            modButtons.add(b);
            modKeys.add(key);
        } else if (key.label.length() == 1 && Character.isLetter(key.label.charAt(0))) {
            letterButtons.add(b);
        } else if (isPrintableCode(key.code)) {
            dynButtons.add(b);
            dynKeys.add(key);
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
                Character c = rev().get((shift ? 0x100 : 0) | code);
                if (c != null && c != '\n' && c != '\t') t.append(c);
            }
        } finally {
            suppress = false;
        }
        prev = box.getText().toString();
        box.setSelection(box.getText().length());
    }

    // key labels that depend on the language (symbols, digits, comma, period)
    private void refreshLabels() {
        for (int i = 0; i < dynButtons.size(); i++) {
            K key = dynKeys.get(i);
            String lab = key.label;
            if (urduMode) {
                Character c = UREV.get((key.fmod != 0 ? 0x100 : 0) | key.code);
                if (c != null) lab = uLabel(c);
            }
            dynButtons.get(i).setText(lab);
        }
        refreshMods();
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
        for (int i = 0; i < urduButtons.size(); i++) {
            int idx = uIdx(urduKeys.get(i).code);
            if (idx < 0) continue;
            String lab = uLabel(shift ? US.charAt(idx) : UB.charAt(idx));
            Button b = urduButtons.get(i);
            b.setText(lab);
            b.setTextSize(lab.length() > 2 ? 11 : 20);
        }
    }

    // ---------------- text box -> laptop ----------------

    private int countTypeable(String s) {
        int n = 0;
        HashMap<Character, int[]> m = map();
        for (int i = 0; i < s.length(); i++) {
            if (m.containsKey(s.charAt(i))) n++;
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
        HashMap<Character, int[]> m = map();
        for (int i = 0; i < s.length(); i++) {
            int[] kc = m.get(s.charAt(i));
            if (kc == null) {
                skipped = true;
                continue;
            }
            queue.add(new int[]{kc[0], kc[1]});
            queue.add(new int[]{0, 0});
        }
        if (skipped) {
            Toast.makeText(this, urduMode
                            ? T("Kuch harf Urdu keyboard mein nahi hain (English harf?)",
                            "Some characters are not on the Urdu keyboard (English letters?)")
                            : T("Urdu/khaas huroof laptop par nahi ja sakte. Urdu mode on karo.",
                            "Urdu/special characters can't be typed. Turn on Urdu mode."),
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
            if (a[0] == -2) {          // wait entry
                queue.poll();
                H.postDelayed(this, a[1]);
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

    // touchpad: one finger moves, tap = left click, two fingers scroll, two-finger tap = right click
    private View.OnTouchListener padListener() {
        return (v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = downX = e.getX();
                    lastY = downY = e.getY();
                    downTime = e.getEventTime();
                    padMoved = false;
                    twoFinger = false;
                    accX = 0;
                    accY = 0;
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (e.getPointerCount() == 2) {
                        twoFinger = true;
                        twoDist = 0;
                        twoDownTime = e.getEventTime();
                        twoLastY = (e.getY(0) + e.getY(1)) / 2f;
                        scrollAcc = 0;
                        padMoved = true;
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (twoFinger) {
                        if (e.getPointerCount() >= 2) {
                            float cy = (e.getY(0) + e.getY(1)) / 2f;
                            twoDist += Math.abs(cy - twoLastY);
                            scrollAcc += cy - twoLastY;
                            twoLastY = cy;
                            int st = (int) (scrollAcc / dp(14));
                            if (st != 0) {
                                scrollAcc -= st * dp(14);
                                sendMouse(0, 0, Math.max(-127, Math.min(127, -st)));
                            }
                        }
                        return true;
                    }
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
                case MotionEvent.ACTION_POINTER_UP:
                    if (twoFinger && e.getPointerCount() == 2) {
                        if (twoDist < dp(8) && e.getEventTime() - twoDownTime < 300) {
                            mouseButtons |= 2;       // two-finger tap = right click
                            sendMouse(0, 0, 0);
                            mouseButtons &= ~2;
                            sendMouse(0, 0, 0);
                        }
                        twoFinger = false;
                        padMoved = true;
                        int rem = e.getActionIndex() == 0 ? 1 : 0;
                        lastX = e.getX(rem);
                        lastY = e.getY(rem);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!padMoved && !twoFinger && e.getEventTime() - downTime < 250) {
                        mouseButtons |= 1;
                        sendMouse(0, 0, 0);
                        mouseButtons &= ~1;
                        sendMouse(0, 0, 0);
                    }
                    twoFinger = false;
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
        rawMouse(mouseButtons, x, y, wheel);
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
        try {
            BluetoothManager bm = getSystemService(BluetoothManager.class);
            BluetoothAdapter ad = bm == null ? null : bm.getAdapter();
            if (ad == null) {
                status.setText(T("Is phone mein Bluetooth nahi hai", "This phone has no Bluetooth"));
                return;
            }
            if (!ad.isEnabled()) {
                status.setText(T("Bluetooth band hai. 'Allow' dabao", "Bluetooth is off. Tap Allow"));
                if (!askedEnable) {
                    askedEnable = true;
                    startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 77);
                }
                return;
            }
            startHid();
            updateStatus();
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
            status.setText(T("Connected: ", "Connected: ") + sHost.getName());
            setDotColor(0xFF34C759);
        } else if (sRegistered) {
            status.setText(T("Tayyar hai. Settings se laptop chuno",
                    "Ready. Pick your laptop in Settings"));
            setDotColor(0xFFFFB020);
        } else {
            status.setText(T("Bluetooth keyboard shuru ho raha hai...", "Starting Bluetooth keyboard..."));
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
            askedEnable = false;
            initHid();
            Toast.makeText(this, T("Bluetooth abhi tayyar nahi", "Bluetooth is not ready yet"),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        final List<BluetoothDevice> list = new ArrayList<>(sAdapter.getBondedDevices());
        if (list.isEmpty()) {
            Toast.makeText(this, T("Pehle laptop ko Bluetooth se pair karo",
                    "Pair the laptop with Bluetooth first"), Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) names[i] = list.get(i).getName();
        new AlertDialog.Builder(this)
                .setTitle(T("Laptop chuno", "Choose laptop"))
                .setItems(names, (d, i) -> {
                    sRetries = 0;
                    boolean ok = sHid.connect(list.get(i));
                    String msg = ok ? T("Connect ho raha hai...", "Connecting...")
                            : T("Connect nahi hua, dobara try karo", "Could not connect, try again");
                    status.setText(msg);
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
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
