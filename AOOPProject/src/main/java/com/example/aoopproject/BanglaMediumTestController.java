package com.example.aoopproject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.net.URL;
import java.security.SecureRandom;
import java.util.*;

public class BanglaMediumTestController {

    /* ===== UI ===== */
    @FXML private Label titleLabel, progressLabel, questionLabel, feedbackLabel, timerLabel;
    @FXML private Button opt1, opt2, opt3, opt4, nextButton;
    @FXML private Button examStartButton, playQuestionButton, backButton;

    /* ===== Letters & Audio Folders ===== */
    private static final List<String> SWAR = Arrays.asList("অ","আ","ই","ঈ","উ","ঊ","ঋ","এ","ঐ","ও","ঔ");
    private static final List<String> BYANJON = Arrays.asList(
            "ক","খ","গ","ঘ","ঙ","চ","ছ","জ","ঝ","ঞ",
            "ট","ঠ","ড","ঢ","ণ","ত","থ","দ","ধ","ন",
            "প","ফ","ব","ভ","ম","য","র","ল","শ","ষ","স","হ",
            "ড়","ঢ়","য়","ৎ","ং","ঃ","ঁ"
    );

    private static final String VOWEL_AUDIO_BASE = "/com/example/aoopproject/Shoroborno.mp3";
    private static final String CONS_AUDIO_BASE  = "/com/example/aoopproject/Benjonbornoo";

    /* ===== State ===== */
    private final SecureRandom rng = new SecureRandom();
    private final List<Button> optionButtons = new ArrayList<>(4);
    private static final int TOTAL_QUESTIONS = 15;

    private List<String> questionLetters;
    private int idx = 0, score = 0;
    private boolean answered = false, running = false, over = false;
    private String currentAnswer;
    private Button correctBtn;

    /* ===== Audio ===== */
    private MediaPlayer mediaPlayer;

    @FXML
    public void initialize() {
        optionButtons.addAll(Arrays.asList(opt1, opt2, opt3, opt4));

        titleLabel.setText("বাংলা বর্ণ পরীক্ষা — মধ্যম");
        progressLabel.setText("প্রশ্ন ০/" + bn(TOTAL_QUESTIONS) + "   নম্বর: ০");
        questionLabel.setText("👂 'শুরু করুন' চাপুন, শুনুন এবং সঠিক বর্ণ নির্বাচন করুন।");
        feedbackLabel.setText("");
        timerLabel.setText("—");

        setOptionsDisabled(true);
        nextButton.setDisable(true);
        playQuestionButton.setDisable(true);
    }

    @FXML
    private void BackToMenu(ActionEvent e) {
        stopAudio();
        ChangeFxmlController.switchScene(e, "bangla-alphabet-test-format.fxml");
    }

    /* ===== Start / Flow ===== */
    @FXML
    private void handleExamStart(ActionEvent e) {
        if (running) return;
        running = true; over = false; score = 0; idx = 0;

        backButton.setDisable(true);
        examStartButton.setDisable(true);
        playQuestionButton.setDisable(false);

        questionLetters = pickQuestions(TOTAL_QUESTIONS); // mixed pool
        loadCurrent();   // options
        playCurrent();   // play its mp3
        updateProgress();
    }

    private void loadCurrent() {
        feedbackLabel.setText("");
        String target = questionLetters.get(idx);
        currentAnswer = target;

        // 1 correct + 3 unique distractors from the combined pool
        LinkedHashSet<String> opts = new LinkedHashSet<>();
        opts.add(target);
        while (opts.size() < 4) {
            String s = pickRandomFromPool();
            if (!s.equals(target)) opts.add(s);
        }
        List<String> list = new ArrayList<>(opts);
        Collections.shuffle(list, rng);

        correctBtn = null;
        for (int i = 0; i < 4; i++) {
            Button b = optionButtons.get(i);
            String v = list.get(i);
            b.setText(v);
            b.setDisable(false);
            b.setStyle("");
            if (v.equals(target)) correctBtn = b;
        }

        answered = false;
        nextButton.setDisable(true);
        setOptionsDisabled(false);
    }

    @FXML
    private void handleOptionClick(ActionEvent e) {
        if (!running || over || answered) return;

        Button clicked = (Button) e.getSource();
        boolean ok = clicked.getText().equals(currentAnswer);

        if (ok) {
            score++;
            clicked.setStyle("-fx-background-color:#18632b; -fx-text-fill:white; -fx-font-weight:bold;");
            feedbackLabel.setStyle("-fx-text-fill:#0b5d1e; -fx-font-weight:bold;");
            feedbackLabel.setText("অভিনন্দন! 😄");
        } else {
            clicked.setStyle("-fx-background-color:#8f1f1f; -fx-text-fill:white; -fx-font-weight:bold;");
            if (correctBtn != null)
                correctBtn.setStyle("-fx-background-color:#18632b; -fx-text-fill:white; -fx-font-weight:bold;");
            feedbackLabel.setStyle("-fx-text-fill:#7a0c0c; -fx-font-weight:bold;");
            feedbackLabel.setText("ভুল হয়েছে 🙁");
        }

        setOptionsDisabled(true);
        answered = true;
        nextButton.setDisable(false);
        updateProgress();
    }

    @FXML
    private void handleNext(ActionEvent e) {
        if (!running || over || !answered) return;
        idx++;
        if (idx >= TOTAL_QUESTIONS) {
            over = true; running = false;
            backButton.setDisable(false);
            showResults();
            return;
        }
        loadCurrent();
        playCurrent();  // play the next mp3 automatically
        updateProgress();
    }

    @FXML
    private void handlePlayQuestion(ActionEvent e) {
        if (running) playCurrent(); // replay same mp3
    }

    /* ===== Audio helpers ===== */
    private void playCurrent() {
        String letter = questionLetters.get(idx);
        String base = SWAR.contains(letter) ? VOWEL_AUDIO_BASE : CONS_AUDIO_BASE;
        String res = base + "/" + letter + ".mp3";
        playFromClasspath(res);
    }

    private void playFromClasspath(String resourcePath) {
        stopAudio();
        try {
            URL url = getClass().getResource(resourcePath);
            if (url == null) {
                System.err.println("[Audio] Not found: " + resourcePath);
                return;
            }
            Media media = new Media(url.toExternalForm());
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setAutoPlay(true);
            mediaPlayer.setOnError(() -> System.err.println("[Audio] " + mediaPlayer.getError()));
        } catch (Exception ex) {
            System.err.println("[Audio] Failed to play " + resourcePath + " : " + ex.getMessage());
        }
    }

    private void stopAudio() {
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); mediaPlayer.dispose(); } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    /* ===== Pools & Progress ===== */
    private List<String> pickQuestions(int n) {
        List<String> pool = new ArrayList<>();
        pool.addAll(SWAR);
        pool.addAll(BYANJON);
        Collections.shuffle(pool, rng);
        return pool.subList(0, Math.min(n, pool.size()));
    }

    private String pickRandomFromPool() {
        // returns a random letter from the combined set
        int total = SWAR.size() + BYANJON.size();
        int k = rng.nextInt(total);
        return (k < SWAR.size()) ? SWAR.get(k) : BYANJON.get(k - SWAR.size());
        // (same as making a combined list but avoids an extra allocation per call)
    }

    private void setOptionsDisabled(boolean d) { for (Button b : optionButtons) b.setDisable(d); }

    private void updateProgress() {
        progressLabel.setText("প্রশ্ন " + bn(idx + 1) + "/" + bn(TOTAL_QUESTIONS) + "   নম্বর: " + bn(score));
    }

    private void showResults() {
        stopAudio();
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("ফলাফল");
        a.setHeaderText(String.format("আপনি %s পেয়েছেন %s এর মধ্যে", bn(score), bn(TOTAL_QUESTIONS)));
        a.setContentText(perf(score));
        a.showAndWait();
    }

    private String perf(int s) {
        if (s >= 13) return "চমৎকার! 🏆";
        if (s >= 9)  return "ভালো কাজ! 👍";
        if (s >= 5)  return "চেষ্টা ভালো হয়েছে 💪";
        return "চর্চা চালিয়ে যান 🌟";
    }

    private String bn(int n) {
        String[] d = {"০","১","২","৩","৪","৫","৬","৭","৮","৯"};
        String s = String.valueOf(n), out = "";
        for (char c : s.toCharArray()) out += d[c - '0'];
        return out;
    }
}
