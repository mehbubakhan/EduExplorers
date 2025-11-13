package com.example.aoopproject;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
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
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.LinkedHashMap;
import java.util.Map;

public class EnglishWordMakingController {

    /* ===== FXML ===== */
    @FXML private MenuButton wordMenu;
    @FXML private ColorPicker colorPicker;
    @FXML private ToggleButton pencilToggle;
    @FXML private ToggleButton eraserToggle;
    @FXML private ProgressBar progressBar;
    @FXML private Label percentLabel;
    @FXML private StackPane canvasHolder;
    @FXML private Canvas gridCanvas;   // sizing layer (kept clear)
    @FXML private Canvas drawCanvas;   // paint layer
    @FXML private Button playVoiceButton;
    @FXML private Button retryButton;

    /* ===== State ===== */
    private boolean[][] fillMask;
    private boolean[][] painted;
    private int maskW, maskH;

    private String currentLetter = null;
    private String currentWords  = null;
    private String currentLine   = null;

    private double offX = 0.0, offY = 0.0;
    private boolean completedOnce = false; // controls congrats & enables Retry once

    // Brush & outline
    private static final double BRUSH_RADIUS  = 12.0;
    private static final double OUTLINE_WIDTH = 10.0;

    // Fonts and scale (vector outline via Text node)
    private static final Font BASE_FONT = Font.font("Arial", FontWeight.BOLD, 240);
    private double lineScale = 1.0;
    private Text outlineText = null;

    /* ===== Voice (kid-friendly pace) ===== */
    private static final boolean USE_FREETTS = false;
    private final VoiceService voice = new HybridVoiceService(USE_FREETTS);

    private static final Map<String, String> WORDS = new LinkedHashMap<>();
    static {
        WORDS.put("A","Apple, Ant");
        WORDS.put("B","Ball, Bat");
        WORDS.put("C","Cat, Cup");
        WORDS.put("D","Dog, Doll");
        WORDS.put("E","Egg, Elephant");
        WORDS.put("F","Frog, Fox");
        WORDS.put("G","Gun, Girl");
        WORDS.put("H","Horse, Hot");
        WORDS.put("I","Iron, Ice");
        WORDS.put("J","Juice, Jug");
        WORDS.put("K","Kite, Key");
        WORDS.put("L","Lemon, Light");
        WORDS.put("M","Mango, Man");
        WORDS.put("N","Nut, New");
        WORDS.put("O","Oil, Open");
        WORDS.put("P","Pet, Picture");
        WORDS.put("Q","Question, Queen");
        WORDS.put("R","Rat, Run");
        WORDS.put("S","Sad, Sugar");
        WORDS.put("T","Tomato, Team");
        WORDS.put("U","Under, Up");
        WORDS.put("V","Video, Voice");
        WORDS.put("W","White, Wall");
        WORDS.put("X","X-ray, Xerox");
        WORDS.put("Y","Yes, You");
        WORDS.put("Z","Zoo, Zero");
    }

    @FXML
    private void initialize() {
        // Menu items (chooser stays enabled after selecting)
        WORDS.forEach((letter, words) -> {
            String label = letter + " = " + words; // exactly this format
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

        // Drawing
        drawCanvas.addEventHandler(MouseEvent.MOUSE_PRESSED, this::handleDraw);
        drawCanvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::handleDraw);

        // toggle exclusivity
        pencilToggle.setOnAction(e -> eraserToggle.setSelected(false));
        eraserToggle.setOnAction(e -> pencilToggle.setSelected(false));

        playVoiceButton.setDisable(true);
        retryButton.setDisable(true); // enabled only after completion

        Platform.runLater(this::redrawOverlay);
        updateProgressUI();
    }

    @FXML
    void BackToEnglishFormat(ActionEvent event) {
        ChangeFxmlController.switchScene(event, "english-format.fxml");
    }

    /* ===== Select letter (menu REMAINS enabled) ===== */
    private void onLetterChosen(String letter, String words) {
        currentLetter = letter;
        currentWords  = words;
        // spaces around equals and comma (no trailing dot)
        currentLine   = letter + " = " + words.replace(",", " ,");

        completedOnce = false;
        wordMenu.setText(currentLine);

        clear(drawCanvas);

        // Build mask & create vector outline
        buildMask(currentLine);
        buildOrUpdateVectorOutline();

        // Voice
        playVoiceButton.setDisable(false);
        speakNow(letter + " for " + words.replace(",", " and"));

        // UI
        retryButton.setDisable(true);
        redrawOverlay();
        updateProgressUI();
    }

    /* ===== Build mask from scaled Text snapshot ===== */
    private void buildMask(String line) {
        double cw = gridCanvas.getWidth();
        double ch = gridCanvas.getHeight();

        Text tFill = new Text(line);
        tFill.setFont(BASE_FONT);

        var lb = tFill.getLayoutBounds();
        double margin = Math.min(cw, ch) * 0.10;
        double targetW = cw - 2 * margin;
        double targetH = ch - 2 * margin;

        lineScale = Math.min(
                targetW / Math.max(1, lb.getWidth()),
                targetH / Math.max(1, lb.getHeight())
        );
        tFill.setScaleX(lineScale);
        tFill.setScaleY(lineScale);

        Group gFill = new Group(tFill);
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);

        var bb = tFill.getBoundsInParent();
        WritableImage wi = new WritableImage(
                (int)Math.ceil(Math.max(1, bb.getWidth())),
                (int)Math.ceil(Math.max(1, bb.getHeight()))
        );
        gFill.snapshot(sp, wi);

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
    }

    /* ===== Vector outline (Text node) ===== */
    private void buildOrUpdateVectorOutline() {
        if (outlineText != null) {
            canvasHolder.getChildren().remove(outlineText);
        }
        outlineText = new Text(currentLine);
        outlineText.setFont(BASE_FONT);
        outlineText.setScaleX(lineScale);
        outlineText.setScaleY(lineScale);
        outlineText.setFill(Color.TRANSPARENT);
        outlineText.setStroke(Color.BLACK);
        outlineText.setStrokeWidth(OUTLINE_WIDTH);
        outlineText.setStrokeLineJoin(StrokeLineJoin.ROUND);
        outlineText.setStrokeLineCap(StrokeLineCap.ROUND);
        outlineText.setMouseTransparent(true);
        outlineText.setManaged(false);

        canvasHolder.getChildren().add(outlineText);
        outlineText.toFront();
    }

    /** Baseline-correct centering using minX/minY. */
    private void positionOutline() {
        if (outlineText == null) return;

        // Reset before measuring:
        outlineText.setTranslateX(0);
        outlineText.setTranslateY(0);

        Bounds b = outlineText.getBoundsInParent();
        double adjustX = -b.getMinX();
        double adjustY = -b.getMinY();

        outlineText.setTranslateX(offX + adjustX);
        outlineText.setTranslateY(offY + adjustY);
        outlineText.toFront();
    }

    /* ===== Overlay ===== */
    private void redrawOverlay() {
        clear(gridCanvas);
        if (fillMask == null) return;

        offX = (gridCanvas.getWidth()  - maskW) / 2.0;
        offY = (gridCanvas.getHeight() - maskH) / 2.0;

        positionOutline();
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

        // Enable Retry once completed, and congrats exactly once
        if (!completedOnce && isComplete()) {
            completedOnce = true;
            progressBar.setProgress(1.0);
            percentLabel.setText("100%");
            speakAndCongrats();
            retryButton.setDisable(false);
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
        percentLabel.setTooltip(new Tooltip("Filled: " + (int)Math.round(cov*100) + "%"));
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

    /* ===== Voice ===== */
    private void speakAndCongrats() {
        if (currentLetter == null) return;
        speakNow(currentLetter + " for " + currentWords.replace(",", " and"));
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Great Job!");
        alert.setHeaderText("Congratulations! 🙂");
        alert.setContentText("You finished coloring: " + currentLine);
        alert.showAndWait();
    }

    @FXML
    private void handlePlayVoice() {
        if (currentLetter != null && currentWords != null)
            speakNow(currentLetter + " for " + currentWords.replace(",", " and"));
    }

    private void speakNow(String text) {
        new Thread(() -> {
            try { voice.speak(text); }
            catch (Exception ex) { System.err.println("[TTS] Failed: " + ex.getMessage()); }
        }, "tts-thread").start();
    }

    /* ===== Retry (same behavior as lowercase/uppercase) ===== */
    private void resetToBasePosition() {
        if (currentLine == null) return;

        clear(drawCanvas);

        // rebuild mask & outline from the same line (recomputes scale/centering)
        buildMask(currentLine);
        buildOrUpdateVectorOutline();
        redrawOverlay();

        completedOnce = false;   // allow congratulation again
        retryButton.setDisable(true);
        updateProgressUI();
    }

    @FXML
    private void handleRetry() {
        resetToBasePosition();
    }

    /* ===== Voice helper ===== */
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
                    try { v.getClass().getMethod("setRate", float.class).invoke(v, 100f); } catch (NoSuchMethodException ignored) {}
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
                        "(command -v espeak-ng >/dev/null && espeak-ng -s 130 '" + text + "') || " +
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
