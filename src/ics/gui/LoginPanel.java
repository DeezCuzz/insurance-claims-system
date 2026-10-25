package ics.gui;

import ics.exception.ICSException;
import ics.exception.LoginException;
import ics.model.User;
import ics.service.AuthService;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.event.KeyEvent;

/**
 * The login screen (rubric II.C.1 and II.C.6). There is no sign-up here: only an Admin creates accounts.
 *
 * Three wrong tries in one sitting close the program. If they were on a real account, AuthService has
 * also blocked it, and only an Admin can unblock it.
 */
@SuppressWarnings({"serial", "this-escape"})
public class LoginPanel extends JPanel {

    private static final int MAX_TRIES = 3;

    /** What happens after a good login (Main shows the screens for that person). */
    public interface LoginListener {
        void loggedIn(User user);
    }

    private final AuthService auth;
    private final LoginListener onSuccess;
    private final Runnable onTerminate;        // closes the program

    private final JTextField usernameField = Theme.styleField(new JTextField());
    private final JPasswordField passwordField = Theme.styleField(new JPasswordField());
    private final JLabel errorLabel = Theme.label(" ", Theme.FONT_LABEL, Theme.ERROR);
    private final Widgets.RoundedButton loginButton = new Widgets.RoundedButton("Log in", Widgets.RoundedButton.Kind.PRIMARY);
    private int wrongTries;                    // wrong tries since the program started

    public LoginPanel(AuthService auth, LoginListener onSuccess, Runnable onTerminate) {
        super(new GridBagLayout());            // GridBagLayout with one child = the card sits in the middle
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
        passwordField.addActionListener(e -> attemptLogin());     // Enter in the password field logs in
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

    /** Lines a part up on the left; text fields and the button stretch across the card. */
    private static JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (c instanceof JTextField || c instanceof Widgets.RoundedButton) {
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        }
        return c;
    }

    @Override
    public void addNotify() {
        super.addNotify();                                         // the panel is now inside the window
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
                setError(e.getMessage());                          // blocked before: not a new wrong try
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
            setError(e.getMessage());  // a file could not be saved
        }
    }

    private void setError(String text) {
        errorLabel.setText(Theme.wrap(text, 330));
    }

    /** Shows a message box. (A separate method so tests can catch it instead.) */
    protected void announce(String message) {
        JOptionPane.showMessageDialog(this, message, "Login", JOptionPane.WARNING_MESSAGE);
    }
}
