package com.example.aoopproject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

public class DrawingController {

    // Toolbar controls
    @FXML private MenuButton shapesMenuBtn;
    @FXML private ColorPicker colorPicker;
    @FXML private Label hexLabel;
    @FXML private ToggleButton pencilBtn;
    @FXML private ToggleButton eraserBtn;
    @FXML private Slider sizeSlider;
    @FXML private Label percentLabel;

    // Canvases
    @FXML private StackPane canvasContainer;
    @FXML private Canvas drawCanvas;     // permanent
    @FXML private Canvas overlayCanvas;  // preview

    private GraphicsContext gc;   // draw
    private GraphicsContext ogc;  // overlay

    private enum Tool { PENCIL, ERASER, LINE, RECTANGLE, CIRCLE, TRIANGLE }
    private Tool currentTool = Tool.PENCIL;

    private double startX, startY;
    private final Color backgroundColor = Color.WHITE;

    @FXML
    private void initialize() {
        // Set defaults and bind UI bits
        gc  = drawCanvas.getGraphicsContext2D();
        ogc = overlayCanvas.getGraphicsContext2D();

        colorPicker.setValue(Color.web("#1e90ff"));
        hexLabel.setText(toHex(colorPicker.getValue()));
        colorPicker.valueProperty().addListener((obs, o, n) -> {
            hexLabel.setText(toHex(n));
        });

        // Percent label mimics the “0%” style in your screenshot
        sizeSlider.valueProperty().addListener((obs, o, n) -> {
            double pct = (n.doubleValue() - sizeSlider.getMin()) /
                    (sizeSlider.getMax() - sizeSlider.getMin());
            percentLabel.setText(Math.round(pct * 100) + "%");
        });
        sizeSlider.setValue(4); // triggers the listener

        // Toggle state
        pencilBtn.setSelected(true);
        eraserBtn.setSelected(false);

        // Fill background and prep overlay
        fillBackground();
        clearOverlay();

        // Mouse handlers on overlay so previews are clean
        overlayCanvas.addEventHandler(MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        overlayCanvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);
        overlayCanvas.addEventHandler(MouseEvent.MOUSE_RELEASED, this::onMouseReleased);

        overlayCanvas.setMouseTransparent(false);

        // Resize canvas when window grows (keeps white fill)
        canvasContainer.widthProperty().addListener((obs, o, w) -> resizeCanvases(w.doubleValue(), canvasContainer.getHeight()));
        canvasContainer.heightProperty().addListener((obs, o, h) -> resizeCanvases(canvasContainer.getWidth(), h.doubleValue()));
    }

    // ---------- Navigation ----------
    @FXML
    void BackToMenu(ActionEvent event) {
        ChangeFxmlController.switchScene(event, "menu.fxml");
    }

    // ---------- Tool selection ----------
    @FXML private void selectPencil(ActionEvent e) { setTool(Tool.PENCIL); }
    @FXML private void selectLine(ActionEvent e)   { setTool(Tool.LINE); }
    @FXML private void selectRectangle(ActionEvent e) { setTool(Tool.RECTANGLE); }
    @FXML private void selectCircle(ActionEvent e) { setTool(Tool.CIRCLE); }
    @FXML private void selectTriangle(ActionEvent e) { setTool(Tool.TRIANGLE); }

    @FXML
    private void toggleEraser(ActionEvent e) {
        if (eraserBtn.isSelected()) {
            setTool(Tool.ERASER);
            pencilBtn.setSelected(false);
        } else {
            setTool(Tool.PENCIL);
            pencilBtn.setSelected(true);
        }
    }

    @FXML
    private void clearCanvas(ActionEvent e) {
        fillBackground();
        clearOverlay();
    }

    // ---------- Mouse handling ----------
    private void onMousePressed(MouseEvent e) {
        startX = e.getX();
        startY = e.getY();

        if (currentTool == Tool.PENCIL || currentTool == Tool.ERASER) {
            gc.beginPath();
            gc.setLineWidth(sizeSlider.getValue());
            gc.setStroke(currentTool == Tool.ERASER ? backgroundColor : colorPicker.getValue());
            gc.moveTo(startX, startY);
        } else {
            clearOverlay();
        }
    }

    private void onMouseDragged(MouseEvent e) {
        final double x = e.getX();
        final double y = e.getY();

        switch (currentTool) {
            case PENCIL:
            case ERASER:
                gc.lineTo(x, y);
                gc.stroke();
                break;

            case LINE:
                preview(() -> ogc.strokeLine(startX, startY, x, y));
                break;

            case RECTANGLE:
                preview(() -> {
                    double rx = Math.min(startX, x);
                    double ry = Math.min(startY, y);
                    double rw = Math.abs(x - startX);
                    double rh = Math.abs(y - startY);
                    ogc.strokeRect(rx, ry, rw, rh);
                });
                break;

            case CIRCLE:
                preview(() -> {
                    double rx = Math.min(startX, x);
                    double ry = Math.min(startY, y);
                    double rw = Math.abs(x - startX);
                    double rh = Math.abs(y - startY);
                    ogc.strokeOval(rx, ry, rw, rh);
                });
                break;

            case TRIANGLE:
                preview(() -> {
                    // Isosceles triangle: top vertex at (startX,startY), base centered at current Y
                    double baseHalf = Math.abs(x - startX);
                    double baseY = Math.max(startY, y);
                    double[] xs = { startX, startX - baseHalf, startX + baseHalf };
                    double[] ys = { startY, baseY, baseY };
                    ogc.strokePolygon(xs, ys, 3);
                });
                break;
        }
    }

    private void onMouseReleased(MouseEvent e) {
        final double x = e.getX();
        final double y = e.getY();

        switch (currentTool) {
            case PENCIL:
            case ERASER:
                gc.closePath();
                break;

            case LINE:
                apply(() -> gc.strokeLine(startX, startY, x, y));
                break;

            case RECTANGLE:
                apply(() -> {
                    double rx = Math.min(startX, x);
                    double ry = Math.min(startY, y);
                    double rw = Math.abs(x - startX);
                    double rh = Math.abs(y - startY);
                    gc.strokeRect(rx, ry, rw, rh);
                });
                break;

            case CIRCLE:
                apply(() -> {
                    double rx = Math.min(startX, x);
                    double ry = Math.min(startY, y);
                    double rw = Math.abs(x - startX);
                    double rh = Math.abs(y - startY);
                    gc.strokeOval(rx, ry, rw, rh);
                });
                break;

            case TRIANGLE:
                apply(() -> {
                    double baseHalf = Math.abs(x - startX);
                    double baseY = Math.max(startY, y);
                    double[] xs = { startX, startX - baseHalf, startX + baseHalf };
                    double[] ys = { startY, baseY, baseY };
                    gc.strokePolygon(xs, ys, 3);
                });
                break;
        }
        clearOverlay();
    }

    // ---------- Helpers ----------
    private void setTool(Tool tool) {
        currentTool = tool;

        // keep toggles in sync
        pencilBtn.setSelected(currentTool == Tool.PENCIL);
        eraserBtn.setSelected(currentTool == Tool.ERASER);

        ogc.setLineWidth(sizeSlider.getValue());
        ogc.setStroke(colorPicker.getValue());
    }

    private void preview(Runnable painter) {
        clearOverlay();
        ogc.setLineWidth(sizeSlider.getValue());
        ogc.setStroke(colorPicker.getValue());
        painter.run();
    }

    private void apply(Runnable painter) {
        gc.setLineWidth(sizeSlider.getValue());
        gc.setStroke(colorPicker.getValue());
        painter.run();
    }

    private void clearOverlay() {
        ogc.clearRect(0, 0, overlayCanvas.getWidth(), overlayCanvas.getHeight());
    }

    private void fillBackground() {
        gc.setFill(backgroundColor);
        gc.fillRect(0, 0, drawCanvas.getWidth(), drawCanvas.getHeight());
    }

    private void resizeCanvases(double w, double h) {
        if (w <= 0 || h <= 0) return;

        // Snapshot current drawing
        var img = drawCanvas.snapshot(null, null);

        drawCanvas.setWidth(w);
        drawCanvas.setHeight(h);
        overlayCanvas.setWidth(w);
        overlayCanvas.setHeight(h);

        // Repaint background then previous content
        fillBackground();
        gc.drawImage(img, 0, 0);
        clearOverlay();
    }

    private String toHex(Color c) {
        int r = (int)Math.round(c.getRed()*255);
        int g = (int)Math.round(c.getGreen()*255);
        int b = (int)Math.round(c.getBlue()*255);
        return String.format("#%02x%02x%02x", r, g, b);
    }
}
