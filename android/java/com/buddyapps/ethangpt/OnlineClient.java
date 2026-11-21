package com.buddyapps.ethangpt;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Online mode: talks to a free-tier AI provider with the user's own API key.
 *
 * Four providers are supported:
 *   - Groq        (OpenAI-compatible, free key)
 *   - OpenRouter  (OpenAI-compatible, has free models)
 *   - Google Gemini (free key)
 *   - DeepSeek    (OpenAI-compatible, free credits on signup)
 *
 * The key is stored only in this app's private SharedPreferences and is sent
 * only to the provider the user picked. Nothing is sent anywhere else.
 */
public final class OnlineClient {

    public static final String[] PROVIDER_IDS = {"openrouter", "groq", "gemini", "deepseek"};
    public static final String[] PROVIDER_LABELS = {
            "OpenRouter (free models)",
            "Groq (free key)",
            "Google Gemini (free key)",
            "DeepSeek (free credits)"
    };
    // openrouter/free is a router that picks an available free model, so the
    // app keeps working when individual free models come and go.
    public static final String[] DEFAULT_MODELS = {
            "openrouter/free",
            "llama-3.3-70b-versatile",
            "gemini-2.5-flash",
            "deepseek-chat"
    };
    public static final String[] KEY_URLS = {
            "https://openrouter.ai/keys",
            "https://console.groq.com/keys",
            "https://aistudio.google.com/apikey",
            "https://platform.deepseek.com/api_keys"
    };

    private static final String SYSTEM =
            "You are Nexus AI, a helpful and concise assistant running on the user's "
                    + "Android phone. Answer clearly and briefly in plain text.";

    private OnlineClient() { }

    public static String providerId(int index) {
        if (index < 0 || index >= PROVIDER_IDS.length) return PROVIDER_IDS[0];
        return PROVIDER_IDS[index];
    }

    public static String defaultModel(int index) {
        if (index < 0 || index >= DEFAULT_MODELS.length) return DEFAULT_MODELS[0];
        return DEFAULT_MODELS[index];
    }

    public static String chat(String provider, String apiKey, String model,
                              List<String[]> history, String message) throws Exception {
        return chat(provider, apiKey, model, history, message, 1, 1);
    }

    /**
     * @param thinking 0=low, 1=medium, 2=high reasoning effort
     * @param context  0=short, 1=standard, 2=long reply / history budget
     */
    public static String chat(String provider, String apiKey, String model,
                              List<String[]> history, String message,
                              int thinking, int context) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("No online key is available.");
        }
        String key = apiKey.trim();
        String chosen = (model == null || model.trim().isEmpty())
                ? defaultModel(indexOf(provider)) : model.trim();

        if ("gemini".equals(provider)) {
            return gemini(key, chosen, history, message, thinking, context);
        }
        return openAiCompatible(provider, key, chosen, history, message, thinking, context);
    }

    private static int indexOf(String provider) {
        for (int i = 0; i < PROVIDER_IDS.length; i++) {
            if (PROVIDER_IDS[i].equals(provider)) return i;
        }
        return 0;
    }

    private static String openAiCompatible(String provider, String key, String model,
                                           List<String[]> history, String message,
                                           int thinking, int context) throws Exception {
        boolean openRouter = "openrouter".equals(provider);
        String endpoint;
        if (openRouter) endpoint = "https://openrouter.ai/api/v1/chat/completions";
        else if ("deepseek".equals(provider)) endpoint = "https://api.deepseek.com/v1/chat/completions";
        else endpoint = "https://api.groq.com/openai/v1/chat/completions";

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", SYSTEM));
        for (String[] turn : history) {
            String role = "assistant".equals(turn[0]) ? "assistant" : "user";
            messages.put(new JSONObject().put("role", role).put("content", turn[1]));
        }
        messages.put(new JSONObject().put("role", "user").put("content", message));

        int maxTokens = context == 0 ? 700 : (context >= 2 ? 4000 : 1800);
        double temperature = thinking == 0 ? 0.25 : (thinking >= 2 ? 0.85 : 0.7);
        JSONObject body = new JSONObject()
                .put("model", model)
                .put("messages", messages)
                .put("temperature", temperature)
                .put("max_tokens", maxTokens);
        // OpenRouter is the only endpoint here that accepts a reasoning-effort
        // object; sending it to Groq or DeepSeek can be rejected as unknown.
        if (openRouter && thinking > 0) {
            body.put("reasoning", new JSONObject().put("effort",
                    thinking >= 2 ? "high" : "medium"));
        }

        String raw = post(endpoint, body, key, openRouter);
        JSONObject json = new JSONObject(raw);
        if (json.has("error")) {
            throw new Exception(brief(json.get("error").toString()));
        }
        JSONArray choices = json.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            throw new Exception("The provider returned no answer. " + brief(raw));
        }
        JSONObject first = choices.getJSONObject(0);
        JSONObject msg = first.optJSONObject("message");
        String content = "";
        if (msg != null) {
            Object value = msg.opt("content");
            if (value != null && value != JSONObject.NULL) content = value.toString();
        } else {
            Object value = first.opt("text");
            if (value != null && value != JSONObject.NULL) content = value.toString();
        }
        content = content.trim();
        if (content.isEmpty()) {
            // A reasoning model may have used the whole budget thinking.
            Object reasoning = msg == null ? null : msg.opt("reasoning");
            if (reasoning != null && reasoning != JSONObject.NULL) {
                String thought = reasoning.toString().trim();
                if (thought.length() > 400) thought = thought.substring(0, 400) + "...";
                throw new Exception("The free model ran out of tokens while thinking. "
                        + "Try again or pick a different model. It was thinking: " + thought);
            }
            throw new Exception("The provider returned an empty answer.");
        }
        return content;
    }

    private static String gemini(String key, String model, List<String[]> history,
                                 String message, int thinking, int context) throws Exception {
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/"
                + URLEncoder.encode(model, "UTF-8")
                + ":generateContent?key=" + URLEncoder.encode(key, "UTF-8");

        JSONArray contents = new JSONArray();
        for (String[] turn : history) {
            String role = "assistant".equals(turn[0]) ? "model" : "user";
            contents.put(new JSONObject()
                    .put("role", role)
                    .put("parts", new JSONArray().put(new JSONObject().put("text", turn[1]))));
        }
        contents.put(new JSONObject()
                .put("role", "user")
                .put("parts", new JSONArray().put(new JSONObject().put("text", message))));

        JSONObject gen = new JSONObject()
                .put("maxOutputTokens", context == 0 ? 700 : (context >= 2 ? 4000 : 1800))
                .put("temperature", thinking == 0 ? 0.25 : (thinking >= 2 ? 0.85 : 0.7))
                .put("thinkingConfig", new JSONObject().put("thinkingBudget",
                        thinking == 0 ? 1024 : (thinking >= 2 ? 8192 : 4096)));
        JSONObject body = new JSONObject()
                .put("contents", contents)
                .put("generationConfig", gen)
                .put("systemInstruction", new JSONObject()
                        .put("parts", new JSONArray().put(new JSONObject().put("text", SYSTEM))));

        String raw = post(endpoint, body, null, false);
        JSONObject json = new JSONObject(raw);
        if (json.has("error")) {
            throw new Exception(brief(json.get("error").toString()));
        }
        JSONArray candidates = json.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            throw new Exception("Gemini returned no answer. " + brief(raw));
        }
        JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
        if (content == null) throw new Exception("Gemini returned an empty answer.");
        JSONArray parts = content.optJSONArray("parts");
        if (parts == null) throw new Exception("Gemini returned an empty answer.");
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            text.append(parts.getJSONObject(i).optString("text", ""));
        }
        String answer = text.toString().trim();
        if (answer.isEmpty()) throw new Exception("Gemini returned an empty answer.");
        return answer;
    }

    /** Fetch the model ids that are currently free on OpenRouter. */
    public static String[] listFreeModels() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(
                "https://openrouter.ai/api/v1/models").openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        int code = conn.getResponseCode();
        InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String raw = in == null ? "" : readAll(in);
        if (code >= 400) throw new Exception("HTTP " + code + ": " + brief(raw));

        JSONObject json = new JSONObject(raw);
        JSONArray data = json.optJSONArray("data");
        if (data == null) throw new Exception("Could not read the OpenRouter model list.");
        java.util.ArrayList<String> ids = new java.util.ArrayList<String>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject m = data.getJSONObject(i);
            String id = m.getString("id");
            // Lyria is audio generation, not a chat model.
            if (id.contains("lyria")) continue;
            JSONObject pricing = m.optJSONObject("pricing");
            if (pricing == null) continue;
            if (!"0".equals(pricing.optString("prompt"))
                    || !"0".equals(pricing.optString("completion"))) continue;
            JSONArray params = m.optJSONArray("supported_parameters");
            boolean chat = false;
            if (params != null) {
                for (int j = 0; j < params.length(); j++) {
                    String p = params.optString(j, "");
                    if ("temperature".equals(p) || "max_tokens".equals(p)
                            || "max_completion_tokens".equals(p)) {
                        chat = true;
                        break;
                    }
                }
            }
            if (chat) ids.add(m.getString("id"));
        }
        java.util.Collections.sort(ids);
        int router = ids.indexOf("openrouter/free");
        if (router > 0) {
            ids.remove(router);
            ids.add(0, "openrouter/free");
        }
        return ids.toArray(new String[0]);
    }

    private static String post(String endpoint, JSONObject body, String apiKey,
                               boolean openRouter) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(90000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        if (apiKey != null && !apiKey.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        if (openRouter) {
            conn.setRequestProperty("HTTP-Referer",
                    "https://github.com/ethangomez115-crypto/EthanGPT");
            conn.setRequestProperty("X-Title", "Nexus AI");
        }

        OutputStream out = conn.getOutputStream();
        out.write(body.toString().getBytes(Charset.forName("UTF-8")));
        out.close();

        int code = conn.getResponseCode();
        InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String text = in == null ? "" : readAll(in);
        if (code >= 400) {
            throw new Exception("HTTP " + code + ": " + brief(text));
        }
        return text;
    }

    private static String readAll(InputStream in) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, Charset.forName("UTF-8")));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) builder.append(line).append('\n');
        reader.close();
        return builder.toString();
    }

    private static String brief(String text) {
        if (text == null) return "";
        String oneLine = text.replace('\n', ' ').replace('\r', ' ').trim();
        return oneLine.length() > 240 ? oneLine.substring(0, 240) + "..." : oneLine;
    }
}
