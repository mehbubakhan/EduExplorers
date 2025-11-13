package com.example.aoopproject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.security.SecureRandom;
import java.util.*;

public class EnglishTestWordmakingController {

    // ===== FXML =====
    @FXML private Label titleLabel, progressLabel, questionLabel, feedbackLabel, timerLabel;
    @FXML private Button opt1, opt2, opt3, opt4, nextButton, examStartButton, playQuestionButton, backButton;

    // ===== State =====
    private final List<Button> optionButtons = new ArrayList<>();
    private final SecureRandom rng = new SecureRandom();
    private static final int TOTAL_QUESTIONS = 15;

    // A–Z words
    private static final Map<String, String[]> WORDS = new LinkedHashMap<>();
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

    private final List<String> allWords = buildAllWords(); // 52 words
    private List<String> lettersPlan;
    private int qIndex = 0, score = 0;
    private boolean answered = false, running = false, over = false;

    private String currentLetter;   // e.g., "B"
    private String targetWord;      // e.g., "Ball"
    private Button correctButton = null;
    private final Map<Button,String> valueOfBtn = new HashMap<>();

    // ===== Voice =====
    private final VoiceService voice = new HybridVoiceService(false);

    @FXML
    public void initialize() {
        optionButtons.addAll(Arrays.asList(opt1, opt2, opt3, opt4));
        for (Button b : optionButtons) b.setWrapText(true);

        titleLabel.setText("Wordmaking (A–Z Examples)");
        progressLabel.setText("Q 0/15   Score: 0");
        questionLabel.setText("Press Start, listen, and pick the word (____).");
        feedbackLabel.setText("");
        timerLabel.setText("—");

        disableOptions(true);
        nextButton.setDisable(true);
        playQuestionButton.setDisable(true);
    }

    @FXML private void handleExamStart(ActionEvent e){
        if (running) return;
        running = true; over = false; score = 0; qIndex = 0;

        backButton.setDisable(true);
        examStartButton.setDisable(true);
        playQuestionButton.setDisable(false);

        buildPlan();
        loadQuestion();
        updateProgress();
        speakCurrent();
    }

    @FXML private void handlePlayQuestion(ActionEvent e){
        if (!running || over) return;
        speakCurrent();
    }

    @FXML private void handleOptionClick(ActionEvent e){
        if (!running || over || answered) return;

        Button clicked = (Button) e.getSource();
        String chosen = valueOfBtn.get(clicked);
        boolean ok = Objects.equals(chosen, targetWord);

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
//          feedbackLabel.setText("Wrong.");
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
            over = true; running = false;
            backButton.setDisable(false);
            showFinalScore();
        } else {
            loadQuestion();
            updateProgress();
            disableOptions(false);
            speakCurrent();
        }
    }

    @FXML private void BackToTest(ActionEvent e){
        if (running && !over) return; // prevent back during exam
        ChangeFxmlController.switchScene(e, "english-test-format.fxml");
    }

    // ===== Core =====
    private static List<String> buildAllWords(){
        List<String> out = new ArrayList<>(52);
        for (String[] pair : WORDS.values()){
            out.add(pair[0]); out.add(pair[1]);
        }
        return out;
    }

    private void buildPlan(){
        lettersPlan = new ArrayList<>(WORDS.keySet());
        Collections.shuffle(lettersPlan, rng);
        if (lettersPlan.size() > TOTAL_QUESTIONS) {
            lettersPlan = lettersPlan.subList(0, TOTAL_QUESTIONS);
        }
    }

    private void loadQuestion(){
        resetButtons();
        feedbackLabel.setText("");

        currentLetter = lettersPlan.get(qIndex);          // e.g., "B"
        String[] pair = WORDS.get(currentLetter);         // {"Ball","Bat"}
        targetWord = pair[rng.nextInt(2)];                // choose ONE word

        // ❗ Do NOT reveal the word in the prompt line
        questionLabel.setText("Select the word (____)");

        // Build 4 unique word options from the global pool (must include target)
        Set<String> set = new LinkedHashSet<>();
        set.add(targetWord);
        while (set.size() < 4){
            set.add(allWords.get(rng.nextInt(allWords.size())));
        }
        List<String> options = new ArrayList<>(set);
        Collections.shuffle(options, rng);

        valueOfBtn.clear(); correctButton = null;
        for (int i=0;i<4;i++){
            Button b = optionButtons.get(i);
            String v = options.get(i);
            b.setText(v);           // options remain visible
            b.setDisable(false);
            b.setStyle("");
            valueOfBtn.put(b, v);
            if (Objects.equals(v, targetWord)) correctButton = b;
        }
        answered = false;
        nextButton.setDisable(true);
        disableOptions(false);
    }

    private void updateProgress(){
        progressLabel.setText(String.format("Q %d/%d   Score: %d", qIndex+1, TOTAL_QUESTIONS, score));
    }

    private void speakCurrent(){
        // Voice still tells the target so the student listens
        speakAsync("Select the word " + targetWord + ".");
    }

    private void showFinalScore(){
        String comment = (score>=13? "Excellent!" : score>=9? "Great job!" : score>=5? "Good try!" : "Keep practicing!");
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Result");
        a.setHeaderText(null);
        a.setContentText(String.format("You scored %d out of %d — %s", score, TOTAL_QUESTIONS, comment));
        a.show();

        // Also speak the score and comment
        speakAsync("You scored " + score + " out of " + TOTAL_QUESTIONS + ". " + comment);
    }

    // ===== Helpers =====
    private void disableOptions(boolean d){ for (Button b : optionButtons) b.setDisable(d); }
    private void resetButtons(){ for (Button b: optionButtons){ b.setDisable(false); b.setStyle(""); } }
    private void setGreen(Button b){ b.setStyle("-fx-background-color:#18632b; -fx-text-fill:white; -fx-font-weight:bold;"); }
    private void setRed(Button b){ b.setStyle("-fx-background-color:#8f1f1f; -fx-text-fill:white; -fx-font-weight:bold;"); }

    // ===== Voice =====
    private void speakAsync(String text){
        new Thread(() -> { try { voice.speak(text); } catch (Exception ignored) {} }, "tts-wordmaking").start();
    }
    interface VoiceService { void speak(String text) throws Exception; }
    static class HybridVoiceService implements VoiceService {
        HybridVoiceService(boolean unused){}
        @Override public void speak(String text) throws Exception {
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
