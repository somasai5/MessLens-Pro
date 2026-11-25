package com.messlens.ui;

import javafx.scene.Scene;
import javafx.scene.text.Font;

import java.io.InputStream;
import java.net.URL;

/** Applies the MessLens stylesheet (and the bundled fonts it uses) to a scene or dialog. */
public final class Theme {

    /** Fonts shipped inside the jar (SIL Open Font License): Figtree for text, Bricolage Grotesque for headings. */
    private static final String[] FONTS = {
            "Figtree_400Regular.ttf", "Figtree_500Medium.ttf", "Figtree_600SemiBold.ttf", "Figtree_700Bold.ttf",
            "BricolageGrotesque_700Bold.ttf", "BricolageGrotesque_800ExtraBold.ttf",
            "JetBrainsMono_400Regular.ttf", "JetBrainsMono_600SemiBold.ttf"
    };
    private static boolean fontsLoaded;

    private Theme() {}

    /** Registers the bundled fonts once. If a font file is missing the stylesheet falls back to system fonts. */
    private static synchronized void loadFonts() {
        if (fontsLoaded) return;
        fontsLoaded = true;
        for (String f : FONTS) {
            try (InputStream in = Theme.class.getResourceAsStream("/fonts/" + f)) {
                if (in != null) Font.loadFont(in, 12);
            } catch (Exception ignored) {
                // a missing font only changes the look, never the behaviour
            }
        }
    }

    public static void apply(Scene scene) {
        loadFonts();
        URL url = Theme.class.getResource("/styles/theme.css");
        if (url != null && scene != null) {
            String css = url.toExternalForm();
            if (!scene.getStylesheets().contains(css)) scene.getStylesheets().add(css);
        }
    }

    public static String url() {
        loadFonts();
        URL url = Theme.class.getResource("/styles/theme.css");
        return url == null ? null : url.toExternalForm();
    }
}
