package com.buddyapps.ethangpt;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Base64;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Nexus AI — Claude Code-inspired interface: warm dark canvas, terracotta
 * accents, a model pill, a warm orb welcome, slash-command skills in the
 * swipe-out drawer, and a rounded prompt bar.
 *
 * AUTO (the only mode): before every message the app checks for a validated
 * internet connection. When online it uses a free model (key built into the
 * APK); when offline it uses the 10,874,880-parameter N-gram model embedded in
 * the app. If an online request fails it falls back to the offline model too.
 *
 * Voice agent: the mic starts a conversation loop — listen (system recognition
 * service, Google engines explicitly skipped), answer, speak it back with a
 * distinct "Ethan" voice, then listen again. Tap the mic (or Stop) to end it.
 * Chat history is saved on the device; swipe right from the left edge (or tap
 * ☰) to open the drawer with previous chats.
 */
public class MainActivity extends Activity {

    private static final String PREFS = "ethangpt";
    private static final String PREF_PROVIDER = "provider";
    private static final String PREF_KEY = "apiKey";
    private static final String PREF_OPENAI_KEY = "openaiKey";
    private static final String PREF_MODEL = "model";
    private static final String PREF_CHATS = "chats";
    private static final String PREF_ACTIVE_CHAT = "active_chat";

    private static final int REQ_RECORD_AUDIO = 1;
    private static final int REQ_PICK_FILE = 2;

    // Claude Code-inspired warm palette with a terracotta accent.
    private static final int C_BG = 0xFF1F1E1D;
    private static final int C_SURFACE = 0xFF2B2A27;
    private static final int C_SURFACE_2 = 0xFF373532;
    private static final int C_DRAWER = 0xFF242320;
    private static final int C_TEXT = 0xFFF5F4EF;
    private static final int C_TEXT_DIM = 0xFFA8A29A;
    private static final int C_ACCENT = 0xFFD97757;   // Claude terracotta
    private static final int C_GRAD_A = 0xFFE08A63;
    private static final int C_GRAD_B = 0xFFC15F3C;
    private static final int C_PILL_A = 0xFF332F2A;   // warm secondary pills
    private static final int C_PILL_B = 0xFF292622;
    private static final int C_PILL_STROKE = 0xFF4A443C;

    // --- chat screen ---
    private SwipeScrollView chatScroll;
    private LinearLayout chatList;
    private EditText input;
    private TextView statusText;
    private View welcomeView;
    private FrameLayout actionButton;
    private IconView actionIcon;
    private GradientDrawable actionBg;
    private FrameLayout soundButton;
    private IconView soundIcon;
    private LinearLayout voiceBar;
    private TextView voiceStatusText;
    private FrameLayout voiceOverlay;
    private OrbView voiceOverlayOrb;
    private TextView voiceOverlayText;
    private boolean soundMuted = false;

    private ChatBot bot;
    private ModelLoader.Bundle bundle;
    private SharedPreferences prefs;
    private ConnectivityManager connectivity;
    private volatile boolean ready = false;
    private boolean receiverRegistered = false;
    // Offline mode is disabled on devices with less than 8 GB of RAM (the
    // built-in model is memory-heavy there). Such devices require internet.
    private boolean offlineAllowed = true;

    // --- chat history + drawer ---
    private final ArrayList<ChatRecord> chats = new ArrayList<>();
    private String activeChatId = null;
    private LinearLayout drawer;
    private LinearLayout drawerList;
    private View scrim;
    private boolean drawerOpen = false;
    private int drawerWidth = 0;

    // --- swipe navigation between workspaces (no visible tab bar) ---
    private View[] tabPages;
    private int currentTab = 0;

    // --- voice agent ---
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private MediaPlayer mediaPlayer;
    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean voiceMode = false;     // hands-free conversation loop
    private boolean voiceSession = false;  // speak the answer to the current message
    private String sttEngineLabel = "system default";
    private String ttsEngineLabel = "system default";

    private static class ChatRecord {
        String id;
        String title;
        long updated;
        final ArrayList<String[]> turns = new ArrayList<>();
    }

    private final BroadcastReceiver connectivityReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { updateStatus(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        // Disable the offline model on low-RAM devices (< 8 GB total memory).
        try {
            android.app.ActivityManager am = (android.app.ActivityManager)
                    getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                android.app.ActivityManager.MemoryInfo mi =
                        new android.app.ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                offlineAllowed = mi.totalMem >= (8L * 1024 * 1024 * 1024);
            }
        } catch (Exception ignored) { offlineAllowed = true; }

        // A key can be supplied inside the APK (assets/key.txt), so the online
        // model works without the user pasting anything.
        String builtin = readBuiltinKey();
        if (!builtin.isEmpty() && prefs.getString(PREF_KEY, "").trim().isEmpty()) {
            prefs.edit().putString(PREF_KEY, builtin).apply();
        }

        int topInset = statusBarHeight();
        int bottomInset = navBarHeight();

        // --- Main view: edge-to-edge dark canvas ---
        LinearLayout mainView = new LinearLayout(this);
        mainView.setOrientation(LinearLayout.VERTICAL);
        mainView.setBackgroundColor(C_BG);
        // Top inset keeps content below the status bar; the bottom is handled by
        // the new tab bar (which reserves the nav-bar inset itself).
        mainView.setPadding(0, topInset, 0, 0);

        // --- Top bar: ☰ | AUTO pill | sound ---
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(16), dp(10), dp(16), dp(8));

        FrameLayout menu = iconButton(IconView.MENU, 44, C_SURFACE, C_TEXT);
        menu.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openDrawer(); }
        });
        topBar.addView(menu);

        statusText = new TextView(this);
        statusText.setTextSize(13);
        statusText.setTextColor(C_TEXT);
        statusText.setPadding(dp(16), dp(9), dp(16), dp(9));
        GradientDrawable statusBg = gradientPill(C_PILL_A, C_PILL_B,22);
        statusBg.setStroke(dp(1), C_PILL_STROKE);
        statusText.setBackground(statusBg);
        statusText.setSingleLine(true);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pl.leftMargin = dp(10);
        statusText.setLayoutParams(pl);
        statusText.setClickable(true);
        // The AUTO pill is now the only way to change provider / API key.
        statusText.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showOnlineSettings(); }
        });
        topBar.addView(statusText);

        View topSpacer = new View(this);
        topSpacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        topBar.addView(topSpacer);

        soundButton = iconButton(IconView.SPEAKER_ON, 44, C_SURFACE, C_TEXT);
        soundIcon = (IconView) soundButton.getChildAt(0);
        soundButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleSound(); }
        });
        topBar.addView(soundButton);
        mainView.addView(topBar);
        updateStatus();

        // --- Voice-agent status bar (hidden until a voice session starts) ---
        voiceBar = new LinearLayout(this);
        voiceBar.setOrientation(LinearLayout.HORIZONTAL);
        voiceBar.setGravity(Gravity.CENTER_VERTICAL);
        voiceBar.setBackground(rounded(0xFF3A2E26, 14));
        voiceBar.setPadding(dp(14), dp(8), dp(8), dp(8));
        voiceBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams vblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vblp.setMargins(dp(16), 0, dp(16), dp(6));
        voiceBar.setLayoutParams(vblp);

        voiceStatusText = new TextView(this);
        voiceStatusText.setText("Voice agent");
        voiceStatusText.setTextSize(12);
        voiceStatusText.setTextColor(0xFFE8A878);
        voiceStatusText.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        voiceBar.addView(voiceStatusText);

        TextView stopVoice = new TextView(this);
        stopVoice.setText("Stop");
        stopVoice.setTextSize(12);
        stopVoice.setTextColor(0xFFFF8A8A);
        stopVoice.setPadding(dp(12), dp(4), dp(8), dp(4));
        stopVoice.setClickable(true);
        stopVoice.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { stopVoiceMode(); }
        });
        voiceBar.addView(stopVoice);
        mainView.addView(voiceBar);

        // --- Chat area (swipe right near the left edge to open history) ---
        chatList = new LinearLayout(this);
        chatList.setOrientation(LinearLayout.VERTICAL);
        chatList.setPadding(dp(16), dp(4), dp(16), dp(4));

        chatScroll = new SwipeScrollView(this);
        chatScroll.setFillViewport(true);
        chatScroll.addView(chatList);
        chatScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        chatScroll.setSwipeListener(new SwipeScrollView.SwipeListener() {
            @Override public void onOpen() { openDrawer(); }
            @Override public void onClose() { closeDrawer(); }
        });
        mainView.addView(chatScroll);

        // --- Quick chips (New chat / Code ideas / Voice) ---
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(dp(12), dp(4), dp(12), dp(4));
        chips.addView(chip(IconView.PLUS, "New chat", new View.OnClickListener() {
            @Override public void onClick(View v) { newChat(); }
        }));
        chips.addView(chip(IconView.SPARKLE, "Code ideas", new View.OnClickListener() {
            @Override public void onClick(View v) { sendCodeIdeas(); }
        }));
        chips.addView(chip(IconView.CODE, "Code IDE", new View.OnClickListener() {
            @Override public void onClick(View v) { selectTab(2); }
        }));
        chips.addView(chip(IconView.SPARKLE, "Image", new View.OnClickListener() {
            @Override public void onClick(View v) { generateImage(); }
        }));
        chips.addView(chip(IconView.MIC, "Voice", new View.OnClickListener() {
            @Override public void onClick(View v) { toggleVoiceMode(); }
        }));
        chipScroll.addView(chips);
        mainView.addView(chipScroll);

        // --- Rounded input bar: + | message | action ---
        LinearLayout inputWrap = new LinearLayout(this);
        inputWrap.setOrientation(LinearLayout.VERTICAL);
        inputWrap.setPadding(dp(12), dp(2), dp(12), dp(8));

        LinearLayout inputPill = new LinearLayout(this);
        inputPill.setOrientation(LinearLayout.HORIZONTAL);
        inputPill.setGravity(Gravity.CENTER_VERTICAL);
        inputPill.setPadding(dp(6), dp(6), dp(6), dp(6));
        GradientDrawable inputBg = rounded(0xFF292622, 30);
        inputBg.setStroke(dp(1), C_PILL_STROKE);
        inputPill.setBackground(inputBg);

        FrameLayout plus = iconButton(IconView.PLUS, 40, C_SURFACE_2, C_ACCENT);
        plus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickFile(); }
        });
        inputPill.addView(plus);

        input = new EditText(this);
        input.setHint("Ask Nexus anything…");
        input.setHintTextColor(C_TEXT_DIM);
        input.setTextColor(C_TEXT);
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setBackground(null);
        input.setMinHeight(dp(44));
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setPadding(dp(4), 0, dp(4), 0);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                updateActionButton();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendMessage();
                    return true;
                }
                return false;
            }
        });
        inputPill.addView(input);

        buildActionButton();
        inputPill.addView(actionButton);
        inputWrap.addView(inputPill);
        mainView.addView(inputWrap);

        // --- Tool tabs: Terminal / Code IDE / Local Web ---
        final View terminalPane = TerminalView.build(this);
        final View codePane = CodeIdeView.build(this, new CodeIdeView.CodeHost() {
            @Override public void onPreview(String url) {
                LocalBrowserView.loadUrl(url);
                selectTab(3);
            }
            @Override public void onSendToChat(String code) {
                input.setText(code);
                selectTab(0);
                input.requestFocus();
                Toast.makeText(MainActivity.this, "Code added to chat — ask Nexus about it",
                        Toast.LENGTH_SHORT).show();
            }
        });
        final View browserPane = LocalBrowserView.build(this);
        terminalPane.setVisibility(View.GONE);
        codePane.setVisibility(View.GONE);
        browserPane.setVisibility(View.GONE);

        // --- Tab host: page area (weight 1) + bottom tab bar ---
        LinearLayout tabHost = new LinearLayout(this);
        tabHost.setOrientation(LinearLayout.VERTICAL);
        tabHost.setBackgroundColor(C_BG);

        FrameLayout pageArea = new FrameLayout(this);
        pageArea.addView(mainView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pageArea.addView(terminalPane, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pageArea.addView(codePane, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pageArea.addView(browserPane, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tabHost.addView(pageArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        tabPages = new View[] { mainView, terminalPane, codePane, browserPane };
        // No bottom tab bar — swipe gestures move between workspaces. Reserve
        // the system nav-bar inset inside the page area so nothing is hidden.
        pageArea.setPadding(0, 0, 0, bottomInset);
        // Initial workspace: Chat
        selectTab(0);

        // --- Container: tab host + scrim + drawer ---
        SwipeContainer container = new SwipeContainer(this);
        container.setSwipeListener(new DrawerSwipe());
        container.setBackgroundColor(C_BG);
        container.addView(tabHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        scrim = new View(this);
        scrim.setBackgroundColor(0x99000000);
        scrim.setVisibility(View.GONE);
        scrim.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closeDrawer(); }
        });
        container.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        drawer = buildDrawer();
        // Half-width drawer: a peek that still leaves the chat visible.
        drawerWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.5f);
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(
                drawerWidth, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        drawer.setLayoutParams(dl);
        drawer.setTranslationX(-drawerWidth);
        container.addView(drawer);

        voiceOverlay = buildVoiceOverlay();
        voiceOverlay.setVisibility(View.GONE);
        container.addView(voiceOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(container);

        initTts();
        initChats();
        loadModel();
    }

    /** Switch the visible pane: 0=Chat, 1=Terminal, 2=Code IDE, 3=Web. */
    private void selectTab(int idx) {
        if (tabPages == null || idx < 0 || idx >= tabPages.length) return;
        for (int k = 0; k < tabPages.length; k++) {
            tabPages[k].setVisibility(k == idx ? View.VISIBLE : View.GONE);
        }
        currentTab = idx;
    }

    private static final String[] WORKSPACE_NAMES = { "Chat", "Terminal", "Code IDE", "Web" };

    /** Cycle to the next workspace with a left swipe from the right edge. */
    private void nextWorkspace() {
        selectTab((currentTab + 1) % tabPages.length);
        Toast.makeText(this, WORKSPACE_NAMES[currentTab], Toast.LENGTH_SHORT).show();
    }

    /** Cycle to the previous workspace with a right swipe from the right edge. */
    private void prevWorkspace() {
        selectTab((currentTab + tabPages.length - 1) % tabPages.length);
        Toast.makeText(this, WORKSPACE_NAMES[currentTab], Toast.LENGTH_SHORT).show();
    }

    // --------------------------- helpers / style ---------------------------- //
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    /** Warm gradient pill used for primary buttons and accents. */
    private GradientDrawable gradientPill(int a, int b, int radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] { a, b });
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
    }

    private int navBarHeight() {
        int id = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(48);
    }

    private String readBuiltinKey() {
        return readAssetLine("key.txt");
    }

    /** The Gemini key is baked into the APK and used only for the speaking model. */
    private String readGeminiKey() {
        return readAssetLine("gemini_key.txt");
    }

    private String readAssetLine(String name) {
        try {
            java.io.InputStream in = getAssets().open(name);
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(in, "UTF-8"));
            String line = reader.readLine();
            reader.close();
            return line == null ? "" : line.trim();
        } catch (Exception e) {
            return "";
        }
    }

    /** A circular, flat icon button. */
    private FrameLayout iconButton(int type, int sizeDp, int bgColor, int iconColor) {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(bgColor);
        f.setBackground(bg);
        int size = dp(sizeDp);
        f.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        IconView icon = new IconView(this, type);
        icon.setColor(iconColor);
        int is = dp(Math.round(sizeDp * 0.52f));
        f.addView(icon, new FrameLayout.LayoutParams(is, is, Gravity.CENTER));
        f.setClickable(true);
        f.setFocusable(true);
        return f;
    }

    private View chip(int iconType, String label, View.OnClickListener listener) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(14), dp(9), dp(16), dp(9));
        GradientDrawable chipBg = gradientPill(C_PILL_A, C_PILL_B,22);
        chipBg.setStroke(dp(1), C_PILL_STROKE);
        chip.setBackground(chipBg);
        IconView ic = new IconView(this, iconType);
        ic.setColor(C_ACCENT);
        chip.addView(ic, new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(13);
        tv.setTextColor(C_TEXT);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.leftMargin = dp(8);
        tv.setLayoutParams(tl);
        chip.addView(tv);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        chip.setLayoutParams(lp);
        chip.setClickable(true);
        chip.setOnClickListener(listener);
        return chip;
    }

    /**
     * A FrameLayout that watches horizontal swipes anywhere on screen (all tabs,
     * including WebView and Code IDE) and opens/closes the drawer. Touches still
     * pass through to children, so scrolling and typing are unaffected.
     */
    public static class SwipeContainer extends FrameLayout {
        interface SwipeListener {
            void onOpen();
            void onClose();
            void onNext();
            void onPrev();
        }
        private SwipeListener listener;
        private float x0, y0;
        private boolean done;

        public SwipeContainer(Context c) { super(c); }
        void setSwipeListener(SwipeListener l) { this.listener = l; }

        @Override public boolean dispatchTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    x0 = e.getX();
                    y0 = e.getY();
                    done = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!done) {
                        float dx = e.getX() - x0;
                        float dy = e.getY() - y0;
                        // Same sensitivity as the chat screen's SwipeScrollView
                        // so the gesture feels identical on every tab.
                        if (Math.abs(dx) > 44 && Math.abs(dx) > Math.abs(dy) * 1.4f) {
                            done = true;
                            if (listener != null) {
                                float edge = 140f;
                                // Right from the left edge opens the menu.
                                if (dx > 0 && x0 < edge) listener.onOpen();
                                // Left from the right edge: next workspace.
                                else if (dx < 0 && x0 > getWidth() - edge) listener.onNext();
                                // Any other right swipe: previous workspace.
                                else if (dx > 0) listener.onPrev();
                                // Any other left swipe closes the drawer (self-guarded).
                                else if (dx < 0) listener.onClose();
                            }
                        }
                    }
                    break;
                default:
                    break;
            }
            return super.dispatchTouchEvent(e);
        }

        private float dpPx() { return getResources().getDisplayMetrics().density; }
    }

    /** Bridges the swipe container to the drawer and workspace navigation. */
    private final class DrawerSwipe implements SwipeContainer.SwipeListener {
        @Override public void onOpen() { openDrawer(); }
        @Override public void onClose() { closeDrawer(); }
        @Override public void onNext() { closeDrawer(); nextWorkspace(); }
        @Override public void onPrev() { closeDrawer(); prevWorkspace(); }
    }

    /** A ScrollView that lets a horizontal swipe open/close the history drawer. */
    public static class SwipeScrollView extends ScrollView {
        interface SwipeListener { void onOpen(); void onClose(); }
        private SwipeListener listener;
        private float downX, downY;
        private boolean horizontal = false;

        public SwipeScrollView(Context c) { super(c); }
        void setSwipeListener(SwipeListener l) { this.listener = l; }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    horizontal = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!horizontal) {
                        float dx = e.getX() - downX;
                        float dy = e.getY() - downY;
                        if (Math.abs(dx) > 44 && Math.abs(dx) > Math.abs(dy) * 1.4f) {
                            horizontal = true;
                            if (listener != null) {
                                if (dx > 0 && downX < 140) listener.onOpen();
                                else if (dx < 0) listener.onClose();
                            }
                        }
                    }
                    if (horizontal) return true;
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (horizontal) {
                        horizontal = false;
                        return true;
                    }
                    break;
            }
            return super.onTouchEvent(e);
        }
    }

    /** Flat line icons drawn with Canvas (no image assets needed). */
    public static class IconView extends View {
        public static final int MENU = 1, PLUS = 2, MIC = 3, SPEAKER_ON = 4, SPEAKER_OFF = 5,
                GEAR = 6, SEND = 7, SPARKLE = 8, CHAT = 9, TRASH = 10, CLOSE = 11,
                STOP = 12, PERSON = 13, TERMINAL = 14, CODE = 15, WEB = 16;

        private int type;
        private int color = 0xFFF5F4EF;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public IconView(Context c, int type) {
            super(c);
            this.type = type;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        public void setType(int t) { this.type = t; invalidate(); }
        public void setColor(int c) { this.color = c; invalidate(); }

        private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float s = Math.min(w, h);
            c.save();
            c.translate((w - s) / 2f, (h - s) / 2f);
            p.setColor(color);
            p.setStrokeWidth(Math.max(dp(1.4f), s * 0.075f));
            p.setStyle(Paint.Style.STROKE);
            float cx = s / 2f, cy = s / 2f;
            switch (type) {
                case MENU:
                    line(c, 0.24f * s, 0.36f * s, 0.76f * s, 0.36f * s);
                    line(c, 0.24f * s, 0.50f * s, 0.76f * s, 0.50f * s);
                    line(c, 0.24f * s, 0.64f * s, 0.76f * s, 0.64f * s);
                    break;
                case PLUS:
                    line(c, cx, 0.30f * s, cx, 0.70f * s);
                    line(c, 0.30f * s, cy, 0.70f * s, cy);
                    break;
                case MIC:
                    c.drawRoundRect(new RectF(cx - 0.11f * s, 0.24f * s,
                            cx + 0.11f * s, 0.56f * s), 0.11f * s, 0.11f * s, p);
                    c.drawArc(new RectF(cx - 0.19f * s, 0.34f * s,
                            cx + 0.19f * s, 0.64f * s), 0, 180, false, p);
                    line(c, cx, 0.64f * s, cx, 0.74f * s);
                    line(c, 0.36f * s, 0.76f * s, 0.64f * s, 0.76f * s);
                    break;
                case SEND:
                    line(c, cx, 0.70f * s, cx, 0.28f * s);
                    line(c, 0.36f * s, 0.42f * s, cx, 0.28f * s);
                    line(c, 0.64f * s, 0.42f * s, cx, 0.28f * s);
                    break;
                case SPARKLE: {
                    p.setStyle(Paint.Style.FILL);
                    Path path = new Path();
                    path.moveTo(cx, 0.16f * s);
                    path.quadTo(cx + 0.06f * s, cy - 0.06f * s, 0.84f * s, cy);
                    path.quadTo(cx + 0.06f * s, cy + 0.06f * s, cx, 0.84f * s);
                    path.quadTo(cx - 0.06f * s, cy + 0.06f * s, 0.16f * s, cy);
                    path.quadTo(cx - 0.06f * s, cy - 0.06f * s, cx, 0.16f * s);
                    path.close();
                    c.drawPath(path, p);
                    break;
                }
                case CHAT: {
                    c.drawRoundRect(new RectF(0.18f * s, 0.22f * s,
                            0.82f * s, 0.62f * s), 0.14f * s, 0.14f * s, p);
                    Path tail = new Path();
                    tail.moveTo(0.34f * s, 0.62f * s);
                    tail.lineTo(0.30f * s, 0.78f * s);
                    tail.lineTo(0.48f * s, 0.62f * s);
                    c.drawPath(tail, p);
                    break;
                }
                case GEAR: {
                    c.drawCircle(cx, cy, 0.16f * s, p);
                    for (int i = 0; i < 8; i++) {
                        double a = Math.toRadians(i * 45);
                        line(c,
                                (float) (cx + Math.cos(a) * 0.21f * s),
                                (float) (cy + Math.sin(a) * 0.21f * s),
                                (float) (cx + Math.cos(a) * 0.29f * s),
                                (float) (cy + Math.sin(a) * 0.29f * s));
                    }
                    c.drawCircle(cx, cy, 0.05f * s, p);
                    break;
                }
                case SPEAKER_ON:
                case SPEAKER_OFF: {
                    Path body = new Path();
                    body.moveTo(0.24f * s, 0.42f * s);
                    body.lineTo(0.36f * s, 0.42f * s);
                    body.lineTo(0.50f * s, 0.28f * s);
                    body.lineTo(0.50f * s, 0.72f * s);
                    body.lineTo(0.36f * s, 0.58f * s);
                    body.lineTo(0.24f * s, 0.58f * s);
                    body.close();
                    c.drawPath(body, p);
                    if (type == SPEAKER_ON) {
                        c.drawArc(new RectF(0.44f * s, 0.38f * s,
                                0.62f * s, 0.62f * s), -50, 100, false, p);
                        c.drawArc(new RectF(0.44f * s, 0.30f * s,
                                0.76f * s, 0.70f * s), -50, 100, false, p);
                    } else {
                        line(c, 0.58f * s, 0.40f * s, 0.74f * s, 0.60f * s);
                        line(c, 0.74f * s, 0.40f * s, 0.58f * s, 0.60f * s);
                    }
                    break;
                }
                case TRASH: {
                    line(c, 0.24f * s, 0.30f * s, 0.76f * s, 0.30f * s);
                    line(c, 0.40f * s, 0.24f * s, 0.60f * s, 0.24f * s);
                    line(c, 0.40f * s, 0.24f * s, 0.40f * s, 0.30f * s);
                    line(c, 0.60f * s, 0.24f * s, 0.60f * s, 0.30f * s);
                    c.drawRoundRect(new RectF(0.30f * s, 0.32f * s,
                            0.70f * s, 0.78f * s), 0.07f * s, 0.07f * s, p);
                    line(c, 0.44f * s, 0.42f * s, 0.44f * s, 0.68f * s);
                    line(c, 0.56f * s, 0.42f * s, 0.56f * s, 0.68f * s);
                    break;
                }
                case CLOSE:
                    line(c, 0.30f * s, 0.30f * s, 0.70f * s, 0.70f * s);
                    line(c, 0.70f * s, 0.30f * s, 0.30f * s, 0.70f * s);
                    break;
                case STOP:
                    p.setStyle(Paint.Style.FILL);
                    c.drawRoundRect(new RectF(0.32f * s, 0.32f * s,
                            0.68f * s, 0.68f * s), 0.08f * s, 0.08f * s, p);
                    break;
                case PERSON:
                    c.drawCircle(cx, 0.38f * s, 0.13f * s, p);
                    c.drawArc(new RectF(0.26f * s, 0.52f * s,
                            0.74f * s, 0.92f * s), 180, 180, false, p);
                    break;
                case TERMINAL: {
                    c.drawRoundRect(new RectF(0.18f * s, 0.24f * s,
                            0.82f * s, 0.76f * s), 0.10f * s, 0.10f * s, p);
                    line(c, 0.30f * s, 0.44f * s, 0.44f * s, 0.54f * s);
                    line(c, 0.44f * s, 0.54f * s, 0.30f * s, 0.64f * s);
                    line(c, 0.52f * s, 0.66f * s, 0.70f * s, 0.66f * s);
                    break;
                }
                case CODE:
                    line(c, 0.40f * s, 0.34f * s, 0.24f * s, 0.50f * s);
                    line(c, 0.24f * s, 0.50f * s, 0.40f * s, 0.66f * s);
                    line(c, 0.60f * s, 0.34f * s, 0.76f * s, 0.50f * s);
                    line(c, 0.76f * s, 0.50f * s, 0.60f * s, 0.66f * s);
                    line(c, 0.56f * s, 0.28f * s, 0.44f * s, 0.72f * s);
                    break;
                case WEB:
                    c.drawCircle(cx, cy, 0.30f * s, p);
                    c.drawArc(new RectF(cx - 0.30f * s, cy - 0.30f * s,
                            cx + 0.30f * s, cy + 0.30f * s), 0, 360, false, p);
                    line(c, 0.20f * s, cy, 0.80f * s, cy);
                    c.drawArc(new RectF(cx - 0.14f * s, cy - 0.30f * s,
                            cx + 0.14f * s, cy + 0.30f * s), 0, 360, false, p);
                    break;
            }
            c.restore();
        }

        private void line(Canvas c, float x1, float y1, float x2, float y2) {
            c.drawLine(x1, y1, x2, y2, p);
        }
    }

    /** The blue orb from the welcome screen; it pulses while the voice bot is active. */
    public static class OrbView extends View {
        public static final int MOOD_IDLE = 0, MOOD_LISTEN = 1, MOOD_SPEAK = 2, MOOD_THINK = 3;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint eye = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int mood = MOOD_IDLE;
        private boolean pulsing = false;
        private float phase = 0f;

        private final Runnable pulseTick = new Runnable() {
            @Override public void run() {
                phase += 0.16f;
                invalidate();
                if (pulsing) postDelayed(this, 40);
            }
        };

        public OrbView(Context c) {
            super(c);
            eye.setColor(C_TEXT);
        }

        public void setMood(int m) { mood = m; invalidate(); }

        public void startPulse() {
            if (!pulsing) {
                pulsing = true;
                phase = 0f;
                post(pulseTick);
            }
        }

        public void stopPulse() {
            pulsing = false;
            removeCallbacks(pulseTick);
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float s = Math.min(w, h);
            float cx = w / 2f, cy = h / 2f;
            float r = s * 0.46f;

            int[] colors;
            int glowColor;
            switch (mood) {
                case MOOD_SPEAK:
                    colors = new int[]{0xFFFFD9C2, 0xFFE89A6A, 0xFFB85C35};
                    glowColor = 0xFFE89A6A;
                    break;
                case MOOD_THINK:
                    colors = new int[]{0xFFF4C9A8, 0xFFD97757, 0xFF9C4B2C};
                    glowColor = 0xFFD97757;
                    break;
                case MOOD_LISTEN:
                    colors = new int[]{0xFFFFE0C4, 0xFFECA070, 0xFFC15F3C};
                    glowColor = 0xFFECA070;
                    break;
                default:
                    colors = new int[]{0xFFF3CCB0, 0xFFD97757, 0xFFA84E2E};
                    glowColor = 0xFFD97757;
                    break;
            }

            if (pulsing) {
                float pr = r * (1.06f + 0.06f * (float) Math.sin(phase));
                int alpha = (int) (45 + 55 * (0.5f + 0.5f * (float) Math.sin(phase)));
                glow.setColor(Color.argb(alpha,
                        Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor)));
                c.drawCircle(cx, cy, pr, glow);
            }

            paint.setShader(new RadialGradient(cx - s * 0.12f, cy - s * 0.16f, s * 0.62f,
                    colors, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r, paint);
            paint.setShader(null);

            float ew = s * 0.075f, eh = s * 0.17f, gap = s * 0.10f;
            float ey = cy - s * 0.04f;
            c.drawRoundRect(new RectF(cx - gap - ew, ey - eh / 2,
                    cx - gap + ew, ey + eh / 2), ew, ew, eye);
            c.drawRoundRect(new RectF(cx + gap - ew, ey - eh / 2,
                    cx + gap + ew, ey + eh / 2), ew, ew, eye);
        }
    }

    // -------------------------- connectivity -------------------------------- //
    private boolean isInternetAvailable() {
        if (connectivity == null) return false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.net.Network network = connectivity.getActiveNetwork();
                if (network == null) return false;
                NetworkCapabilities caps = connectivity.getNetworkCapabilities(network);
                return caps != null
                        && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            }
            android.net.NetworkInfo info = connectivity.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    private void updateStatus() {
        if (statusText == null) return;
        boolean online = isInternetAvailable();
        statusText.setText("AUTO · " + (online ? "online" : "offline"));
        statusText.setTextColor(online ? C_TEXT : C_TEXT_DIM);
    }

    private void toggleSound() {
        soundMuted = !soundMuted;
        if (soundIcon != null) {
            soundIcon.setType(soundMuted ? IconView.SPEAKER_OFF : IconView.SPEAKER_ON);
        }
        if (soundMuted && tts != null) {
            try { tts.stop(); } catch (Exception ignored) { }
        }
        if (soundMuted) releaseVoicePlayer();
    }

    @Override protected void onStart() {
        super.onStart();
        if (!receiverRegistered) {
            try {
                registerReceiver(connectivityReceiver,
                        new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
                receiverRegistered = true;
            } catch (Exception ignored) { }
        }
        updateStatus();
    }

    @Override protected void onStop() {
        if (voiceMode) stopVoiceMode();
        if (receiverRegistered) {
            try { unregisterReceiver(connectivityReceiver); } catch (Exception ignored) { }
            receiverRegistered = false;
        }
        super.onStop();
    }

    @Override protected void onDestroy() {
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Exception ignored) { }
        }
        releaseVoicePlayer();
        if (recognizer != null) {
            try { recognizer.destroy(); } catch (Exception ignored) { }
        }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ----------------------- voice agent: speaking -------------------------- //
    private String pickTtsEngine() {
        try {
            PackageManager pm = getPackageManager();
            List<ResolveInfo> engines = pm.queryIntentServices(
                    new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0);
            if (engines != null) {
                for (ResolveInfo info : engines) {
                    if (info.serviceInfo == null) continue;
                    String pkg = info.serviceInfo.packageName;
                    if (pkg != null && !pkg.toLowerCase(Locale.ROOT).contains("google")) {
                        ttsEngineLabel = pkg;
                        return pkg;
                    }
                }
            }
        } catch (Exception e) { }
        ttsEngineLabel = "system default";
        return null;
    }

    private void initTts() {
        String engine = pickTtsEngine();
        TextToSpeech.OnInitListener listener = new TextToSpeech.OnInitListener() {
            @Override public void onInit(int status) {
                ttsReady = status == TextToSpeech.SUCCESS;
                if (ttsReady) {
                    int result = tts.setLanguage(Locale.getDefault());
                    if (result == TextToSpeech.LANG_MISSING_DATA
                            || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        ttsReady = false;
                    } else {
                        // Give Ethan a distinct, calm voice.
                        tts.setPitch(1.06f);
                        tts.setSpeechRate(0.96f);
                    }
                }
                updateVoiceStatus(ttsReady
                        ? "Voice agent ready · tap the mic to talk"
                        : "Speech playback is unavailable on this device");
            }
        };
        try {
            tts = engine != null
                    ? new TextToSpeech(this, listener, engine)
                    : new TextToSpeech(this, listener);
        } catch (Exception e) {
            ttsEngineLabel = "unavailable";
        }
    }

    private void speakReply(String text) {
        if (soundMuted || text == null || text.isEmpty()) {
            maybeRestartListening();
            return;
        }
        if (isInternetAvailable()) {
            speakOnline(text);
        } else {
            speakDevice(text);
        }
    }

    /** Natural neural voice online: Gemini → ChatGPT → Baidu → device. */
    private void speakOnline(final String text) {
        final String shortText = text.length() > 300 ? text.substring(0, 300) : text;
        new Thread(new Runnable() {
            @Override public void run() {
                final File file = new File(getCacheDir(), "nexus_voice.mp3");
                boolean ok = false;

                // 1) Gemini's speaking model — the key is baked into the APK.
                String gemini = readGeminiKey();
                if (!gemini.isEmpty()) {
                    try { ok = synthesizeGemini(file, gemini, cleanForSpeech(shortText)); }
                    catch (Exception e) { ok = false; }
                }

                // 2) Real ChatGPT voice, if the user saved an OpenAI key.
                if (!ok) {
                    String openai = prefs.getString(PREF_OPENAI_KEY, "").trim();
                    if (!openai.isEmpty()) {
                        try {
                            String json = "{\"model\":\"gpt-4o-mini-tts\",\"voice\":\"alloy\","
                                    + "\"response_format\":\"mp3\",\"input\":"
                                    + JSONObject.quote(cleanForSpeech(shortText)) + "}";
                            byte[] body = json.getBytes("UTF-8");
                            HttpURLConnection conn = (HttpURLConnection) new URL(
                                    "https://api.openai.com/v1/audio/speech").openConnection();
                            conn.setRequestMethod("POST");
                            conn.setDoOutput(true);
                            conn.setConnectTimeout(8000);
                            conn.setReadTimeout(20000);
                            conn.setRequestProperty("Authorization", "Bearer " + openai);
                            conn.setRequestProperty("Content-Type", "application/json");
                            conn.setFixedLengthStreamingMode(body.length);
                            java.io.OutputStream os = conn.getOutputStream();
                            os.write(body);
                            os.close();
                            if (conn.getResponseCode() == 200) {
                                saveStream(file, conn.getInputStream());
                                ok = file.length() > 0;
                            }
                            conn.disconnect();
                        } catch (Exception e) {
                            ok = false;
                        }
                    }
                }

                // 3) Free neural voice fallback (no key needed).
                if (!ok) {
                    try {
                        String q = URLEncoder.encode(cleanForSpeech(shortText), "UTF-8");
                        String url = "https://fanyi.baidu.com/gettts?lan=en&spd=5&pit=5&vol=5"
                                + "&per=0&source=web&text=" + q;
                        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                        conn.setConnectTimeout(8000);
                        conn.setReadTimeout(12000);
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                        if (conn.getResponseCode() == 200) {
                            saveStream(file, conn.getInputStream());
                            ok = file.length() > 0;
                        }
                        conn.disconnect();
                    } catch (Exception e) {
                        ok = false;
                    }
                }

                final boolean played = ok;
                if (played) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() { playVoiceFile(file, shortText); }
                    });
                } else {
                    runOnUiThread(new Runnable() {
                        @Override public void run() { speakDevice(shortText); }
                    });
                }
            }
        }).start();
    }

    /** Call Gemini TTS, decode its base64 PCM audio and write a playable WAV. */
    private boolean synthesizeGemini(File file, String key, String text) throws Exception {
        String json = "{\"contents\":[{\"parts\":[{\"text\":"
                + JSONObject.quote(text) + "}]}],"
                + "\"generationConfig\":{\"responseModalities\":[\"AUDIO\"],"
                + "\"speechConfig\":{\"voiceConfig\":{\"prebuiltVoiceConfig\":"
                + "{\"voiceName\":\"Kore\"}}}}}";
        byte[] body = json.getBytes("UTF-8");
        HttpURLConnection conn = (HttpURLConnection) new URL(
                "https://generativelanguage.googleapis.com/v1beta/models/"
                + "gemini-2.5-flash-preview-tts:generateContent?key=" + key).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(25000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setFixedLengthStreamingMode(body.length);
        java.io.OutputStream os = conn.getOutputStream();
        os.write(body);
        os.close();
        int code = conn.getResponseCode();
        if (code != 200) { conn.disconnect(); return false; }
        byte[] response = readAllBytes(conn.getInputStream());
        conn.disconnect();

        JSONObject root = new JSONObject(new String(response, "UTF-8"));
        String b64 = root.getJSONArray("candidates")
                .getJSONObject(0).getJSONObject("content")
                .getJSONArray("parts").getJSONObject(0)
                .getJSONObject("inlineData").getString("data");
        byte[] pcm = Base64.decode(b64, Base64.DEFAULT);
        if (pcm.length < 44) return false;
        writeWav(file, pcm, 24000);
        return file.length() > 44;
    }

    private byte[] readAllBytes(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return bos.toByteArray();
    }

    /** Wrap raw 16-bit mono PCM samples in a 44-byte WAV header. */
    private void writeWav(File file, byte[] pcm, int sampleRate) throws Exception {
        byte[] h = new byte[44];
        putAscii(h, 0, "RIFF");
        putInt(h, 4, 36 + pcm.length);
        putAscii(h, 8, "WAVE");
        putAscii(h, 12, "fmt ");
        putInt(h, 16, 16);
        putShort(h, 20, 1);                 // PCM
        putShort(h, 22, 1);                 // mono
        putInt(h, 24, sampleRate);
        putInt(h, 28, sampleRate * 2);      // byte rate
        putShort(h, 32, 2);                 // block align
        putShort(h, 34, 16);                // bits per sample
        putAscii(h, 36, "data");
        putInt(h, 40, pcm.length);
        FileOutputStream out = new FileOutputStream(file);
        out.write(h);
        out.write(pcm);
        out.close();
    }

    private void putAscii(byte[] b, int off, String s) {
        for (int i = 0; i < s.length(); i++) b[off + i] = (byte) s.charAt(i);
    }

    private void putInt(byte[] b, int off, int v) {
        b[off] = (byte) v;
        b[off + 1] = (byte) (v >> 8);
        b[off + 2] = (byte) (v >> 16);
        b[off + 3] = (byte) (v >> 24);
    }

    private void putShort(byte[] b, int off, int v) {
        b[off] = (byte) v;
        b[off + 1] = (byte) (v >> 8);
    }

    private void saveStream(File file, InputStream in) throws Exception {
        FileOutputStream out = new FileOutputStream(file);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.close();
        in.close();
    }

    private String cleanForSpeech(String text) {
        return text.replaceAll("[*#_`>\\[\\]()]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void playVoiceFile(final File file, final String fallbackText) {
        try {
            releaseVoicePlayer();
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(file.getAbsolutePath());
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer mp) {
                    setVoiceMood(OrbView.MOOD_LISTEN);
                    releaseVoicePlayer();
                    maybeRestartListening();
                }
            });
            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer mp, int what, int extra) {
                    releaseVoicePlayer();
                    speakDevice(fallbackText);
                    return true;
                }
            });
            mediaPlayer.prepare();
            setVoiceMood(OrbView.MOOD_SPEAK);
            updateVoiceStatus("Nexus AI is speaking…");
            mediaPlayer.start();
        } catch (Exception e) {
            releaseVoicePlayer();
            speakDevice(fallbackText);
        }
    }

    private void releaseVoicePlayer() {
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Exception ignored) { }
            try { mediaPlayer.release(); } catch (Exception ignored) { }
            mediaPlayer = null;
        }
    }

    /** Offline / fallback: the phone's own TTS engine. */
    private void speakDevice(String text) {
        if (!ttsReady || text == null || text.isEmpty()) {
            maybeRestartListening(); // no TTS: go straight back to listening
            return;
        }
        try {
            String shortText = text.length() > 600 ? text.substring(0, 600) : text;
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {
                    setVoiceMood(OrbView.MOOD_SPEAK);
                    updateVoiceStatus("Nexus AI is speaking…");
                }
                @Override public void onDone(String utteranceId) {
                    setVoiceMood(OrbView.MOOD_LISTEN);
                    maybeRestartListening();
                }
                @Override public void onError(String utteranceId) {
                    setVoiceMood(OrbView.MOOD_LISTEN);
                    maybeRestartListening();
                }
                @Override public void onError(String utteranceId, int errorCode) {
                    setVoiceMood(OrbView.MOOD_LISTEN);
                    maybeRestartListening();
                }
            });
            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "ethangpt");
            tts.speak(shortText, TextToSpeech.QUEUE_FLUSH, params, "ethangpt");
        } catch (Exception e) {
            maybeRestartListening();
        }
    }

    // ---------------------- voice agent: listening -------------------------- //
    /** Prefer the device's own recognition service; explicitly skip Google. */
    private ComponentName pickSpeechService() {
        try {
            PackageManager pm = getPackageManager();
            List<ResolveInfo> services = pm.queryIntentServices(
                    new Intent(RecognitionService.SERVICE_INTERFACE), 0);
            if (services != null) {
                for (ResolveInfo info : services) {
                    if (info.serviceInfo == null) continue;
                    String pkg = info.serviceInfo.packageName;
                    if (pkg != null && !pkg.toLowerCase(Locale.ROOT).contains("google")) {
                        sttEngineLabel = pkg;
                        return new ComponentName(pkg, info.serviceInfo.name);
                    }
                }
            }
        } catch (Exception e) { }
        sttEngineLabel = "system default";
        return null;
    }

    private void ensureRecognizer() {
        if (recognizer != null) return;
        ComponentName service = pickSpeechService();
        try {
            recognizer = service != null
                    ? SpeechRecognizer.createSpeechRecognizer(this, service)
                    : SpeechRecognizer.createSpeechRecognizer(this);
        } catch (Exception e) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            sttEngineLabel = "system default";
        }
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                setVoiceMood(OrbView.MOOD_LISTEN);
                updateVoiceStatus("Listening…");
            }
            @Override public void onBeginningOfSpeech() {
                setVoiceMood(OrbView.MOOD_LISTEN);
                updateVoiceStatus("I'm listening…");
            }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() {
                setVoiceMood(OrbView.MOOD_THINK);
                updateVoiceStatus("Processing…");
            }
            @Override public void onError(int error) {
                String message;
                boolean retry;
                switch (error) {
                    case SpeechRecognizer.ERROR_NO_MATCH:
                        message = "Didn't catch that — listening again";
                        retry = true;
                        break;
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        message = "No speech heard — listening again";
                        retry = true;
                        break;
                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                        message = "Microphone permission is needed";
                        retry = false;
                        break;
                    case SpeechRecognizer.ERROR_NETWORK:
                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                        message = "The voice engine needs a connection";
                        retry = true;
                        break;
                    default:
                        message = "Voice error (" + error + ") — listening again";
                        retry = true;
                }
                updateVoiceStatus(message);
                if (retry) {
                    setVoiceMood(OrbView.MOOD_LISTEN);
                    maybeRestartListening();
                } else {
                    setVoiceMood(OrbView.MOOD_IDLE);
                    if (!voiceMode) hideVoiceBar();
                }
            }
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String spoken = matches.get(0);
                    input.setText(spoken);
                    voiceSession = true; // speak the answer back
                    setVoiceMood(OrbView.MOOD_THINK);
                    updateVoiceStatus("Got it — answering…");
                    sendMessage();
                } else {
                    maybeRestartListening();
                }
            }
            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> parts = partialResults.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (parts != null && !parts.isEmpty()) {
                    updateVoiceStatus("Listening: " + parts.get(0));
                }
            }
            @Override public void onEvent(int eventType, Bundle params) { }
        });

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
    }

    private void startListening() {
        if (!voiceMode) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},
                    REQ_RECORD_AUDIO);
            return;
        }
        try {
            ensureRecognizer();
            recognizer.startListening(recognizerIntent);
            setVoiceMood(OrbView.MOOD_LISTEN);
            updateVoiceStatus("Listening…");
        } catch (Exception e) {
            updateVoiceStatus("Could not start listening: " + e.getMessage());
            handler.postDelayed(new Runnable() {
                @Override public void run() { if (voiceMode) startListening(); }
            }, 1200);
        }
    }

    private void maybeRestartListening() {
        if (!voiceMode) return;
        handler.postDelayed(new Runnable() {
            @Override public void run() { if (voiceMode) startListening(); }
        }, 450);
    }

    private void toggleVoiceMode() {
        if (voiceMode) {
            stopVoiceMode();
            return;
        }
        voiceMode = true;
        voiceBar.setVisibility(View.VISIBLE);
        if (voiceOverlay != null) {
            voiceOverlay.setVisibility(View.VISIBLE);
            voiceOverlayText.setText("Starting…");
            setVoiceMood(OrbView.MOOD_LISTEN);
            voiceOverlayOrb.startPulse();
        }
        updateActionButton();
        updateVoiceStatus("Starting the voice agent…");
        startListening();
    }

    private void stopVoiceMode() {
        voiceMode = false;
        voiceSession = false;
        if (recognizer != null) {
            try { recognizer.stopListening(); recognizer.cancel(); } catch (Exception ignored) { }
        }
        if (tts != null) {
            try { tts.stop(); } catch (Exception ignored) { }
        }
        releaseVoicePlayer();
        handler.removeCallbacksAndMessages(null);
        if (voiceOverlayOrb != null) voiceOverlayOrb.stopPulse();
        if (voiceOverlay != null) voiceOverlay.setVisibility(View.GONE);
        hideVoiceBar();
        updateActionButton();
    }

    private void hideVoiceBar() {
        if (voiceBar != null) voiceBar.setVisibility(View.GONE);
    }

    private void updateVoiceStatus(final String text) {
        if (voiceStatusText != null) {
            runOnUiThread(new Runnable() {
                @Override public void run() { voiceStatusText.setText(text); }
            });
        }
        if (voiceOverlayText != null) {
            runOnUiThread(new Runnable() {
                @Override public void run() { voiceOverlayText.setText(text); }
            });
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                     int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening();
            } else {
                updateVoiceStatus("Microphone permission is needed for voice input");
                stopVoiceMode();
            }
        }
    }

    // --------------------------- attach files -------------------------------- //
    /** + button: attach any file from the device to the current chat. */
    private void pickFile() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, REQ_PICK_FILE);
        } catch (Exception e) {
            Toast.makeText(this, "No file picker available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_FILE || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) { }

        String name = displayName(uri);
        byte[] bytes = readUri(uri);
        if (bytes == null) {
            Toast.makeText(this, "Could not read that file", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean binary = false;
        int check = Math.min(bytes.length, 4096);
        for (int i = 0; i < check; i++) {
            if (bytes[i] == 0) { binary = true; break; }
        }
        String body;
        if (binary) {
            body = "(binary file, " + bytes.length + " bytes)";
        } else {
            int limit = Math.min(bytes.length, 20000);
            body = new String(bytes, 0, limit, java.nio.charset.StandardCharsets.UTF_8);
            if (bytes.length > limit) body += "\n… (truncated)";
        }
        removeWelcome();
        addUserMessage("Attached file: " + name + "\n\n" + body);
        input.setText("Here is the attached file '" + name + "'. ");
        input.requestFocus();
        Toast.makeText(this, "File added to chat", Toast.LENGTH_SHORT).show();
    }

    private String displayName(Uri uri) {
        Cursor c = getContentResolver().query(uri, null, null, null, null);
        try {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) {
                    String n = c.getString(i);
                    if (n != null && !n.isEmpty()) return n;
                }
            }
        } catch (Exception ignored) { } finally {
            if (c != null) c.close();
        }
        String last = uri.getLastPathSegment();
        return last != null ? last : "file";
    }

    private byte[] readUri(Uri uri) {
        InputStream in = null;
        try {
            in = getContentResolver().openInputStream(uri);
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) { }
        }
    }

    // --------------------------- action button ------------------------------ //
    private void buildActionButton() {
        actionButton = new FrameLayout(this);
        actionBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] { C_SURFACE_2, C_SURFACE_2 });
        actionBg.setShape(GradientDrawable.OVAL);
        actionButton.setBackground(actionBg);
        int size = dp(44);
        actionButton.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        actionIcon = new IconView(this, IconView.MIC);
        actionIcon.setColor(C_TEXT);
        int is = dp(22);
        actionButton.addView(actionIcon, new FrameLayout.LayoutParams(is, is, Gravity.CENTER));
        actionButton.setClickable(true);
        actionButton.setFocusable(true);
        actionButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onActionTap(); }
        });
        updateActionButton();
    }

    private void onActionTap() {
        if (voiceMode) {
            stopVoiceMode();
            return;
        }
        String text = input.getText().toString().trim();
        if (text.isEmpty()) toggleVoiceMode();
        else sendMessage();
    }

    private void updateActionButton() {
        if (actionIcon == null) return;
        if (voiceMode) {
            actionBg.setColors(new int[] { 0xFFE86A5A, 0xFFC0446A });
            actionIcon.setType(IconView.STOP);
        } else if (input != null && !input.getText().toString().trim().isEmpty()) {
            actionBg.setColors(new int[] { C_GRAD_A, C_GRAD_B });
            actionIcon.setType(IconView.SEND);
        } else {
            actionBg.setColors(new int[] { C_SURFACE_2, C_SURFACE_2 });
            actionIcon.setType(IconView.MIC);
        }
    }

    // --------------------------- voice overlay ------------------------------ //
    /** Full-screen voice mode: a pulsing orb that listens and speaks. */
    private FrameLayout buildVoiceOverlay() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xF21F1E1D);
        overlay.setClickable(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(24), 0, dp(24), 0);

        voiceOverlayOrb = new OrbView(this);
        col.addView(voiceOverlayOrb, new LinearLayout.LayoutParams(dp(132), dp(132)));

        voiceOverlayText = new TextView(this);
        voiceOverlayText.setText("Listening…");
        voiceOverlayText.setTextSize(16);
        voiceOverlayText.setTextColor(C_TEXT);
        voiceOverlayText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = dp(30);
        col.addView(voiceOverlayText, tl);

        TextView hint = new TextView(this);
        hint.setText("Speak naturally — I'll answer out loud");
        hint.setTextSize(12);
        hint.setTextColor(C_TEXT_DIM);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.topMargin = dp(8);
        col.addView(hint, hl);

        TextView end = new TextView(this);
        end.setText("End voice chat");
        end.setTextSize(14);
        end.setTextColor(0xFF1F1E1D);
        end.setPadding(dp(26), dp(10), dp(26), dp(10));
        end.setBackground(rounded(0xFFF5F4EF, 24));
        LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        el.topMargin = dp(36);
        end.setLayoutParams(el);
        end.setClickable(true);
        end.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { stopVoiceMode(); }
        });
        col.addView(end);

        overlay.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        return overlay;
    }

    private void setVoiceMood(final int mood) {
        if (voiceOverlayOrb == null) return;
        runOnUiThread(new Runnable() {
            @Override public void run() { voiceOverlayOrb.setMood(mood); }
        });
    }

    // --------------------------- chat persistence --------------------------- //
    private void loadChats() {
        String json = prefs.getString(PREF_CHATS, "");
        if (!restoreFromJson(json)) {
            // Very old data or a fresh install: fall back to the shared-storage
            // backup so chats survive an uninstall/reinstall.
            String backup = ChatBackup.load(this);
            if (restoreFromJson(backup)) {
                saveChats();
                Toast.makeText(this, "Chats restored from backup", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /** Parse a saved-chats JSON blob (from prefs or a restored backup). */
    private void parseChats(String json) {
        chats.clear();
        if (json == null || json.isEmpty()) return;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                ChatRecord c = new ChatRecord();
                c.id = o.optString("id", UUID.randomUUID().toString());
                c.title = o.optString("title", "New chat");
                c.updated = o.optLong("updated", 0);
                JSONArray turns = o.optJSONArray("turns");
                if (turns != null) {
                    for (int j = 0; j < turns.length(); j++) {
                        JSONArray t = turns.getJSONArray(j);
                        c.turns.add(new String[]{t.optString(0), t.optString(1)});
                    }
                }
                chats.add(c);
            }
        } catch (Exception e) {
            chats.clear();
        }
        Collections.sort(chats, new Comparator<ChatRecord>() {
            @Override public int compare(ChatRecord a, ChatRecord b) {
                return Long.compare(b.updated, a.updated);
            }
        });
        activeChatId = prefs.getString(PREF_ACTIVE_CHAT, "");
    }

    /** Serialise all chats (and the active id) to JSON. */
    private String chatsToJson() {
        try {
            JSONArray arr = new JSONArray();
            for (ChatRecord c : chats) {
                JSONObject o = new JSONObject();
                o.put("id", c.id);
                o.put("title", c.title);
                o.put("updated", c.updated);
                JSONArray turns = new JSONArray();
                for (String[] t : c.turns) {
                    JSONArray pair = new JSONArray();
                    pair.put(t[0]);
                    pair.put(t[1]);
                    turns.put(pair);
                }
                o.put("turns", turns);
                arr.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("active", activeChatId == null ? "" : activeChatId);
            root.put("chats", arr);
            return root.toString();
        } catch (Exception e) {
            return "{\"active\":\"\",\"chats\":[]}";
        }
    }

    /** Apply a backup blob: replaces chats and restores the active id. */
    private boolean restoreFromJson(String json) {
        if (json == null || json.trim().isEmpty()) return false;
        String trimmed = json.trim();
        try {
            if (trimmed.startsWith("[")) {
                // Old format / plain backups: a bare array of chats.
                parseChats(trimmed);
            } else {
                JSONObject root = new JSONObject(trimmed);
                JSONArray arr = root.optJSONArray("chats");
                if (arr != null) parseChats(arr.toString());
                String active = root.optString("active", "");
                if (!active.isEmpty() && findChat(active) != null) activeChatId = active;
            }
            return !chats.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private void saveChats() {
        try {
            String json = chatsToJson();
            prefs.edit()
                    .putString(PREF_CHATS, json)
                    .putString(PREF_ACTIVE_CHAT, activeChatId == null ? "" : activeChatId)
                    .apply();
            // Keep the on-device backup in sync in the background.
            final String snapshot = json;
            new Thread(new Runnable() {
                @Override public void run() { ChatBackup.save(MainActivity.this, snapshot); }
            }).start();
        } catch (Exception ignored) { }
    }

    private ChatRecord findChat(String id) {
        if (id == null) return null;
        for (ChatRecord c : chats) {
            if (id.equals(c.id)) return c;
        }
        return null;
    }

    private String makeTitle(String text) {
        if (text == null) return "New chat";
        String line = text.trim();
        int newline = line.indexOf('\n');
        if (newline > 0) line = line.substring(0, newline);
        if (line.isEmpty()) return "New chat";
        if (line.length() > 38) line = line.substring(0, 38).trim() + "…";
        return line;
    }

    private void persistCurrentChat() {
        if (bot == null) return;
        ChatRecord active = findChat(activeChatId);
        if (active == null) {
            active = new ChatRecord();
            active.id = UUID.randomUUID().toString();
            chats.add(0, active);
            activeChatId = active.id;
        }
        active.turns.clear();
        for (String[] t : bot.history()) {
            active.turns.add(new String[]{t[0], t[1]});
        }
        for (String[] t : active.turns) {
            if ("user".equals(t[0])) {
                active.title = makeTitle(t[1]);
                break;
            }
        }
        active.updated = System.currentTimeMillis();
        saveChats();
    }

    // --------------------------- backup / share ----------------------------- //
    private void backupChatsNow() {
        persistCurrentChat();
        String json = chatsToJson();
        ChatBackup.save(this, json);
        Toast.makeText(this, "Chats backed up to " + ChatBackup.location(),
                Toast.LENGTH_LONG).show();
    }

    private void restoreChatsNow() {
        final String json = ChatBackup.load(this);
        if (json == null || json.trim().isEmpty()) {
            Toast.makeText(this, "No backup file found at " + ChatBackup.location(),
                    Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Restore chats?")
                .setMessage("This will replace the chats currently on this device with "
                        + "the saved backup. Continue?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Restore", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (restoreFromJson(json)) {
                            saveChats();
                            syncBotHistory();
                            renderChat(findChat(activeChatId) != null
                                    ? findChat(activeChatId) : chats.get(0));
                            refreshDrawer();
                            Toast.makeText(MainActivity.this, "Chats restored",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "Backup could not be read",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }

    private void shareChatsNow() {
        persistCurrentChat();
        ChatBackup.share(this, chatsToJson());
    }

    private void initChats() {
        loadChats();
        ChatRecord active = findChat(activeChatId);
        if (active == null && !chats.isEmpty()) {
            active = chats.get(0);
            activeChatId = active.id;
        }
        if (active == null) {
            active = new ChatRecord();
            active.id = UUID.randomUUID().toString();
            active.title = "New chat";
            active.updated = System.currentTimeMillis();
            chats.add(active);
            activeChatId = active.id;
            saveChats();
        }
        renderChat(active);
        refreshDrawer();
    }

    private void renderChat(ChatRecord c) {
        activeChatId = c.id;
        chatList.removeAllViews();
        welcomeView = null;
        if (c.turns.isEmpty()) {
            addWelcome();
        } else {
            for (String[] t : c.turns) {
                if ("user".equals(t[0])) addUserMessage(t[1]);
                else addAssistantMessage(t[1], false);
            }
        }
        syncBotHistory();
        input.setText("");
        voiceSession = false;
        updateActionButton();
        scrollToBottom();
    }

    private void addWelcome() {
        FrameLayout holder = new FrameLayout(this);
        holder.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(20), dp(8), dp(20), dp(16));

        OrbView orb = new OrbView(this);
        orb.setClickable(true);
        orb.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleVoiceMode(); }
        });
        col.addView(orb, new LinearLayout.LayoutParams(dp(72), dp(72)));

        TextView hi = new TextView(this);
        hi.setText("What can I help with?");
        hi.setTextSize(22);
        hi.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        hi.setTextColor(C_TEXT);
        hi.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.topMargin = dp(18);
        col.addView(hi, hl);

        TextView sub = new TextView(this);
        sub.setText("Nexus AI · private, offline-ready assistant");
        sub.setTextSize(12);
        sub.setTextColor(C_TEXT_DIM);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subl.topMargin = dp(6);
        col.addView(sub, subl);

        // Suggestion cards, tappable to prefill the prompt box.
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gl.topMargin = dp(22);
        grid.setLayoutParams(gl);

        grid.addView(suggestionRow(
                suggestion(IconView.SPARKLE, "Create image", "Create an image of "),
                suggestion(IconView.CODE, "Write code", "Write code that ")));

        LinearLayout r2 = suggestionRow(
                suggestion(IconView.SPARKLE, "Explain topic", "Explain "),
                suggestion(IconView.CODE, "Summarize text", "Summarize this text: "));
        LinearLayout.LayoutParams r2lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r2lp.topMargin = dp(10);
        r2.setLayoutParams(r2lp);
        grid.addView(r2);
        col.addView(grid);

        // Claude Code-style slash-command hint; opens the Skills menu.
        TextView skillsHint = new TextView(this);
        skillsHint.setText("Skills: /review  /fix  /test  /docs     Browse all →");
        skillsHint.setTextSize(12);
        skillsHint.setTextColor(C_ACCENT);
        skillsHint.setPadding(dp(12), dp(8), dp(12), dp(8));
        skillsHint.setBackground(rounded(C_SURFACE, 18));
        LinearLayout.LayoutParams shl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shl.topMargin = dp(12);
        skillsHint.setLayoutParams(shl);
        skillsHint.setClickable(true);
        skillsHint.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openDrawer(); }
        });
        col.addView(skillsHint);

        holder.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        chatList.addView(holder);
        welcomeView = holder;
    }

    /** One suggestion card; tapping it fills the prompt box. */
    private View suggestion(int iconType, String label, final String prompt) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(11), dp(10), dp(8), dp(10));
        GradientDrawable bg = gradientPill(C_PILL_A, C_PILL_B,16);
        bg.setStroke(dp(1), C_PILL_STROKE);
        card.setBackground(bg);
        IconView ic = new IconView(this, iconType);
        ic.setColor(C_ACCENT);
        card.addView(ic, new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(12);
        t.setSingleLine(true);
        t.setTextColor(C_TEXT);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.leftMargin = dp(9);
        t.setLayoutParams(tl);
        card.addView(t);
        card.setClickable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                input.setText(prompt);
                input.setSelection(prompt.length());
                input.requestFocus();
            }
        });
        return card;
    }

    private LinearLayout suggestionRow(View left, View right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l.setMargins(0, 0, dp(5), 0);
        left.setLayoutParams(l);
        LinearLayout.LayoutParams r = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        r.setMargins(dp(5), 0, 0, 0);
        right.setLayoutParams(r);
        row.addView(left);
        row.addView(right);
        return row;
    }

    private void removeWelcome() {
        if (welcomeView != null) {
            chatList.removeView(welcomeView);
            welcomeView = null;
        }
    }

    private void syncBotHistory() {
        if (bot == null) return;
        bot.clearHistory();
        ChatRecord c = findChat(activeChatId);
        if (c != null) {
            for (String[] t : c.turns) bot.addTurn(t[0], t[1]);
        }
    }

    // ------------------------------- drawer --------------------------------- //
    private LinearLayout buildDrawer() {
        LinearLayout d = new LinearLayout(this);
        d.setOrientation(LinearLayout.VERTICAL);
        d.setBackgroundColor(C_DRAWER);
        d.setPadding(0, statusBarHeight() + dp(10), 0, navBarHeight() + dp(10));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(6), dp(16), dp(14));

        FrameLayout avatar = new FrameLayout(this);
        GradientDrawable ab = new GradientDrawable();
        ab.setShape(GradientDrawable.OVAL);
        ab.setColor(0xFF4A443C);
        avatar.setBackground(ab);
        avatar.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        IconView person = new IconView(this, IconView.PERSON);
        person.setColor(C_ACCENT);
        avatar.addView(person, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
        top.addView(avatar);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(12);
        titles.setLayoutParams(tlp);
        TextView name = new TextView(this);
        name.setText("Nexus AI");
        name.setTextSize(16);
        name.setTextColor(C_TEXT);
        titles.addView(name);
        TextView sub = new TextView(this);
        sub.setText("Built-in · AUTO");
        sub.setTextSize(11);
        sub.setTextColor(C_TEXT_DIM);
        titles.addView(sub);
        top.addView(titles);

        d.addView(top);

        // One scrollable menu so the Skills list fits on small screens too.
        ScrollView menuScroll = new ScrollView(this);
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menuScroll.addView(menu);
        menuScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        d.addView(menuScroll);

        menu.addView(menuRow(IconView.SPARKLE, "Code ideas", new View.OnClickListener() {
            @Override public void onClick(View v) { closeDrawer(); sendCodeIdeas(); }
        }));
        menu.addView(menuRow(IconView.MIC, "Voice agent", new View.OnClickListener() {
            @Override public void onClick(View v) { closeDrawer(); toggleVoiceMode(); }
        }));

        menu.addView(sectionLabel("Chat"));
        menu.addView(menuRow(IconView.PLUS, "New chat", new View.OnClickListener() {
            @Override public void onClick(View v) { newChat(); }
        }));

        // Claude Code-style slash-command skills. Each one pre-fills a real
        // prompt in the chat box — the user can edit it before sending.
        menu.addView(sectionLabel("Skills"));
        menu.addView(skillRow("/review", "Find bugs and improvements", IconView.CODE,
                "Review the following code for bugs, security issues and style problems. "
                        + "For each issue, explain why it matters and show the fix:\n\n"));
        menu.addView(skillRow("/explain", "Explain code step by step", IconView.CODE,
                "Explain the following code in plain language, step by step, including "
                        + "the purpose of each function:\n\n"));
        menu.addView(skillRow("/fix", "Find and fix a bug", IconView.CODE,
                "Find the bug in the following code, explain the root cause and provide "
                        + "a corrected version:\n\n"));
        menu.addView(skillRow("/test", "Write unit tests", IconView.CODE,
                "Write a complete set of unit tests for the following code, covering "
                        + "normal cases and edge cases:\n\n"));
        menu.addView(skillRow("/refactor", "Clean up and optimise", IconView.CODE,
                "Refactor the following code to be cleaner and more efficient. Keep the "
                        + "behaviour identical and explain your changes:\n\n"));
        menu.addView(skillRow("/docs", "Write documentation", IconView.CODE,
                "Write clear documentation for the following code: what it does, how to "
                        + "use it, and a short example:\n\n"));
        menu.addView(skillRow("/summarize", "Condense long text", IconView.CHAT,
                "Summarize the following text concisely, keeping all the key points:\n\n"));
        menu.addView(skillRow("/brainstorm", "Generate creative ideas", IconView.SPARKLE,
                "Brainstorm creative ideas for the following topic. Give me at least 8 "
                        + "varied suggestions, each with a one-line description:\n\n"));

        menu.addView(sectionLabel("Backup & share"));
        menu.addView(menuRow(IconView.SPARKLE, "Backup chats to file", new View.OnClickListener() {
            @Override public void onClick(View v) { closeDrawer(); backupChatsNow(); }
        }));
        menu.addView(menuRow(IconView.CHAT, "Restore chats from file", new View.OnClickListener() {
            @Override public void onClick(View v) { closeDrawer(); restoreChatsNow(); }
        }));
        menu.addView(menuRow(IconView.SEND, "Share chats", new View.OnClickListener() {
            @Override public void onClick(View v) { closeDrawer(); shareChatsNow(); }
        }));

        menu.addView(sectionLabel("Chat history"));

        drawerList = new LinearLayout(this);
        drawerList.setOrientation(LinearLayout.VERTICAL);
        drawerList.setPadding(dp(8), 0, dp(8), 0);
        menu.addView(drawerList);

        TextView note = new TextView(this);
        note.setText("Saved on this device · backup: " + ChatBackup.location());
        note.setTextSize(12);
        note.setTextColor(C_TEXT_DIM);
        note.setGravity(Gravity.CENTER);
        note.setPadding(dp(16), dp(8), dp(16), dp(10));
        d.addView(note);

        TextView newPill = new TextView(this);
        newPill.setText("New chat");
        newPill.setTextSize(14);
        newPill.setTextColor(0xFF1F1E1D);
        newPill.setPadding(dp(30), dp(10), dp(30), dp(10));
        newPill.setBackground(rounded(0xFFF5F4EF, 24));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nl.gravity = Gravity.CENTER_HORIZONTAL;
        nl.bottomMargin = dp(4);
        newPill.setLayoutParams(nl);
        newPill.setClickable(true);
        newPill.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { newChat(); }
        });
        d.addView(newPill);
        return d;
    }

    private View menuRow(int iconType, String label, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(13), dp(20), dp(13));
        IconView ic = new IconView(this, iconType);
        ic.setColor(C_TEXT_DIM);
        row.addView(ic, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(16);
        tv.setTextColor(C_TEXT);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.leftMargin = dp(14);
        row.addView(tv, tl);
        row.setClickable(true);
        row.setOnClickListener(listener);
        return row;
    }

    /** Claude Code-style skill: a slash command, a short hint, and a real prompt. */
    private View skillRow(final String command, String description, int iconType,
                          final String prompt) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(9), dp(20), dp(9));

        IconView ic = new IconView(this, iconType);
        ic.setColor(C_ACCENT);
        row.addView(ic, new LinearLayout.LayoutParams(dp(20), dp(20)));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bp.leftMargin = dp(12);
        box.setLayoutParams(bp);

        TextView cmd = new TextView(this);
        cmd.setText(command);
        cmd.setTextSize(14);
        cmd.setTextColor(C_ACCENT);
        box.addView(cmd);

        TextView desc = new TextView(this);
        desc.setText(description);
        desc.setTextSize(11);
        desc.setTextColor(C_TEXT_DIM);
        box.addView(desc);
        row.addView(box);

        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { applySkill(prompt); }
        });
        return row;
    }

    /** Load a skill's prompt into the chat box so the user can edit before sending. */
    private void applySkill(String prompt) {
        closeDrawer();
        selectTab(0);
        input.setText(prompt);
        input.setSelection(prompt.length());
        input.requestFocus();
        Toast.makeText(this, "Skill loaded into chat", Toast.LENGTH_SHORT).show();
    }

    private TextView sectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(C_TEXT_DIM);
        tv.setPadding(dp(20), dp(14), dp(20), dp(6));
        return tv;
    }

    private void refreshDrawer() {
        if (drawerList == null) return;
        drawerList.removeAllViews();
        for (final ChatRecord c : chats) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int pad = dp(10);
            row.setPadding(pad, pad, pad, pad);
            boolean active = c.id.equals(activeChatId);
            row.setBackground(rounded(active ? C_SURFACE : C_DRAWER, 12));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.setMargins(0, 0, 0, dp(2));
            row.setLayoutParams(rp);

            LinearLayout textBox = new LinearLayout(this);
            textBox.setOrientation(LinearLayout.VERTICAL);
            textBox.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView name = new TextView(this);
            name.setText(c.title);
            name.setTextSize(14);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setTextColor(active ? C_TEXT : 0xFFD8D3CB);
            textBox.addView(name);

            TextView when = new TextView(this);
            when.setText(DateUtils.getRelativeTimeSpanString(
                    c.updated, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
            when.setTextSize(11);
            when.setTextColor(C_TEXT_DIM);
            textBox.addView(when);
            row.addView(textBox);

            FrameLayout del = iconButton(IconView.TRASH, 32, C_DRAWER, C_TEXT_DIM);
            del.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { confirmDelete(c); }
            });
            row.addView(del);

            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openChat(c); }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) { confirmDelete(c); return true; }
            });
            drawerList.addView(row);
        }
    }

    private void openDrawer() {
        if (drawerOpen) return;
        refreshDrawer();
        drawerOpen = true;
        scrim.setVisibility(View.VISIBLE);
        drawer.setTranslationX(-drawerWidth);
        drawer.setVisibility(View.VISIBLE);
        drawer.animate().translationX(0).setDuration(220).setListener(null);
        scrim.animate().alpha(1f).setDuration(220);
    }

    private void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        drawer.animate().translationX(-drawerWidth).setDuration(200).setListener(null);
        scrim.animate().alpha(0f).setDuration(200);
        scrim.setVisibility(View.GONE);
    }

    private void openChat(ChatRecord c) {
        persistCurrentChat();
        renderChat(c);
        saveChats();
        refreshDrawer();
        closeDrawer();
    }

    private void newChat() {
        persistCurrentChat();
        ChatRecord c = new ChatRecord();
        c.id = UUID.randomUUID().toString();
        c.title = "New chat";
        c.updated = System.currentTimeMillis();
        chats.add(0, c);
        activeChatId = c.id;
        saveChats();
        renderChat(c);
        refreshDrawer();
        closeDrawer();
        Toast.makeText(this, "New chat", Toast.LENGTH_SHORT).show();
    }

    private void confirmDelete(final ChatRecord c) {
        new AlertDialog.Builder(this)
                .setTitle("Delete chat?")
                .setMessage("Delete “" + c.title + "”? This cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        chats.remove(c);
                        if (c.id.equals(activeChatId)) {
                            if (chats.isEmpty()) {
                                ChatRecord fresh = new ChatRecord();
                                fresh.id = UUID.randomUUID().toString();
                                fresh.title = "New chat";
                                fresh.updated = System.currentTimeMillis();
                                chats.add(fresh);
                                activeChatId = fresh.id;
                            } else {
                                activeChatId = chats.get(0).id;
                            }
                            if (bot != null) bot.clearHistory();
                            renderChat(findChat(activeChatId));
                        }
                        saveChats();
                        refreshDrawer();
                    }
                })
                .show();
    }

    // ----------------------------- model load ------------------------------ //
    /** A tiny empty model used only to keep conversation history when offline
     *  mode is disabled on low-RAM devices. It never generates answers. */
    private static ChatBot emptyHistoryBot() {
        String[] toks = new String[] { "<pad>", "<bos>", "<eos>", "<unk>" };
        BPETokenizer tok = new BPETokenizer(toks, new int[0][], 0, 1, 2, 3);
        java.util.List<java.util.HashMap<String, java.util.HashMap<Integer, Integer>>> tables =
                new ArrayList<>();
        tables.add(new java.util.HashMap<String, java.util.HashMap<Integer, Integer>>());
        NGramModel tiny = new NGramModel(tok, 1, tables);
        return new ChatBot(tiny);
    }

    private void loadModel() {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    if (!offlineAllowed) {
                        // Skip the memory-heavy offline model on low-RAM devices.
                        // Online chat still needs a ChatBot for history tracking.
                        bot = emptyHistoryBot();
                        bot.configure(60, 0.8, 10, 0.9);
                        ready = true;
                        syncBotHistory();
                        runOnUiThread(new Runnable() {
                            @Override public void run() { updateActionButton(); }
                        });
                        return;
                    }
                    bundle = ModelLoader.load(getAssets().open("model.txt"));
                    bot = new ChatBot(bundle.model);
                    bot.configure(60, 0.8, 10, 0.9);
                    ready = true;
                    syncBotHistory();
                    runOnUiThread(new Runnable() {
                        @Override public void run() { updateActionButton(); }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            addAssistantMessage("Could not load the built-in model: "
                                    + e.getMessage(), false);
                        }
                    });
                }
            }
        }).start();
    }

    // ----------------------------- chat bubbles ---------------------------- //
    private void addUserMessage(final String text) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                removeWelcome();
                TextView bubble = new TextView(MainActivity.this);
                bubble.setText(text);
                bubble.setTextSize(15);
                bubble.setTextColor(C_TEXT);
                bubble.setPadding(dp(16), dp(10), dp(16), dp(10));
                bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.8));
                bubble.setBackground(gradientPill(0xFF4A3A2E, 0xFF3A2E28, 20));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.gravity = Gravity.END;
                lp.setMargins(dp(64), dp(8), 0, dp(8));
                bubble.setLayoutParams(lp);
                chatList.addView(bubble);
                scrollToBottom();
            }
        });
    }

    private void addAssistantMessage(final String text, final boolean speak) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                removeWelcome();
                LinearLayout wrap = new LinearLayout(MainActivity.this);
                wrap.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                wp.setMargins(0, dp(12), dp(24), dp(4));
                wrap.setLayoutParams(wp);

                TextView label = new TextView(MainActivity.this);
                label.setText("Nexus AI");
                label.setTextSize(11);
                label.setTextColor(C_TEXT_DIM);
                wrap.addView(label);

                TextView body = new TextView(MainActivity.this);
                body.setText(text);
                body.setTextSize(15);
                body.setTextColor(C_TEXT);
                body.setLineSpacing(dp(3), 1.12f);
                body.setTextIsSelectable(true);
                wrap.addView(body);

                addMessageActions(wrap, text);

                chatList.addView(wrap);
                scrollToBottom();
                if (speak) speakReply(text);
            }
        });
    }

    /** Generate an image from the text in the chat box (free, no key needed). */
    private void generateImage() {
        String prompt = input.getText().toString().trim();
        if (prompt.isEmpty()) {
            Toast.makeText(this, "Describe the image in the chat box, then tap Image",
                    Toast.LENGTH_LONG).show();
            return;
        }
        final String p = prompt;
        addUserMessage(p);
        input.setText("");
        addAssistantMessage("Generating an image for: " + p, false);
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final byte[] data = ImageGenerator.generate(p);
                    final Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                    final Uri uri = NexusFiles.save(MainActivity.this,
                            "Nexus-image-" + System.currentTimeMillis() + ".jpg",
                            "image/jpeg", "Images", data);
                    runOnUiThread(new Runnable() {
                        @Override public void run() { addImageMessage(bmp, uri); }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            addAssistantMessage("Image generation failed: " + e.getMessage(),
                                    false);
                        }
                    });
                }
            }
        }, "nexus-image").start();
    }

    private void addImageMessage(final Bitmap bmp, final Uri uri) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                removeWelcome();
                LinearLayout wrap = new LinearLayout(MainActivity.this);
                wrap.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                wp.setMargins(0, dp(12), dp(24), dp(4));
                wrap.setLayoutParams(wp);

                TextView label = new TextView(MainActivity.this);
                label.setText("Nexus AI · generated image");
                label.setTextSize(11);
                label.setTextColor(C_TEXT_DIM);
                wrap.addView(label);

                if (bmp != null) {
                    ImageView img = new ImageView(MainActivity.this);
                    img.setImageBitmap(bmp);
                    img.setAdjustViewBounds(true);
                    img.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    img.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.8));
                    img.setMaxHeight(dp(320));
                    wrap.addView(img);
                }

                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, dp(4), 0, dp(2));
                row.addView(textAction("Share image", new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        NexusFiles.share(MainActivity.this, uri, "image/jpeg", "Nexus AI image");
                    }
                }));
                row.addView(textAction("Open image", new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        try {
                            Intent i = new Intent(Intent.ACTION_VIEW);
                            i.setDataAndType(uri, "image/jpeg");
                            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(i);
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "No image viewer found",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                }));
                wrap.addView(row);
                chatList.addView(wrap);
                scrollToBottom();
            }
        });
    }

    /** Small action buttons under each assistant reply: copy, save, share, code, preview. */
    private void addMessageActions(final LinearLayout wrap, final String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(2));

        row.addView(textAction("Copy", new View.OnClickListener() {
            @Override public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager)
                        getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Nexus reply", text));
                Toast.makeText(MainActivity.this, "Reply copied", Toast.LENGTH_SHORT).show();
            }
        }));
        row.addView(textAction("Save file", new View.OnClickListener() {
            @Override public void onClick(View v) {
                String name = "Nexus-reply-" + System.currentTimeMillis() + ".txt";
                Uri uri = NexusFiles.save(MainActivity.this, name, "text/plain", "Files",
                        text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                Toast.makeText(MainActivity.this,
                        uri == null ? "Could not save file"
                                : "Saved to " + NexusFiles.textLocation("Files"),
                        Toast.LENGTH_SHORT).show();
            }
        }));
        row.addView(textAction("Share", new View.OnClickListener() {
            @Override public void onClick(View v) {
                String name = "Nexus-reply-" + System.currentTimeMillis() + ".txt";
                Uri uri = NexusFiles.save(MainActivity.this, name, "text/plain", "Files",
                        text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                NexusFiles.share(MainActivity.this, uri, "text/plain", "Nexus AI reply");
            }
        }));
        if (text.contains("```")) {
            row.addView(textAction("Open in IDE", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    CodeIdeView.openCode(extractCode(text));
                    selectTab(2);
                }
            }));
        }
        if (text.contains("<!doctype") || text.contains("<html")) {
            row.addView(textAction("Preview", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    java.io.File dir = new java.io.File(getFilesDir(), "preview");
                    dir.mkdirs();
                    writeString(new java.io.File(dir, "index.html"), extractCode(text));
                    String url = LocalPreviewServer.start(dir);
                    LocalBrowserView.loadUrl(url);
                    selectTab(3);
                }
            }));
        }
        wrap.addView(row);
    }

    /** Pull the first fenced code block out of a reply (or the whole text). */
    private String extractCode(String text) {
        int s = text.indexOf("```");
        if (s < 0) return text;
        int nl = text.indexOf('\n', s + 3);
        int e = text.indexOf("```", nl < 0 ? s + 3 : nl + 1);
        if (nl < 0) return text;
        if (e < 0) e = text.length();
        return text.substring(nl + 1, e).trim();
    }

    private void writeString(java.io.File f, String s) {
        try {
            java.io.FileOutputStream out = new java.io.FileOutputStream(f);
            out.write(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.close();
        } catch (Exception ignored) { }
    }

    private TextView textAction(String label, View.OnClickListener listener) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(11);
        t.setTextColor(C_ACCENT);
        t.setPadding(dp(9), dp(5), dp(9), dp(5));
        GradientDrawable actionBgLocal = gradientPill(C_PILL_A, C_PILL_B,12);
        actionBgLocal.setStroke(dp(1), C_PILL_STROKE);
        t.setBackground(actionBgLocal);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        t.setLayoutParams(lp);
        t.setClickable(true);
        t.setOnClickListener(listener);
        return t;
    }

    private void scrollToBottom() {
        chatScroll.post(new Runnable() {
            @Override public void run() { chatScroll.fullScroll(View.FOCUS_DOWN); }
        });
    }

    // ------------------------------- sending ------------------------------- //
    private void sendMessage() {
        if (!ready || bot == null) {
            Toast.makeText(this, "The model is still loading", Toast.LENGTH_SHORT).show();
            maybeRestartListening();
            return;
        }
        final String raw = input.getText().toString().trim();
        if (raw.isEmpty()) {
            Toast.makeText(this, "Type something first", Toast.LENGTH_SHORT).show();
            maybeRestartListening();
            return;
        }
        final String text = expandSkill(raw);
        input.setText("");
        addUserMessage(raw);
        if (isInternetAvailable()) {
            sendOnline(text);
        } else if (offlineAllowed) {
            sendOffline(text);
        } else {
            addAssistantMessage("Offline mode is disabled on this device (less than "
                    + "8 GB RAM). Connect to the internet to chat.", voiceSession);
            voiceSession = false;
            updateActionButton();
        }
    }

    /** Expand a typed Claude Code-style slash command into its full prompt. */
    private String expandSkill(String text) {
        if (!text.startsWith("/")) return text;
        int split = text.indexOf(' ');
        String cmd = split < 0 ? text : text.substring(0, split);
        String rest = split < 0 ? "" : text.substring(split + 1).trim();
        String prefix;
        if (cmd.equals("/review")) {
            prefix = "Review the following code for bugs, security issues and style "
                    + "problems. For each issue, explain why it matters and show the fix:\n\n";
        } else if (cmd.equals("/explain")) {
            prefix = "Explain the following code in plain language, step by step, "
                    + "including the purpose of each function:\n\n";
        } else if (cmd.equals("/fix")) {
            prefix = "Find the bug in the following code, explain the root cause and "
                    + "provide a corrected version:\n\n";
        } else if (cmd.equals("/test")) {
            prefix = "Write a complete set of unit tests for the following code, covering "
                    + "normal cases and edge cases:\n\n";
        } else if (cmd.equals("/refactor")) {
            prefix = "Refactor the following code to be cleaner and more efficient. Keep "
                    + "the behaviour identical and explain your changes:\n\n";
        } else if (cmd.equals("/docs")) {
            prefix = "Write clear documentation for the following code: what it does, how "
                    + "to use it, and a short example:\n\n";
        } else if (cmd.equals("/summarize")) {
            prefix = "Summarize the following text concisely, keeping all the key points:\n\n";
        } else if (cmd.equals("/brainstorm")) {
            prefix = "Brainstorm creative ideas for the following topic. Give me at least "
                    + "8 varied suggestions, each with a one-line description:\n\n";
        } else {
            return text;
        }
        return prefix + rest;
    }

    private void sendCodeIdeas() {
        if (!ready || bot == null) {
            Toast.makeText(this, "The model is still loading", Toast.LENGTH_SHORT).show();
            return;
        }
        String prompt = "Give me 5 creative coding project ideas, each with a "
                + "one-sentence description.";
        input.setText(prompt);
        voiceSession = false;
        sendMessage();
    }

    private void sendOffline(final String text) {
        new Thread(new Runnable() {
            @Override public void run() {
                final String reply = bot.respond(text);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        addAssistantMessage(reply, voiceSession);
                        voiceSession = false;
                        updateActionButton();
                        persistCurrentChat();
                        refreshDrawer();
                    }
                });
            }
        }).start();
    }

    private void sendOnline(final String text) {
        final String key = prefs.getString(PREF_KEY, "");
        if (key.trim().isEmpty()) {
            if (offlineAllowed) {
                addAssistantMessage("No online key is available, so I'm using the "
                        + "built-in model.", voiceSession);
                sendOffline(text);
            } else {
                addAssistantMessage("No online key is available, and offline mode is "
                        + "disabled on this device (less than 8 GB RAM).", voiceSession);
                voiceSession = false;
                updateActionButton();
            }
            return;
        }
        final int provider = prefs.getInt(PREF_PROVIDER, 0);
        final String modelName = prefs.getString(PREF_MODEL, "");

        new Thread(new Runnable() {
            @Override public void run() {
                final ArrayList<String[]> history = new ArrayList<>(bot.history());
                String answer = null;
                String failure = null;
                try {
                    answer = OnlineClient.chat(OnlineClient.providerId(provider), key,
                            modelName, history, text);
                    bot.addTurn("user", text);
                    bot.addTurn("assistant", answer);
                } catch (Exception e) {
                    failure = e.getMessage();
                }

                if (answer != null) {
                    final String ok = answer;
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            addAssistantMessage(ok, voiceSession);
                            voiceSession = false;
                            updateActionButton();
                            persistCurrentChat();
                            refreshDrawer();
                        }
                    });
                    return;
                }

                final String why = failure == null ? "unknown error" : failure;
                if (!offlineAllowed) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            addAssistantMessage("Online request failed (" + why
                                    + "). Offline mode is disabled on this device "
                                    + "(less than 8 GB RAM), so there is no fallback.",
                                    voiceSession);
                            voiceSession = false;
                            updateActionButton();
                            persistCurrentChat();
                            refreshDrawer();
                        }
                    });
                    return;
                }
                final String offline = bot.respond(text);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        addAssistantMessage("Online request failed (" + why
                                + "). Switched to the built-in model:\n" + offline,
                                voiceSession);
                        voiceSession = false;
                        updateActionButton();
                        persistCurrentChat();
                        refreshDrawer();
                    }
                });
            }
        }).start();
    }

    // --------------------------- voice key --------------------------------- //
    /** The user pastes their own OpenAI key here; Buddy never types it. */
    private void showVoiceKeyDialog() {
        final EditText field = new EditText(this);
        field.setHint("sk-...");
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setSingleLine(true);
        field.setText(prefs.getString(PREF_OPENAI_KEY, ""));
        new AlertDialog.Builder(this)
                .setTitle("OpenAI voice key")
                .setMessage("Paste your OpenAI API key. It is stored only on this device "
                        + "and used for the ChatGPT voice model.")
                .setView(field)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        prefs.edit().putString(PREF_OPENAI_KEY,
                                field.getText().toString().trim()).apply();
                        Toast.makeText(MainActivity.this,
                                prefs.getString(PREF_OPENAI_KEY, "").isEmpty()
                                        ? "Voice key cleared" : "ChatGPT voice enabled",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    // --------------------------- online settings ---------------------------- //
    /** Provider (including DeepSeek), API key and model, all stored locally. */
    private void showOnlineSettings() {
        final int[] provider = new int[] { prefs.getInt(PREF_PROVIDER, 0) };
        if (provider[0] < 0 || provider[0] >= OnlineClient.PROVIDER_IDS.length) provider[0] = 0;

        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), dp(8));
        scroll.addView(box);

        TextView h = new TextView(this);
        h.setText("Online provider");
        h.setTextSize(14);
        h.setTextColor(C_ACCENT);
        box.addView(h);

        final Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, OnlineClient.PROVIDER_LABELS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(provider[0]);
        box.addView(spinner);

        final TextView note = new TextView(this);
        note.setTextSize(12);
        note.setTextColor(C_TEXT_DIM);
        note.setPadding(0, dp(8), 0, dp(10));
        box.addView(note);

        TextView keyLabel = new TextView(this);
        keyLabel.setText("API key (stored only on this device)");
        keyLabel.setTextColor(C_TEXT);
        keyLabel.setPadding(0, dp(4), 0, dp(2));
        box.addView(keyLabel);
        final EditText key = new EditText(this);
        key.setSingleLine(true);
        key.setHint("Paste your key here");
        key.setText(prefs.getString(PREF_KEY, ""));
        box.addView(key);

        TextView modelLabel = new TextView(this);
        modelLabel.setText("Model (leave blank for the default)");
        modelLabel.setTextColor(C_TEXT);
        modelLabel.setPadding(0, dp(10), 0, dp(2));
        box.addView(modelLabel);
        final EditText model = new EditText(this);
        model.setSingleLine(true);
        model.setHint(OnlineClient.defaultModel(provider[0]));
        model.setText(prefs.getString(PREF_MODEL, ""));
        box.addView(model);

        Button getKey = new Button(this);
        getKey.setText("Get a free key for this provider");
        getKey.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        box.addView(getKey);

        Button freeModels = new Button(this);
        freeModels.setText("Browse free OpenRouter models");
        freeModels.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        box.addView(freeModels);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                provider[0] = pos;
                model.setHint(OnlineClient.defaultModel(pos));
                note.setText("Offline model stays available. Online mode needs your own key from "
                        + OnlineClient.PROVIDER_LABELS[pos] + " — it is never bundled in the app.");
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });

        getKey.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW,
                            Uri.parse(OnlineClient.KEY_URLS[provider[0]]));
                    startActivity(i);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "No browser app found",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        freeModels.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showFreeModelPicker(); }
        });

        new AlertDialog.Builder(this)
                .setTitle("Online settings")
                .setView(scroll)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String k = key.getText().toString().trim();
                        String m = model.getText().toString().trim();
                        prefs.edit()
                                .putInt(PREF_PROVIDER, provider[0])
                                .putString(PREF_KEY, k)
                                .putString(PREF_MODEL, m)
                                .apply();
                        updateStatus();
                        Toast.makeText(MainActivity.this,
                                k.isEmpty() ? "Online key cleared — offline model active"
                                        : "Saved " + OnlineClient.PROVIDER_LABELS[provider[0]],
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    /** Show OpenRouter's currently-free models and let the user pick one. */
    private void showFreeModelPicker() {
        Toast.makeText(this, "Fetching free models…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final String[] ids = OnlineClient.listFreeModels();
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Free OpenRouter models")
                                    .setItems(ids, new DialogInterface.OnClickListener() {
                                        @Override public void onClick(DialogInterface d, int which) {
                                            prefs.edit()
                                                    .putInt(PREF_PROVIDER, 0)
                                                    .putString(PREF_MODEL, ids[which])
                                                    .apply();
                                            Toast.makeText(MainActivity.this,
                                                    "Model set to " + ids[which],
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    })
                                    .show();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            Toast.makeText(MainActivity.this, "Could not load models: "
                                    + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    // ------------------------------- info ---------------------------------- //
    private void showInfo() {
        int provider = prefs.getInt(PREF_PROVIDER, 0);
        String mode = isInternetAvailable() ? "ONLINE (internet available)"
                : "OFFLINE (no internet)";
        String info = "AUTO mode: " + mode + "\n"
                + "provider: " + OnlineClient.PROVIDER_LABELS[provider] + "\n"
                + "model: " + (prefs.getString(PREF_MODEL, "").isEmpty()
                        ? OnlineClient.defaultModel(provider)
                        : prefs.getString(PREF_MODEL, "")) + "\n"
                + "built-in: count-based N-gram, "
                + (bundle == null ? "loading" : String.format("%,d", bundle.model.parameterCount())
                        + " params") + "\n"
                + "chats saved: " + chats.size() + "\n"
                + "voice listening: " + sttEngineLabel + "\n"
                + "voice speaking: " + ttsEngineLabel + "\n"
                + "speech model: " + (!readGeminiKey().isEmpty()
                        ? "Gemini 2.5 Flash TTS (Kore)"
                        : prefs.getString(PREF_OPENAI_KEY, "").trim().isEmpty()
                        ? "Baidu neural / device" : "ChatGPT gpt-4o-mini-tts (alloy)") + "\n"
                + "voice agent: mic = hands-free conversation\n";
        addAssistantMessage(info, false);
    }
}
