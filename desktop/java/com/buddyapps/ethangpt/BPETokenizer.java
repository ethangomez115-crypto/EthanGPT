package com.buddyapps.ethangpt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java port of EthanGPT's character-level BPE tokenizer.
 *
 * Kept free of Android imports so it can be unit-tested on a desktop JVM.
 * It works on token ids throughout: each learned merge is (leftId, rightId,
 * combinedId), which is exactly equivalent to the string-based Python version
 * because every token string has a unique id.
 */
public class BPETokenizer {

    private static final Pattern PRE_TOKEN = Pattern.compile("\\S+|\\s+");

    private final String[] idToToken;
    private final Map<String, Integer> unitToId = new HashMap<>();
    private final int[][] merges;   // each: {leftId, rightId, combinedId}
    private final int padId;
    private final int bosId;
    private final int eosId;
    private final int unkId;

    public BPETokenizer(String[] idToToken, int[][] merges,
                        int padId, int bosId, int eosId, int unkId) {
        this.idToToken = idToToken;
        this.merges = merges;
        this.padId = padId;
        this.bosId = bosId;
        this.eosId = eosId;
        this.unkId = unkId;
        for (int id = 0; id < idToToken.length; id++) {
            String token = idToToken[id];
            if (token != null && token.codePointCount(0, token.length()) == 1) {
                unitToId.put(token, id);
            }
        }
    }

    public int size() {
        return idToToken.length;
    }

    public int bosId() { return bosId; }
    public int eosId() { return eosId; }
    public int unkId() { return unkId; }

    public int[] encode(String text) {
        List<Integer> out = new ArrayList<>();
        Matcher matcher = PRE_TOKEN.matcher(text);
        while (matcher.find()) {
            String chunk = matcher.group();
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < chunk.length(); ) {
                int cp = chunk.codePointAt(i);
                i += Character.charCount(cp);
                String unit = new String(Character.toChars(cp));
                Integer id = unitToId.get(unit);
                ids.add(id != null ? id : unkId);
            }
            for (int[] merge : merges) {
                ids = applyMerge(ids, merge[0], merge[1], merge[2]);
            }
            out.addAll(ids);
        }
        int[] result = new int[out.size()];
        for (int i = 0; i < result.length; i++) result[i] = out.get(i);
        return result;
    }

    private static List<Integer> applyMerge(List<Integer> ids, int left, int right, int combined) {
        if (ids.size() < 2) return ids;
        List<Integer> result = new ArrayList<>(ids.size());
        int i = 0;
        while (i < ids.size()) {
            if (i + 1 < ids.size() && ids.get(i) == left && ids.get(i + 1) == right) {
                result.add(combined);
                i += 2;
            } else {
                result.add(ids.get(i));
                i += 1;
            }
        }
        return result;
    }

    public String decode(int[] ids) {
        StringBuilder builder = new StringBuilder();
        for (int id : ids) {
            if (id < 0 || id >= idToToken.length) continue;
            if (id == padId || id == bosId || id == eosId || id == unkId) continue;
            builder.append(idToToken[id]);
        }
        return builder.toString();
    }
}
