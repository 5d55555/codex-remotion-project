package com.pocketcodex.app;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * 输入框附件的导入（在后台线程调用）：照片缩到长边 2048 像素、按 EXIF 转正、存成 JPEG；
 * 文件原样复制。都放进应用私有的附件目录，Codex 用路径读（和 App 同一个用户 ID）。
 */
final class Attach {
    private Attach() {
    }

    static final int MAX_SIDE = 2048;
    /** 聊天模式直接附上内容的文本文件上限。 */
    static final int INLINE_LIMIT = 64 * 1024;

    static File dir(CodexRuntimeManager rt) {
        File d = rt.paths.attachments;
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    /** 读图片 → 缩放、转正 → JPEG。不是图片返回 null。 */
    static File importImage(Context c, CodexRuntimeManager rt, Uri uri) throws IOException {
        ContentResolver cr = c.getContentResolver();
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) throw new IOException("打不开这张图片");
            BitmapFactory.decodeStream(in, null, o);
        }
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        Bitmap b;
        try (InputStream in = cr.openInputStream(uri)) {
            b = BitmapFactory.decodeStream(in, null, o2);
        }
        if (b == null) return null;
        int rot = 0;
        try (InputStream in = cr.openInputStream(uri)) {
            if (in != null) {
                android.media.ExifInterface ex = new android.media.ExifInterface(in);
                int or = ex.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL);
                rot = or == android.media.ExifInterface.ORIENTATION_ROTATE_90 ? 90 : or == android.media.ExifInterface.ORIENTATION_ROTATE_180 ? 180
                        : or == android.media.ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
            }
        } catch (Exception ignored) {
            // 没有 EXIF（PNG 等）
        }
        float scale = Math.min(1f, MAX_SIDE / (float) Math.max(b.getWidth(), b.getHeight()));
        if (scale < 1f || rot != 0) {
            Matrix m = new Matrix();
            m.postScale(scale, scale);
            m.postRotate(rot);
            Bitmap nb = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            if (nb != b) b.recycle();
            b = nb;
        }
        File f = new File(dir(rt), "IMG_" + UUID.randomUUID().toString().substring(0, 8) + ".jpg");
        try (OutputStream out = new FileOutputStream(f)) {
            b.compress(Bitmap.CompressFormat.JPEG, 88, out);
        }
        b.recycle();
        return f;
    }

    /** 文件原样复制，返回 {文件, 显示名}。 */
    static Object[] importFile(Context c, CodexRuntimeManager rt, Uri uri) throws IOException {
        String name = displayName(c, uri);
        if (name == null || name.trim().isEmpty()) name = "附件";
        name = name.replaceAll("[/\\\\\\x00]", "_");
        File d = new File(dir(rt), UUID.randomUUID().toString().substring(0, 8));
        d.mkdirs();
        File f = new File(d, name);
        long total = 0;
        try (InputStream in = c.getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(f)) {
            if (in == null) throw new IOException("打不开这个文件");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                total += n;
                if (total > 512L * 1024 * 1024) throw new IOException("文件太大（超过 512 MB）");
            }
        }
        return new Object[]{f, name};
    }

    static String displayName(Context c, Uri uri) {
        if ("file".equals(uri.getScheme())) return new File(uri.getPath()).getName();
        try (Cursor cur = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) return cur.getString(0);
        } catch (Exception ignored) {
            // 有的文件管理器不支持查询
        }
        String last = uri.getLastPathSegment();
        return last == null ? null : last.substring(last.lastIndexOf('/') + 1);
    }

    /** 小的纯文本文件返回内容（UTF-8，没有 NUL），否则 null。 */
    static String readTextIfSmall(File f) {
        if (!f.isFile() || f.length() > INLINE_LIMIT) return null;
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            byte[] all = b.toByteArray();
            for (byte x : all) if (x == 0) return null;
            String s = new String(all, StandardCharsets.UTF_8);
            return s.indexOf('�') >= 0 ? null : s;
        } catch (IOException e) {
            return null;
        }
    }

    static boolean isImageName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".heic") || n.endsWith(".bmp");
    }

    /** 缩略图（小一点，界面用）。 */
    static Bitmap thumb(String path, int sizePx) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        if (o.outWidth <= 0) return null;
        int s = 1;
        while (Math.min(o.outWidth, o.outHeight) / (s * 2) >= sizePx) s *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = s;
        return BitmapFactory.decodeFile(path, o2);
    }
}
