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

public class MathNumberBanglaController {

    /* ------- FXML ------- */
    @FXML private MenuButton numberMenu;
    @FXML private ColorPicker colorPicker;
    @FXML private ToggleButton pencilToggle;
    @FXML private ToggleButton eraserToggle;
    @FXML private ProgressBar progressBar;
    @FXML private Label percentLabel;
    @FXML private StackPane canvasHolder;
    @FXML private Canvas gridCanvas;   // outline layer
    @FXML private Canvas drawCanvas;   // paint layer
    @FXML private Button playVoiceButton;
    @FXML private Button retryButton;

    /* ------- State ------- */
    private boolean[][] fillMask;
    private boolean[][] painted;
    private int maskW, maskH;

    private boolean inProgress = false;
    private String currentNumberText = null; // Bangla numerals like "১২"

    private static final double BRUSH_RADIUS  = 12.0;
    private static final double OUTLINE_WIDTH = 8.0;

    private double offX = 0.0, offY = 0.0;

    // Use a font that supports Bangla digits
    private static final Font BASE_FONT = bestBanglaFont(320);

    /* ------- Audio (classpath) ------- */
    // MP3s live at: resources/com/example/aoopproject/Math/<bangla-number>.mp3
    private static final String AUDIO_BASE = "/com/example/aoopproject/Math";
    private MediaPlayer mediaPlayer; // keep 1 player alive, stop/dispose when needed

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
        // ১ .. ১০০
        for (int i = 1; i <= 100; i++) {
            final String label = toBanglaNumber(i);
            MenuItem mi = new MenuItem(label);
            mi.setOnAction(e -> onNumberChosen(label));
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

        if (retryButton != null) {
            retryButton.setDisable(true);
            retryButton.setTooltip(new Tooltip("সম্পূর্ণ করলে আবার শুরু করা যাবে"));
        }

        Platform.runLater(this::redrawOverlay);
        updateLocksAndProgress();
    }

    /* ------- Back ------- */
    @FXML
    void BackToLearn(ActionEvent event) {
        stopMediaOnly();
        ChangeFxmlController.switchScene(event, "math-format.fxml");
    }

    /* ------- Choose number ------- */
    private void onNumberChosen(String text) {
        if (inProgress) return;

        currentNumberText = text;
        numberMenu.setText("সংখ্যা: " + text);

        clear(drawCanvas);
        buildMaskFromText(text);

        inProgress = true;
        playVoiceButton.setDisable(false);
        if (retryButton != null) retryButton.setDisable(true);

        redrawOverlay();
        updateLocksAndProgress();

        // Auto-play the chosen number
        playCurrentNumber();
    }

    /* ------- Overlay (border) ------- */
    private void redrawOverlay() {
        clear(gridCanvas);
        if (fillMask != null) {
            offX = (gridCanvas.getWidth()  - maskW) / 2.0;
            offY = (gridCanvas.getHeight() - maskH) / 2.0;
            drawOutlineFromMask();
        }
        gridCanvas.toFront();
    }

    private void drawOutlineFromMask() {
        GraphicsContext g = gridCanvas.getGraphicsContext2D();
        g.setFill(Color.BLACK);
        double r = OUTLINE_WIDTH / 2.0;

        for (int y = 1; y < maskH - 1; y++) {
            for (int x = 1; x < maskW - 1; x++) {
                if (!fillMask[y][x]) continue;
                // boundary pixel?
                if (!fillMask[y-1][x] || !fillMask[y+1][x] || !fillMask[y][x-1] || !fillMask[y][x+1]) {
                    double cx = offX + x + 0.5;
                    double cy = offY + y + 0.5;
                    g.fillOval(cx - r, cy - r, OUTLINE_WIDTH, OUTLINE_WIDTH);
                }
            }
        }
    }

    /* ------- Build mask from Bangla text ------- */
    private void buildMaskFromText(String text) {
        double cw = gridCanvas.getWidth();
        double ch = gridCanvas.getHeight();

        Text t = new Text(text);
        t.setFont(BASE_FONT);

        var lb = t.getLayoutBounds();
        double margin = Math.min(cw, ch) * 0.10;
        double targetW = cw - 2 * margin;
        double targetH = ch - 2 * margin;
        double scale = Math.min(
                targetW / Math.max(1, lb.getWidth()),
                targetH / Math.max(1, lb.getHeight())
        );

        t.setScaleX(scale);
        t.setScaleY(scale);

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
        updateLocksAndProgress();
    }

    /* ------- Painting (masked) ------- */
    private void handleDraw(MouseEvent e) {
        if (currentNumberText == null || fillMask == null || !inProgress) return;
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

            // Play the number MP3, then show congrats
            playNumberMp3(currentNumberText, () -> Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("শুভেচ্ছা!");
                alert.setHeaderText("অভিনন্দন! 🙂");
                alert.setContentText("তুমি “" + currentNumberText + "” রঙ করা শেষ করেছো!");
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

    /* ------- Coverage ------- */
    private double coverage() {
        if (fillMask == null || painted == null) return 0;
        long total = 0, done = 0;
        for (int y = 0; y < maskH; y++) {
            for (int x = 0; x < maskW; x++) {
                if (fillMask[y][x]) { total++; if (painted[y][x]) done++; }
            }
        }
        return total == 0 ? 0 : (double) done / total;
    }

    private boolean isComplete() {
        if (fillMask == null || painted == null) return false;
        for (int y = 0; y < maskH; y++)
            for (int x = 0; x < maskW; x++)
                if (fillMask[y][x] && !painted[y][x]) return false;
        return true;
    }

    /* ------- Play MP3s ------- */
    @FXML
    private void handlePlayVoice() {
        if (currentNumberText != null) playCurrentNumber();
    }

    private void playCurrentNumber() {
        playNumberMp3(currentNumberText, null);
    }

    private void playNumberMp3(String banglaDigits, Runnable onEnd) {
        stopMediaOnly();

        // try exact, then common suffixes you have in your folder
        String[] candidates = new String[] {
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

    /* ------- Retry ------- */
    @FXML
    private void handleRetry() {
        if (currentNumberText == null) return;

        clear(drawCanvas);
        buildMaskFromText(currentNumberText);
        redrawOverlay();

        inProgress = true;
        if (retryButton != null) retryButton.setDisable(true);
        updateLocksAndProgress();
    }

    private void updateLocksAndProgress() {
        numberMenu.setDisable(inProgress);
        double cov = coverage();
        progressBar.setProgress(cov);
        percentLabel.setText((int)Math.round(cov*100) + "%");
        percentLabel.setTooltip(new Tooltip("ভর্তি হয়েছে: " + (int)Math.round(cov*100) + "%"));
    }

    /* ------- Helpers ------- */
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
}
