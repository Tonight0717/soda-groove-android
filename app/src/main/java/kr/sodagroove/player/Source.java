package kr.sodagroove.player;

import java.net.URI;
import java.util.*;

final class Source {
  final String video, list;
  final boolean music;

  Source(String video, String list, boolean music) {
    this.video = video;
    this.list = list;
    this.music = music;
  }

  static Source parse(String raw) {
    try {
      String text = raw.trim();
      URI uri = URI.create(text.contains("://") ? text : "https://" + text);
      String host = uri.getHost().toLowerCase(Locale.ROOT);
      boolean shortUrl = host.equals("youtu.be");
      if (!Arrays.asList("https", "http").contains(uri.getScheme())
          || uri.getUserInfo() != null
          || (!shortUrl
              && !Arrays.asList(
                      "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com")
                  .contains(host))) return null;
      Map<String, String> params = new HashMap<>();
      for (String field : Objects.toString(uri.getRawQuery(), "").split("&")) {
        String[] pair = field.split("=", 2);
        if (pair.length == 2) params.put(pair[0], pair[1]);
      }
      String path = Objects.toString(uri.getPath(), "");
      String video = "", list = params.getOrDefault("list", "");
      if (shortUrl) video = path.replaceFirst("^/", "");
      else if (path.equals("/watch")) video = params.getOrDefault("v", "");
      else if (path.startsWith("/shorts/") || path.startsWith("/live/"))
        video = path.substring(path.lastIndexOf('/') + 1);
      else if (!path.equals("/playlist")) return null;
      if (!video.isEmpty() && !video.matches("[A-Za-z0-9_-]{11}")) return null;
      if (!list.isEmpty() && !list.matches("[A-Za-z0-9_-]{2,160}")) return null;
      return video.isEmpty() && list.isEmpty()
          ? null
          : new Source(video, list, host.equals("music.youtube.com"));
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  String url() {
    String base = music ? "https://music.youtube.com/" : "https://www.youtube.com/";
    return base
        + (video.isEmpty()
            ? "playlist?list=" + list
            : "watch?v=" + video + (list.isEmpty() ? "" : "&list=" + list));
  }

  String key() {
    return list.isEmpty() ? "v/" + video : "p/" + list;
  }

  String service() {
    return music ? "YouTube Music" : "YouTube";
  }
}
