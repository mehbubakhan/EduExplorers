package com.example.aoopproject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.net.URL;
import java.security.SecureRandom;
import java.util.*;

public class MathTestBanglaNumberController {

    @FXML private Label titleLabel, timerLabel, progressLabel, questionLabel, feedbackLabel;
    @FXML private Button opt1, opt2, opt3, opt4, startButton, playButton, nextButton, backButton;

    private final List<Button> optionButtons = new ArrayList<>();
    private final SecureRandom rng = new SecureRandom();
    private static final int TOTAL_QUESTIONS = 15;
    private static final int MIN_N = 0, MAX_N = 100;

    private int currentIndex = 0, score = 0, answerValue = 0;
    private boolean answered = false, running = false, over = false;

    private final Map<Button, Integer> valueOfButton = new HashMap<>();
    private Button correctButton = null;

    /* ===== Audio (classpath) ===== */
    // Files live at: resources/com/example/aoopproject/Math/<bangla-number>.mp3
    private static final String AUDIO_BASE = "/com/example/aoopproject/Math";
    private MediaPlayer mediaPlayer;

    @FXML
    public void initialize() {
        optionButtons.addAll(Arrays.asList(opt1, opt2, opt3, opt4));
        for (Button b : optionButtons) {
            b.setPrefHeight(130);
            b.setPrefWidth(360);
            b.setMaxWidth(Double.MAX_VALUE);
            b.setStyle("-fx-font-size: 20px;");
        }

        titleLabel.setText("গণিত পরীক্ষা - সংখ্যা");
        questionLabel.setText("‘শুরু’ চাপুন, ভয়েস শুনুন এবং সঠিক উত্তর নির্বাচন করুন।");
        feedbackLabel.setText("");
        timerLabel.setText("—");
        progressLabel.setText("প্রশ্ন 0/15   নম্বর: 0");

        disableOptions(true);
        playButton.setDisable(true);
        nextButton.setDisable(true);
        backButton.setDisable(false);
    }

    /* ===== Exam Flow ===== */
    @FXML
    private void handleExamStart(ActionEvent e) {
        if (running) return;
        running = true;
        over = false;
        score = 0;
        currentIndex = 0;

        startButton.setDisable(true);
        playButton.setDisable(false);
        backButton.setDisable(true);

        disableOptions(false);
        loadQuestion();
        playCurrentNumber();      // ▶️ auto-play first question
        updateProgress();
    }

    private void loadQuestion() {
        resetButtons();
        feedbackLabel.setText("");

        int target = randomNumber();
        answerValue = target;

        // unique options
        Set<Integer> used = new LinkedHashSet<>();
        used.add(target);
        while (used.size() < 4) used.add(randomNumber());

        List<Integer> options = new ArrayList<>(used);
        Collections.shuffle(options, rng);

        valueOfButton.clear();
        for (int i = 0; i < 4; i++) {
            Button b = optionButtons.get(i);
            int val = options.get(i);
            b.setText(toBnDigits(val));
            valueOfButton.put(b, val);
            if (val == target) correctButton = b;
        }

        answered = false;
        nextButton.setDisable(true);
    }

    @FXML
    private void handleOptionClick(ActionEvent e) {
        if (!running || over || answered) return;
        Button clicked = (Button) e.getSource();
        int chosen = valueOfButton.getOrDefault(clicked, -1);
        boolean ok = chosen == answerValue;

        if (ok) {
            score++;
            setGreen(clicked);
            feedbackLabel.setStyle("-fx-text-fill:#0b5d1e;-fx-font-weight:bold;");
            feedbackLabel.setText("অভিনন্দন! 😄");
        } else {
            setRed(clicked);
            if (correctButton != null) setGreen(correctButton);
            feedbackLabel.setStyle("-fx-text-fill:#7a0c0c;-fx-font-weight:bold;");
            feedbackLabel.setText("ভুল হয়েছে 🙁");
        }

        answered = true;
        disableOptions(true);
        nextButton.setDisable(false);
        updateProgress();

        if (currentIndex >= TOTAL_QUESTIONS - 1) {
            running = false;
            over = true;
            showResult();
        }
    }

    @FXML
    private void handleNext(ActionEvent e) {
        if (!running || over || !answered) return;
        currentIndex++;
        if (currentIndex >= TOTAL_QUESTIONS) {
            running = false;
            over = true;
            showResult();
        } else {
            loadQuestion();
            updateProgress();
            disableOptions(false);
            playCurrentNumber();   // ▶️ auto-play each new question
        }
    }

    @FXML
    private void handlePlayQuestion(ActionEvent e) {
        if (running) playCurrentNumber();  // ▶️ replay current number
    }

    @FXML
    private void BackToTest(ActionEvent event) {
        if (backButton.isDisabled()) return;
        stopAudio();
        ChangeFxmlController.switchScene(event, "math-test-format.fxml");
    }

    /* ===== Helpers ===== */

    private void updateProgress() {
        progressLabel.setText(String.format("প্রশ্ন %d/%d   নম্বর: %d",
                currentIndex + 1, TOTAL_QUESTIONS, score));
    }

    private void showResult() {
        backButton.setDisable(false);
        stopAudio();
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("ফলাফল");
        a.setHeaderText(String.format("আপনি %d পেয়েছেন %d এর মধ্যে", score, TOTAL_QUESTIONS));
        a.setContentText(score >= 13 ? "চমৎকার! 🏆" :
                score >= 9 ? "ভালো কাজ! 👍" :
                        score >= 5 ? "চেষ্টা ভালো হয়েছে 💪" : "চর্চা চালিয়ে যান 🌟");
        a.show();
    }

    private void disableOptions(boolean dis) {
        for (Button b : optionButtons) b.setDisable(dis);
    }

    private void resetButtons() {
        for (Button b : optionButtons) {
            b.setDisable(false);
            b.setStyle("");
        }
    }

    private void setGreen(Button b) {
        b.setStyle("-fx-background-color:#18632b; -fx-text-fill:white; -fx-font-weight:bold;");
    }

    private void setRed(Button b) {
        b.setStyle("-fx-background-color:#8f1f1f; -fx-text-fill:white; -fx-font-weight:bold;");
    }

    private int randomNumber() {
        return rng.nextInt(MAX_N - MIN_N + 1) + MIN_N;
    }

    private String toBnDigits(int n) {
        String[] d = {"০","১","২","৩","৪","৫","৬","৭","৮","৯"};
        String s = String.valueOf(n);
        StringBuilder out = new StringBuilder();
        for (char c : s.toCharArray()) out.append(d[c - '0']);
        return out.toString();
    }

    /* ===== Audio helpers ===== */

    private void playCurrentNumber() {
        String bangla = toBnDigits(answerValue);
        playNumberMp3(bangla);
    }

    private void playNumberMp3(String banglaDigits) {
        stopAudio();

        // Try exact then common suffixed variants you have in your Math folder
        String[] candidates = {
                banglaDigits + ".mp3",
                banglaDigits + "_1.mp3",
                banglaDigits + "_2.mp3"
        };

        for (String name : candidates) {
            String resourcePath = AUDIO_BASE + "/" + name;
            MediaPlayer mp = buildPlayerFromClasspath(resourcePath);
            if (mp != null) {
                mediaPlayer = mp;
                mediaPlayer.play();
                return;
            }
        }
        // If none found, fail silently (message already printed in builder)
    }

    private MediaPlayer buildPlayerFromClasspath(String resourcePath) {
        try {
            URL url = getClass().getResource(resourcePath);
            if (url == null) {
                System.err.println("[Audio] Not found: " + resourcePath);
                return null;
            }
            Media media = new Media(url.toExternalForm());
            MediaPlayer player = new MediaPlayer(media);
            player.setAutoPlay(false);
            player.setOnError(() -> System.err.println("[Audio] " + player.getError()));
            return player;
        } catch (Exception ex) {
            System.err.println("[Audio] Failed to play " + resourcePath + " : " + ex.getMessage());
            return null;
        }
    }

    private void stopAudio() {
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); mediaPlayer.dispose(); } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }
}
