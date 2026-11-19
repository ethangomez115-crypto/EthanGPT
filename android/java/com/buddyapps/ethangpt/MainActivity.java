package com.buddyapps.ethangpt;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * EthanGPT v2 companion app.
 *
 * This app is a front end to the real Python model you built in Termux. Start
 * the server there with:  python main.py serve
 * Then this screen talks to it over HTTP (default http://127.0.0.1:8080).
 *
 * Modes: offline (local N-gram model, no internet), opencode / ollama (free
 * local AI connectors) and browser (opens a web search on the phone).
 */
public class MainActivity extends Activity {

    private TextView chat;
    private EditText input;
    private EditText serverUrl;
    private ScrollView chatScroll;
    private Button[] modeButtons;
    private final String[] modeNames = {"offline", "opencode", "ollama", "browser"};
    private String mode = "offline";

    private static final String SELECTED = "#2E7D62";
    private static final String UNSELECTED = "#242837";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = 20;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#12141C"));
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("EthanGPT");
        title.setTextSize(24);
        title.setTextColor(Color.parseColor("#7EE0C0"));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("v2 • offline AI + free connectors • 10M+ params");
        subtitle.setTextSize(12);
        subtitle.setTextColor(Color.parseColor("#9AA0AE"));
        subtitle.setPadding(0, 2, 0, 10);
        root.addView(subtitle);

        // Server address row.
        LinearLayout serverRow = new LinearLayout(this);
        serverRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView serverLabel = new TextView(this);
        serverLabel.setText("Server: ");
        serverLabel.setTextColor(Color.parseColor("#9AA0AE"));
        serverLabel.setTextSize(13);
        serverRow.addView(serverLabel);
        serverUrl = new EditText(this);
        serverUrl.setText("http://127.0.0.1:8080");
        serverUrl.setTextColor(Color.parseColor("#E8EAF0"));
        serverUrl.setSingleLine(true);
        serverUrl.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        serverRow.addView(serverUrl);
        root.addView(serverRow);

        // Mode buttons.
        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setPadding(0, 10, 0, 4);
        modeButtons = new Button[modeNames.length];
        for (int i = 0; i < modeNames.length; i++) {
            final int index = i;
            Button b = new Button(this);
            b.setText(modeNames[i]);
            b.setTextSize(11);
            b.setBackgroundColor(Color.parseColor(i == 0 ? SELECTED : UNSELECTED));
            b.setTextColor(Color.WHITE);
            b.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setMode(index);
                }
            });
            modeButtons[i] = b;
            modes.addView(b);
        }
        root.addView(modes);

        // Chat area.
        chat = new TextView(this);
        chat.setTextSize(14);
        chat.setTextColor(Color.parseColor("#E8EAF0"));
        chat.setText("EthanGPT: Ready.\n\n"
                + "1) In Termux run: cd ~/EthanGPT && python main.py serve\n"
                + "2) Leave it running, then type a message here.\n\n"
                + "Off/offline uses the local 10M-parameter model. "
                + "Open Code, Ollama and Browser are free connectors. "
                + "Tap Info to check the model behind the server.");
        chat.setLineSpacing(4f, 1.1f);

        chatScroll = new ScrollView(this);
        chatScroll.addView(chat);
        chatScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(chatScroll);

        input = new EditText(this);
        input.setHint("Type a message...");
        input.setTextColor(Color.parseColor("#E8EAF0"));
        input.setHintTextColor(Color.parseColor("#777D8C"));
        input.setBackgroundColor(Color.parseColor("#242837"));
        input.setPadding(16, 16, 16, 16);
        root.addView(input);

        // Action buttons: Send / Web.
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, 10, 0, 0);
        actions.addView(makeButton("Send", new View.OnClickListener() {
            @Override public void onClick(View v) { sendMessage(); }
        }));
        actions.addView(makeButton("Web", new View.OnClickListener() {
            @Override public void onClick(View v) { openBrowser(getBase()); }
        }));
        root.addView(actions);

        // Secondary buttons: Info / Termux.
        LinearLayout secondary = new LinearLayout(this);
        secondary.setOrientation(LinearLayout.HORIZONTAL);
        secondary.setPadding(0, 6, 0, 0);
        secondary.addView(makeButton("Info", new View.OnClickListener() {
            @Override public void onClick(View v) { fetchInfo(); }
        }));
        secondary.addView(makeButton("Termux", new View.OnClickListener() {
            @Override public void onClick(View v) { openTermux(); }
        }));
        root.addView(secondary);

        setContentView(root);
    }

    private Button makeButton(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(13);
        b.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        b.setOnClickListener(listener);
        return b;
    }

    private void setMode(int index) {
        mode = modeNames[index];
        for (int i = 0; i < modeButtons.length; i++) {
            modeButtons[i].setBackgroundColor(
                    Color.parseColor(i == index ? SELECTED : UNSELECTED));
        }
        Toast.makeText(this, "Mode: " + mode, Toast.LENGTH_SHORT).show();
    }

    private String getBase() {
        String base = serverUrl.getText().toString().trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base;
    }

    private void appendChat(final String who, final String text) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                chat.append("\n\n" + who + ": " + text);
                chatScroll.post(new Runnable() {
                    @Override public void run() { chatScroll.fullScroll(View.FOCUS_DOWN); }
                });
            }
        });
    }

    private void sendMessage() {
        final String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Type something first", Toast.LENGTH_SHORT).show();
            return;
        }
        input.setText("");
        appendChat("You", text);

        if ("browser".equals(mode)) {
            String url = "https://www.google.com/search?q=" + encode(text);
            openBrowser(url);
            appendChat("EthanGPT", "Opened a web search for your question.");
            return;
        }

        final String base = getBase();
        new Thread(new Runnable() {
            @Override public void run() {
                String reply;
                try {
                    String json = "{\"message\":" + quote(text)
                            + ",\"mode\":" + quote(mode) + "}";
                    String raw = httpPost(base + "/chat", json);
                    reply = new JSONObject(raw).getString("reply");
                } catch (Exception e) {
                    reply = "I could not reach the model at " + base
                            + ". Start it in Termux with: cd ~/EthanGPT && python main.py serve";
                }
                appendChat("EthanGPT", reply);
            }
        }).start();
    }

    private void fetchInfo() {
        final String base = getBase();
        new Thread(new Runnable() {
            @Override public void run() {
                String info;
                try {
                    JSONObject o = new JSONObject(httpGet(base + "/info"));
                    info = "mode=" + o.optString("mode")
                            + "\nparameters=" + o.optLong("parameter_count")
                            + "\nvocabulary=" + o.optInt("vocab_size")
                            + "\ncontext order=" + o.optInt("order")
                            + "\nhistory messages=" + o.optInt("history_length");
                } catch (Exception e) {
                    info = "No server at " + base + ". Start it in Termux: python main.py serve";
                }
                appendChat("EthanGPT (info)", info);
            }
        }).start();
    }

    private void openBrowser(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            appendChat("EthanGPT", "Could not open the browser.");
        }
    }

    private void openTermux() {
        Intent intent = getPackageManager().getLaunchIntentForPackage("com.termux");
        if (intent == null) {
            Toast.makeText(this, "Termux is not installed", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(intent);
    }

    // ---- tiny HTTP + JSON helpers -------------------------------------

    private static String encode(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s.replace(" ", "+"); }
    }

    private static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        return b.append('"').toString();
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) out.append(line);
        reader.close();
        return out.toString();
    }

    private static String httpPost(String urlStr, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(8000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setDoOutput(true);
        OutputStream os = c.getOutputStream();
        os.write(body.getBytes("UTF-8"));
        os.flush();
        os.close();
        int code = c.getResponseCode();
        InputStream in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
        String result = read(in);
        c.disconnect();
        return result;
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        int code = c.getResponseCode();
        InputStream in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
        String result = read(in);
        c.disconnect();
        return result;
    }
}
