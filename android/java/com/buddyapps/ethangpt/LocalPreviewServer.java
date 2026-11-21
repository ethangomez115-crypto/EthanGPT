package com.buddyapps.ethangpt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A tiny loopback-only HTTP server used to preview websites generated in the
 * Code IDE. It binds to 127.0.0.1 only, so nothing is exposed to the network.
 * Pure Java — shared by the Android and desktop builds.
 */
public final class LocalPreviewServer {
    private LocalPreviewServer() { }

    private static volatile ServerSocket server;
    private static volatile File root;
    private static volatile int port = 0;

    /** Start (or restart) the server rooted at {@code rootDir}. Returns the preview URL. */
    public static synchronized String start(File rootDir) {
        stop();
        root = rootDir;
        if (root != null && !root.exists()) root.mkdirs();
        try {
            server = new ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"));
            port = server.getLocalPort();
        } catch (Exception e) {
            server = null;
            port = 0;
            return null;
        }
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                while (server != null && !server.isClosed()) {
                    try {
                        final Socket s = server.accept();
                        Thread worker = new Thread(new Runnable() {
                            @Override public void run() { handle(s); }
                        }, "nexus-preview-req");
                        worker.setDaemon(true);
                        worker.start();
                    } catch (Exception e) {
                        break;
                    }
                }
            }
        }, "nexus-preview");
        t.setDaemon(true);
        t.start();
        return url();
    }

    /** The current preview URL, or null if the server is not running. */
    public static String url() {
        if (port <= 0) return null;
        return "http://127.0.0.1:" + port + "/index.html";
    }

    public static synchronized void stop() {
        if (server != null) {
            try { server.close(); } catch (Exception ignored) { }
            server = null;
        }
        port = 0;
    }

    public static boolean isRunning() {
        return server != null && !server.isClosed();
    }

    private static void handle(Socket socket) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), Charset.forName("UTF-8")));
            String request = in.readLine();
            if (request == null) { socket.close(); return; }
            String path = request.split(" ")[1];
            int q = path.indexOf('?');
            if (q >= 0) path = path.substring(0, q);
            path = URLDecoder.decode(path, "UTF-8");
            if (path.equals("/") || path.isEmpty()) path = "/index.html";

            File base = root;
            File file = (base == null) ? null : new File(base, path.replaceFirst("^/", ""));
            // Block path traversal outside the preview directory.
            if (file == null || !file.getCanonicalPath().startsWith(base.getCanonicalPath())) {
                send(socket, "404 Not Found", "text/plain; charset=utf-8",
                        "Not found".getBytes("UTF-8"));
                return;
            }
            if (!file.exists() || file.isDirectory()) {
                send(socket, "404 Not Found", "text/plain; charset=utf-8",
                        ("Not found: " + path).getBytes("UTF-8"));
                return;
            }
            byte[] data = readAll(file);
            send(socket, "200 OK", contentType(file.getName()), data);
        } catch (Exception e) {
            try { socket.close(); } catch (Exception ignored) { }
        }
    }

    private static void send(Socket socket, String status, String type, byte[] body)
            throws Exception {
        OutputStream out = socket.getOutputStream();
        String header = "HTTP/1.1 " + status + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        out.write(header.getBytes("UTF-8"));
        out.write(body);
        out.flush();
        socket.close();
    }

    private static byte[] readAll(File f) throws Exception {
        InputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[(int) f.length()];
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) break;
                off += n;
            }
            byte[] result = new byte[off];
            System.arraycopy(buf, 0, result, 0, off);
            return result;
        } finally {
            in.close();
        }
    }

    private static String contentType(String name) {
        String n = name.toLowerCase(Locale.US);
        if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (n.endsWith(".json")) return "application/json; charset=utf-8";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".txt")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }
}
