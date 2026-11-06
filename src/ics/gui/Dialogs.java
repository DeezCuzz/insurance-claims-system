package ics.gui;

import javax.print.*;
import javax.print.attribute.*;
import javax.print.attribute.standard.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.print.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.List;

public final class Dialogs {
    private Dialogs() {
    }

    public static String askPassword(Component parent, String action, String error) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "Confirm with your password", Dialog.ModalityType.APPLICATION_MODAL);
        final String[] result = {null};

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
        dialog.getRootPane().setDefaultButton(confirm);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setSize(420, 320);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return result[0];
    }

    public static void showSlip(Component parent, String title, List<String> lines) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);

        JTextArea text = new JTextArea(String.join("\n", lines));
        text.setEditable(false);
        text.setFont(Theme.FONT_MONO.deriveFont(Font.PLAIN, 13f));
        text.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JScrollPane scroll = new JScrollPane(text);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));

        Widgets.RoundedButton print = new Widgets.RoundedButton("Print", Widgets.RoundedButton.Kind.PRIMARY);
        Widgets.RoundedButton close = new Widgets.RoundedButton("Close", Widgets.RoundedButton.Kind.SECONDARY);
        print.setMnemonic(KeyEvent.VK_P);
        Theme.tip(print, "Send this slip to a printer (Alt+P)");
        JLabel status = Theme.label(" ", Theme.FONT_CAPTION, Theme.TEXT_MUTED);
        print.addActionListener(e -> printSlip(dialog, text, status, print));
        close.addActionListener(e -> dialog.dispose());

        JPanel buttons = new JPanel(new GridLayout(1, 2, 10, 0));
        buttons.setOpaque(false);
        buttons.add(print);
        buttons.add(close);
        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.setOpaque(false);
        south.add(status, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);
        JPanel card = Theme.card(20);
        card.setLayout(new BorderLayout(0, 14));
        card.add(scroll, BorderLayout.CENTER);
        card.add(south, BorderLayout.SOUTH);

        dialog.setContentPane(card);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setSize(560, 580);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private static void printSlip(JDialog dialog, JTextArea text, JLabel status, JButton print) {
        if (PrintServiceLookup.lookupPrintServices(null, null).length == 0) {
            status.setText("No printer is set up on this computer. Install one (Windows has Microsoft Print to PDF) and try again.");
            return;
        }
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintable(text.getPrintable(null, null));
        PrintRequestAttributeSet settings = new HashPrintRequestAttributeSet();
        settings.add(DialogTypeSelection.COMMON);
        settings.add(new DialogOwner(dialog));
        if (!job.printDialog(settings)) {
            status.setText("Printing cancelled.");
            return;
        }
        print.setEnabled(false);
        status.setText(Theme.wrap("Sending the slip to the printer... If you chose Microsoft Print to PDF, "
                + "a Save window opens: look for it on the taskbar.", 500));
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws PrinterException {
                job.print(settings);
                return null;
            }

            @Override
            protected void done() {
                print.setEnabled(true);
                try {
                    get();
                    status.setText("Sent to the printer.");
                } catch (InterruptedException | ExecutionException ex) {
                    status.setText(Theme.wrap("The slip could not be printed: " + reasonOf(ex), 500));
                }
            }
        }.execute();
    }

    private static String reasonOf(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t.getMessage() != null && !t.getMessage().isBlank() && !(t instanceof ExecutionException)) {
                return t.getMessage();
            }
        }
        return error.getClass().getSimpleName();
    }
}
