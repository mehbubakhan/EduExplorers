package com.example.aoopproject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.util.*;

public class EnglishTestLowercaseController {

    /* ===== FXML ===== */
    @FXML private Label titleLabel, progressLabel, questionLabel, feedbackLabel, timerLabel;
    @FXML private Button opt1, opt2, opt3, opt4, nextButton, startButton, playButton, backButton;

    /* ===== State ===== */
    private final List<Button> optionButtons = new ArrayList<>();
    private final Random rng = new Random();

    private static final int TOTAL_QUESTIONS = 15;

    private List<Character> questions;   // store targets in UPPERCASE A..Z
    private int qIndex = 0, score = 0;
    private boolean answered = false, running = false, over = false;

    private char target;                 // correct base (uppercase)
    private Button correctButton = null;

    /* ===== Voice ===== */
    private final VoiceService voice = new HybridVoiceService(false);

    @FXML
    public void initialize() {
        optionButtons.addAll(Arrays.asList(opt1, opt2, opt3, opt4));

        titleLabel.setText("Alphabet Quiz — lowercase");
        progressLabel.setText("Q 0/15   Score: 0");
        questionLabel.setText("👂 Press Start, listen, and select the correct letter (____).");
        feedbackLabel.setText("");
        timerLabel.setText("—");

        disableOptions(true);
        nextButton.setDisable(true);
        playButton.setDisable(true);
        backButton.setDisable(false);

        // Prepare question set in preview mode
        startNew(true);
    }

    /* ===== Navigation ===== */
    @FXML
    private void switchToEnglishTestLowercase(ActionEvent event) {
        ChangeFxmlController.switchScene(event, "english-test-lowercase.fxml");
    }

    @FXML
    private void BackToTest(ActionEvent event) {
        if (backButton.isDisabled()) return; // locked during exam
        ChangeFxmlController.switchScene(event, "english-test-format.fxml");
    }

    /* ===== Exam Flow ===== */
    @FXML
    private void handleExamStart(ActionEvent e) {
        if (running) return;
        running = true; over = false; score = 0; qIndex = 0;

        backButton.setDisable(true);  // lock Back while exam runs
        startButton.setDisable(true);
        playButton.setDisable(false);
        disableOptions(false);
        nextButton.setDisable(true);

        startNew(false);   // new run + load first
        speakCurrent();    // speak once for first question
        updateProgress();
    }

    @FXML
    private void handlePlayQuestion(ActionEvent e) {
        if (running) speakCurrent();
    }

    @FXML
    private void handleOptionClick(ActionEvent e) {
        if (!running || over || answered) return;
        Button clicked = (Button) e.getSource();

        // Buttons show lowercase; compare case-insensitively to uppercase target
        char chosenBase = Character.toUpperCase(clicked.getText().charAt(0));
        boolean ok = (chosenBase == target);

        if (ok) {
            score++;
            setGreen(clicked);
            feedbackLabel.setStyle("-fx-text-fill:#0b5d1e; -fx-font-weight:bold;");
            feedbackLabel.setText("Correct! 😄");     // changed from "Congratulations!"
            speakAsync("Correct!");
        } else {
            setRed(clicked);
            if (correctButton != null) setGreen(correctButton);
            feedbackLabel.setStyle("-fx-text-fill:#7a0c0c; -fx-font-weight:bold;");
            feedbackLabel.setText("Sorry, you are wrong. 🙁");
            speakAsync("Sorry, wrong answer.");
        }

        answered = true;
        disableOptions(true);
        updateProgress();

        if (qIndex >= TOTAL_QUESTIONS - 1) {
            running = false; over = true;
            showResult();
        } else {
            nextButton.setDisable(false);
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
            loadQuestion();   // load next
            updateProgress();
            disableOptions(false);
            speakCurrent();   // speak once per new question
        }
    }

    /* ===== Core ===== */
    private void startNew(boolean previewOnly) {
        score = 0; qIndex = 0; answered = false; over = false;
        questions = pickLetters(TOTAL_QUESTIONS);
        loadQuestion();
        updateProgress();
        disableOptions(previewOnly);
        nextButton.setDisable(true);
        feedbackLabel.setText("");
    }

    private void loadQuestion() {
        feedbackLabel.setText("");
        correctButton = null;

        target = questions.get(qIndex); // uppercase A..Z

        // Do NOT reveal the letter in the prompt line
        questionLabel.setText("Select the alphabet (____)");

        // Build unique 4 options (render as lowercase)
        Set<Character> opts = new LinkedHashSet<>();
        opts.add(target);
        while (opts.size() < 4) {
            char c = (char) ('A' + rng.nextInt(26));
            opts.add(c);
        }
        List<Character> list = new ArrayList<>(opts);
        Collections.shuffle(list, rng);

        for (int i = 0; i < 4; i++) {
            Button b = optionButtons.get(i);
            char v = list.get(i);
            b.setText(String.valueOf(Character.toLowerCase(v))); // lowercase option text
            b.setDisable(false);
            b.setStyle("");
            if (v == target) correctButton = b;
        }

        answered = false;
        nextButton.setDisable(true);
    }

    private List<Character> pickLetters(int n) {
        List<Character> letters = new ArrayList<>();
        for (char c = 'A'; c <= 'Z'; c++) letters.add(c);
        Collections.shuffle(letters, rng);
        return letters.subList(0, Math.min(n, letters.size()));
    }

    private void updateProgress() {
        progressLabel.setText(String.format("Q %d/%d   Score: %d", qIndex + 1, TOTAL_QUESTIONS, score));
    }

    private void showResult() {
        backButton.setDisable(false); // unlock
        startButton.setDisable(false);
        playButton.setDisable(true);
        disableOptions(true);
        nextButton.setDisable(true);

        String message;
        if (score >= 13) message = "Excellent!";
        else if (score >= 9) message = "Great job!";
        else if (score >= 5) message = "Good try!";
        else message = "Keep practicing!";

        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Your Result");
        a.setHeaderText(String.format("You scored %d out of %d", score, TOTAL_QUESTIONS));
        a.setContentText(message + " 🌟");
        a.show();

        // Speak both the score and the motivational message
        speakAsync("You scored " + score + " out of " + TOTAL_QUESTIONS + ". " + message);
    }

    /* ===== Voice ===== */
    private void speakCurrent() {
        // Voice still says the name of the letter even though the prompt hides it
        speakAsync("Select the alphabet " + spokenForm(target));
    }

    private String spokenForm(char c) {
        return switch (c) {
            case 'H' -> "aitch";
            case 'W' -> "double u";
            default  -> String.valueOf(c);
        };
    }

    private void speakAsync(String text) {
        new Thread(() -> {
            try { voice.speak(text); } catch (Exception ignored) {}
        }, "tts-lowercase").start();
    }

    /* ===== UI helpers ===== */
    private void disableOptions(boolean dis) { for (Button b : optionButtons) b.setDisable(dis); }
    private void setGreen(Button b){ b.setStyle("-fx-background-color:#18632b; -fx-text-fill:white; -fx-font-weight:bold;"); }
    private void setRed(Button b){ b.setStyle("-fx-background-color:#8f1f1f; -fx-text-fill:white; -fx-font-weight:bold;"); }

    /* ===== Voice engine ===== */
    interface VoiceService { void speak(String text) throws Exception; }
    static class HybridVoiceService implements VoiceService {
        HybridVoiceService(boolean unused) {}
        @Override public void speak(String text) throws Exception {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("mac")) {
                run(new String[]{"say", text});
            } else if (os.contains("win")) {
                run(new String[]{"powershell","-NoProfile","-Command",
                        "$s=New-Object -ComObject SAPI.SpVoice; $s.Speak('" + text.replace("'", "''") + "');"});
            } else {
                run(new String[]{"bash","-lc",
                        "(command -v espeak-ng >/dev/null && espeak-ng '" + text + "') || " +
                                "(command -v espeak >/dev/null && espeak '" + text + "')" });
            }
        }
        private static void run(String[] cmd) throws Exception {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.waitFor();
        }
    }
}
