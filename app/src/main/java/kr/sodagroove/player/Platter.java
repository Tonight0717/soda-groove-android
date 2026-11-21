package kr.sodagroove.player;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

final class Platter extends View {
  interface Tap {
    void tapped(boolean spinning);
  }

  private static final int LABEL = 0xffd7f0e5, LABEL_INK = 0xff1f4f52, ARM = 0xff4d7883;
  private static final int CARTRIDGE = 0xffd5ed8b;

  private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final Paint photo = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path discClip = new Path();
  private final Path arm = new Path();
  private final Rect crop = new Rect();
  private final RectF labelBounds = new RectF();
  Bitmap cover, vinyl;
  Tap tap;
  boolean spinning;
  float rotation, drop, cx, cy, r;
  private long tick;

  Platter(Context context) {
    super(context);
    setClickable(true);
    setContentDescription("LP 턴테이블 · 탭하면 판이 돌아갑니다");
    pen.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    pen.setTextAlign(Paint.Align.CENTER);
    tick = SystemClock.uptimeMillis();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldW, int oldH) {
    r = Math.max(1, Math.min(w * .30f, h * .40f));
    cx = w * .458f;
    cy = h * .47f;
    labelBounds.set(cx - r * .37f, cy - r * .37f, cx + r * .37f, cy + r * .37f);
    discClip.reset();
    discClip.addCircle(cx, cy, r * .37f, Path.Direction.CW);
    vinyl = disc(r);
  }

  private static Bitmap disc(float radius) {
    int size = Math.max(2, Math.round(radius * 2));
    Bitmap art = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(art);
    float mid = size / 2f;
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setColor(0xcc5e9fae);
    c.drawCircle(mid, mid, mid, p);
    p.setShader(
        new SweepGradient(
            mid,
            mid,
            new int[] {0x1effffff, 0x06ffffff, 0x1600262e, 0x06ffffff, 0x1effffff},
            null));
    c.drawCircle(mid, mid, mid, p);
    p.setShader(null);
    p.setStyle(Paint.Style.STROKE);
    for (int i = 0; i < 46; i++) {
      p.setStrokeWidth(Math.max(1f, mid * .0055f));
      p.setColor(i % 6 == 0 ? 0x30e2f8fb : 0x26052a33);
      c.drawCircle(mid, mid, mid * (.395f + i * .0132f), p);
    }
    p.setStrokeWidth(Math.max(1f, mid * .022f));
    p.setColor(0x33052a33);
    c.drawCircle(mid, mid, mid * .988f, p);
    return art;
  }

  @Override
  protected void onDraw(Canvas c) {
    long now = SystemClock.uptimeMillis();
    float dt = Math.min(48f, now - tick);
    tick = now;
    if (spinning) rotation = (rotation + dt * .045f) % 360f;
    drop = Math.max(0, Math.min(1, drop + (spinning ? dt : -dt) / 380f));

    ink.setStyle(Paint.Style.FILL);
    ink.setColor(0x1f2c6470);
    c.drawCircle(cx, cy + r * .055f, r * 1.005f, ink);

    c.save();
    c.rotate(rotation, cx, cy);
    if (vinyl != null) c.drawBitmap(vinyl, cx - r, cy - r, photo);
    label(c);
    c.restore();

    tonearm(c);
    if (spinning || drop > 0) postInvalidateOnAnimation();
  }

  private void label(Canvas c) {
    ink.setStyle(Paint.Style.FILL);
    ink.setColor(LABEL);
    c.drawCircle(cx, cy, r * .37f, ink);
    if (cover != null) {
      c.save();
      c.clipPath(discClip);
      int side = Math.min(cover.getWidth(), cover.getHeight()),
          x = (cover.getWidth() - side) / 2,
          y = (cover.getHeight() - side) / 2;
      crop.set(x, y, x + side, y + side);
      c.drawBitmap(cover, crop, labelBounds, photo);
      c.restore();
    } else {
      pen.setColor(LABEL_INK);
      pen.setTextSize(r * .115f);
      c.drawText("SODA", cx, cy - r * .055f, pen);
      pen.setTextSize(r * .085f);
      c.drawText("•", cx, cy + r * .06f, pen);
      pen.setTextSize(r * .115f);
      c.drawText("GROOVE", cx, cy + r * .19f, pen);
    }
    ink.setColor(0xff2b5a60);
    c.drawCircle(cx, cy, r * .018f, ink);
  }

  private void tonearm(Canvas c) {
    float px = cx + 1.11f * r, py = cy - .81f * r;
    double a = Math.toRadians(93 + 13 * drop), length = 1.26f * r;
    float ex = px + (float) (Math.cos(a) * length * .86f);
    float ey = py + (float) (Math.sin(a) * length * .86f);
    double b = a + Math.toRadians(12);
    float tx = ex + (float) (Math.cos(b) * length * .16f);
    float ty = ey + (float) (Math.sin(b) * length * .16f);
    arm.reset();
    arm.moveTo(px, py);
    arm.lineTo(ex, ey);
    arm.lineTo(tx, ty);
    ink.setStyle(Paint.Style.STROKE);
    ink.setStrokeCap(Paint.Cap.ROUND);
    ink.setStrokeJoin(Paint.Join.ROUND);
    ink.setStrokeWidth(r * .085f);
    ink.setColor(ARM);
    c.drawPath(arm, ink);
    ink.setStrokeWidth(r * .03f);
    ink.setColor(0x55e9fbfd);
    c.drawPath(arm, ink);
    ink.setStyle(Paint.Style.FILL);
    ink.setColor(ARM);
    c.drawCircle(px, py, r * .15f, ink);
    ink.setColor(0xffeaf8fa);
    c.drawCircle(px, py, r * .088f, ink);
    ink.setColor(CARTRIDGE);
    c.drawCircle(tx, ty, r * .095f, ink);
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_UP:
        performClick();
        return true;
      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_CANCEL:
        return true;
      default:
        return false;
    }
  }

  @Override
  public boolean performClick() {
    super.performClick();
    spinning = !spinning;
    tick = SystemClock.uptimeMillis();
    invalidate();
    if (tap != null) tap.tapped(spinning);
    return true;
  }
}
