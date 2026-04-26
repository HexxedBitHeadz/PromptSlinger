package com.hebi.burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

import java.io.File;

public class HeBiExtension implements BurpExtension {

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("HeBi-Chatter");

        File storageDir = new File(System.getProperty("user.home"), ".hebi-chatter");
        HistoryStore store = new HistoryStore(storageDir);

        HeBiPanel panel = new HeBiPanel(api, store);

        api.userInterface().registerSuiteTab("HeBi-Chatter", panel);
        api.userInterface().registerContextMenuItemsProvider(new HeBiContextMenu(panel));

        api.logging().logToOutput("HeBi-Chatter loaded. History at: " + storageDir.getAbsolutePath());
    }
}
