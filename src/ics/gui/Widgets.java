package ics.gui;

import ics.model.Claim;

import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.Scrollable;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.JTextComponent;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Our own Swing parts, all in one file: buttons, the logo, the table, the progress pictures.
 * WHY one file: each part is small and only used by our screens, so keeping them together is easier to find
 * than eight tiny files. Each part is a "static nested class", used like  new Widgets.RoundedButton(...).
 */
public final class Widgets {

    private Widgets() {
        // only nested classes and static helpers
    }

    // ================================================================== helpers

    /** Runs the action every time the text in the field changes (for live previews and search boxes). */
    public static void onTextChange(JTextComponent field, Runnable action) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { action.run(); }
            @Override public void removeUpdate(DocumentEvent e)  { action.run(); }
            @Override public void changedUpdate(DocumentEvent e) { action.run(); }
        });
    }

    /** Decides the word a drop-down list shows for one choice. */
    public interface WordFor<T> {
        String wordFor(T value);
    }

    /**
     * Makes a drop-down list show readable words ("Admin", "Health") instead of the names saved in the files
     * ("ADMIN", "HEALTH"). Used with a lambda, for example  showWords(typeBox, type -> type.getLabel()).
     */
    public static <T> void showWords(JComboBox<T> box, WordFor<T> words) {
        box.setRenderer(new DefaultListCellRenderer() {
            @Override
            @SuppressWarnings("unchecked")
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value != null) {
                    setText(words.wordFor((T) value));
                }
                return this;
            }
        });
    }

    /** A table inside a scroll pane, with the header and a thin border. */
    public static JScrollPane scroll(JTable table) {
        JScrollPane scroll = new JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.getViewport().setBackground(Theme.WHITE);
        return scroll;
    }

    /** Puts long content (a form, a detail view) in a scroll pane that only scrolls up and down. */
    public static JScrollPane scrollDown(JComponent content) {
        FitWidthPanel holder = new FitWidthPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(content, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    /** A coloured label with padding, for status "chips" like  Under review  or  Paid. */
    public static JLabel chip() {
        JLabel chip = Theme.label(" ", Theme.FONT_LABEL, Theme.WHITE);
        chip.setOpaque(true);
        chip.setBorder(new EmptyBorder(4, 10, 4, 10));
        return chip;
    }

    /** The colour for a claim status, the same on every screen. */
    public static Color colorFor(Claim.Status status) {
        if (status == Claim.Status.APPROVED || status == Claim.Status.PAID) {
            return Theme.SUCCESS;
        }
        if (status == Claim.Status.DENIED || status == Claim.Status.REJECTED) {
            return Theme.ERROR;
        }
        if (status == Claim.Status.UNDER_REVIEW || status == Claim.Status.INFO_REQUESTED) {
            return Theme.AMBER;
        }
        return Theme.TEXT_MUTED;
    }

    /** The colour for a policy status word: Active green, Cancelled red, Expired grey, Upcoming orange. */
    public static Color colorForPolicy(String statusLabel) {
        if (statusLabel.equals("Active")) {
            return Theme.SUCCESS;
        }
        if (statusLabel.equals("Cancelled")) {
            return Theme.ERROR;
        }
        if (statusLabel.equals("Expired")) {
            return Theme.TEXT_MUTED;
        }
        return Theme.ORANGE;                                            // Upcoming
    }

    // ================================================================== Table

    /**
     * A read-only table with our look. It remembers the rows by the ID in column 0, so after a refresh the same
     * record stays selected. Selection events caused by the refresh itself are ignored.
     */
    @SuppressWarnings({"serial", "this-escape"})
    public static class Table extends JTable {

        private final DefaultTableModel model;
        private boolean refreshing;        // true while setRows() is filling the table

        public Table(String[] columns, int[] widths) {
            model = new DefaultTableModel(columns, 0) {
                @Override public boolean isCellEditable(int row, int column) { return false; }
            };
            setModel(model);
            Theme.styleTable(this);
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            setAutoResizeMode(AUTO_RESIZE_SUBSEQUENT_COLUMNS);
            for (int i = 0; i < widths.length; i++) {
                getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
                getColumnModel().getColumn(i).setMinWidth(55);
            }
        }

        /** Narrow window: scroll sideways. Wide window: stretch to fill. */
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return getParent() != null && getPreferredSize().width < getParent().getWidth();
        }

        /** Runs the action when the PERSON selects a row (not when the table refreshes itself). */
        public void onRowSelected(Runnable action) {
            getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting() && !refreshing && getSelectedRow() >= 0) {
                    action.run();
                }
            });
        }

        /** Replaces all rows, then selects again the row whose ID is keepId (if it is still there). */
        public void setRows(List<Object[]> rows, String keepId) {
            refreshing = true;
            model.setRowCount(0);
            for (Object[] row : rows) {
                model.addRow(row);
            }
            for (int r = 0; r < model.getRowCount(); r++) {
                if (model.getValueAt(r, 0).equals(keepId)) {
                    setRowSelectionInterval(r, r);
                }
            }
            refreshing = false;
        }

        /** Selects the row with this ID without running the onRowSelected action. */
        public void selectQuietly(String id) {
            refreshing = true;
            for (int r = 0; r < model.getRowCount(); r++) {
                if (model.getValueAt(r, 0).equals(id)) {
                    setRowSelectionInterval(r, r);
                }
            }
            refreshing = false;
        }

        public void clearSelectionQuietly() {
            refreshing = true;
            clearSelection();
            refreshing = false;
        }

        /** Colours one column, for example a status column. The rule decides the colour from the row number. */
        public void colourColumn(int column, ColourRule rule) {
            getColumnModel().getColumn(column).setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                               boolean focus, int row, int col) {
                    JLabel l = (JLabel) super.getTableCellRendererComponent(t, value, selected, false, row, col);
                    l.setBorder(new EmptyBorder(0, 10, 0, 10));
                    l.setFont(Theme.FONT_LABEL);
                    l.setForeground(rule.colourFor(row, String.valueOf(value)));
                    return l;
                }
            });
        }
    }

    /** Decides the text colour of one cell. Used with a lambda, for example  (row, text) -> Theme.ERROR. */
    public interface ColourRule {
        Color colourFor(int row, String text);
    }

    // ================================================================== buttons

    /**
     * Our main button style, drawn by hand so it looks the same on Windows, Mac and Linux.
     *   PRIMARY   = rose, for the main action of a screen (Save, Log in)
     *   SECONDARY = white with a purple outline (Clear, Cancel)
     */
    @SuppressWarnings({"serial", "this-escape"})
    public static class RoundedButton extends JButton {

        public enum Kind { PRIMARY, SECONDARY }

        private final Kind kind;

        public RoundedButton(String text, Kind kind) {
            super(text);
            this.kind = kind;
            setFont(Theme.FONT_BUTTON);
            setFocusPainted(false);
            setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(Theme.BUTTON_SIZE);
        }

        /** Text colour: white on rose, purple on white, grey when switched off. */
        @Override
        public Color getForeground() {
            if (kind == null) {
                return super.getForeground();      // happens once, inside the JButton constructor
            }
            if (!isEnabled()) {
                return new Color(0x9A9A9A);
            }
            return kind == Kind.PRIMARY ? Theme.WHITE : Theme.NAVY;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ButtonModel m = getModel();
            Color fill;
            Color outline = null;
            if (!isEnabled()) {
                fill = new Color(0xEEEBF0);
                outline = Theme.BORDER;
            } else if (kind == Kind.PRIMARY) {
                fill = m.isPressed() ? Theme.ROSE.darker().darker() : m.isRollover() ? Theme.ROSE.darker() : Theme.ROSE;
            } else {
                fill = m.isRollover() ? Theme.SOFT_PURPLE : Theme.WHITE;
                outline = Theme.NAVY;
            }
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            if (outline != null) {
                g2.setColor(outline);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            }
            g2.dispose();
            super.paintComponent(g);               // draws the text (and the Alt+letter underline)
        }
    }

    /** A sidebar item: a plain grey button with a thin border. The selected one is darker and bold. */
    @SuppressWarnings({"serial", "this-escape"})
    public static class NavButton extends JButton {

        private final String key;
        private boolean selected;

        public NavButton(String key) {
            super(key);
            this.key = key;
            setFont(Theme.FONT_BODY);
            setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
            setFocusPainted(false);
            setContentAreaFilled(false);
            setPreferredSize(new Dimension(200, 34));
        }

        public String getKey() {
            return key;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
            setFont(selected ? Theme.FONT_BUTTON : Theme.FONT_BODY);
            repaint();
        }

        @Override
        public Color getForeground() {
            return new Color(0x222222);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Color fill = getModel().isPressed() ? new Color(0xB8B8B8) : selected ? new Color(0xC9C9C9) : new Color(0xE9E9E9);
            g.setColor(fill);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(new Color(0x8F8F8F));
            g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            super.paintComponent(g);
        }
    }

    // ================================================================== pictures (rubric I.A.4)

    /** The logo: a shield with a check mark, drawn in code so no image file is needed. */
    public static class ShieldIcon implements Icon {

        private final int size;
        private final Color shieldColor;
        private final Color checkColor;

        public ShieldIcon(int size, Color shieldColor, Color checkColor) {
            this.size = size;
            this.shieldColor = shieldColor;
            this.checkColor = checkColor;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            double s = size;
            Path2D shield = new Path2D.Double();
            shield.moveTo(s * 0.50, s * 0.04);
            shield.lineTo(s * 0.92, s * 0.20);
            shield.curveTo(s * 0.92, s * 0.62, s * 0.78, s * 0.84, s * 0.50, s * 0.97);
            shield.curveTo(s * 0.22, s * 0.84, s * 0.08, s * 0.62, s * 0.08, s * 0.20);
            shield.closePath();
            g2.setColor(shieldColor);
            g2.fill(shield);
            Path2D check = new Path2D.Double();
            check.moveTo(s * 0.30, s * 0.50);
            check.lineTo(s * 0.45, s * 0.65);
            check.lineTo(s * 0.72, s * 0.36);
            g2.setColor(checkColor);
            g2.setStroke(new BasicStroke((float) (s * 0.09), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(check);
            g2.dispose();
        }

        @Override public int getIconWidth()  { return size; }
        @Override public int getIconHeight() { return size; }

        /** The logo as a picture, for the window icon. */
        public BufferedImage toImage() {
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            paintIcon(null, g2, 0, 0);
            g2.dispose();
            return image;
        }
    }

    /** A thin bar showing how much of a policy's limit is used. Turns red when it is all used. */
    @SuppressWarnings({"serial", "this-escape"})
    public static class UsageBar extends JComponent {

        private double used;
        private double total = 1;

        public UsageBar() {
            setPreferredSize(new Dimension(300, 14));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));
        }

        public void setValues(double used, double total) {
            this.used = Math.max(0, used);
            this.total = total <= 0 ? 1 : total;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int h = getHeight();
            g2.setColor(Theme.SOFT_PURPLE);
            g2.fillRoundRect(0, 0, getWidth(), h, h, h);
            int filled = (int) Math.round(getWidth() * Math.min(1.0, used / total));
            if (filled > 0) {
                g2.setColor(used >= total ? Theme.ERROR : Theme.NAVY);
                g2.fillRoundRect(0, 0, Math.max(filled, h), h, h, h);
            }
            g2.dispose();
        }
    }

    /**
     * Four dots showing where a claim is: Applied -> Under review -> Decision -> Paid.
     * Done = filled navy, current = a ring, waiting for the User = amber, failed (rejected or denied) = red.
     */
    @SuppressWarnings({"serial", "this-escape"})
    public static class ClaimProgress extends JComponent {

        private static final int DONE = 0;
        private static final int CURRENT = 1;
        private static final int WAITING = 2;
        private static final int FAILED = 3;
        private static final int PENDING = 4;

        private final String[] labels = new String[4];
        private final int[] steps = new int[4];

        public ClaimProgress() {
            setPreferredSize(new Dimension(340, 52));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        }

        /** Sets the four dots and their words from the claim's status. */
        public void showClaim(Claim claim) {
            labels[0] = "Applied";
            labels[1] = "Under review";
            labels[2] = "Decision";
            labels[3] = "Paid";
            Claim.Status status = claim.getStatus();
            if (status == Claim.Status.CLOSED) {       // a closed claim shows how it ended
                status = claim.getPaidAmount() > 0 ? Claim.Status.PAID : Claim.Status.DENIED;
            }
            if (status == Claim.Status.INFO_REQUESTED) {
                set(DONE, WAITING, PENDING, PENDING);
                labels[1] = "Needs your reply";
            } else if (status == Claim.Status.APPROVED || status == Claim.Status.PAID) {
                // fix C5: approving releases the money, so an approved claim is already paid
                set(DONE, DONE, DONE, DONE);
                labels[2] = "Approved";
            } else if (status == Claim.Status.DENIED) {
                set(DONE, DONE, FAILED, PENDING);
                labels[2] = "Denied";
            } else if (status == Claim.Status.REJECTED) {
                set(DONE, FAILED, PENDING, PENDING);
                labels[1] = "Rejected";
            } else {
                set(DONE, CURRENT, PENDING, PENDING);  // FILED or UNDER_REVIEW
            }
            repaint();
        }

        private void set(int a, int b, int c, int d) {
            steps[0] = a;
            steps[1] = b;
            steps[2] = c;
            steps[3] = d;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int margin = 40;
            int gap = (getWidth() - 2 * margin) / 3;
            int cy = 12;
            int r = 9;
            Color amber = new Color(0xC98A00);
            for (int i = 0; i < 3; i++) {                  // the lines between the dots
                g2.setColor(steps[i + 1] != PENDING ? Theme.NAVY : Theme.BORDER);
                g2.setStroke(new BasicStroke(3f));
                g2.drawLine(margin + i * gap + r, cy, margin + (i + 1) * gap - r, cy);
            }
            g2.setFont(Theme.FONT_CAPTION);
            FontMetrics fm = g2.getFontMetrics();
            for (int i = 0; i < 4; i++) {
                int cx = margin + i * gap;
                int step = steps[i];
                if (step == DONE) {
                    dot(g2, cx, cy, r, Theme.NAVY);
                } else if (step == WAITING) {
                    dot(g2, cx, cy, r, amber);
                } else if (step == FAILED) {
                    dot(g2, cx, cy, r, Theme.ERROR);
                } else if (step == CURRENT) {
                    dot(g2, cx, cy, r, Theme.WHITE);
                    g2.setColor(Theme.NAVY);
                    g2.setStroke(new BasicStroke(3f));
                    g2.drawOval(cx - r + 1, cy - r + 1, 2 * r - 2, 2 * r - 2);
                    dot(g2, cx, cy, 3, Theme.NAVY);
                } else {
                    dot(g2, cx, cy, r, Theme.WHITE);
                    g2.setColor(Theme.BORDER);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawOval(cx - r + 1, cy - r + 1, 2 * r - 2, 2 * r - 2);
                }
                g2.setColor(step == PENDING ? Theme.TEXT_MUTED : step == FAILED ? Theme.ERROR
                        : step == WAITING ? amber.darker() : Theme.TEXT_DARK);
                g2.drawString(labels[i], cx - fm.stringWidth(labels[i]) / 2, cy + r + 18);
            }
            g2.dispose();
        }

        private static void dot(Graphics2D g2, int cx, int cy, int r, Color color) {
            g2.setColor(color);
            g2.fillOval(cx - r, cy - r, 2 * r, 2 * r);
        }
    }

    // ================================================================== layout helper

    /** A panel that is always as wide as the scroll pane showing it, so nothing is cut off on the right. */
    @SuppressWarnings({"serial", "this-escape"})
    public static class FitWidthPanel extends JPanel implements Scrollable {

        public FitWidthPanel(LayoutManager layout) {
            super(layout);
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) { return 64; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
