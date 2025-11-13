package com.example.aoopproject;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

public class CollabDrawingController {

    @FXML private Canvas canvas;
    @FXML private TextField usernameField, roomField;
    @FXML private ColorPicker colorPicker;
    @FXML private ToggleButton pencilBtn, brushBtn, eraserBtn;
    @FXML private Slider pencilSize, brushSize, eraserSize;
    @FXML private TextArea chatArea;
    @FXML private TextField chatInput;

    private CollabClient collab;
    private GraphicsContext g;
    private boolean joined = false;

    private enum Tool { PENCIL, BRUSH, ERASER }
    private Tool currentTool = Tool.PENCIL;

    @FXML
    public void initialize() {
        g = canvas.getGraphicsContext2D();
        colorPicker.setValue(Color.BLACK);

        // Tool selection logic
        pencilBtn.setOnAction(e -> selectTool(Tool.PENCIL));
        brushBtn.setOnAction(e -> selectTool(Tool.BRUSH));
        eraserBtn.setOnAction(e -> selectTool(Tool.ERASER));

        // Chat enter key
        chatInput.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) onSendChat(null);
        });

        // Mouse events
        canvas.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> drawEvent("DOWN", e.getX(), e.getY()));
        canvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> drawEvent("MOVE", e.getX(), e.getY()));
        canvas.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> drawEvent("UP", e.getX(), e.getY()));
    }

    private void selectTool(Tool tool) {
        currentTool = tool;
        pencilBtn.setSelected(tool == Tool.PENCIL);
        brushBtn.setSelected(tool == Tool.BRUSH);
        eraserBtn.setSelected(tool == Tool.ERASER);

        pencilSize.setVisible(tool == Tool.PENCIL);
        pencilSize.setManaged(tool == Tool.PENCIL);
        brushSize.setVisible(tool == Tool.BRUSH);
        brushSize.setManaged(tool == Tool.BRUSH);
        eraserSize.setVisible(tool == Tool.ERASER);
        eraserSize.setManaged(tool == Tool.ERASER);
    }

    private void drawEvent(String phase, double x, double y) {
        if (!joined) return;

        DrawParams p = paramsForCurrentTool();
        collab.sendDraw(phase, x, y, p.colorHex(), p.width());
        drawLocal(phase, x, y, p.colorHex(), p.width(), p.capRound(), p.joinRound());
    }

    private record DrawParams(String colorHex, double width, boolean capRound, boolean joinRound) {}

    private DrawParams paramsForCurrentTool() {
        return switch (currentTool) {
            case ERASER -> new DrawParams("#FFFFFF", eraserSize.getValue(), true, true);
            case BRUSH -> new DrawParams(toHex(colorPicker.getValue()), brushSize.getValue(), true, true);
            case PENCIL -> new DrawParams(toHex(colorPicker.getValue()), pencilSize.getValue(), false, false);
        };
    }

    private void drawLocal(String phase, double x, double y, String colorHex, double width, boolean capRound, boolean joinRound) {
        g.setStroke(Color.web(colorHex));
        g.setLineWidth(width);
        g.setLineCap(capRound ? StrokeLineCap.ROUND : StrokeLineCap.BUTT);
        g.setLineJoin(joinRound ? StrokeLineJoin.ROUND : StrokeLineJoin.MITER);

        switch (phase) {
            case "DOWN" -> { g.beginPath(); g.moveTo(x, y); }
            case "MOVE" -> { g.lineTo(x, y); g.stroke(); }
            case "UP"   -> g.closePath();
        }
    }

    @FXML
    public void onJoin(ActionEvent evt) {
        if (joined) return;
        try {
            String user = usernameField.getText().trim();
            String room = roomField.getText().trim();
            if (user.isEmpty() || room.isEmpty()) {
                showAlert("Error", "Please enter username and room name.");
                return;
            }

            collab = new CollabClient("127.0.0.1", 6001);
            collab.setOnMessage(this::handleMessage);
            collab.connect(user, room);
            joined = true;
            showAlert("Joined", "You joined room: " + room);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void onLeave(ActionEvent evt) {
        if (!joined) return;
        collab.leave();
        joined = false;
        showAlert("Left", "You left the room.");
    }

    @FXML
    public void onClear(ActionEvent evt) {
        g.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    @FXML
    public void onSendChat(ActionEvent evt) {
        if (!joined) return;
        String msg = chatInput.getText().trim();
        if (msg.isEmpty()) return;
        collab.sendChat(msg);
        appendChat("You: " + msg);
        chatInput.clear();
    }

    private void handleMessage(String line) {
        if (line.startsWith("EVT ")) {
            String[] p = line.split("\\s+");
            if (p.length >= 7) {
                double x = Double.parseDouble(p[3]);
                double y = Double.parseDouble(p[4]);
                String colorHex = p[5];
                double width = Double.parseDouble(p[6]);
                Platform.runLater(() -> drawLocal(p[2], x, y, colorHex, width, true, true));
            }
        } else if (line.startsWith("CHAT ")) {
            String[] parts = line.split(" ", 3);
            if (parts.length >= 3) {
                Platform.runLater(() -> appendChat(parts[1] + ": " + parts[2]));
            }
        } else if (line.startsWith("INFO ")) {
            Platform.runLater(() -> appendChat("[Info] " + line.substring(5)));
        }
    }

    private void appendChat(String msg) {
        chatArea.appendText(msg + "\n");
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int)(c.getRed()*255),
                (int)(c.getGreen()*255),
                (int)(c.getBlue()*255));
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.show();
    }

    @FXML
    public void home(ActionEvent e) {
        ChangeFxmlController.switchScene(e, "menu.fxml");
    }
}
