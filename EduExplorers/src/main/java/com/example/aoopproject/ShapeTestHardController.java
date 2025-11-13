package com.example.aoopproject;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
    import javafx.fxml.FXML;
    import javafx.geometry.Pos;
    import javafx.scene.Node;
    import javafx.scene.control.*;
    import javafx.scene.layout.*;
    import javafx.scene.paint.Color;
    import javafx.scene.shape.*;
    import javafx.util.Duration;

    import java.security.SecureRandom;
    import java.util.*;

    public class ShapeTestHardController {

        /* ===== FXML ===== */
        @FXML private Label titleLabel, timerLabel, progressLabel, questionLabel, feedbackLabel;
        @FXML private Button opt1, opt2, opt3, opt4, nextButton, startButton, playButton, backButton, retryButton;
        @FXML
        void NextToShapeTestHard(ActionEvent event){
            ChangeFxmlController.switchScene(event,"shape-test-hard.fxml");
        }
        /* ===== State ===== */
        private final List<Button> optionButtons = new ArrayList<>();
        private final SecureRandom rng = new SecureRandom();
        private static final int TOTAL_QUESTIONS = 15;

        private enum QType { SHAPE, COLOR, SHAPE_AND_COLOR }
        private enum ShapeKind { CIRCLE, TRIANGLE, SQUARE, RECTANGLE, OVAL, STAR, DIAMOND, PENTAGON, HEXAGON }
        private enum ColorKind {
            RED(Color.RED), BLUE(Color.DODGERBLUE), GREEN(Color.GREEN), YELLOW(Color.GOLD),
            ORANGE(Color.ORANGE), PURPLE(Color.MEDIUMPURPLE), PINK(Color.HOTPINK),
            BROWN(Color.SADDLEBROWN), BLACK(Color.BLACK), GRAY(Color.GRAY);
            final Color fx; ColorKind(Color c){ this.fx = c; }
            @Override public String toString(){ String n=name().toLowerCase(); return Character.toUpperCase(n.charAt(0))+n.substring(1); }
        }

        private List<QType> questionPlan;
        private int qIndex = 0, score = 0;
        private boolean answered = false, running = false, over = false;

        private ShapeKind targetShape;
        private ColorKind targetColor;

        private static class OptionKey {
            final ShapeKind shape; final ColorKind color; final boolean useGraphic;
            OptionKey(ShapeKind s, ColorKind c, boolean g){ shape=s; color=c; useGraphic=g; }
            boolean matches(ShapeKind s, ColorKind c, QType t){
                return switch (t){
                    case SHAPE -> shape==s;
                    case COLOR -> color==c;
                    case SHAPE_AND_COLOR -> shape==s && color==c;
                };
            }
            boolean sameIdentity(OptionKey o){
                return Objects.equals(shape,o.shape) && Objects.equals(color,o.color) && useGraphic==o.useGraphic;
            }
            boolean sameValue(OptionKey o){
                return Objects.equals(shape,o.shape) && Objects.equals(color,o.color);
            }
        }
        private final Map<Button,OptionKey> optionAnswer = new HashMap<>();
        private Button correctButton = null;

        /* ===== Timer ===== */
        private Timeline examTimer; private int secondsLeft = 0;

        /* ===== Voice ===== */
        private final VoiceService voice = new HybridVoiceService(false);

        @FXML
        public void initialize(){
            optionButtons.addAll(Arrays.asList(opt1, opt2, opt3, opt4));
            for (Button b : optionButtons) {
                b.setPrefHeight(130);
                b.setPrefWidth(360);
                b.setMaxWidth(Double.MAX_VALUE);
                b.setContentDisplay(ContentDisplay.TOP);
                b.setWrapText(true);
                b.setStyle("-fx-font-size: 18px;");
            }
            titleLabel.setText("Shape & Color Quiz (Hard)");
            timerLabel.setText("1:00");
            progressLabel.setText("Q 0/15   Score: 0");
            questionLabel.setText("Press Start, listen to the voice, and select the correct option.");
            feedbackLabel.setText("");

            disableOptions(true);
            nextButton.setDisable(true);
            playButton.setDisable(true);
            retryButton.setDisable(true);
            backButton.setDisable(false);
        }

        /* ===== Exam lifecycle ===== */
        @FXML private void handleExamStart(ActionEvent e){
            startExam();
        }

        @FXML private void handleRetry(ActionEvent e){
            startExam();
        }

        private void startExam(){
            if (running) return;
            running = true; over = false;
            score = 0; qIndex = 0;

            backButton.setDisable(true);     // lock Back during the exam
            retryButton.setDisable(true);    // lock Retry too
            startButton.setDisable(true);
            playButton.setDisable(false);
            disableOptions(false);
            nextButton.setDisable(true);

            buildQuestionPlan();
            loadQuestion();   // no speak here to avoid double
            startTimer(60);   // 60 seconds total
            speakCurrent();   // speak exactly once
            updateProgress();
        }

        private void buildQuestionPlan(){
            questionPlan = new ArrayList<>(TOTAL_QUESTIONS);
            for (int i=0;i<TOTAL_QUESTIONS;i++){
                int r = rng.nextInt(3);
                questionPlan.add(r==0? QType.SHAPE : (r==1? QType.COLOR : QType.SHAPE_AND_COLOR));
            }
        }

        private void loadQuestion(){
            resetButtons();
            feedbackLabel.setText("");

            QType type = questionPlan.get(qIndex);
            targetShape = (type==QType.SHAPE || type==QType.SHAPE_AND_COLOR) ? randomShape() : null;
            targetColor = (type==QType.COLOR || type==QType.SHAPE_AND_COLOR) ? randomColor() : null;

            String prompt = switch (type){
                case SHAPE -> "Select the shape " + nice(targetShape);
                case COLOR -> "Select the color " + targetColor;
                case SHAPE_AND_COLOR -> "Select the " + targetColor + " " + nice(targetShape);
            };
            questionLabel.setText(prompt);

            optionAnswer.clear(); correctButton = null;

            List<OptionKey> keys = makeOptions(type, targetShape, targetColor);
            Collections.shuffle(keys, rng);

            for (int i=0;i<4;i++){
                Button b = optionButtons.get(i);
                OptionKey k = keys.get(i);
                renderButton(b, k, type);
                optionAnswer.put(b, k);
                if (k.matches(targetShape, targetColor, type)) correctButton = b;
            }

            answered = false;
            nextButton.setDisable(true);
        }

        private List<OptionKey> makeOptions(QType type, ShapeKind s, ColorKind c){
            LinkedHashSet<OptionKey> set = new LinkedHashSet<>();
            set.add(new OptionKey(s, c, chooseGraphicFor(type))); // correct first (random repr)

            while (set.size() < 4){
                ShapeKind rs = null; ColorKind rc = null;
                switch (type){
                    case SHAPE -> rs = randomShape();
                    case COLOR -> rc = randomColor();
                    case SHAPE_AND_COLOR -> { rs = randomShape(); rc = randomColor(); }
                }
                OptionKey cand = new OptionKey(rs, rc, chooseGraphicFor(type));

                if (set.stream().anyMatch(cand::sameIdentity)) continue;
                if (type==QType.SHAPE && set.stream().anyMatch(o -> o.shape==cand.shape)) continue;
                if (type==QType.COLOR && set.stream().anyMatch(o -> o.color==cand.color)) continue;
                if (type==QType.SHAPE_AND_COLOR && set.stream().anyMatch(o -> o.sameValue(cand))) continue;

                set.add(cand);
            }
            return new ArrayList<>(set);
        }

        private boolean chooseGraphicFor(QType t){ return rng.nextBoolean(); }

        private void renderButton(Button b, OptionKey key, QType type){
            b.setStyle(""); b.setDisable(false); b.setText(null); b.setGraphic(null);
            b.setContentDisplay(ContentDisplay.TOP);

            if (type==QType.COLOR){
                if (key.useGraphic) b.setGraphic(colorSwatch(64,48,key.color.fx));
                else b.setText(key.color.toString());
                return;
            }
            if (type==QType.SHAPE){
                if (key.useGraphic) b.setGraphic(shapeGraphic(key.shape,64,64, Color.BLACK));
                else b.setText(nice(key.shape));
                return;
            }
            // BOTH
            if (key.useGraphic) b.setGraphic(shapeGraphic(key.shape,64,64, key.color.fx));
            else b.setText(key.color + " " + nice(key.shape));
        }

        @FXML private void handleOptionClick(ActionEvent e){
            if (!running || over || answered) return;
            Button clicked = (Button)e.getSource();
            OptionKey chosen = optionAnswer.get(clicked);
            QType type = questionPlan.get(qIndex);

            boolean ok = chosen != null && chosen.matches(targetShape, targetColor, type);
            if (ok){
                score++;
                setGreen(clicked);
                feedbackLabel.setStyle("-fx-text-fill:#0b5d1e; -fx-font-weight:bold;");
                feedbackLabel.setText("Correct! 😄");
                speakAsync("Correct!");
            } else {
                setRed(clicked);
                if (correctButton!=null) setGreen(correctButton);
                feedbackLabel.setStyle("-fx-text-fill:#7a0c0c; -fx-font-weight:bold;");
                feedbackLabel.setText("Sorry, wrong answer. 🙁");
                speakAsync("Sorry, wrong answer.");
            }

            answered = true;
            disableOptions(true);
            updateProgress();

            if (qIndex >= TOTAL_QUESTIONS - 1){
                finishExam(false);
            } else {
                nextButton.setDisable(false);
            }
        }

        @FXML private void handleNext(ActionEvent e){
            if (!running || over || !answered) return;
            qIndex++;
            if (qIndex >= TOTAL_QUESTIONS){
                finishExam(false);
            } else {
                loadQuestion();      // doesn't speak
                updateProgress();
                disableOptions(false);
                speakCurrent();      // speak once per new question
            }
        }

        @FXML private void handlePlayQuestion(ActionEvent e){
            if (running) speakCurrent();
        }

        @FXML private void BackToTest(ActionEvent event){
            if (backButton.isDisabled()) return; // guard during exam
            stopTimer();
            ChangeFxmlController.switchScene(event, "shape-test-format.fxml");
        }

        /* ===== Timer ===== */
        private void startTimer(int seconds){
            stopTimer();
            secondsLeft = seconds;
            timerLabel.setText(formatTime(secondsLeft));
            examTimer = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
                secondsLeft--;
                timerLabel.setText(formatTime(secondsLeft));
                if (secondsLeft <= 0){
                    finishExam(true); // timeout
                }
            }));
            examTimer.setCycleCount(seconds);
            examTimer.playFromStart();
        }

        private void stopTimer(){
            if (examTimer != null){ examTimer.stop(); examTimer = null; }
        }

        private String formatTime(int s){ int m=s/60, r=s%60; return String.format("%d:%02d", m, r); }

        private void finishExam(boolean timeout){
            if (over) return;
            stopTimer();
            running=false; over=true;

            disableOptions(true);
            nextButton.setDisable(true);

            // Unlock Back and Retry
            backButton.setDisable(false);
            retryButton.setDisable(false);
            startButton.setDisable(false);
            playButton.setDisable(true);

            if (timeout){
                feedbackLabel.setStyle("-fx-text-fill:#7a0c0c; -fx-font-weight:bold;");
                feedbackLabel.setText(String.format("⏰ Time out — You reached Q %d/%d", qIndex+1, TOTAL_QUESTIONS));
            }
            showAndSpeakResult(timeout);
        }

        /* ===== Voice + Result ===== */
        private void speakCurrent(){
            QType type = questionPlan.get(qIndex);
            String text = switch (type){
                case SHAPE -> "Select the shape " + nice(targetShape);
                case COLOR -> "Select the color " + targetColor;
                case SHAPE_AND_COLOR -> "Select the " + targetColor + " " + nice(targetShape);
            };
            speakAsync(text);
        }

        private void showAndSpeakResult(boolean timeout){
            String comment = (score >= 13) ? "Excellent!" :
                    (score >= 9)  ? "Great job!" :
                            (score >= 5)  ? "Good try!"  :
                                    "Keep practicing!";

            String header = timeout
                    ? String.format("Time out — You reached Q %d/%d", qIndex+1, TOTAL_QUESTIONS)
                    : String.format("You scored %d/%d", score, TOTAL_QUESTIONS);

            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("Result");
            a.setHeaderText(header);
            a.setContentText(comment);

            final String speech = timeout
                    ? String.format("Time out. You scored %d out of %d. %s", score, TOTAL_QUESTIONS, comment)
                    : String.format("You scored %d out of %d. %s", score, TOTAL_QUESTIONS, comment);

            a.setOnShown(ev -> speakAsync(speech));
            a.show();
        }

        private void speakAsync(String text){
            new Thread(() -> {
                try { voice.speak(text); } catch (Exception ignored) {}
            }, "tts-hard").start();
        }

        /* ===== Helpers ===== */
        private void updateProgress(){
            progressLabel.setText(String.format("Q %d/%d   Score: %d", qIndex+1, TOTAL_QUESTIONS, score));
        }

        private void disableOptions(boolean dis){ for (Button b : optionButtons) b.setDisable(dis); }
        private void resetButtons(){ for (Button b: optionButtons){ b.setDisable(false); b.setStyle(""); b.setGraphic(null); b.setText(null);} }
        private void setGreen(Button b){ b.setStyle("-fx-background-color:#18632b; -fx-text-fill:white; -fx-font-weight:bold;"); }
        private void setRed(Button b){ b.setStyle("-fx-background-color:#8f1f1f; -fx-text-fill:white; -fx-font-weight:bold;"); }

        private ShapeKind randomShape(){ ShapeKind[] arr=ShapeKind.values(); return arr[rng.nextInt(arr.length)]; }
        private ColorKind randomColor(){ ColorKind[] arr=ColorKind.values(); return arr[rng.nextInt(arr.length)]; }

        private String nice(ShapeKind s){
            String n=s.name().toLowerCase();
            return Character.toUpperCase(n.charAt(0))+n.substring(1);
        }

        /* ===== Graphics ===== */
        private Node shapeGraphic(ShapeKind kind, double w, double h, Color fill){
            Shape s;
            switch (kind){
                case CIRCLE -> s = new Circle(Math.min(w,h)/3.0);
                case TRIANGLE -> {
                    Polygon p = new Polygon();
                    p.getPoints().addAll(w*0.5, h*0.15, w*0.15, h*0.85, w*0.85, h*0.85);
                    s = p;
                }
                case SQUARE -> s = new Rectangle(w*0.6, w*0.6);
                case RECTANGLE -> s = new Rectangle(w*0.8, h*0.5);
                case OVAL -> s = new Ellipse(w*0.32, h*0.22);
                case STAR -> {
                    Polygon star = new Polygon();
                    double cx=w*0.5, cy=h*0.5, r1=Math.min(w,h)*0.28, r2=r1*0.45;
                    for (int i=0;i<10;i++){
                        double ang = Math.toRadians(-90 + i*36);
                        double r = (i%2==0)? r1 : r2;
                        star.getPoints().addAll(cx + r*Math.cos(ang), cy + r*Math.sin(ang));
                    }
                    s = star;
                }
                case DIAMOND -> s = new Polygon(w*0.5, h*0.12, w*0.82, h*0.5, w*0.5, h*0.88, w*0.18, h*0.5);
                case PENTAGON -> s = regularPolygon(5, w, h);
                case HEXAGON -> s = regularPolygon(6, w, h);
                default -> s = new Rectangle(w*0.6, h*0.6);
            }
            s.setFill(fill);
            s.setStroke(Color.BLACK);
            s.setStrokeWidth(1.2);
            StackPane box = new StackPane(s);
            box.setMinSize(w,h); box.setPrefSize(w,h); box.setAlignment(Pos.CENTER);
            return box;
        }

        private Shape regularPolygon(int n, double w, double h){
            Polygon p = new Polygon();
            double cx=w*0.5, cy=h*0.5, r=Math.min(w,h)*0.32;
            for (int i=0;i<n;i++){
                double ang = Math.toRadians(-90 + i*360.0/n);
                p.getPoints().addAll(cx + r*Math.cos(ang), cy + r*Math.sin(ang));
            }
            return p;
        }

        private Node colorSwatch(double w, double h, Color c){
            Rectangle r = new Rectangle(w, h);
            r.setFill(c);
            r.setStroke(Color.BLACK);
            r.setStrokeWidth(1.0);
            StackPane box = new StackPane(r);
            box.setMinSize(w,h);
            box.setPrefSize(w,h);
            box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            return box;
        }

        /* ===== Voice Engine (inner) ===== */
        interface VoiceService { void speak(String text) throws Exception; }

        static class HybridVoiceService implements VoiceService {
            HybridVoiceService(boolean unused){}

            @Override public void speak(String text) throws Exception{
                String os = System.getProperty("os.name","").toLowerCase();
                if (os.contains("win")){
                    String safe = text.replace("'", "''");
                    String cmd = "$s=New-Object -ComObject SAPI.SpVoice; $s.Speak('" + safe + "');";
                    run(new String[]{"powershell","-NoProfile","-Command",cmd});
                } else if (os.contains("mac")){
                    run(new String[]{"say", text});
                } else {
                    run(new String[]{
                            "bash","-lc",
                            "(command -v espeak-ng >/dev/null && espeak-ng '" + text + "') || " +
                                    "(command -v espeak >/dev/null && espeak '" + text + "')"
                    });
                }
            }

            private static void run(String[] cmd) throws Exception{
                Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
                p.waitFor();
            }
        }
    }
