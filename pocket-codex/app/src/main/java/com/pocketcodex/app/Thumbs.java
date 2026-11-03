package com.pocketcodex.app;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 缩略图：后台解码 + 按字节计的内存缓存（最多 16 MB，或可用内存的 1/16）。
 * 对话里的图片、输入框附件栏都走这里，滚动和刷新时不再在主线程读文件、解码。
 * 附件和对话图片都放在应用私有目录、文件名唯一、不会原地改写，所以缓存键只用路径和尺寸。
 */
final class Thumbs {
    private Thumbs() {
    }

    /** 截图测试里同步解码（和 Overlay.animate 一样的测试开关）。 */
    static boolean synchronous = false;

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(cacheBytes()) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getByteCount();
        }
    };

    private static final ExecutorService IO = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "thumbs");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static int cacheBytes() {
        return (int) Math.min(16L << 20, Runtime.getRuntime().maxMemory() / 16);
    }

    private static String key(String path, int sizePx) {
        return path + "|" + sizePx;
    }

    /**
     * 把缩略图放进 iv：缓存里有就直接用；没有先放占位（placeholderRes 为 0 就留空），后台解码完再放。
     * 解码期间 iv 被复用给别的图（RecyclerView 复用、附件栏重建）时，旧结果直接丢弃。
     */
    static void load(final ImageView iv, final String path, final int sizePx, int placeholderRes) {
        final String k = key(path, sizePx);
        iv.setTag(R.id.tag_thumb, k);
        Bitmap hit = CACHE.get(k);
        if (hit != null) {
            iv.setImageBitmap(hit);
            return;
        }
        if (placeholderRes != 0) iv.setImageResource(placeholderRes);
        else iv.setImageDrawable(null);
        if (synchronous) {
            Bitmap bm = decode(k, path, sizePx);
            if (bm != null) iv.setImageBitmap(bm);
            return;
        }
        IO.execute(() -> {
            final Bitmap bm = decode(k, path, sizePx);
            if (bm == null) return; // 解码失败：留着占位
            MAIN.post(() -> {
                if (k.equals(iv.getTag(R.id.tag_thumb))) iv.setImageBitmap(bm);
            });
        });
    }

    /** 很小、数量很少的图标（插件图标）：同步取，但只解码一次。解码失败返回 null。 */
    static Bitmap getSync(String path, int sizePx) {
        String k = key(path, sizePx);
        Bitmap hit = CACHE.get(k);
        return hit != null ? hit : decode(k, path, sizePx);
    }

    private static Bitmap decode(String k, String path, int sizePx) {
        Bitmap bm;
        try {
            bm = Attach.thumb(path, sizePx);
        } catch (OutOfMemoryError e) {
            return null;
        }
        if (bm != null) CACHE.put(k, bm);
        return bm;
    }
}
