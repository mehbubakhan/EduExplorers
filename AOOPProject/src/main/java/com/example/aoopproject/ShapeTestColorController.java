package com.example.aoopproject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;                  // for setGraphic(Node)
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.security.SecureRandom;
import java.util.*;

/**
 * Color-only quiz controller:
 * - 15 questions, 4 unique color swatches per question (graphic-only)
 * - TTS speaks once per question + feedback + final score
 * - Back disabled during the exam; re-enabled when finished
 */
public class ShapeTestColorController {

    /* ===== FXML Bindings ===== */
    @FXML private Label titleLabel, timerLabel, progressLabel, questionLabel, feedbackLabel;
    @FXML private Button opt1, opt2, opt3, opt4, nextButton, startButton, playButton, backButton;
    @FXML
    private void switchToShapeTestColor(ActionEvent event){
        ChangeFxmlController.switchScene(event,"shape-test-color.fxml");
    }
    /* ===== State ===== */
    private final List<Button> optionButtons = new ArrayList<>();
    private final SecureRandom rng = new SecureRandom();
    private static final int TOTAL_QUESTIONS = 15;

    private enum ColorKind {
        RED(Color.RED), BLUE(Color.DODGERBLUE), GREEN(Color.GREEN), YELLOW(Color.GOLD),
        ORANGE(Color.ORANGE), PURPLE(Color.MEDIUMPURPLE), PINK(Color.HOTPINK),
        BROWN(Color.SADDLEBROWN), BLACK(Color.BLACK), GRAY(Color.GRAY);
        final Color fx;
        ColorKind(Color c){ this.fx=c; }
        @Override public String toString(){
            String n=name().toLowerCase();
            return Character.toUpperCase(n.charAt(0))+n.substring(1);
        }
    }

    private ColorKind targetColor;
    private int qIndex = 0, score = 0;
    private boolean answered = false, running = false, over = false;

    private final Map<Button, ColorKind> optionAnswer = new HashMap<>();
    private Button correctButton = null;

    /* ===== Voice ===== */
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

        titleLabel.setText("Color Quiz");
        timerLabel.setText("—");
        progressLabel.setText("Q 0/15   Score: 0");
        questionLabel.setText("Press Start, listen to the voice, and select the correct color.");
        feedbackLabel.setText("");

        disableOptions(true);
        nextButton.setDisable(true);
        playButton.setDisable(true);
        backButton.setDisable(false);
    }

    /* ===== Quiz Flow ===== */
    @FXML private void handleExamStart(ActionEvent e) {
        if (running) return;
        running = true; over = false; score = 0; qIndex = 0;

        backButton.setDisable(true);         // lock Back
        startButton.setDisable(true);
        playButton.setDisable(false);
        disableOptions(false);
        nextButton.setDisable(true);

        loadQuestion();   // no speak here
        speakCurrent();   // speak once for the first question
        updateProgress();
    }

    private void loadQuestion() {
        resetButtons();
        feedbackLabel.setText("");

        targetColor = randomColor();
        questionLabel.setText("Select the color " + targetColor);

        optionAnswer.clear();
        correctButton = null;

        // Build 4 unique color options
        Set<ColorKind> used = new HashSet<>();
        used.add(targetColor);
        List<ColorKind> options = new ArrayList<>();
        options.add(targetColor);
        while (options.size() < 4) {
            ColorKind c = randomColor();
            if (used.add(c)) options.add(c);
        }
        Collections.shuffle(options, rng);

        for (int i = 0; i < 4; i++) {
            Button b = optionButtons.get(i);
            ColorKind c = options.get(i);
            b.setGraphic(colorSwatch(64, 48, c.fx));  // swatch-only
            b.setText(null);
            optionAnswer.put(b, c);
            if (c == targetColor) correctButton = b;
        }

        answered = false;
        nextButton.setDisable(true);
    }

    @FXML private void handleOptionClick(ActionEvent e) {
        if (!running || over || answered) return;

        Button clicked = (Button) e.getSource();
        ColorKind chosen = optionAnswer.get(clicked);
        boolean ok = chosen == targetColor;

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
            running = false; over = true;
            showAndSpeakResult();
        } else {
            nextButton.setDisable(false);
        }
    }

    @FXML private void handleNext(ActionEvent e) {
        if (!running || over || !answered) return;
        qIndex++;
        if (qIndex >= TOTAL_QUESTIONS) {
            running = false; over = true;
            showAndSpeakResult();
        } else {
            loadQuestion();
            updateProgress();
            disableOptions(false);
            speakCurrent();    // speak once for the new question
        }
    }

    @FXML private void handlePlayQuestion(ActionEvent e) {
        if (running) speakCurrent();
    }

    @FXML private void BackToTest(ActionEvent event) {
        if (backButton.isDisabled()) return; // guard during exam
        ChangeFxmlController.switchScene(event, "shape-test-format.fxml");
    }

    /* ===== Voice + Result ===== */
    private void speakCurrent() { speakAsync("Select the color " + targetColor); }

    private void speakAsync(String text) {
        new Thread(() -> {
            try { voice.speak(text); } catch (Exception ignored) {}
        }, "tts-thread").start();
    }

    private void showAndSpeakResult() {
        backButton.setDisable(false);  // unlock Back

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

    /* ===== Helpers ===== */
    private void updateProgress() {
        progressLabel.setText(String.format("Q %d/%d   Score: %d", qIndex + 1, TOTAL_QUESTIONS, score));
    }

    private void disableOptions(boolean dis) { for (Button b : optionButtons) b.setDisable(dis); }

    private void resetButtons() {
        for (Button b : optionButtons) {
            b.setDisable(false);
            b.setStyle("");
            b.setGraphic(null);
            b.setText(null);
        }
    }

    private void setGreen(Button b) {
        b.setStyle("-fx-background-color:#18632b; -fx-text-fill:white; -fx-font-weight:bold;");
    }

    private void setRed(Button b) {
        b.setStyle("-fx-background-color:#8f1f1f; -fx-text-fill:white; -fx-font-weight:bold;");
    }

    private ColorKind randomColor() {
        ColorKind[] arr = ColorKind.values();
        return arr[rng.nextInt(arr.length)];
    }

    private Node colorSwatch(double w, double h, Color c) {
        Rectangle r = new Rectangle(w, h);
        r.setFill(c);
        r.setStroke(Color.BLACK);
        r.setStrokeWidth(1.0);
        StackPane box = new StackPane(r);
        box.setMinSize(w, h);
        box.setPrefSize(w, h);
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return box; // StackPane is a Node
    }

    /* ===== Voice Engine (fixed escaping) ===== */
    interface VoiceService { void speak(String text) throws Exception; }

    static class HybridVoiceService implements VoiceService {
        HybridVoiceService(boolean unused) {}

        @Override public void speak(String text) throws Exception {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                // Escape single quotes for PowerShell
                String safeText = text.replace("'", "''");
                String cmd = "$s=New-Object -ComObject SAPI.SpVoice; $s.Speak('" + safeText + "');";
                run(new String[]{"powershell", "-NoProfile", "-Command", cmd});
            } else if (os.contains("mac")) {
                run(new String[]{"say", text});
            } else {
                run(new String[]{
                        "bash","-lc",
                        "(command -v espeak-ng >/dev/null && espeak-ng '" + text + "') || " +
                                "(command -v espeak >/dev/null && espeak '" + text + "')"
                });
            }
        }

        private static void run(String[] cmd) throws Exception {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.waitFor();
        }
    }
}
