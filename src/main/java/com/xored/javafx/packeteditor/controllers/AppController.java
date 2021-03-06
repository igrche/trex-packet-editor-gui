package com.xored.javafx.packeteditor.controllers;

import com.google.common.eventbus.EventBus;
import com.google.inject.Inject;
import com.xored.javafx.packeteditor.data.FieldEditorModel;
import com.xored.javafx.packeteditor.service.IMetadataService;
import javafx.fxml.Initializable;

import java.net.URL;
import java.util.ResourceBundle;

public class AppController implements Initializable {

    @Inject
    IMetadataService metadataService;

    @Inject
    FieldEditorController editorController;

    @Inject
    EventBus eventBus;

    @Inject
    FieldEditorModel model;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        metadataService.initialize();
        eventBus.register(editorController);
        eventBus.register(model);
    }
}
