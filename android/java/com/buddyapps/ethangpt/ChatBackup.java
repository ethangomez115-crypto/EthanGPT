package com.buddyapps.ethangpt;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

/**
 * Backs up the chat history to a file in the user's shared storage so it
 * survives an uninstall/reinstall, and shares backups with other apps.
 *
 * - Android 10+ (API 29+): written through MediaStore.Downloads so no
 *   storage permission is required.
 * - Older devices: written directly under public Documents (needs the
 *   legacy READ/WRITE_EXTERNAL_STORAGE permissions declared in the manifest).
 */
public final class ChatBackup {

    private static final String NAME = "NexusAI-Chats-Backup.json";
    private static final String SUBDIR = "NexusAI";

    private ChatBackup() { }

    /** Save JSON to shared storage. Returns a content Uri (API 29+) or null. */
    public static Uri save(Context ctx, String json) {
        if (json == null) return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return saveMediaStore(ctx, json);
        }
        saveLegacy(json);
        return null;
    }

    private static Uri saveMediaStore(Context ctx, String json) {
        try {
            android.content.ContentResolver r = ctx.getContentResolver();
            Uri coll = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            // Remove any older backup first so there is only ever one file.
            String sel = MediaStore.Downloads.DISPLAY_NAME + "=?";
            String[] args = new String[] { NAME };
            r.delete(coll, sel, args);

            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, NAME);
            v.put(MediaStore.Downloads.MIME_TYPE, "application/json");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/" + SUBDIR);
            Uri uri = r.insert(coll, v);
            if (uri == null) return null;
            OutputStream os = r.openOutputStream(uri);
            if (os == null) return null;
            try {
                os.write(json.getBytes("UTF-8"));
                os.flush();
            } finally {
                os.close();
            }
            return uri;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean saveLegacy(String json) {
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS), SUBDIR);
            if (!dir.exists() && !dir.mkdirs()) return false;
            File f = new File(dir, NAME);
            FileOutputStream os = new FileOutputStream(f);
            try {
                os.write(json.getBytes("UTF-8"));
                os.flush();
            } finally {
                os.close();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Read the backup JSON from shared storage, or null if none exists. */
    public static String load(Context ctx) {
        String json = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            json = loadMediaStore(ctx);
        }
        if (json == null) {
            json = loadLegacy();
        }
        return json;
    }

    private static String loadMediaStore(Context ctx) {
        Cursor cur = null;
        try {
            android.content.ContentResolver r = ctx.getContentResolver();
            Uri coll = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            String[] proj = new String[] { MediaStore.Downloads._ID };
            String sel = MediaStore.Downloads.DISPLAY_NAME + "=?";
            String[] args = new String[] { NAME };
            cur = r.query(coll, proj, sel, args, MediaStore.Downloads.DATE_ADDED + " DESC");
            if (cur == null || !cur.moveToFirst()) return null;
            long id = cur.getLong(cur.getColumnIndexOrThrow(MediaStore.Downloads._ID));
            Uri uri = Uri.withAppendedPath(coll, String.valueOf(id));
            InputStream in = r.openInputStream(uri);
            if (in == null) return null;
            try {
                return readAll(in);
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (cur != null) cur.close();
        }
    }

    private static String loadLegacy() {
        try {
            File f = new File(new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS), SUBDIR), NAME);
            if (!f.exists()) return null;
            InputStream in = new FileInputStream(f);
            try {
                return readAll(in);
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static String readAll(InputStream in) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    /** Human-readable location for the UI. */
    public static String location() {
        return "Downloads/" + SUBDIR + "/" + NAME;
    }

    /** Hand the backup to any sharing app (whatsApp, Gmail, Drive, etc.). */
    public static void share(Context ctx, String json) {
        Uri uri = save(ctx, json);
        try {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/json");
            send.putExtra(Intent.EXTRA_SUBJECT, "Nexus AI chat backup");
            if (uri != null) {
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                send.putExtra(Intent.EXTRA_TEXT, json == null ? "" : json);
            }
            ctx.startActivity(Intent.createChooser(send, "Share chats"));
        } catch (Exception e) {
            Toast.makeText(ctx, "Nothing available to share with", Toast.LENGTH_SHORT).show();
        }
    }
}
