package kr.sodagroove.player;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class DeckTest {
  static final String ID = "M7lc1UVf-VE";
  static final String LIST = "PL123456789";

  @Before
  public void noNetwork() {
    Covers.offline = true;
    Titles.offline = true;
  }

  @After
  public void restoreNetwork() {
    Covers.offline = false;
    Titles.offline = false;
  }

  @Test
  public void bothServicesAndLists() {
    for (String host : new String[] {"www.youtube.com", "music.youtube.com"}) {
      for (String path :
          new String[] {
            "watch?v=" + ID, "playlist?list=" + LIST, "watch?v=" + ID + "&list=" + LIST
          }) {
        String url = "https://" + host + "/" + path;
        Source parsed = Source.parse(url);
        assertNotNull(parsed);
        assertEquals(url, parsed.url());
        assertEquals(host.startsWith("music"), parsed.music);
      }
    }
    assertEquals(ID, Source.parse("https://youtu.be/" + ID + "?si=abcd").video);
    for (String bad :
        new String[] {
          "",
          "https://youtube.com.evil.example/watch?v=" + ID,
          "https://youtu.be/123",
          "https://music.youtube.com/playlist?list="
        }) assertNull(Source.parse(bad));
  }

  @Test
  public void emptyDeckHasNoPlayerAndShowsTheFigmaCopy() {
    ActivityController<DeckActivity> ctl = Robolectric.buildActivity(DeckActivity.class).setup();
    View root = ctl.get().getWindow().getDecorView();
    assertNull(findType(root, WebView.class));
    assertNotNull(find(root, DeckActivity.EMPTY_TITLE));
    assertNotNull(find(root, DeckActivity.EMPTY_CAPTION));
    assertNotNull(find(root, "0:00 / 0:00"));
    assertNotNull(find(root, "소다그루브"));
    assertNull(find(root, "YouTube · " + ID));
    TextView word = find(root, "Soda Groove");
    assertNotNull(word);
    assertNotEquals(Typeface.BOLD_ITALIC, word.getTypeface().getStyle());
    assertEquals(0xfff7fbfc, ctl.get().getWindow().getNavigationBarColor());
    ctl.pause().stop().destroy();
  }

  @Test
  public void platterTapTogglesTheSpin() {
    ActivityController<DeckActivity> ctl = Robolectric.buildActivity(DeckActivity.class).setup();
    Platter deck = findType(ctl.get().getWindow().getDecorView(), Platter.class);
    assertNotNull(deck);
    assertFalse(deck.spinning);
    deck.performClick();
    assertTrue(deck.spinning);
    deck.performClick();
    assertFalse(deck.spinning);
    ctl.pause().stop().destroy();
  }

  @Test
  public void cachedTitleAppearsOnTheCard() {
    RuntimeEnvironment.getApplication()
        .getSharedPreferences("deck", 0)
        .edit()
        .putString("t/v/" + ID, "테스트 곡")
        .commit();
    ActivityController<DeckActivity> ctl = Robolectric.buildActivity(DeckActivity.class).setup();
    View root = ctl.get().getWindow().getDecorView();
    open(root, "https://www.youtube.com/watch?v=" + ID);
    assertNotNull(find(root, "테스트 곡"));
    assertNull(find(root, "YouTube · " + ID));
    assertNotNull(find(root, "유튜브에서 열기"));
    assertNotNull(find(root, DeckActivity.LOADED_CAPTION));
    ctl.pause().stop().destroy();
  }

  @Test
  public void emptyShelfAndThemePersistence() {
    ActivityController<DeckActivity> controller =
        Robolectric.buildActivity(DeckActivity.class).setup();
    DeckActivity a = controller.get();
    View root = a.getWindow().getDecorView();
    find(root, "진열장").performClick();
    assertNotNull(find(root, "내 진열장"));
    find(root, "턴테이블").performClick();
    find(root, "색상").performClick();
    shadowOf(android.os.Looper.getMainLooper()).idle();
    AlertDialog d = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    shadowOf(d).clickOnItem(1);
    assertEquals(1, a.getSharedPreferences("deck", Context.MODE_PRIVATE).getInt("color", -1));
    controller.pause().stop().destroy();
  }

  @Test
  public void savedPlaylistSurvivesRelaunchAndCanBeDeleted() {
    ActivityController<DeckActivity> ctl = Robolectric.buildActivity(DeckActivity.class).setup();
    View root = ctl.get().getWindow().getDecorView();
    open(root, "https://music.youtube.com/playlist?list=" + LIST);
    assertNotNull(find(root, "뮤직에서 열기"));
    find(root, "진열장에 저장").performClick();
    ctl.pause().stop().destroy();
    ctl = Robolectric.buildActivity(DeckActivity.class).setup();
    root = ctl.get().getWindow().getDecorView();
    find(root, "진열장").performClick();
    assertNotNull(find(root, "재생목록 · " + LIST));
    find(root, "삭제").performClick();
    org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getButton(-1).performClick();
    shadowOf(android.os.Looper.getMainLooper()).idle();
    assertNull(find(root, "재생목록 · " + LIST));
    ctl.pause().stop().destroy();
  }

  private void open(View root, String url) {
    find(root, "링크 열기").performClick();
    shadowOf(android.os.Looper.getMainLooper()).idle();
    AlertDialog d = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    findInput(d.getWindow().getDecorView()).setText(url);
    d.getButton(-1).performClick();
  }

  private TextView find(View v, String text) {
    if (v instanceof TextView && text.contentEquals(((TextView) v).getText())) return (TextView) v;
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
        TextView hit = find(((ViewGroup) v).getChildAt(i), text);
        if (hit != null) return hit;
      }
    return null;
  }

  private <T extends View> T findType(View v, Class<T> type) {
    if (type.isInstance(v)) return type.cast(v);
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
        T hit = findType(((ViewGroup) v).getChildAt(i), type);
        if (hit != null) return hit;
      }
    return null;
  }

  private EditText findInput(View v) {
    if (v instanceof EditText) return (EditText) v;
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
        EditText e = findInput(((ViewGroup) v).getChildAt(i));
        if (e != null) return e;
      }
    return null;
  }
}
