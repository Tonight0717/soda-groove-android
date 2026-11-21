package kr.sodagroove.player;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.util.LruCache;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

final class Covers {
  interface Result {
    void loaded(Bitmap image);
  }

  static final ExecutorService pool = Executors.newFixedThreadPool(3);
  /** Unit tests switch this off so the suite never touches the network. */
  static boolean offline;
  static final Handler main = new Handler(Looper.getMainLooper());
  static final LruCache<String, Bitmap> memory =
      new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        protected int sizeOf(String key, Bitmap value) {
          return value.getByteCount();
        }
      };

  static void get(Context context, String id, Result result) {
    if (!id.matches("[A-Za-z0-9_-]{11}")) return;
    Bitmap cached = memory.get(id);
    if (cached != null) {
      result.loaded(cached);
      return;
    }
    if (offline) return;
    File dir = new File(context.getCacheDir(), "covers");
    dir.mkdirs();
    pool.execute(
        () -> {
          File file = new File(dir, id + ".jpg");
          Bitmap image = BitmapFactory.decodeFile(file.getPath());
          if (image == null) {
            HttpURLConnection conn = null;
            try {
              conn =
                  (HttpURLConnection)
                      new URL("https://i.ytimg.com/vi/" + id + "/mqdefault.jpg").openConnection();
              conn.setConnectTimeout(5000);
              conn.setReadTimeout(5000);
              try (InputStream in = conn.getInputStream()) {
                image = BitmapFactory.decodeStream(in);
              }
              if (image != null) {
                try (FileOutputStream out = new FileOutputStream(file)) {
                  image.compress(Bitmap.CompressFormat.JPEG, 90, out);
                }
              }
            } catch (IOException ignored) {
            } finally {
              if (conn != null) conn.disconnect();
            }
          }
          if (image != null) {
            memory.put(id, image);
            Bitmap value = image;
            main.post(() -> result.loaded(value));
          }
        });
  }
}
