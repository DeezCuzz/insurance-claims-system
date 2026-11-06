package ics.gui;

import ics.model.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.table.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.util.*;
import java.util.List;

public final class Widgets {
    private Widgets() {
    }

    public static void onTextChange(JTextComponent field, Runnable action) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { action.run(); }
            @Override public void removeUpdate(DocumentEvent e)  { action.run(); }
            @Override public void changedUpdate(DocumentEvent e) { action.run(); }
        });
    }

    public interface WordFor<T> {
        String wordFor(T value);
    }

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

    public static JScrollPane scroll(JTable table) {
        JScrollPane scroll = new JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.getViewport().setBackground(Theme.WHITE);
        return scroll;
    }

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

    public static JLabel chip() {
        JLabel chip = Theme.label(" ", Theme.FONT_LABEL, Theme.WHITE);
        chip.setOpaque(true);
        chip.setBorder(new EmptyBorder(4, 10, 4, 10));
        return chip;
    }

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
        return Theme.ORANGE;
    }

    @SuppressWarnings({"serial", "this-escape"})
    public static class Table extends JTable {
        private final DefaultTableModel model;
        private boolean refreshing;

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

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return getParent() != null && getPreferredSize().width < getParent().getWidth();
        }

        public void onRowSelected(Runnable action) {
            getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting() && !refreshing && getSelectedRow() >= 0) {
                    action.run();
                }
            });
        }

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

    public interface ColourRule {
        Color colourFor(int row, String text);
    }

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

        @Override
        public Color getForeground() {
            if (kind == null) {
                return super.getForeground();
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
            super.paintComponent(g);
        }
    }

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

        public BufferedImage toImage() {
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            paintIcon(null, g2, 0, 0);
            g2.dispose();
            return image;
        }
    }

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

        public void showClaim(Claim claim) {
            labels[0] = "Applied";
            labels[1] = "Under review";
            labels[2] = "Decision";
            labels[3] = "Paid";
            Claim.Status status = claim.getStatus();
            if (status == Claim.Status.CLOSED) {
                status = claim.getPaidAmount() > 0 ? Claim.Status.PAID : Claim.Status.DENIED;
            }
            if (status == Claim.Status.INFO_REQUESTED) {
                set(DONE, WAITING, PENDING, PENDING);
                labels[1] = "Needs your reply";
            } else if (status == Claim.Status.APPROVED || status == Claim.Status.PAID) {
                set(DONE, DONE, DONE, DONE);
                labels[2] = "Approved";
            } else if (status == Claim.Status.DENIED) {
                set(DONE, DONE, FAILED, PENDING);
                labels[2] = "Denied";
            } else if (status == Claim.Status.REJECTED) {
                set(DONE, FAILED, PENDING, PENDING);
                labels[1] = "Rejected";
            } else {
                set(DONE, CURRENT, PENDING, PENDING);
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
            for (int i = 0; i < 3; i++) {
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
