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

    /**
     * Gallery on the listing screen: every photo fills the same frame (centre-cropped), so portrait and
     * landscape photos look the same size; the whole photo opens in the full-screen viewer on tap.
     */
    public void listingGallery(ImageView photo, ImageView backdrop, String path) {
        backdrop.setImageDrawable(null);
        if (path == null) {
            placeholder(photo, R.drawable.ic_paw, R.color.brand_coral);
            return;
        }
        String key = "listing:1440:" + path;
        backdrop.setTag(R.id.photo_tag, key);
        load(photo, key, R.drawable.ic_paw, R.color.brand_coral, 1440, ImageView.ScaleType.CENTER_CROP,
                () -> app.discovery().photo(app.auth().viewer(), path), bitmap -> {
                    if (key.equals(backdrop.getTag(R.id.photo_tag))) {
                        backdrop.setImageBitmap(bitmap);
                    }
                });
    }

    /** Full-screen viewer: the whole photo, positioned by the view's own matrix so it can be zoomed. */
    public void listingFull(ImageView view, String path) {
        listing(view, path, R.drawable.ic_paw, 1440, ImageView.ScaleType.MATRIX);
    }

    private void listing(ImageView view, String path, int placeholder, int maxEdge) {
        listing(view, path, placeholder, maxEdge, ImageView.ScaleType.CENTER_CROP);
    }

    private void listing(ImageView view, String path, int placeholder, int maxEdge, ImageView.ScaleType fitted) {
        if (path == null) {
            placeholder(view, placeholder, R.color.brand_coral);
            return;
        }
        load(view, "listing:" + maxEdge + ":" + path, placeholder, R.color.brand_coral, maxEdge, fitted,
                () -> app.discovery().photo(app.auth().viewer(), path));
    }

    /** A pet's photo from the private pets bucket (own pets, or pets on a walk the user can see). */
    public void pet(ImageView view, String path) {
        if (path == null || !app.auth().signedIn()) {
            placeholder(view, R.drawable.ic_paw, R.color.brand_coral);
            return;
        }
        load(view, "pet:" + path, R.drawable.ic_paw, R.color.brand_coral, 512, ImageView.ScaleType.CENTER_CROP,
                () -> app.myPets().photo(app.auth().requireSession(), path));
    }

    /** The pet photo on a walk request; guests can see it while the request is open or assigned. */
    public void walkPet(ImageView view, String path) {
        load(view, "walkpet:" + path, R.drawable.ic_paw, R.color.brand_coral, 720, ImageView.ScaleType.CENTER_CROP,
                () -> app.walkListings().petPhoto(app.auth().viewer(), path));
    }

    public void walkPetFull(ImageView view, String path) {
        load(view, "walkpet:1440:" + path, R.drawable.ic_paw, R.color.brand_coral, 1440, ImageView.ScaleType.MATRIX,
                () -> app.walkListings().petPhoto(app.auth().viewer(), path));
    }

    /** A chat photo from the private messages bucket; only the two participants can read it. */
    public void chat(ImageView view, String path, int maxEdge, ImageView.ScaleType fitted) {
        if (path == null || !app.auth().signedIn()) {
            placeholder(view, R.drawable.ic_image, R.color.brand_coral);
            return;
        }
        load(view, "chat:" + maxEdge + ":" + path, R.drawable.ic_image, R.color.brand_coral, maxEdge, fitted,
                () -> app.messaging().photo(app.auth().requireSession(), path));
    }

    /** Stops a pending load from landing in a recycled view that no longer shows a photo. */
    public void forget(ImageView view) {
        view.setTag(R.id.photo_tag, null);
        view.setImageDrawable(null);
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
            placeholder(view, R.drawable.ic_person, R.color.brand_coral);
            return;
        }
        load(view, "avatar:" + avatarPath, R.drawable.ic_person, R.color.brand_coral, 256, ImageView.ScaleType.CENTER_CROP,
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

    private void load(ImageView view, String key, int placeholder, int tint, int maxEdge, ImageView.ScaleType fitted,
                      Supplier<byte[]> source) {
        load(view, key, placeholder, tint, maxEdge, fitted, source, null);
    }

    private void load(ImageView view, String key, int placeholder, int tint, int maxEdge, ImageView.ScaleType fitted,
                      Supplier<byte[]> source, Consumer<Bitmap> alsoShow) {
        view.setTag(R.id.photo_tag, key);
        Bitmap cached = cache.get(key);
        if (cached != null) {
            show(view, cached, false, fitted);
            if (alsoShow != null) {
                alsoShow.accept(cached);
            }
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
                        show(view, bitmap, true, fitted);
                        if (alsoShow != null) {
                            alsoShow.accept(bitmap);
                        }
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

    private static void show(ImageView view, Bitmap bitmap, boolean animate, ImageView.ScaleType fitted) {
        view.setImageTintList(null);
        view.setScaleType(fitted);
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
