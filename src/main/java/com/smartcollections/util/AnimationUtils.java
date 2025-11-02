package com.smartcollections.util;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.util.Duration;

public class AnimationUtils {
    
    public static FadeTransition createFadeIn(Node node) {
        FadeTransition ft = new FadeTransition(Duration.millis(300), node);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        return ft;
    }
    
    public static FadeTransition createFadeOut(Node node) {
        FadeTransition ft = new FadeTransition(Duration.millis(200), node);
        ft.setFromValue(1.0);
        ft.setToValue(0.0);
        return ft;
    }
    
    public static void fadeTransition(Node node, boolean show) {
        FadeTransition ft = show ? createFadeIn(node) : createFadeOut(node);
        ft.play();
    }
    
    public static Timeline createPulse(Node node) {
        ScaleTransition st = new ScaleTransition(Duration.millis(200), node);
        st.setToX(1.1);
        st.setToY(1.1);
        st.setAutoReverse(true);
        st.setCycleCount(2);
        return new Timeline();
    }
}
