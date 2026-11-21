package com.buddyapps.ethangpt;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/**
 * Full-screen overlay that sits on top of the chat. It lists the websites Nexus
 * generated in chat and previews the selected one in a plain, full-screen WebView
 * (no browser chrome, no address bar, no code editor).
 */
public final class ProjectsView {
    private static final int BG = 0xFF1F1E1D;
    private static final int SURFACE = 0xFF2B2A27;
    private static final int TEXT = 0xFFF5F4EF;
    private static final int DIM = 0xFFA8A29A;
    private static final int ACCENT = 0xFFD97757;

    private final Context ctx;
    private final Runnable onClose;
    private final FrameLayout root;
    private final ScrollView listScroll;
    private final LinearLayout listCol;
    private final FrameLayout detail;
    private final WebView web;
    private final TextView detailTitle;
    private File current;

    private ProjectsView(Context ctx, Runnable onClose) {
        this.ctx = ctx;
        this.onClose = onClose;

        root = new FrameLayout(ctx);
        root.setBackgroundColor(BG);

        // ---- project list ----
        listCol = new LinearLayout(ctx);
        listCol.setOrientation(LinearLayout.VERTICAL);
        listCol.setPadding(dp(18), dp(16), dp(18), dp(28));
        listScroll = new ScrollView(ctx);
        listScroll.setFillViewport(true);
        listScroll.addView(listCol, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(listScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- full-screen preview ----
        detail = new FrameLayout(ctx);
        detail.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(SURFACE);
        header.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView back = textBtn("\u2039 Projects", ACCENT, 15);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showList(); }
        });
        header.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        detailTitle = new TextView(ctx);
        detailTitle.setTextColor(TEXT);
        detailTitle.setTextSize(15);
        detailTitle.setGravity(Gravity.CENTER);
        detailTitle.setSingleLine(true);
        detailTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = dp(10);
        tp.rightMargin = dp(10);
        detailTitle.setLayoutParams(tp);
        header.addView(detailTitle);

        TextView share = textBtn("Share", ACCENT, 15);
        share.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { shareCurrent(); }
        });
        header.addView(share, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView close = textBtn("\u00d7", TEXT, 22);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showList();
                if (onClose != null) onClose.run();
            }
        });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.leftMargin = dp(14);
        close.setLayoutParams(cp);
        header.addView(close);

        web = new WebView(ctx);
        web.setBackgroundColor(BG);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        web.setWebViewClient(new WebViewClient());

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(web, new LinearLayout.LayoutParams(0, 0, 1f));
        detail.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        detail.setVisibility(View.GONE);
        root.addView(detail, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        renderList();
    }

    public static ProjectsView create(Context ctx, Runnable onClose) {
        return new ProjectsView(ctx, onClose);
    }

    public View getView() { return root; }

    /** Refresh the list, e.g. when the overlay is opened again. */
    public void refresh() { renderList(); }

    // ------------------------------ list ---------------------------------- //

    private void renderList() {
        listCol.removeAllViews();

        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(ctx);
        title.setText("Projects");
        title.setTextSize(23);
        title.setTextColor(TEXT);
        top.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = textBtn("\u00d7", TEXT, 26);
        close.setPadding(dp(10), 0, dp(4), 0);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (onClose != null) onClose.run();
            }
        });
        top.addView(close);
        listCol.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        File[] dirs = projects();
        TextView sub = new TextView(ctx);
        sub.setText(dirs.length == 0
                ? "Ask Nexus to build a website, then tap Preview on its reply to save it here."
                : dirs.length + " saved project" + (dirs.length == 1 ? "" : "s")
                  + " \u00b7 tap one to preview");
        sub.setTextSize(13);
        sub.setTextColor(DIM);
        sub.setPadding(0, dp(4), 0, dp(16));
        listCol.addView(sub);

        for (final File dir : dirs) {
            File index = new File(dir, "index.html");
            if (!index.exists()) continue;

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(SURFACE);
            row.setPadding(dp(14), dp(14), dp(14), dp(14));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = dp(10);
            row.setLayoutParams(rp);

            TextView icon = new TextView(ctx);
            icon.setText("\u25c8");
            icon.setTextSize(22);
            icon.setTextColor(ACCENT);
            icon.setPadding(0, 0, dp(14), 0);
            row.addView(icon);

            LinearLayout info = new LinearLayout(ctx);
            info.setOrientation(LinearLayout.VERTICAL);
            TextView name = new TextView(ctx);
            name.setText(pretty(dir.getName()));
            name.setTextSize(15);
            name.setTextColor(TEXT);
            info.addView(name);
            TextView meta = new TextView(ctx);
            meta.setText(SIMPLE_DATE.format(new Date(index.lastModified()))
                    + "  \u00b7  " + (index.length() / 1024) + " KB");
            meta.setTextSize(12);
            meta.setTextColor(DIM);
            info.addView(meta);
            row.addView(info, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView arrow = new TextView(ctx);
            arrow.setText("\u203a");
            arrow.setTextSize(22);
            arrow.setTextColor(DIM);
            row.addView(arrow);

            row.setClickable(true);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openProject(dir); }
            });
            listCol.addView(row);
        }
    }

    private static final SimpleDateFormat SIMPLE_DATE =
            new SimpleDateFormat("dd MMM, HH:mm", Locale.US);

    private File[] projects() {
        File dir = new File(ctx.getFilesDir(), "projects");
        if (!dir.exists()) dir.mkdirs();
        File[] arr = dir.listFiles();
        if (arr == null) return new File[0];
        File[] files = new File[arr.length];
        int n = 0;
        for (File f : arr) if (f.isDirectory()) files[n++] = f;
        File[] out = Arrays.copyOf(files, n);
        Arrays.sort(out, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        return out;
    }

    // ---------------------------- preview --------------------------------- //

    private void openProject(File dir) {
        if (!new File(dir, "index.html").exists()) {
            Toast.makeText(ctx, "This project has no page yet", Toast.LENGTH_SHORT).show();
            return;
        }
        String url = LocalPreviewServer.start(dir);
        if (url == null) {
            Toast.makeText(ctx, "Could not start the preview", Toast.LENGTH_SHORT).show();
            return;
        }
        current = dir;
        detailTitle.setText(pretty(dir.getName()));
        web.loadUrl(url);
        detail.setVisibility(View.VISIBLE);
        listScroll.setVisibility(View.GONE);
    }

    private void showList() {
        detail.setVisibility(View.GONE);
        listScroll.setVisibility(View.VISIBLE);
        renderList();
    }

    private void shareCurrent() {
        if (current == null) return;
        File f = new File(current, "index.html");
        byte[] data = readAll(f);
        if (data == null) {
            Toast.makeText(ctx, "Nothing to share", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = NexusFiles.save(ctx, pretty(current.getName()).replace(' ', '_') + ".html",
                "text/html", "Projects", data);
        NexusFiles.share(ctx, uri, "text/html", "Nexus AI project");
    }

    // ------------------------------ save ---------------------------------- //

    /** Save generated HTML as a new, timestamped project. Returns its folder name. */
    public static String save(Context ctx, String html, String label) {
        if (html == null || html.trim().isEmpty()) return null;
        File dir = new File(ctx.getFilesDir(), "projects");
        if (!dir.exists()) dir.mkdirs();
        String base = sanitize(label);
        if (base.isEmpty()) base = "Project";
        String name = base + "-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                .format(new Date()) + "-" + (System.currentTimeMillis() % 1000);
        File project = new File(dir, name);
        project.mkdirs();
        write(new File(project, "index.html"), html);
        return name;
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : s.trim().toCharArray()) {
            if (Character.isLetterOrDigit(c)) b.append(c);
            else if (c == ' ' && b.length() > 0 && b.charAt(b.length() - 1) != '-') b.append('-');
            if (b.length() >= 28) break;
        }
        while (b.length() > 0 && b.charAt(b.length() - 1) == '-') b.deleteCharAt(b.length() - 1);
        return b.toString();
    }

    private static String pretty(String folder) {
        String s = folder.replace('-', ' ');
        // Drop the trailing "<date> <time> <ms>" stamp for a clean title.
        String[] parts = s.split(" ");
        if (parts.length >= 3) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < parts.length - 3; i++) {
                if (b.length() > 0) b.append(' ');
                b.append(parts[i]);
            }
            if (b.length() > 0) return b.toString();
        }
        return s;
    }

    // ----------------------------- helpers -------------------------------- //

    private TextView textBtn(String label, int color, float size) {
        TextView t = new TextView(ctx);
        t.setText(label);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setPadding(dp(4), dp(4), dp(4), dp(4));
        return t;
    }

    private static void write(File f, String text) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(f);
            out.write(text.getBytes("UTF-8"));
            out.flush();
        } catch (Exception ignored) {
        } finally {
            if (out != null) try { out.close(); } catch (Exception ignored) { }
        }
    }

    private static byte[] readAll(File f) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            byte[] b = new byte[(int) f.length()];
            int off = 0;
            while (off < b.length) {
                int r = in.read(b, off, b.length - off);
                if (r < 0) break;
                off += r;
            }
            return b;
        } catch (Exception e) {
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) { }
        }
    }

    private int dp(float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
