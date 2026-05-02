package com.hebi.burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

import java.io.File;

public class HeBiExtension implements BurpExtension {

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("PromptSlinger");

        File storageDir = new File(System.getProperty("user.home"), ".promptslinger");
        HistoryStore store = new HistoryStore(storageDir);

        HeBiPanel panel = new HeBiPanel(api, store);

        api.userInterface().registerSuiteTab("PromptSlinger", panel);
        api.userInterface().registerContextMenuItemsProvider(new HeBiContextMenu(panel));

        api.logging().logToOutput("PromptSlinger loaded. History at: " + storageDir.getAbsolutePath());
    }
}
