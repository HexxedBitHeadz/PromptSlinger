package com.hebi.burp;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class HeBiContextMenu implements ContextMenuItemsProvider {

    private final HeBiPanel panel;

    public HeBiContextMenu(HeBiPanel panel) {
        this.panel = panel;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        List<Component> items = new ArrayList<>();

        // Requests selected in Proxy history / Target site map
        List<HttpRequestResponse> selected = event.selectedRequestResponses();
        if (!selected.isEmpty()) {
            HttpRequestResponse rr = selected.get(0);
            JMenuItem item = new JMenuItem("Send to PromptSlinger");
            item.addActionListener(e -> panel.loadRequest(rr));
            items.add(item);
            return items;
        }

        // Request open in a message editor (Repeater, Intruder, etc.)
        event.messageEditorRequestResponse().ifPresent(mer -> {
            HttpRequestResponse rr = mer.requestResponse();
            JMenuItem item = new JMenuItem("Send to PromptSlinger");
            item.addActionListener(e -> panel.loadRequest(rr));
            items.add(item);
        });

        return items;
    }
}
