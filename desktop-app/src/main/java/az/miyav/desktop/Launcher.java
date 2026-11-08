package az.miyav.desktop;

/**
 * Entry point of the packaged desktop app. JavaFX refuses to start when the main class itself extends
 * {@code Application} and JavaFX sits on the class path (as in the installer), so this plain class starts it.
 */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        MiyavDesktopApp.main(args);
    }
}
