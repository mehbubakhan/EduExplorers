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
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.net.URL;

public class MathNumberBanglaSpellingController {

    /* ===== FXML ===== */
    @FXML private MenuButton numberMenu;
    @FXML private ColorPicker colorPicker;
    @FXML private ToggleButton pencilToggle;
    @FXML private ToggleButton eraserToggle;
    @FXML private ProgressBar progressBar;
    @FXML private Label percentLabel;
    @FXML private Label spellingLabel;     // shows like "১২ - বারো"
    @FXML private StackPane canvasHolder;
    @FXML private Canvas gridCanvas;       // outline / z-order
    @FXML private Canvas drawCanvas;       // paint layer
    @FXML private Button playVoiceButton;
    @FXML private Button retryButton;

    /* ===== State ===== */
    private boolean[][] fillMask, painted;
    private int maskW, maskH;

    private boolean inProgress = false;
    private String currentNumeral   = null; // e.g. "১২"
    private String currentSpelling  = null; // e.g. "বারো"
    private String currentRenderTxt = null; // e.g. "১২ - বারো" (drawn & colored)

    private static final double BRUSH_RADIUS  = 12.0;
    private static final double OUTLINE_WIDTH = 8.0;

    // centering offsets for mask<->canvas map
    private double offX = 0.0, offY = 0.0;

    // Bangla-capable font & scale
    private static final Font BASE_FONT = bestBanglaFont(320);
    private double textScale = 1.0;

    // Crisp vector outline drawn from Text (stays perfectly aligned)
    private Text outlineText = null;

    /* ===== Audio (classpath) ===== */
    // MP3s live at: resources/com/example/aoopproject/Math/<bangla-number>.mp3
    private static final String AUDIO_BASE = "/com/example/aoopproject/Math";
    private MediaPlayer mediaPlayer;

    private static Font bestBanglaFont(double size) {
        String[] families = {"Nirmala UI", "Vrinda", "Arial Unicode MS", "SansSerif"};
        for (String fam : families) {
            try { return Font.font(fam, FontWeight.EXTRA_BOLD, size); }
            catch (Exception ignored) {}
        }
        return Font.font("SansSerif", FontWeight.EXTRA_BOLD, size);
    }

    @FXML
    private void initialize() {
        // Build menu: "১ - এক", "২ - দুই", ..., "১০০ - একশো বা এক শত"
        for (int i = 1; i <= 100; i++) {
            final String numeral = toBanglaNumber(i);
            final String word    = WORDS[i];
            final String label   = numeral + " - " + word;
            MenuItem mi = new MenuItem(label);
            mi.setOnAction(e -> onNumberChosen(numeral, word));
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

        if (retryButton != null) {
            retryButton.setDisable(true);
            retryButton.setTooltip(new Tooltip("রঙ করা শেষ হলে আবার শুরু করা যাবে"));
        }

        Platform.runLater(this::redrawOverlay);
        updateLocksAndProgress();
    }

    @FXML
    void BackToLearn(ActionEvent event) {
        stopMediaOnly();
        ChangeFxmlController.switchScene(event, "math-format.fxml");
    }

    /* ===== Menu selection ===== */
    private void onNumberChosen(String numeral, String word) {
        if (inProgress) return;

        currentNumeral   = numeral;
        currentSpelling  = word;
        currentRenderTxt = numeral + " - " + word; // EXACT: number ␠-␠ spelling

        numberMenu.setText("সংখ্যা: " + currentRenderTxt);
        if (spellingLabel != null) spellingLabel.setText(currentRenderTxt);

        clear(drawCanvas);

        // Build mask & vector outline FROM the composite "১২ - বারো"
        buildMaskFromText(currentRenderTxt);
        buildOrUpdateVectorOutline(currentRenderTxt);

        inProgress = true;
        playVoiceButton.setDisable(false);
        if (retryButton != null) retryButton.setDisable(true);

        redrawOverlay();
        updateLocksAndProgress();

        // 🔊 Play the numeral MP3 immediately
        playCurrentNumeral();
    }

    /* ===== Overlay / border ===== */
    private void redrawOverlay() {
        clear(gridCanvas);
        if (fillMask == null) return;

        offX = (gridCanvas.getWidth()  - maskW) / 2.0;
        offY = (gridCanvas.getHeight() - maskH) / 2.0;

        positionOutline();  // keep outline centered with mask
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

    /* ===== Build mask from text ("১২ - বারো") ===== */
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

            // 🔊 Play numeral MP3, then show congrats
            playNumberMp3(currentNumeral, () -> Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Great Job!");
                alert.setHeaderText("অভিনন্দন! 🙂");
                alert.setContentText("তুমি “" + currentRenderTxt + "” রঙ করা শেষ করেছো!");
                alert.showAndWait();
                if (retryButton != null) retryButton.setDisable(false);
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
        percentLabel.setTooltip(new Tooltip("ভর্তি হয়েছে: " + (int)Math.round(cov*100) + "%"));
    }

    /* ===== Voice (MP3) ===== */
    @FXML
    private void handlePlayVoice() {
        if (currentNumeral != null) playCurrentNumeral();
    }

    private void playCurrentNumeral() {
        playNumberMp3(currentNumeral, null);
    }

    private void playNumberMp3(String banglaDigits, Runnable onEnd) {
        stopMediaOnly();

        // Try exact, then common suffixed variants you have in your folder
        String[] candidates = {
                banglaDigits + ".mp3",
                banglaDigits + "_1.mp3",
                banglaDigits + "_2.mp3"
        };

        MediaPlayer mp = null;
        for (String name : candidates) {
            String resourcePath = AUDIO_BASE + "/" + name;
            mp = buildPlayerFromClasspath(resourcePath);
            if (mp != null) break;
        }
        if (mp == null) return; // alert already shown inside builder

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

    /* ===== Retry ===== */
    @FXML
    private void handleRetry() {
        if (currentRenderTxt == null) return;

        clear(drawCanvas);
        buildMaskFromText(currentRenderTxt);
        buildOrUpdateVectorOutline(currentRenderTxt);

        inProgress = true;
        if (retryButton != null) retryButton.setDisable(true);

        redrawOverlay();
        updateLocksAndProgress(); // resets progress to 0%
    }

    /* ===== Helpers ===== */
    private void clear(Canvas c) {
        c.getGraphicsContext2D().clearRect(0, 0, c.getWidth(), c.getHeight());
    }

    /** Convert 1..100 to Bangla numerals. */
    private static String toBanglaNumber(int n) {
        String western = Integer.toString(n);
        char[] map = {'০','১','২','৩','৪','৫','৬','৭','৮','৯'};
        StringBuilder sb = new StringBuilder();
        for (char ch : western.toCharArray()) sb.append(map[ch - '0']);
        return sb.toString();
    }

    /* ===== Bangla spellings (1..100) ===== */
    private static final String[] WORDS = new String[] {
            "", // 0 unused
            "এক","দুই","তিন","চার","পাঁচ","ছয়","সাত","আট","নয়","দশ",
            "এগারো","বারো","তেরো","চৌদ্দ","পনেরো","ষোলো","সতেরো","আঠারো","উনিশ","বিশ",
            "একুশ","বাইশ","তেইশ","চব্বিশ","পঁচিশ","ছাব্বিশ","সাতাশ","আঠাশ","উনত্রিশ","ত্রিশ",
            "একত্রিশ","বত্রিশ","তেত্রিশ","চৌত্রিশ","পঁয়ত্রিশ","ছত্রিশ","সাঁইত্রিশ","আটত্রিশ","উনচল্লিশ","চল্লিশ",
            "একচল্লিশ","বিয়াল্লিশ","তেতাল্লিশ","চুয়াল্লিশ","পঁয়তাল্লিশ","ছেচল্লিশ","সাতচল্লিশ","আটচল্লিশ","উনপঞ্চাশ","পঞ্চাশ",
            "একান্ন","বাহান্ন","তিপ্পান্ন","চুয়ান্ন","পঞ্চান্ন","ছাপ্পান্ন","সাতান্ন","আটান্ন","উনষাট","ষাট",
            "একষট্টি","বাষট্টি","তেষট্টি","চৌষট্টি","পঁয়ষট্টি","ছেষট্টি","সাতষট্টি","আটষট্টি","উনসত্তর","সত্তর",
            "একাত্তর","বাহাত্তর","তিয়াত্তর","চুয়াত্তর","পঁচাত্তর","ছিয়াত্তর","সাতাত্তর","আটাত্তর","ঊনআশি","আশি",
            "একাশি","বিরাশি","তিরাশি","চুরাশি","পঁচাশি","ছিয়াশি","সাতাশি","অষ্টাশি","ঊননব্বই","নব্বই",
            "একানব্বই","বিরানব্বই","তিরানব্বই","চুরানব্বই","পঁচানব্বই","ছিয়ানব্বই","সাতানব্বই","আটানব্বই","নিরানব্বই","একশো বা এক শত"
    };
}
