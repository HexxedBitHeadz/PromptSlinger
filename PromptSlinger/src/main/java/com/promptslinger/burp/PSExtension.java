package com.promptslinger.burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

import java.io.File;

public class PSExtension implements BurpExtension {

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("PromptSlinger");

        File storageDir = new File(System.getProperty("user.home"), ".promptslinger");
        HistoryStore store = new HistoryStore(storageDir);

        PSPanel panel = new PSPanel(api, store);

        api.userInterface().registerSuiteTab("PromptSlinger", panel);
        api.userInterface().registerContextMenuItemsProvider(new PSContextMenu(panel));

        api.logging().logToOutput("PromptSlinger loaded. History at: " + storageDir.getAbsolutePath());
    }
}
