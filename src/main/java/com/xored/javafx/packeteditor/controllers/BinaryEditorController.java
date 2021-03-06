package com.xored.javafx.packeteditor.controllers;

import com.xored.javafx.packeteditor.data.BinaryData;
import com.xored.javafx.packeteditor.data.IBinaryData;
import com.xored.javafx.packeteditor.scapy.ScapyUtils;
import com.xored.javafx.packeteditor.service.PacketDataService;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Group;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import javax.inject.Inject;
import java.net.URL;
import java.util.Observable;
import java.util.Observer;
import java.util.ResourceBundle;

public class BinaryEditorController implements Initializable, Observer {
    @FXML private Group beGroup;
    @FXML private ScrollPane beGroupScrollPane;
    @Inject private IBinaryData binaryData;
    @Inject
    PacketDataService packetController;

    boolean updating = false;

    private Text[][] texts;
    private Text[] lineNums;
    private Text[] lineHex;
    private Rectangle[] selRect = new Rectangle[3];
    private Rectangle editingRect = new Rectangle();

    double xOffset = 10;
    double yOffset = 20;
    double numLineLength = 45;
    double byteLength = 15;
    double bytePad = 5;
    double byteWordPad = 15;

    int idxEditing = -1;
    int editingStep = 0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        reloadAll();
        binaryData.getObservable().addObserver(this);

        ChangeListener<Number> sizeListener = new ChangeListener<Number>() {
            public void changed(ObservableValue<? extends Number> ov,
                                Number old_val, Number new_val) {
                Rectangle clip = new Rectangle(0
                        , 0
                        , beGroupScrollPane.getLayoutBounds().getWidth()
                        , beGroupScrollPane.getLayoutBounds().getHeight());
                beGroupScrollPane.setClip(clip);
            }
        };

        beGroupScrollPane.widthProperty().addListener(sizeListener);
        beGroupScrollPane.heightProperty().addListener(sizeListener);
    }

    private void reloadAll() {
        int len = binaryData.getLength();
        final int w = 16;
        final int h = len/w + ((0 < len % w) ? 1 : 0);

        texts = new Text[h][];
        lineNums = new Text[h];
        lineHex = new Text[h];

        beGroup.getChildren().clear();
        for (int i = 0; i < selRect.length; i++) {
            selRect[i] = new Rectangle();
            selRect[i].setFill(Color.AQUAMARINE);
            selRect[i].setTranslateZ(0);

            beGroup.getChildren().add(selRect[i]);
        }

        beGroup.getChildren().add(editingRect);
        for (int i = 0; i < h; i++) {
            texts[i] = new Text[w];
            lineNums[i] = new Text(String.format("%04X", i * w) + ':');
            lineHex[i] = new Text(convertHexToString(binaryData.getBytes(i*w, w)));

            lineNums[i].setTranslateX(xOffset);
            lineNums[i].setTranslateY(yOffset * (i+1));
            lineNums[i].setFont(Font.font("monospace"));


            lineHex[i].setTranslateX(numLineLength + xOffset + w * byteLength + w * bytePad + (w/4 - 1) * byteWordPad + xOffset);
            lineHex[i].setTranslateY(yOffset * (i+1));
            lineHex[i].setFont(Font.font("monospace"));

            beGroup.getChildren().addAll(lineNums[i], lineHex[i]);

            for (int j = 0; j < w &&  (i * w + j < len); j++) {
                final int f_i = i;
                final int f_j = j;
                final int idx = i * w + j;

                final Text text = new Text();
                byte currentByte = binaryData.getByte(idx);
                String hexByte = String.format("%02X", currentByte);
                text.setText(hexByte);


                text.setTranslateX(numLineLength + xOffset + j * bytePad + (j/4) * byteWordPad + byteLength * j);
                text.setTranslateY(yOffset * (i+1));
                text.setTranslateZ(100);

                text.setFont(Font.font("monospace"));

                texts[i][j] = text;

                text.setOnMouseClicked(new EventHandler<MouseEvent>() {
                    @Override
                    public void handle(MouseEvent mouseEvent) {
                        if(mouseEvent.getButton().equals(MouseButton.PRIMARY)){
                            if(mouseEvent.getClickCount() == 2){
                                startEditing(idx);
                                beGroup.requestFocus();
                            }
                        }
                    }
                });

                text.setOnKeyPressed((KeyEvent ke) -> {
                    System.out.println("123");
                });


                beGroup.getChildren().add(text);
                beGroup.setFocusTraversable(true);
            }
        }

        beGroup.setOnKeyPressed((KeyEvent ke) -> {
            if (-1 != idxEditing) {
                try {
                    Integer val = Integer.parseInt(ke.getText(), 16);

                    int b = binaryData.getByte(idxEditing);
                    if (0 == editingStep) {
                        b &= 0x0FFFF0F;
                    } else {
                        b &= 0x0FFFFF0;
                    }
                    b |= val << (1 - editingStep) * 4;
                    binaryData.setByte(idxEditing, (byte)b);
                    byte[] newBytes = binaryData.getBytes(0, binaryData.getLength());
                    packetController.reconstructPacketFromBinary(newBytes);

                    int i = idxEditing / texts[0].length;
                    int j = idxEditing % texts[0].length;

                    updating = true;
                    //String.format("%02X", b).getChars(0, 2, symbols, 0);
                    texts[i][j].setText( String.format("%02X", (byte)b));
                    lineHex[i].setText(convertHexToString(binaryData.getBytes(i*texts[i].length,  texts[i].length)));
                    updating = false;


                    editingStep ++;
                    if (editingStep == 2) {
                        editingStep = 0;
                        idxEditing = -1;
                        editingRect.setWidth(0);
                        editingRect.setHeight(0);
                    }
                } catch (Exception e) {
                    //slip
                }
            }
        });
    }

    private String convertHexToString(byte[] hex) {
        StringBuilder sb = new StringBuilder();
        for (byte decimal : hex) {
            if (ScapyUtils.isPrintableChar(decimal)) {
                sb.append((char) decimal);
            } else {
                sb.append('.');
            }
        }
        return sb.toString();
    }

    @Override
    public void update(Observable o, Object arg) {
        if ((o == binaryData) && (BinaryData.OP.SET_BYTES.equals(arg))) {
            updating = true;
            for (int i = 0; i < texts.length; i++) {
                for (int j = 0; j < texts[i].length; j++) {
                    int idx = i * texts[i].length + j;
                    if (idx >= binaryData.getLength()) {
                        break;
                    }
                    texts[i][j].setText(String.format("%02X", (byte) binaryData.getByte(idx)));
                }
                lineHex[i].setText(convertHexToString(binaryData.getBytes(i*texts[i].length,  texts[i].length)));
            }
            updating = false;
        }
        if ((o == binaryData) && (BinaryData.OP.SELECTION.equals(arg))) {
            updateSelection();
        }
        if ((o == binaryData) && (BinaryData.OP.RELOAD.equals(arg))) {
            reloadAll();
        }
    }

    private void updateSelection() {
        int startIdx = binaryData.getSelOffset();
        int endIdx = startIdx + Math.max(0, binaryData.getSelLength() - 1);

        int startRow = getByteCellRow(startIdx);
        int endRow = getByteCellRow(endIdx);

        int startColumn = getByteCellColumn(startIdx);
        int endColumn = getByteCellColumn(endIdx);
        double startX = getByteCellX(startColumn);
        double endX = getByteCellX(endColumn);

        double lineStartX = getByteCellX(0);
        double lineEndX = getByteCellX(texts[0].length - 1);

        if (startRow == endRow) {
            selRect[0].setTranslateX(startX);
            selRect[0].setWidth(endX - startX + byteLength);
            selRect[0].setHeight(yOffset);
            selRect[0].setTranslateY(startRow * yOffset + 5);

            for (int i = 1; i < 3; i++) {
                selRect[i].setWidth(0);
                selRect[i].setHeight(0);
            }

        } else if (endRow == startRow + 1) {
            selRect[0].setTranslateX(startX);
            selRect[0].setWidth(lineEndX - startX + byteLength);
            selRect[0].setHeight(yOffset);
            selRect[0].setTranslateY(startRow * yOffset + 5);

            selRect[1].setTranslateX(lineStartX);
            selRect[1].setWidth(endX - lineStartX + byteLength);
            selRect[1].setHeight(yOffset);
            selRect[1].setTranslateY(endRow * yOffset + 5);

            selRect[2].setWidth(0);
            selRect[2].setHeight(0);
        } else {
            selRect[0].setTranslateX(startX);
            selRect[0].setWidth(lineEndX - startX + byteLength);
            selRect[0].setHeight(yOffset);
            selRect[0].setTranslateY(startRow * yOffset + 5);

            selRect[1].setTranslateX(lineStartX);
            selRect[1].setWidth(endX - lineStartX + byteLength);
            selRect[1].setHeight(yOffset);
            selRect[1].setTranslateY(endRow * yOffset + 5);

            selRect[2].setTranslateX(lineStartX);
            selRect[2].setWidth(lineEndX - lineStartX + byteLength);
            selRect[2].setHeight(yOffset * (endRow - startRow - 1));
            selRect[2].setTranslateY((startRow + 1) * yOffset + 5);
        }
    }

    private void startEditing(int idx) {
        idxEditing = idx;
        int ty = getByteCellRow(idx);
        int tx = getByteCellColumn(idx);

        double x = getByteCellX(tx) - bytePad/2;

        editingRect.setTranslateX(x);
        editingRect.setWidth(byteLength + bytePad);
        editingRect.setHeight(yOffset);
        editingRect.setTranslateY(ty * yOffset + 5);
        editingRect.setTranslateZ(0);

        editingRect.setFill(Color.WHITE);
        editingRect.setStroke(Color.BLACK);
    }

    private int getByteCellRow(int idx) {
        return idx / texts[0].length;
    }

    private int getByteCellColumn(int idx) {
        return idx % texts[0].length;
    }

    private double getByteCellX(int idx) {
        int xi = getByteCellColumn(idx);
        return numLineLength + xOffset + xi * bytePad + (xi/4) * byteWordPad + byteLength * xi;
    }
}
