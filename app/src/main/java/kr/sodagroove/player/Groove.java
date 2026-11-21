package kr.sodagroove.player;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** The thin progress line under the record: track plus a single dot, as in the mockup. */
final class Groove extends View {
  private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
  float fraction;

  Groove(Context context) {
    super(context);
    setContentDescription("진행 위치");
  }

  private float dp(float n) {
    return n * getResources().getDisplayMetrics().density;
  }

  @Override
  protected void onDraw(Canvas c) {
    float w = getWidth(), cy = getHeight() / 2f;
    float knob = dp(4.5f), edge = knob + dp(1);
    line.setColor(0xffb9ccd2);
    line.setStrokeCap(Paint.Cap.ROUND);
    line.setStrokeWidth(dp(2));
    c.drawLine(edge, cy, w - edge, cy, line);
    ink.setColor(0xff2f5f6e);
    float x = edge + Math.max(0, Math.min(1, fraction)) * (w - 2 * edge);
    c.drawCircle(x, cy, knob, ink);
  }
}
