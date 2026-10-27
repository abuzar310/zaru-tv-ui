package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Locale;

/**
 * The month calendar on the screensaver's home scene, drawn by hand in the house's champagne gold: the
 * month and year, the weekday initials and the days, with today in a filled brass circle. It sits on a
 * dark glass card with a hairline gold edge and checks the date every minute, so it turns over at midnight.
 */
public class HomeCalendarView extends View {
    static final int CHAMPAGNE = 0xFFF5E1B8;
    private static final int SOFT = 0x8CF5E1B8;
    private static final int BRASS = 0xFFDDAE62;
    private static final int INK = 0xFF1A1206;

    private final float d, sp;
    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private Typeface light, regular, medium;
    private int shownMonth = -1;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (Calendar.getInstance().get(Calendar.MONTH) != shownMonth) requestLayout();
            invalidate();
            postDelayed(this, 60000);
        }
    };

    public HomeCalendarView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        sp = c.getResources().getDisplayMetrics().scaledDensity;
        card.setColor(0x99080C16);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1f, d));
        edge.setColor(0x47F5E1B8);
        dot.setColor(BRASS);
        try { light = c.getResources().getFont(R.font.outfit_light); } catch (Throwable ignored) {}
        try { regular = c.getResources().getFont(R.font.outfit_regular); } catch (Throwable ignored) {}
        try { medium = c.getResources().getFont(R.font.outfit_medium); } catch (Throwable ignored) {}
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        postDelayed(tick, 60000);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    /** Leading blank cells before the 1st, for the locale's first day of the week. */
    private static int offset(Calendar now) {
        Calendar c = (Calendar) now.clone();
        c.set(Calendar.DAY_OF_MONTH, 1);
        return (c.get(Calendar.DAY_OF_WEEK) - c.getFirstDayOfWeek() + 7) % 7;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        Calendar now = Calendar.getInstance();
        shownMonth = now.get(Calendar.MONTH);
        int rows = (offset(now) + now.getActualMaximum(Calendar.DAY_OF_MONTH) + 6) / 7;
        setMeasuredDimension((int) (300 * d), (int) ((74 + rows * 24 + 14) * d));
    }

    @Override
    protected void onDraw(Canvas cv) {
        Calendar now = Calendar.getInstance();
        float w = getWidth(), pad = 20 * d;
        r.set(0, 0, w, getHeight());
        cv.drawRoundRect(r, 24 * d, 24 * d, card);
        float e = edge.getStrokeWidth() / 2f;
        r.inset(e, e);
        cv.drawRoundRect(r, 24 * d, 24 * d, edge);

        DateFormatSymbols sym = DateFormatSymbols.getInstance(Locale.getDefault());

        // Month and year.
        ink.setTextAlign(Paint.Align.LEFT);
        ink.setTypeface(regular);
        ink.setTextSize(17 * sp);
        ink.setColor(CHAMPAGNE);
        float y = 18 * d - ink.ascent();
        String month = sym.getMonths()[now.get(Calendar.MONTH)];
        cv.drawText(month, pad, y, ink);
        float mw = ink.measureText(month + " ");
        ink.setColor(SOFT);
        cv.drawText(String.valueOf(now.get(Calendar.YEAR)), pad + mw, y, ink);

        // Weekday initials, starting on the locale's first day.
        float cell = (w - 2 * pad) / 7f;
        int first = now.getFirstDayOfWeek();
        String[] wk = sym.getShortWeekdays();
        ink.setTextAlign(Paint.Align.CENTER);
        ink.setTypeface(medium);
        ink.setTextSize(10.5f * sp);
        ink.setLetterSpacing(0.08f);
        ink.setColor(SOFT);
        float wy = 52 * d - ink.ascent();
        for (int i = 0; i < 7; i++) {
            String s = wk[(first - 1 + i) % 7 + 1];
            cv.drawText(s.isEmpty() ? "" : s.substring(0, 1).toUpperCase(Locale.getDefault()), pad + cell * (i + 0.5f), wy, ink);
        }
        ink.setLetterSpacing(0f);

        // The days, today in a brass circle.
        int start = offset(now);
        int days = now.getActualMaximum(Calendar.DAY_OF_MONTH), today = now.get(Calendar.DAY_OF_MONTH);
        float top = 74 * d, rowH = 24 * d;
        ink.setTextSize(14 * sp);
        for (int day = 1; day <= days; day++) {
            int idx = start + day - 1;
            float cx = pad + cell * (idx % 7 + 0.5f), cy = top + rowH * (idx / 7 + 0.5f);
            boolean isToday = day == today;
            if (isToday) cv.drawCircle(cx, cy, 11.5f * d, dot);
            ink.setTypeface(isToday ? medium : light);
            ink.setColor(isToday ? INK : CHAMPAGNE);
            Paint.FontMetrics fm = ink.getFontMetrics();
            cv.drawText(Integer.toString(day), cx, cy - (fm.ascent + fm.descent) / 2f, ink);
        }
    }
}
