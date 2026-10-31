package net.kdt.pojavlaunch.links;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.StyleSpan;
import android.text.style.TabStopSpan;
import android.util.Base64;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.widget.TextView;

import net.kdt.pojavlaunch.R;

/**
 * The body of a link confirmation: a compact table with a quiet label per row, then an optional sentence. A value has
 * the accent color, as only values react to a press; the arrow and the new value of a change are bold. A long press on
 * a value copies it, also a shortened URL in full, so a change can be undone by hand.
 */
final class LinkText {
    private static final String ARROW = " \u2192 ";
    private static final String MISSING = "-";
    private static final String ELLIPSIS = "\u2026";
    private static final int MAX_VALUE_LENGTH = 40;
    private static final int LABEL_COLUMN_DP = 72;

    private final Context mContext;
    private final SpannableStringBuilder mText = new SpannableStringBuilder();
    private final int mColumn;
    private final int mValueColor;
    private final int mQuietColor;
    private String mLabel;

    LinkText(Context context) {
        mContext = context;
        mColumn = Math.round(LABEL_COLUMN_DP * context.getResources().getDisplayMetrics().density);
        mValueColor = themeColor(context, android.R.attr.colorAccent);
        mQuietColor = themeColor(context, android.R.attr.textColorSecondary);
    }

    /** @param icon a profile's "data:image/png;base64," icon, or null */
    static Bitmap iconBitmap(String icon) {
        if (icon == null || !icon.contains(",")) return null;
        byte[] png = Base64.decode(icon.substring(icon.indexOf(',') + 1), Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(png, 0, png.length);
    }

    private static int themeColor(Context context, int attribute) {
        TypedValue color = new TypedValue();
        context.getTheme().resolveAttribute(attribute, color, true);
        if (color.resourceId != 0) return context.getColorStateList(color.resourceId).getDefaultColor();
        return color.data;
    }

    LinkText row(int label, String value) {
        int start = startRow(label);
        int values = mText.length();
        addValue(shorten(value), false);
        endValues(values, null, value);
        endRow(start);
        return this;
    }

    /** A row whose value is not worth copying. */
    LinkText plainRow(int label, String value) {
        int start = startRow(label);
        mText.append(value);
        endRow(start);
        return this;
    }

    /** The start that a URL's old and new value share is left out; both stay whole URLs from there. */
    LinkText change(int label, String oldValue, String newValue) {
        String old = oldValue == null ? MISSING : oldValue;
        // one shared folder stays: it tells what the path is about
        int prefix = Math.max(old.lastIndexOf('/', sharedPrefix(old, newValue) - 2) + 1, 0);
        String shared = prefix > 0 ? ELLIPSIS + "/" : "";
        int start = startRow(label);
        int values = mText.length();
        if (oldValue == null) mText.append(MISSING);
        else addValue(prefix > 0 ? shared + old.substring(prefix) : shorten(old), false);
        int arrow = mText.length();
        mText.append(ARROW);
        mText.setSpan(new StyleSpan(Typeface.BOLD), arrow, mText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        addValue(prefix > 0 ? shared + newValue.substring(prefix) : shorten(newValue), true);
        endValues(values, oldValue, newValue);
        endRow(start);
        return this;
    }

    LinkText sentence(int text) {
        return sentence(mContext.getString(text));
    }

    LinkText sentence(String text) {
        mText.append("\n\n");
        int start = mText.length();
        mText.append(text);
        mText.setSpan(new ForegroundColorSpan(mQuietColor), start, mText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        mText.setSpan(new StyleSpan(Typeface.ITALIC), start, mText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return this;
    }

    CharSequence build() {
        return mText;
    }

    /** A long press on a value opens a menu with its copy actions; the rest of the text stays plain. */
    static void enableCopy(TextView message) {
        float[] press = new float[2];
        message.setOnTouchListener((view, event) -> {
            press[0] = event.getX();
            press[1] = event.getY();
            return false;
        });
        message.setOnCreateContextMenuListener((menu, view, info) -> {
            int offset = message.getOffsetForPosition(press[0], press[1]);
            Values[] found = ((Spanned) message.getText()).getSpans(offset, offset, Values.class);
            if (found.length == 0) return;
            Context context = message.getContext();
            if (found[0].original == null) {
                addCopy(menu, context, context.getString(R.string.link_copy_value, found[0].label), found[0].current);
            } else {
                addCopy(menu, context, context.getString(R.string.link_copy_original, found[0].label), found[0].original);
                addCopy(menu, context, context.getString(R.string.link_copy_new, found[0].label), found[0].current);
            }
        });
    }

    private static void addCopy(ContextMenu menu, Context context, String title, String value) {
        menu.add(title).setOnMenuItemClickListener(item -> {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText(title, value));
            return true;
        });
    }

    private int startRow(int label) {
        if (mText.length() > 0) mText.append('\n');
        int start = mText.length();
        mLabel = mContext.getString(label);
        mText.append(mLabel);
        mText.setSpan(new ForegroundColorSpan(mQuietColor), start, mText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        mText.append('\t');
        return start;
    }

    /** "host/.../last/parts" for a long URL. */
    private static String shorten(String value) {
        int scheme = value.indexOf("://");
        if (scheme < 0 || value.length() <= MAX_VALUE_LENGTH) return value;
        String path = value.substring(scheme + 3);
        int host = path.indexOf('/');
        int end = path.lastIndexOf('/', path.lastIndexOf('/') - 1);
        return end <= host ? path : path.substring(0, host) + "/" + ELLIPSIS + path.substring(end);
    }

    private void addValue(String shown, boolean bold) {
        int start = mText.length();
        // a zero width space lets a long URL wrap inside its column
        mText.append(shown.replace("/", "/\u200B"));
        mText.setSpan(new ForegroundColorSpan(mValueColor), start, mText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (bold) mText.setSpan(new StyleSpan(Typeface.BOLD), start, mText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void endValues(int start, String original, String current) {
        mText.setSpan(new Values(mLabel, original, current), start, mText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** Up to and including the last shared slash. */
    private static int sharedPrefix(String first, String second) {
        int length = 0;
        for (int i = 0; i < Math.min(first.length(), second.length()) && first.charAt(i) == second.charAt(i); i++) {
            if (first.charAt(i) == '/') length = i + 1;
        }
        return length;
    }

    /** Marks a row's values with what they are in full; original is null unless the row is a change. */
    private static final class Values {
        final String label;
        final String original;
        final String current;

        Values(String label, String original, String current) {
            this.label = label;
            this.original = original;
            this.current = current;
        }
    }

    private void endRow(int start) {
        mText.setSpan(new TabStopSpan.Standard(mColumn), start, mText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        mText.setSpan(new LeadingMarginSpan.Standard(0, mColumn), start, mText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
