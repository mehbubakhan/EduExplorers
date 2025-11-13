package com.example.aoopproject;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Duration;

import java.security.SecureRandom;
import java.util.*;

public class BanglaHardTestController {

    @FXML private Label titleLabel, progressLabel, questionLabel, feedbackLabel, timerLabel;
    @FXML private Button opt1, opt2, opt3, opt4, nextButton;
    @FXML private Button examStartButton, playQuestionButton, backButton;

    private static final List<String> SWAR = Arrays.asList("অ","আ","ই","ঈ","উ","ঊ","ঋ","এ","ঐ","ও","ঔ");
    private static final List<String> BYANJON = Arrays.asList(
            "ক","খ","গ","ঘ","ঙ","চ","ছ","জ","ঝ","ঞ",
            "ট","ঠ","ড","ঢ","ণ","ত","থ","দ","ধ","ন",
            "প","ফ","ব","ভ","ম","য","র","ল","শ","ষ","স","হ"
    );

    private final SecureRandom rng = new SecureRandom();
    private final List<Button> optionButtons = new ArrayList<>();
    private static final int TOTAL_QUESTIONS = 15;

    private List<String> questionLetters;
    private int idx=0, score=0;
    private boolean answered=false, running=false, over=false;
    private String currentAnswer; private Button correctBtn;

    // timer
    private Timeline examTimer; private int secondsLeft=0;

    private final VoiceService voice = new BanglaVoice();

    @FXML
    public void initialize(){
        optionButtons.addAll(Arrays.asList(opt1,opt2,opt3,opt4));
        titleLabel.setText("বাংলা বর্ণ পরীক্ষা — কঠিন (৬০ সেকেন্ড)");
        progressLabel.setText("প্রশ্ন ০/১৫   নম্বর: ০");
        questionLabel.setText("👂 'শুরু করুন' চাপুন, শুনুন এবং সঠিক বর্ণ নির্বাচন করুন।");
        feedbackLabel.setText(""); timerLabel.setText("১:০০");
        setOptionsDisabled(true);
        nextButton.setDisable(true);
        playQuestionButton.setDisable(true);
    }

    /** 🔙 Back to Bangla Alphabet Test Format */
    @FXML
    private void BackToMenu(ActionEvent e){
        stopTimer();
        // IMPORTANT: make sure the file name matches your actual FXML
        ChangeFxmlController.switchScene(e,"bangla-alphabet-test-format.fxml");
    }

    @FXML private void handleExamStart(ActionEvent e){
        if (running) return;
        running=true; over=false; score=0; idx=0;
        backButton.setDisable(true);
        examStartButton.setDisable(true);
        playQuestionButton.setDisable(false);
        questionLetters = pickQuestions(TOTAL_QUESTIONS);
        loadCurrent();
        startTimer(60);
        speakPrompt(questionLetters.get(idx));
        updateProgress();
    }

    private void loadCurrent(){
        feedbackLabel.setText("");
        String target = questionLetters.get(idx);
        currentAnswer = target;

        LinkedHashSet<String> opts=new LinkedHashSet<>();
        opts.add(target);
        while(opts.size()<4){
            String s = pickRandomFromPool();
            if (!s.equals(target)) opts.add(s);
        }
        List<String> list=new ArrayList<>(opts);
        Collections.shuffle(list, rng);
        for (int i=0;i<4;i++){
            Button b=optionButtons.get(i); String v=list.get(i);
            b.setText(v); b.setDisable(false); b.setStyle("");
            if (v.equals(target)) correctBtn=b;
        }
        answered=false; nextButton.setDisable(true);
    }

    @FXML private void handleOptionClick(ActionEvent e){
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
        answered=true; nextButton.setDisable(false);
        updateProgress();
    }

    @FXML private void handleNext(ActionEvent e){
        if (!running || over || !answered) return;
        idx++;
        if (idx>=TOTAL_QUESTIONS){
            finish(false); return;
        }
        loadCurrent(); setOptionsDisabled(false);
        speakPrompt(questionLetters.get(idx));
        updateProgress();
    }

    @FXML private void handlePlayQuestion(ActionEvent e){
        if (running) speakPrompt(questionLetters.get(idx));
    }

    // ===== timer =====
    private void startTimer(int seconds){
        stopTimer();
        secondsLeft = seconds;
        timerLabel.setText(format(secondsLeft));
        examTimer = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
            secondsLeft--;
            timerLabel.setText(format(secondsLeft));
            if (secondsLeft<=0){ finish(true); }
        }));
        examTimer.setCycleCount(seconds);
        examTimer.playFromStart();
    }
    private void stopTimer(){ if (examTimer!=null){ examTimer.stop(); examTimer=null; } }
    private String format(int s){ int m=s/60, r=s%60; return bn(m)+":"+(r<10? "০"+bn(r):bn(r)); }

    private void finish(boolean timeout){
        if (over) return;
        stopTimer(); over=true; running=false;
        setOptionsDisabled(true); nextButton.setDisable(true);
        backButton.setDisable(false);
        if (timeout){
            feedbackLabel.setStyle("-fx-text-fill:#7a0c0c; -fx-font-weight:bold;");
            feedbackLabel.setText("⏰ সময় শেষ 😢 — "+bn(idx+1)+"/"+bn(TOTAL_QUESTIONS));
        }
        showResults(timeout);
    }

    private void setOptionsDisabled(boolean d){ for(Button b: optionButtons) b.setDisable(d); }
    private void updateProgress(){ progressLabel.setText("প্রশ্ন "+bn(idx+1)+"/"+bn(TOTAL_QUESTIONS)+"   নম্বর: "+bn(score)); }

    private List<String> pickQuestions(int n){
        List<String> pool=new ArrayList<>(); pool.addAll(SWAR); pool.addAll(BYANJON);
        Collections.shuffle(pool, rng);
        return pool.subList(0, Math.min(n, pool.size()));
    }
    private String pickRandomFromPool(){ List<String> p=new ArrayList<>(); p.addAll(SWAR); p.addAll(BYANJON); return p.get(rng.nextInt(p.size())); }

    private void speakPrompt(String letter){
        new Thread(() -> { try{ voice.speak("সঠিক বর্ণ নির্বাচন করুন " + letter); }catch(Exception ignored){} }, "tts-bn-hard").start();
    }

    private void showResults(boolean timeout){
        Alert a=new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("ফলাফল");
        a.setHeaderText(timeout
                ? String.format("সময় শেষ — আপনি পৌঁছেছেন %s/%s প্রশ্ন", bn(idx+1), bn(TOTAL_QUESTIONS))
                : String.format("আপনি %s পেয়েছেন %s এর মধ্যে", bn(score), bn(TOTAL_QUESTIONS)));
        a.setContentText(perf(score));
        a.showAndWait();
    }

    private String perf(int s){ if (s>=13) return "চমৎকার! 🏆"; if (s>=9) return "ভালো কাজ! 👍"; if (s>=5) return "চেষ্টা ভালো হয়েছে 💪"; return "চর্চা চালিয়ে যান 🌟"; }
    private String bn(int n){ String[] d={"০","১","২","৩","৪","৫","৬","৭","৮","৯"}; String s=String.valueOf(n),o=""; for(char c:s.toCharArray()) o+=d[c-'0']; return o; }

    interface VoiceService { void speak(String text) throws Exception; }
    static class BanglaVoice implements VoiceService{
        @Override public void speak(String text) throws Exception{
            String os=System.getProperty("os.name","").toLowerCase();
            if (os.contains("win")){
                String ps="$s=New-Object -ComObject SAPI.SpVoice; $text='"+text.replace("'", "''")+"'; $s.Speak($text);";
                run(new String[]{"powershell","-NoProfile","-Command",ps});
            } else if (os.contains("mac")){
                run(new String[]{"say",text});
            } else {
                run(new String[]{"bash","-lc","(command -v espeak-ng >/dev/null && espeak-ng -v bn '"+text+"') || (command -v espeak >/dev/null && espeak -v bn '"+text+"')"});
            }
        }
        private static void run(String[] cmd) throws Exception{ Process p=new ProcessBuilder(cmd).redirectErrorStream(true).start(); p.waitFor(); }
    }
}
