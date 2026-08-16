package app.morphe.extension.twitch.emotes;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.ReplacementSpan;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

@SuppressWarnings("unused")
public final class EmoteSupport {
    private static final Object LOCK = new Object();
    private static final WeakHashMap<TextView, BoundMessage> BOUND_MESSAGES = new WeakHashMap<>();
    private static final EmoteCatalog CATALOG = new EmoteCatalog(EmoteSupport::refreshCatalog);
    private static final EmoteImageLoader IMAGES = new EmoteImageLoader(EmoteSupport::refreshImage);
    private static final View.OnAttachStateChangeListener VIEW_LIFECYCLE =
            new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View view) {
                    TextView textView = (TextView) view;
                    BoundMessage message;
                    synchronized (LOCK) {
                        message = BOUND_MESSAGES.get(textView);
                    }
                    if (message != null) {
                        scheduleRender(textView, message);
                    }
                }

                @Override
                public void onViewDetachedFromWindow(View view) {
                    stopAnimations(((TextView) view).getText());
                }
            };

    private static volatile String lastRoomId;

    private EmoteSupport() {
    }

    public static void onChannelChanged(String channelId, String channelName) {
        String normalized = normalizeChannelId(channelId);
        if (normalized != null) {
            lastRoomId = normalized;
        }
    }

    public static void bind(TextView textView, String sourceChannelId) {
        if (textView == null) {
            return;
        }
        CharSequence current = textView.getText();
        synchronized (LOCK) {
            BOUND_MESSAGES.remove(textView);
        }
        stopAnimations(current);
        textView.removeOnAttachStateChangeListener(VIEW_LIFECYCLE);

        if (current == null || current.length() == 0) {
            return;
        }
        String channelId = normalizeChannelId(sourceChannelId);
        if (channelId == null) {
            // Normal non-shared-chat rows can omit sourceChannelId. Twitch 29.9.1 presents one active
            // live-chat room, so use the latest connection key only for that missing-field case.
            channelId = lastRoomId;
        }

        BoundMessage message = new BoundMessage(SpannedString.valueOf(current), channelId);
        synchronized (LOCK) {
            BOUND_MESSAGES.put(textView, message);
        }
        textView.addOnAttachStateChangeListener(VIEW_LIFECYCLE);
        CATALOG.ensureLoaded(textView.getContext(), channelId);
        render(textView, message);
    }

    private static void render(TextView textView, BoundMessage message) {
        synchronized (LOCK) {
            if (BOUND_MESSAGES.get(textView) != message) {
                return;
            }
        }
        if (!textView.isAttachedToWindow()) {
            return;
        }
        stopAnimations(textView.getText());

        SpannedString original = message.original;
        ReplacementSpan[] existingSpans = original.getSpans(
                0,
                original.length(),
                ReplacementSpan.class
        );
        SpannableStringBuilder builder = null;
        Set<String> pendingImages = null;
        Map<String, Emote> missingImages = null;
        int length = original.length();
        int index = 0;
        while (index < length) {
            while (index < length && isSeparator(original.charAt(index))) {
                index++;
            }
            int start = index;
            while (index < length && !isSeparator(original.charAt(index))) {
                index++;
            }
            int end = index;
            if (start == end || hasReplacementSpan(original, existingSpans, start, end)) {
                continue;
            }

            String token = original.subSequence(start, end).toString();
            Emote emote = CATALOG.find(message.channelId, token);
            if (emote == null) {
                continue;
            }
            android.graphics.drawable.Drawable drawable =
                    IMAGES.createDrawable(textView.getResources(), emote);
            if (drawable == null) {
                if (pendingImages == null) {
                    pendingImages = new HashSet<>();
                    missingImages = new LinkedHashMap<>();
                }
                pendingImages.add(emote.url);
                missingImages.put(emote.url, emote);
                continue;
            }

            if (builder == null) {
                builder = new SpannableStringBuilder(original);
            }
            builder.setSpan(
                    new CenteredImageSpan(textView, drawable),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        message.pendingImages = pendingImages == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(pendingImages);

        // Publish pendingImages before enqueueing work. A disk-cache decode can complete immediately on
        // a worker, and its callback must already be able to find this row as a waiter.
        if (missingImages != null) {
            for (Emote emote : missingImages.values()) {
                IMAGES.request(
                        textView.getContext(),
                        emote,
                        Math.max(1, Math.round(textView.getTextSize() * 1.25f))
                );
            }
        }

        if (builder != null) {
            textView.setText(new SpannedString(builder), TextView.BufferType.SPANNABLE);
        } else if (textView.getText() != message.original) {
            textView.setText(message.original, TextView.BufferType.SPANNABLE);
        }
    }

    private static boolean hasReplacementSpan(
            Spanned text,
            ReplacementSpan[] spans,
            int start,
            int end
    ) {
        for (ReplacementSpan span : spans) {
            if (text.getSpanStart(span) < end && text.getSpanEnd(span) > start) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSeparator(char value) {
        return Character.isWhitespace(value) || (value >= '⁦' && value <= '⁩');
    }

    private static String normalizeChannelId(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 20) {
            return null;
        }
        for (int index = 0; index < trimmed.length(); index++) {
            if (!Character.isDigit(trimmed.charAt(index))) {
                return null;
            }
        }
        return trimmed;
    }

    private static void refreshImage(String url) {
        List<Map.Entry<TextView, BoundMessage>> snapshot = boundMessages();
        for (Map.Entry<TextView, BoundMessage> entry : snapshot) {
            TextView textView = entry.getKey();
            BoundMessage message = entry.getValue();
            if (textView != null && message != null && message.pendingImages.contains(url)) {
                scheduleRender(textView, message);
            }
        }
    }

    private static void refreshCatalog(String channelId) {
        List<Map.Entry<TextView, BoundMessage>> snapshot = boundMessages();
        for (Map.Entry<TextView, BoundMessage> entry : snapshot) {
            TextView textView = entry.getKey();
            BoundMessage message = entry.getValue();
            if (textView == null || message == null) {
                continue;
            }
            if (channelId == null || channelId.equals(message.channelId)) {
                scheduleRender(textView, message);
            }
        }
    }

    private static List<Map.Entry<TextView, BoundMessage>> boundMessages() {
        synchronized (LOCK) {
            return new ArrayList<>(BOUND_MESSAGES.entrySet());
        }
    }

    private static void scheduleRender(TextView textView, BoundMessage message) {
        synchronized (LOCK) {
            if (BOUND_MESSAGES.get(textView) != message || message.refreshPosted) {
                return;
            }
            message.refreshPosted = true;
        }
        boolean posted = textView.post(() -> {
            synchronized (LOCK) {
                message.refreshPosted = false;
                if (BOUND_MESSAGES.get(textView) != message) {
                    return;
                }
            }
            render(textView, message);
        });
        if (!posted) {
            synchronized (LOCK) {
                message.refreshPosted = false;
            }
        }
    }

    private static void stopAnimations(CharSequence text) {
        if (!(text instanceof Spanned)) {
            return;
        }
        Spanned spanned = (Spanned) text;
        for (CenteredImageSpan span :
                spanned.getSpans(0, spanned.length(), CenteredImageSpan.class)) {
            span.stop();
        }
    }

    private static final class BoundMessage {
        final SpannedString original;
        final String channelId;
        volatile Set<String> pendingImages = Collections.emptySet();
        boolean refreshPosted;

        BoundMessage(SpannedString original, String channelId) {
            this.original = original;
            this.channelId = channelId;
        }
    }
}
