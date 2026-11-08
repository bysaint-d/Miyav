package az.miyav.android.ui;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;

import az.miyav.android.MiyavApplication;
import az.miyav.android.R;
import az.miyav.domain.user.User;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Loads listing photos (with the user's token when signed in, otherwise as a guest, which storage RLS
 * limits to photos of active listings) and public avatars, with an in-memory cache.
 */
public final class PhotoLoader {
    private final MiyavApplication app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> cache;

    public PhotoLoader(MiyavApplication app) {
        this.app = app;
        int kilobytes = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
        this.cache = new LruCache<>(kilobytes) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }

    /** Card and thumbnail size: decoded small so lists scroll smoothly and the cache holds more photos. */
    public void listing(ImageView view, String path, int placeholder) {
        listing(view, path, placeholder, 720);
    }

    /** Full-width gallery on the listing screen. */
    public void listingLarge(ImageView view, String path, int placeholder) {
        listing(view, path, placeholder, 1440);
    }

    private void listing(ImageView view, String path, int placeholder, int maxEdge) {
        if (path == null) {
            placeholder(view, placeholder, R.color.brand_coral);
            return;
        }
        load(view, "listing:" + maxEdge + ":" + path, placeholder, R.color.brand_coral, maxEdge,
                () -> app.discovery().photo(app.auth().viewer(), path));
    }

    /** A pet's photo from the private pets bucket (own pets, or pets on a walk the user can see). */
    public void pet(ImageView view, String path) {
        if (path == null || !app.auth().signedIn()) {
            placeholder(view, R.drawable.ic_paw, R.color.brand_coral);
            return;
        }
        load(view, "pet:" + path, R.drawable.ic_paw, R.color.brand_coral, 512,
                () -> app.myPets().photo(app.auth().requireSession(), path));
    }

    /** Forgets a pet photo after it was replaced. */
    public void forgetPet(String path) {
        if (path != null) {
            cache.remove("pet:" + path);
        }
    }

    public void avatar(ImageView view, User user) {
        avatar(view, user == null ? null : user.avatarPath());
    }

    public void avatar(ImageView view, String avatarPath) {
        if (avatarPath == null) {
            placeholder(view, R.drawable.ic_person, R.color.brand_lavender);
            return;
        }
        load(view, "avatar:" + avatarPath, R.drawable.ic_person, R.color.brand_lavender, 256,
                () -> app.userService().avatar(avatarPath));
    }

    /** Fetches an avatar bitmap for non-ImageView consumers such as map markers. */
    public void avatarBitmap(String avatarPath, Consumer<Bitmap> onLoaded) {
        if (avatarPath == null) {
            return;
        }
        String key = "avatar:" + avatarPath;
        Bitmap cached = cache.get(key);
        if (cached != null) {
            onLoaded.accept(cached);
            return;
        }
        pool.execute(() -> {
            try {
                Bitmap bitmap = decode(app.userService().avatar(avatarPath), 256);
                if (bitmap != null) {
                    cache.put(key, bitmap);
                    main.post(() -> onLoaded.accept(bitmap));
                }
            } catch (RuntimeException ignored) {
                // The species marker stays when the avatar cannot be loaded.
            }
        });
    }

    public void clear() {
        cache.evictAll();
    }

    private void load(ImageView view, String key, int placeholder, int tint, int maxEdge, Supplier<byte[]> source) {
        view.setTag(R.id.photo_tag, key);
        Bitmap cached = cache.get(key);
        if (cached != null) {
            show(view, cached, false);
            return;
        }
        placeholder(view, placeholder, tint);
        pool.execute(() -> {
            try {
                Bitmap bitmap = decode(source.get(), maxEdge);
                if (bitmap == null) {
                    return;
                }
                cache.put(key, bitmap);
                main.post(() -> {
                    if (key.equals(view.getTag(R.id.photo_tag))) {
                        show(view, bitmap, true);
                    }
                });
            } catch (RuntimeException ignored) {
                // The placeholder stays visible when the image cannot be loaded.
            }
        });
    }

    private static void placeholder(ImageView view, int placeholder, int tint) {
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setImageResource(placeholder);
        view.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(view.getContext(), tint)));
    }

    private static void show(ImageView view, Bitmap bitmap, boolean animate) {
        view.setImageTintList(null);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setImageBitmap(bitmap);
        if (animate) {
            view.setAlpha(0f);
            view.animate().alpha(1f).setDuration(180).start();
        }
    }

    private static Bitmap decode(byte[] bytes, int maxEdge) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }
}
