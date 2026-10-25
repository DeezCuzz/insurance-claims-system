package ics.gui;

import ics.exception.ICSException;
import ics.exception.InvalidInputException;
import ics.model.User;
import ics.service.AuthService;

import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

/** Anyone changes their own password (rubric II.C.3). */
@SuppressWarnings({"serial", "this-escape"})
public class ChangePasswordPanel extends BasePanel {

    private final AuthService auth;
    private final User user;

    private final JPasswordField currentField = Theme.styleField(new JPasswordField());
    private final JPasswordField newField = Theme.styleField(new JPasswordField());
    private final JPasswordField confirmField = Theme.styleField(new JPasswordField());
    private final Widgets.RoundedButton changeButton = new Widgets.RoundedButton("Change password", Widgets.RoundedButton.Kind.PRIMARY);
    private final Map<String, JPasswordField> fieldsByName = new HashMap<>();   // to jump to the field that is wrong

    public ChangePasswordPanel(AuthService auth, User user) {
        super("Change Password", "Choose a new password for " + user.getUsername());
        this.auth = auth;
        this.user = user;
        fieldsByName.put("Current password", currentField);
        fieldsByName.put("New password", newField);
        fieldsByName.put("Confirm new password", confirmField);

        Theme.tip(currentField, "The password you logged in with (required)");
        Theme.tip(newField, "At least 8 characters, with a letter and a number (required)");
        Theme.tip(confirmField, "Type the new password again (required)");
        changeButton.setMnemonic(KeyEvent.VK_P);
        Theme.tip(changeButton, "Save the new password (Alt+P or Enter)");
        changeButton.setPreferredSize(new Dimension(0, 40));
        changeButton.addActionListener(e -> change());

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        addField(form, 0, Theme.requiredLabel("Current password"), currentField);
        addField(form, 1, Theme.requiredLabel("New password"), newField);
        addField(form, 2, Theme.requiredLabel("Confirm new password"), confirmField);

        JPanel south = new JPanel(new BorderLayout(0, 10));
        south.setOpaque(false);
        south.add(Theme.label("At least 8 characters, with a letter and a number.", Theme.FONT_CAPTION, Theme.TEXT_MUTED),
                BorderLayout.NORTH);
        south.add(changeButton, BorderLayout.CENTER);
        south.add(statusLabel, BorderLayout.SOUTH);

        JPanel card = Theme.card(28);
        card.setLayout(new BorderLayout(0, 18));
        card.setPreferredSize(new Dimension(440, 420));
        card.add(Theme.label("Your password", Theme.FONT_HEADING, Theme.TEXT_DARK), BorderLayout.NORTH);
        card.add(form, BorderLayout.CENTER);
        card.add(south, BorderLayout.SOUTH);

        JPanel holder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));   // keeps the card its own size
        holder.setOpaque(false);
        holder.add(card);
        getBody().add(holder, BorderLayout.NORTH);
    }

    @Override
    public void onShow() {
        if (SwingUtilities.getRootPane(this) != null) {
            SwingUtilities.getRootPane(this).setDefaultButton(changeButton);   // Enter = Change password
        }
        currentField.requestFocusInWindow();
    }

    private void change() {
        try {
            auth.changePassword(user, new String(currentField.getPassword()),
                    new String(newField.getPassword()), new String(confirmField.getPassword()));
            currentField.setText("");
            newField.setText("");
            confirmField.setText("");
            setStatus("Password changed. Use the new one the next time you log in.", true);
        } catch (InvalidInputException e) {
            setStatus(e.getMessage(), false);
            JPasswordField wrong = fieldsByName.get(e.getFieldName());
            if (wrong != null) {
                wrong.setText("");
                wrong.requestFocusInWindow();
            }
        } catch (ICSException e) {
            setStatus(e.getMessage(), false);
        }
    }
}
