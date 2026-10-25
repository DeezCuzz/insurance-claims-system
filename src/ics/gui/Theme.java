package ics.gui;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Every colour, font and size in one place, taken from the Bulletin Board sheet (rubric I.A.1: consistency).
 * WHY: change a value here and every screen follows. No screen writes its own colour or size.
 */
public final class Theme {

    private Theme() {
        // only static members
    }

    // ---- colours (Bulletin Board) ----
    public static final Color NAVY = new Color(0x4B1C9C);        // sidebar and headings
    public static final Color ROSE = new Color(0xA31D72);        // the main button on each screen
    public static final Color WHITE = Color.WHITE;
    public static final Color BACKGROUND = new Color(0xF7F5F7);  // page background, and fields that are locked
    // extra shades
    public static final Color TEXT_DARK = new Color(0x2B2B2B);
    public static final Color TEXT_MUTED = new Color(0x6B6B6B);
    public static final Color BORDER = new Color(0xDDD6E5);
    public static final Color SOFT_PURPLE = new Color(0xEFE8FA);
    public static final Color ERROR = new Color(0xC0392B);
    public static final Color SUCCESS = new Color(0x2E8B4F);
    public static final Color AMBER = new Color(0x9A6700);       // "waiting" states
    public static final Color AMBER_FILL = new Color(0xFCE9C9);
    public static final Color ORANGE = new Color(0xD9650B);      // Upcoming policies (fix F3)
    public static final Color GREEN_FILL = new Color(0xE3F2E1);

    // ---- sizes (Bulletin Board: minimum / maximum panel sizes) ----
    public static final Dimension MIN_WINDOW = new Dimension(1024, 768);
    public static final Dimension DEFAULT_WINDOW = new Dimension(1280, 800);
    public static final int SIDEBAR_WIDTH = 240;
    public static final Dimension FIELD_SIZE = new Dimension(260, 32);
    public static final Dimension BUTTON_SIZE = new Dimension(120, 36);

    // ---- fonts (Bulletin Board: Montserrat, JetBrains Mono). A safe font is used if they are not installed. ----
    private static final List<String> INSTALLED = Arrays.asList(
            GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());

    private static String pick(String fallback, String... wanted) {
        for (String name : wanted) {
            if (INSTALLED.contains(name)) {
                return name;
            }
        }
        return fallback;
    }

    private static final String SANS = pick(Font.SANS_SERIF, "Montserrat", "Segoe UI", "Arial");
    private static final String LIGHT = pick(SANS, "Montserrat Light");
    private static final String MONO = pick(Font.MONOSPACED, "JetBrains Mono", "Consolas", "Menlo");

    public static final Font FONT_TITLE = new Font(SANS, Font.BOLD, 30);
    public static final Font FONT_HEADING = new Font(SANS, Font.BOLD, 22);
    public static final Font FONT_STAT = new Font(SANS, Font.BOLD, 34);
    public static final Font FONT_LABEL = new Font(SANS, Font.BOLD, 12);
    public static final Font FONT_BUTTON = new Font(SANS, Font.BOLD, 13);
    public static final Font FONT_BODY = new Font(LIGHT, Font.PLAIN, 14);
    public static final Font FONT_CAPTION = new Font(LIGHT, Font.PLAIN, 12);
    public static final Font FONT_MONO = new Font(MONO, Font.PLAIN, 11);

    /** The peso sign if the font can draw it; otherwise "PHP " (a missing letter would show as a box). */
    private static final String CURRENCY = FONT_BODY.canDisplay('\u20B1') ? "\u20B1" : "PHP ";

    /** The money sign the screens use: the peso sign, or "PHP " if the font cannot draw it. */
    public static String currencySign() {
        return CURRENCY;
    }

    /** Call once at the start, before any window opens. */
    public static void install() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
            // the default look is fine if this fails
        }
        UIManager.put("OptionPane.messageFont", FONT_BODY);
        UIManager.put("OptionPane.buttonFont", FONT_BUTTON);
        UIManager.put("ToolTip.font", FONT_CAPTION);
        UIManager.put("ToolTip.background", new Color(0xFFFBE6));
        UIManager.put("ComboBox.disabledForeground", TEXT_MUTED);
        UIManager.put("ComboBox.disabledBackground", BACKGROUND);
        ToolTipManager.sharedInstance().setInitialDelay(300);
    }

    // ---- small builders every screen uses ----

    /** 64000 becomes "₱64,000.00". */
    public static String money(double amount) {
        return CURRENCY + String.format(Locale.US, "%,.2f", amount);
    }

    /** A white card with a thin border and padding. */
    public static JPanel card(int padding) {
        JPanel panel = new JPanel();
        panel.setBackground(WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(new LineBorder(BORDER, 1, true),
                new EmptyBorder(padding, padding, padding, padding)));
        return panel;
    }

    public static JLabel label(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    public static JLabel fieldLabel(String text) {
        return label(text, FONT_LABEL, TEXT_DARK);
    }

    /** A field label with a red * (rubric II.A.4: required fields must be marked). */
    public static JLabel requiredLabel(String text) {
        return label("<html>" + text + " <font color='#C0392B'>*</font></html>", FONT_LABEL, TEXT_DARK);
    }

    /** Gives a text field the standard look, plus a purple outline while you type in it. */
    public static <T extends JTextComponent> T styleField(T field) {
        Border normal = BorderFactory.createCompoundBorder(new LineBorder(BORDER, 1, true), new EmptyBorder(4, 8, 4, 8));
        Border focused = BorderFactory.createCompoundBorder(new LineBorder(NAVY, 2, true), new EmptyBorder(3, 7, 3, 7));
        field.setFont(FONT_BODY);
        field.setForeground(TEXT_DARK);
        field.setBackground(WHITE);
        field.setBorder(normal);
        field.setPreferredSize(FIELD_SIZE);
        field.setMinimumSize(new Dimension(100, 28));
        field.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { field.setBorder(focused); }
            @Override public void focusLost(FocusEvent e)   { field.setBorder(normal); }
        });
        return field;
    }

    public static void styleCombo(JComboBox<?> box) {
        box.setFont(FONT_BODY);
        box.setBackground(WHITE);
        box.setPreferredSize(FIELD_SIZE);
    }

    /** Sets a tooltip (rubric I.A.3) and returns the same component. */
    public static <T extends JComponent> T tip(T component, String text) {
        component.setToolTipText(text);
        return component;
    }

    /** Navy header row, padded cells, light purple selection. The first column (IDs) uses the mono font. */
    public static void styleTable(JTable table) {
        table.setFont(FONT_BODY);
        table.setRowHeight(30);
        table.setShowVerticalLines(false);
        table.setGridColor(BORDER);
        table.setSelectionBackground(SOFT_PURPLE);
        table.setSelectionForeground(TEXT_DARK);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(0, 34));
        table.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, value, false, false, row, col);
                l.setOpaque(true);
                l.setBackground(NAVY);
                l.setForeground(WHITE);
                l.setFont(FONT_LABEL);
                l.setBorder(new EmptyBorder(0, 10, 0, 10));
                return l;
            }
        });
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, value, selected, false, row, col);
                l.setBorder(new EmptyBorder(0, 10, 0, 10));
                l.setFont(col == 0 ? FONT_MONO : FONT_BODY);
                return l;
            }
        });
    }

    /**
     * HTML that wraps at this many screen pixels, for labels with long text. Pass plain text; < and & are made safe.
     * WHY the 0.75: Swing draws CSS "px" about 4/3 bigger than asked, so without it the text gets cut off.
     */
    public static String wrap(String text, int screenPixels) {
        return "<html><body style='width:" + Math.round(screenPixels * 0.75) + "px'>" + escape(text) + "</body></html>";
    }

    /** Same as wrap(), but for text that already contains HTML tags (tables, bold). */
    public static String wrapHtml(String html, int screenPixels) {
        return "<html><body style='width:" + Math.round(screenPixels * 0.75) + "px'>" + html + "</body></html>";
    }

    /** Makes text safe to put inside HTML (so a typed < does not break the label). */
    public static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    /** One row of a "label: value" HTML table, used by the detail panels. */
    public static void tableRow(StringBuilder html, String label, String value) {
        html.append("<tr><td nowrap valign='top'><b>").append(escape(label)).append("</b>&nbsp;&nbsp;</td><td>")
                .append(escape(value)).append("</td></tr>");
    }
}
