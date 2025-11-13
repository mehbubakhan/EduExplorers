package com.example.aoopproject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;                 // <-- ADD THIS IMPORT
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;

import java.security.SecureRandom;
import java.util.*;

/**
 * Shape-only quiz controller with:
 * - TTS speaks once per question (no double on first load)
 * - Back button disabled during exam, enabled after completion
 */
public class ShapeTestShapeController {

    /* ===== FXML Bindings ===== */
    @FXML private Label titleLabel, timerLabel, progressLabel, questionLabel, feedbackLabel;
    @FXML private Button opt1, opt2, opt3, opt4, nextButton, startButton, playButton, backButton;

    @FXML
    private void switchToShapeTestShape(ActionEvent event){
        ChangeFxmlController.switchScene(event,"shape-test-shape.fxml");
    }

    /* ===== State ===== */
    private final List<Button> optionButtons = new ArrayList<>();
    private final SecureRandom rng = new SecureRandom();
    private static final int TOTAL_QUESTIONS = 15;

    private enum ShapeKind { CIRCLE, TRIANGLE, SQUARE, RECTANGLE, OVAL, STAR, DIAMOND, PENTAGON, HEXAGON }
    private ShapeKind targetShape;
    private int qIndex = 0, score = 0;
    private boolean answered = false, running = false, over = false;

    private final Map<Button, ShapeKind> optionAnswer = new HashMap<>();
    private Button correctButton = null;

    /* ===== Voice System ===== */
    private final VoiceService voice = new HybridVoiceService(false);

    @FXML
    public void initialize() {
        optionButtons.addAll(Arrays.asList(opt1, opt2, opt3, opt4));
        for (Button b : optionButtons) {
            b.setPrefHeight(130);
            b.setPrefWidth(360);
            b.setMaxWidth(Double.MAX_VALUE);
            b.setContentDisplay(ContentDisplay.TOP);
            b.setWrapText(true);
            b.setStyle("-fx-font-size: 18px;");
        }
        titleLabel.setText("Shape Quiz");
        timerLabel.setText("—");
        progressLabel.setText("Q 0/15   Score: 0");
        questionLabel.setText("Press Start, listen to the voice, and select the correct option.");
        feedbackLabel.setText("");
        disableOptions(true);
        nextButton.setDisable(true);
        playButton.setDisable(true);
        backButton.setDisable(false);
    }

    /* ===== Quiz Flow ===== */
    @FXML private void handleExamStart(ActionEvent e) {
        if (running) return;
        running = true;
        over = false;
        score = 0;
        qIndex = 0;

        // Lock Back while running
        backButton.setDisable(true);

        startButton.setDisable(true);
        playButton.setDisable(false);
        disableOptions(false);
        nextButton.setDisable(true);

        loadQuestion();      // DOES NOT speak
        speakCurrent();      // Speak exactly once for first question
        updateProgress();
    }

    private void loadQuestion() {
        resetButtons();
        feedbackLabel.setText("");

        targetShape = randomShape();
        questionLabel.setText("Select the shape " + nice(targetShape));

        optionAnswer.clear();
        correctButton = null;

        // Build 4 unique options
        Set<ShapeKind> used = new HashSet<>();
        used.add(targetShape);
        List<ShapeKind> options = new ArrayList<>();
        options.add(targetShape);
        while (options.size() < 4) {
            ShapeKind s = randomShape();
            if (used.add(s)) options.add(s);
        }
        Collections.shuffle(options, rng);

        for (int i = 0; i < 4; i++) {
            Button b = optionButtons.get(i);
            ShapeKind s = options.get(i);
            b.setGraphic(shapeGraphic(s, 64, 64, Color.BLACK));  // needs javafx.scene.Node
            b.setText(null);
            optionAnswer.put(b, s);
            if (s == targetShape) correctButton = b;
        }

        answered = false;
        nextButton.setDisable(true);
        // No speak here — avoids first-question double speech
    }

    @FXML private void handleOptionClick(ActionEvent e) {
        if (!running || over || answered) return;
        Button clicked = (Button) e.getSource();
        ShapeKind chosen = optionAnswer.get(clicked);
        boolean ok = chosen == targetShape;

        if (ok) {
            score++;
            setGreen(clicked);
            feedbackLabel.setStyle("-fx-text-fill:#0b5d1e; -fx-font-weight:bold;");
            feedbackLabel.setText("Correct! 😄");
            speakAsync("Correct!");
        } else {
            setRed(clicked);
            if (correctButton != null) setGreen(correctButton);
            feedbackLabel.setStyle("-fx-text-fill:#7a0c0c; -fx-font-weight:bold;");
            feedbackLabel.setText("Sorry, wrong answer. 🙁");
            speakAsync("Sorry, wrong answer.");
        }

        answered = true;
        disableOptions(true);
        updateProgress();

        if (qIndex >= TOTAL_QUESTIONS - 1) {
            running = false;
            over = true;
            showAndSpeakResult();
        } else {
            nextButton.setDisable(false);
        }
    }

    @FXML private void handleNext(ActionEvent e) {
        if (!running || over || !answered) return;
        qIndex++;
        if (qIndex >= TOTAL_QUESTIONS) {
            running = false;
            over = true;
            showAndSpeakResult();
        } else {
            loadQuestion();           // doesn’t speak
            updateProgress();
            disableOptions(false);
            speakCurrent();           // speak exactly once per new question
        }
    }

    @FXML private void handlePlayQuestion(ActionEvent e) {
        if (running) speakCurrent();
    }

    @FXML private void BackToTest(ActionEvent event) {
        if (backButton.isDisabled()) return; // guard while exam is running
        ChangeFxmlController.switchScene(event, "shape-test-format.fxml");
    }

    /* ===== Voice + Result ===== */

    private void showAndSpeakResult() {
        // Unlock Back when the exam is over
        backButton.setDisable(false);

        String comment = (score >= 13) ? "Excellent!" :
                (score >= 9)  ? "Great job!" :
                        (score >= 5)  ? "Good try!"  :
                                "Keep practicing!";

        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Result");
        a.setHeaderText(String.format("You scored %d/%d", score, TOTAL_QUESTIONS));
        a.setContentText(comment);
        a.setOnShown(ev -> speakAsync("You scored " + score + " out of " + TOTAL_QUESTIONS + ". " + comment));
        a.show();
    }

    private void speakCurrent() {
        speakAsync("Select the shape " + nice(targetShape));
    }

    private void speakAsync(String text) {
        new Thread(() -> {
            try { voice.speak(text); } catch (Exception ignored) {}
        }, "tts-thread").start();
    }

    /* ===== Helpers ===== */
    private void updateProgress() {
        progressLabel.setText(String.format("Q %d/%d   Score: %d", qIndex + 1, TOTAL_QUESTIONS, score));
    }

    private void disableOptions(boolean dis) { for (Button b : optionButtons) b.setDisable(dis); }
    private void resetButtons() { for (Button b : optionButtons) { b.setDisable(false); b.setStyle(""); b.setGraphic(null); } }
    private void setGreen(Button b) { b.setStyle("-fx-background-color:#18632b; -fx-text-fill:white; -fx-font-weight:bold;"); }
    private void setRed(Button b) { b.setStyle("-fx-background-color:#8f1f1f; -fx-text-fill:white; -fx-font-weight:bold;"); }

    private ShapeKind randomShape() {
        ShapeKind[] arr = ShapeKind.values();
        return arr[rng.nextInt(arr.length)];
    }

    private String nice(ShapeKind s) {
        String n = s.name().toLowerCase();
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private Node shapeGraphic(ShapeKind kind, double w, double h, Color fill) {
        Shape s;
        switch (kind) {
            case CIRCLE -> s = new Circle(Math.min(w, h) / 3.0);
            case TRIANGLE -> {
                Polygon p = new Polygon();
                p.getPoints().addAll(w * 0.5, h * 0.15, w * 0.15, h * 0.85, w * 0.85, h * 0.85);
                s = p;
            }
            case SQUARE -> s = new Rectangle(w * 0.6, w * 0.6);
            case RECTANGLE -> s = new Rectangle(w * 0.8, h * 0.5);
            case OVAL -> s = new Ellipse(w * 0.32, h * 0.22);
            case STAR -> {
                Polygon star = new Polygon();
                double cx = w * 0.5, cy = h * 0.5, r1 = Math.min(w, h) * 0.28, r2 = r1 * 0.45;
                for (int i = 0; i < 10; i++) {
                    double ang = Math.toRadians(-90 + i * 36);
                    double r = (i % 2 == 0) ? r1 : r2;
                    star.getPoints().addAll(cx + r * Math.cos(ang), cy + r * Math.sin(ang));
                }
                s = star;
            }
            case DIAMOND -> s = new Polygon(w * 0.5, h * 0.12, w * 0.82, h * 0.5, w * 0.5, h * 0.88, w * 0.18, h * 0.5);
            case PENTAGON -> s = regularPolygon(5, w, h);
            case HEXAGON -> s = regularPolygon(6, w, h);
            default -> s = new Rectangle(w * 0.6, h * 0.6);
        }
        s.setFill(fill);
        s.setStroke(Color.BLACK);
        s.setStrokeWidth(1.2);
        StackPane box = new StackPane(s);
        box.setMinSize(w, h);
        box.setPrefSize(w, h);
        box.setAlignment(Pos.CENTER);
        return box; // StackPane extends javafx.scene.layout.Pane -> Node, OK
    }

    private Shape regularPolygon(int n, double w, double h) {
        Polygon p = new Polygon();
        double cx = w * 0.5, cy = h * 0.5, r = Math.min(w, h) * 0.32;
        for (int i = 0; i < n; i++) {
            double ang = Math.toRadians(-90 + i * 360.0 / n);
            p.getPoints().addAll(cx + r * Math.cos(ang), cy + r * Math.sin(ang));
        }
        return p;
    }

    /* ===== Voice Engine ===== */
    interface VoiceService { void speak(String text) throws Exception; }

    static class HybridVoiceService implements VoiceService {
        HybridVoiceService(boolean unused) {}
        @Override public void speak(String text) throws Exception {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                String cmd = "$s=New-Object -ComObject SAPI.SpVoice; $s.Speak('" + text.replace("'", "''") + "');";
                run(new String[]{"powershell", "-NoProfile", "-Command", cmd});
            } else if (os.contains("mac")) {
                run(new String[]{"say", text});
            } else {
                run(new String[]{"bash","-lc","(command -v espeak-ng >/dev/null && espeak-ng '" + text + "') || (command -v espeak >/dev/null && espeak '" + text + "')"});
            }
        }
        private static void run(String[] cmd) throws Exception {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.waitFor();
        }
    }
}
