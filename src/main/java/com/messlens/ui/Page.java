package com.messlens.ui;

import javafx.scene.Node;

/** One screen of the application. Each sidebar item opens exactly one Page. */
public interface Page {

    /** Short unique key, e.g. "capture". */
    String id();

    /** Text shown in the sidebar. */
    String navLabel();

    /** Icon name from {@link Icons}. */
    String icon();

    /** Big heading shown at the top of the page. */
    String title();

    /** One line under the heading that explains what to do here. */
    String subtitle();

    /** The page content (built once, reused every time the page is opened). */
    Node view();

    /**
     * Widest the page (heading + content) may grow. The block is centred in the window, so on a big screen
     * there is equal space on both sides instead of an empty strip on the right.
     */
    default double maxWidth() {
        return 1280;
    }

    /** Optional buttons shown at the right of the heading (Refresh, Export ...). */
    default Node headerActions() {
        return null;
    }

    /** Called every time the page becomes visible: reload fresh data here. */
    default void onShow() {}

    /** Called just before another page replaces this one: switch the camera off here. */
    default void onHide() {}

    /** Called when the user signs out: release camera and other resources here. */
    default void dispose() {}

    /** Small number shown next to the sidebar item (0 = hidden). */
    default int badgeCount() {
        return 0;
    }
}
