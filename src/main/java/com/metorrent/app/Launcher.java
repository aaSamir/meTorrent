package com.metorrent.app;

/**
 * Indirection around {@link App#main(String[])}.
 * Launching through a non-Application class avoids JavaFX's module-path
 * detection issues when the app is run from a shaded/fat jar.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        App.main(args);
    }
}
