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

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/** Saves generated images/files to shared storage and shares them by content URI. */
public final class NexusFiles {
    private NexusFiles() { }

    /** Save bytes under Download/NexusAI/<subdir>. Returns a shareable Uri. */
    public static Uri save(Context ctx, String name, String mime, String subdir, byte[] data) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return saveMediaStore(ctx, name, mime, subdir, data);
        }
        return saveLegacy(name, subdir, data);
    }

    private static Uri saveMediaStore(Context ctx, String name, String mime,
                                      String subdir, byte[] data) {
        try {
            android.content.ContentResolver r = ctx.getContentResolver();
            Uri coll = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            String sel = MediaStore.Downloads.DISPLAY_NAME + "=?";
            r.delete(coll, sel, new String[] { name });
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, mime);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/NexusAI/" + subdir);
            Uri uri = r.insert(coll, v);
            if (uri == null) return null;
            OutputStream os = r.openOutputStream(uri);
            if (os == null) return null;
            try {
                os.write(data);
                os.flush();
            } finally {
                os.close();
            }
            return uri;
        } catch (Exception e) {
            return null;
        }
    }

    private static Uri saveLegacy(String name, String subdir, byte[] data) {
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "NexusAI/" + subdir);
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, name);
            FileOutputStream os = new FileOutputStream(f);
            try {
                os.write(data);
                os.flush();
            } finally {
                os.close();
            }
            return Uri.fromFile(f);
        } catch (Exception e) {
            return null;
        }
    }

    /** Share a saved file/image with any app that accepts the MIME type. */
    public static void share(Context ctx, Uri uri, String mime, String subject) {
        if (uri == null) {
            Toast.makeText(ctx, "Nothing to share yet", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType(mime);
            send.putExtra(Intent.EXTRA_SUBJECT, subject);
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(Intent.createChooser(send, "Send with"));
        } catch (Exception e) {
            Toast.makeText(ctx, "Nothing available to share with", Toast.LENGTH_SHORT).show();
        }
    }

    public static String textLocation(String subdir) {
        return "Download/NexusAI/" + subdir;
    }
}
