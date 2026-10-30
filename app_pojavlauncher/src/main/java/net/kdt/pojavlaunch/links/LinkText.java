package net.kdt.pojavlaunch.links;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.StyleSpan;
import android.text.style.TabStopSpan;
import android.util.TypedValue;
import android.view.View;
import android.widget.Toast;

import net.kdt.pojavlaunch.R;

/**
 * The body of a link confirmation: a compact table with a bold label per row, then an optional sentence. Only the new
 * value of a change has the accent color. A value is copied by tapping it, also a shortened URL in full, so a change
 * can be undone by hand.
 */
final class LinkText {
    private static final String ARROW = " \u2192 ";
    private static final String MISSING = "-";
    private static final String ELLIPSIS = "\u2026";
    private static final int MAX_VALUE_LENGTH = 40;
    private static final int LABEL_COLUMN_DP = 84;

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
        addValue(shorten(value), value, false);
        endRow(start);
        return this;
    }

    /** The start that a URL's old and new value share is left out; both stay whole URLs from there. */
    LinkText change(int label, String oldValue, String newValue) {
        String old = oldValue == null ? MISSING : oldValue;
        int prefix = sharedPrefix(old, newValue);
        String shared = prefix > 0 ? ELLIPSIS + "/" : "";
        int start = startRow(label);
        addValue(shared + old.substring(prefix), old, false);
        mText.append(ARROW);
        addValue(shared + newValue.substring(prefix), newValue, true);
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

    /** "host/.../last/parts" for a long URL. */
    private static String shorten(String value) {
        int scheme = value.indexOf("://");
        if (scheme < 0 || value.length() <= MAX_VALUE_LENGTH) return value;
        String path = value.substring(scheme + 3);
        int host = path.indexOf('/');
        int end = path.lastIndexOf('/', path.lastIndexOf('/') - 1);
        return end <= host ? path : path.substring(0, host) + "/" + ELLIPSIS + path.substring(end);
    }

    private void addValue(String shown, String value, boolean highlighted) {
        int start = mText.length();
        // a zero width space lets a long URL wrap inside its column
        mText.append(shown.replace("/", "/\u200B"));
        mText.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View view) {
                ClipboardManager clipboard = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText(value, value));
                Toast.makeText(mContext, R.string.link_copied, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void updateDrawState(TextPaint paint) {
                // keeps the row's look: the tap is a shortcut, not a link
            }
        }, start, mText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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

    private void endRow(int start) {
        mText.setSpan(new TabStopSpan.Standard(mColumn), start, mText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        mText.setSpan(new LeadingMarginSpan.Standard(0, mColumn), start, mText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
