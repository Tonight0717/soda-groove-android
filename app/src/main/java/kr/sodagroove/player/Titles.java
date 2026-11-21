package kr.sodagroove.player;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.regex.*;
import org.json.JSONException;
import org.json.JSONObject;

/** Reads the real song title (and playlist name) for a link, off the main thread. */
final class Titles {
  interface Result {
    void loaded(String title);
  }

  /** Unit tests switch this off so the suite never touches the network. */
  static boolean offline;

  private static final ExecutorService pool = Executors.newFixedThreadPool(2);
  private static final Handler main = new Handler(Looper.getMainLooper());
  private static final Pattern OG =
      Pattern.compile("<meta[^>]+(?:property|name)=\"og:title\"[^>]*content=\"([^\"]*)\"");
  private static final Pattern OG_REVERSED =
      Pattern.compile("<meta[^>]+content=\"([^\"]*)\"[^>]*(?:property|name)=\"og:title\"");
  private static final Pattern PAGE_TITLE = Pattern.compile("<title>([^<]*)</title>");

  private Titles() {}

  static String cached(Context context, Source source) {
    return context.getSharedPreferences("deck", 0).getString("t/" + source.key(), "");
  }

  static void fetch(Context context, Source source, Result result) {
    String hit = cached(context, source);
    if (!hit.isEmpty()) {
      result.loaded(hit);
      return;
    }
    if (offline) return;
    final Context app = context.getApplicationContext();
    final String key = source.key(), video = source.video, list = source.list;
    pool.execute(
        () -> {
          String found = video.isEmpty() ? playlist(list) : video(video);
          if (found == null || found.isEmpty()) return;
          app.getSharedPreferences("deck", 0).edit().putString("t/" + key, found).apply();
          String value = found;
          main.post(() -> result.loaded(value));
        });
  }

  private static String video(String id) {
    for (String host : new String[] {"www.youtube.com", "music.youtube.com"}) {
      String body =
          text(
              "https://"
                  + host
                  + "/oembed?url="
                  + encode("https://www.youtube.com/watch?v=" + id)
                  + "&format=json");
      if (body == null) continue;
      try {
        String name = new JSONObject(body).optString("title", "").trim();
        if (!name.isEmpty()) return name;
      } catch (JSONException | RuntimeException ignored) {
      }
    }
    return null;
  }

  private static String playlist(String id) {
    String page = text("https://www.youtube.com/playlist?list=" + encode(id));
    if (page == null) return null;
    String name = match(OG, page);
    if (name.isEmpty()) name = match(OG_REVERSED, page);
    if (name.isEmpty()) name = match(PAGE_TITLE, page).replaceFirst("\\s+-\\s+YouTube$", "");
    name = unescape(name).trim();
    return name.isEmpty() || name.contains("youtube.com") ? null : name;
  }

  private static String match(Pattern pattern, String page) {
    Matcher m = pattern.matcher(page);
    return m.find() ? m.group(1) : "";
  }

  private static String unescape(String text) {
    return text.replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&");
  }

  private static String encode(String value) {
    try {
      return URLEncoder.encode(value, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      return value;
    }
  }

  private static String text(String address) {
    HttpURLConnection conn = null;
    try {
      conn = (HttpURLConnection) new URL(address).openConnection();
      conn.setConnectTimeout(4000);
      conn.setReadTimeout(4000);
      conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36");
      if (conn.getResponseCode() != 200) return null;
      try (InputStream in = conn.getInputStream()) {
        return new String(read(in), StandardCharsets.UTF_8);
      }
    } catch (IOException e) {
      return null;
    } finally {
      if (conn != null) conn.disconnect();
    }
  }

  private static byte[] read(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    for (int n; (n = in.read(buffer)) > 0 && out.size() < 512 * 1024; ) out.write(buffer, 0, n);
    return out.toByteArray();
  }
}
