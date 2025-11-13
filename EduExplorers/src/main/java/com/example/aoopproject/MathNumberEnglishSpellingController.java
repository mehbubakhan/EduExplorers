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

public class MathNumberEnglishSpellingController {

    /* ===== FXML ===== */
    @FXML private MenuButton numberMenu;
    @FXML private ColorPicker colorPicker;
    @FXML private ToggleButton pencilToggle;
    @FXML private ToggleButton eraserToggle;
    @FXML private ProgressBar progressBar;
    @FXML private Label percentLabel;
    @FXML private Label spellingLabel;     // optional toolbar display like "6 - Six"
    @FXML private StackPane canvasHolder;
    @FXML private Canvas gridCanvas;       // outline/z-order
    @FXML private Canvas drawCanvas;       // paint layer
    @FXML private Button playVoiceButton;
    @FXML private Button retryButton;      // NEW

    /* ===== State ===== */
    private boolean[][] fillMask, painted;
    private int maskW, maskH;

    private boolean inProgress = false;
    private String currentDigits    = null; // e.g. "6"
    private String currentSpelling  = null; // e.g. "six"
    private String currentRenderTxt = null; // e.g. "6 - Six"  <-- what we draw & color

    private static final double BRUSH_RADIUS  = 12.0;
    private static final double OUTLINE_WIDTH = 8.0;

    private double offX = 0.0, offY = 0.0;

    private static final Font BASE_FONT = Font.font("Arial", FontWeight.EXTRA_BOLD, 320);
    private double textScale = 1.0;

    private Text outlineText = null;

    /* ===== Voice ===== */
    private static final boolean USE_FREETTS = false;
    private final VoiceService voice = new HybridVoiceService(USE_FREETTS);

    @FXML
    private void initialize() {
        // Build menu "n - words" 1..100
        for (int i = 1; i <= 100; i++) {
            final String digits = String.valueOf(i);
            final String words  = WORDS[i];
            final String label  = digits + " - " + words;
            MenuItem mi = new MenuItem(label);
            mi.setOnAction(e -> onNumberChosen(digits, words));
            numberMenu.getItems().add(mi);
        }

        ToggleGroup tg = new ToggleGroup();
        pencilToggle.setToggleGroup(tg);
        eraserToggle.setToggleGroup(tg);
        pencilToggle.setSelected(true);
        colorPicker.setValue(Color.DODGERBLUE);

        gridCanvas.widthProperty().bind(canvasHolder.widthProperty());
        gridCanvas.heightProperty().bind(canvasHolder.heightProperty());
        drawCanvas.widthProperty().bind(canvasHolder.widthProperty());
        drawCanvas.heightProperty().bind(canvasHolder.heightProperty());

        gridCanvas.widthProperty().addListener((o,a,b) -> redrawOverlay());
        gridCanvas.heightProperty().addListener((o,a,b) -> redrawOverlay());
        gridCanvas.setMouseTransparent(true);

        drawCanvas.addEventHandler(MouseEvent.MOUSE_PRESSED, this::handleDraw);
        drawCanvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::handleDraw);

        playVoiceButton.setDisable(true);
        if (spellingLabel != null) spellingLabel.setText("");

        // NEW: Retry starts disabled
        if (retryButton != null) {
            retryButton.setDisable(true);
            retryButton.setTooltip(new Tooltip("Finish coloring to enable retry"));
        }

        Platform.runLater(this::redrawOverlay);
        updateLocksAndProgress();
    }

    @FXML
    void BackToMathFormat(ActionEvent event) {
        ChangeFxmlController.switchScene(event, "math-format.fxml");
    }

    /* ===== Menu selection ===== */
    private void onNumberChosen(String digits, String words) {
        if (inProgress) return;

        currentDigits   = digits;
        currentSpelling = words;

        // EXACT FORMAT: number ␠-␠ Spelling (e.g., "6 - Six")
        currentRenderTxt = digits + " - " + capitalize(words);

        numberMenu.setText("Number: " + digits + " - " + words);
        if (spellingLabel != null) spellingLabel.setText(currentRenderTxt);

        clear(drawCanvas);

        buildMaskFromText(currentRenderTxt);
        buildOrUpdateVectorOutline(currentRenderTxt);

        inProgress = true;
        playVoiceButton.setDisable(false);

        // NEW: while recoloring, retry disabled
        if (retryButton != null) retryButton.setDisable(true);

        redrawOverlay();
        updateLocksAndProgress();
    }

    /* ===== Overlay / border ===== */
    private void redrawOverlay() {
        clear(gridCanvas);
        if (fillMask == null) return;

        offX = (gridCanvas.getWidth()  - maskW) / 2.0;
        offY = (gridCanvas.getHeight() - maskH) / 2.0;

        positionOutline();
        gridCanvas.toFront();
        if (outlineText != null) outlineText.toFront();
    }

    private void buildOrUpdateVectorOutline(String renderText) {
        if (outlineText != null) canvasHolder.getChildren().remove(outlineText);

        outlineText = new Text(renderText);
        outlineText.setFont(BASE_FONT);
        outlineText.setScaleX(textScale);
        outlineText.setScaleY(textScale);
        outlineText.setFill(Color.TRANSPARENT);
        outlineText.setStroke(Color.BLACK);
        outlineText.setStrokeWidth(OUTLINE_WIDTH);
        outlineText.setStrokeLineJoin(StrokeLineJoin.ROUND);
        outlineText.setStrokeLineCap(StrokeLineCap.ROUND);
        outlineText.setMouseTransparent(true);
        outlineText.setManaged(false);

        canvasHolder.getChildren().add(outlineText);
    }

    private void positionOutline() {
        if (outlineText == null) return;

        outlineText.setTranslateX(0);
        outlineText.setTranslateY(0);

        Bounds b = outlineText.getBoundsInParent();
        double adjustX = -b.getMinX();
        double adjustY = -b.getMinY();

        outlineText.setTranslateX(offX + adjustX);
        outlineText.setTranslateY(offY + adjustY);
    }

    /* ===== Build mask from text ("6 - Six") ===== */
    private void buildMaskFromText(String text) {
        double cw = gridCanvas.getWidth();
        double ch = gridCanvas.getHeight();

        Text t = new Text(text);
        t.setFont(BASE_FONT);

        var lb = t.getLayoutBounds();
        double margin = Math.min(cw, ch) * 0.10;
        double targetW = cw - 2 * margin;
        double targetH = ch - 2 * margin;
        textScale = Math.min(
                targetW / Math.max(1, lb.getWidth()),
                targetH / Math.max(1, lb.getHeight())
        );

        t.setScaleX(textScale);
        t.setScaleY(textScale);

        Group group = new Group(t);
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);

        var bb = t.getBoundsInParent();
        WritableImage wi = new WritableImage(
                (int)Math.ceil(bb.getWidth()),
                (int)Math.ceil(bb.getHeight())
        );
        group.snapshot(sp, wi);

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

    /* ===== Painting (pixel-masked: no bleed) ===== */
    private void handleDraw(MouseEvent e) {
        if (currentRenderTxt == null || fillMask == null || !inProgress) return;
        if (e.getButton() != MouseButton.PRIMARY) return;

        double cx = e.getX(), cy = e.getY();
        if (!isInsideMask(cx, cy)) return;

        if (eraserToggle.isSelected()) maskedEraseCircle(cx, cy);
        else                           maskedPaintCircle(cx, cy, colorPicker.getValue());

        double cov = coverage();
        progressBar.setProgress(cov);
        percentLabel.setText((int)Math.round(cov*100) + "%");

        if (isComplete()) {
            inProgress = false;
            updateLocksAndProgress();
            speakAndCongrats();

            // NEW: enable retry after completion
            if (retryButton != null) retryButton.setDisable(false);
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
    }

    /* ===== Progress ===== */
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

    private void updateLocksAndProgress() {
        numberMenu.setDisable(inProgress);
        double cov = coverage();
        progressBar.setProgress(cov);
        percentLabel.setText((int)Math.round(cov*100) + "%");
        percentLabel.setTooltip(new Tooltip("Filled: " + (int)Math.round(cov*100) + "%"));
    }

    /* ===== Voice & Congrats ===== */
    private void speakAndCongrats() {
        speakNow("The number is " + currentSpelling);
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Great Job!");
        alert.setHeaderText("Congratulations! 🙂");
        alert.setContentText("You finished coloring “" + currentRenderTxt + "”.");
        alert.showAndWait();
    }

    @FXML
    private void handlePlayVoice() {
        if (currentSpelling != null) speakNow("The number is " + currentSpelling);
    }

    // NEW: Retry handler
    @FXML
    private void handleRetry() {
        if (currentRenderTxt == null) return;

        clear(drawCanvas);
        buildMaskFromText(currentRenderTxt);
        buildOrUpdateVectorOutline(currentRenderTxt);

        inProgress = true;
        if (retryButton != null) retryButton.setDisable(true);

        redrawOverlay();
        updateLocksAndProgress(); // progress back to 0%
    }

    private void speakNow(String text) {
        new Thread(() -> {
            try { voice.speak(text); }
            catch (Exception ex) { System.err.println("[TTS] Failed: " + ex.getMessage()); }
        }, "tts-thread").start();
    }

    /* ===== Helpers ===== */
    private void clear(Canvas c) {
        c.getGraphicsContext2D().clearRect(0, 0, c.getWidth(), c.getHeight());
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /* ===== 1..100 words ===== */
    private static final String[] WORDS = new String[] {
            "", // 0
            "one","two","three","four","five","six","seven","eight","nine","ten",
            "eleven","twelve","thirteen","fourteen","fifteen","sixteen","seventeen","eighteen","nineteen","twenty",
            "twenty-one","twenty-two","twenty-three","twenty-four","twenty-five","twenty-six","twenty-seven","twenty-eight","twenty-nine","thirty",
            "thirty-one","thirty-two","thirty-three","thirty-four","thirty-five","thirty-six","thirty-seven","thirty-eight","thirty-nine","forty",
            "forty-one","forty-two","forty-three","forty-four","forty-five","forty-six","forty-seven","forty-eight","forty-nine","fifty",
            "fifty-one","fifty-two","fifty-three","fifty-four","fifty-five","fifty-six","fifty-seven","fifty-eight","fifty-nine","sixty",
            "sixty-one","sixty-two","sixty-three","sixty-four","sixty-five","sixty-six","sixty-seven","sixty-eight","sixty-nine","seventy",
            "seventy-one","seventy-two","seventy-three","seventy-four","seventy-five","seventy-six","seventy-seven","seventy-eight","seventy-nine","eighty",
            "eighty-one","eighty-two","eighty-three","eighty-four","eighty-five","eighty-six","eighty-seven","eighty-eight","eighty-nine","ninety",
            "ninety-one","ninety-two","ninety-three","ninety-four","ninety-five","ninety-six","ninety-seven","ninety-eight","ninety-nine","one hundred"
    };

    /* ===== Hybrid Voice ===== */
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
                    Object voice = vm.getClass().getMethod("getVoice", String.class).invoke(vm, "kevin16");
                    if (voice == null) throw new IllegalStateException("FreeTTS 'kevin16' not found.");
                    voice.getClass().getMethod("allocate").invoke(voice);
                    voice.getClass().getMethod("speak", String.class).invoke(voice, text);
                    voice.getClass().getMethod("deallocate").invoke(voice);
                    return;
                } catch (Exception e) { last = e; }
            }
            String os = System.getProperty("os.name","").toLowerCase();
            if (os.contains("mac")) {
                runAndWait(new String[]{"say", text}); return;
            } else if (os.contains("win")) {
                runAndWait(new String[]{"powershell","-NoProfile","-Command",
                        "$s=New-Object -ComObject SAPI.SpVoice; $s.Speak('" + text.replace("'", "''") + "');"}); return;
            } else if (os.contains("linux")) {
                runAndWait(new String[]{"bash","-lc",
                        "(command -v espeak-ng >/dev/null && espeak-ng '" + text + "') || (command -v espeak >/dev/null && espeak '" + text + "')"}); return;
            }
            if (last != null) throw last;
            throw new IllegalStateException("No TTS available.");
        }

        private static void runAndWait(String[] cmd) throws Exception {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.waitFor();
        }
    }
}
