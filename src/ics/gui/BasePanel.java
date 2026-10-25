package ics.gui;

import ics.exception.AccessDeniedException;
import ics.exception.ICSException;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.ActionEvent;

/**
 * The parent of every screen (INHERITANCE). It gives each screen, without repeating the code:
 *   - a big title and a one-line subtitle at the top (rubric II.A.3)
 *   - a body area for the screen's own layout
 *   - a status line for messages (green = done, red = problem)
 *   - "are you sure?" and "type your Admin password" helpers
 *
 * To make a new screen: extend BasePanel, call super("Title", "subtitle"), build inside getBody(),
 * and override onShow() to reload data each time the screen opens.
 */
@SuppressWarnings({"serial", "this-escape"})
public abstract class BasePanel extends JPanel {

    private static final int MAX_PASSWORD_TRIES = 3;

    private final JPanel body = new JPanel(new BorderLayout());
    protected final JLabel statusLabel = Theme.label(" ", Theme.FONT_LABEL, Theme.SUCCESS);
    private String protectedMessage = "";

    /** What a password-protected button does once the Admin has typed the password. */
    protected interface ProtectedAction {
        void run(String adminPassword) throws ICSException;
    }

    /** How a password-protected action ended. */
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
        statusLabel.setPreferredSize(new Dimension(10, 36));   // room for a message on two lines

        body.setOpaque(false);
        add(header, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
    }

    protected JPanel getBody() {
        return body;
    }

    /** Called every time this screen is opened. Screens override it to reload their tables. */
    public void onShow() {
    }

    /** Shows a message in the status line: green if ok, red if it is a problem. */
    protected void setStatus(String text, boolean ok) {
        statusLabel.setForeground(ok ? Theme.SUCCESS : Theme.ERROR);
        statusLabel.setText(text == null || text.isBlank() ? " " : "<html>" + Theme.escape(text) + "</html>");
    }

    /** Asks "are you sure?" before something that cannot be undone. (A separate method so tests can answer it.) */
    protected boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(this, message, "Please confirm",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /** Shows the password window. (A separate method so tests can answer it.) */
    protected String askAdminPassword(String actionText, String error) {
        return Dialogs.askPassword(this, actionText, error);
    }

    /**
     * Asks for the Admin password (up to three tries), then runs the action (rubric II.C.4).
     * A wrong password never changes anything. After CANCELLED or FAILED, getProtectedMessage() says why.
     */
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

    /** Runs the action when this key is pressed on this screen, for example  onKey("ctrl N", ...)  (rubric I.A.3). */
    protected void onKey(String keyStroke, Runnable action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyStroke), keyStroke);
        getActionMap().put(keyStroke, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    /** Puts a label above a field in a GridBagLayout form. index = 0 for the first field, 1 for the next... */
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
