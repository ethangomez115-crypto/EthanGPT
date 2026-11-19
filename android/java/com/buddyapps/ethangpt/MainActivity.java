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

import java.net.URLEncoder;

/**
 * EthanGPT v3 — the model is built into the app.
 *
 * No Termux, no server, no internet: the tokenizer and the 10M-parameter
 * N-gram model are embedded as assets and run directly in Java on the phone.
 * The Browser button opens a web search for harder questions.
 */
public class MainActivity extends Activity {

    private TextView chat;
    private EditText input;
    private Button sendButton;
    private ScrollView chatScroll;
    private TextView subtitle;

    private ChatBot bot;
    private ModelLoader.Bundle bundle;
    private volatile boolean ready = false;

    private static final String INTRO =
            "EthanGPT: I run fully on this phone — the model is built in, no Termux "
                    + "and no internet needed.\n\n"
                    + "Ask me about EthanGPT, tokens, the tokenizer, the offline model, "
                    + "or light coding ideas. For harder questions tap Web.";

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

        subtitle = new TextView(this);
        subtitle.setText("Inbuilt offline AI • loading model...");
        subtitle.setTextSize(12);
        subtitle.setTextColor(Color.parseColor("#9AA0AE"));
        subtitle.setPadding(0, 2, 0, 10);
        root.addView(subtitle);

        chat = new TextView(this);
        chat.setTextSize(14);
        chat.setTextColor(Color.parseColor("#E8EAF0"));
        chat.setText(INTRO);
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

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, 10, 0, 0);
        sendButton = makeButton("Send", new View.OnClickListener() {
            @Override public void onClick(View v) { sendMessage(); }
        });
        sendButton.setEnabled(false);
        actions.addView(sendButton);
        actions.addView(makeButton("Web", new View.OnClickListener() {
            @Override public void onClick(View v) { openWeb(); }
        }));
        root.addView(actions);

        LinearLayout secondary = new LinearLayout(this);
        secondary.setOrientation(LinearLayout.HORIZONTAL);
        secondary.setPadding(0, 6, 0, 0);
        secondary.addView(makeButton("Info", new View.OnClickListener() {
            @Override public void onClick(View v) { showInfo(); }
        }));
        secondary.addView(makeButton("Clear", new View.OnClickListener() {
            @Override public void onClick(View v) { clearChat(); }
        }));
        root.addView(secondary);

        setContentView(root);
        loadModel();
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

    private void loadModel() {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    bundle = ModelLoader.load(getAssets().open("model.txt"));
                    bot = new ChatBot(bundle.model);
                    bot.configure(60, 0.8, 10, 0.9);
                    ready = true;
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            subtitle.setText("Inbuilt offline AI • "
                                    + String.format("%,d", bundle.model.parameterCount())
                                    + " params • " + bundle.model.order() + "-gram");
                            sendButton.setEnabled(true);
                            appendChat("EthanGPT", "Model loaded on this phone. Ask away.");
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            subtitle.setText("Could not load the built-in model");
                            appendChat("EthanGPT", "Model load failed: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
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
        if (!ready) {
            Toast.makeText(this, "The model is still loading", Toast.LENGTH_SHORT).show();
            return;
        }
        final String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Type something first", Toast.LENGTH_SHORT).show();
            return;
        }
        input.setText("");
        appendChat("You", text);
        sendButton.setEnabled(false);
        new Thread(new Runnable() {
            @Override public void run() {
                final String reply = bot.respond(text);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        appendChat("EthanGPT", reply);
                        sendButton.setEnabled(true);
                    }
                });
            }
        }).start();
    }

    private void openWeb() {
        String question = input.getText().toString().trim();
        if (question.isEmpty()) question = "EthanGPT small language model";
        String url;
        try {
            url = "https://www.google.com/search?q=" + URLEncoder.encode(question, "UTF-8");
        } catch (Exception e) {
            url = "https://www.google.com/search?q=" + question.replace(" ", "+");
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            appendChat("EthanGPT", "Could not open a browser on this device.");
        }
    }

    private void showInfo() {
        if (!ready || bundle == null) {
            appendChat("EthanGPT (info)", "The model is still loading.");
            return;
        }
        String info = "runs entirely on this phone (no Termux, no server)\n"
                + "type: count-based N-gram language model (not a neural transformer)\n"
                + "parameters: " + String.format("%,d", bundle.model.parameterCount()) + "\n"
                + "vocabulary: " + bundle.vocabSize + " tokens\n"
                + "context order: " + bundle.model.order() + "\n"
                + "learned merges: " + bundle.mergeCount + "\n"
                + "messages in this chat: " + bot.historySize();
        appendChat("EthanGPT (info)", info);
    }

    private void clearChat() {
        if (bot != null) bot.clearHistory();
        chat.setText(INTRO);
        input.setText("");
    }
}
