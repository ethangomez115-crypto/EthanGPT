package com.buddyapps.ethangpt;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class TerminalView {
    private static final int BG = 0xFF1A1A1A;
    private static final int SURFACE = 0xFF2A2A2A;
    private static final int TEXT = 0xFFF2F2F2;
    private static final int DIM = 0xFF9A9A9A;
    private static final int ACCENT = 0xFF10A37F;
    private static final int MAX_CHARS = 20000;
    private static final long TIMEOUT_MS = 15000L;

    public static View build(final Context ctx) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        int pad = dp(ctx, 12);
        root.setPadding(pad, pad, pad, pad);

        TextView header = new TextView(ctx);
        header.setText("TERMINAL");
        header.setTextColor(ACCENT);
        header.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.setTextSize(13f);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        header.setLayoutParams(headerLp);
        root.addView(header);

        TextView note = new TextView(ctx);
        note.setText("Local shell · runs with this app's permissions (no root)");
        note.setTextColor(DIM);
        note.setTextSize(11f);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteLp.topMargin = dp(ctx, 2);
        noteLp.bottomMargin = dp(ctx, 8);
        note.setLayoutParams(noteLp);
        root.addView(note);

        final ScrollView scrollView = new ScrollView(ctx);
        scrollView.setBackgroundColor(BG);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollView.setLayoutParams(scrollLp);

        final TextView console = new TextView(ctx);
        console.setTypeface(Typeface.MONOSPACE);
        console.setTextColor(0xFFE8E8E8);
        console.setTextSize(12f);
        console.setText("Buddy terminal ready.\nType 'help' for a list of commands.\n\n");
        console.setTextIsSelectable(true);
        scrollView.addView(console, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scrollView);

        LinearLayout bottom = new LinearLayout(ctx);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bottomLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bottomLp.topMargin = dp(ctx, 8);
        bottom.setLayoutParams(bottomLp);

        final EditText input = new EditText(ctx);
        input.setHint("command…");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        input.setTypeface(Typeface.MONOSPACE);
        input.setTextColor(TEXT);
        input.setHintTextColor(DIM);
        input.setBackgroundColor(SURFACE);
        input.setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        input.setLayoutParams(inputLp);
        bottom.addView(input);

        Button run = new Button(ctx);
        run.setText("Run");
        run.setTextColor(Color.WHITE);
        run.setBackgroundColor(ACCENT);
        LinearLayout.LayoutParams runLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        runLp.leftMargin = dp(ctx, 6);
        run.setLayoutParams(runLp);
        bottom.addView(run);

        Button clear = new Button(ctx);
        clear.setText("clear");
        clear.setTextColor(DIM);
        clear.setBackgroundColor(SURFACE);
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        clearLp.leftMargin = dp(ctx, 6);
        clear.setLayoutParams(clearLp);
        bottom.addView(clear);

        root.addView(bottom);

        class Helpers {
            void append(final String s) {
                console.post(new Runnable() {
                    @Override
                    public void run() {
                        String cur = console.getText().toString();
                        String next = cur + s;
                        if (next.length() > MAX_CHARS) {
                            next = next.substring(next.length() - MAX_CHARS);
                        }
                        console.setText(next);
                        scrollView.post(new Runnable() {
                            @Override
                            public void run() {
                                scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                            }
                        });
                    }
                });
            }

            void execute(final String cmd) {
                append("$ " + cmd + "\n");
                Process proc = null;
                try {
                    proc = new ProcessBuilder("/system/bin/sh", "-c", cmd)
                            .redirectErrorStream(true)
                            .directory(ctx.getFilesDir())
                            .start();
                } catch (Exception e) {
                    append("error: " + e.getMessage() + "\n\n");
                    return;
                }

                final Process process = proc;
                final Thread killer = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(TIMEOUT_MS);
                        } catch (InterruptedException ie) {
                            return;
                        }
                        try {
                            process.destroy();
                        } catch (Exception ignored) {
                        }
                    }
                });
                killer.setDaemon(true);
                killer.start();

                final Thread reader = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        StringBuilder sb = new StringBuilder();
                        InputStream is = process.getInputStream();
                        BufferedReader br = new BufferedReader(new InputStreamReader(is));
                        try {
                            String line;
                            while ((line = br.readLine()) != null) {
                                sb.append(line).append('\n');
                            }
                        } catch (Exception e) {
                            sb.append("error: ").append(e.getMessage()).append('\n');
                        } finally {
                            try {
                                br.close();
                            } catch (Exception ignored) {
                            }
                            killer.interrupt();
                            try {
                                process.waitFor();
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                            final String out = sb.toString();
                            console.post(new Runnable() {
                                @Override
                                public void run() {
                                    String cur = console.getText().toString();
                                    String next = cur + out + "\n";
                                    if (next.length() > MAX_CHARS) {
                                        next = next.substring(next.length() - MAX_CHARS);
                                    }
                                    console.setText(next);
                                    scrollView.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                                        }
                                    });
                                }
                            });
                        }
                    }
                });
                reader.setDaemon(true);
                reader.start();
            }
        }

        final Helpers helpers = new Helpers();

        class Controller {
            void submit() {
                final String cmd = input.getText().toString().trim();
                input.setText("");
                if (cmd.length() == 0) {
                    return;
                }
                if (cmd.equals("help")) {
                    helpers.append("$ help\n"
                            + "help       - show this list\n"
                            + "clear      - clear the console\n"
                            + "pwd        - print current directory\n"
                            + "ls         - list files\n"
                            + "echo TEXT  - print text\n"
                            + "cat FILE   - print a file\n"
                            + "Any other command runs via /system/bin/sh\n\n");
                    return;
                }
                if (cmd.equals("clear")) {
                    console.post(new Runnable() {
                        @Override
                        public void run() {
                            console.setText("");
                        }
                    });
                    return;
                }
                if (cmd.equals("pwd")) {
                    helpers.append("$ pwd\n" + ctx.getFilesDir().getAbsolutePath() + "\n\n");
                    return;
                }
                helpers.execute(cmd);
            }
        }

        final Controller controller = new Controller();

        run.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                controller.submit();
            }
        });

        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    controller.submit();
                    return true;
                }
                return false;
            }
        });

        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                console.post(new Runnable() {
                    @Override
                    public void run() {
                        console.setText("");
                    }
                });
            }
        });

        return root;
    }

    private static int dp(Context ctx, float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
