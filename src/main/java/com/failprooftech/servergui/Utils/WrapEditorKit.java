package com.failprooftech.servergui.Utils;

import java.awt.Component;

import javax.swing.JViewport;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BoxView;
import javax.swing.text.ComponentView;
import javax.swing.text.Element;
import javax.swing.text.IconView;
import javax.swing.text.LabelView;
import javax.swing.text.ParagraphView;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;

/**
 * Editor kit for JTextPane that wraps long lines to the visible width.
 * When {@link ConsoleStyleHelper#isConsoleWrapWordBreakOnly()} is false (default), lines can break at any character; when true, only at word boundaries.
 */
public class WrapEditorKit extends StyledEditorKit {

    private static final ViewFactory WRAP_FACTORY = new WrapColumnFactory();

    @Override
    public ViewFactory getViewFactory() {
        return WRAP_FACTORY;
    }

    private static class WrapColumnFactory implements ViewFactory {
        @Override
        public View create(Element elem) {
            String kind = elem.getName();
            if (kind != null) {
                if (kind.equals(AbstractDocument.ContentElementName)) {
                    return new WrapLabelView(elem);
                }
                if (kind.equals(AbstractDocument.ParagraphElementName)) {
                    return new WrapParagraphView(elem);
                }
                if (kind.equals(AbstractDocument.SectionElementName)) {
                    return new BoxView(elem, BoxView.Y_AXIS);
                }
                if (kind.equals(StyleConstants.ComponentElementName)) {
                    return new ComponentView(elem);
                }
                if (kind.equals(StyleConstants.IconElementName)) {
                    return new IconView(elem);
                }
            }
            return new LabelView(elem);
        }
    }

    /**
     * Label view: when word-break-only is off (default), allows breaking at any character;
     * when word-break-only is on, uses default behavior (break at spaces only).
     */
    private static class WrapLabelView extends LabelView {
        public WrapLabelView(Element elem) {
            super(elem);
        }

        @Override
        public int getBreakWeight(int axis, float pos, float len) {
            if (axis == View.X_AXIS && !ConsoleStyleHelper.isConsoleWrapWordBreakOnly()) {
                return View.GoodBreakWeight;
            }
            return super.getBreakWeight(axis, pos, len);
        }
    }

    /**
     * Paragraph view that constrains preferred width to the container so text always wraps to the visible width.
     */
    private static class WrapParagraphView extends ParagraphView {
        public WrapParagraphView(Element elem) {
            super(elem);
        }

        @Override
        public float getPreferredSpan(int axis) {
            if (axis == View.X_AXIS) {
                Component c = getContainer();
                if (c != null) {
                    Component parent = c.getParent();
                    if (parent instanceof JViewport) {
                        int w = ((JViewport) parent).getWidth();
                        if (w > 0) {
                            return w;
                        }
                    }
                    int w = c.getWidth();
                    if (w > 0) {
                        return w;
                    }
                }
            }
            return super.getPreferredSpan(axis);
        }

        @Override
        public float getMinimumSpan(int axis) {
            if (axis == View.X_AXIS) {
                return 0;
            }
            return super.getMinimumSpan(axis);
        }
    }
}
