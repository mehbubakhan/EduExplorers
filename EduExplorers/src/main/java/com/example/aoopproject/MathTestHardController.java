package com.example.aoopproject;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.net.URL;
import java.security.SecureRandom;
import java.util.*;

public class MathTestHardController {

    @FXML private Label titleLabel, timerLabel, progressLabel, questionLabel, feedbackLabel;
    @FXML private Button opt1, opt2, opt3, opt4, startButton, playButton, nextButton, retryButton, backButton;

    private final List<Button> optionButtons = new ArrayList<>();
    private final Map<Button, Integer> valueOfButton = new HashMap<>();
    private final SecureRandom rng = new SecureRandom();

    private static final int TOTAL_QUESTIONS = 15;
    private static final int MIN_N = 0, MAX_N = 100;

    private int qIndex = 0, score = 0, answerValue = 0;
    private boolean answered = false, running = false, over = false;
    private Button correctButton = null;

    // ===== 60s Global Timer =====
    private Timeline timer;
    private int secondsLeft = 0;

    // ===== Words =====
    private static final String[] EN = buildEnglishWords(); // 0..100
    private static final String[] BN = buildBanglaWords();  // 0..100

    // ===== Audio (mp3 in /Math) =====
    private static final String AUDIO_BASE = "/com/example/aoopproject/Math";
    private MediaPlayer mediaPlayer;

    // short feedback via system TTS
    private final VoiceService voice = new HybridVoiceService(false);

    private enum Render { BN_DIGIT, BN_WORD, EN_DIGIT, EN_WORD }
    private enum PromptMode { EN_DIGIT, EN_WORD, BN_DIGIT, BN_WORD }
    private PromptMode currentPromptMode = PromptMode.EN_DIGIT;

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

        titleLabel.setText("Math Test — Hard (Mixed + 60s)");
        questionLabel.setText("🔊 Listen...");
        feedbackLabel.setText("");
        timerLabel.setText("1:00");
        progressLabel.setText("Q 0/15   Score: 0");

        disableOptions(true);
        playButton.setDisable(true);
        nextButton.setDisable(true);
        retryButton.setDisable(true);
        backButton.setDisable(false);
    }

    // ===== Exam lifecycle =====
    @FXML private void handleExamStart(ActionEvent e) { startExam(); }
    @FXML private void handleRetry(ActionEvent e)     { startExam(); }

    private void startExam() {
        if (running) return;
        running = true; over = false;
        score = 0; qIndex = 0;

        backButton.setDisable(true);
        retryButton.setDisable(true);
        startButton.setDisable(true);
        playButton.setDisable(false);
        disableOptions(false);
        nextButton.setDisable(true);

        loadQuestion();     // avoid double audio
        startTimer(60);     // 60-second global timer
        playPrompt();       // play TTS/MP3; label stays "🔊 Listen..."
        updateProgress();
        feedbackLabel.setText("");
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
        updateProgress();

        if (qIndex >= TOTAL_QUESTIONS - 1) {
            finishExam(false);
        } else {
            nextButton.setDisable(false);
        }
    }

    @FXML
    private void handleNext(ActionEvent e) {
        if (!running || over || !answered) return;
        qIndex++;
        if (qIndex >= TOTAL_QUESTIONS) {
            finishExam(false);
        } else {
            loadQuestion();
            updateProgress();
            disableOptions(false);
            playPrompt();          // replay new prompt
        }
    }

    @FXML
    private void handlePlayQuestion(ActionEvent e) {
        if (running) playPrompt();  // replay current prompt (TTS or MP3)
    }

    @FXML
    private void BackToTest(ActionEvent event) {
        if (backButton.isDisabled()) return; // lock while running
        stopTimer();
        stopAudio();
        ChangeFxmlController.switchScene(event, "math-test-format.fxml");
    }

    // ===== Timer =====
    private void startTimer(int seconds) {
        stopTimer();
        secondsLeft = seconds;
        timerLabel.setText(formatTime(secondsLeft));
        timer = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
            secondsLeft--;
            timerLabel.setText(formatTime(secondsLeft));
            if (secondsLeft <= 0) finishExam(true);
        }));
        timer.setCycleCount(seconds);
        timer.playFromStart();
    }

    private void stopTimer() {
        if (timer != null) { timer.stop(); timer = null; }
    }

    private String formatTime(int s) {
        int m = s / 60, r = s % 60;
        return String.format("%d:%02d", m, r);
    }

    private void finishExam(boolean timeout) {
        if (over) return;
        stopTimer();
        stopAudio();
        running = false; over = true;

        disableOptions(true);
        nextButton.setDisable(true);

        backButton.setDisable(false);
        retryButton.setDisable(false);
        startButton.setDisable(false);
        playButton.setDisable(true);

        if (timeout) {
            feedbackLabel.setStyle("-fx-text-fill:#7a0c0c;-fx-font-weight:bold;");
            feedbackLabel.setText(String.format("⏰ Time out — You reached Q %d/%d", qIndex + 1, TOTAL_QUESTIONS));
        }

        showResult(timeout);
    }

    // ===== Core question rendering =====
    private void loadQuestion() {
        resetButtons();
        feedbackLabel.setText("");

        int target = randomNumber();
        answerValue = target;

        // choose a random prompt mode for this question (audio only)
        currentPromptMode = randomPromptMode();

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

            // Force the correct option to match the prompt’s representation
            Render r = (val == answerValue) ? renderForPrompt(currentPromptMode) : randomRender();

            b.setText(renderValue(val, r));
            valueOfButton.put(b, val);
            if (val == target) correctButton = b;

            b.setDisable(false);
            b.setStyle("-fx-font-size: 20px;");
        }

        answered = false;
        nextButton.setDisable(true);

        // Always show only “Listen…”
        questionLabel.setText("🔊 Listen...");
    }

    // Map prompt mode -> the same render for the correct option
    private Render renderForPrompt(PromptMode pm) {
        return switch (pm) {
            case EN_DIGIT -> Render.EN_DIGIT;
            case EN_WORD  -> Render.EN_WORD;
            case BN_DIGIT -> Render.BN_DIGIT;
            case BN_WORD  -> Render.BN_WORD;
        };
    }

    // ===== Prompt playback (randomized language/format) =====
    private void playPrompt() {
        questionLabel.setText("🔊 Listen..."); // never show text like “নির্বাচন করুন …”

        if (currentPromptMode == PromptMode.EN_DIGIT) {
            speakAsync("Select " + answerValue);
        } else if (currentPromptMode == PromptMode.EN_WORD) {
            speakAsync("Select " + EN[answerValue]);
        } else {
            // BN modes -> play MP3 using Bangla digits filename
            String bnDigits = toBnDigits(answerValue);
            playNumberMp3(bnDigits);
        }
    }

    // ===== Audio (Bangla numeral mp3 from /Math) =====
    private void playNumberMp3(String bnDigits) {
        stopAudio();
        playButton.setDisable(true); // debounce while playing
        String[] candidates = { bnDigits + ".mp3", bnDigits + "_1.mp3", bnDigits + "_2.mp3" };
        for (String file : candidates) {
            String path = AUDIO_BASE + "/" + file;
            if (tryPlay(path)) {
                mediaPlayer.setOnEndOfMedia(() -> {
                    try { mediaPlayer.dispose(); } catch (Exception ignored) {}
                    mediaPlayer = null;
                    playButton.setDisable(false);
                });
                mediaPlayer.setOnError(() -> {
                    System.err.println("[Audio] " + mediaPlayer.getError());
                    playButton.setDisable(false);
                });
                return;
            }
        }
        // silent if not found
        playButton.setDisable(false);
    }

    private boolean tryPlay(String resourcePath) {
        try {
            URL url = getClass().getResource(resourcePath);
            if (url == null) return false;
            Media media = new Media(url.toExternalForm());
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setAutoPlay(true);
            mediaPlayer.setOnError(() -> System.err.println("[Audio] " + mediaPlayer.getError()));
            return true;
        } catch (Exception ex) {
            System.err.println("[Audio] Failed: " + resourcePath + " -> " + ex.getMessage());
            return false;
        }
    }

    private void stopAudio() {
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); mediaPlayer.dispose(); } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    // ===== Result & small TTS =====
    private void showResult(boolean timeout) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Your Result");
        String header = timeout
                ? String.format("Time out — You reached Q %d/%d", qIndex + 1, TOTAL_QUESTIONS)
                : String.format("You scored %d out of %d", score, TOTAL_QUESTIONS);
        a.setHeaderText(header);
        a.setContentText(score >= 13 ? "Excellent! 🏆"
                : score >= 9 ? "Great job! 👍"
                : score >= 5 ? "Good try! 💪"
                : "Keep practicing! 🌟");
        final String speech = timeout
                ? String.format("Time out. You scored %d out of %d.", score, TOTAL_QUESTIONS)
                : String.format("You scored %d out of %d.", score, TOTAL_QUESTIONS);
        a.setOnShown(ev -> speakAsync(speech));
        a.show();
    }

    private void speakAsync(String text) {
        new Thread(() -> {
            try { voice.speak(text); } catch (Exception ignored) {}
        }, "tts-hard").start();
    }

    // ===== Helpers =====
    private void updateProgress() {
        progressLabel.setText(String.format("Q %d/%d   Score: %d", qIndex + 1, TOTAL_QUESTIONS, score));
    }
    private void disableOptions(boolean dis) { for (Button b : optionButtons) b.setDisable(dis); }
    private void resetButtons() { for (Button b : optionButtons) { b.setDisable(false); b.setStyle(""); } }
    private void setGreen(Button b) { b.setStyle("-fx-background-color:#18632b; -fx-text-fill:white; -fx-font-weight:bold;"); }
    private void setRed(Button b) { b.setStyle("-fx-background-color:#8f1f1f; -fx-text-fill:white; -fx-font-weight:bold;"); }
    private int randomNumber() { return rng.nextInt(MAX_N - MIN_N + 1) + MIN_N; }

    private PromptMode randomPromptMode() {
        return switch (rng.nextInt(4)) {
            case 0 -> PromptMode.EN_DIGIT;
            case 1 -> PromptMode.EN_WORD;
            case 2 -> PromptMode.BN_DIGIT;
            default -> PromptMode.BN_WORD;
        };
    }

    private Render randomRender() {
        return switch (rng.nextInt(4)) {
            case 0 -> Render.BN_DIGIT;
            case 1 -> Render.BN_WORD;
            case 2 -> Render.EN_DIGIT;
            default -> Render.EN_WORD;
        };
    }

    private String renderValue(int n, Render r) {
        return switch (r) {
            case BN_DIGIT -> toBnDigits(n);
            case BN_WORD  -> BN[n];
            case EN_DIGIT -> String.valueOf(n);
            case EN_WORD  -> capitalize(EN[n]);
        };
    }

    private static String capitalize(String w) {
        return (w == null || w.isEmpty()) ? w : w.substring(0,1).toUpperCase() + w.substring(1);
    }

    private static String toBnDigits(int n) {
        String[] d = {"০","১","২","৩","৪","৫","৬","৭","৮","৯"};
        String s = String.valueOf(n);
        StringBuilder out = new StringBuilder();
        for (char c : s.toCharArray()) out.append(d[c - '0']);
        return out.toString();
    }

    // ===== English words 0..100 =====
    private static String[] buildEnglishWords() {
        String[] u={"zero","one","two","three","four","five","six","seven","eight","nine"};
        String[] t={"ten","eleven","twelve","thirteen","fourteen","fifteen","sixteen","seventeen","eighteen","nineteen"};
        String[] out=new String[101]; out[0]="zero";
        for(int i=1;i<10;i++) out[i]=u[i];
        for(int i=10;i<20;i++) out[i]=t[i-10];
        out[20]="twenty"; out[30]="thirty"; out[40]="forty"; out[50]="fifty";
        out[60]="sixty"; out[70]="seventy"; out[80]="eighty"; out[90]="ninety";
        for(int k=20;k<=90;k+=10) for(int j=1;j<=9;j++) out[k+j]=out[k]+"-"+u[j];
        out[100]="one hundred"; return out;
    }

    // ===== Bangla words 0..100 =====
    private static String[] buildBanglaWords() {
        String[] b=new String[101];
        b[0]="শূন্য"; b[1]="এক"; b[2]="দুই"; b[3]="তিন"; b[4]="চার"; b[5]="পাঁচ";
        b[6]="ছয়"; b[7]="সাত"; b[8]="আট"; b[9]="নয়"; b[10]="দশ";
        b[11]="এগারো"; b[12]="বারো"; b[13]="তেরো"; b[14]="চৌদ্দ"; b[15]="পনেরো";
        b[16]="ষোলো"; b[17]="সতেরো"; b[18]="আঠারো"; b[19]="উনিশ"; b[20]="বিশ";
        b[21]="একুশ"; b[22]="বাইশ"; b[23]="তেইশ"; b[24]="চব্বিশ"; b[25]="পঁচিশ";
        b[26]="ছাব্বিশ"; b[27]="সাতাশ"; b[28]="আটাশ"; b[29]="উনত্রিশ"; b[30]="ত্রিশ";
        b[31]="একত্রিশ"; b[32]="বত্রিশ"; b[33]="তেত্রিশ"; b[34]="চৌত্রিশ"; b[35]="পঁয়ত্রিশ";
        b[36]="ছত্রিশ"; b[37]="সাঁইত্রিশ"; b[38]="আটত্রিশ"; b[39]="উনচল্লিশ"; b[40]="চল্লিশ";
        b[41]="একচল্লিশ"; b[42]="বিয়াল্লিশ"; b[43]="তেতাল্লিশ"; b[44]="চুয়াল্লিশ"; b[45]="পঁয়তাল্লিশ";
        b[46]="ছেচল্লিশ"; b[47]="সাতচল্লিশ"; b[48]="আটচল্লিশ"; b[49]="উনপঞ্চাশ"; b[50]="পঞ্চাশ";
        b[51]="একান্ন"; b[52]="বাহান্ন"; b[53]="তেপ্পান্ন"; b[54]="চুয়ান্ন"; b[55]="পঞ্চান্ন";
        b[56]="ছাপ্পান্ন"; b[57]="সাতান্ন"; b[58]="আটান্ন"; b[59]="উনষাট"; b[60]="ষাট";
        b[61]="একষট্টি"; b[62]="বাষট্টি"; b[63]="তেষট্টি"; b[64]="চৌষট্টি"; b[65]="পঁয়ষট্টি";
        b[66]="ছেষট্টি"; b[67]="সাতষট্টি"; b[68]="আটষট্টি"; b[69]="উনসত্তর"; b[70]="সত্তর";
        b[71]="একাত্তর"; b[72]="বাহাত্তর"; b[73]="তেহাত্তর"; b[74]="চুয়াত্তর"; b[75]="পঁচাত্তর";
        b[76]="ছিয়াত্তর"; b[77]="সাতাত্তর"; b[78]="আটাত্তর"; b[79]="উনআশি"; b[80]="আশি";
        b[81]="একাশি"; b[82]="বিরাশি"; b[83]="তিরাশি"; b[84]="চুরাশি"; b[85]="পঁচাশি";
        b[86]="ছিয়াশি"; b[87]="সাতাশি"; b[88]="আটাশি"; b[89]="উননব্বই"; b[90]="নব্বই";
        b[91]="একানব্বই"; b[92]="বিরানব্বই"; b[93]="তিরানব্বই"; b[94]="চুরানব্বই"; b[95]="পঁচানব্বই";
        b[96]="ছিয়ানব্বই"; b[97]="সাতানব্বই"; b[98]="আটানব্বই"; b[99]="নিরানব্বই"; b[100]="একশ";
        return b;
    }

    // ===== Voice engine (short prompts) =====
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
