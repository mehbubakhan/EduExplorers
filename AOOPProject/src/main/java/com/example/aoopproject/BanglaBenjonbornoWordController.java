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

public class BanglaBenjonbornoWordController {

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
    @FXML private Button retryButton;

    /* ===== Paint state ===== */
    private boolean[][] fillMask;
    private boolean[][] painted;
    private int maskW, maskH;

    private String currentLetter = null;
    private String currentWords  = null;
    private String currentLine   = null;

    private double offX = 0.0, offY = 0.0;
    private boolean completedOnce = false;

    private static final double BRUSH_RADIUS  = 12.0;
    private static final double OUTLINE_WIDTH = 7.0;

    private static final Font LINE_FONT    = bestBanglaFont(240);
    private static final Font OUTLINE_FONT = bestBanglaFont(240);

    private WritableImage outlineImage;

    /* ===== Audio (classpath) ===== */
    private static final String AUDIO_WORD_BASE   = "/com/example/aoopproject/Benjonbornoword";
    private static final String AUDIO_LETTER_BASE = "/com/example/aoopproject/Benjonbornoo";
    private MediaPlayer mediaPlayer;

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

    /* ===== Words list ===== */
    private static final Map<String, String> WORDS = new LinkedHashMap<>();
    static {
        WORDS.put("ক", "কলা, কাক");
        WORDS.put("খ", "খাতা, খাবার");
        WORDS.put("গ", "গরু, গান");
        WORDS.put("ঘ", "ঘর, ঘুড়ি");
        WORDS.put("ঙ", "আঙুর, রঙ");

        WORDS.put("চ", "চশমা, চাকা");
        WORDS.put("ছ", "ছাতা, ছাগল");
        WORDS.put("জ", "জল, জামা");
        WORDS.put("ঝ", "ঝুড়ি, ঝরনা");
        WORDS.put("ঞ", "মিঞ, মিঞা");

        WORDS.put("ট", "টেবিল, টব");
        WORDS.put("ঠ", "কাঠ, ঠেলা");
        WORDS.put("ড", "ডাল, ডাব");
        WORDS.put("ঢ", "ঢাক, ঢেউ");
        WORDS.put("ণ", "বাণী, গণনা");

        WORDS.put("ত", "তাল, তালা");
        WORDS.put("থ", "থালা, থাবা");
        WORDS.put("দ", "দরজা, দুধ");
        WORDS.put("ধ", "ধান, ধরা");
        WORDS.put("ন", "নাক, নাম");

        WORDS.put("প", "পতাকা, পানি");
        WORDS.put("ফ", "ফুল, ফল");
        WORDS.put("ব", "বাঘ, বাড়ি");
        WORDS.put("ভ", "ভাত, ভোর");
        WORDS.put("ম", "মাছ, মাটি");

        WORDS.put("য", "যব, যম");
        WORDS.put("র", "রবি, রাত");
        WORDS.put("ল", "লাল, লতা");

        WORDS.put("শ", "শরৎ, শাপলা");
        WORDS.put("ষ", "ষাঁড়, ষাট");
        WORDS.put("স", "সকাল, সময়");
        WORDS.put("হ", "হাত, হাঁস");

        WORDS.put("ড়", "বড়, ঘড়ি");
        WORDS.put("ঢ়", "আষাঢ়, গাঢ়");
        WORDS.put("য়", "জয়, নয়");
        WORDS.put("ৎ", "সৎ, মৎস্য");
        WORDS.put("ং", "বাংলা, শিং");
        WORDS.put("ঃ", "দুঃখ, দুঃসাহস");
        WORDS.put("ঁ", "চাঁদ, দাঁত");
    }

    @FXML
    private void initialize() {
        buildGroupedMenu();

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
            retryButton.setTooltip(new Tooltip("সম্পূর্ণ হলে আবার শুরু করতে পারবেন"));
        }

        Platform.runLater(this::redrawOverlay);
        updateProgressUI();
    }

    private void buildGroupedMenu() {
        wordMenu.getItems().clear();
        String[][] groups = {
                {"ক","খ","গ","ঘ","ঙ"},
                {"চ","ছ","জ","ঝ","ঞ"},
                {"ট","ঠ","ড","ঢ","ণ"},
                {"ত","থ","দ","ধ","ন"},
                {"প","ফ","ব","ভ","ম"},
                {"য","র","ল"},
                {"শ","ষ","স","হ"},
                {"ড়","ঢ়","য়","ৎ","ং","ঃ","ঁ"}
        };
        for (String[] grp : groups) {
            String title = grp[0] + "–" + grp[grp.length-1];
            Menu sub = new Menu(title);
            for (String key : grp) {
                String words = WORDS.get(key);
                if (words == null) continue;
                MenuItem mi = new MenuItem(key + " = " + words);
                mi.setOnAction(e -> onLetterChosen(key, words));
                sub.getItems().add(mi);
            }
            wordMenu.getItems().add(sub);
        }
    }

    @FXML
    void BackToBanglaFormat(ActionEvent event) {
        stopMediaOnly();
        ChangeFxmlController.switchScene(event,"bangla-format.fxml");
    }

    /* ===== Select letter: build mask + play audio ===== */
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

        // ▶ play audio immediately
        playWordAudio(letter, null);

        redrawOverlay();
        updateProgressUI();
    }

    /* ===== Build mask (filled) + outline (stroked) ===== */
    private void buildMaskAndOutline(String line) {
        double cw = gridCanvas.getWidth();
        double ch = gridCanvas.getHeight();

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

            // ▶ play audio again, then show congrats
            playWordAudio(currentLetter, () -> Platform.runLater(() -> {
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

    /* ===== Progress ===== */
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

    /* ===== Listen button ===== */
    @FXML
    private void handlePlayVoice() {
        if (currentLetter != null) {
            playWordAudio(currentLetter, null);
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
    private void handleRetry() { resetToBasePosition(); }

    /* ===== Audio helpers ===== */

    /** Plays Benjonbornoword/<LETTER>word.mp3, falling back to Benjonbornoo/<LETTER>.mp3 */
    private void playWordAudio(String letter, Runnable onEnd) {
        stopMediaOnly();

        String wordPath   = AUDIO_WORD_BASE   + "/" + letter + "word.mp3";
        String letterPath = AUDIO_LETTER_BASE + "/" + letter + ".mp3";

        MediaPlayer mp = buildPlayerFromClasspath(wordPath);
        if (mp == null) mp = buildPlayerFromClasspath(letterPath);
        if (mp == null) {
            // No audio found; just return silently
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
            if (url == null) return null;
            Media media = new Media(url.toExternalForm());
            MediaPlayer player = new MediaPlayer(media);
            player.setAutoPlay(false);
            return player;
        } catch (Exception ex) {
            System.err.println("[MP3] Failed load " + resourcePath + " : " + ex.getMessage());
            return null;
        }
    }

    private void stopMediaOnly() {
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); mediaPlayer.dispose(); } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    /* ===== Utils ===== */
    private void clear(Canvas c) { c.getGraphicsContext2D().clearRect(0, 0, c.getWidth(), c.getHeight()); }
}
