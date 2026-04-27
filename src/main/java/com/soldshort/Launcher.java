package com.soldshort;

import com.soldshort.ui.MainApp;

/**
 * Entry point used when running the application as a fat JAR.
 *
 * JavaFX requires its Main class to extend Application, which prevents the JVM
 * from loading it directly from a shaded JAR.  This plain (non-JavaFX) launcher
 * delegates immediately to MainApp.main() so that the JavaFX bootstrap can
 * detect and initialise the runtime correctly.
 */
public class Launcher {
    public static void main(String[] args) {
        MainApp.main(args);
    }
}
