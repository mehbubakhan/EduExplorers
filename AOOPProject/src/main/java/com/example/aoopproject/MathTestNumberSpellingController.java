package com.example.aoopproject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.security.SecureRandom;
import java.util.*;

/**
 * English Number Spelling quiz (0..100):
 * - Voice says a number (e.g., "Select the number 57")
 * - Options show English words only (e.g., "Fifty-seven")
 */
public class MathTestNumberSpellingController {

    @FXML private Label titleLabel, timerLabel, progressLabel, questionLabel, feedbackLabel;
    @FXML private Button opt1, opt2, opt3, opt4, startButton, playButton, nextButton, backButton;

    private final List<Button> optionButtons = new ArrayList<>();
    private final Map<Button, Integer> valueOfButton = new HashMap<>();
    private final SecureRandom rng = new SecureRandom();

    private static final int TOTAL_QUESTIONS = 15;
    private static final int MIN_N = 0, MAX_N = 100;

    private int qIndex = 0, score = 0, answerValue = 0;
    private boolean answered = false, running = false, over = false;
    private Button correctButton = null;

    private static final String[] EN = buildEnglishWords(); // 0..100
    private final VoiceService voice = new HybridVoiceService(false);

    @FXML
    public void initialize() {
        optionButtons.addAll(Arrays.asList(opt1, opt2, opt3, opt4));
        for (Button b : optionButtons) {
            b.setPrefHeight(130);
            b.setPrefWidth(360);
            b.setMaxWidth(Double.MAX_VALUE);
            b.setStyle("-fx-font-size: 20px;");
            b.setWrapText(true);
        }

        titleLabel.setText("Math Test — Number Spelling");
        questionLabel.setText("Press Start, listen to the voice, and select the correct English word.");
        feedbackLabel.setText("");
        timerLabel.setText("—");
        progressLabel.setText("Q 0/15   Score: 0");

        disableOptions(true);
        playButton.setDisable(true);
        nextButton.setDisable(true);
        backButton.setDisable(false);
    }

    /* ===== Exam Flow ===== */
    @FXML
    private void handleExamStart(ActionEvent e) {
        if (running) return;
        running = true; over = false; score = 0; qIndex = 0;

        startButton.setDisable(true);
        playButton.setDisable(false);
        backButton.setDisable(true);

        disableOptions(false);
        loadQuestion();   // don't speak inside
        speakCurrent();   // speak once for this question
        updateProgress();
    }

    private void loadQuestion() {
        resetButtons();
        feedbackLabel.setText("");

        int target = randomNumber();
        answerValue = target;

        // 4 unique values
        Set<Integer> used = new LinkedHashSet<>();
        used.add(target);
        while (used.size() < 4) used.add(randomNumber());
        List<Integer> options = new ArrayList<>(used);
        Collections.shuffle(options, rng);

        valueOfButton.clear();
        correctButton = null;

        for (int i = 0; i < 4; i++) {
            Button b = optionButtons.get(i);
            int val = options.get(i);
            b.setText(capitalize(EN[val])); // show English word
            valueOfButton.put(b, val);
            if (val == target) correctButton = b;

            b.setDisable(false);
            b.setStyle("");
        }

        answered = false;
        nextButton.setDisable(true);
        questionLabel.setText("Listen and choose the correct English word.");
    }

    @FXML
    private void handleOptionClick(ActionEvent e) {
        if (!running || over || answered) return;
        Button clicked = (Button) e.getSource();
        int chosen = valueOfButton.getOrDefault(clicked, Integer.MIN_VALUE);
        boolean ok = chosen == answerValue;

        if (ok) {
            score++;
            setGreen(clicked);
            feedbackLabel.setStyle("-fx-text-fill:#0b5d1e;-fx-font-weight:bold;");
            feedbackLabel.setText("Correct! 😄");
            speakAsync("Correct");
        } else {
            setRed(clicked);
            if (correctButton != null) setGreen(correctButton);
            feedbackLabel.setStyle("-fx-text-fill:#7a0c0c;-fx-font-weight:bold;");
            feedbackLabel.setText("Sorry, wrong answer. 🙁");
            speakAsync("Sorry, wrong answer");
        }

        answered = true;
        disableOptions(true);
        nextButton.setDisable(false);
        updateProgress();

        if (qIndex >= TOTAL_QUESTIONS - 1) {
            running = false; over = true;
            showResult();
        }
    }

    @FXML
    private void handleNext(ActionEvent e) {
        if (!running || over || !answered) return;
        qIndex++;
        if (qIndex >= TOTAL_QUESTIONS) {
            running = false; over = true;
            showResult();
        } else {
            loadQuestion();
            updateProgress();
            disableOptions(false);
            speakCurrent();
        }
    }

    @FXML
    private void handlePlayQuestion(ActionEvent e) {
        if (running) speakCurrent();
    }

    @FXML
    private void BackToTest(ActionEvent event) {
        if (backButton.isDisabled()) return;
        ChangeFxmlController.switchScene(event, "math-test-format.fxml");
    }

    /* ===== Voice & Result ===== */
    private void speakCurrent() { speakAsync("Select the number " + answerValue); }

    private void showResult() {
        backButton.setDisable(false);
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Your Result");
        a.setHeaderText(String.format("You scored %d out of %d", score, TOTAL_QUESTIONS));
        a.setContentText(score >= 13 ? "Excellent! 🏆"
                : score >= 9 ? "Great job! 👍"
                : score >= 5 ? "Good try! 💪"
                : "Keep practicing! 🌟");
        a.show();
    }

    private void speakAsync(String text) {
        new Thread(() -> {
            try { voice.speak(text); } catch (Exception ignored) {}
        }, "tts-en-number-spelling").start();
    }

    /* ===== Helpers ===== */
    private void updateProgress() {
        progressLabel.setText(String.format("Q %d/%d   Score: %d", qIndex + 1, TOTAL_QUESTIONS, score));
    }

    private void disableOptions(boolean dis) { for (Button b : optionButtons) b.setDisable(dis); }

    private void resetButtons() { for (Button b : optionButtons) { b.setDisable(false); b.setStyle(""); } }

    private void setGreen(Button b) { b.setStyle("-fx-background-color:#18632b; -fx-text-fill:white; -fx-font-weight:bold;"); }

    private void setRed(Button b) { b.setStyle("-fx-background-color:#8f1f1f; -fx-text-fill:white; -fx-font-weight:bold;"); }

    private int randomNumber() { return rng.nextInt(MAX_N - MIN_N + 1) + MIN_N; }

    private static String capitalize(String w) {
        return (w == null || w.isEmpty()) ? w : w.substring(0,1).toUpperCase() + w.substring(1);
    }

    /* ===== English words 0..100 ===== */
    private static String[] buildEnglishWords() {
        String[] u={"zero","one","two","three","four","five","six","seven","eight","nine"};
        String[] t={"ten","eleven","twelve","thirteen","fourteen","fifteen","sixteen","seventeen","eighteen","nineteen"};
        String[] tens={"","","twenty","thirty","forty","fifty","sixty","seventy","eighty","ninety"};
        String[] out=new String[101]; out[0]="zero";
        for(int i=1;i<10;i++) out[i]=u[i];
        for(int i=10;i<20;i++) out[i]=t[i-10];
        out[20]="twenty"; out[30]="thirty"; out[40]="forty"; out[50]="fifty";
        out[60]="sixty"; out[70]="seventy"; out[80]="eighty"; out[90]="ninety";
        for(int k=20;k<=90;k+=10) for(int j=1;j<=9;j++) out[k+j]=out[k]+"-"+u[j];
        out[100]="one hundred"; return out;
    }

    /* ===== Voice engine ===== */
    interface VoiceService { void speak(String text) throws Exception; }
    static class HybridVoiceService implements VoiceService {
        HybridVoiceService(boolean unused) {}
        @Override public void speak(String text) throws Exception {
            String os = System.getProperty("os.name","").toLowerCase();
            if (os.contains("win")) {
                String safe = text.replace("'", "''");
                String cmd = "$s=New-Object -ComObject SAPI.SpVoice; $s.Speak('" + safe + "');";
                run(new String[]{"powershell","-NoProfile","-Command", cmd});
            } else if (os.contains("mac")) {
                run(new String[]{"say", text});
            } else {
                run(new String[]{"bash","-lc",
                        "(command -v espeak-ng >/dev/null && espeak-ng '" + text + "') || " +
                                "(command -v espeak >/dev/null && espeak '" + text + "')"});
            }
        }
        private static void run(String[] cmd) throws Exception {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.waitFor();
        }
    }
}
