package com.example.aoopproject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.net.URL;
import java.security.SecureRandom;
import java.util.*;

public class BanglaShorobornoTestController {

    /* ===== UI ===== */
    @FXML private Label titleLabel, progressLabel, questionLabel, feedbackLabel, timerLabel;
    @FXML private Button opt1, opt2, opt3, opt4, nextButton;
    @FXML private Button examStartButton, playQuestionButton, backButton;

    /* ===== Data ===== */
    private static final List<String> SWAR = Arrays.asList("অ","আ","ই","ঈ","উ","ঊ","ঋ","এ","ঐ","ও","ঔ");
    private static final String AUDIO_BASE = "/com/example/aoopproject/Shoroborno.mp3";
    private final SecureRandom rng = new SecureRandom();
    private final List<Button> optionButtons = new ArrayList<>(4);

    private final int TOTAL_QUESTIONS = Math.min(15, SWAR.size());

    private List<String> questionLetters;
    private int idx = 0, score = 0;
    private boolean answered=false, running=false, over=false;
    private String currentAnswer;
    private Button correctBtn;

    /* ===== Audio ===== */
    private MediaPlayer mediaPlayer;

    @FXML
    public void initialize(){
        optionButtons.addAll(Arrays.asList(opt1,opt2,opt3,opt4));
        titleLabel.setText("বাংলা বর্ণ পরীক্ষা — স্বরবর্ণ");
        progressLabel.setText("প্রশ্ন ০/" + bn(TOTAL_QUESTIONS) + "   নম্বর: ০");
        questionLabel.setText("👂 'শুরু করুন' চাপুন, শুনুন এবং সঠিক বর্ণ নির্বাচন করুন।");
        feedbackLabel.setText("");
        timerLabel.setText("—");
        setOptionsDisabled(true);
        nextButton.setDisable(true);
        playQuestionButton.setDisable(true);
    }

    @FXML
    private void BackToMenu(ActionEvent e){
        stopAudio();
        ChangeFxmlController.switchScene(e,"bangla-alphabet-test-format.fxml");
    }

    /* ===== Start / flow ===== */
    @FXML
    private void handleExamStart(ActionEvent e){
        if (running) return;
        running=true; over=false; score=0; idx=0;
        backButton.setDisable(true);
        examStartButton.setDisable(true);
        playQuestionButton.setDisable(false);

        questionLetters = pickQuestions(TOTAL_QUESTIONS);
        loadCurrent();
        playCurrent();                    // auto-play first question
        updateProgress();
    }

    private void loadCurrent(){
        feedbackLabel.setText("");
        String target = questionLetters.get(idx);
        currentAnswer = target;

        // make 4 options with the correct one included
        LinkedHashSet<String> opts = new LinkedHashSet<>();
        opts.add(target);
        while (opts.size() < 4) {
            String s = SWAR.get(rng.nextInt(SWAR.size()));
            if (!s.equals(target)) opts.add(s);
        }
        List<String> list = new ArrayList<>(opts);
        Collections.shuffle(list, rng);

        correctBtn = null;
        for (int i=0;i<4;i++){
            Button b = optionButtons.get(i);
            String v = list.get(i);
            b.setText(v);
            b.setDisable(false);
            b.setStyle("");
            if (v.equals(target)) correctBtn=b;
        }
        answered=false;
        nextButton.setDisable(true);
        setOptionsDisabled(false);
    }

    @FXML
    private void handleOptionClick(ActionEvent e){
        if (!running || over || answered) return;
        Button clicked=(Button)e.getSource();
        boolean ok = clicked.getText().equals(currentAnswer);
        if (ok){
            score++;
            clicked.setStyle("-fx-background-color:#18632b; -fx-text-fill:white; -fx-font-weight:bold;");
            feedbackLabel.setStyle("-fx-text-fill:#0b5d1e; -fx-font-weight:bold;");
            feedbackLabel.setText("অভিনন্দন! 😄");
        }else{
            clicked.setStyle("-fx-background-color:#8f1f1f; -fx-text-fill:white; -fx-font-weight:bold;");
            if (correctBtn!=null) correctBtn.setStyle("-fx-background-color:#18632b; -fx-text-fill:white; -fx-font-weight:bold;");
            feedbackLabel.setStyle("-fx-text-fill:#7a0c0c; -fx-font-weight:bold;");
            feedbackLabel.setText("ভুল হয়েছে 🙁");
        }
        setOptionsDisabled(true);
        answered=true;
        nextButton.setDisable(false);
        updateProgress();
    }

    @FXML
    private void handleNext(ActionEvent e){
        if (!running || over || !answered) return;
        idx++;
        if (idx >= TOTAL_QUESTIONS){
            over=true; running=false;
            backButton.setDisable(false);
            showResults();
            return;
        }
        loadCurrent();
        playCurrent();                 // auto-play each new question
        updateProgress();
    }

    @FXML
    private void handlePlayQuestion(ActionEvent e){
        if (running) playCurrent();    // replay the same mp3
    }

    /* ===== Helpers ===== */
    private void setOptionsDisabled(boolean d){ for (Button b: optionButtons) b.setDisable(d); }

    private void updateProgress(){
        progressLabel.setText("প্রশ্ন " + bn(idx+1) + "/" + bn(TOTAL_QUESTIONS) + "   নম্বর: " + bn(score));
    }

    private List<String> pickQuestions(int n){
        List<String> pool = new ArrayList<>(SWAR);
        Collections.shuffle(pool, rng);
        return pool.subList(0, Math.min(n, pool.size()));
    }

    private void playCurrent(){
        String letter = questionLetters.get(idx);
        String res = AUDIO_BASE + "/" + letter + ".mp3";
        playFromClasspath(res);
    }

    private void playFromClasspath(String resourcePath){
        stopAudio();
        try{
            URL url = getClass().getResource(resourcePath);
            if (url == null) {
                System.err.println("[Audio] Not found: " + resourcePath);
                return;
            }
            Media media = new Media(url.toExternalForm());
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setAutoPlay(true);
            mediaPlayer.setOnError(() -> System.err.println("[Audio] " + mediaPlayer.getError()));
        }catch(Exception ex){
            System.err.println("[Audio] Failed to play " + resourcePath + " : " + ex.getMessage());
        }
    }

    private void stopAudio(){
        if (mediaPlayer != null){
            try { mediaPlayer.stop(); mediaPlayer.dispose(); } catch(Exception ignored){}
            mediaPlayer = null;
        }
    }

    private void showResults(){
        stopAudio();
        Alert a=new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("ফলাফল");
        a.setHeaderText(String.format("আপনি %s পেয়েছেন %s এর মধ্যে", bn(score), bn(TOTAL_QUESTIONS)));
        a.setContentText(perf(score));
        a.showAndWait();
    }

    private String perf(int s){
        if (s>=Math.round(TOTAL_QUESTIONS*0.85)) return "চমৎকার! 🏆";
        if (s>=Math.round(TOTAL_QUESTIONS*0.60)) return "ভালো কাজ! 👍";
        if (s>=Math.round(TOTAL_QUESTIONS*0.35)) return "চেষ্টা ভালো হয়েছে 💪";
        return "চর্চা চালিয়ে যান 🌟";
    }

    private String bn(int n){
        String[] d={"০","১","২","৩","৪","৫","৬","৭","৮","৯"};
        String s=String.valueOf(n), out="";
        for(char c:s.toCharArray()) out+=d[c-'0'];
        return out;
    }


}
