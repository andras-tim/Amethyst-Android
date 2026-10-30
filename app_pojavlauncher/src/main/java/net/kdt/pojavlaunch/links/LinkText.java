package net.kdt.pojavlaunch.links;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.StyleSpan;
import android.text.style.TabStopSpan;
import android.util.TypedValue;

/**
 * The body of a link confirmation: a compact table with a bold label per row, then an optional sentence. Only the new
 * value of a change has the accent color, and the old one stays readable, so a change can be undone by hand.
 */
final class LinkText {
    private static final String ARROW = " \u2192 ";
    private static final String MISSING = "-";
    private static final int LABEL_COLUMN_DP = 92;

    private final Context mContext;
    private final SpannableStringBuilder mText = new SpannableStringBuilder();
    private final int mColumn;
    private final int mNewColor;

    LinkText(Context context) {
        mContext = context;
        mColumn = Math.round(LABEL_COLUMN_DP * context.getResources().getDisplayMetrics().density);
        TypedValue color = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.colorAccent, color, true);
        mNewColor = color.data;
    }

    LinkText row(int label, String value) {
        int start = startRow(label);
        addValue(value, false);
        endRow(start);
        return this;
    }

    /** What a path-like old and new value share is shown once, around the change. */
    LinkText change(int label, String oldValue, String newValue) {
        String old = oldValue == null ? MISSING : oldValue;
        int prefix = sharedPrefix(old, newValue);
        int suffix = sharedSuffix(old.substring(prefix), newValue.substring(prefix));
        int start = startRow(label);
        addValue(old.substring(0, prefix), false);
        addValue(old.substring(prefix, old.length() - suffix), false);
        mText.append(ARROW);
        addValue(newValue.substring(prefix, newValue.length() - suffix), true);
        addValue(old.substring(old.length() - suffix), false);
        endRow(start);
        return this;
    }

    LinkText sentence(int text) {
        mText.append("\n\n").append(mContext.getString(text));
        return this;
    }

    CharSequence build() {
        return mText;
    }

    private int startRow(int label) {
        if (mText.length() > 0) mText.append('\n');
        int start = mText.length();
        mText.append(mContext.getString(label));
        mText.setSpan(new StyleSpan(Typeface.BOLD), start, mText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        mText.append('\t');
        return start;
    }

    private void addValue(String value, boolean highlighted) {
        int start = mText.length();
        // a zero width space lets a long URL wrap inside its column
        mText.append(value.replace("/", "/\u200B"));
        if (highlighted) {
            mText.setSpan(new ForegroundColorSpan(mNewColor), start, mText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            mText.setSpan(new StyleSpan(Typeface.BOLD), start, mText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    /** Up to and including the last shared slash. */
    private static int sharedPrefix(String first, String second) {
        int length = 0;
        for (int i = 0; i < Math.min(first.length(), second.length()) && first.charAt(i) == second.charAt(i); i++) {
            if (first.charAt(i) == '/') length = i + 1;
        }
        return length;
    }

    /** From the first slash of the shared end. */
    private static int sharedSuffix(String first, String second) {
        int length = 0;
        for (int i = 1; i <= Math.min(first.length(), second.length())
                && first.charAt(first.length() - i) == second.charAt(second.length() - i); i++) {
            if (first.charAt(first.length() - i) == '/') length = i;
        }
        return length;
    }

    private void endRow(int start) {
        mText.setSpan(new TabStopSpan.Standard(mColumn), start, mText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        mText.setSpan(new LeadingMarginSpan.Standard(0, mColumn), start, mText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
