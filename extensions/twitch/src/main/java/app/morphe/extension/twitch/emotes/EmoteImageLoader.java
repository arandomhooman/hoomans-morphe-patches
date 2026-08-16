package app.morphe.extension.twitch.emotes;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.LruCache;
import android.util.Size;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class EmoteImageLoader {
    private static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;
    private static final long MAX_DISK_BYTES = 32L * 1024L * 1024L;
    private static final int MAX_SOURCE_DIMENSION = 8_192;
    private static final long MAX_SOURCE_PIXELS = 32L * 1024L * 1024L;
    private static final int MAX_DECODE_DIMENSION = 512;
    private static final int MIN_DECODE_DIMENSION = 128;
    private static final long RETRY_BACKOFF_MS = 5L * 60L * 1000L;
    private static final String USER_AGENT = "HoomanMorpheTwitchEmotes/1.0";

    private final Object diskCacheLock = new Object();
    private long diskCacheBytes = -1L;
    private final Set<String> loading = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Long> failedUntil = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2,
            2,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(64),
            new DaemonThreadFactory()
    );
    private final Consumer<String> onUpdated;
    private final LruCache<String, ImageData> memory = new LruCache<>(16 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, ImageData value) {
            return Math.max(1, value.costBytes);
        }
    };

    EmoteImageLoader(Consumer<String> onUpdated) {
        this.onUpdated = onUpdated;
    }

    Drawable createDrawable(Resources resources, Emote emote) {
        ImageData data = memory.get(emote.url);
        if (data == null) {
            return null;
        }
        if (data.drawableState != null) {
            return data.drawableState.newDrawable(resources);
        }
        return data.bitmap == null ? null : new BitmapDrawable(resources, data.bitmap);
    }

    void request(Context context, Emote emote, int targetHeight) {
        Long retryAfter = failedUntil.get(emote.url);
        if (retryAfter != null && retryAfter > System.currentTimeMillis()) {
            return;
        }
        if (memory.get(emote.url) != null || !loading.add(emote.url)) {
            return;
        }
        Context applicationContext = context.getApplicationContext();
        int targetDimension = Math.min(
                MAX_DECODE_DIMENSION,
                Math.max(MIN_DECODE_DIMENSION, targetHeight * 2)
        );
        try {
            executor.execute(() -> load(applicationContext, emote, targetDimension));
        } catch (RejectedExecutionException ignored) {
            loading.remove(emote.url);
            failedUntil.put(emote.url, System.currentTimeMillis() + RETRY_BACKOFF_MS);
        }
    }

    private void load(Context context, Emote emote, int targetDimension) {
        boolean succeeded = false;
        try {
            File directory = new File(context.getCacheDir(), "morphe_emote_images");
            File cache = new File(directory, sha256(emote.url) + ".bin");
            byte[] cached = readCache(cache);
            ImageData data = null;
            if (cached != null) {
                try {
                    data = decode(cached, emote.animated, targetDimension);
                } catch (Exception ignored) {
                    deleteCache(cache);
                }
            }
            if (data == null) {
                byte[] downloaded = download(emote.url);
                data = decode(downloaded, emote.animated, targetDimension);
                writeCache(directory, cache, downloaded);
            }
            memory.put(emote.url, data);
            failedUntil.remove(emote.url);
            succeeded = true;
        } catch (Exception ignored) {
            failedUntil.put(emote.url, System.currentTimeMillis() + RETRY_BACKOFF_MS);
        } finally {
            loading.remove(emote.url);
            if (succeeded) {
                onUpdated.accept(emote.url);
            }
        }
    }

    private static ImageData decode(byte[] bytes, boolean animated, int targetDimension)
            throws IOException {
        if (animated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Drawable decoded;
            try {
                decoded = ImageDecoder.decodeDrawable(
                        ImageDecoder.createSource(ByteBuffer.wrap(bytes)),
                        (decoder, info, source) -> configureDecoder(decoder, info, targetDimension)
                );
            } catch (IllegalArgumentException failure) {
                throw new IOException("Invalid animated emote dimensions", failure);
            }
            Drawable.ConstantState state = decoded.getConstantState();
            if (state != null) {
                int width = Math.max(1, decoded.getIntrinsicWidth());
                int height = Math.max(1, decoded.getIntrinsicHeight());
                long estimate = (long) width * height * 4L * 4L;
                return new ImageData(null, state, saturatedInt(estimate));
            }
        }

        Bitmap bitmap = decodeBitmap(bytes, targetDimension);
        if (bitmap == null) {
            throw new IOException("Unable to decode emote image");
        }
        return new ImageData(bitmap, null, bitmap.getByteCount());
    }

    private static void configureDecoder(
            ImageDecoder decoder,
            ImageDecoder.ImageInfo info,
            int targetDimension
    ) {
        Size size = info.getSize();
        validateDimensions(size.getWidth(), size.getHeight());
        int[] target = constrainedSize(size.getWidth(), size.getHeight(), targetDimension);
        if (target[0] != size.getWidth() || target[1] != size.getHeight()) {
            decoder.setTargetSize(target[0], target[1]);
        }
    }

    private static Bitmap decodeBitmap(byte[] bytes, int targetDimension) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        validateDimensions(bounds.outWidth, bounds.outHeight);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetDimension);
        Bitmap decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (decoded == null) {
            return null;
        }

        int[] target = constrainedSize(decoded.getWidth(), decoded.getHeight(), targetDimension);
        if (target[0] == decoded.getWidth() && target[1] == decoded.getHeight()) {
            return decoded;
        }
        Bitmap scaled = Bitmap.createScaledBitmap(decoded, target[0], target[1], true);
        if (scaled != decoded) {
            decoded.recycle();
        }
        return scaled;
    }

    private static void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0 || width > MAX_SOURCE_DIMENSION ||
                height > MAX_SOURCE_DIMENSION || (long) width * height > MAX_SOURCE_PIXELS) {
            throw new IllegalArgumentException("Emote dimensions exceed safe decode limits");
        }
    }

    private static int sampleSize(int width, int height, int targetDimension) {
        int sample = 1;
        while (width / (sample * 2) >= targetDimension &&
                height / (sample * 2) >= targetDimension) {
            sample *= 2;
        }
        return sample;
    }

    private static int[] constrainedSize(int width, int height, int maxDimension) {
        int largest = Math.max(width, height);
        if (largest <= maxDimension) {
            return new int[]{width, height};
        }
        float scale = (float) maxDimension / largest;
        return new int[]{
                Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale))
        };
    }

    private static int saturatedInt(long value) {
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private byte[] readCache(File cache) {
        synchronized (diskCacheLock) {
            if (!cache.isFile()) {
                return null;
            }
            cache.setLastModified(System.currentTimeMillis());
            try (InputStream input = new FileInputStream(cache)) {
                return readLimited(input);
            } catch (IOException ignored) {
                deleteCacheLocked(cache);
                return null;
            }
        }
    }

    private void writeCache(File directory, File cache, byte[] bytes) throws IOException {
        synchronized (diskCacheLock) {
            if (!directory.isDirectory() && !directory.mkdirs()) {
                return;
            }
            initializeDiskCacheBytes(directory);
            long oldLength = cache.isFile() ? cache.length() : 0L;
            File temporary = new File(directory, cache.getName() + ".tmp");
            try {
                try (FileOutputStream output = new FileOutputStream(temporary)) {
                    output.write(bytes);
                }
                if (!temporary.renameTo(cache)) {
                    try (FileOutputStream output = new FileOutputStream(cache)) {
                        output.write(bytes);
                    }
                }
            } finally {
                //noinspection ResultOfMethodCallIgnored
                temporary.delete();
            }
            diskCacheBytes = Math.max(0L, diskCacheBytes - oldLength) + cache.length();
            trimDiskCache(directory);
        }
    }

    private void deleteCache(File cache) {
        synchronized (diskCacheLock) {
            deleteCacheLocked(cache);
        }
    }

    private void deleteCacheLocked(File cache) {
        long length = cache.length();
        if (cache.delete() && diskCacheBytes >= 0L) {
            diskCacheBytes = Math.max(0L, diskCacheBytes - length);
        }
    }

    private static byte[] download(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(7_000);
        connection.setReadTimeout(10_000);
        connection.setRequestProperty("Accept", "image/webp,image/*;q=0.8");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status + " for " + url);
            }
            try (InputStream input = connection.getInputStream()) {
                return readLimited(input);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8_192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_IMAGE_BYTES) {
                throw new IOException("Emote image exceeds " + MAX_IMAGE_BYTES + " bytes");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte item : digest) {
            result.append(String.format("%02x", item & 0xff));
        }
        return result.toString();
    }

    private void initializeDiskCacheBytes(File directory) {
        if (diskCacheBytes >= 0L) {
            return;
        }
        diskCacheBytes = 0L;
        File[] files = directory.listFiles((file, name) -> name.endsWith(".bin"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            diskCacheBytes += file.length();
        }
    }

    private void trimDiskCache(File directory) {
        if (diskCacheBytes <= MAX_DISK_BYTES) {
            return;
        }
        File[] files = directory.listFiles((file, name) -> name.endsWith(".bin"));
        if (files == null) {
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (File file : files) {
            long length = file.length();
            if (file.delete()) {
                diskCacheBytes = Math.max(0L, diskCacheBytes - length);
            }
            if (diskCacheBytes <= MAX_DISK_BYTES) {
                break;
            }
        }
    }

    private static final class ImageData {
        final Bitmap bitmap;
        final Drawable.ConstantState drawableState;
        final int costBytes;

        ImageData(Bitmap bitmap, Drawable.ConstantState drawableState, int costBytes) {
            this.bitmap = bitmap;
            this.drawableState = drawableState;
            this.costBytes = costBytes;
        }
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        private int nextId;

        @Override
        public synchronized Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "morphe-emote-image-" + nextId++);
            thread.setDaemon(true);
            return thread;
        }
    }
}
