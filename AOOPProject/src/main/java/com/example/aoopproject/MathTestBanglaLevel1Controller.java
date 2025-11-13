package com.example.aoopproject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.net.URL;
import java.security.SecureRandom;
import java.util.*;

/**
 * Bangla Level 1 (ধাপ ১):
 * - Mixed render per option: Bangla digit OR Bangla word for numbers 1..100.
 * - Voice prompt plays MP3 from resources/com/example/aoopproject/Math/<digits>.mp3
 * - 15 questions, no timer.
 */
public class MathTestBanglaLevel1Controller {

    @FXML private Label titleLabel, timerLabel, progressLabel, questionLabel, feedbackLabel;
    @FXML private Button opt1, opt2, opt3, opt4, startButton, playButton, nextButton, backButton;

    private final List<Button> optionButtons = new ArrayList<>();
    private final Map<Button, Integer> valueOfButton = new HashMap<>();
    private final SecureRandom rng = new SecureRandom();

    private static final int TOTAL_QUESTIONS = 15;
    private static final int MIN_N = 1;   // MP3 set usually starts at ১
    private static final int MAX_N = 100;

    private int qIndex = 0, score = 0, answerValue = 0;
    private boolean answered = false, running = false, over = false;
    private Button correctButton = null;

    /* ===== Audio (classpath) ===== */
    private static final String AUDIO_BASE = "/com/example/aoopproject/Math";
    private MediaPlayer mediaPlayer;

    /* ===== Bangla words 1..100 (mixed options use this) ===== */
    private static final String[] BN = buildBanglaWords();

    private enum Render { BN_DIGIT, BN_WORD }

    @FXML
    public void initialize() {
        optionButtons.addAll(Arrays.asList(opt1, opt2, opt3, opt4));
        for (Button b : optionButtons) {
            b.setPrefHeight(130);
            b.setPrefWidth(360);
            b.setMaxWidth(Double.MAX_VALUE);
            b.setWrapText(true);
            b.setStyle("-fx-font-size: 20px;");
        }

        titleLabel.setText("গণিত পরীক্ষা - ধাপ ১ (বাংলা)");
        questionLabel.setText("‘শুরু’ চাপুন, অডিও শুনুন এবং সঠিক উত্তর নির্বাচন করুন।");
        feedbackLabel.setText("");
        timerLabel.setText("—");
        progressLabel.setText("প্রশ্ন 0/15   নম্বর: 0");

        disableOptions(true);
        playButton.setDisable(true);
        nextButton.setDisable(true);
        backButton.setDisable(false);
    }

    /* ===== Navigation ===== */
    @FXML
    private void BackToTest(ActionEvent event) {
        stopAudio();
        ChangeFxmlController.switchScene(event, "math-test-format.fxml");
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
        loadQuestion();
        playCurrentMp3();     // ▶️ speak once per question via MP3
        updateProgress();
    }

    private void loadQuestion() {
        resetButtons();
        feedbackLabel.setText("");

        int target = randomNumber();
        answerValue = target;

        // 4 unique numeric values
        LinkedHashSet<Integer> used = new LinkedHashSet<>();
        used.add(target);
        while (used.size() < 4) used.add(randomNumber());

        List<Integer> options = new ArrayList<>(used);
        Collections.shuffle(options, rng);

        valueOfButton.clear();
        correctButton = null;

        for (int i = 0; i < 4; i++) {
            Button b = optionButtons.get(i);
            int val = options.get(i);

            Render r = rng.nextBoolean() ? Render.BN_DIGIT : Render.BN_WORD; // mix
            b.setText(r == Render.BN_DIGIT ? toBnDigits(val) : BN[val]);

            valueOfButton.put(b, val);
            if (val == target) correctButton = b;

            b.setDisable(false);
            b.setStyle("");
        }

        answered = false;
        nextButton.setDisable(true);
        questionLabel.setText("অডিও শুনে সঠিক উত্তর বাছাই করুন (সংখ্যা/বানান)।");
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
            playCurrentMp3(); // ▶️ auto-play each new question
        }
    }

    @FXML
    private void handlePlayQuestion(ActionEvent e) {
        if (running) playCurrentMp3();    // ▶️ replay same number
    }

    /* ===== MP3 playback ===== */

    private void playCurrentMp3() {
        String digits = toBnDigits(answerValue);
        playNumberMp3(digits);
    }

    private void playNumberMp3(String banglaDigits) {
        stopAudio();

        // Try exact, then common suffixed variants (seen in your folder)
        String[] candidates = {
                banglaDigits + ".mp3",
                banglaDigits + "_1.mp3",
                banglaDigits + "_2.mp3"
        };

        for (String name : candidates) {
            String path = AUDIO_BASE + "/" + name;
            MediaPlayer mp = buildPlayerFromClasspath(path);
            if (mp != null) {
                mediaPlayer = mp;
                mediaPlayer.play();
                return;
            }
        }
        // Not found -> keep quiet; errors are logged in builder.
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

    /* ===== Helpers ===== */

    private void updateProgress() {
        progressLabel.setText(String.format("প্রশ্ন %d/%d   নম্বর: %d", qIndex + 1, TOTAL_QUESTIONS, score));
    }

    private void disableOptions(boolean dis) { for (Button b : optionButtons) b.setDisable(dis); }

    private void resetButtons() { for (Button b : optionButtons) { b.setDisable(false); b.setStyle(""); } }

    private void setGreen(Button b) { b.setStyle("-fx-background-color:#18632b; -fx-text-fill:white; -fx-font-weight:bold;"); }

    private void setRed(Button b) { b.setStyle("-fx-background-color:#8f1f1f; -fx-text-fill:white; -fx-font-weight:bold;"); }

    private int randomNumber() { return rng.nextInt(MAX_N - MIN_N + 1) + MIN_N; }

    private static String toBnDigits(int n) {
        char[] map = {'০','১','২','৩','৪','৫','৬','৭','৮','৯'};
        String s = Integer.toString(n);
        StringBuilder out = new StringBuilder();
        for (char c : s.toCharArray()) out.append(map[c - '0']);
        return out.toString();
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

    /* ===== Words 1..100 ===== */
    private static String[] buildBanglaWords() {
        String[] w = new String[101];
        w[0] = "শূন্য";
        String[] arr = {
                "এক","দুই","তিন","চার","পাঁচ","ছয়","সাত","আট","নয়","দশ",
                "এগারো","বারো","তেরো","চৌদ্দ","পনেরো","ষোলো","সতেরো","আঠারো","উনিশ","বিশ",
                "একুশ","বাইশ","তেইশ","চব্বিশ","পঁচিশ","ছাব্বিশ","সাতাশ","আঠাশ","উনত্রিশ","ত্রিশ",
                "একত্রিশ","বত্রিশ","তেত্রিশ","চৌত্রিশ","পঁয়ত্রিশ","ছত্রিশ","সাঁইত্রিশ","আটত্রিশ","উনচল্লিশ","চল্লিশ",
                "একচল্লিশ","বিয়াল্লিশ","তেতাল্লিশ","চুয়াল্লিশ","পঁয়তাল্লিশ","ছেচল্লিশ","সাতচল্লিশ","আটচল্লিশ","উনপঞ্চাশ","পঞ্চাশ",
                "একান্ন","বাহান্ন","তিপ্পান্ন","চুয়ান্ন","পঞ্চান্ন","ছাপ্পান্ন","সাতান্ন","আটান্ন","উনষাট","ষাট",
                "একষট্টি","বাষট্টি","তেষট্টি","চৌষট্টি","পঁয়ষট্টি","ছেষট্টি","সাতষট্টি","আটষট্টি","উনসত্তর","সত্তর",
                "একাত্তর","বাহাত্তর","তিয়াত্তর","চুয়াত্তর","পঁচাত্তর","ছিয়াত্তর","সাতাত্তর","আটাত্তর","ঊনআশি","আশি",
                "একাশি","বিরাশি","তিরাশি","চুরাশি","পঁচাশি","ছিয়াশি","সাতাশি","অষ্টাশি","ঊননব্বই","নব্বই",
                "একানব্বই","বিরানব্বই","তিরানব্বই","চুরানব্বই","পঁচানব্বই","ছিয়ানব্বই","সাতানব্বই","আটানব্বই","নিরানব্বই","একশো বা এক শত"
        };
        for (int i = 1; i <= 100; i++) w[i] = arr[i-1];
        return w;
    }
}
