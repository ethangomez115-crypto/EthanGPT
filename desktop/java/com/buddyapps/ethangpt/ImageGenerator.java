package com.buddyapps.ethangpt;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/** Generates an image from a text prompt using a free, keyless public endpoint. */
public final class ImageGenerator {
    private ImageGenerator() { }

    public static byte[] generate(String prompt) throws Exception {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new Exception("Enter an image description first.");
        }
        String url = "https://image.pollinations.ai/prompt/"
                + URLEncoder.encode(prompt.trim(), "UTF-8")
                + "?width=512&height=512&nologo=true";
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(90000);
        int code = conn.getResponseCode();
        InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (in == null) throw new Exception("No response from the image service.");
        try {
            if (code >= 400) {
                byte[] err = readAll(in);
                throw new Exception("Image service HTTP " + code + ": "
                        + new String(err, 0, Math.min(err.length, 160), "UTF-8"));
            }
            return readAll(in);
        } finally {
            in.close();
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
