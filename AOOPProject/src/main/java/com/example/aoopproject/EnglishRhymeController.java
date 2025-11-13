package com.example.aoopproject;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.*;
import javafx.util.Duration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EnglishRhymeController {

    /* ====== FXML ====== */
    @FXML private AnchorPane root;
    @FXML private Button backButton;
    @FXML private MenuButton chooseRhymeMenu;
    @FXML private Button playButton;
    @FXML private Button pauseButton;
    @FXML private Button resumeButton;
    @FXML private Button replayButton;
    @FXML private Label nowPlayingLabel;
    @FXML private TextFlow lyricsFlow;

    /* ====== Player / sync state ====== */
    private MediaPlayer mediaPlayer;
    private Rhyme current;

    private final List<WordToken> tokens = new ArrayList<>();
    private final List<Text> tokenNodes = new ArrayList<>();
    private AnimationTimer timer;
    private int lastColoredIndex = -1;

    // Colors
    private static final Color COLOR_IDLE_FILL   = Color.WHITE;
    private static final Color COLOR_OUTLINE     = Color.BLACK;
    private static final Color COLOR_ACTIVE_FILL = Color.PURPLE;

    private static final String BASE_PATH = "/com/example/aoopproject/EnglishRhyme";

    private enum Rhyme {
        BAA_BAA("Baa Baa Black Sheep", "Baa Baa Black Sheep.mp3", "Baa Baa Black Sheep.lrc"),
        HUMPTY("Humpty Dumpty", "Humpty Dumpty.mp3", "Humpty Dumpty.lrc"),
        RAIN_RAIN("Rain Rain Go Away", "Rain Rain go away.mp3", "Rain Rain Go Away.lrc"),
        WHEELS("The Wheels on the Bus", "The wheels on the bus .mp3", "The Wheels on the Bus.lrc"),
        TWINKLE("Twinkle Twinkle Little Star", "Twinkle Twinkle Little Star .mp3", "Twinkle Twinkle Little Star.lrc");

        final String title, mp3, lrc;
        Rhyme(String title, String mp3, String lrc) { this.title = title; this.mp3 = mp3; this.lrc = lrc; }
    }

    @FXML
    private void initialize() {
        Map<String, Rhyme> items = new LinkedHashMap<>();
        items.put(Rhyme.BAA_BAA.title, Rhyme.BAA_BAA);
        items.put(Rhyme.HUMPTY.title, Rhyme.HUMPTY);
        items.put(Rhyme.RAIN_RAIN.title, Rhyme.RAIN_RAIN);
        items.put(Rhyme.WHEELS.title, Rhyme.WHEELS);
        items.put(Rhyme.TWINKLE.title, Rhyme.TWINKLE);

        items.forEach((title, rhyme) -> {
            MenuItem mi = new MenuItem(title);
            mi.setOnAction(e -> onChooseRhyme(rhyme));
            chooseRhymeMenu.getItems().add(mi);
        });

        setTransportEnabled(false);
        nowPlayingLabel.setText("Now Playing: —");
        showPrompt("Choose a rhyme to begin!");

        timer = new AnimationTimer() {
            @Override public void handle(long now) {
                if (mediaPlayer == null) return;
                updateHighlight(mediaPlayer.getCurrentTime().toMillis());
            }
        };
    }

    /* ===== Navigation ===== */
    @FXML
    void BackToEnglishFormat(ActionEvent event) {
        stopAndDispose();
        ChangeFxmlController.switchScene(event, "english-format.fxml");
    }
    @FXML
    private void switchToRhyme(ActionEvent event) {
        ChangeFxmlController.switchScene(event, "english-rhyme.fxml");
    }

    /* ===== Choose rhyme ===== */
    private void onChooseRhyme(Rhyme rhyme) {
        current = rhyme;
        chooseRhymeMenu.setText("Choose Rhyme: " + rhyme.title);
        nowPlayingLabel.setText("Now Playing: " + rhyme.title);

        String lrcRes = BASE_PATH + "/" + rhyme.lrc;
        try {
            List<LrcLine> lines = parseLrcFromClasspath(lrcRes);
            buildWordTokens(lines);
            renderTokens();
        } catch (IOException e) {
            tokens.clear();
            tokenNodes.clear();
            lyricsFlow.getChildren().clear();
            showPrompt("Lyrics not found.\n(" + lrcRes + ")");
        }

        String mp3Res = BASE_PATH + "/" + rhyme.mp3;
        MediaPlayer mp = buildPlayerFromClasspath(mp3Res);
        if (mp == null) {
            setTransportEnabled(false);
            return;
        }
        stopAndDispose();
        mediaPlayer = mp;

        mediaPlayer.setOnReady(() -> {
            double totalMs = mediaPlayer.getMedia().getDuration().toMillis();
            finalizeTokenEnds(totalMs);
        });
        mediaPlayer.setOnEndOfMedia(() -> replayButton.setDisable(false));

        setTransportEnabled(true);
        resetColors();
    }

    /* ===== Transport ===== */
    @FXML private void handlePlay() {
        if (mediaPlayer != null) {
            resetColors();
            mediaPlayer.seek(Duration.ZERO);
            mediaPlayer.play();
            timer.start();
        }
    }
    @FXML private void handlePause()  { if (mediaPlayer != null) { mediaPlayer.pause(); timer.stop(); } }
    @FXML private void handleResume() { if (mediaPlayer != null) { mediaPlayer.play();  timer.start(); } }
    @FXML private void handleReplay() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.seek(Duration.ZERO);
            resetColors();
            mediaPlayer.play();
            timer.start();
        }
    }

    private void setTransportEnabled(boolean enabled) {
        playButton.setDisable(!enabled);
        pauseButton.setDisable(!enabled);
        resumeButton.setDisable(!enabled);
        replayButton.setDisable(!enabled);
    }

    private void stopAndDispose() {
        if (mediaPlayer != null) {
            try { timer.stop(); mediaPlayer.stop(); mediaPlayer.dispose(); } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    /* ===== Error dialog ===== */
    private void showError(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
            a.setTitle("Playback Error");
            a.setHeaderText(null);
            a.showAndWait();
        });
    }

    /* ===== Lyrics rendering ===== */
    private void showPrompt(String msg) {
        lyricsFlow.getChildren().clear();
        Text t = makeStyledText(msg);
        t.setFill(COLOR_IDLE_FILL);
        lyricsFlow.getChildren().add(t);
    }

    private void resetColors() {
        for (Text t : tokenNodes) t.setFill(COLOR_IDLE_FILL);
        lastColoredIndex = -1;
    }

    private Text makeStyledText(String content) {
        Text text = new Text(content);
        text.setFont(Font.font("System", FontWeight.BOLD, 32)); // smaller
        text.setStroke(COLOR_OUTLINE);
        text.setStrokeWidth(2.5); // border line = 2.5 (your request)
        text.setStrokeType(StrokeType.OUTSIDE);
        text.setFill(COLOR_IDLE_FILL);
        return text;
    }

    private void renderTokens() {
        tokenNodes.clear();
        lyricsFlow.getChildren().clear();
        for (WordToken tok : tokens) {
            Text text = makeStyledText(tok.text);
            tokenNodes.add(text);
            lyricsFlow.getChildren().add(text);
        }
    }

    private void updateHighlight(double currentMs) {
        int i = Math.max(0, lastColoredIndex);
        while (i + 1 < tokens.size() && tokens.get(i + 1).startMs <= currentMs) i++;
        for (int j = lastColoredIndex + 1; j <= i && j >= 0; j++)
            tokenNodes.get(j).setFill(COLOR_ACTIVE_FILL);
        lastColoredIndex = i;
        for (int k = i + 1; k < tokenNodes.size(); k++)
            tokenNodes.get(k).setFill(COLOR_IDLE_FILL);
    }

    private void finalizeTokenEnds(double mediaTotalMs) {
        for (int i = 0; i < tokens.size(); i++) {
            WordToken t = tokens.get(i);
            if (Double.isNaN(t.endMs))
                t.endMs = (i + 1 < tokens.size()) ? tokens.get(i + 1).startMs : mediaTotalMs;
        }
    }

    /* ===== LRC parsing ===== */
    private static final Pattern LINE_TS = Pattern.compile("^\\s*\\[(\\d{2}):(\\d{2}\\.\\d{2,3})]\\s*(.*)$");
    private static final Pattern WORD_TS = Pattern.compile("<(\\d{2}):(\\d{2}\\.\\d{2,3})>");

    private static class LrcLine {
        double startMs; String raw;
        LrcLine(double startMs, String raw) { this.startMs = startMs; this.raw = raw; }
    }
    private static class WordToken {
        String text; double startMs; double endMs = Double.NaN;
        WordToken(String text, double startMs) { this.text = text; this.startMs = startMs; }
    }

    private List<LrcLine> parseLrcFromClasspath(String resourcePath) throws IOException {
        URL url = getClass().getResource(resourcePath);
        if (url == null) throw new IOException("LRC not found: " + resourcePath);
        List<LrcLine> lines = new ArrayList<>();
        try (var is = getClass().getResourceAsStream(resourcePath);
             var br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = LINE_TS.matcher(line);
                if (m.find()) {
                    double ms = toMs(m.group(1), m.group(2));
                    String rest = m.group(3);
                    lines.add(new LrcLine(ms, rest));
                }
            }
        }
        lines.sort(Comparator.comparingDouble(l -> l.startMs));
        return lines;
    }

    private void buildWordTokens(List<LrcLine> lines) {
        tokens.clear();
        for (int i = 0; i < lines.size(); i++) {
            LrcLine line = lines.get(i);
            double lineStart = line.startMs;
            double lineEnd = (i + 1 < lines.size()) ? lines.get(i + 1).startMs : Double.NaN;

            Matcher wm = WORD_TS.matcher(line.raw);
            List<Double> tagTimes = new ArrayList<>();
            while (wm.find()) tagTimes.add(toMs(wm.group(1), wm.group(2)));

            if (!tagTimes.isEmpty()) {
                String cleaned = WORD_TS.matcher(line.raw).replaceAll("");
                List<String> pieces = splitKeepDelims(cleaned);
                int wIndex = 0;
                for (String p : pieces) {
                    double st = (wIndex < tagTimes.size())
                            ? tagTimes.get(wIndex)
                            : fallbackTime(lineStart, lineEnd, wIndex);
                    tokens.add(new WordToken(p, st));
                    if (!p.trim().isEmpty()) wIndex++;
                }
            } else {
                List<String> pieces = splitKeepDelims(line.raw);
                int wordCount = (int) pieces.stream().filter(s -> !s.trim().isEmpty()).count();
                if (wordCount == 0) tokens.add(new WordToken("\n", lineStart));
                else {
                    double span = Double.isNaN(lineEnd) ? 800 : Math.max(400, lineEnd - lineStart);
                    double step = span / wordCount;
                    int wIndex = 0;
                    for (String p : pieces) {
                        tokens.add(new WordToken(p, lineStart + step * wIndex));
                        if (!p.trim().isEmpty()) wIndex++;
                    }
                }
            }
            tokens.add(new WordToken("\n", Double.isNaN(lineEnd) ? lineStart + 50 : lineEnd));
        }
    }

    private static List<String> splitKeepDelims(String s) {
        List<String> out = new ArrayList<>();
        if (s.isEmpty()) return out;
        StringBuilder buf = new StringBuilder();
        boolean lastSpace = Character.isWhitespace(s.charAt(0));
        for (char c : s.toCharArray()) {
            boolean space = Character.isWhitespace(c);
            if (space == lastSpace) buf.append(c);
            else { out.add(buf.toString()); buf.setLength(0); buf.append(c); lastSpace = space; }
        }
        if (buf.length() > 0) out.add(buf.toString());
        return out;
    }

    private static double toMs(String mm, String ss) {
        int minutes = Integer.parseInt(mm);
        double secFrac = Double.parseDouble(ss);
        return minutes * 60_000 + secFrac * 1000.0;
    }

    private static double fallbackTime(double startMs, double endMs, int idx) {
        if (Double.isNaN(endMs)) return startMs + 50 * idx;
        double span = Math.max(400, endMs - startMs);
        return startMs + (span / Math.max(1, idx + 1)) * idx;
    }

    private MediaPlayer buildPlayerFromClasspath(String resourcePath) {
        try {
            var url = getClass().getResource(resourcePath);
            if (url == null) {
                showError("MP3 not found: " + resourcePath);
                return null;
            }
            Media media = new Media(url.toExternalForm());
            MediaPlayer player = new MediaPlayer(media);
            player.setAutoPlay(false);
            return player;
        } catch (Exception ex) {
            showError("Failed to initialize player: " + ex.getMessage());
            return null;
        }
    }
}
