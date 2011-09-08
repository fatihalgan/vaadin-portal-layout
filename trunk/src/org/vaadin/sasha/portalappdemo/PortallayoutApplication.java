package org.vaadin.sasha.portalappdemo;

import com.vaadin.Application;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Window;

@SuppressWarnings("serial")
public class PortallayoutApplication extends Application {

    boolean debugMode = false;

    private Window mainWindow;

    @Override
    public void init() {
        setTheme("portallayouttheme");
        mainWindow = new Window("Portallayout Application");
        setMainWindow(mainWindow);
        testInit();
    }

    public void testInit() {
        HorizontalLayout windowLayout = new HorizontalLayout();
        windowLayout.setSizeFull();
        mainWindow.setContent(windowLayout);
        PortalTabSheet tabs = new PortalTabSheet(this);
        windowLayout.addComponent(tabs);
        windowLayout.setExpandRatio(tabs, 1.0f);
        tabs.setSizeFull();
    }

}
