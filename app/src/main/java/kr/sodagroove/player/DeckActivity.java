package kr.sodagroove.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONException;
import org.json.JSONObject;

public class DeckActivity extends Activity {
  private static final int INK = 0xff21414c, PAPER = 0xfff7fbfc, MUTED = 0xff5c7c87;
  private static final int PILL = 0xffe3f0f3, SAVE = 0xffd6ede5, OPEN = 0xffe1eff8;
  private static final int[] PALETTE = {0xffd8eff8, 0xffd9f1e7, 0xfffbe4df};
  private static final String[] COLORS = {"하늘", "민트", "코랄"};
  static final String EMPTY_TITLE = "첫 레코드를 올려 보세요";
  static final String EMPTY_CAPTION = "유튜브 또는 유튜브 뮤직 링크를 열어 시작하세요.";
  static final String LOADED_CAPTION = "LP판을 탭해 회전 감상하거나 원본 앱에서 바로 들으세요.";
  static final String SPINNING_CAPTION = "LP판이 돌아가는 중이에요. 소리는 원본 앱에서 들을 수 있어요.";

  private SharedPreferences prefs;
  private LinearLayout root, sheet;
  private TextView title, status, clock, service;
  private Button original;
  private Platter deck;
  private Source source;
  private String songId = "", songTitle = "";
  private int generation;
  private boolean shelf;

  private int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private GradientDrawable shape(int color, float radius) {
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

  private TextView label(String s, float size, boolean medium) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextColor(INK);
    t.setTextSize(size);
    t.setIncludeFontPadding(false);
    if (medium) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    return t;
  }

  private Button pill(String caption, int surface, float size, int height, Runnable action) {
    Button b = new Button(this);
    b.setAllCaps(false);
    b.setText(caption);
    b.setTextSize(size);
    b.setTextColor(
        new ColorStateList(
            new int[][] {{-android.R.attr.state_enabled}, {}},
            new int[] {0xff9db0b6, 0xff2b5361}));
    b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    b.setStateListAnimator(null);
    b.setElevation(0);
    b.setMinWidth(0);
    b.setMinimumWidth(0);
    b.setMinHeight(0);
    b.setMinimumHeight(0);
    b.setLetterSpacing(0);
    b.setIncludeFontPadding(false);
    b.setGravity(Gravity.CENTER);
    b.setPadding(dp(14), 0, dp(14), 0);
    b.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x24407885), shape(surface, height / 2f), null));
    b.setOnClickListener(v -> action.run());
    return b;
  }

  private LinearLayout.LayoutParams weight() {
    return new LinearLayout.LayoutParams(0, -2, 1);
  }

  private void pad(View v, int x, int y) {
    v.setPadding(dp(x), dp(y), dp(x), dp(y));
  }

  private void screen() {
    root = column();
    root.setBackgroundColor(PAPER);
    setContentView(root);
    getWindow().setStatusBarColor(PAPER);
    getWindow().setNavigationBarColor(PAPER);
    int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
    if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
    getWindow().getDecorView().setSystemUiVisibility(flags);
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    prefs = getSharedPreferences("deck", 0);
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
    generation++;
    shelf = false;
    screen();
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setVerticalScrollBarEnabled(false);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    LinearLayout body = column();
    body.setGravity(Gravity.CENTER_VERTICAL);
    scroll.addView(body, new ScrollView.LayoutParams(-1, -2));

    LinearLayout top = row();
    top.setPadding(dp(18), dp(12), dp(12), dp(2));
    body.addView(top);
    TextView word = label("Soda Groove", 20, true);
    word.setTextColor(0xff1d4350);
    top.addView(word, weight());
    Button palette = pill("색상", PILL, 12, 32, this::colors);
    Button shelfButton = pill("진열장", PILL, 12, 32, this::showShelf);
    LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(-2, dp(32));
    first.setMargins(dp(6), 0, 0, 0);
    top.addView(palette, first);
    LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(-2, dp(32));
    second.setMargins(dp(6), 0, dp(2), 0);
    top.addView(shelfButton, second);

    int screenW = getResources().getConfiguration().screenWidthDp;
    int screenH = getResources().getConfiguration().screenHeightDp;
    int cardHeight =
        Math.min(Math.round((screenW - 28) * 1.22f), Math.max(300, screenH - 220));

    sheet = column();
    sheet.setBackground(shape(PALETTE[prefs.getInt("color", 0)], 24));
    sheet.setPadding(dp(16), dp(16), dp(16), dp(16));
    LinearLayout.LayoutParams card = new LinearLayout.LayoutParams(-1, dp(cardHeight));
    card.setMargins(dp(14), dp(8), dp(14), 0);
    body.addView(sheet, card);

    LinearLayout header = row();
    sheet.addView(header);
    service = label(source == null ? "소다그루브" : source.service(), 12, true);
    service.setTextColor(0xff3f6472);
    header.addView(service, weight());
    Button link = pill("링크 열기", 0xffffffff, 13, 34, this::linkDialog);
    header.addView(link, new LinearLayout.LayoutParams(-2, dp(34)));

    deck = new Platter(this);
    deck.tap = this::toggled;
    LinearLayout.LayoutParams platter = new LinearLayout.LayoutParams(-1, 0, 1);
    platter.topMargin = dp(6);
    deck.setMinimumHeight(dp(140));
    sheet.addView(deck, platter);

    title = label(EMPTY_TITLE, 18, true);
    title.setMaxLines(2);
    title.setEllipsize(TextUtils.TruncateAt.END);
    LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(-1, -2);
    line.topMargin = dp(8);
    sheet.addView(title, line);

    LinearLayout track = row();
    LinearLayout.LayoutParams tracks = new LinearLayout.LayoutParams(-1, dp(24));
    tracks.topMargin = dp(4);
    sheet.addView(track, tracks);
    Groove groove = new Groove(this);
    track.addView(groove, new LinearLayout.LayoutParams(0, -1, 1));
    clock = label("0:00 / 0:00", 11, false);
    clock.setTextColor(MUTED);
    clock.setPadding(dp(8), 0, 0, 0);
    track.addView(clock);

    LinearLayout actions = row();
    LinearLayout.LayoutParams bar = new LinearLayout.LayoutParams(-1, -2);
    bar.setMargins(dp(14), dp(12), dp(14), 0);
    body.addView(actions, bar);
    Button save = pill("진열장에 저장", SAVE, 14, 48, this::save);
    LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(48), 1);
    left.setMargins(0, 0, dp(5), 0);
    actions.addView(save, left);
    original = pill("원본 열기", OPEN, 14, 48, this::openOriginal);
    LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(48), 1);
    right.setMargins(dp(5), 0, 0, 0);
    actions.addView(original, right);

    status = label("", 12, false);
    status.setTextColor(MUTED);
    LinearLayout.LayoutParams note = new LinearLayout.LayoutParams(-1, -2);
    note.setMargins(dp(16), dp(12), dp(16), dp(20));
    body.addView(status, note);

    if (source == null) {
      title.setText(EMPTY_TITLE);
      original.setEnabled(false);
      message(EMPTY_CAPTION);
    } else placeRecord();
  }

  private void placeRecord() {
    final Source current = source;
    final int serial = ++generation;
    service.setText(current.service());
    original.setEnabled(true);
    original.setText(current.music ? "뮤직에서 열기" : "유튜브에서 열기");
    songId = current.video;
    songTitle = Titles.cached(this, current);
    renderTitle();
    deck.cover = null;
    deck.invalidate();
    if (!songId.isEmpty())
      Covers.get(
          this,
          songId,
          b -> {
            if (serial == generation && deck != null) {
              deck.cover = b;
              deck.invalidate();
            }
          });
    deck.spinning = false;
    message(LOADED_CAPTION);
    remember();
    Titles.fetch(
        this,
        current,
        text -> {
          if (serial != generation || source == null) return;
          songTitle = text;
          renderTitle();
          remember();
        });
  }

  private void renderTitle() {
    if (title == null) return;
    if (source == null) {
      title.setText(EMPTY_TITLE);
      return;
    }
    title.setText(songTitle.isEmpty() ? fallbackTitle(source) : songTitle);
  }

  static String fallbackTitle(Source s) {
    return s.video.isEmpty() ? "재생목록 · " + s.list : "YouTube · " + s.video;
  }

  private void toggled(boolean now) {
    if (source == null) {
      message(now ? "레코드가 놓이면 판이 돌아가요." : EMPTY_CAPTION);
      return;
    }
    message(now ? SPINNING_CAPTION : LOADED_CAPTION);
  }

  private void colors() {
    new AlertDialog.Builder(this)
        .setTitle("턴테이블 색상")
        .setSingleChoiceItems(
            COLORS,
            prefs.getInt("color", 0),
            (d, i) -> {
              prefs.edit().putInt("color", i).apply();
              sheet.setBackground(shape(PALETTE[i], 24));
              d.dismiss();
            })
        .show();
  }

  private void linkDialog() {
    EditText url = new EditText(this);
    url.setSingleLine(true);
    url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
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

  private void remember() {
    if (source == null) return;
    String name = songTitle.isEmpty() ? fallbackTitle(source) : songTitle;
    if (source.url().equals(prefs.getString("lastUrl", ""))
        && name.equals(prefs.getString("lastTitle", ""))) return;
    prefs
        .edit()
        .putString("lastUrl", source.url())
        .putString("lastId", songId)
        .putString("lastTitle", name)
        .apply();
    DeckWidget.update(this);
  }

  private void message(String text) {
    if (status != null) status.setText(text);
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
            : (songTitle.isEmpty() ? "재생목록 · " + item.list : songTitle);
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
    shelf = true;
    screen();
    LinearLayout bar = row();
    bar.setPadding(dp(18), dp(12), dp(12), dp(4));
    root.addView(bar);
    TextView heading = label("내 진열장", 21, true);
    bar.addView(heading, weight());
    bar.addView(pill("턴테이블", PILL, 12, 32, this::showDeck), new LinearLayout.LayoutParams(-2, dp(32)));
    ScrollView scroll = new ScrollView(this);
    scroll.setVerticalScrollBarEnabled(false);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout body = column();
    pad(body, 12, 10);
    scroll.addView(body);
    JSONObject lib = library();
    java.util.ArrayList<String> keys = new java.util.ArrayList<>();
    lib.keys().forEachRemaining(keys::add);
    java.util.Collections.reverse(keys);
    if (keys.isEmpty()) {
      TextView empty = label("아직 저장한 레코드가 없어요.\n턴테이블에서 곡을 열고 ‘진열장에 저장’을 눌러 주세요.", 15, false);
      empty.setTextColor(MUTED);
      pad(empty, 8, 32);
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
      card.addView(art, -1, dp(Math.min(175, getResources().getConfiguration().screenWidthDp / 2 - 28)));
      Covers.get(this, entry.optString("art"), art::setImageBitmap);
      TextView name = label(entry.optString("name"), 14, true);
      name.setMaxLines(2);
      name.setEllipsize(TextUtils.TruncateAt.END);
      LinearLayout.LayoutParams nameLine = new LinearLayout.LayoutParams(-1, -2);
      nameLine.topMargin = dp(6);
      card.addView(name, nameLine);
      card.setOnClickListener(
          v -> {
            source = Source.parse(entry.optString("url"));
            showDeck();
          });
      Button remove = pill("삭제", PILL, 12, 30, () -> confirmDelete(key));
      LinearLayout.LayoutParams removeLine = new LinearLayout.LayoutParams(-2, dp(30));
      removeLine.topMargin = dp(4);
      card.addView(remove, removeLine);
      if (i == keys.size() - 1 && i % 2 == 0)
        pair.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
    }
  }

  private void confirmDelete(String key) {
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
        .show();
  }

  @Override
  public void onBackPressed() {
    if (shelf) showDeck();
    else super.onBackPressed();
  }
}
