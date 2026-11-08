package az.miyav.desktop;

import az.miyav.domain.repository.LocalFavoriteStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Guest favourites in the user's MIYAV folder: one listing id per line, nothing else. */
final class FileFavoriteStore implements LocalFavoriteStore {
    private static final int LIMIT = 100;
    private final Path file;

    FileFavoriteStore(Path directory) {
        this.file = directory.resolve("guest-favorites.txt");
    }

    @Override
    public synchronized Set<UUID> load() {
        Set<UUID> ids = new LinkedHashSet<>();
        if (!Files.exists(file)) {
            return ids;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                try {
                    ids.add(UUID.fromString(line.trim()));
                } catch (IllegalArgumentException ignored) {
                    // A damaged line is dropped on the next write.
                }
            }
        } catch (IOException ignored) {
            // Unreadable favourites behave like none; browsing is unaffected.
        }
        return ids;
    }

    @Override
    public synchronized void add(UUID listingId) {
        Set<UUID> ids = load();
        if (ids.size() < LIMIT) {
            ids.add(listingId);
            write(ids);
        }
    }

    @Override
    public synchronized void remove(UUID listingId) {
        Set<UUID> ids = load();
        ids.remove(listingId);
        write(ids);
    }

    @Override
    public synchronized void removeAll(Collection<UUID> listingIds) {
        Set<UUID> ids = load();
        ids.removeAll(listingIds);
        write(ids);
    }

    private void write(Set<UUID> ids) {
        List<String> lines = new ArrayList<>();
        for (UUID id : ids) {
            lines.add(id.toString());
        }
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Saving a favourite is best effort on a read-only profile folder.
        }
    }
}
