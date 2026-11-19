package com.buddyapps.ethangpt;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;

/**
 * Loads the embedded model.txt (written by tools/export_android_model.py) into
 * a tokenizer and an N-gram model. Pure Java: no Android imports.
 */
public final class ModelLoader {

    public static final class Bundle {
        public final BPETokenizer tokenizer;
        public final NGramModel model;
        public final int vocabSize;
        public final int mergeCount;

        Bundle(BPETokenizer tokenizer, NGramModel model, int vocabSize, int mergeCount) {
            this.tokenizer = tokenizer;
            this.model = model;
            this.vocabSize = vocabSize;
            this.mergeCount = mergeCount;
        }
    }

    private ModelLoader() { }

    public static Bundle load(InputStream in) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, Charset.forName("UTF-8")));
        String header = reader.readLine();
        if (header == null || !header.startsWith("ETHANGPT_MODEL")) {
            throw new IllegalArgumentException("Not an EthanGPT model file");
        }

        int order = 0;
        int vocabSize = 0;
        String[] idToToken = null;
        List<int[]> merges = new ArrayList<>();
        List<HashMap<String, HashMap<Integer, Integer>>> tables = null;

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) continue;
            char type = line.charAt(0);
            if (line.startsWith("order ")) {
                order = Integer.parseInt(line.substring(6).trim());
                tables = new ArrayList<>();
                for (int i = 0; i < order; i++) tables.add(new HashMap<String, HashMap<Integer, Integer>>());
            } else if (line.startsWith("vocab ")) {
                vocabSize = Integer.parseInt(line.substring(6).trim());
                idToToken = new String[vocabSize];
            } else if (line.startsWith("N ")) {
                // merge count; not needed beyond the G lines themselves
            } else if (type == 'V') {
                String[] parts = line.split("\\s+");
                int id = Integer.parseInt(parts[1]);
                String token = new String(Base64.getDecoder().decode(parts[2]), "UTF-8");
                if (id >= 0 && id < idToToken.length) idToToken[id] = token;
            } else if (type == 'G') {
                String[] parts = line.split("\\s+");
                merges.add(new int[]{
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3])
                });
            } else if (type == 'C') {
                int bar = line.indexOf('|');
                String left = line.substring(0, bar).trim();
                String right = line.substring(bar + 1).trim();
                String[] leftParts = left.split("\\s+");
                int n = Integer.parseInt(leftParts[1]);
                StringBuilder key = new StringBuilder();
                for (int i = 2; i < leftParts.length; i++) {
                    if (i > 2) key.append(',');
                    key.append(leftParts[i]);
                }
                HashMap<Integer, Integer> counts = new HashMap<>();
                if (!right.isEmpty()) {
                    for (String pair : right.split("\\s+")) {
                        int colon = pair.indexOf(':');
                        int nextId = Integer.parseInt(pair.substring(0, colon));
                        int count = Integer.parseInt(pair.substring(colon + 1));
                        counts.put(nextId, count);
                    }
                }
                tables.get(n - 1).put(key.toString(), counts);
            }
        }
        reader.close();

        int padId = find(idToToken, "<pad>", 0);
        int bosId = find(idToToken, "<bos>", 1);
        int eosId = find(idToToken, "<eos>", 2);
        int unkId = find(idToToken, "<unk>", 3);

        int[][] mergeArray = new int[merges.size()][];
        for (int i = 0; i < merges.size(); i++) mergeArray[i] = merges.get(i);

        BPETokenizer tokenizer = new BPETokenizer(idToToken, mergeArray, padId, bosId, eosId, unkId);
        NGramModel model = new NGramModel(tokenizer, order, tables);
        return new Bundle(tokenizer, model, vocabSize, merges.size());
    }

    private static int find(String[] tokens, String target, int fallback) {
        for (int i = 0; i < tokens.length; i++) {
            if (target.equals(tokens[i])) return i;
        }
        return fallback;
    }
}
