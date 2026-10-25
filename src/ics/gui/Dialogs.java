package ics.gui;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.print.PrinterException;
import java.util.List;

/** The two small pop-up windows: "type your password" and the payout slip. */
public final class Dialogs {

    private Dialogs() {
        // only static methods
    }

    /**
     * "Type your password to continue." Used before anything risky (rubric II.C.4).
     * It only COLLECTS the password; the service checks it.
     *
     * @param action what is about to happen, for example "delete POL-0003"
     * @param error  red text for a second try, or "" for none
     * @return the typed password, or null if the Admin pressed Cancel
     */
    public static String askPassword(Component parent, String action, String error) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "Confirm with your password", Dialog.ModalityType.APPLICATION_MODAL);
        final String[] result = {null};      // an array so the button code below can fill it in

        JPasswordField field = Theme.styleField(new JPasswordField());
        Theme.tip(field, "Your own Admin password");
        JLabel errorLabel = Theme.label(error.isBlank() ? " " : error, Theme.FONT_LABEL, Theme.ERROR);
        Widgets.RoundedButton confirm = new Widgets.RoundedButton("Confirm", Widgets.RoundedButton.Kind.PRIMARY);
        Widgets.RoundedButton cancel = new Widgets.RoundedButton("Cancel", Widgets.RoundedButton.Kind.SECONDARY);
        Theme.tip(confirm, "Continue (Enter)");
        Theme.tip(cancel, "Do nothing (Esc)");

        confirm.addActionListener(e -> {
            String typed = new String(field.getPassword());
            if (typed.isEmpty()) {
                errorLabel.setText("Password is required.");
                return;
            }
            result[0] = typed;
            dialog.dispose();
        });
        cancel.addActionListener(e -> dialog.dispose());

        JPanel buttons = new JPanel(new GridLayout(1, 2, 10, 0));
        buttons.setOpaque(false);
        buttons.add(confirm);
        buttons.add(cancel);

        JPanel middle = new JPanel(new BorderLayout(0, 6));
        middle.setOpaque(false);
        middle.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        middle.add(Theme.requiredLabel("Password"), BorderLayout.NORTH);
        middle.add(field, BorderLayout.CENTER);
        middle.add(errorLabel, BorderLayout.SOUTH);

        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.setOpaque(false);
        top.add(Theme.label("Confirm with your password", Theme.FONT_HEADING, Theme.TEXT_DARK), BorderLayout.NORTH);
        top.add(Theme.label(Theme.wrap("Type your Admin password to " + action + ".", 360),
                Theme.FONT_BODY, Theme.TEXT_MUTED), BorderLayout.CENTER);

        JPanel card = Theme.card(24);
        card.setLayout(new BorderLayout(0, 8));
        card.add(top, BorderLayout.NORTH);
        card.add(middle, BorderLayout.CENTER);
        card.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(card);
        dialog.getRootPane().setDefaultButton(confirm);                       // Enter = Confirm
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),    // Esc = Cancel
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setSize(420, 320);                                             // Bulletin Board dialog size
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);                                              // waits here until it closes
        return result[0];
    }

    /** Shows the payout slip as plain text with a Print button (rubric II.A.5: output). */
    public static void showSlip(Component parent, String title, List<String> lines) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);

        JTextArea text = new JTextArea(String.join("\n", lines));
        text.setEditable(false);
        text.setFont(Theme.FONT_MONO.deriveFont(Font.PLAIN, 13f));   // monospaced, so the columns line up
        text.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JScrollPane scroll = new JScrollPane(text);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));

        Widgets.RoundedButton print = new Widgets.RoundedButton("Print", Widgets.RoundedButton.Kind.PRIMARY);
        Widgets.RoundedButton close = new Widgets.RoundedButton("Close", Widgets.RoundedButton.Kind.SECONDARY);
        print.setMnemonic(KeyEvent.VK_P);
        Theme.tip(print, "Send this slip to a printer (Alt+P)");
        print.addActionListener(e -> {
            try {
                text.print();                                         // Java's built-in print dialog
            } catch (PrinterException ex) {
                JOptionPane.showMessageDialog(dialog, "The slip could not be printed: " + ex.getMessage());
            }
        });
        close.addActionListener(e -> dialog.dispose());

        JPanel buttons = new JPanel(new GridLayout(1, 2, 10, 0));
        buttons.setOpaque(false);
        buttons.add(print);
        buttons.add(close);
        JPanel card = Theme.card(20);
        card.setLayout(new BorderLayout(0, 14));
        card.add(scroll, BorderLayout.CENTER);
        card.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(card);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setSize(560, 580);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }
}
