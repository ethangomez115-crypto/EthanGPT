package com.buddyapps.ethangpt;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * EthanGPT - Stage 1 companion app.
 *
 * This is an honest chat-style interface: the language model has NOT been
 * trained yet, so the app never pretends to answer with model output. It
 * shows the project status and can hand you over to Termux, where the real
 * Python model is built and trained.
 */
public class MainActivity extends Activity {

    private TextView chat;
    private EditText input;
    private ScrollView chatScroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = 24;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#12141C"));
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("EthanGPT");
        title.setTextSize(26);
        title.setTextColor(Color.parseColor("#7EE0C0"));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("From-scratch small language model  •  Stage 1");
        subtitle.setTextSize(13);
        subtitle.setTextColor(Color.parseColor("#9AA0AE"));
        subtitle.setPadding(0, 4, 0, 18);
        root.addView(subtitle);

        chat = new TextView(this);
        chat.setTextSize(15);
        chat.setTextColor(Color.parseColor("#E8EAF0"));
        chat.setText(
                "EthanGPT: Hello. I am the interface for a model you are building "
                        + "yourself in Python.\n\n"
                        + "Status: Stage 1 complete — device checked, Termux ready.\n"
                        + "The tokenizer, transformer and training are NOT built yet, "
                        + "so I cannot give real model answers yet.\n\n"
                        + "Device: Samsung Galaxy A06 5G, 4 GB RAM, 64 GB storage.\n"
                        + "Next: Stage 2 (tokenizer) in Termux.\n\n"
                        + "Type below to try the interface, or open Termux to run the check.");
        chat.setLineSpacing(4f, 1.12f);

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

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, 16, 0, 0);

        Button send = new Button(this);
        send.setText("Send");
        send.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });

        Button termux = new Button(this);
        termux.setText("Open Termux");
        termux.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        termux.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTermux();
            }
        });

        buttons.addView(send);
        buttons.addView(termux);
        root.addView(buttons);

        setContentView(root);
    }

    private void sendMessage() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Type something first", Toast.LENGTH_SHORT).show();
            return;
        }
        input.setText("");
        chat.append("\n\nYou: " + text);
        chat.append("\n\nEthanGPT: I have no trained weights yet, so this is not a "
                + "model answer. Train me in Termux with the Python project, "
                + "then this screen will be connected to the generated model.");
        chatScroll.post(new Runnable() {
            @Override
            public void run() {
                chatScroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void openTermux() {
        Intent intent = getPackageManager().getLaunchIntentForPackage("com.termux");
        if (intent == null) {
            Toast.makeText(this, "Termux is not installed", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(intent);
    }
}
