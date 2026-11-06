package ics.gui;

import ics.exception.*;
import ics.model.*;
import ics.service.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

@SuppressWarnings({"serial", "this-escape"})
public class LoginPanel extends JPanel {
    private static final int MAX_TRIES = 3;

    public interface LoginListener {
        void loggedIn(User user);
    }

    private final AuthService auth;
    private final LoginListener onSuccess;
    private final Runnable onTerminate;

    private final JTextField usernameField = Theme.styleField(new JTextField());
    private final JPasswordField passwordField = Theme.styleField(new JPasswordField());
    private final JLabel errorLabel = Theme.label(" ", Theme.FONT_LABEL, Theme.ERROR);
    private final Widgets.RoundedButton loginButton = new Widgets.RoundedButton("Log in", Widgets.RoundedButton.Kind.PRIMARY);
    private int wrongTries;

    public LoginPanel(AuthService auth, LoginListener onSuccess, Runnable onTerminate) {
        super(new GridBagLayout());
        this.auth = auth;
        this.onSuccess = onSuccess;
        this.onTerminate = onTerminate;
        setBackground(Theme.BACKGROUND);

        Theme.tip(usernameField, "The username your Admin gave you (required)");
        Theme.tip(passwordField, "Your password (required). Three wrong tries close the program.");
        loginButton.setMnemonic(KeyEvent.VK_L);
        Theme.tip(loginButton, "Log in (Enter or Alt+L)");
        loginButton.setPreferredSize(new Dimension(0, 40));
        loginButton.addActionListener(e -> attemptLogin());
        passwordField.addActionListener(e -> attemptLogin());
        usernameField.addActionListener(e -> passwordField.requestFocusInWindow());

        JLabel logo = new JLabel(new Widgets.ShieldIcon(56, Theme.NAVY, Theme.WHITE));
        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.add(left(logo));
        stack.add(Box.createVerticalStrut(12));
        stack.add(left(Theme.label("Insurance Claims System", Theme.FONT_HEADING, Theme.NAVY)));
        stack.add(left(Theme.label("Log in to continue", Theme.FONT_BODY, Theme.TEXT_MUTED)));
        stack.add(Box.createVerticalStrut(20));
        stack.add(left(Theme.requiredLabel("Username")));
        stack.add(Box.createVerticalStrut(4));
        stack.add(left(usernameField));
        stack.add(Box.createVerticalStrut(14));
        stack.add(left(Theme.requiredLabel("Password")));
        stack.add(Box.createVerticalStrut(4));
        stack.add(left(passwordField));
        stack.add(Box.createVerticalStrut(8));
        stack.add(left(errorLabel));
        stack.add(Box.createVerticalStrut(12));
        stack.add(left(loginButton));
        stack.add(Box.createVerticalStrut(12));
        stack.add(left(Theme.label("No account? Ask your Admin to create one.", Theme.FONT_CAPTION, Theme.TEXT_MUTED)));

        JPanel card = Theme.card(32);
        card.setLayout(new BorderLayout());
        card.setPreferredSize(new Dimension(400, 520));
        card.add(stack, BorderLayout.CENTER);
        add(card);
    }

    private static JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (c instanceof JTextField || c instanceof Widgets.RoundedButton) {
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        }
        return c;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        SwingUtilities.getRootPane(this).setDefaultButton(loginButton);
        SwingUtilities.invokeLater(() -> usernameField.requestFocusInWindow());
    }

    private void attemptLogin() {
        try {
            User user = auth.login(usernameField.getText(), new String(passwordField.getPassword()));
            onSuccess.loggedIn(user);
        } catch (LoginException e) {
            if (e.isJustBlocked()) {
                announce(e.getMessage() + "\nAsk an Admin to unblock it. The system will now close.");
                onTerminate.run();
                return;
            }
            if (e.isBlocked()) {
                setError(e.getMessage());
                return;
            }
            wrongTries++;
            if (wrongTries >= MAX_TRIES) {
                announce("Too many wrong tries. The system will now close.");
                onTerminate.run();
                return;
            }
            int left = e.getTriesLeft() >= 0 ? Math.min(e.getTriesLeft(), MAX_TRIES - wrongTries) : MAX_TRIES - wrongTries;
            setError(e.getMessage() + " " + left + (left == 1 ? " try left." : " tries left."));
            passwordField.setText("");
            passwordField.requestFocusInWindow();
        } catch (ICSException e) {
            setError(e.getMessage());
        }
    }

    private void setError(String text) {
        errorLabel.setText(Theme.wrap(text, 330));
    }

    protected void announce(String message) {
        JOptionPane.showMessageDialog(this, message, "Login", JOptionPane.WARNING_MESSAGE);
    }
}
