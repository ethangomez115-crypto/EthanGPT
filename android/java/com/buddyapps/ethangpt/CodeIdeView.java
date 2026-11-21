package com.buddyapps.ethangpt;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CodeIdeView {

    /** Lets the IDE hand a preview URL or code back to the host (chat tab). */
    public interface CodeHost {
        void onPreview(String url);
        void onSendToChat(String code);
    }

    /** Bridge so chat replies can open a snippet in this IDE. */
    private static final String[] sOpenCode = new String[] { "" };
    private static Runnable sOpenRunnable;

    public static void openCode(String code) {
        sOpenCode[0] = code == null ? "" : code;
        if (sOpenRunnable != null) sOpenRunnable.run();
    }

    private static final int BG = 0xFF1A1A1A;
    private static final int SURFACE = 0xFF2A2A2A;
    private static final int EDITOR = 0xFF232323;
    private static final int OUTPUT_BG = 0xFF1B1B1B;
    private static final int TEXT = 0xFFF2F2F2;
    private static final int DIM = 0xFF9A9A9A;
    private static final int ACCENT = 0xFF10A37F;
    private static final int KEYWORD = 0xFFC586C0;
    private static final int STRING = 0xFFCE9178;
    private static final int COMMENT = 0xFF6A9955;
    private static final int OUTPUT_TEXT = 0xFFD0D0D0;
    private static final int MAX_CHARS = 20000;

    private static final int LANG_JAVA = 0;
    private static final int LANG_PYTHON = 1;
    private static final int LANG_JS = 2;
    private static final int LANG_HTML = 3;
    private static final int LANG_SHELL = 4;
    private static final int LANG_TEXT = 5;

    private static final String[] LANGS = {"Java", "Python", "JavaScript", "HTML", "Shell", "Plain text"};

    private static final Set<String> JAVA_KW = set("abstract", "boolean", "break", "byte", "case", "catch",
            "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends",
            "final", "finally", "float", "for", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public", "return",
            "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while", "true", "false", "null", "String");
    private static final Set<String> PY_KW = set("and", "as", "assert", "async", "await", "break", "class",
            "continue", "def", "del", "elif", "else", "except", "false", "finally", "for", "from", "global",
            "if", "import", "in", "is", "lambda", "none", "nonlocal", "not", "or", "pass", "print", "raise",
            "return", "self", "true", "try", "while", "with", "yield", "True", "False", "None");
    private static final Set<String> JS_KW = set("async", "await", "break", "case", "catch", "class", "const",
            "continue", "default", "delete", "do", "else", "export", "extends", "false", "finally", "for",
            "function", "if", "import", "in", "instanceof", "let", "new", "null", "of", "return", "super",
            "switch", "this", "throw", "true", "try", "typeof", "undefined", "var", "void", "while", "yield");
    private static final Set<String> HTML_KW = set("html", "head", "body", "title", "meta", "link", "style",
            "script", "div", "span", "p", "a", "img", "ul", "ol", "li", "table", "tr", "td", "form",
            "input", "button", "h1", "h2", "h3", "h4", "h5", "h6", "header", "footer", "section", "nav");
    private static final Set<String> SH_KW = set("if", "then", "else", "elif", "fi", "for", "in", "do", "done",
            "while", "case", "esac", "function", "return", "local", "export", "echo", "cd", "exit", "set",
            "true", "false");
    private static final Set<String> EMPTY_KW = set();

    public static View build(final Context ctx, final CodeHost host) {
        final File codeDir = new File(ctx.getFilesDir(), "code");
        if (!codeDir.exists()) {
            codeDir.mkdirs();
        }
        final File previewDir = new File(ctx.getFilesDir(), "preview");
        if (!previewDir.exists()) {
            previewDir.mkdirs();
        }
        File[] initial = codeDir.listFiles();
        if (initial == null || initial.length == 0) {
            writeFile(new File(codeDir, "Main.java"),
                    "public class Main {\n"
                            + "    public static void main(String[] args) {\n"
                            + "        System.out.println(\"Hello from Nexus IDE!\");\n"
                            + "    }\n"
                            + "}\n");
        }

        final List<String> files = new ArrayList<String>();
        final ArrayAdapter<String> fileAdapter = new ArrayAdapter<String>(ctx,
                android.R.layout.simple_spinner_item, files);
        fileAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        final ArrayAdapter<String> langAdapter = new ArrayAdapter<String>(ctx,
                android.R.layout.simple_spinner_item, LANGS);
        langAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        final int[] langHolder = new int[]{LANG_JAVA};
        final boolean[] applying = new boolean[]{false};

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        int pad = dp(ctx, 10);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        headerLp.bottomMargin = dp(ctx, 6);
        header.setLayoutParams(headerLp);

        TextView title = new TextView(ctx);
        title.setText("CODE IDE");
        title.setTextColor(ACCENT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextSize(12f);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.rightMargin = dp(ctx, 6);
        title.setLayoutParams(titleLp);
        header.addView(title);

        final Spinner fileSpinner = new Spinner(ctx);
        fileSpinner.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        fileSpinner.setAdapter(fileAdapter);
        header.addView(fileSpinner);

        final Button newBtn = button(ctx, "New", SURFACE, TEXT);
        final Button saveBtn = button(ctx, "Save", ACCENT, Color.WHITE);
        final Button delBtn = button(ctx, "Del", SURFACE, 0xFFFF8080);
        header.addView(newBtn, smallButtonParams(ctx));
        header.addView(saveBtn, smallButtonParams(ctx));
        header.addView(delBtn, smallButtonParams(ctx));
        root.addView(header);

        LinearLayout langRow = new LinearLayout(ctx);
        langRow.setOrientation(LinearLayout.HORIZONTAL);
        langRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams langRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        langRowLp.bottomMargin = dp(ctx, 6);
        langRow.setLayoutParams(langRowLp);

        TextView langLabel = new TextView(ctx);
        langLabel.setText("Language");
        langLabel.setTextColor(DIM);
        langLabel.setTextSize(11f);
        LinearLayout.LayoutParams langLabelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        langLabelLp.rightMargin = dp(ctx, 6);
        langLabel.setLayoutParams(langLabelLp);
        langRow.addView(langLabel);

        final Spinner languageSpinner = new Spinner(ctx);
        languageSpinner.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        languageSpinner.setAdapter(langAdapter);
        langRow.addView(languageSpinner);
        root.addView(langRow);

        final EditText editor = new EditText(ctx);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextColor(TEXT);
        editor.setHintTextColor(DIM);
        editor.setHint("// type code here…");
        editor.setTextSize(13f);
        editor.setBackgroundColor(EDITOR);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setHorizontallyScrolling(true);
        editor.setMinWidth(dp(ctx, 2000));
        editor.setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8));
        int minWidth = dp(ctx, 2000);

        final ScrollView vScroll = new ScrollView(ctx);
        vScroll.setBackgroundColor(EDITOR);
        vScroll.addView(editor, new ScrollView.LayoutParams(minWidth,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        HorizontalScrollView hScroll = new HorizontalScrollView(ctx);
        hScroll.setBackgroundColor(EDITOR);
        hScroll.setHorizontalScrollBarEnabled(true);
        hScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        hScroll.addView(vScroll, new HorizontalScrollView.LayoutParams(minWidth,
                LinearLayout.LayoutParams.MATCH_PARENT));
        root.addView(hScroll);

        // Clean code-viewer layout (like AI Studio): just the editor, no run console.
        LinearLayout actions = new LinearLayout(ctx);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = dp(ctx, 8);
        actions.setLayoutParams(actionsLp);

        final Button copyBtn = button(ctx, "Copy code", SURFACE, TEXT);
        actions.addView(copyBtn, smallButtonParams(ctx));
        final Button previewBtn = button(ctx, "Preview site", SURFACE, TEXT);
        actions.addView(previewBtn, smallButtonParams(ctx));
        final Button askBtn = button(ctx, "Ask Nexus", SURFACE, TEXT);
        actions.addView(askBtn, smallButtonParams(ctx));
        root.addView(actions);

        class Highlighter {
            void apply(Editable s) {
                if (applying[0]) {
                    return;
                }
                applying[0] = true;
                try {
                    ForegroundColorSpan[] olds = s.getSpans(0, s.length(), ForegroundColorSpan.class);
                    for (int i = 0; i < olds.length; i++) {
                        s.removeSpan(olds[i]);
                    }
                    SpannableStringBuilder sb = new SpannableStringBuilder(s.toString());
                    mark(sb, keywordsFor(langHolder[0]));
                    ForegroundColorSpan[] news = sb.getSpans(0, sb.length(), ForegroundColorSpan.class);
                    for (int i = 0; i < news.length; i++) {
                        ForegroundColorSpan sp = news[i];
                        s.setSpan(sp, sb.getSpanStart(sp), sb.getSpanEnd(sp), sb.getSpanFlags(sp));
                    }
                } finally {
                    applying[0] = false;
                }
            }

            void refresh() {
                apply(editor.getText());
            }
        }
        final Highlighter highlighter = new Highlighter();

        editor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                highlighter.apply(s);
            }
        });

        class Controller {
            void refreshFiles() {
                files.clear();
                File[] arr = codeDir.listFiles();
                if (arr != null) {
                    for (File f : arr) {
                        if (f.isFile()) {
                            files.add(f.getName());
                        }
                    }
                }
                Collections.sort(files);
                fileAdapter.notifyDataSetChanged();
            }

            String currentName() {
                int pos = fileSpinner.getSelectedItemPosition();
                if (pos < 0 || pos >= files.size()) {
                    return null;
                }
                return files.get(pos);
            }

            void loadSelected() {
                String name = currentName();
                if (name == null) {
                    return;
                }
                String text = readFile(new File(codeDir, name));
                int li = langForExt(name);
                langHolder[0] = li;
                languageSpinner.setSelection(li);
                applying[0] = true;
                editor.setText(text);
                applying[0] = false;
                highlighter.refresh();
            }

            void save() {
                String name = currentName();
                if (name == null) {
                    Toast.makeText(ctx, "No file selected", Toast.LENGTH_SHORT).show();
                    return;
                }
                boolean ok = writeFile(new File(codeDir, name), editor.getText().toString());
                Toast.makeText(ctx, ok ? ("Saved " + name) : ("Could not save " + name),
                        Toast.LENGTH_SHORT).show();
            }

            void newFile() {
                LinearLayout box = new LinearLayout(ctx);
                box.setOrientation(LinearLayout.VERTICAL);
                int p = dp(ctx, 16);
                box.setPadding(p, dp(ctx, 8), p, dp(ctx, 8));
                final EditText nameField = new EditText(ctx);
                nameField.setHint("filename (e.g. Main.java)");
                nameField.setSingleLine(true);
                nameField.setTextColor(TEXT);
                nameField.setHintTextColor(DIM);
                box.addView(nameField);

                new AlertDialog.Builder(ctx)
                        .setTitle("New file")
                        .setView(box)
                        .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                String n = nameField.getText().toString().trim();
                                if (n.length() == 0) {
                                    Toast.makeText(ctx, "Enter a filename", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                if (n.indexOf('.') < 0) {
                                    n = n + extForLang(langHolder[0]);
                                }
                                File f = new File(codeDir, n);
                                try {
                                    if (!f.exists()) {
                                        f.createNewFile();
                                    }
                                } catch (Exception e) {
                                    Toast.makeText(ctx, "Could not create file", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                refreshFiles();
                                int idx = files.indexOf(n);
                                if (idx < 0) {
                                    idx = 0;
                                }
                                fileSpinner.setSelection(idx);
                                loadSelected();
                                Toast.makeText(ctx, "Created " + n, Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }

            void deleteFile() {
                final String name = currentName();
                if (name == null) {
                    Toast.makeText(ctx, "No file selected", Toast.LENGTH_SHORT).show();
                    return;
                }
                new AlertDialog.Builder(ctx)
                        .setTitle("Delete file")
                        .setMessage("Delete " + name + "?")
                        .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                File f = new File(codeDir, name);
                                if (f.exists()) {
                                    f.delete();
                                }
                                refreshFiles();
                                if (files.size() > 0) {
                                    fileSpinner.setSelection(0);
                                    loadSelected();
                                } else {
                                    applying[0] = true;
                                    editor.setText("");
                                    applying[0] = false;
                                    highlighter.refresh();
                                }
                                Toast.makeText(ctx, "Deleted " + name, Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }

            /** Writes the current code as a local site and previews it on 127.0.0.1. */
            void preview() {
                String code = editor.getText().toString();
                if (code.trim().isEmpty()) {
                    Toast.makeText(ctx, "Nothing to preview", Toast.LENGTH_SHORT).show();
                    return;
                }
                String name = currentName();
                boolean html = langHolder[0] == LANG_HTML
                        || (name != null && (name.toLowerCase().endsWith(".html")
                        || name.toLowerCase().endsWith(".htm")));
                String page;
                if (html) {
                    page = code;
                } else {
                    // Wrap plain code/text in a minimal HTML page for preview.
                    page = "<!doctype html><meta charset='utf-8'>"
                            + "<title>Nexus preview</title>"
                            + "<style>body{background:#141414;color:#eee;font:13px monospace;"
                            + "padding:16px;white-space:pre-wrap}</style><body>"
                            + htmlEscape(code) + "</body>";
                }
                writeFile(new File(previewDir, "index.html"), page);
                String url = LocalPreviewServer.start(previewDir);
                if (url == null) {
                    Toast.makeText(ctx, "Could not start the local preview",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                if (host != null) host.onPreview(url);
                Toast.makeText(ctx, "Preview running at " + url, Toast.LENGTH_LONG).show();
            }

            void askNexus() {
                String code = editor.getText().toString();
                if (host != null) {
                    host.onSendToChat(code);
                } else {
                    ClipboardManager cm = (ClipboardManager)
                            ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("code", code));
                    Toast.makeText(ctx, "Code copied — paste it in Chat",
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
        final Controller controller = new Controller();

        fileSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                controller.loadSelected();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                langHolder[0] = pos;
                highlighter.refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        newBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                controller.newFile();
            }
        });
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                controller.save();
            }
        });
        delBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                controller.deleteFile();
            }
        });
        copyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String code = editor.getText().toString();
                ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("code", code));
                Toast.makeText(ctx, "Code copied", Toast.LENGTH_SHORT).show();
            }
        });
        previewBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                controller.preview();
            }
        });
        askBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                controller.askNexus();
            }
        });

        controller.refreshFiles();
        if (files.size() > 0) {
            fileSpinner.setSelection(0);
            controller.loadSelected();
        }
        sOpenRunnable = new Runnable() {
            @Override public void run() {
                String name = "Snippet_" + System.currentTimeMillis() + ".txt";
                writeFile(new File(codeDir, name), sOpenCode[0]);
                controller.refreshFiles();
                int idx = files.indexOf(name);
                if (idx >= 0) fileSpinner.setSelection(idx);
            }
        };
        return root;
    }

    private static String htmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void mark(SpannableStringBuilder sb, Set<String> keywords) {
        String s = sb.toString();
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                int st = i;
                while (i < n && s.charAt(i) != '\n') {
                    i++;
                }
                if (i > st) {
                    sb.setSpan(new ForegroundColorSpan(COMMENT), st, i, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                continue;
            }
            if (c == '#') {
                int st = i;
                while (i < n && s.charAt(i) != '\n') {
                    i++;
                }
                if (i > st) {
                    sb.setSpan(new ForegroundColorSpan(COMMENT), st, i, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                int st = i;
                char quote = c;
                i++;
                while (i < n) {
                    char d = s.charAt(i);
                    if (d == '\\' && i + 1 < n) {
                        i += 2;
                        continue;
                    }
                    if (d == quote) {
                        i++;
                        break;
                    }
                    if (d == '\n') {
                        break;
                    }
                    i++;
                }
                if (i > st) {
                    sb.setSpan(new ForegroundColorSpan(STRING), st, i, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                continue;
            }
            if (Character.isLetter(c) || c == '_' || c == '$') {
                int st = i;
                while (i < n) {
                    char d = s.charAt(i);
                    if (Character.isLetterOrDigit(d) || d == '_' || d == '$') {
                        i++;
                    } else {
                        break;
                    }
                }
                String word = s.substring(st, i);
                if (keywords.contains(word)) {
                    sb.setSpan(new ForegroundColorSpan(KEYWORD), st, i, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                continue;
            }
            i++;
        }
    }

    private static Set<String> set(String... words) {
        HashSet<String> h = new HashSet<String>();
        for (String w : words) {
            h.add(w);
        }
        return h;
    }

    private static Set<String> keywordsFor(int lang) {
        switch (lang) {
            case LANG_JAVA:
                return JAVA_KW;
            case LANG_PYTHON:
                return PY_KW;
            case LANG_JS:
                return JS_KW;
            case LANG_HTML:
                return HTML_KW;
            case LANG_SHELL:
                return SH_KW;
            default:
                return EMPTY_KW;
        }
    }

    private static String extForLang(int lang) {
        switch (lang) {
            case LANG_JAVA:
                return ".java";
            case LANG_PYTHON:
                return ".py";
            case LANG_JS:
                return ".js";
            case LANG_HTML:
                return ".html";
            case LANG_SHELL:
                return ".sh";
            default:
                return ".txt";
        }
    }

    private static int langForExt(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".java")) {
            return LANG_JAVA;
        }
        if (n.endsWith(".py")) {
            return LANG_PYTHON;
        }
        if (n.endsWith(".js")) {
            return LANG_JS;
        }
        if (n.endsWith(".html") || n.endsWith(".htm")) {
            return LANG_HTML;
        }
        if (n.endsWith(".sh")) {
            return LANG_SHELL;
        }
        return LANG_TEXT;
    }

    private static Button button(Context ctx, String label, int bg, int fg) {
        Button b = new Button(ctx);
        b.setText(label);
        b.setTextSize(11f);
        b.setTextColor(fg);
        b.setMinimumWidth(0);
        b.setPadding(dp(ctx, 8), dp(ctx, 2), dp(ctx, 8), dp(ctx, 2));
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bg);
        gd.setCornerRadius(dp(ctx, 5));
        b.setBackground(gd);
        return b;
    }

    private static LinearLayout.LayoutParams smallButtonParams(Context ctx) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(ctx, 4);
        return lp;
    }

    private static boolean writeFile(File f, String text) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(f);
            out.write(text.getBytes("UTF-8"));
            out.flush();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String readFile(File f) {
        StringBuilder sb = new StringBuilder();
        InputStream in = null;
        try {
            in = new FileInputStream(f);
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            br.close();
        } catch (Exception e) {
            return sb.toString();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
        return sb.toString();
    }

    private static int dp(Context ctx, float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
