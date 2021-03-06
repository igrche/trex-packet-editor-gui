package com.xored.javafx.packeteditor.controls;

import com.google.inject.Injector;
import com.xored.javafx.packeteditor.TRexPacketCraftingTool;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.util.Random;

public class PayloadEditor extends VBox {
    static org.slf4j.Logger logger = LoggerFactory.getLogger(TRexPacketCraftingTool.class);

    @FXML private VBox root;
    @FXML private HBox payloadEditorHboxChoice;
    @FXML private HBox payloadEditorHboxValue;
    @FXML private GridPane payloadEditorGrid;

    // Choice and save button
    @FXML private ComboBox<String> payloadChoiceType;
    @FXML private Button payloadButtonSave;
    @FXML private Button payloadButtonCancel;

    // Payload from text
    @FXML private TextArea textText;

    // Payload from file
    @FXML private TextField textFilename;
    @FXML private Button    textFilenameButton;

    // Payload generation from text pattern
    @FXML private TextField textPatternSize;
    @FXML private TextArea  textPatternText;

    // Payload generation from file pattern
    @FXML private TextField filePatternSize;
    @FXML private TextField filePatternFilename;
    @FXML private Button    filePatternButton;

    // Payload random ascii generation
    @FXML private TextField randomAsciiSize;

    // Payload random non-ascii generation
    @FXML private TextField randomNonAsciiSize;


    public enum PayloadType {
        UNKNOWN         (-1, "UNKNOWN"),
        TEXT            (0, "Text"),
        FILE            (1, "File"),
        TEXT_PATTERN    (2, "Text pattern"),
        FILE_PATTERN    (3, "File pattern"),
        RANDOM_ASCII    (4, "Random ASCII"),
        RANDOM_NON_ASCII(5, "Random non ASCII");

        private final int    index;
        private final String human;

        PayloadType(int index, String human) {
            this.index = index;
            this.human = human;
        }

        public int    index() { return index; }
        public String human() { return human; }
    }

    public enum EditorMode {
        UNKNOWN (-1, "UNKNOWN"),
        READ    (0, "Reading mode"),
        EDIT    (1, "Editing mode");

        private final int    mode;
        private final String human;

        EditorMode(int mode, String human) {
            this.mode = mode;
            this.human = human;
        }

        public int    mode()  { return mode; }
        public String human() { return human; }
    }

    static private Random rand = new Random();

    public static void randomBytes(boolean ascii, byte[] bytes) {
        int mask = 0xFF;
        if (ascii) mask = 0x7F;
        for (int i = 0; i < bytes.length; ) {
            for (int rnd = rand.nextInt(), n = Math.min(bytes.length - i, 4); n-- > 0; rnd >>= 8) {
                bytes[i++] = (byte) (rnd & mask);
            }
        }
    }

    private EventHandler<ActionEvent> handlerActionSaveExternal;
    private PayloadType type = PayloadType.UNKNOWN;
    private EditorMode  mode = EditorMode.UNKNOWN;
    private byte[]      data;

    private ChangeListener<String> onlyNumberListener = (observable, oldValue, newValue) -> {
        if (!newValue.matches("\\d*"))
            ((StringProperty) observable).set(oldValue);
    };

    private EventHandler<ActionEvent> handlerActionSaveInternal = (event) -> {
        boolean itsok = false;

        // First call our code
        if (type == PayloadType.TEXT) {
            itsok = true;
        }
        else if (type == PayloadType.FILE) {
            itsok = file2text();
        }
        else if (type == PayloadType.TEXT_PATTERN) {
            itsok = textpattern2text();
        }
        else if (type == PayloadType.FILE_PATTERN) {
            itsok = filepattern2text();
        }
        else if (type == PayloadType.RANDOM_ASCII) {
            itsok = generateRandomBytes(true);
            if (itsok) {
                setText(new String(data));
            }
        }
        else if (type == PayloadType.RANDOM_NON_ASCII) {
            itsok = generateRandomBytes(false);
            if (itsok) {
                setText(new String(data));
            }
        }

        if (itsok) {
            if (handlerActionSaveExternal != null) {
                handlerActionSaveExternal.handle(event);
            }
        }
    };

    public PayloadEditor(Injector injector) {
        FXMLLoader fxmlLoader = injector.getInstance(FXMLLoader.class);

        fxmlLoader.setLocation(ClassLoader.getSystemResource("com/xored/javafx/packeteditor/controllers/PayloadEditor.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (Exception e) {
            logger.error(e.getMessage());
        }

        payloadChoiceType.setOnAction((event) -> {
            int index = payloadChoiceType.getSelectionModel().getSelectedIndex();
            if (index >= 0) {
                payloadEditorHboxValue.setVisible(true);
                payloadEditorHboxValue.setManaged(true);
                gridSetVisible(payloadEditorGrid, index);
                setType(int2type(index));
                setMode(EditorMode.EDIT);
            }
            else {
                setType(PayloadType.UNKNOWN);
            }
        });

        payloadButtonSave.setOnAction(handlerActionSaveInternal);

        textFilenameButton.setOnAction((event) -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select payload text file");
            File file = fileChooser.showOpenDialog(this.getScene().getWindow());
            if (file != null) {
                textFilename.setText(file.getAbsolutePath());
            }
        });

        filePatternButton.setOnAction((event) -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select payload pattern file");
            File file = fileChooser.showOpenDialog(this.getScene().getWindow());
            if (file != null) {
                filePatternFilename.setText(file.getAbsolutePath());
            }
        });

        textPatternSize.textProperty().addListener(onlyNumberListener);
        filePatternSize.textProperty().addListener(onlyNumberListener);
        randomAsciiSize.textProperty().addListener(onlyNumberListener);
        randomNonAsciiSize.textProperty().addListener(onlyNumberListener);

        setMode(EditorMode.UNKNOWN);
    }

    public EditorMode getMode() {
        return mode;
    }

    public void setMode(EditorMode mode) {
        this.mode = mode;
        switch (mode) {
            case READ:
                logger.debug("READ mode for payload editor is disabled now");
                break;
            case EDIT:
                payloadEditorHboxChoice.setVisible(true);
                payloadEditorHboxChoice.setManaged(true);

                int index = type2int(type);
                if (index >= 0) {
                    select(index);
                } else {
                    payloadEditorHboxValue.setVisible(false);
                    payloadEditorHboxValue.setManaged(false);
                }
                break;
            case UNKNOWN:
                logger.debug("Set UNKNOWN payload editor mode");
                break;
            default:
                logger.error("Unknown payload editor mode");
        }
    }

    public String getText() {
        if (type==PayloadType.TEXT || type==PayloadType.TEXT_PATTERN) {
            return textProperty().get();
        } else {
            return null;
        }
    }

    public void setText(String text) {
        textProperty().set(text);
        setType(PayloadType.TEXT);
    }

    public PayloadType getType() {
        return type;
    }

    public void setType(PayloadType type) {
        this.type = type;

        if (type != PayloadType.UNKNOWN) {
            select(type2int(type));
            setMode(EditorMode.EDIT);
        }
        else {
            getSelectionModel().select(null);
            payloadEditorHboxValue.setVisible(false);
            payloadEditorHboxValue.setManaged(false);
            logger.warn("Set UNKNOWN type for payload");
        }
    }

    public StringProperty textProperty() {
        return textText.textProperty();
    }

    public void setPrefSize(double width, double height) {
        super.setPrefSize(width, height);
    }

    public void setContextMenu(ContextMenu contextMenu) {
        textText.setContextMenu(contextMenu);
    }

    public SingleSelectionModel<String> getSelectionModel() {
        return payloadChoiceType.getSelectionModel();
    }

    public final void setOnAction(EventHandler<ActionEvent> value) {
        setOnActionSave(value);
    }

    public final void setOnActionSave(EventHandler<ActionEvent> value) {
        payloadButtonSave.setOnAction(handlerActionSaveInternal);
        handlerActionSaveExternal = value;
    }

    public final void setOnActionCancel(EventHandler<ActionEvent> value) {
        payloadButtonCancel.setOnAction(value);
    }

    public void select(int index) {
        gridSetVisible(payloadEditorGrid, index);
        getSelectionModel().select(index);
    }

    public static boolean isTextFile(String fileUrl) throws IOException {
        File f = new File(fileUrl);
        String type = Files.probeContentType(f.toPath());
        boolean res1, res2;

        if (type == null) {
            //type couldn't be determined, assume binary
            res1 = false;
        } else if (type.startsWith("text")) {
            res1 = true;
        } else {
            //type isn't text
            res1 = false;
        }

        FileInputStream in = new FileInputStream(f);
        res2 = isTextStream(in);
        in.close();

        return res1 && res2;
    }

    public static boolean isTextArray(byte[] array) throws IOException {
        InputStream in = new ByteArrayInputStream(array);
        boolean res2 = isTextStream(in);
        in.close();
        return res2;
    }

    public static boolean isTextStream(InputStream in) throws IOException {
        int size = in.available();
        if(size > (1024 * 64)) size = 1024 * 64;
        byte[] data = new byte[size];
        in.read(data);

        int ascii = 0;
        int other = 0;

        for (int i = 0; i < data.length; i++) {
            byte b = data[i];
            if( b < 0x09 ) return true;

            if( b == 0x09 || b == 0x0A || b == 0x0C || b == 0x0D ) ascii++;
            else if( b >= 0x20  &&  b <= 0x7E ) ascii++;
            else other++;
        }

        if( other == 0 ) return true;

        return 100 * other / (ascii + other) <= 95;
    }

    private void gridSetVisible(GridPane grid, int index) {
        for (Node node : grid.getChildren()) {
            node.setVisible(false);
            node.setManaged(false);
        }
        if (index >= 0) {
            Node node = grid.getChildren().get(index);
            node.setVisible(true);
            node.setManaged(true);
            payloadEditorHboxValue.setVisible(true);
            payloadEditorHboxValue.setManaged(true);
        }
    }

    private int type2int(PayloadType type) {
        return type.index;
    }

    private PayloadType int2type(int index) {
        for (PayloadType t : PayloadType.values()) {
            if (t.index == index) return t;
        }
        return PayloadType.UNKNOWN;
    }

    private boolean file2text() {
        File file = new File(textFilename.getText());

        if (file.exists() && file.canRead()) {
            try {
                if (isTextFile(file.getAbsolutePath())) {
                    setText(new String(Files.readAllBytes(file.toPath())));
                    return true;
                }
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        }
        else {
            logger.error("File '" + file.getAbsolutePath() + "' not exists or is not readable");
        }
        return false;
    }

    private boolean textpattern2text() {
        String ssize = textPatternSize.getText();
        if (ssize==null) {
            logger.error("Size must be greater than zero");
            return false;
        }

        int size = 0;
        try {
            size = Integer.parseInt(ssize);
        }
        catch (NumberFormatException e) {
            logger.error("Size must be greater than zero");
            return false;
        }

        if (size > 0) {
            if (size > 64*1024) size = 64*1024;
            String pattern = textPatternText.getText();
            if (pattern.length() > 0) {
                String text = new String("");

                while (text.length() < size) {
                    text = text.concat(pattern);
                }
                text = text.substring(0, size - 1);
                setText(text);
                return true;
            } else {
                logger.error("Pattern must be greater than zero");
            }
        } else {
            logger.error("Size must be greater than zero");
        }
        return false;
    }

    private boolean filepattern2text() {
        File file = new File(filePatternFilename.getText());
        String pattern = null;

        if (file.exists() && file.canRead()) {
            try {
                if (isTextFile(file.getAbsolutePath())) {
                    pattern = new String(Files.readAllBytes(file.toPath()));
                }
            } catch (IOException e) {
                logger.error(e.getMessage());
                return false;
            }
        }
        else {
            logger.error("File '" + file.getAbsolutePath() + "' not exists or is not readable");
            return false;
        }

        String ssize = filePatternSize.getText();
        if (ssize==null) {
            logger.error("Size must be greater than zero");
            return false;
        }

        int size = 0;
        try {
            size = Integer.parseInt(ssize);
        }
        catch (NumberFormatException e) {
            logger.error("Size must be greater than zero");
            return false;
        }

        if (size > 0) {
            if (size > 64*1024) size = 64*1024;
            if (pattern.length() > 0) {
                String text = new String("");

                while (text.length() < size) {
                    text = text.concat(pattern);
                }
                text = text.substring(0, size - 1);
                setText(text);
                return true;
            } else {
                logger.error("Pattern must be greater than zero");
            }
        } else {
            logger.error("Size must be greater than zero");
        }
        return false;
    }

    private boolean generateRandomBytes(boolean ascii) {
        String ssize = null;

        if (ascii) ssize = randomAsciiSize.getText();
        else       ssize = randomNonAsciiSize.getText();

        if (ssize==null) {
            logger.error("Size must be greater than zero");
            return false;
        }

        int size = 0;
        try {
            size = Integer.parseInt(ssize);
        }
        catch (NumberFormatException e) {
            logger.error("Size must be greater than zero");
            return false;
        }

        if (size > 0) {
            if (size > 64*1024) size = 64*1024;
            data = new byte[size];
            randomBytes(ascii, data);
            return true;
        } else {
            logger.error("Size must be greater than zero");
        }

        return false;
    }

}
