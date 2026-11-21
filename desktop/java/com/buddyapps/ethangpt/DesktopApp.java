package com.buddyapps.ethangpt;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Nexus AI Desktop — a cross-platform (Windows / Linux / macOS) front-end for
 * the same portable AI core used by the Android app.
 *
 * Requires Java 11+ (Java 17 recommended). No Android dependencies.
 */
public final class DesktopApp {

    // ---------- theme ----------
    static final Color BG = new Color(0x14, 0x14, 0x14);
    static final Color SURFACE = new Color(0x22, 0x22, 0x22);
    static final Color SURFACE2 = new Color(0x2C, 0x2C, 0x2C);
    static final Color TEXT = new Color(0xF2, 0xF2, 0xF2);
    static final Color DIM = new Color(0x9A, 0x9A, 0x9A);
    static final Color ACCENT = new Color(0x10, 0xA3, 0x7F);

    // ---------- state ----------
    private static ChatBot bot;
    private static ModelLoader.Bundle bundle;
    private static final List<ChatRecord> chats = new ArrayList<>();
    private static String activeChatId;

    private static JTabbedPane tabs;
    private static JTextPane chatPane;
    private static JTextField chatInput;
    private static JLabel status;
    private static JComboBox<String> providerBox;
    private static JTextField modelField;
    private static JPasswordField keyField;

    private static final File CONFIG_DIR = new File(System.getProperty("user.home"), ".nexusai");
    private static final File CONFIG_FILE = new File(CONFIG_DIR, "config.properties");
    private static final File CHAT_FILE = new File(CONFIG_DIR, "chats.json");

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
                catch (Exception ignored) { }
                buildUi();
                loadCoreInBackground();
            }
        });
    }

    // ----------------------------- UI -------------------------------------- //
    private static void buildUi() {
        JFrame frame = new JFrame("Nexus AI");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(940, 680);
        frame.setLocationRelativeTo(null);
        frame.setBackground(BG);

        tabs = new JTabbedPane();
        tabs.setBackground(BG);
        tabs.setForeground(TEXT);
        tabs.setFont(tabs.getFont().deriveFont(Font.BOLD, 13f));
        tabs.addTab("Chat", buildChatPanel());
        tabs.addTab("Terminal", buildTerminalPanel());
        tabs.addTab("Code IDE", buildCodePanel());
        tabs.addTab("Web", buildWebPanel());
        tabs.addTab("Settings", buildSettingsPanel());

        frame.setContentPane(tabs);
        frame.setVisible(true);
    }

    private static JPanel buildChatPanel() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel head = new JPanel(new BorderLayout());
        head.setBackground(BG);
        JLabel title = new JLabel("Nexus AI");
        title.setForeground(ACCENT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        status = new JLabel("Loading model…");
        status.setForeground(DIM);
        head.add(title, BorderLayout.WEST);
        head.add(status, BorderLayout.EAST);
        root.add(head, BorderLayout.NORTH);

        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setBackground(SURFACE);
        chatPane.setForeground(TEXT);
        chatPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        JScrollPane scroll = new JScrollPane(chatPane);
        scroll.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        root.add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(6, 6));
        bottom.setBackground(BG);
        chatInput = new JTextField();
        styleField(chatInput);
        chatInput.addActionListener(new java.awt.event.ActionListener() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { sendChat(); }
        });
        JButton send = button("Send");
        send.addActionListener(new java.awt.event.ActionListener() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { sendChat(); }
        });
        bottom.add(chatInput, BorderLayout.CENTER);
        bottom.add(send, BorderLayout.EAST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setBackground(BG);
        addAction(actions, "New chat", new Runnable() { @Override public void run() { newChat(); } });
        addAction(actions, "Backup chats", new Runnable() { @Override public void run() { backupChats(); } });
        addAction(actions, "Restore chats", new Runnable() { @Override public void run() { restoreChats(); } });
        addAction(actions, "Share chats", new Runnable() { @Override public void run() { shareChats(); } });
        addAction(actions, "Copy last reply", new Runnable() { @Override public void run() { copyLast(); } });
        addAction(actions, "Save reply", new Runnable() { @Override public void run() { saveReply(); } });
        addAction(actions, "Generate image", new Runnable() { @Override public void run() { generateImage(); } });
        bottom.add(actions, BorderLayout.SOUTH);
        root.add(bottom, BorderLayout.SOUTH);

        return root;
    }

    private static JPanel buildTerminalPanel() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        final JTextArea out = new JTextArea();
        out.setEditable(false);
        out.setBackground(new Color(0x1A, 0x1A, 0x1A));
        out.setForeground(TEXT);
        out.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane scroll = new JScrollPane(out);
        root.add(scroll, BorderLayout.CENTER);
        out.setText("Nexus Terminal — runs with this app's permissions (no root).\n"
                + "Type 'help' for commands.\n\n");

        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(BG);
        final JTextField cmd = new JTextField();
        styleField(cmd);
        JButton run = button("Run");
        run.addActionListener(new java.awt.event.ActionListener() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                String c = cmd.getText().trim();
                if (c.isEmpty()) return;
                cmd.setText("");
                out.append("$ " + c + "\n");
                Shell.run(c, out);
            }
        });
        cmd.addActionListener(run.getActionListeners()[0]);
        row.add(cmd, BorderLayout.CENTER);
        row.add(run, BorderLayout.EAST);
        root.add(row, BorderLayout.SOUTH);
        return root;
    }

    private static JPanel buildCodePanel() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        final JTextArea editor = new JTextArea();
        editor.setBackground(new Color(0x23, 0x23, 0x23));
        editor.setForeground(TEXT);
        editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        editor.setText("// Nexus AI Code IDE\n"
                + "// Write a shell script and tap Run, or use Chat to ask Nexus about code.\n"
                + "echo \"Hello from Nexus AI\"\n");
        JScrollPane scroll = new JScrollPane(editor);
        root.add(scroll, BorderLayout.CENTER);

        final JTextArea out = new JTextArea(6, 2);
        out.setEditable(false);
        out.setBackground(new Color(0x1B, 0x1B, 0x1B));
        out.setForeground(new Color(0xD0, 0xD0, 0xD0));
        out.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane outScroll = new JScrollPane(out);
        root.add(outScroll, BorderLayout.SOUTH);

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        bar.setBackground(BG);
        final File dir = new File(CONFIG_DIR, "code");
        final File previewDir = new File(CONFIG_DIR, "preview");
        addAction(bar, "Preview site", new Runnable() {
            @Override public void run() {
                try {
                    previewDir.mkdirs();
                    write(new File(previewDir, "index.html"), editor.getText());
                    String url = LocalPreviewServer.start(previewDir);
                    if (url != null) Desktop.getDesktop().browse(new URI(url));
                    else JOptionPane.showMessageDialog(null, "Could not start the local preview.");
                } catch (Exception ex) { error(ex); }
            }
        });
        addAction(bar, "New", new Runnable() {
            @Override public void run() {
                String name = JOptionPane.showInputDialog("File name (e.g. script.sh):", "script.sh");
                if (name != null && !name.trim().isEmpty()) editor.setText("");
            }
        });
        addAction(bar, "Save", new Runnable() {
            @Override public void run() {
                try {
                    dir.mkdirs();
                    String name = JOptionPane.showInputDialog("Save as:", "script.sh");
                    if (name == null || name.trim().isEmpty()) return;
                    write(new File(dir, name.trim()), editor.getText());
                    JOptionPane.showMessageDialog(null, "Saved to " + new File(dir, name.trim()));
                } catch (Exception ex) { error(ex); }
            }
        });
        addAction(bar, "Run shell", new Runnable() {
            @Override public void run() {
                out.setText("");
                Shell.run(editor.getText(), out);
            }
        });
        addAction(bar, "Ask Nexus", new Runnable() {
            @Override public void run() {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(editor.getText()), null);
                tabs.setSelectedIndex(0);
                chatInput.setText("Explain this code:\n" + editor.getText());
                chatInput.requestFocus();
            }
        });
        root.add(bar, BorderLayout.NORTH);
        return root;
    }

    private static JPanel buildWebPanel() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(BG);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(8, 8, 8, 8);
        g.gridx = 0; g.gridy = 0; g.anchor = GridBagConstraints.WEST;
        JLabel h = new JLabel("Web");
        h.setForeground(ACCENT);
        h.setFont(h.getFont().deriveFont(Font.BOLD, 18f));
        root.add(h, g);
        g.gridy++;
        JLabel msg = new JLabel("<html><div width='420' style='color:#DDDDDD'>"
                + "Nexus AI opens web pages in your system browser, so you get a full, "
                + "modern browsing experience with your logins and extensions. "
                + "Enter a URL below.</div></html>");
        root.add(msg, g);

        g.gridy++;
        final JTextField url = new JTextField("https://example.com", 28);
        styleField(url);
        root.add(url, g);
        JButton go = button("Open in browser");
        go.addActionListener(new java.awt.event.ActionListener() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                try {
                    String u = url.getText().trim();
                    if (!u.startsWith("http")) u = "https://" + u;
                    Desktop.getDesktop().browse(new URI(u));
                } catch (Exception ex) { error(ex); }
            }
        });
        g.gridx = 1;
        root.add(go, g);
        return root;
    }

    private static JPanel buildSettingsPanel() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(BG);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(8, 8, 8, 8);
        g.anchor = GridBagConstraints.WEST;
        g.gridx = 0; g.gridy = 0;
        JLabel h = new JLabel("Online settings");
        h.setForeground(ACCENT);
        h.setFont(h.getFont().deriveFont(Font.BOLD, 18f));
        root.add(h, g);

        g.gridy++;
        root.add(label("Provider (uses free tiers)"), g);
        g.gridx = 1;
        providerBox = new JComboBox<>(OnlineClient.PROVIDER_LABELS);
        providerBox.setBackground(SURFACE);
        providerBox.setForeground(TEXT);
        root.add(providerBox, g);

        g.gridx = 0; g.gridy++;
        root.add(label("Model"), g);
        g.gridx = 1;
        modelField = new JTextField(24);
        styleField(modelField);
        root.add(modelField, g);

        g.gridx = 0; g.gridy++;
        root.add(label("API key (stored locally, never bundled)"), g);
        g.gridx = 1;
        keyField = new JPasswordField(24);
        styleField(keyField);
        root.add(keyField, g);

        g.gridx = 0; g.gridy++;
        root.add(label("Get a free key:"), g);
        g.gridx = 1;
        root.add(new JLabel("<html><a href=''>OpenRouter · Groq · Google AI Studio</a></html>"), g);

        g.gridx = 1; g.gridy++;
        JButton save = button("Save settings");
        save.addActionListener(new java.awt.event.ActionListener() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { saveConfig(); }
        });
        root.add(save, g);

        g.gridx = 0; g.gridy++;
        JLabel note = new JLabel("<html><div width='520' style='color:#9A9A9A'>"
                + "The offline model works with no key and no internet. Online mode needs "
                + "your own free API key from a provider above. Keys are saved only in "
                + "~/.nexusai/config.properties on this machine.</div></html>");
        root.add(note, g);

        loadConfig();
        return root;
    }

    // --------------------------- core / chat -------------------------------- //
    private static void loadCoreInBackground() {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    InputStream in = DesktopApp.class.getResourceAsStream("/model.txt");
                    if (in == null) throw new FileNotFoundException("model.txt missing from JAR");
                    bundle = ModelLoader.load(in);
                    in.close();
                    bot = new ChatBot(bundle.model);
                    bot.configure(60, 0.8, 10, 0.9);
                    final String info = "offline model ready · " + bundle.vocabSize + " tokens";
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override public void run() {
                            status.setText(info);
                            append("Nexus AI", "Offline model loaded. Ask me anything, or add "
                                    + "a free API key in Settings for online answers.", ACCENT);
                        }
                    });
                } catch (final Exception e) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override public void run() { status.setText("model error: " + e.getMessage()); }
                    });
                }
                loadChats();
            }
        }, "nexus-core").start();
    }

    private static void sendChat() {
        final String text = chatInput.getText().trim();
        if (text.isEmpty()) return;
        if (bot == null) { append("System", "Model still loading…", DIM); return; }
        chatInput.setText("");
        append("You", text, TEXT);
        final int provider = providerBox != null ? providerBox.getSelectedIndex() : 0;
        final String key = keyField != null ? new String(keyField.getPassword()).trim() : "";
        final String model = modelField != null ? modelField.getText().trim() : "";
        status.setText("Thinking…");
        new Thread(new Runnable() {
            @Override public void run() {
                String answer;
                if (!key.isEmpty()) {
                    try {
                        answer = OnlineClient.chat(OnlineClient.providerId(provider), key, model,
                                bot.history(), text);
                        bot.addTurn("user", text);
                        bot.addTurn("assistant", answer);
                    } catch (final Exception e) {
                        answer = "Online request failed (" + e.getMessage() + ").";
                    }
                } else {
                    answer = bot.respond(text);
                }
                persistChat();
                final String a = answer;
                SwingUtilities.invokeLater(new Runnable() {
                    @Override public void run() {
                        append("Nexus AI", a, ACCENT);
                        status.setText("offline model ready");
                    }
                });
            }
        }, "nexus-reply").start();
    }

    private static void append(String who, String text, Color color) {
        StyledDocument doc = chatPane.getStyledDocument();
        SimpleAttributeSet header = new SimpleAttributeSet();
        StyleConstants.setBold(header, true);
        StyleConstants.setForeground(header, color);
        SimpleAttributeSet body = new SimpleAttributeSet();
        StyleConstants.setForeground(body, TEXT);
        try {
            doc.insertString(doc.getLength(), who + ": ", header);
            doc.insertString(doc.getLength(), text + "\n\n", body);
        } catch (BadLocationException ignored) { }
        chatPane.setCaretPosition(doc.getLength());
    }

    /** Return the last assistant reply text, or null. */
    private static String lastReply() {
        try {
            StyledDocument doc = chatPane.getStyledDocument();
            String all = doc.getText(0, doc.getLength());
            int idx = all.lastIndexOf("Nexus AI: ");
            if (idx < 0) return null;
            String last = all.substring(idx + "Nexus AI: ".length()).trim();
            return last.isEmpty() ? null : last;
        } catch (Exception e) { return null; }
    }

    private static void saveReply() {
        String reply = lastReply();
        if (reply == null) {
            JOptionPane.showMessageDialog(null, "No reply to save yet.");
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("Nexus-reply.txt"));
        if (fc.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            try { write(fc.getSelectedFile(), reply); }
            catch (Exception e) { error(e); }
        }
    }

    /** Generate an image from the chat prompt using the free, keyless endpoint. */
    private static void generateImage() {
        final String prompt = chatInput.getText().trim();
        if (prompt.isEmpty()) {
            JOptionPane.showMessageDialog(null, "Describe the image in the chat box first.");
            return;
        }
        append("You", prompt, TEXT);
        chatInput.setText("");
        status.setText("Generating image…");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final byte[] data = ImageGenerator.generate(prompt);
                    File dir = new File(CONFIG_DIR, "images");
                    dir.mkdirs();
                    final File file = new File(dir, "Nexus-image-" + System.currentTimeMillis() + ".jpg");
                    writeBytes(file, data);
                    try { Desktop.getDesktop().open(file); } catch (Exception ignored) { }
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override public void run() {
                            append("Nexus AI", "Image generated and saved to " + file, ACCENT);
                            status.setText("offline model ready");
                        }
                    });
                } catch (final Exception e) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override public void run() {
                            append("Nexus AI", "Image generation failed: " + e.getMessage(), ACCENT);
                            status.setText("offline model ready");
                        }
                    });
                }
            }
        }, "nexus-image").start();
    }

    private static void newChat() {
        if (bot != null) bot.clearHistory();
        activeChatId = UUID.randomUUID().toString();
        chatPane.setText("");
        append("Nexus AI", "New chat started.", ACCENT);
        persistChat();
    }

    private static void copyLast() {
        String last = lastReply();
        if (last != null) {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(last), null);
        }
    }

    // --------------------------- persistence -------------------------------- //
    static final class ChatRecord {
        String id, title;
        long updated;
        List<String[]> turns = new ArrayList<>();
    }

    private static void persistChat() {
        if (bot == null) return;
        ChatRecord c = findChat(activeChatId);
        if (c == null) {
            c = new ChatRecord();
            c.id = activeChatId == null ? UUID.randomUUID().toString() : activeChatId;
            activeChatId = c.id;
            chats.add(0, c);
        }
        c.turns.clear();
        for (String[] t : bot.history()) c.turns.add(new String[]{t[0], t[1]});
        c.updated = System.currentTimeMillis();
        if (!bot.history().isEmpty()) c.title = bot.history().get(0)[1];
        writeChats();
    }

    private static ChatRecord findChat(String id) {
        if (id == null) return null;
        for (ChatRecord c : chats) if (id.equals(c.id)) return c;
        return null;
    }

    private static String chatsJson() {
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
                    pair.put(t[0]); pair.put(t[1]);
                    turns.put(pair);
                }
                o.put("turns", turns);
                arr.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("active", activeChatId == null ? "" : activeChatId);
            root.put("chats", arr);
            return root.toString();
        } catch (Exception e) { return "{\"active\":\"\",\"chats\":[]}"; }
    }

    private static boolean applyChats(String json) {
        if (json == null || json.trim().isEmpty()) return false;
        try {
            chats.clear();
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("chats");
            if (arr == null) return false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                ChatRecord c = new ChatRecord();
                c.id = o.optString("id", UUID.randomUUID().toString());
                c.title = o.optString("title", "Chat");
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
            activeChatId = root.optString("active", "");
            return true;
        } catch (Exception e) { return false; }
    }

    private static void loadChats() {
        if (!CHAT_FILE.exists()) return;
        String saved;
        try { saved = read(CHAT_FILE); }
        catch (Exception e) { return; }
        if (applyChats(saved)) {
            ChatRecord c = findChat(activeChatId);
            if (c == null && !chats.isEmpty()) c = chats.get(0);
            if (c != null && bot != null) {
                bot.clearHistory();
                for (String[] t : c.turns) bot.addTurn(t[0], t[1]);
                final ChatRecord chosen = c;
                SwingUtilities.invokeLater(new Runnable() {
                    @Override public void run() {
                        chatPane.setText("");
                        append("Nexus AI", "Restored chat: " + chosen.title, ACCENT);
                        for (String[] t : chosen.turns) {
                            append(t[0].equals("user") ? "You" : "Nexus AI", t[1],
                                    t[0].equals("user") ? TEXT : ACCENT);
                        }
                    }
                });
            }
        }
    }

    private static void writeChats() {
        try { write(CHAT_FILE, chatsJson()); }
        catch (Exception ignored) { }
    }

    private static void backupChats() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("NexusAI-Chats-Backup.json"));
        if (fc.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            try {
                write(fc.getSelectedFile(), chatsJson());
                JOptionPane.showMessageDialog(null, "Chats backed up.");
            } catch (Exception e) { error(e); }
        }
    }

    private static void restoreChats() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            try {
                String json = read(fc.getSelectedFile());
                if (applyChats(json) && bot != null) {
                    bot.clearHistory();
                    ChatRecord c = findChat(activeChatId);
                    if (c != null) for (String[] t : c.turns) bot.addTurn(t[0], t[1]);
                    writeChats();
                    JOptionPane.showMessageDialog(null, "Chats restored.");
                } else {
                    JOptionPane.showMessageDialog(null, "That file is not a Nexus AI backup.");
                }
            } catch (Exception e) { error(e); }
        }
    }

    private static void shareChats() {
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(chatsJson()), null);
        JOptionPane.showMessageDialog(null, "Chat backup JSON copied to clipboard — "
                + "paste it into WhatsApp, email, or a file.");
    }

    // --------------------------- config ------------------------------------- //
    private static void loadConfig() {
        try {
            if (!CONFIG_FILE.exists()) return;
            Properties p = new Properties();
            java.io.Reader r = new InputStreamReader(new FileInputStream(CONFIG_FILE), Charset.forName("UTF-8"));
            p.load(r); r.close();
            int idx = Integer.parseInt(p.getProperty("provider", "0"));
            if (providerBox != null && idx < providerBox.getItemCount()) providerBox.setSelectedIndex(idx);
            if (modelField != null) modelField.setText(p.getProperty("model", ""));
            if (keyField != null) keyField.setText(p.getProperty("key", ""));
        } catch (Exception ignored) { }
    }

    private static void saveConfig() {
        try {
            CONFIG_DIR.mkdirs();
            Properties p = new Properties();
            p.setProperty("provider", String.valueOf(providerBox.getSelectedIndex()));
            p.setProperty("model", modelField.getText().trim());
            p.setProperty("key", new String(keyField.getPassword()).trim());
            java.io.Writer w = new OutputStreamWriter(new FileOutputStream(CONFIG_FILE), Charset.forName("UTF-8"));
            p.store(w, "Nexus AI local settings"); w.close();
            JOptionPane.showMessageDialog(null, "Settings saved to " + CONFIG_FILE);
        } catch (Exception e) { error(e); }
    }

    // --------------------------- helpers ------------------------------------ //
    private static JLabel label(String s) {
        JLabel l = new JLabel(s); l.setForeground(TEXT); return l;
    }

    private static void styleField(JTextField f) {
        f.setBackground(SURFACE2);
        f.setForeground(TEXT);
        f.setCaretColor(TEXT);
        f.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
    }

    private static JButton button(String text) {
        JButton b = new JButton(text);
        b.setBackground(ACCENT);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(true);
        return b;
    }

    private static void addAction(JPanel panel, String text, Runnable action) {
        JButton b = new JButton(text);
        b.setBackground(SURFACE2);
        b.setForeground(TEXT);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(true);
        b.addActionListener(new java.awt.event.ActionListener() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { action.run(); }
        });
        panel.add(b);
    }

    private static void write(File f, String s) throws IOException {
        writeBytes(f, s.getBytes("UTF-8"));
    }

    private static void writeBytes(File f, byte[] data) throws IOException {
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        OutputStream os = new FileOutputStream(f);
        os.write(data); os.flush(); os.close();
    }

    private static String read(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        byte[] b = new byte[(int) f.length()];
        int off = 0;
        while (off < b.length) {
            int n = in.read(b, off, b.length - off);
            if (n < 0) break;
            off += n;
        }
        in.close();
        return new String(b, 0, off, "UTF-8");
    }

    private static void error(Exception e) {
        JOptionPane.showMessageDialog(null, "Error: " + e.getMessage(), "Nexus AI",
                JOptionPane.ERROR_MESSAGE);
    }

    /** Runs shell commands for the Terminal and Code tabs. */
    static final class Shell {
        static void run(final String command, final JTextArea out) {
            new Thread(new Runnable() {
                @Override public void run() {
                    if (command.trim().equals("help")) {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override public void run() {
                                out.append("Commands: help, clear, pwd, ls, cat, echo, "
                                        + "or any shell command.\n");
                            }
                        });
                        return;
                    }
                    if (command.trim().equals("clear")) {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override public void run() { out.setText(""); }
                        });
                        return;
                    }
                    try {
                        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command)
                                .redirectErrorStream(true).directory(CONFIG_DIR);
                        final Process p = pb.start();
                        final StringBuilder sb = new StringBuilder();
                        InputStream in = p.getInputStream();
                        byte[] buf = new byte[4096];
                        int n;
                        long start = System.currentTimeMillis();
                        while ((n = in.read(buf)) != -1) {
                            sb.append(new String(buf, 0, n, "UTF-8"));
                            if (System.currentTimeMillis() - start > 15000) { p.destroy(); break; }
                        }
                        in.close();
                        final String result = sb.toString();
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override public void run() {
                                out.append(result);
                                out.setCaretPosition(out.getDocument().getLength());
                            }
                        });
                    } catch (final Exception e) {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override public void run() { out.append("error: " + e.getMessage() + "\n"); }
                        });
                    }
                }
            }, "nexus-shell").start();
        }
    }
}
