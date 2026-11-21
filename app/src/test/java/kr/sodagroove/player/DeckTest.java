package kr.sodagroove.player;

import android.app.AlertDialog;
import android.content.Context;
import android.view.*;
import android.widget.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class DeckTest {
    static final String ID="M7lc1UVf-VE";
    static final String LIST="PL123456789";
    @Test public void bothServicesAndLists(){
        for(String host:new String[]{"www.youtube.com","music.youtube.com"}){
            for(String path:new String[]{"watch?v="+ID,"playlist?list="+LIST,"watch?v="+ID+"&list="+LIST}){
                String url="https://"+host+"/"+path;
                Source parsed=Source.parse(url);assertNotNull(parsed);assertEquals(url,parsed.url());
                assertEquals(host.startsWith("music"),parsed.music);
            }
        }
        assertEquals(ID,Source.parse("https://youtu.be/"+ID+"?si=abcd").video);
        for(String bad:new String[]{"","https://youtube.com.evil.example/watch?v="+ID,"https://youtu.be/123","https://music.youtube.com/playlist?list="})
            assertNull(Source.parse(bad));
    }
    private TextView find(View v,String text){
        if(v instanceof TextView && text.contentEquals(((TextView)v).getText()))return (TextView)v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){
            TextView hit=find(((ViewGroup)v).getChildAt(i),text);if(hit!=null)return hit;
        }
        return null;
    }
    @Test public void emptyShelfAndThemePersistence(){
        ActivityController<DeckActivity> controller=Robolectric.buildActivity(DeckActivity.class).setup();
        DeckActivity a=controller.get();View root=a.getWindow().getDecorView();
        find(root,"진열장").performClick();assertNotNull(find(root,"내 진열장"));
        find(root,"턴테이블").performClick();find(root,"색상").performClick();
        AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        shadowOf(d).clickOnItem(1);
        assertEquals(1,a.getSharedPreferences("deck",Context.MODE_PRIVATE).getInt("color",-1));
        controller.pause().stop().destroy();
    }
    @Test public void savedPlaylistSurvivesRelaunchAndCanBeDeleted(){
        ActivityController<DeckActivity> ctl=Robolectric.buildActivity(DeckActivity.class).setup();
        DeckActivity a=ctl.get();View root=a.getWindow().getDecorView();
        find(root,"링크 열기").performClick();
        AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        EditText input=findInput(d.getWindow().getDecorView());
        input.setText("https://music.youtube.com/playlist?list="+LIST);d.getButton(-1).performClick();
        find(root,"진열장에 저장").performClick();ctl.pause().stop().destroy();
        ctl=Robolectric.buildActivity(DeckActivity.class).setup();root=ctl.get().getWindow().getDecorView();
        find(root,"진열장").performClick();assertNotNull(find(root,"재생목록 · "+LIST));
        find(root,"삭제").performClick();org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getButton(-1).performClick();
        assertNull(find(root,"재생목록 · "+LIST));ctl.pause().stop().destroy();
    }
    private EditText findInput(View v){
        if(v instanceof EditText)return (EditText)v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){
            EditText e=findInput(((ViewGroup)v).getChildAt(i));if(e!=null)return e;
        }
        return null;
    }
}
