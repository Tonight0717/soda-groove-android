package kr.sodagroove.player;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.graphics.*;
import android.widget.RemoteViews;
import java.io.*;

public class DeckWidget extends AppWidgetProvider {
  static void update(Context c) {
    AppWidgetManager m = AppWidgetManager.getInstance(c);
    int[] ids = m.getAppWidgetIds(new ComponentName(c, DeckWidget.class));
    if (ids.length > 0) new DeckWidget().onUpdate(c, m, ids);
  }

  @Override
  public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
    SharedPreferences p = c.getSharedPreferences("deck", 0);
    RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
    v.setTextViewText(R.id.song, p.getString("lastTitle", "소다그루브 열기"));
    String id = p.getString("lastId", "");
    Bitmap art =
        BitmapFactory.decodeFile(
            new File(new File(c.getCacheDir(), "covers"), id + ".jpg").getPath());
    if (art != null) {
      Bitmap out = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(out);
      Paint pen = new Paint(3);
      pen.setColor(0xff14586d);
      canvas.drawCircle(78, 80, 72, pen);
      pen.setStyle(Paint.Style.STROKE);
      pen.setColor(0xff2f8991);
      for (int r = 39; r < 70; r += 5) canvas.drawCircle(78, 80, r, pen);
      pen.setStyle(Paint.Style.FILL);
      Path path = new Path();
      path.addCircle(78, 80, 34, Path.Direction.CW);
      canvas.save();
      canvas.clipPath(path);
      int side = Math.min(art.getWidth(), art.getHeight()),
          x = (art.getWidth() - side) / 2,
          y = (art.getHeight() - side) / 2;
      canvas.drawBitmap(art, new Rect(x, y, x + side, y + side), new RectF(44, 46, 112, 114), pen);
      canvas.restore();
      pen.setColor(0xffc4ef63);
      pen.setStrokeWidth(7);
      canvas.drawLine(147, 20, 119, 102, pen);
      v.setImageViewBitmap(R.id.disc, out);
    } else v.setImageViewResource(R.id.disc, R.drawable.record_icon);
    Intent open =
        new Intent(c, DeckActivity.class)
            .setAction("kr.sodagroove.LAST")
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    v.setOnClickPendingIntent(
        R.id.widget_root,
        PendingIntent.getActivity(
            c, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    for (int idWidget : ids) m.updateAppWidget(idWidget, v);
  }
}
