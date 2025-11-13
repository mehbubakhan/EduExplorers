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

public class BanglaShorobornoController {

    /* ===== FXML ===== */
    @FXML private MenuButton letterMenu;
    @FXML private ColorPicker colorPicker;
    @FXML private ToggleButton pencilToggle;
    @FXML private ToggleButton eraserToggle;
    @FXML private ProgressBar progressBar;
    @FXML private Label percentLabel;
    @FXML private StackPane canvasHolder;
    @FXML private Canvas gridCanvas;       // sizing/overlay
    @FXML private Canvas drawCanvas;       // paint layer
    @FXML private Button playVoiceButton;
    @FXML private Button retryButton;

    /* ===== State ===== */
    private boolean[][] fillMask, painted;
    private int maskW, maskH;

    private boolean inProgress = false;
    private String currentLetter = null;   // e.g., "অ"

    private static final double BRUSH_RADIUS  = 12.0;
    private static final double OUTLINE_WIDTH = 8.0;

    private double offX = 0.0, offY = 0.0;           // center offsets
    private static final Font BASE_FONT = bestBanglaFont(320);
    private double textScale = 1.0;

    // crisp vector outline (keeps border clean on top)
    private Text outlineText = null;

    /* ===== Audio (classpath like EnglishRhyme) ===== */
    private static final String AUDIO_BASE = "/com/example/aoopproject/Shoroborno.mp3";
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
        // Shoroborno options
        String[] vowels = {"অ","আ","ই","ঈ","উ","ঊ","ঋ","এ","ঐ","ও","ঔ"};
        for (String l : vowels) {
            MenuItem mi = new MenuItem(l);
            mi.setOnAction(e -> onLetterChosen(l));
            letterMenu.getItems().add(mi);
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

        if (retryButton != null) {
            retryButton.setDisable(true);
            retryButton.setTooltip(new Tooltip("আবার শুরু করুন (সম্পূর্ণ করার পর সক্রিয় হবে)"));
        }

        Platform.runLater(this::redrawOverlay);
        updateLocksAndProgress();
    }

    @FXML
    void BackToBanglaFormat(ActionEvent event) {
        stopMediaOnly();
        ChangeFxmlController.switchScene(event, "bangla-format.fxml");
    }

    /* ===== Choose letter ===== */
    private void onLetterChosen(String letter) {
        if (inProgress) return;

        currentLetter = letter;
        letterMenu.setText("অক্ষর: " + letter);

        clear(drawCanvas);

        buildMaskFromText(letter);
        buildOrUpdateVectorOutline(letter);

        inProgress = true;
        playVoiceButton.setDisable(false);
        retryButton.setDisable(true);

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

    private void buildOrUpdateVectorOutline(String text) {
        if (outlineText != null) canvasHolder.getChildren().remove(outlineText);

        outlineText = new Text(text);
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

    /* ===== Build mask from letter ===== */
    private void buildMaskFromText(String text) {
        double cw = gridCanvas.getWidth();
        double ch = gridCanvas.getHeight();

        Text t = new Text(text);
        t.setFont(BASE_FONT);

        var lb = t.getLayoutBounds();
        double margin = Math.min(cw, ch) * 0.10; // 10% margin
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

    /* ===== Painting (pixel-masked, no bleed) ===== */
    private void handleDraw(MouseEvent e) {
        if (currentLetter == null || fillMask == null || !inProgress) return;
        if (e.getButton() != MouseButton.PRIMARY) return;

        double cx = e.getX(), cy = e.getY();
        if (!isInsideMask(cx, cy)) return;

        if (eraserToggle.isSelected()) maskedEraseCircle(cx, cy);
        else maskedPaintCircle(cx, cy, colorPicker.getValue());

        double cov = coverage();
        progressBar.setProgress(cov);
        percentLabel.setText((int)Math.round(cov*100) + "%");

        if (isComplete()) {
            inProgress = false;
            updateLocksAndProgress();

            // Play letter audio, then show the congrats dialog
            playLetterMp3(currentLetter, () -> {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Great Job!");
                    alert.setHeaderText("অভিনন্দন! 🙂");
                    alert.setContentText("তুমি “" + currentLetter + "” রঙ করা শেষ করেছো!");
                    alert.showAndWait();
                    retryButton.setDisable(false);
                });
            });
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
        letterMenu.setDisable(inProgress);
        double cov = coverage();
        progressBar.setProgress(cov);
        percentLabel.setText((int)Math.round(cov*100) + "%");
        percentLabel.setTooltip(new Tooltip("ভর্তি হয়েছে: " + (int)Math.round(cov*100) + "%"));
    }

    /* ===== Retry ===== */
    private void resetToBasePosition() {
        if (currentLetter == null) return;

        clear(drawCanvas);
        buildMaskFromText(currentLetter);
        buildOrUpdateVectorOutline(currentLetter);
        redrawOverlay();

        inProgress = true;
        retryButton.setDisable(true);
        updateLocksAndProgress();
    }

    @FXML
    private void handleRetry() {
        resetToBasePosition();
    }

    /* ===== Listen (শুনুন) ===== */
    @FXML
    private void handlePlayVoice() {
        if (currentLetter != null) {
            playLetterMp3(currentLetter, null);
        }
    }

    /* ===== Letter MP3 playback ===== */
    private void playLetterMp3(String letter, Runnable onEnd) {
        stopMediaOnly();

        String resourcePath = AUDIO_BASE + "/" + mapLetterToFile(letter);
        MediaPlayer mp = buildPlayerFromClasspath(resourcePath);
        if (mp == null) {
            // If not found, just return quietly.
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

    private String mapLetterToFile(String letter) {
        // All files should be exactly "<letter>.mp3"
        // Example: অ -> অ.mp3
        return letter + ".mp3";
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

    /* ===== Utils ===== */
    private void clear(Canvas c) {
        c.getGraphicsContext2D().clearRect(0, 0, c.getWidth(), c.getHeight());
    }
}
