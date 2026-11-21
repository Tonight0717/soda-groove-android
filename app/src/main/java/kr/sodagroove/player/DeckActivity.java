package kr.sodagroove.player;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.util.Locale;
import org.json.*;

public class DeckActivity extends Activity {
  private static final int NAVY = 0xff103f50, PAPER = 0xfff7fbf8;
  private static final int[] PALETTE = {0xffa4e2f6, 0xffa6ebce, 0xffffb7aa};
  private static final String[] COLORS = {"하늘", "민트", "코랄"};
  private final Handler handler = new Handler(Looper.getMainLooper());
  private SharedPreferences prefs;
  private LinearLayout root, controls, sheet;
  private TextView title, status, clock, service;
  private Button original;
  private SeekBar seek;
  private Platter deck;
  private WebView web;
  private Source source;
  private String playerHtml, songId = "", songTitle = "", lastMessage = "";
  private double duration;
  private long started;
  private int generation;
  private boolean active, shelf, scrubbing;
  private final Runnable heartbeat = this::poll;

  private int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private GradientDrawable shape(int color, int radius) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(color);
    g.setCornerRadius(dp(radius));
    return g;
  }

  private LinearLayout column() {
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    return layout;
  }

  private LinearLayout row() {
    LinearLayout layout = new LinearLayout(this);
    layout.setGravity(Gravity.CENTER_VERTICAL);
    return layout;
  }

  private TextView label(String s, int size, boolean bold) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextColor(NAVY);
    t.setTextSize(size);
    if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    return t;
  }

  private Button button(String caption, Runnable action) {
    Button b = new Button(this);
    b.setAllCaps(false);
    b.setText(caption);
    b.setTextSize(14);
    b.setTextColor(NAVY);
    b.setBackgroundTintList(ColorStateList.valueOf(0xffecf7f4));
    b.setOnClickListener(v -> action.run());
    return b;
  }

  private void pad(View v, int x, int y) {
    v.setPadding(dp(x), dp(y), dp(x), dp(y));
  }

  private LinearLayout.LayoutParams weight() {
    return new LinearLayout.LayoutParams(0, -2, 1);
  }

  private void screen() {
    root = column();
    root.setBackgroundColor(PAPER);
    setContentView(root);
    getWindow().setStatusBarColor(PAPER);
    getWindow().setNavigationBarColor(NAVY);
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    prefs = getSharedPreferences("deck", 0);
    try (InputStream in = getAssets().open("player.html");
        java.util.Scanner scan = new java.util.Scanner(in, "UTF-8").useDelimiter("\\A")) {
      playerHtml = scan.next();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    String last = prefs.getString("lastUrl", "");
    source = Source.parse(state != null ? state.getString("source", last) : last);
    if ("kr.sodagroove.LAST".equals(getIntent().getAction())) source = Source.parse(last);
    if (state != null && state.getBoolean("shelf")) showShelf();
    else showDeck();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putBoolean("shelf", shelf);
    out.putString("source", source == null ? "" : source.url());
  }

  @Override
  protected void onNewIntent(Intent i) {
    super.onNewIntent(i);
    setIntent(i);
    if ("kr.sodagroove.LAST".equals(i.getAction())) {
      source = Source.parse(prefs.getString("lastUrl", ""));
      showDeck();
    }
  }

  private void showDeck() {
    disposePlayer();
    shelf = false;
    screen();
    LinearLayout top = row();
    pad(top, 12, 2);
    root.addView(top);
    TextView word = label("Soda Groove", 24, true);
    word.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
    top.addView(word, weight());
    top.addView(button("색상", this::colors));
    top.addView(button("진열장", this::showShelf));
    ScrollView scroll = new ScrollView(this);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    controls = column();
    pad(controls, 14, 6);
    scroll.addView(controls);
    sheet = column();
    sheet.setBackground(shape(PALETTE[prefs.getInt("color", 0)], 22));
    pad(sheet, 14, 10);
    controls.addView(sheet);
    LinearLayout header = row();
    sheet.addView(header);
    service = label(source == null ? "소다그루브" : source.service(), 13, true);
    header.addView(service, weight());
    header.addView(button("링크 열기", this::linkDialog));
    deck = new Platter(this);
    deck.seek = this::seekTo;
    int h = Math.max(140, Math.min(245, getResources().getConfiguration().screenHeightDp - 480));
    sheet.addView(deck, -1, dp(h));
    title = label("첫 레코드를 올려 보세요", 20, true);
    title.setMaxLines(2);
    title.setEllipsize(TextUtils.TruncateAt.END);
    sheet.addView(title);
    LinearLayout track = row();
    sheet.addView(track);
    seek = new SeekBar(this);
    seek.setMax(1000);
    track.addView(seek, weight());
    clock = label("0:00 / 0:00", 12, false);
    track.addView(clock);
    seek.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar b) {
            scrubbing = true;
          }

          public void onStopTrackingTouch(SeekBar b) {
            seekTo(b.getProgress() / 1000f);
            scrubbing = false;
          }

          public void onProgressChanged(SeekBar b, int n, boolean user) {
            if (user) clock.setText(time(duration * n / 1000) + " / " + time(duration));
          }
        });
    seek.setEnabled(false);
    LinearLayout actions = row();
    controls.addView(actions);
    actions.addView(button("진열장에 저장", this::save), weight());
    original = button("원본 열기", this::openOriginal);
    actions.addView(original, weight());
    status = label("", 14, false);
    controls.addView(status);
    if (source == null) {
      message("유튜브 또는 유튜브 뮤직 링크를 열어 시작하세요.");
      original.setEnabled(false);
    } else loadPlayer();
  }

  private void colors() {
    new AlertDialog.Builder(this)
        .setTitle("턴테이블 색상")
        .setSingleChoiceItems(
            COLORS,
            prefs.getInt("color", 0),
            (d, i) -> {
              prefs.edit().putInt("color", i).apply();
              sheet.setBackground(shape(PALETTE[i], 22));
              d.dismiss();
            })
        .show();
  }

  private void linkDialog() {
    EditText url = new EditText(this);
    url.setSingleLine(true);
    url.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
    url.setHint("공유에서 복사한 링크");
    LinearLayout box = column();
    pad(box, 18, 4);
    box.addView(url);
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle("곡 또는 재생목록 열기")
            .setView(box)
            .setNegativeButton("취소", null)
            .setPositiveButton("열기", null)
            .create();
    d.setOnShowListener(
        x ->
            d.getButton(-1)
                .setOnClickListener(
                    v -> {
                      Source next = Source.parse(url.getText().toString());
                      if (next == null) {
                        url.setError("유튜브 곡 또는 재생목록 링크를 확인하세요.");
                        return;
                      }
                      source = next;
                      d.dismiss();
                      showDeck();
                    }));
    d.show();
  }

  private void loadPlayer() {
    service.setText(source.service());
    title.setText(source.list.isEmpty() ? "곡 불러오는 중" : "재생목록");
    original.setText(source.service() + " 열기");
    message("공식 플레이어의 ▶ 버튼으로 재생하세요.");
    songId = "";
    songTitle = "";
    duration = 0;
    started = SystemClock.elapsedRealtime();
    if (!source.video.isEmpty()) setTrack(source.video, "");
    web = new WebView(this);
    web.setBackgroundColor(NAVY);
    web.setContentDescription("유튜브 공식 플레이어");
    web.getSettings().setJavaScriptEnabled(true);
    web.getSettings().setDomStorageEnabled(true);
    web.getSettings().setAllowFileAccess(false);
    web.getSettings().setAllowContentAccess(false);
    web.setWebChromeClient(new WebChromeClient());
    web.setWebViewClient(
        new WebViewClient() {
          @Override
          public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            if (!req.isForMainFrame()) return false;
            if (req.hasGesture() && "https".equals(req.getUrl().getScheme()))
              external(req.getUrl());
            return true;
          }
        });
    int height =
        Math.max(204, Math.min(268, getResources().getConfiguration().screenWidthDp * 9 / 16));
    root.addView(web, new LinearLayout.LayoutParams(-1, dp(height)));
    JSONObject spec = new JSONObject();
    put(spec, "video", source.video);
    put(spec, "list", source.list);
    web.loadDataWithBaseURL(
        "https://" + getPackageName() + "/",
        playerHtml.replace("__SOURCE__", spec.toString()),
        "text/html",
        "UTF-8",
        null);
    if (active) startPolling();
  }

  private void startPolling() {
    handler.removeCallbacks(heartbeat);
    handler.post(heartbeat);
  }

  private void poll() {
    if (!active || web == null) return;
    int serial = generation;
    web.evaluateJavascript(
        "typeof snapshot==='function'?snapshot():null",
        value -> {
          if (serial != generation || !active || web == null) return;
          try {
            if (!"null".equals(value)) render(new JSONObject(value));
            else waiting();
          } catch (JSONException e) {
            waiting();
          }
          handler.removeCallbacks(heartbeat);
          handler.postDelayed(heartbeat, 400);
        });
  }

  private void waiting() {
    if (SystemClock.elapsedRealtime() - started > 14000)
      message("플레이어 연결이 늦어요. 인터넷 연결을 확인한 뒤 링크를 다시 열거나 원본에서 들어 보세요.");
  }

  private void render(JSONObject data) {
    int error = data.optInt("error"), state = data.optInt("state", -1);
    boolean ready = data.optBoolean("ready");
    deck.playing = ready && error == 0 && state == 1;
    deck.invalidate();
    duration = data.optDouble("duration", 0);
    double seconds = data.optDouble("seconds", 0);
    deck.canSeek = ready && error == 0 && duration > 0;
    seek.setEnabled(deck.canSeek);
    deck.fraction = duration > 0 ? (float) Math.min(1, seconds / duration) : 0;
    if (!scrubbing) {
      seek.setProgress(Math.round(deck.fraction * 1000));
      clock.setText(time(seconds) + " / " + time(duration));
    }
    if (error != 0) {
      message(
          error == 101 || error == 150
              ? "이 곡은 외부 앱 재생이 제한돼요. 원본 열기를 눌러 주세요."
              : "재생할 수 없어요 (" + error + "). 원본에서 확인해 주세요.");
      return;
    }
    if (!ready) {
      waiting();
      return;
    }
    String id = data.optString("id"), name = data.optString("title");
    if (id.matches("[A-Za-z0-9_-]{11}")) {
      setTrack(id, name);
      if (deck.playing) remember();
    }
    if (deck.playing) message(source.list.isEmpty() ? "재생 중" : "재생목록 재생 중");
    else if (state == 2) message("일시정지");
    else if (state == 0) message("재생이 끝났어요.");
    else if (state == 3) message("불러오는 중…");
    else message("공식 플레이어의 ▶ 버튼으로 재생하세요.");
  }

  private void setTrack(String id, String name) {
    if (!id.equals(songId)) {
      songId = id;
      songTitle = "";
      deck.cover = null;
      deck.invalidate();
      int serial = generation;
      Covers.get(
          this,
          id,
          b -> {
            if (serial == generation && id.equals(songId)) {
              deck.cover = b;
              deck.invalidate();
              if (id.equals(prefs.getString("lastId", ""))) DeckWidget.update(this);
            }
          });
    }
    if (!name.isEmpty()) songTitle = name;
    title.setText(songTitle.isEmpty() ? "YouTube · " + id : songTitle);
  }

  private void remember() {
    String url = new Source(songId, "", source.music).url(), name = title.getText().toString();
    if (url.equals(prefs.getString("lastUrl", "")) && name.equals(prefs.getString("lastTitle", "")))
      return;
    prefs
        .edit()
        .putString("lastUrl", url)
        .putString("lastId", songId)
        .putString("lastTitle", name)
        .apply();
    DeckWidget.update(this);
  }

  private static String time(double t) {
    int v = (int) Math.max(0, t);
    return String.format(Locale.ROOT, "%d:%02d", v / 60, v % 60);
  }

  private void seekTo(float f) {
    if (web != null && deck.canSeek)
      web.evaluateJavascript("seek(" + Float.toString(Math.max(0, Math.min(1, f))) + ")", null);
  }

  private void message(String text) {
    if (!text.equals(lastMessage) || status.length() == 0) status.setText(text);
    lastMessage = text;
  }

  private void external(Uri uri) {
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, uri));
    } catch (ActivityNotFoundException ignored) {
      toast("링크를 열 수 있는 앱이 없어요.");
    }
  }

  private void openOriginal() {
    if (source != null) external(Uri.parse(source.url()));
  }

  private void disposePlayer() {
    generation++;
    handler.removeCallbacks(heartbeat);
    if (web != null) {
      web.onPause();
      ((ViewGroup) web.getParent()).removeView(web);
      web.destroy();
      web = null;
    }
    if (deck != null) {
      deck.playing = false;
      deck.canSeek = false;
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    active = true;
    if (web != null) {
      web.onResume();
      startPolling();
    }
  }

  @Override
  protected void onPause() {
    active = false;
    pausePlayer();
    super.onPause();
  }

  private void pausePlayer() {
    handler.removeCallbacks(heartbeat);
    if (web != null) {
      web.evaluateJavascript("if(typeof player!=='undefined' && ready)player.pauseVideo()", null);
      web.onPause();
      deck.playing = false;
      deck.invalidate();
    }
  }

  @Override
  protected void onStop() {
    super.onStop();
    if (web != null) {
      disposePlayer();
    }
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (source != null && !shelf && web == null) showDeck();
  }

  @Override
  protected void onDestroy() {
    disposePlayer();
    super.onDestroy();
  }

  private JSONObject library() {
    try {
      return new JSONObject(prefs.getString("library", "{}"));
    } catch (JSONException e) {
      return new JSONObject();
    }
  }

  private static void put(JSONObject o, String k, String v) {
    try {
      o.put(k, v);
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private void toast(String text) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
  }

  private void save() {
    if (source == null) {
      toast("먼저 링크를 열어 주세요.");
      return;
    }
    if (!source.list.isEmpty() && !songId.isEmpty()) {
      new AlertDialog.Builder(this)
          .setTitle("무엇을 저장할까요?")
          .setItems(
              new String[] {"현재 곡", "재생목록"},
              (d, n) -> store(n == 0 ? new Source(songId, "", source.music) : source))
          .show();
    } else store(source);
  }

  private void store(Source item) {
    JSONObject lib = library(), entry = new JSONObject();
    String name =
        item.list.isEmpty()
            ? (songTitle.isEmpty() ? "곡 · " + item.video : songTitle)
            : "재생목록 · " + (songTitle.isEmpty() ? item.list : songTitle);
    put(entry, "url", item.url());
    put(entry, "name", name);
    put(entry, "art", songId);
    try {
      lib.put(item.key(), entry);
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
    prefs.edit().putString("library", lib.toString()).apply();
    toast("진열장에 저장했어요.");
  }

  private void showShelf() {
    disposePlayer();
    shelf = true;
    screen();
    LinearLayout bar = row();
    pad(bar, 14, 6);
    root.addView(bar);
    bar.addView(label("내 진열장", 24, true), weight());
    bar.addView(button("턴테이블", this::showDeck));
    ScrollView scroll = new ScrollView(this);
    root.addView(scroll);
    LinearLayout body = column();
    pad(body, 10, 8);
    scroll.addView(body);
    JSONObject lib = library();
    java.util.ArrayList<String> keys = new java.util.ArrayList<>();
    lib.keys().forEachRemaining(keys::add);
    java.util.Collections.reverse(keys);
    if (keys.isEmpty()) {
      TextView empty = label("아직 저장한 레코드가 없어요.\n턴테이블에서 곡을 열고 ‘진열장에 저장’을 눌러 주세요.", 17, false);
      pad(empty, 12, 32);
      body.addView(empty);
    }
    LinearLayout pair = null;
    for (int i = 0; i < keys.size(); i++) {
      String key = keys.get(i);
      JSONObject entry = lib.optJSONObject(key);
      if (entry == null) continue;
      if (i % 2 == 0) {
        pair = row();
        pair.setGravity(Gravity.TOP);
        body.addView(pair);
      }
      LinearLayout card = column();
      pad(card, 8, 8);
      pair.addView(card, new LinearLayout.LayoutParams(0, -2, 1));
      ImageView art = new ImageView(this);
      art.setScaleType(ImageView.ScaleType.CENTER_CROP);
      art.setImageResource(R.drawable.record_icon);
      art.setBackground(shape(0xffd4edf0, 12));
      card.addView(
          art, -1, dp(Math.min(175, getResources().getConfiguration().screenWidthDp / 2 - 28)));
      Covers.get(this, entry.optString("art"), art::setImageBitmap);
      TextView name = label(entry.optString("name"), 15, true);
      name.setMaxLines(2);
      name.setEllipsize(TextUtils.TruncateAt.END);
      card.addView(name);
      card.setOnClickListener(
          v -> {
            source = Source.parse(entry.optString("url"));
            showDeck();
          });
      card.addView(
          button(
              "삭제",
              () ->
                  new AlertDialog.Builder(this)
                      .setMessage("이 항목을 진열장에서 지울까요?")
                      .setNegativeButton("취소", null)
                      .setPositiveButton(
                          "삭제",
                          (d, j) -> {
                            JSONObject current = library();
                            current.remove(key);
                            prefs.edit().putString("library", current.toString()).apply();
                            showShelf();
                          })
                      .show()));
      if (i == keys.size() - 1 && i % 2 == 0)
        pair.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
    }
  }

  @Override
  public void onBackPressed() {
    if (shelf) showDeck();
    else super.onBackPressed();
  }
}
