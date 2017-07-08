package io.mrarm.irc.util;

import android.content.Context;
import android.text.Editable;
import android.text.NoCopySpan;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

public class SpannableStringHelper {

    public static CharSequence format(CharSequence seq, Object... args) {
        int argI = 0;
        SpannableStringBuilder builder = new SpannableStringBuilder(seq);
        for (int i = 0; i < builder.length() - 1; i++) {
            if (builder.charAt(i) == '%') {
                int c = builder.charAt(++i);
                CharSequence replacement = null;
                switch (c) {
                    case 's':
                        replacement = (CharSequence) args[argI++];
                        break;
                    case '%':
                        replacement = "%";
                        break;
                }
                if (replacement != null) {
                    builder.replace(i - 1, i + 1, replacement);
                    i += replacement.length() - 2;
                }
            }
        }
        return builder;
    }

    public static CharSequence getText(Context context, int resId, Object... args) {
        return format(context.getText(resId), args);
    }

    public static Object cloneSpan(Object span) {
        if (span instanceof ForegroundColorSpan)
            return new ForegroundColorSpan(((ForegroundColorSpan) span).getForegroundColor());
        if (span instanceof BackgroundColorSpan)
            return new BackgroundColorSpan(((BackgroundColorSpan) span).getBackgroundColor());
        if (span instanceof StyleSpan)
            return new StyleSpan(((StyleSpan) span).getStyle());
        return null;
    }

    public static boolean areSpansEqual(Object span, Object span2) {
        if (span.getClass() != span2.getClass())
            return false;
        if (span instanceof ForegroundColorSpan)
            return ((ForegroundColorSpan) span).getForegroundColor() ==
                    ((ForegroundColorSpan) span2).getForegroundColor();
        if (span instanceof BackgroundColorSpan)
            return ((BackgroundColorSpan) span).getBackgroundColor() ==
                    ((BackgroundColorSpan) span2).getBackgroundColor();
        if (span instanceof StyleSpan)
            return ((StyleSpan) span).getStyle() == ((StyleSpan) span2).getStyle();
        return true;
    }

    public static void removeSpans(Spannable text, Class<?> type, int start, int end, Object mustEqual, boolean excludeNoCopySpans) {
        if (start == end)
            return;
        Object[] spans = text.getSpans(start, end, type);
        for (Object span : spans) {
            if (excludeNoCopySpans && span instanceof NoCopySpan)
                continue;
            if (mustEqual != null && !areSpansEqual(span, mustEqual))
                continue;
            int flags = text.getSpanFlags(span);
            int sStart = text.getSpanStart(span);
            int sEnd = text.getSpanEnd(span);
            text.removeSpan(span);
            if (sStart < start) {
                text.setSpan(span, sStart, start, flags);
                if (sEnd > end)
                    span = cloneSpan(span);
            }
            if (sEnd > end) {
                text.setSpan(span, end, sEnd, flags);
            }
        }
    }

    public static void removeSpans(Spannable text, Class<?> type, int start, int end, boolean excludeNoCopySpans) {
        removeSpans(text, type, start, end, null, excludeNoCopySpans);
    }

    public static void setAndMergeSpans(Spannable text, Object what, int start, int end, int flags) {
        Object[] spans = text.getSpans(Math.max(start - 1, 0), Math.min(end + 1, 0), what.getClass());
        for (Object span : spans) {
            if (!areSpansEqual(span, what))
                continue;
            int sStart = text.getSpanStart(span);
            int sEnd = text.getSpanEnd(span);
            if (sEnd < start || sStart > end)
                continue;
            text.removeSpan(span);
            if (sStart < start)
                start = sStart;
            if (sEnd > end)
                end = sEnd;
        }
        text.setSpan(what, start, end, flags);
    }

}