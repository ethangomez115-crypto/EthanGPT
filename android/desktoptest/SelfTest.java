package com.buddyapps.ethangpt;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Desktop verification of the Java port (not compiled into the APK).
 * Usage: java com.buddyapps.ethangpt.SelfTest <model.txt> <vectors.txt>
 */
public final class SelfTest {

    public static void main(String[] args) throws Exception {
        String modelPath = args.length > 0 ? args[0] : "assets/model.txt";
        String vectorsPath = args.length > 1 ? args[1] : "desktoptest/vectors.txt";

        ModelLoader.Bundle bundle = ModelLoader.load(new FileInputStream(modelPath));
        System.out.println("vocab=" + bundle.vocabSize
                + " merges=" + bundle.mergeCount
                + " order=" + bundle.model.order()
                + " params=" + bundle.model.parameterCount());

        int failures = 0;
        int checked = 0;
        BufferedReader reader = new BufferedReader(new FileReader(vectorsPath));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) continue;
            int space = line.indexOf(' ');
            String text = new String(Base64.getDecoder().decode(line.substring(0, space)), "UTF-8");
            String[] expectedParts = line.substring(space + 1).split(",");
            int[] actual = bundle.tokenizer.encode(text);
            checked++;
            boolean ok = actual.length == expectedParts.length;
            if (ok) {
                for (int i = 0; i < actual.length; i++) {
                    if (actual[i] != Integer.parseInt(expectedParts[i])) { ok = false; break; }
                }
            }
            if (!ok) {
                failures++;
                System.out.println("  MISMATCH: " + text);
                System.out.println("    expected " + line.substring(space + 1));
                System.out.print("    actual   ");
                for (int i = 0; i < actual.length; i++) {
                    if (i > 0) System.out.print(',');
                    System.out.print(actual[i]);
                }
                System.out.println();
            }
        }
        reader.close();

        System.out.println("tokenizer vectors: " + checked + " checked, " + failures + " mismatches");

        ChatBot bot = new ChatBot(bundle.model);
        String[] questions = {
                "What is EthanGPT?",
                "What is a token?",
                "Can the model run offline?",
                "How do I install Python on Android?"
        };
        System.out.println("\nSample answers:");
        for (String q : questions) {
            System.out.println("Q: " + q);
            System.out.println("A: " + bot.respond(q));
            System.out.println();
        }

        boolean paramsOk = bundle.model.parameterCount() == 10874880L;
        System.out.println("parameter count exact: " + paramsOk);
        if (failures > 0 || !paramsOk) {
            System.out.println("SELFTEST FAILED");
            System.exit(1);
        }
        System.out.println("SELFTEST PASSED");
    }
}
