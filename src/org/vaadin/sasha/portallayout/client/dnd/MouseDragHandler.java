/*
 * Copyright 2009 Fred Sauer
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.vaadin.sasha.portallayout.client.dnd;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.BorderStyle;
import com.google.gwt.event.dom.client.HasMouseDownHandlers;
import com.google.gwt.event.dom.client.HasTouchStartHandlers;
import com.google.gwt.event.dom.client.HumanInputEvent;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseMoveHandler;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.event.dom.client.MouseUpHandler;
import com.google.gwt.event.dom.client.TouchCancelEvent;
import com.google.gwt.event.dom.client.TouchCancelHandler;
import com.google.gwt.event.dom.client.TouchEndEvent;
import com.google.gwt.event.dom.client.TouchEndHandler;
import com.google.gwt.event.dom.client.TouchEvent;
import com.google.gwt.event.dom.client.TouchMoveEvent;
import com.google.gwt.event.dom.client.TouchMoveHandler;
import com.google.gwt.event.dom.client.TouchStartEvent;
import com.google.gwt.event.dom.client.TouchStartHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.FocusPanel;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.HashMap;

import org.vaadin.sasha.portallayout.client.dnd.util.DOMUtil;
import org.vaadin.sasha.portallayout.client.dnd.util.Location;
import org.vaadin.sasha.portallayout.client.dnd.util.WidgetLocation;

/**
 * Implementation helper class which handles mouse events for all draggable
 * widgets for a given {@link DragController}.
 */
class MouseDragHandler implements MouseMoveHandler, MouseDownHandler,
        MouseUpHandler, TouchStartHandler, TouchMoveHandler, TouchEndHandler,
        TouchCancelHandler {

    private class RegisteredDraggable {
        private final Widget dragable;

        private HandlerRegistration mouseDownHandlerRegistration;

        private HandlerRegistration touchStartHandlerRegistration;

        RegisteredDraggable(Widget dragable, Widget dragHandle) {
            this.dragable = dragable;
            if (dragHandle instanceof HasTouchStartHandlers) {
                touchStartHandlerRegistration = ((HasTouchStartHandlers) dragHandle)
                        .addTouchStartHandler(MouseDragHandler.this);
            }
            if (dragHandle instanceof HasMouseDownHandlers) {
                mouseDownHandlerRegistration = ((HasMouseDownHandlers) dragHandle)
                        .addMouseDownHandler(MouseDragHandler.this);
            }
        }

        Widget getDragable() {
            return dragable;
        }

        HandlerRegistration getMouseDownHandlerRegistration() {
            return mouseDownHandlerRegistration;
        }

        HandlerRegistration getTouchStartHandlerRegistration() {
            return touchStartHandlerRegistration;
        }
    }

    private static final int ACTIVELY_DRAGGING = 3;

    private static final int DRAGGING_NO_MOVEMENT_YET = 2;

    private static Widget mouseDownWidget;

    private static final int NOT_DRAGGING = 1;

    private static boolean supportsTouchEvents;

    private FocusPanel capturingWidget;

    private final DragContext context;

    private int dragging = NOT_DRAGGING;

    private HashMap<Widget, RegisteredDraggable> dragHandleMap = new HashMap<Widget, RegisteredDraggable>();

    private int mouseDownOffsetX;

    private int mouseDownOffsetY;

    private int mouseDownPageOffsetX;

    private int mouseDownPageOffsetY;

    MouseDragHandler(DragContext context) {
        this.context = context;
        initCapturingWidget();
    }

    public void onMouseDown(MouseDownEvent event) {
        if (supportsTouchEvents) {
            return;
        }
        if (dragging == ACTIVELY_DRAGGING
                || dragging == DRAGGING_NO_MOVEMENT_YET) {
            // Ignore additional mouse buttons depressed while still dragging
            return;
        }

        Widget sender = (Widget) event.getSource();
        int x = event.getX();
        int y = event.getY();

        int button = event.getNativeButton();

        if (button != NativeEvent.BUTTON_LEFT) {
            return;
        }

        if (mouseDownWidget != null) {
            // For multiple overlapping draggable widgets, ignore all but first
            // onMouseDown
            return;
        }

        // mouse down (not first mouse move) determines draggable widget
        mouseDownWidget = sender;
        context.draggable = dragHandleMap.get(mouseDownWidget).getDragable();
        assert context.draggable != null;

        if (!toggleKey(event)
                && !context.selectedWidgets.contains(context.draggable)) {
            context.dragController.clearSelection();
            context.dragController.toggleSelection(context.draggable);
        }
        if (context.dragController.getBehaviorCancelDocumentSelections()) {
            Scheduler.get().scheduleDeferred(new ScheduledCommand() {
                public void execute() {
                    DOMUtil.cancelAllDocumentSelections();
                }
            });
        }

        event.preventDefault();

        mouseDownOffsetX = x;
        mouseDownOffsetY = y;
        WidgetLocation loc1 = new WidgetLocation(mouseDownWidget, null);
        if (mouseDownWidget != context.draggable) {
            WidgetLocation loc2 = new WidgetLocation(context.draggable, null);
            mouseDownOffsetX += loc1.getLeft() - loc2.getLeft();
            mouseDownOffsetY += loc1.getTop() - loc2.getTop();
        }
        if (context.dragController.getBehaviorDragStartSensitivity() == 0
                && !toggleKey(event)) {
            // set context.mouseX/Y before startDragging() is called
            context.mouseX = x + loc1.getLeft();
            context.mouseY = y + loc1.getTop();
            startDragging();
            if (dragging == NOT_DRAGGING) {
                return;
            }
            actualMove(context.mouseX, context.mouseY);
        } else {
            mouseDownPageOffsetX = mouseDownOffsetX + loc1.getLeft();
            mouseDownPageOffsetY = mouseDownOffsetY + loc1.getTop();
            startCapturing();
        }
    }

    public void onMouseMove(MouseMoveEvent event) {
        if (supportsTouchEvents) {
            return;
        }
        Widget sender = (Widget) event.getSource();
        Element elem = sender.getElement();
        // TODO optimize for the fact that elem is at (0,0)
        int x = event.getRelativeX(elem);
        int y = event.getRelativeY(elem);

        if (dragging == ACTIVELY_DRAGGING
                || dragging == DRAGGING_NO_MOVEMENT_YET) {
            // TODO remove Safari workaround after GWT issue 1807 fixed
            if (sender != capturingWidget) {
                // In Safari 1.3.2 MAC, other mouse events continue to arrive
                // even when capturing
                return;
            }
            dragging = ACTIVELY_DRAGGING;
        } else {
            if (mouseDownWidget != null) {
                if (Math.max(Math.abs(x - mouseDownPageOffsetX),
                        Math.abs(y - mouseDownPageOffsetY)) >= context.dragController
                        .getBehaviorDragStartSensitivity()) {
                    if (context.dragController
                            .getBehaviorCancelDocumentSelections()) {
                        DOMUtil.cancelAllDocumentSelections();
                    }
                    if (!context.selectedWidgets.contains(context.draggable)) {
                        context.dragController
                                .toggleSelection(context.draggable);
                    }

                    // set context.mouseX/Y before startDragging() is called
                    Location location = new WidgetLocation(mouseDownWidget,
                            null);
                    context.mouseX = mouseDownOffsetX + location.getLeft();
                    context.mouseY = mouseDownOffsetY + location.getTop();

                    startDragging();
                } else {
                    // prevent IE image drag when drag sensitivity > 5
                    event.preventDefault();
                }
            }
            if (dragging == NOT_DRAGGING) {
                return;
            }
        }
        // proceed with the actual drag
        event.preventDefault();
        actualMove(x, y);
    }

    public void onMouseUp(MouseUpEvent event) {
        if (supportsTouchEvents) {
            return;
        }
        Widget sender = (Widget) event.getSource();
        Element elem = sender.getElement();
        // TODO optimize for the fact that elem is at (0,0)
        int x = event.getRelativeX(elem);
        int y = event.getRelativeY(elem);

        int button = event.getNativeButton();
        if (button != NativeEvent.BUTTON_LEFT) {
            return;
        }

        // in case mouse down occurred elsewhere
        if (mouseDownWidget == null) {
            return;
        }

        try {
            if (context.dragController.getBehaviorCancelDocumentSelections()) {
                DOMUtil.cancelAllDocumentSelections();
            }
            if (dragging == NOT_DRAGGING) {
                doSelectionToggle(event);
                return;
            }

            // TODO Remove Safari workaround after GWT issue 1807 fixed
            if (sender != capturingWidget) {
                // In Safari 1.3.2 MAC does not honor capturing widget for mouse
                // up
                Location location = new WidgetLocation(sender, null);
                x += location.getLeft();
                y += location.getTop();
            }
            // Proceed with the drop
            try {
                drop(x, y);
                if (dragging != ACTIVELY_DRAGGING) {
                    doSelectionToggle(event);
                }
            } finally {
                dragEndCleanup();
            }
        } finally {
            mouseDownWidget = null;
            dragEndCleanup();
        }
    }

    public void onTouchCancel(TouchCancelEvent event) {
        onTouchEndorCancel(event);
    }

    public void onTouchEnd(TouchEndEvent event) {
        onTouchEndorCancel(event);
    }

    public void onTouchMove(TouchMoveEvent event) {
        if (event.getTouches().length() != 1) {
            // ignore multiple fingers for now
            return;
        }
        event.preventDefault();
        Widget sender = (Widget) event.getSource();
        Element elem = sender.getElement();
        // TODO optimize for the fact that elem is at (0,0)
        int x = event.getTouches().get(0).getRelativeX(elem);
        int y = event.getTouches().get(0).getRelativeY(elem);

        if (dragging == ACTIVELY_DRAGGING
                || dragging == DRAGGING_NO_MOVEMENT_YET) {
            dragging = ACTIVELY_DRAGGING;
        } else {
            if (mouseDownWidget != null) {
                if (Math.max(Math.abs(x - mouseDownOffsetX),
                        Math.abs(y - mouseDownOffsetY)) >= context.dragController
                        .getBehaviorDragStartSensitivity()) {
                    if (!context.selectedWidgets.contains(context.draggable)) {
                        context.dragController
                                .toggleSelection(context.draggable);
                    }

                    // set context.mouseX/Y before startDragging() is called
                    Location location = new WidgetLocation(mouseDownWidget,
                            null);
                    context.mouseX = mouseDownOffsetX + location.getLeft();
                    context.mouseY = mouseDownOffsetY + location.getTop();

                    // adjust (x,y) to be relative to capturingWidget at (0,0)
                    // so that context.desiredDraggableX/Y is valid
                    x += location.getLeft();
                    y += location.getTop();

                    startDragging();
                } else {
                    // prevent IE image drag when drag sensitivity > 5
                    event.preventDefault();
                }
            }
            if (dragging == NOT_DRAGGING) {
                return;
            }
        }
        // proceed with the actual drag
        event.preventDefault();
        actualMove(x, y);
    }

    public void onTouchStart(TouchStartEvent event) {
        supportsTouchEvents = true;
        if (event.getTouches().length() != 1) {
            // ignore multiple fingers for now
            return;
        }

        // TODO verify that multiple onTocuhStart events are fired for
        // overlapping draggables
        if (mouseDownWidget != null) {
            // For multiple overlapping draggable widgets, ignore all but first
            // onTouchStart
            return;
        }

        event.preventDefault();
        Widget sender = (Widget) event.getSource();
        int x = event.getTouches().get(0)
                .getRelativeX(event.getRelativeElement());
        int y = event.getTouches().get(0)
                .getRelativeY(event.getRelativeElement());

        // mouse down (not first mouse move) determines draggable widget
        mouseDownWidget = sender;
        context.draggable = dragHandleMap.get(mouseDownWidget).getDragable();
        assert context.draggable != null;

        context.dragController.clearSelection();
        context.dragController.toggleSelection(context.draggable);

        event.preventDefault();

        mouseDownOffsetX = x;
        mouseDownOffsetY = y;
        WidgetLocation loc1 = new WidgetLocation(mouseDownWidget, null);
        if (mouseDownWidget != context.draggable) {
            WidgetLocation loc2 = new WidgetLocation(context.draggable, null);
            mouseDownOffsetX += loc1.getLeft() - loc2.getLeft();
            mouseDownOffsetY += loc1.getTop() - loc2.getTop();
        }
        if (context.dragController.getBehaviorDragStartSensitivity() == 0
                && !toggleKey(event)) {
            // set context.mouseX/Y before startDragging() is called
            context.mouseX = x + loc1.getLeft();
            context.mouseY = y + loc1.getTop();
            startDragging();
            if (dragging == NOT_DRAGGING) {
                return;
            }
            actualMove(context.mouseX, context.mouseY);
        } else {
            startCapturing();
        }
    }

    void actualMove(int x, int y) {
        context.mouseX = x;
        context.mouseY = y;
        context.desiredDraggableX = x - mouseDownOffsetX;
        context.desiredDraggableY = y - mouseDownOffsetY;

        context.dragController.dragMove();
    }

    void makeDraggable(Widget draggable, Widget dragHandle) {
        if (draggable instanceof PopupPanel) {
            DOMUtil.reportFatalAndThrowRuntimeException("PopupPanel (and its subclasses) cannot be made draggable; See http://code.google.com/p/gwt-dnd/issues/detail?id=43");
        }
        try {
            RegisteredDraggable registeredDraggable = new RegisteredDraggable(draggable, dragHandle);
            dragHandleMap.put(dragHandle, registeredDraggable);
        } catch (Exception ex) {
            throw new RuntimeException("dragHandle must implement HasMouseDownHandlers to be draggable", ex);
        }
    }

    void makeNotDraggable(Widget dragHandle) {
        RegisteredDraggable registeredDraggable = dragHandleMap
                .remove(dragHandle);
        if (registeredDraggable == null) {
            throw new RuntimeException("dragHandle was not draggable");
        }
        registeredDraggable.getMouseDownHandlerRegistration().removeHandler();
        registeredDraggable.getTouchStartHandlerRegistration().removeHandler();
    }

    private void doSelectionToggle(HumanInputEvent<?> event) {
        Widget widget = dragHandleMap.get(mouseDownWidget).getDragable();
        assert widget != null;
        if (!toggleKey(event)) {
            context.dragController.clearSelection();
        }
        context.dragController.toggleSelection(widget);
    }

    private void dragEndCleanup() {
        DOM.releaseCapture(capturingWidget.getElement());
        capturingWidget.removeFromParent();
        dragging = NOT_DRAGGING;
        context.dragEndCleanup();
    }

    private void drop(int x, int y) {
        actualMove(x, y);

        // Does the DragController allow the drop?
        try {
            context.dragController.previewDragEnd();
        } catch (VetoDragException ex) {
            context.vetoException = ex;
        }

        context.dragController.dragEnd();
    }

    private void initCapturingWidget() {
        capturingWidget = new FocusPanel();
        capturingWidget.addMouseMoveHandler(this);
        capturingWidget.addMouseUpHandler(this);
        capturingWidget.addTouchMoveHandler(this);
        capturingWidget.addTouchEndHandler(this);
        capturingWidget.addTouchCancelHandler(this);
        Style style = capturingWidget.getElement().getStyle();
        // workaround for IE8 opacity
        // http://code.google.com/p/google-web-toolkit/issues/detail?id=5538
        style.setProperty("filter", "alpha(opacity=0)");
        style.setOpacity(0);
        style.setZIndex(1000);
        style.setMargin(0, Style.Unit.PX);
        style.setBorderStyle(BorderStyle.NONE);
        style.setBackgroundColor("blue");
    }

    private void onTouchEndorCancel(TouchEvent<?> event) {
        if (event.getTouches().length() != 0) {
            // ignore multiple fingers for now
            return;
        }

        // in case mouse down occurred elsewhere
        if (mouseDownWidget == null) {
            return;
        }

        try {
            if (dragging == NOT_DRAGGING) {
                doSelectionToggle(event);
                return;
            }

            // Proceed with the drop
            try {
                drop(context.mouseX, context.mouseY);
                if (dragging != ACTIVELY_DRAGGING) {
                    doSelectionToggle(event);
                }
            } finally {
                dragEndCleanup();
            }
        } finally {
            mouseDownWidget = null;
            dragEndCleanup();
        }
    }

    private void startCapturing() {
        capturingWidget.setPixelSize(0, 0);
        RootPanel.get().add(capturingWidget, 0, 0);
        DOM.setCapture(capturingWidget.getElement());
    }

    private void startDragging() {
        context.dragStartCleanup();
        try {
            context.dragController.previewDragStart();
        } catch (VetoDragException ex) {
            context.vetoException = ex;
            mouseDownWidget = null;
            dragEndCleanup();
            return;
        }
        context.dragController.dragStart();

        startCapturing();
        capturingWidget.setPixelSize(RootPanel.get().getOffsetWidth(), RootPanel.get().getOffsetHeight());
        dragging = DRAGGING_NO_MOVEMENT_YET;
    }

    private boolean toggleKey(HumanInputEvent<?> event) {
        return event.isControlKeyDown() || event.isMetaKeyDown();
    }
}
