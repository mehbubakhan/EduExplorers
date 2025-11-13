package com.example.aoopproject;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

public class BanglaShorobornoWordController {

    /* ===== FXML ===== */
    @FXML private MenuButton wordMenu;
    @FXML private ColorPicker colorPicker;
    @FXML private ToggleButton pencilToggle;
    @FXML private ToggleButton eraserToggle;
    @FXML private ProgressBar progressBar;
    @FXML private Label percentLabel;
    @FXML private StackPane canvasHolder;
    @FXML private Canvas gridCanvas;   // border overlay
    @FXML private Canvas drawCanvas;   // paint layer
    @FXML private Button playVoiceButton;
    @FXML private Button retryButton;  // NEW

    /* ===== State ===== */
    private boolean[][] fillMask;
    private boolean[][] painted;
    private int maskW, maskH;

    private String currentLetter = null;
    private String currentWords  = null;
    private String currentLine   = null;

    private double offX = 0.0, offY = 0.0;
    private boolean completedOnce = false; // show congrats once

    // Brush & border
    private static final double BRUSH_RADIUS  = 12.0;
    private static final double OUTLINE_WIDTH = 7.0;

    // Bangla-friendly fonts
    private static final Font LINE_FONT    = bestBanglaFont(240);
    private static final Font OUTLINE_FONT = bestBanglaFont(240);

    // Pre-rendered outline image for crisp border
    private WritableImage outlineImage;

    /* ===== Audio (matches your resources tree) ===== */
    // Folder shown in your screenshot: resources/com.example.aoopproject/shorobornoword
    // Files named like: অword.mp3, আword.mp3, ইword.mp3 ...
    private static final String AUDIO_BASE = "/com/example/aoopproject/shorobornoword";
    private MediaPlayer mediaPlayer;

    /* ===== (Optional) TTS fallback kept but not used by default ===== */
    private static final boolean USE_FREETTS = false;
    private final VoiceService voice = new HybridVoiceService(USE_FREETTS);

    private static Font bestBanglaFont(double size) {
        String[] families = {
                "Nirmala UI", "Vrinda", "Noto Sans Bengali", "SolaimanLipi",
                "Siyam Rupali", "Arial Unicode MS", "SansSerif"
        };
        for (String fam : families) {
            try { return Font.font(fam, FontWeight.BOLD, size); }
            catch (Exception ignored) {}
        }
        return Font.font("SansSerif", FontWeight.BOLD, size);
    }

    private static final Map<String, String> WORDS = new LinkedHashMap<>();
    static {
        WORDS.put("অ", "অজগর, অলি");
        WORDS.put("আ", "আম, আকাশ");
        WORDS.put("ই", "ইলিশ, ইট");
        WORDS.put("ঈ", "ঈদ, ঈগল");
        WORDS.put("উ", "উট, উঠান");
        WORDS.put("ঊ", "ঊষা, ঊর্মি");
        WORDS.put("ঋ", "ঋষি, ঋণ");
        WORDS.put("এ", "এক, একা");
        WORDS.put("ঐ", "ঐরাবত, ঐক্য");
        WORDS.put("ও", "ওজন, ওষুধ");
        WORDS.put("ঔ", "ঔষধ, ঔষধি");
    }

    @FXML
    private void initialize() {
        // Menu: keep enabled after selecting
        WORDS.forEach((letter, words) -> {
            String label = letter + " = " + words;
            MenuItem mi = new MenuItem(label);
            mi.setOnAction(e -> onLetterChosen(letter, words));
            wordMenu.getItems().add(mi);
        });

        // Tools
        ToggleGroup tg = new ToggleGroup();
        pencilToggle.setToggleGroup(tg);
        eraserToggle.setToggleGroup(tg);
        pencilToggle.setSelected(true);
        colorPicker.setValue(Color.DODGERBLUE);

        // Canvas sizing
        gridCanvas.widthProperty().bind(canvasHolder.widthProperty());
        gridCanvas.heightProperty().bind(canvasHolder.heightProperty());
        drawCanvas.widthProperty().bind(canvasHolder.widthProperty());
        drawCanvas.heightProperty().bind(canvasHolder.heightProperty());

        gridCanvas.widthProperty().addListener((o,a,b) -> redrawOverlay());
        gridCanvas.heightProperty().addListener((o,a,b) -> redrawOverlay());
        gridCanvas.setMouseTransparent(true);

        // Drawing (with strict mask)
        drawCanvas.addEventHandler(MouseEvent.MOUSE_PRESSED, this::handleDraw);
        drawCanvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::handleDraw);

        // toggle exclusivity
        pencilToggle.setOnAction(e -> eraserToggle.setSelected(false));
        eraserToggle.setOnAction(e -> pencilToggle.setSelected(false));

        playVoiceButton.setDisable(true);

        // Retry starts disabled
        if (retryButton != null) {
            retryButton.setDisable(true);
            retryButton.setTooltip(new Tooltip("সম্পূর্ণ হলে আবার শুরু করতে পারবেন"));
        }

        Platform.runLater(this::redrawOverlay);
        updateProgressUI();
    }

    @FXML
    void BackToBanglaFormat(ActionEvent event) {
        stopMediaOnly(); // ensure player disposed
        ChangeFxmlController.switchScene(event,"bangla-format.fxml");
    }

    /* ===== Select letter (also plays the letter’s word audio) ===== */
    private void onLetterChosen(String letter, String words) {
        currentLetter = letter;
        currentWords  = words;
        currentLine   = letter + " = " + words.replace(",", " ,");

        completedOnce = false;
        wordMenu.setText(currentLine);

        clear(drawCanvas);
        buildMaskAndOutline(currentLine);

        playVoiceButton.setDisable(false);
        retryButton.setDisable(true);

        // >>> Play the audio like “অword.mp3”
        playWordMp3(letter, null);

        redrawOverlay();
        updateProgressUI();
    }

    /* ===== Build mask (filled) + outline (stroked) ===== */
    private void buildMaskAndOutline(String line) {
        double cw = gridCanvas.getWidth();
        double ch = gridCanvas.getHeight();

        // Filled text (mask)
        Text tFill = new Text(line);
        tFill.setFont(LINE_FONT);

        var lb = tFill.getLayoutBounds();
        double margin = Math.min(cw, ch) * 0.10;
        double targetW = cw - 2 * margin;
        double targetH = ch - 2 * margin;
        double scale = Math.min(
                targetW / Math.max(1, lb.getWidth()),
                targetH / Math.max(1, lb.getHeight())
        );
        tFill.setScaleX(scale);
        tFill.setScaleY(scale);

        Group gFill = new Group(tFill);
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);

        var bb = gFill.getBoundsInParent();
        WritableImage wi = new WritableImage(
                (int)Math.ceil(Math.max(1, bb.getWidth())),
                (int)Math.ceil(Math.max(1, bb.getHeight()))
        );
        gFill.snapshot(sp, wi);

        // Build mask
        PixelReader pr = wi.getPixelReader();
        maskW = (int) wi.getWidth();
        maskH = (int) wi.getHeight();
        fillMask = new boolean[maskH][maskW];
        painted  = new boolean[maskH][maskW];

        for (int y = 0; y < maskH; y++) {
            for (int x = 0; x < maskW; x++) {
                boolean inside = pr.getColor(x, y).getOpacity() > 0.05;
                fillMask[y][x] = inside;
                painted[y][x]  = false;
            }
        }

        // Stroked text (crisp border image)
        Text tStroke = new Text(line);
        tStroke.setFont(OUTLINE_FONT);
        tStroke.setScaleX(scale);
        tStroke.setScaleY(scale);
        tStroke.setFill(Color.TRANSPARENT);
        tStroke.setStroke(Color.BLACK);
        tStroke.setStrokeWidth(OUTLINE_WIDTH);
        tStroke.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        tStroke.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);

        Group gStroke = new Group(tStroke);
        outlineImage = new WritableImage(maskW, maskH);
        gStroke.snapshot(sp, outlineImage);
    }

    /* ===== Overlay border ===== */
    private void redrawOverlay() {
        clear(gridCanvas);
        if (fillMask == null) return;

        offX = (gridCanvas.getWidth()  - maskW) / 2.0;
        offY = (gridCanvas.getHeight() - maskH) / 2.0;

        if (outlineImage != null) {
            gridCanvas.getGraphicsContext2D().drawImage(outlineImage, offX, offY);
        }
        gridCanvas.toFront();
    }

    /* ===== Painting (strictly inside mask) ===== */
    private void handleDraw(MouseEvent e) {
        if (currentLine == null || fillMask == null) return;
        if (e.getButton() != MouseButton.PRIMARY && e.getButton() != MouseButton.SECONDARY) return;

        double cx = e.getX(), cy = e.getY();
        if (!isInsideMask(cx, cy)) return;

        boolean useEraser = eraserToggle.isSelected() || e.getButton() == MouseButton.SECONDARY;

        if (useEraser) maskedEraseCircle(cx, cy);
        else           maskedPaintCircle(cx, cy, colorPicker.getValue());

        double cov = coverage();
        progressBar.setProgress(cov);
        percentLabel.setText((int)Math.round(cov*100) + "%");

        if (!completedOnce && isComplete()) {
            completedOnce = true;
            progressBar.setProgress(1.0);
            percentLabel.setText("100%");
            // Optional: play the word audio again on completion
            playWordMp3(currentLetter, () -> Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("দারুণ!");
                alert.setHeaderText("অভিনন্দন! 🙂");
                alert.setContentText("তুমি রঙ করা শেষ করেছ: " + currentLine);
                alert.showAndWait();
                retryButton.setDisable(false);
            }));
        }
    }

    private boolean isInsideMask(double cx, double cy) {
        int mx = (int) Math.floor(cx - offX);
        int my = (int) Math.floor(cy - offY);
        return !(mx < 0 || my < 0 || mx >= maskW || my >= maskH) && fillMask[my][mx];
    }

    private void maskedPaintCircle(double cx, double cy, Color color) {
        GraphicsContext g = drawCanvas.getGraphicsContext2D();
        g.setFill(color);
        int minX = (int) Math.floor(cx - BRUSH_RADIUS - offX);
        int minY = (int) Math.floor(cy - BRUSH_RADIUS - offY);
        int maxX = (int) Math.ceil (cx + BRUSH_RADIUS - offX);
        int maxY = (int) Math.ceil (cy + BRUSH_RADIUS - offY);
        double r2 = BRUSH_RADIUS * BRUSH_RADIUS;

        for (int y = Math.max(0, minY); y <= Math.min(maskH - 1, maxY); y++) {
            for (int x = Math.max(0, minX); x <= Math.min(maskW - 1, maxX); x++) {
                if (!fillMask[y][x]) continue;
                double px = offX + x + 0.5, py = offY + y + 0.5;
                double dx = px - cx, dy = py - cy;
                if (dx*dx + dy*dy <= r2) {
                    g.fillRect(offX + x, offY + y, 1, 1);
                    painted[y][x] = true;
                }
            }
        }
    }

    private void maskedEraseCircle(double cx, double cy) {
        GraphicsContext g = drawCanvas.getGraphicsContext2D();
        int minX = (int) Math.floor(cx - BRUSH_RADIUS - offX);
        int minY = (int) Math.floor(cy - BRUSH_RADIUS - offY);
        int maxX = (int) Math.ceil (cx + BRUSH_RADIUS - offX);
        int maxY = (int) Math.ceil (cy + BRUSH_RADIUS - offY);
        double r2 = BRUSH_RADIUS * BRUSH_RADIUS;

        for (int y = Math.max(0, minY); y <= Math.min(maskH - 1, maxY); y++) {
            for (int x = Math.max(0, minX); x <= Math.min(maskW - 1, maxX); x++) {
                if (!fillMask[y][x]) continue;
                double px = offX + x + 0.5, py = offY + y + 0.5;
                double dx = px - cx, dy = py - cy;
                if (dx*dx + dy*dy <= r2) {
                    g.clearRect(offX + x, offY + y, 1, 1);
                    painted[y][x] = false;
                }
            }
        }
        updateProgressUI();
    }

    /* ===== Progress & completion ===== */
    private void updateProgressUI() {
        double cov = coverage();
        progressBar.setProgress(cov);
        percentLabel.setText((int)Math.round(cov*100) + "%");
    }

    private double coverage() {
        if (fillMask == null || painted == null) return 0;
        long total = 0, done = 0;
        for (int y = 0; y < maskH; y++)
            for (int x = 0; x < maskW; x++)
                if (fillMask[y][x]) { total++; if (painted[y][x]) done++; }
        return total == 0 ? 0 : (double) done / total;
    }

    private boolean isComplete() {
        if (fillMask == null || painted == null) return false;
        for (int y = 0; y < maskH; y++)
            for (int x = 0; x < maskW; x++)
                if (fillMask[y][x] && !painted[y][x]) return false;
        return true;
    }

    /* ===== Listen (ভয়েস চালাও) — plays the same file again ===== */
    @FXML
    private void handlePlayVoice() {
        if (currentLetter != null) {
            playWordMp3(currentLetter, null);
        }
    }

    /* ===== Retry ===== */
    private void resetToBasePosition() {
        if (currentLine == null) return;

        clear(drawCanvas);
        buildMaskAndOutline(currentLine);
        redrawOverlay();

        completedOnce = false;
        retryButton.setDisable(true);
        updateProgressUI();
    }

    @FXML
    private void handleRetry() {
        resetToBasePosition();
    }

    /* ===== Audio helpers ===== */

    private void playWordMp3(String letter, Runnable onEnd) {
        stopMediaOnly();

        String resourcePath = AUDIO_BASE + "/" + (letter + "word.mp3");
        MediaPlayer mp = buildPlayerFromClasspath(resourcePath);
        if (mp == null) {
            // Optional: fallback to TTS
            // speakNow(letter + " এর জন্য " + (currentWords != null ? currentWords.replace(",", " এবং") : ""));
            return;
        }
        mediaPlayer = mp;

        if (onEnd != null) mediaPlayer.setOnEndOfMedia(onEnd);
        mediaPlayer.setOnError(() -> {
            System.err.println("[MP3] Error: " + mediaPlayer.getError());
            if (onEnd != null) onEnd.run();
        });

        mediaPlayer.play();
    }

    private MediaPlayer buildPlayerFromClasspath(String resourcePath) {
        try {
            URL url = getClass().getResource(resourcePath);
            if (url == null) {
                System.err.println("[MP3] Not found: " + resourcePath);
                showAudioAlert("অডিও পাওয়া যায়নি:\n" + resourcePath);
                return null;
            }
            Media media = new Media(url.toExternalForm());
            MediaPlayer player = new MediaPlayer(media);
            player.setAutoPlay(false);
            return player;
        } catch (Exception ex) {
            System.err.println("[MP3] Failed: " + ex.getMessage());
            showAudioAlert("অডিও চালু করা যায়নি:\n" + ex.getMessage());
            return null;
        }
    }

    private void stopMediaOnly() {
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); mediaPlayer.dispose(); } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    private void showAudioAlert(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
            a.setTitle("Audio");
            a.setHeaderText(null);
            a.showAndWait();
        });
    }

    /* ===== Optional TTS (kept for fallback) ===== */
    private void speakNow(String text) {
        new Thread(() -> {
            try { voice.speak(text); }
            catch (Exception ex) { System.err.println("[TTS] Failed: " + ex.getMessage()); }
        }, "tts-thread").start();
    }

    interface VoiceService { void speak(String text) throws Exception; }
    static class HybridVoiceService implements VoiceService {
        private final boolean tryFreeTts;
        HybridVoiceService(boolean tryFreeTts) { this.tryFreeTts = tryFreeTts; }

        @Override public void speak(String text) throws Exception {
            Exception last = null;
            if (tryFreeTts) {
                try {
                    Class<?> vmClass = Class.forName("com.sun.speech.freetts.VoiceManager");
                    Object vm = vmClass.getMethod("getInstance").invoke(null);
                    Object v  = vmClass.getMethod("getVoice", String.class).invoke(vm, "kevin16");
                    if (v == null) throw new IllegalStateException("FreeTTS voice 'kevin16' not found.");
                    v.getClass().getMethod("allocate").invoke(v);
                    try { v.getClass().getMethod("setRate", float.class).invoke(v, 110f); } catch (NoSuchMethodException ignored) {}
                    v.getClass().getMethod("speak", String.class).invoke(v, text);
                    v.getClass().getMethod("deallocate").invoke(v);
                    return;
                } catch (Exception e) { last = e; }
            }
            String os = System.getProperty("os.name","").toLowerCase();
            if (os.contains("mac")) {
                runAndWait(new String[]{"say","-r","120", text}); return;
            } else if (os.contains("win")) {
                runAndWait(new String[]{"powershell","-NoProfile","-Command",
                        "$s=New-Object -ComObject SAPI.SpVoice; $s.Rate=-4; $s.Speak('"
                                + text.replace("'", "''") + "');"}); return;
            } else if (os.contains("linux")) {
                runAndWait(new String[]{"bash","-lc",
                        "(command -v espeak-ng >/dev/null && espeak-ng -v bn -s 130 '" + text + "') || " +
                                "(command -v espeak >/dev/null && espeak -s 130 '" + text + "')"}); return;
            }
            if (last != null) throw last;
            throw new IllegalStateException("No TTS available.");
        }
        private static void runAndWait(String[] cmd) throws Exception {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.waitFor();
        }
    }

    /* ===== Utils ===== */
    private void clear(Canvas c) { c.getGraphicsContext2D().clearRect(0, 0, c.getWidth(), c.getHeight()); }
}
