package net.kdt.pojavlaunch.customcontrols.buttons;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.customcontrols.ControlData;

/**
 * A Material Symbols icon above the label, for a button whose layout names one in its {@code icon} field (e.g.
 * {@code "backpack"}). The font draws an icon from its name through a ligature. Buttons without it keep the plain label.
 */
public final class ControlIcon {
    private static final float ICON_SHARE = 0.45f;  // of the button height
    private static final float LABEL_SHARE = 0.135f;
    private static final float MIN_LABEL_DP = 7.5f;
    private static final float DEFAULT_LABEL_SP = 14;
    private static Typeface sTypeface, sOutlineTypeface;

    private ControlIcon() {}

    public static void apply(TextView view, ControlData data) {
        if (data.icon == null || data.icon.isEmpty()) {
            if (view.getCompoundDrawables()[1] instanceof GlyphDrawable) {
                view.setCompoundDrawables(null, null, null, null);
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, DEFAULT_LABEL_SP);
            }
            return;
        }
        Context context = view.getContext();
        float height = data.getHeight();
        float density = context.getResources().getDisplayMetrics().density;
        float iconSize = height * ICON_SHARE;
        float labelSize = Math.max(height * LABEL_SHARE, MIN_LABEL_DP * density);
        int gap = Math.round(density);

        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, labelSize);
        GlyphDrawable icon = new GlyphDrawable(typeface(context, false), typeface(context, true), data.icon, iconSize);
        icon.setOutline(data.iconOutline);
        view.setCompoundDrawables(null, icon, null, null);
        view.setCompoundDrawablePadding(gap);
        // TextView pins a top drawable to the top padding: center the icon and the label together
        int labelHeight = view.getText().length() == 0 ? 0 : view.getLineHeight();
        int top = Math.max(0, Math.round((height - iconSize - gap - labelHeight) / 2));
        view.setPadding(view.getPaddingLeft(), top, view.getPaddingRight(), top);
    }

    /** Draws the icon outlined (true) or filled (false); a button without an icon is left alone. */
    public static void setOutline(TextView view, boolean outline) {
        Drawable icon = view.getCompoundDrawables()[1];
        if (icon instanceof GlyphDrawable) ((GlyphDrawable) icon).setOutline(outline);
    }

    private static Typeface typeface(Context context, boolean outline) {
        if (outline) {
            if (sOutlineTypeface == null) sOutlineTypeface = ResourcesCompat.getFont(context, R.font.material_symbols_rounded_outline);
            return sOutlineTypeface;
        }
        if (sTypeface == null) sTypeface = ResourcesCompat.getFont(context, R.font.material_symbols_rounded);
        return sTypeface;
    }

    private static final class GlyphDrawable extends Drawable {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Typeface mFilled, mOutline;
        private final String mName;
        private final int mSize;

        GlyphDrawable(Typeface filled, Typeface outline, String name, float size) {
            mFilled = filled;
            mOutline = outline;
            mName = name;
            mSize = Math.round(size);
            mPaint.setTypeface(filled);
            mPaint.setTextSize(size);
            mPaint.setColor(Color.WHITE);
            mPaint.setTextAlign(Paint.Align.CENTER);
            setBounds(0, 0, mSize, mSize);
        }

        void setOutline(boolean outline) {
            mPaint.setTypeface(outline ? mOutline : mFilled);
            invalidateSelf();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Paint.FontMetrics metrics = mPaint.getFontMetrics();
            float baseline = getBounds().exactCenterY() - (metrics.ascent + metrics.descent) / 2;
            canvas.drawText(mName, getBounds().exactCenterX(), baseline, mPaint);
        }

        @Override
        public int getIntrinsicWidth() {return mSize;}

        @Override
        public int getIntrinsicHeight() {return mSize;}

        @Override
        public void setAlpha(int alpha) {mPaint.setAlpha(alpha);}

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {mPaint.setColorFilter(colorFilter);}

        @Override
        public int getOpacity() {return PixelFormat.TRANSLUCENT;}
    }
}
