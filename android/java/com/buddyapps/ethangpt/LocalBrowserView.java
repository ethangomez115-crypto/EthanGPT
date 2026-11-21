package com.buddyapps.ethangpt;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * A real, general-purpose web browser tab. Loads any URL the user types
 * (including test websites) inside an embedded WebView.
 */
public final class LocalBrowserView {

    private static final int BG = 0xFF1B1B1F;
    private static final int SURFACE = 0xFF2A2A31;
    private static final int TEXT = 0xFFF5F5F7;
    private static final int DIM = 0xFF9AA0AA;
    private static final int ACCENT = 0xFF8AB4F8;
    private static final String HOME_URL = "https://example.com";

    // Live references so other tabs (e.g. the Code IDE preview) can navigate here.
    private static WebView sWeb;
    private static EditText sAddress;

    private LocalBrowserView() { }

    /** Navigate the browser pane to a URL (used by the Code IDE's local preview). */
    public static void loadUrl(String url) {
        if (url == null) return;
        if (sAddress != null) sAddress.setText(url);
        if (sWeb != null) sWeb.loadUrl(url);
    }

    public static View build(final Context ctx) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        // Keep clear of the status bar (this pane is not inset by MainActivity).
        int top = dp(ctx, 26);
        root.setPadding(dp(ctx, 8), top, dp(ctx, 8), dp(ctx, 4));

        TextView title = new TextView(ctx);
        title.setText("NEXUS BROWSER");
        title.setTextSize(12);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(ACCENT);
        title.setPadding(0, 0, 0, dp(ctx, 6));
        root.addView(title);

        // --- Address bar ---
        LinearLayout addressRow = new LinearLayout(ctx);
        addressRow.setOrientation(LinearLayout.HORIZONTAL);
        addressRow.setGravity(Gravity.CENTER_VERTICAL);

        final EditText address = new EditText(ctx);
        address.setHint("https://example.com");
        address.setText(HOME_URL);
        address.setSingleLine(true);
        address.setTextColor(TEXT);
        address.setHintTextColor(DIM);
        address.setTextSize(14);
        address.setTypeface(Typeface.MONOSPACE);
        GradientDrawable ab = new GradientDrawable();
        ab.setColor(SURFACE);
        ab.setCornerRadius(dp(ctx, 10));
        address.setBackground(ab);
        address.setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10));
        address.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        addressRow.addView(address);

        Button go = button(ctx, "Go");
        go.setLayoutParams(new LinearLayout.LayoutParams(
                dp(ctx, 56), ViewGroup.LayoutParams.WRAP_CONTENT));
        addressRow.addView(go);

        root.addView(addressRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // --- Navigation toolbar ---
        LinearLayout tools = new LinearLayout(ctx);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setPadding(0, dp(ctx, 6), 0, dp(ctx, 6));
        final Button back = button(ctx, "Back");
        final Button fwd = button(ctx, "Fwd");
        final Button reload = button(ctx, "Reload");
        final Button home = button(ctx, "Home");
        Button[] nav = new Button[] { back, fwd, reload, home };
        for (Button b : nav) {
            b.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            tools.addView(b);
        }
        root.addView(tools, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Viewing options: the WebView below is the in-app view; these let the
        // user hand the current page to the system browser or any share target.
        LinearLayout viewOptions = new LinearLayout(ctx);
        viewOptions.setOrientation(LinearLayout.HORIZONTAL);
        viewOptions.setPadding(0, 0, 0, dp(ctx, 4));
        final Button openExternal = button(ctx, "Open in browser");
        final Button shareLink = button(ctx, "Share link");
        Button[] opts = new Button[] { openExternal, shareLink };
        for (Button b : opts) {
            b.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            viewOptions.addView(b);
        }
        root.addView(viewOptions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final ProgressBar progress = new ProgressBar(ctx, null,
                android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 3)));

        final WebView web = new WebView(ctx);
        sWeb = web;
        sAddress = address;
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        web.setBackgroundColor(BG);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                address.setText(url);
                view.loadUrl(url);
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                address.setText(url);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int p) {
                progress.setProgress(p);
                progress.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
            }
            @Override public void onReceivedTitle(WebView view, String t) {
                if (t != null && !t.trim().isEmpty()) {
                    title.setText("NEXUS BROWSER  ·  " + t);
                }
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final android.view.View.OnClickListener load = new android.view.View.OnClickListener() {
            @Override public void onClick(View v) {
                String url = address.getText().toString().trim();
                if (url.isEmpty()) {
                    Toast.makeText(ctx, "Enter a URL first", Toast.LENGTH_SHORT).show();
                    return;
                }
                web.loadUrl(normalize(url));
                InputMethodManager imm = (InputMethodManager)
                        ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(address.getWindowToken(), 0);
            }
        };
        go.setOnClickListener(load);
        address.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int action, KeyEvent e) {
                if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE
                        || action == EditorInfo.IME_ACTION_SEARCH) {
                    load.onClick(v);
                    return true;
                }
                return false;
            }
        });
        back.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(View v) {
                if (web.canGoBack()) web.goBack();
                else Toast.makeText(ctx, "No previous page", Toast.LENGTH_SHORT).show();
            }
        });
        fwd.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(View v) {
                if (web.canGoForward()) web.goForward();
                else Toast.makeText(ctx, "No next page", Toast.LENGTH_SHORT).show();
            }
        });
        reload.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(View v) { web.reload(); }
        });
        home.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(View v) {
                address.setText(HOME_URL);
                web.loadUrl(HOME_URL);
            }
        });
        openExternal.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW,
                            Uri.parse(currentUrl(web, address)));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(i);
                } catch (Exception e) {
                    Toast.makeText(ctx, "No browser app found", Toast.LENGTH_SHORT).show();
                }
            }
        });
        shareLink.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_TEXT, currentUrl(web, address));
                    ctx.startActivity(Intent.createChooser(send, "Share link"));
                } catch (Exception e) {
                    Toast.makeText(ctx, "Nothing available to share with",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        web.loadUrl(HOME_URL);
        return root;
    }

    /** The fully-qualified URL currently shown, for opening/sharing elsewhere. */
    private static String currentUrl(WebView web, EditText address) {
        String u = web.getUrl();
        if (u == null || u.trim().isEmpty() || u.startsWith("about:")) {
            u = address.getText().toString().trim();
        }
        return normalize(u);
    }

    /** Add a scheme when the user typed a bare domain, and google-search otherwise. */
    private static String normalize(String input) {
        String u = input.trim();
        if (u.startsWith("http://") || u.startsWith("https://")
                || u.startsWith("file://") || u.startsWith("about:")) {
            return u;
        }
        if (u.matches("^[\\w.-]+\\.[A-Za-z]{2,}(/.*)?$")) {
            return "https://" + u;
        }
        return "https://www.google.com/search?q="
                + u.replace(" ", "+");
    }

    private static Button button(Context ctx, String label) {
        Button b = new Button(ctx);
        b.setText(label);
        b.setTextSize(12);
        b.setTextColor(TEXT);
        GradientDrawable g = new GradientDrawable();
        g.setColor(SURFACE);
        g.setCornerRadius(dp(ctx, 10));
        b.setBackground(g);
        b.setPadding(dp(ctx, 4), dp(ctx, 8), dp(ctx, 4), dp(ctx, 8));
        return b;
    }

    private static int dp(Context ctx, float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
