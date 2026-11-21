package kr.sodagroove.player;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

final class Platter extends View {
  interface Seek {
    void to(float fraction);
  }

  final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  Bitmap cover;
  Seek seek;
  boolean playing, dragging, canSeek;
  float fraction, preview, rotation, engagement, tipX, tipY, cx, cy, r;
  long tick;

  Platter(Context context) {
    super(context);
    setContentDescription("LP 턴테이블 · 바늘을 끌어 구간 이동");
  }

  void circle(Canvas c, float x, float y, float radius, int color) {
    ink.setColor(color);
    c.drawCircle(x, y, radius, ink);
  }

  @Override
  protected void onDraw(Canvas c) {
    long now = SystemClock.uptimeMillis();
    float dt = Math.min(60, now - tick);
    tick = now;
    if (playing) rotation = (rotation + dt * 0.09f) % 360;
    engagement = playing ? Math.min(1, engagement + dt / 330) : Math.max(0, engagement - dt / 330);
    float w = getWidth(), h = getHeight();
    r = Math.min(w * .34f, h * .435f);
    cx = w * .46f;
    cy = h * .51f;
    circle(c, cx + 2, cy + r * .055f, r * 1.025f, 0x33436772);
    circle(c, cx, cy, r * 1.02f, 0xffeafcff);
    ink.setShader(
        new SweepGradient(
            cx, cy, new int[] {0xff07516a, 0xff2597a4, 0xff0c536b, 0xff52b5bf, 0xff07516a}, null));
    ink.setColor(-1);
    c.drawCircle(cx, cy, r, ink);
    ink.setShader(null);
    ink.setStyle(Paint.Style.STROKE);
    ink.setStrokeWidth(Math.max(1, r * .006f));
    for (int i = 0; i < 30; i++)
      circle(c, cx, cy, r * (.42f + i * .019f), i % 4 == 0 ? 0x65c3f7ef : 0x3c073844);
    ink.setStyle(Paint.Style.FILL);
    c.save();
    c.rotate(rotation, cx, cy);
    circle(c, cx, cy, r * .39f, 0xffb4f0da);
    if (cover != null) {
      Path clip = new Path();
      clip.addCircle(cx, cy, r * .37f, Path.Direction.CW);
      c.save();
      c.clipPath(clip);
      int size = Math.min(cover.getWidth(), cover.getHeight()),
          x = (cover.getWidth() - size) / 2,
          y = (cover.getHeight() - size) / 2;
      ink.setColor(-1);
      c.drawBitmap(
          cover,
          new Rect(x, y, x + size, y + size),
          new RectF(cx - r * .37f, cy - r * .37f, cx + r * .37f, cy + r * .37f),
          ink);
      c.restore();
    } else {
      ink.setColor(0xff006678);
      ink.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
      ink.setTextSize(r * .135f);
      ink.setTextAlign(Paint.Align.CENTER);
      c.drawText("SODA", cx, cy - r * .1f, ink);
      c.drawText("GROOVE", cx, cy + r * .21f, ink);
    }
    circle(c, cx, cy, r * .045f, 0xff174457);
    circle(c, cx, cy, r * .019f, 0xffdefff6);
    c.restore();
    float px = cx + 1.12f * r, py = cy - .81f * r;
    float f = dragging ? preview : fraction;
    float lift = dragging ? 1 : engagement;
    double angle = Math.toRadians(88 + (23 + 22 * f) * lift);
    tipX = px + (float) Math.cos(angle) * r * 1.24f;
    tipY = py + (float) Math.sin(angle) * r * 1.24f;
    float elbowX = tipX + .12f * r, elbowY = tipY - .16f * r;
    ink.setStrokeCap(Paint.Cap.ROUND);
    ink.setStrokeWidth(r * .065f);
    ink.setColor(0xff396371);
    c.drawLine(px, py, elbowX, elbowY, ink);
    c.drawLine(elbowX, elbowY, tipX, tipY, ink);
    ink.setStrokeWidth(r * .027f);
    ink.setColor(0xffe9ffff);
    c.drawLine(px, py, elbowX, elbowY, ink);
    circle(c, px, py, r * .12f, 0xff437986);
    circle(c, px, py, r * .071f, 0xffc5e7ea);
    circle(c, tipX, tipY, r * .063f, 0xffb8e74b);
    if (playing || engagement > 0) postInvalidateOnAnimation();
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        if (!canSeek
            || Math.hypot(e.getX() - tipX, e.getY() - tipY)
                > Math.max(32 * getResources().getDisplayMetrics().density, r * .22)) return false;
        preview = fraction;
        dragging = true;
        getParent().requestDisallowInterceptTouchEvent(true);
        invalidate();
        return true;
      case MotionEvent.ACTION_MOVE:
        if (!dragging) return false;
        double a =
            Math.toDegrees(Math.atan2(e.getY() - (cy - .81f * r), e.getX() - (cx + 1.12f * r)));
        preview = (float) Math.max(0, Math.min(1, (a - 111) / 22));
        invalidate();
        return true;
      case MotionEvent.ACTION_UP:
        if (dragging && seek != null) seek.to(preview);
        performClick();
      case MotionEvent.ACTION_CANCEL:
        dragging = false;
        getParent().requestDisallowInterceptTouchEvent(false);
        invalidate();
        return true;
      default:
        return false;
    }
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }
}
