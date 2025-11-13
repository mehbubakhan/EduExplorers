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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton; // <- right-click erase support
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.util.StringConverter;

import java.util.*;

public class ShapeController {

    /* ---------- FXML ---------- */
    @FXML private MenuButton shapeMenu;
    @FXML private ComboBox<ColorItem> colorBox;
    @FXML private ToggleButton pencilToggle;
    @FXML private ToggleButton eraserToggle;
    @FXML private Button btnClear;
    @FXML private Button btnRetry;              // NEW
    @FXML private ProgressBar progressBar;
    @FXML private Label percentLabel;
    @FXML private Button btnSpeakShape, btnSpeakColor, btnSpeakBoth;

    @FXML private StackPane canvasHolder;
    @FXML private Canvas gridCanvas;   // sizing only
    @FXML private Canvas drawCanvas;   // paint layer (clipped)

    /* ---------- Voice ---------- */
    private static final boolean USE_FREETTS = false;
    private final VoiceService voice = new HybridVoiceService(USE_FREETTS);

    /* ---------- Mask / state ---------- */
    private boolean[][] fillMask;
    private boolean[][] painted;
    private int maskW, maskH;

    private boolean inProgress = false;
    private String currentShape = null;

    private Color  currentPaint     = Color.BLACK; // default
    private String currentColorName = "Black";

    // Track unique colors actually used
    private final LinkedHashSet<String> usedColorNames = new LinkedHashSet<>();

    // geometry reused for mask/clip/outline
    private Group geometryFilled = null;
    private Group clipNode = null;
    private Group outlineGroup = null;

    private double offX = 0, offY = 0;

    // brush / outline
    private static final double BRUSH_RADIUS  = 12.0;
    private static final double OUTLINE_WIDTH = 8.0;
    private static final int ICON_PX = 28;

    // coverage thresholds
    private static final double MASK_OPACITY_THRESHOLD = 0.50;
    private static final double PIX_OPACITY_THRESHOLD  = 0.04;
    private static final double PIXEL_VERIFY_START     = 0.995;
    private static final double COMPLETE_THRESHOLD     = 0.999;

    /* ---------- Named colors ---------- */
    private static final List<ColorItem> NAMED_COLORS = List.of(
            c("Black",        Color.BLACK),
            c("White",        Color.WHITE),
            c("Red",          Color.RED),
            c("Orange",       Color.ORANGE),
            c("Yellow",       Color.YELLOW),
            c("Lime",         Color.LIME),
            c("Green",        Color.GREEN),
            c("Teal",         Color.TEAL),
            c("Cyan",         Color.CYAN),
            c("Sky Blue",     Color.SKYBLUE),
            c("Blue",         Color.BLUE),
            c("Indigo",       Color.INDIGO),
            c("Purple",       Color.PURPLE),
            c("Magenta",      Color.MAGENTA),
            c("Pink",         Color.HOTPINK),
            c("Brown",        Color.SADDLEBROWN),
            c("Gray",         Color.GRAY),
            c("Silver",       Color.SILVER),
            c("Gold",         Color.GOLD),
            c("Dodger Blue",  Color.DODGERBLUE)
    );
    private static ColorItem c(String name, Color color) { return new ColorItem(name, color); }

    /* ---------- Init ---------- */
    @FXML
    private void initialize() {
        buildShapeMenu();
        setupColorBox();
        setupMicIcons();

        ToggleGroup tg = new ToggleGroup();
        pencilToggle.setToggleGroup(tg);
        eraserToggle.setToggleGroup(tg);
        pencilToggle.setSelected(true);

        gridCanvas.widthProperty().bind(canvasHolder.widthProperty());
        gridCanvas.heightProperty().bind(canvasHolder.heightProperty());
        drawCanvas.widthProperty().bind(canvasHolder.widthProperty());
        drawCanvas.heightProperty().bind(canvasHolder.heightProperty());

        gridCanvas.widthProperty().addListener((o, a, b) -> onResize());
        gridCanvas.heightProperty().addListener((o, a, b) -> onResize());
        gridCanvas.setMouseTransparent(true);

        drawCanvas.addEventHandler(MouseEvent.MOUSE_PRESSED,  this::handleDraw);
        drawCanvas.addEventHandler(MouseEvent.MOUSE_DRAGGED,  this::handleDraw);
        drawCanvas.addEventHandler(MouseEvent.MOUSE_RELEASED, this::handleRelease);

        // voice buttons initial state
        btnSpeakShape.setDisable(true);  // until a shape is chosen
        btnSpeakColor.setDisable(false); // enabled from the start (default Black)
        btnSpeakBoth.setDisable(true);   // until 100%

        // NEW: Retry starts disabled
        if (btnRetry != null) {
            btnRetry.setDisable(true);
            btnRetry.setTooltip(new Tooltip("Finish coloring to enable retry"));
        }

        Platform.runLater(this::onResize);
        updateLocksAndProgress();
    }

    private void setupMicIcons() {
        Image mic;
        try {
            mic = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream("/icons/mic.png")));
        } catch (Exception e) {
            mic = null; // fallback to text if icon not found
        }
        if (mic != null) {
            for (Button b : new Button[]{btnSpeakShape, btnSpeakColor, btnSpeakBoth}) {
                ImageView iv = new ImageView(mic);
                iv.setFitWidth(16); iv.setFitHeight(16);
                b.setGraphic(iv);
                b.setText("");
            }
        } else {
            btnSpeakShape.setText("Speak Shape");
            btnSpeakColor.setText("Speak Color");
            btnSpeakBoth.setText("Speak Both");
        }
    }

    private void setupColorBox() {
        colorBox.getItems().setAll(NAMED_COLORS);

        colorBox.setCellFactory(cb -> new ListCell<>() {
            @Override
            protected void updateItem(ColorItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                javafx.scene.shape.Rectangle sw = new javafx.scene.shape.Rectangle(18, 18, item.color);
                sw.setStroke(Color.GRAY);
                setText(item.name);
                setGraphic(sw);
            }
        });
        colorBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(ColorItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                javafx.scene.shape.Rectangle sw = new javafx.scene.shape.Rectangle(18, 18, item.color);
                sw.setStroke(Color.GRAY);
                setText(item.name);
                setGraphic(sw);
            }
        });
        colorBox.setConverter(new StringConverter<>() {
            @Override public String toString(ColorItem item) { return item == null ? "" : item.name; }
            @Override public ColorItem fromString(String s) {
                return NAMED_COLORS.stream().filter(ci -> ci.name.equals(s)).findFirst().orElse(null);
            }
        });

        // Default: Black (also speaks when changed)
        colorBox.getSelectionModel().selectFirst();
        currentPaint = colorBox.getValue().color;
        currentColorName = colorBox.getValue().name;

        colorBox.setOnAction(e -> {
            ColorItem ci = colorBox.getValue();
            if (ci == null) return;
            currentPaint = ci.color;
            currentColorName = ci.name;

            // Make sure coloring works after a color pick
            pencilToggle.setSelected(true);
            eraserToggle.setSelected(false);

            speakNow("This is " + currentColorName + " color.");
        });
    }

    private void buildShapeMenu() {
        shapeMenu.getItems().clear();
        String[] shapes = {
                "Circle","Triangle","Square","Rectangle","Oval","Star",
                "Diamond","Pentagon","Hexagon","Trapezoid","Parallelogram",
                "Right Arrow","Left Arrow","Up Arrow","Down Arrow"
        };
        for (String name : shapes) {
            WritableImage icon = snapshotFilled(createShapeGroup(name, 100, 100));
            ImageView iv = new ImageView(icon);
            iv.setFitWidth(ICON_PX); iv.setFitHeight(ICON_PX); iv.setPreserveRatio(true);
            MenuItem mi = new MenuItem(name, iv);
            mi.setOnAction(e -> onShapeChosen(name));
            shapeMenu.getItems().add(mi);
        }
        shapeMenu.setText("Shape: —");
    }

    @FXML
    void BackToLearn(ActionEvent event) {
        ChangeFxmlController.switchScene(event, "learn.fxml");
    }

    /* ---------- Choosing a shape (menu always enabled) ---------- */
    private void onShapeChosen(String shape) {
        currentShape = shape;

        shapeMenu.setText("Shape: " + shape);

        clear(drawCanvas);
        usedColorNames.clear();
        buildMaskAndGeometry(shape);

        inProgress = true;
        btnSpeakShape.setDisable(false);
        btnSpeakBoth.setDisable(true);

        // NEW: while recoloring, Retry disabled
        if (btnRetry != null) btnRetry.setDisable(true);

        speakNow("This is " + currentShape + ".");

        onResize();
        updateLocksAndProgress();
    }

    /* ---------- Clear ---------- */
    @FXML
    private void handleClear() {
        if (fillMask == null) return;
        clear(drawCanvas);
        for (int y = 0; y < maskH; y++) Arrays.fill(painted[y], false);
        usedColorNames.clear();
        inProgress = true;
        btnSpeakBoth.setDisable(true);
        progressBar.setProgress(0);
        percentLabel.setText("0%");
        // NEW: Clearing mid-way also disables Retry
        if (btnRetry != null) btnRetry.setDisable(true);
    }

    /* ---------- NEW: Retry (enabled only after completion) ---------- */
    @FXML
    private void handleRetry() {
        if (currentShape == null) return;
        // Reset the same shape completely
        handleClear();
    }

    /* ---------- geometry & mask ---------- */

    private void buildMaskAndGeometry(String shape) {
        double cw = gridCanvas.getWidth();
        double ch = gridCanvas.getHeight();

        double margin = Math.min(cw, ch) * 0.10;
        double availW = Math.max(10, cw - 2 * margin);
        double availH = Math.max(10, ch - 2 * margin);

        geometryFilled = createShapeGroup(shape, availW, availH);

        WritableImage wi = snapshotFilled(geometryFilled);
        PixelReader pr = wi.getPixelReader();
        maskW = (int) wi.getWidth();
        maskH = (int) wi.getHeight();

        fillMask = new boolean[maskH][maskW];
        painted  = new boolean[maskH][maskW];

        for (int y = 0; y < maskH; y++) {
            for (int x = 0; x < maskW; x++) {
                boolean inside = pr.getColor(x, y).getOpacity() > MASK_OPACITY_THRESHOLD;
                fillMask[y][x] = inside;
                painted[y][x]  = false;
            }
        }

        clipNode = cloneFilledGroup(geometryFilled);
        clipNode.setManaged(false);
        drawCanvas.setClip(clipNode);

        if (outlineGroup != null) {
            canvasHolder.getChildren().remove(outlineGroup);
            outlineGroup = null;
        }
    }

    private void onResize() {
        if (fillMask == null || geometryFilled == null) return;

        double cw = gridCanvas.getWidth(), ch = gridCanvas.getHeight();
        offX = (cw - maskW) / 2.0;
        offY = (ch - maskH) / 2.0;

        clipNode.setTranslateX(offX);
        clipNode.setTranslateY(offY);
        drawCanvas.setClip(clipNode);

        if (outlineGroup != null) canvasHolder.getChildren().remove(outlineGroup);
        outlineGroup = createStrokedFromFilled(geometryFilled, OUTLINE_WIDTH);
        outlineGroup.setMouseTransparent(true);
        outlineGroup.setManaged(false);
        outlineGroup.setTranslateX(offX);
        outlineGroup.setTranslateY(offY);
        canvasHolder.getChildren().add(outlineGroup);
        outlineGroup.toFront();
    }

    /* ---------- painting ---------- */

    private void handleDraw(MouseEvent e) {
        if (currentShape == null || fillMask == null || !inProgress) return;

        // NEW: right-click also erases (without toggling)
        boolean useEraser = eraserToggle.isSelected() || e.getButton() == MouseButton.SECONDARY;
        if (e.getButton() != MouseButton.PRIMARY && e.getButton() != MouseButton.SECONDARY) return;

        double cx = e.getX(), cy = e.getY();
        if (!isInsideMask(cx, cy)) return;

        boolean paintedNow;
        if (useEraser) {
            paintedNow = eraseAt(cx, cy);
        } else {
            paintedNow = drawAt(cx, cy, currentPaint);
            if (paintedNow) usedColorNames.add(currentColorName);
        }

        updateCoverageAndMaybeFinish(false);
    }

    private void handleRelease(MouseEvent e) {
        if (!inProgress) return;
        updateCoverageAndMaybeFinish(true);
    }

    private void updateCoverageAndMaybeFinish(boolean forcePixelVerify) {
        double cov = effectiveCoverage();
        progressBar.setProgress(Math.min(1.0, cov));
        updatePercentLabel(cov);

        if (cov >= COMPLETE_THRESHOLD) {
            inProgress = false;
            progressBar.setProgress(1.0);
            percentLabel.setText("100%");
            btnSpeakBoth.setDisable(false);
            // NEW: enable Retry once finished
            if (btnRetry != null) btnRetry.setDisable(false);
            updateLocksAndProgress();
            showCongratsAndSpeak();
        }
    }

    private boolean isInsideMask(double cx, double cy) {
        int mx = (int) Math.floor(cx - offX);
        int my = (int) Math.floor(cy - offY);
        return !(mx < 0 || my < 0 || mx >= maskW || my >= maskH) && fillMask[my][mx];
    }

    private boolean drawAt(double cx, double cy, Color color) {
        GraphicsContext g = drawCanvas.getGraphicsContext2D();
        g.setFill(color);
        g.fillOval(cx - BRUSH_RADIUS, cy - BRUSH_RADIUS, BRUSH_RADIUS * 2, BRUSH_RADIUS * 2);
        return stampPainted(cx, cy, true, BRUSH_RADIUS);
    }

    private boolean eraseAt(double cx, double cy) {
        GraphicsContext g = drawCanvas.getGraphicsContext2D();
        g.clearRect(cx - BRUSH_RADIUS, cy - BRUSH_RADIUS, BRUSH_RADIUS * 2, BRUSH_RADIUS * 2);
        return stampPainted(cx, cy, false, BRUSH_RADIUS);
    }

    private boolean stampPainted(double cx, double cy, boolean value, double r) {
        int minX = (int) Math.floor(cx - r - offX);
        int minY = (int) Math.floor(cy - r - offY);
        int maxX = (int) Math.ceil (cx + r - offX);
        int maxY = (int) Math.ceil (cy + r - offY);

        double r2 = r * r;
        boolean changed = false;

        for (int y = Math.max(0, minY); y <= Math.min(maskH - 1, maxY); y++) {
            for (int x = Math.max(0, minX); x <= Math.min(maskW - 1, maxX); x++) {
                if (!fillMask[y][x]) continue;
                double px = offX + x, py = offY + y;
                double dx = px - cx, dy = py - cy;
                if (dx * dx + dy * dy <= r2) {
                    if (painted[y][x] != value) { painted[y][x] = value; changed = true; }
                }
            }
        }
        return changed;
    }

    /* ---------- coverage ---------- */

    private double coverage() {
        long total = 0, done = 0;
        for (int y = 0; y < maskH; y++) {
            for (int x = 0; x < maskW; x++) {
                if (fillMask[y][x]) { total++; if (painted[y][x]) done++; }
            }
        }
        return total == 0 ? 0 : (double) done / total;
    }

    private double effectiveCoverage() {
        double rough = coverage();
        if (rough < PIXEL_VERIFY_START) return rough;

        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        WritableImage snap = new WritableImage(
                (int)Math.ceil(drawCanvas.getWidth()),
                (int)Math.ceil(drawCanvas.getHeight())
        );
        drawCanvas.snapshot(sp, snap);
        PixelReader pr = snap.getPixelReader();

        long total = 0, done = 0;
        for (int y = 0; y < maskH; y++) {
            int sy = (int)Math.floor(offY + y + 0.5);
            if (sy < 0 || sy >= snap.getHeight()) continue;
            for (int x = 0; x < maskW; x++) {
                if (!fillMask[y][x]) continue;
                int sx = (int)Math.floor(offX + x + 0.5);
                if (sx < 0 || sx >= snap.getWidth()) continue;
                total++;
                if (pr.getColor(sx, sy).getOpacity() > PIX_OPACITY_THRESHOLD) done++;
            }
        }
        if (total == 0) return rough;
        return Math.max(rough, (double)done / total);
    }

    /* ---------- progress & UI ---------- */

    private void updateLocksAndProgress() {
        shapeMenu.setDisable(false); // menu always enabled
        double cov = effectiveCoverage();
        progressBar.setProgress(inProgress ? cov : 1.0);
        if (inProgress) updatePercentLabel(cov); else percentLabel.setText("100%");
    }

    private int updatePercentLabel(double cov) {
        int pct = (int) Math.floor(cov * 100.0 + 1e-9);
        if (pct >= 100 && cov < COMPLETE_THRESHOLD) pct = 99;
        percentLabel.setText(pct + "%");
        percentLabel.setTooltip(new Tooltip("Filled: " + pct + "%"));
        return pct;
    }

    /* ---------- Voice buttons ---------- */

    @FXML private void handleSpeakShape() {
        if (currentShape != null) speakNow("This is " + currentShape + ".");
    }

    @FXML private void handleSpeakColor() {
        speakNow("This is " + currentColorName + " color.");
    }

    @FXML private void handleSpeakBoth() {
        if (currentShape != null) {
            String list = colorListForSpeech();
            speakNow("You colored the " + currentShape + " " + list + ".");
        }
    }

    private void showCongratsAndSpeak() {
        String list = colorListForSpeech();
        speakNow("You colored the " + currentShape + " " + list + ".");
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Great Job!");
        alert.setHeaderText("Congratulations!");
        alert.setContentText("You finished coloring the " + currentShape + " using " + list + "!");
        alert.showAndWait();
    }

    private String colorListForSpeech() {
        if (usedColorNames.isEmpty()) return currentColorName;
        var list = new ArrayList<>(usedColorNames);
        if (list.size() == 1) return list.get(0);
        return String.join(", ", list.subList(0, list.size()-1)) + " and " + list.get(list.size()-1);
    }

    /* ---------- helpers ---------- */

    private void clear(Canvas c) { c.getGraphicsContext2D().clearRect(0, 0, c.getWidth(), c.getHeight()); }

    private WritableImage snapshotFilled(Group g) {
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        Bounds bb = g.getLayoutBounds();
        return g.snapshot(sp, new WritableImage(
                (int)Math.ceil(Math.max(1, bb.getWidth())),
                (int)Math.ceil(Math.max(1, bb.getHeight()))
        ));
    }

    private Group cloneFilledGroup(Group src) {
        Group dst = new Group();
        for (javafx.scene.Node n : src.getChildren()) {
            if (n instanceof Circle c) {
                dst.getChildren().add(new Circle(c.getCenterX(), c.getCenterY(), c.getRadius(), Color.BLACK));
            } else if (n instanceof Ellipse e) {
                Ellipse copy = new Ellipse(e.getCenterX(), e.getCenterY(), e.getRadiusX(), e.getRadiusY());
                copy.setFill(Color.BLACK); dst.getChildren().add(copy);
            } else if (n instanceof Rectangle r) {
                Rectangle copy = new Rectangle(r.getX(), r.getY(), r.getWidth(), r.getHeight());
                copy.setFill(Color.BLACK); dst.getChildren().add(copy);
            } else if (n instanceof Polygon p) {
                Polygon copy = new Polygon(); copy.getPoints().addAll(p.getPoints()); copy.setFill(Color.BLACK);
                copy.setRotate(p.getRotate()); dst.getChildren().add(copy);
            }
        }
        return dst;
    }

    private Group createStrokedFromFilled(Group src, double stroke) {
        Group dst = new Group();
        for (javafx.scene.Node n : src.getChildren()) {
            Shape copy = null;
            if (n instanceof Circle c) copy = new Circle(c.getCenterX(), c.getCenterY(), c.getRadius());
            else if (n instanceof Ellipse e) copy = new Ellipse(e.getCenterX(), e.getCenterY(), e.getRadiusX(), e.getRadiusY());
            else if (n instanceof Rectangle r) copy = new Rectangle(r.getX(), r.getY(), r.getWidth(), r.getHeight());
            else if (n instanceof Polygon p) { Polygon poly = new Polygon(); poly.getPoints().addAll(p.getPoints()); poly.setRotate(p.getRotate()); copy = poly; }
            if (copy != null) {
                copy.setFill(Color.TRANSPARENT);
                copy.setStroke(Color.BLACK);
                copy.setStrokeWidth(stroke);
                copy.setStrokeLineJoin(StrokeLineJoin.ROUND);
                copy.setStrokeLineCap(StrokeLineCap.ROUND);
                dst.getChildren().add(copy);
            }
        }
        return dst;
    }

    /** Filled-black geometry sized to fit within availW x availH at (0,0). */
    private Group createShapeGroup(String shape, double availW, double availH) {
        Group g = new Group();
        switch (shape.toLowerCase()) {
            case "circle" -> {
                double s = Math.min(availW, availH);
                g.getChildren().add(new Circle(s/2, s/2, s/2, Color.BLACK));
            }
            case "square" -> {
                double s = Math.min(availW, availH);
                Rectangle r = new Rectangle(0, 0, s, s); r.setFill(Color.BLACK);
                g.getChildren().add(r);
            }
            case "rectangle" -> {
                double w = Math.min(availW, availH * 4.0/3.0);
                double h = w * 3.0/4.0;
                Rectangle r = new Rectangle(0, 0, w, h); r.setFill(Color.BLACK);
                g.getChildren().add(r);
            }
            case "oval" -> {
                double w = availW, h = availH;
                double aspect = 1.6;
                if (w/h > aspect) w = h * aspect; else h = w / aspect;
                Ellipse e = new Ellipse(w/2, h/2, w/2, h/2); e.setFill(Color.BLACK);
                g.getChildren().add(e);
            }
            case "triangle" -> {
                double s = Math.min(availW, availH);
                double h = s * Math.sqrt(3)/2.0;
                Polygon p = new Polygon(0.0,h, s/2.0,0.0, s,h); p.setFill(Color.BLACK);
                g.getChildren().add(p);
            }
            case "star" -> {
                double s = Math.min(availW, availH);
                double cx = s/2.0, cy = s/2.0, rO = s/2.0, rI = rO*0.5;
                Polygon star = new Polygon();
                for (int i = 0; i < 10; i++) {
                    double a = Math.toRadians(-90 + i*36);
                    double r = (i%2==0)? rO : rI;
                    star.getPoints().addAll(cx + r*Math.cos(a), cy + r*Math.sin(a));
                }
                star.setFill(Color.BLACK); g.getChildren().add(star);
            }
            case "diamond" -> {
                double w = availW, h = availH;
                Polygon rh = new Polygon(w/2,0, w,h/2, w/2,h, 0,h/2); rh.setFill(Color.BLACK);
                g.getChildren().add(rh);
            }
            case "pentagon" -> g.getChildren().add(regularPolygon(5, availW, availH));
            case "hexagon" -> g.getChildren().add(regularPolygon(6, availW, availH));
            case "trapezoid" -> {
                double w = availW, h = availH;
                Polygon t = new Polygon(w*0.2,0, w*0.8,0, w,h, 0,h); t.setFill(Color.BLACK);
                g.getChildren().add(t);
            }
            case "parallelogram" -> {
                double w = availW, h = availH;
                Polygon p = new Polygon(w*0.2,0, w,0, w*0.8,h, 0,h); p.setFill(Color.BLACK);
                g.getChildren().add(p);
            }
            case "right arrow" -> g.getChildren().add(arrowBase(availW, availH, 0));
            case "left arrow"  -> g.getChildren().add(arrowBase(availW, availH, 180));
            case "up arrow"    -> g.getChildren().add(arrowBase(availW, availH, -90));
            case "down arrow"  -> g.getChildren().add(arrowBase(availW, availH, 90));
            default -> {
                double s = Math.min(availW, availH);
                g.getChildren().add(new Circle(s/2, s/2, s/2, Color.BLACK));
            }
        }
        return g;
    }

    private Polygon regularPolygon(int sides, double availW, double availH) {
        double s = Math.min(availW, availH), cx = s/2.0, cy = s/2.0, r = s/2.0;
        List<Double> pts = new ArrayList<>(sides*2);
        for (int i = 0; i < sides; i++) {
            double ang = Math.toRadians(-90 + (360.0/sides) * i);
            pts.add(cx + r*Math.cos(ang)); pts.add(cy + r*Math.sin(ang));
        }
        Polygon poly = new Polygon(); poly.getPoints().addAll(pts); poly.setFill(Color.BLACK);
        return poly;
    }

    private Polygon arrowBase(double w, double h, double rotate) {
        Polygon a = new Polygon(
                0.0,   h*0.35,
                w*0.6, h*0.35,
                w*0.6, 0.0,
                w,     h*0.5,
                w*0.6, h,
                w*0.6, h*0.65,
                0.0,   h*0.65
        );
        a.setFill(Color.BLACK);
        a.setRotate(rotate);
        return a;
    }

    /* ---------- Voice base ---------- */
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
                    Object v  = vm.getClass().getMethod("getVoice", String.class).invoke(vm, "kevin16");
                    if (v == null) throw new IllegalStateException("FreeTTS voice 'kevin16' not found.");
                    v.getClass().getMethod("allocate").invoke(v);
                    try { v.getClass().getMethod("setRate", float.class).invoke(v, 140f); } catch (NoSuchMethodException ignored) {}
                    v.getClass().getMethod("speak", String.class).invoke(v, text);
                    v.getClass().getMethod("deallocate").invoke(v);
                    return;
                } catch (Exception e) { last = e; }
            }
            String os = System.getProperty("os.name","").toLowerCase();
            if (os.contains("mac")) {
                runAndWait(new String[]{"say","-r","150", text}); return;
            } else if (os.contains("win")) {
                runAndWait(new String[]{"powershell","-NoProfile","-Command",
                        "$s=New-Object -ComObject SAPI.SpVoice; $s.Rate=-1; $s.Speak('"
                                + text.replace("'", "''") + "');"}); return;
            } else if (os.contains("linux")) {
                runAndWait(new String[]{"bash","-lc",
                        "(command -v espeak-ng >/dev/null && espeak-ng -s 155 '" + text + "') || " +
                                "(command -v espeak >/dev/null && espeak -s 155 '" + text + "')"}); return;
            }
            if (last != null) throw last;
            throw new IllegalStateException("No TTS available.");
        }
        private static void runAndWait(String[] cmd) throws Exception {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.waitFor();
        }
    }

    private void speakNow(String text) {
        new Thread(() -> {
            try { voice.speak(text); }
            catch (Exception ex) { System.err.println("[TTS] Failed: " + ex.getMessage()); }
        }, "tts-thread").start();
    }

    /* ---------- Color item ---------- */
    public static final class ColorItem {
        public final String name;
        public final Color color;
        public ColorItem(String name, Color color) { this.name = name; this.color = color; }
        @Override public String toString() { return name; }
    }
}
