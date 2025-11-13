package com.example.aoopproject;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Duration;
import java.security.SecureRandom;
import java.util.*;

public class EnglishTestHardController {

    // ===== FXML =====
    @FXML private Label titleLabel, progressLabel, questionLabel, feedbackLabel, timerLabel;
    @FXML private Button opt1, opt2, opt3, opt4, nextButton, examStartButton, playQuestionButton, retryButton, backButton;

    // ===== State =====
    private final List<Button> optionButtons = new ArrayList<>();
    private final SecureRandom rng = new SecureRandom();
    private static final int TOTAL_QUESTIONS = 15;

    private enum QType { LETTER, WORD }
    private List<QType> plan;
    private int qIndex = 0, score = 0;
    private boolean answered = false, running = false, over = false;

    // LETTER
    private char targetLetter;

    // WORD
    private static final Map<String,String[]> WORDS = new LinkedHashMap<>();
    static {
        WORDS.put("A", new String[]{"Apple","Ant"});
        WORDS.put("B", new String[]{"Ball","Bat"});
        WORDS.put("C", new String[]{"Cat","Cup"});
        WORDS.put("D", new String[]{"Dog","Doll"});
        WORDS.put("E", new String[]{"Egg","Elephant"});
        WORDS.put("F", new String[]{"Frog","Fox"});
        WORDS.put("G", new String[]{"Gun","Girl"});
        WORDS.put("H", new String[]{"Horse","Hot"});
        WORDS.put("I", new String[]{"Iron","Ice"});
        WORDS.put("J", new String[]{"Juice","Jug"});
        WORDS.put("K", new String[]{"Kite","Key"});
        WORDS.put("L", new String[]{"Lemon","Light"});
        WORDS.put("M", new String[]{"Mango","Man"});
        WORDS.put("N", new String[]{"Nut","New"});
        WORDS.put("O", new String[]{"Oil","Open"});
        WORDS.put("P", new String[]{"Pet","Picture"});
        WORDS.put("Q", new String[]{"Question","Queen"});
        WORDS.put("R", new String[]{"Rat","Run"});
        WORDS.put("S", new String[]{"Sad","Sugar"});
        WORDS.put("T", new String[]{"Tomato","Team"});
        WORDS.put("U", new String[]{"Under","Up"});
        WORDS.put("V", new String[]{"Video","Voice"});
        WORDS.put("W", new String[]{"White","Wall"});
        WORDS.put("X", new String[]{"X-ray","Xerox"});
        WORDS.put("Y", new String[]{"Yes","You"});
        WORDS.put("Z", new String[]{"Zoo","Zero"});
    }
    private final List<String> allWords = buildAllWords();
    private String targetWord;

    private Button correctButton = null;
    private final Map<Button,String> optionValue = new HashMap<>();

    // Timer
    private Timeline examTimer; private int secondsLeft = 0;

    // ===== Voice =====
    private final VoiceService voice = new HybridVoiceService(false);

    @FXML
    public void initialize(){
        optionButtons.addAll(Arrays.asList(opt1, opt2, opt3, opt4));
        for (Button b : optionButtons) b.setWrapText(true);

        titleLabel.setText("English Test (Hard)");
        progressLabel.setText("Q 0/15   Score: 0");
        questionLabel.setText("Press Start, listen, and select the correct option.");
        feedbackLabel.setText("");
        timerLabel.setText("1:00");

        disableOptions(true);
        nextButton.setDisable(true);
        playQuestionButton.setDisable(true);
        retryButton.setDisable(true);
    }

    /* ===== Navigation back to format ===== */
    @FXML
    private void BackToTest(ActionEvent e){
        if (running && !over) return; // lock during exam
        stopTimer();
        ChangeFxmlController.switchScene(e, "english-test-format.fxml");
    }

    /* ===== Exam flow ===== */
    @FXML private void handleExamStart(ActionEvent e){ startExam(); }
    @FXML private void handleRetry(ActionEvent e){ startExam(); }

    private void startExam(){
        if (running) return;
        running=true; over=false; answered=false; score=0; qIndex=0;

        backButton.setDisable(true);
        retryButton.setDisable(true);
        examStartButton.setDisable(true);
        playQuestionButton.setDisable(false);

        buildPlan();
        loadQuestion();
        updateProgress();
        startTimer(60);
        speakCurrent();
    }

    @FXML private void handlePlayQuestion(ActionEvent e){
        if (running && !over) speakCurrent();
    }

    @FXML private void handleOptionClick(ActionEvent e){
        if (!running || over || answered) return;

        Button clicked = (Button) e.getSource();
        String chosen = optionValue.get(clicked);

        boolean ok;
        QType type = plan.get(qIndex);
        if (type == QType.LETTER){
            ok = chosen != null && chosen.length()==1 && Character.toUpperCase(chosen.charAt(0))==targetLetter;
        } else {
            ok = Objects.equals(chosen, targetWord);
        }

        if (ok){
            score++;
            setGreen(clicked);
            feedbackLabel.setStyle("-fx-text-fill:#0b5d1e; -fx-font-weight:bold;");
            feedbackLabel.setText("Correct!");
            speakAsync("Correct!");
        } else {
            setRed(clicked);
            if (correctButton != null) setGreen(correctButton);
            feedbackLabel.setStyle("-fx-text-fill:#7a0c0c; -fx-font-weight:bold;");
            feedbackLabel.setText("Sorry, wrong answer.");
            speakAsync("Sorry, wrong answer.");
        }

        answered = true;
        disableOptions(true);
        nextButton.setDisable(false);
        updateProgress();
    }

    @FXML private void handleNext(ActionEvent e){
        if (!running || over || !answered) return;
        qIndex++;
        if (qIndex >= TOTAL_QUESTIONS){
            finishExam(false);
        } else {
            loadQuestion();
            updateProgress();
            disableOptions(false);
            speakCurrent();
        }
    }

    /* ===== Core ===== */
    private static List<String> buildAllWords(){
        List<String> out = new ArrayList<>(52);
        for (String[] pair : WORDS.values()){
            out.add(pair[0]); out.add(pair[1]);
        }
        return out;
    }

    private void buildPlan(){
        plan = new ArrayList<>(TOTAL_QUESTIONS);
        for (int i=0;i<TOTAL_QUESTIONS;i++){
            plan.add(rng.nextBoolean()? QType.LETTER : QType.WORD);
        }
    }

    private void loadQuestion(){
        resetButtons();
        feedbackLabel.setText("");
        optionValue.clear(); correctButton = null;

        QType type = plan.get(qIndex);
        if (type == QType.LETTER){
            // prompt hides the letter
            questionLabel.setText("Select the alphabet (____)");

            targetLetter = (char) ('A' + rng.nextInt(26));
            Set<Character> set = new LinkedHashSet<>();
            set.add(targetLetter);
            while (set.size() < 4) set.add((char) ('A' + rng.nextInt(26)));
            List<Character> opts = new ArrayList<>(set);
            Collections.shuffle(opts, rng);

            for (int i=0;i<4;i++){
                Button b = optionButtons.get(i);
                char base = opts.get(i);
                String txt = String.valueOf(rng.nextBoolean()? Character.toUpperCase(base) : Character.toLowerCase(base));
                b.setText(txt);
                optionValue.put(b, String.valueOf(Character.toUpperCase(base)));
                if (Character.toUpperCase(base)==targetLetter) correctButton = b;
            }
            answered=false; nextButton.setDisable(true); disableOptions(false);

        } else { // WORD
            String[] keys = WORDS.keySet().toArray(new String[0]);
            String letterKey = keys[rng.nextInt(keys.length)];
            String[] pair = WORDS.get(letterKey);
            targetWord = pair[rng.nextInt(2)];

            // prompt hides the word
            questionLabel.setText("Select the word (____)");

            Set<String> set = new LinkedHashSet<>();
            set.add(targetWord);
            while (set.size() < 4){
                set.add(allWords.get(rng.nextInt(allWords.size())));
            }
            List<String> opts = new ArrayList<>(set);
            Collections.shuffle(opts, rng);

            for (int i=0;i<4;i++){
                Button b = optionButtons.get(i);
                String v = opts.get(i);
                b.setText(v);
                optionValue.put(b, v);
                if (Objects.equals(v, targetWord)) correctButton = b;
            }
            answered=false; nextButton.setDisable(true); disableOptions(false);
        }
    }

    private void finishExam(boolean timeout){
        if (over) return;
        stopTimer();
        running=false; over=true;
        disableOptions(true);
        nextButton.setDisable(true);

        backButton.setDisable(false);
        retryButton.setDisable(false);
        examStartButton.setDisable(false);
        playQuestionButton.setDisable(true);

        String comment = (score>=13? "Excellent!" : score>=9? "Great job!" : score>=5? "Good try!" : "Keep practicing!");
        String body = timeout
                ? String.format("You scored %d out of %d — %s (Time out)", score, TOTAL_QUESTIONS, comment)
                : String.format("You scored %d out of %d — %s", score, TOTAL_QUESTIONS, comment);

        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Result");
        a.setHeaderText(null);
        a.setContentText(body);
        a.setOnShown(ev -> speakAsync(body));
        a.show();
    }

    // ===== Timer =====
    private void startTimer(int seconds){
        stopTimer();
        secondsLeft = seconds;
        timerLabel.setText(formatTime(secondsLeft));
        examTimer = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
            secondsLeft--;
            timerLabel.setText(formatTime(secondsLeft));
            if (secondsLeft <= 0) finishExam(true);
        }));
        examTimer.setCycleCount(seconds);
        examTimer.playFromStart();
    }
    private void stopTimer(){ if (examTimer!=null){ examTimer.stop(); examTimer=null; } }
    private String formatTime(int s){ int m=s/60, r=s%60; return String.format("%d:%02d", m, r); }

    private void speakCurrent(){
        QType type = plan.get(qIndex);
        if (type == QType.LETTER){
            speakAsync("Select the alphabet " + spokenLetter(targetLetter));
        } else {
            speakAsync("Select the word " + targetWord);
        }
    }
    private String spokenLetter(char c){
        if (c=='H') return "aitch";
        if (c=='W') return "double u";
        return String.valueOf(c);
    }

    private void updateProgress(){
        progressLabel.setText(String.format("Q %d/%d   Score: %d", qIndex+1, TOTAL_QUESTIONS, score));
    }

    // ===== Helpers =====
    private void disableOptions(boolean d){ for (Button b : optionButtons) b.setDisable(d); }
    private void resetButtons(){ for (Button b: optionButtons){ b.setDisable(false); b.setStyle(""); } }
    private void setGreen(Button b){ b.setStyle("-fx-background-color:#18632b; -fx-text-fill:white; -fx-font-weight:bold;"); }
    private void setRed(Button b){ b.setStyle("-fx-background-color:#8f1f1f; -fx-text-fill:white; -fx-font-weight:bold;"); }

    // ===== Voice =====
    private void speakAsync(String text){
        new Thread(() -> { try { voice.speak(text); } catch (Exception ignored) {} }, "tts-hard").start();
    }
    interface VoiceService { void speak(String text) throws Exception; }
    static class HybridVoiceService implements VoiceService {
        HybridVoiceService(boolean unused){}
        @Override public void speak(String text) throws Exception{
            String os = System.getProperty("os.name","").toLowerCase();
            if (os.contains("mac")) {
                run(new String[]{"say", text});
            } else if (os.contains("win")) {
                run(new String[]{"powershell","-NoProfile","-Command",
                        "$s=New-Object -ComObject SAPI.SpVoice; $s.Speak('"+text.replace("'", "''")+"');"});
            } else {
                run(new String[]{"bash","-lc",
                        "(command -v espeak-ng >/dev/null && espeak-ng '"+text+"') || (command -v espeak >/dev/null && espeak '"+text+"')"});
            }
        }
        private static void run(String[] cmd) throws Exception {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.waitFor();
        }
    }
}
