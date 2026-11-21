package com.buddyapps.ethangpt;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Inbuilt offline chatbot. Uses the embedded N-gram model directly on the
 * device: no Termux, no server, no internet.
 */
public class ChatBot {

    private final NGramModel model;
    private final Random rng = new Random();
    private final List<String[]> history = new ArrayList<>();

    private int maxNewTokens = 60;
    private double temperature = 0.8;
    private int topK = 10;
    private double topP = 0.9;

    public ChatBot(NGramModel model) {
        this.model = model;
    }

    public void configure(int maxNewTokens, double temperature, int topK, double topP) {
        this.maxNewTokens = maxNewTokens;
        this.temperature = temperature;
        this.topK = topK;
        this.topP = topP;
    }

    public int historySize() {
        return history.size();
    }

    /** The conversation so far, as {role, text} pairs, for online mode. */
    public List<String[]> history() {
        return history;
    }

    /** Record a turn that was answered by something other than this model. */
    public void addTurn(String role, String text) {
        history.add(new String[]{role, text});
    }

    public void clearHistory() {
        history.clear();
    }

    public String respond(String message) {
        String clean = message == null ? "" : message.trim();
        if (clean.isEmpty()) return "Please type a message.";
        history.add(new String[]{"user", clean});

        String prompt = "Q: " + clean + "\nA:";

        // Be honest: only answer when the model actually saw this kind of prompt
        // in training. Otherwise it would just stitch random corpus sentences.
        int evidence = model.promptMatchOrder(prompt);
        if (evidence < 3) {
            String fallback = "I don't know that from my small built-in data. "
                    + "With internet available I'll answer online; otherwise ask "
                    + "about Nexus AI, tokens, the tokenizer or coding ideas.";
            history.add(new String[]{"assistant", fallback});
            return fallback;
        }

        String answer = model.generate(prompt, maxNewTokens, temperature, topK, topP, rng).trim();
        if (answer.isEmpty()) {
            answer = "I do not know an answer from my small offline training data.";
        }
        history.add(new String[]{"assistant", answer});
        return answer;
    }
}
