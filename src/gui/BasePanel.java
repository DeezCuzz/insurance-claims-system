package ics.gui;

import ics.exception.*;
import javax.swing.*;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.*;
import java.awt.event.*;

@SuppressWarnings({"serial", "this-escape"})
public abstract class BasePanel extends JPanel {
    private static final int MAX_PASSWORD_TRIES = 3;

    private final JPanel body = new JPanel(new BorderLayout());
    protected final JLabel statusLabel = Theme.label(" ", Theme.FONT_LABEL, Theme.SUCCESS);
    private String protectedMessage = "";

    protected interface ProtectedAction {
        void run(String adminPassword) throws ICSException;
    }

    protected enum Outcome { DONE, CANCELLED, FAILED }

    protected BasePanel(String title, String subtitle) {
        super(new BorderLayout(0, 16));
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel titleLabel = Theme.label(title, Theme.FONT_TITLE, Theme.NAVY);
        JLabel subtitleLabel = Theme.label(subtitle, Theme.FONT_BODY, Theme.TEXT_MUTED);
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);
        subtitleLabel.setAlignmentX(LEFT_ALIGNMENT);
        header.add(titleLabel);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitleLabel);

        statusLabel.setVerticalAlignment(JLabel.TOP);
        statusLabel.setPreferredSize(new Dimension(10, 36));

        statusLabel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                fitStatusHeight();
            }
        });

        body.setOpaque(false);
        add(header, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
    }

    protected JPanel getBody() {
        return body;
    }

    public void onShow() {
    }

    protected void setStatus(String text, boolean ok) {
        statusLabel.setForeground(ok ? Theme.SUCCESS : Theme.ERROR);
        statusLabel.setText(text == null || text.isBlank() ? " " : "<html>" + Theme.escape(text) + "</html>");
        fitStatusHeight();
    }

    private void fitStatusHeight() {
        int height = 36;
        int width = statusLabel.getWidth();
        View view = (View) statusLabel.getClientProperty(BasicHTML.propertyKey);
        if (width > 0 && view != null) {
            view.setSize(width, 0);
            int needed = (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS)) + 4;
            height = Math.max(36, needed);
        }
        if (statusLabel.getPreferredSize().height != height) {
            statusLabel.setPreferredSize(new Dimension(10, height));
            statusLabel.revalidate();
        }
    }

    protected boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(this, message, "Please confirm",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    protected String askAdminPassword(String actionText, String error) {
        return Dialogs.askPassword(this, actionText, error);
    }

    protected Outcome runProtected(String actionText, ProtectedAction action) {
        String error = "";
        for (int attempt = 1; attempt <= MAX_PASSWORD_TRIES; attempt++) {
            String password = askAdminPassword(actionText, error);
            if (password == null) {
                protectedMessage = "Nothing was changed.";
                return Outcome.CANCELLED;
            }
            try {
                action.run(password);
                return Outcome.DONE;
            } catch (AccessDeniedException e) {
                int left = MAX_PASSWORD_TRIES - attempt;
                error = e.getMessage() + (left > 0 ? " " + left + (left == 1 ? " try left." : " tries left.") : "");
            } catch (ICSException e) {
                protectedMessage = e.getMessage();
                return Outcome.FAILED;
            }
        }
        protectedMessage = "Too many wrong passwords. Nothing was changed.";
        return Outcome.FAILED;
    }

    protected String getProtectedMessage() {
        return protectedMessage;
    }

    protected void onKey(String keyStroke, Runnable action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyStroke), keyStroke);
        getActionMap().put(keyStroke, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    protected static void addField(JPanel form, int index, JLabel label, JComponent field) {
        GridBagConstraints labelSpot = new GridBagConstraints();
        labelSpot.gridx = 0;
        labelSpot.gridy = index * 2;
        labelSpot.gridwidth = 2;
        labelSpot.anchor = GridBagConstraints.WEST;
        labelSpot.insets = new Insets(index == 0 ? 0 : 14, 0, 4, 0);
        form.add(label, labelSpot);

        GridBagConstraints fieldSpot = new GridBagConstraints();
        fieldSpot.gridx = 0;
        fieldSpot.gridy = index * 2 + 1;
        fieldSpot.gridwidth = 2;
        fieldSpot.weightx = 1;
        fieldSpot.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, fieldSpot);
    }
}
