package com.buddyapps.ethangpt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Java port of EthanGPT's count-based N-gram language model.
 *
 * tables.get(n - 1) maps a context string ("id1,id2,...") to a map of
 * next-token id -> count. Generation backs off to the longest context it has
 * seen, then samples with temperature, top-k and top-p exactly like the Python
 * version. No Android imports, so it can be tested on a desktop JVM.
 */
public class NGramModel {

    private final BPETokenizer tokenizer;
    private final int order;
    private final List<HashMap<String, HashMap<Integer, Integer>>> tables;
    private final long parameterCount;

    public NGramModel(BPETokenizer tokenizer, int order,
                      List<HashMap<String, HashMap<Integer, Integer>>> tables) {
        this.tokenizer = tokenizer;
        this.order = order;
        this.tables = tables;
        long params = 0;
        for (HashMap<String, HashMap<Integer, Integer>> table : tables) {
            params += (long) table.size() * tokenizer.size();
        }
        this.parameterCount = params;
    }

    public int order() { return order; }
    public long parameterCount() { return parameterCount; }

    private static String key(List<Integer> context, int from, int to) {
        StringBuilder builder = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (i > from) builder.append(',');
            builder.append(context.get(i));
        }
        return builder.toString();
    }

    private HashMap<Integer, Integer> distribution(List<Integer> context) {
        int max = Math.min(context.size(), order);
        for (int n = max; n >= 1; n--) {
            String k = key(context, context.size() - n, context.size());
            HashMap<Integer, Integer> counts = tables.get(n - 1).get(k);
            if (counts != null && !counts.isEmpty()) return counts;
        }
        return null;
    }

    /**
     * How many trailing tokens of this prompt were actually seen together in
     * training (the longest matching context length). High means the model has
     * real evidence; low means it would only be guessing from backoff.
     */
    public int promptMatchOrder(String prompt) {
        List<Integer> context = new ArrayList<>();
        for (int i = 0; i < order; i++) context.add(tokenizer.bosId());
        for (int id : tokenizer.encode(prompt)) context.add(id);
        int max = Math.min(context.size(), order);
        for (int n = max; n >= 1; n--) {
            String k = key(context, context.size() - n, context.size());
            if (tables.get(n - 1).containsKey(k)) return n;
        }
        return 0;
    }

    public String generate(String prompt, int maxNewTokens, double temperature,
                           int topK, double topP, Random rng) {
        List<Integer> context = new ArrayList<>();
        for (int i = 0; i < order; i++) context.add(tokenizer.bosId());
        for (int id : tokenizer.encode(prompt)) context.add(id);

        List<Integer> generated = new ArrayList<>();
        for (int step = 0; step < maxNewTokens; step++) {
            HashMap<Integer, Integer> counts = distribution(context);
            if (counts == null) break;
            Integer token = sample(counts, temperature, topK, topP, rng);
            if (token == null) break;
            generated.add(token);
            context.add(token);
        }
        int[] ids = new int[generated.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = generated.get(i);
        return tokenizer.decode(ids);
    }

    private static Integer sample(Map<Integer, Integer> counts, double temperature,
                                  int topK, double topP, Random rng) {
        if (counts.isEmpty()) return null;
        List<Map.Entry<Integer, Integer>> items = new ArrayList<>(counts.entrySet());
        Collections.sort(items, new Comparator<Map.Entry<Integer, Integer>>() {
            @Override
            public int compare(Map.Entry<Integer, Integer> a, Map.Entry<Integer, Integer> b) {
                int byCount = Integer.compare(b.getValue(), a.getValue());
                if (byCount != 0) return byCount;
                return Integer.compare(a.getKey(), b.getKey());
            }
        });

        int limit = (topK > 0 && topK < items.size()) ? topK : items.size();
        if (temperature <= 0.0) return items.get(0).getKey();

        double[] logits = new double[limit];
        double maxLogit = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < limit; i++) {
            logits[i] = Math.log(items.get(i).getValue() + 1e-9) / temperature;
            if (logits[i] > maxLogit) maxLogit = logits[i];
        }
        double[] probs = new double[limit];
        double total = 0.0;
        for (int i = 0; i < limit; i++) {
            probs[i] = Math.exp(logits[i] - maxLogit);
            total += probs[i];
        }
        for (int i = 0; i < limit; i++) probs[i] /= total;

        int keep = limit;
        if (topP > 0.0 && topP < 1.0) {
            double cumulative = 0.0;
            keep = 0;
            for (int i = 0; i < limit; i++) {
                cumulative += probs[i];
                keep++;
                if (cumulative >= topP) break;
            }
        }

        double target = rng.nextDouble();
        double running = 0.0;
        for (int i = 0; i < keep; i++) {
            running += probs[i];
            if (target <= running) return items.get(i).getKey();
        }
        return items.get(keep - 1).getKey();
    }
}
