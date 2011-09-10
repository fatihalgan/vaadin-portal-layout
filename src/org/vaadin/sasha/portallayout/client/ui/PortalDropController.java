package org.vaadin.sasha.portallayout.client.ui;

import org.vaadin.sasha.portallayout.client.dnd.DragContext;
import org.vaadin.sasha.portallayout.client.dnd.VetoDragException;
import org.vaadin.sasha.portallayout.client.dnd.drop.AbstractPositioningDropController;
import org.vaadin.sasha.portallayout.client.dnd.util.CoordinateLocation;
import org.vaadin.sasha.portallayout.client.dnd.util.DOMUtil;
import org.vaadin.sasha.portallayout.client.dnd.util.LocationWidgetComparator;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.Widget;

/**
 * Drop process controller for the portlets.
 * 
 * @author p4elkin
 */
public class PortalDropController extends AbstractPositioningDropController {

    private PortalDropPositioner dummy;

    public PortalDropPositioner getDummy() {
        return dummy;
    }

    private VPortalLayout portal;

    private int targetDropIndex = -1;

    public PortalDropController(final VPortalLayout portal) {
        super(portal.getContentPanel());
        this.portal = portal;
    }

    protected LocationWidgetComparator getLocationWidgetComparator() {
        return LocationWidgetComparator.BOTTOM_RIGHT_COMPARATOR;
    }

    private void updatePortletLocationOnDrop(final Portlet portlet) {
        final VPortalLayout currentParent = portlet.getParentPortal();

        if (portal.getChildPosition(portlet) != targetDropIndex) {
            portlet.setParentPortal(portal);
            portal.onPortletPositionUpdated(portlet, targetDropIndex);
        }
        
        if (!currentParent.equals(portal))
            currentParent.onPortletMovedOut(portlet);
    }

    private int updateDropPosition(final DragContext context) {
        final CoordinateLocation curLocation = new CoordinateLocation(context.mouseX, context.mouseY);
        int targetDropIndex = DOMUtil.findIntersect(portal.getContentPanel(), curLocation, getLocationWidgetComparator());
        return targetDropIndex;
    }

    private int getDummyIndex() {
        return (dummy == null) ? -1 : portal.getChildPosition(dummy);
    }

    protected PortalDropPositioner newPositioner(DragContext context) {
        final Portlet portlet = (Portlet) context.selectedWidgets.get(0);
        if (portlet != null)
            return new PortalDropPositioner(portlet);
        return null;
    }

    @Override
    public void onDrop(DragContext context) {
        super.onDrop(context);
        assert targetDropIndex != -1 : "Should not happen after onPreviewDrop did not veto";
        final Widget widget = context.selectedWidgets.get(0);
        removeDummy();
        updatePortletLocationOnDrop((Portlet) widget);
        portal.addToRootElement((Portlet) widget, targetDropIndex);
    }

    @Override
    public void onLeave(DragContext context) {
        removeDummy();
    }

    private void removeDummy() {
        if (dummy != null) {
            ((Panel)dummy.getParent()).remove(dummy);
            dummy = null;
        }
    }

    @Override
    public void onMove(final DragContext context) {
        super.onMove(context);
        Scheduler.get().scheduleDeferred(new ScheduledCommand() {
            @Override
            public void execute() {
                if (dummy == null)
                    return;
                int targetIndex = updateDropPosition(context);
                int dummyIndex = getDummyIndex();
                if (dummyIndex != targetIndex
                        && (dummyIndex != targetIndex - 1 || targetIndex == 0)) {
                    if (dummyIndex == 0 && portal.getChildCount() == 1) {
                        // Do nothing...
                    } else if (targetIndex == -1) {
                        Panel parent = (Panel)dummy.getParent();
                        parent.remove(dummy);
                    } else {
                        portal.addToRootElement(dummy, targetIndex);
                    }
                }
            }
        });
    }

    @Override
    public void onEnter(final DragContext context) {
        dummy = newPositioner(context);
        int dummyIndex = updateDropPosition(context);
        portal.onPortletEntered(dummy, dummyIndex);
    }

    @Override
    public void onPreviewDrop(DragContext context) throws VetoDragException {
        super.onPreviewDrop(context);
        targetDropIndex = getDummyIndex();
        if (targetDropIndex == -1)
            throw new VetoDragException();
    }
}
