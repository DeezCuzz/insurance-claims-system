package ics.gui;

import ics.exception.ICSException;
import ics.exception.InvalidInputException;
import ics.model.User;
import ics.service.UserService;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin screen: create, edit, deactivate, unblock and delete accounts (rubric II.A.2, II.C.2, II.C.4, II.C.5).
 *
 *   left   the form. Empty = "New account". Click a row in the table = "Edit account".
 *   right  search box and the table of accounts
 *
 * The rules are in UserService; this screen only collects what was typed and shows the result.
 */
@SuppressWarnings({"serial", "this-escape"})
public class UserPanel extends BasePanel {

    private final UserService service;
    private final User admin;                  // the Admin using this screen
    private User selected;                     // the account being edited, or null for a new one

    private final JLabel formTitle = Theme.label("New account", Theme.FONT_HEADING, Theme.TEXT_DARK);
    private final JComboBox<User.Role> roleBox = new JComboBox<>(User.Role.values());
    private final JTextField usernameField = Theme.styleField(new JTextField());
    private final JTextField fullNameField = Theme.styleField(new JTextField());
    private final JTextField contactField = Theme.styleField(new JTextField());
    private final JTextField addressField = Theme.styleField(new JTextField());
    private final JPasswordField passwordField = Theme.styleField(new JPasswordField());
    private final JPasswordField confirmField = Theme.styleField(new JPasswordField());
    private final Map<String, JComponent> fieldsByName = new HashMap<>();

    private final Widgets.RoundedButton addButton = new Widgets.RoundedButton("Add user", Widgets.RoundedButton.Kind.PRIMARY);
    private final Widgets.RoundedButton updateButton = new Widgets.RoundedButton("Update", Widgets.RoundedButton.Kind.PRIMARY);
    private final Widgets.RoundedButton clearButton = new Widgets.RoundedButton("Clear", Widgets.RoundedButton.Kind.SECONDARY);
    private final Widgets.RoundedButton blockButton = new Widgets.RoundedButton("Deactivate", Widgets.RoundedButton.Kind.SECONDARY);
    private final Widgets.RoundedButton deleteButton = new Widgets.RoundedButton("Delete", Widgets.RoundedButton.Kind.SECONDARY);

    private final JTextField searchField = Theme.styleField(new JTextField());
    private final JLabel countLabel = Theme.label("", Theme.FONT_CAPTION, Theme.TEXT_MUTED);
    private final Widgets.Table table = new Widgets.Table(
            new String[] {"ID", "Username", "Full name", "Role", "Contact", "Status"},
            new int[] {80, 95, 125, 60, 115, 75});

    public UserPanel(UserService service, User admin) {
        super("Users", "Admin only: create the accounts people log in with");
        this.service = service;
        this.admin = admin;
        fieldsByName.put("Role", roleBox);
        fieldsByName.put("Username", usernameField);
        fieldsByName.put("Full name", fullNameField);
        fieldsByName.put("Contact number", contactField);
        fieldsByName.put("Password", passwordField);
        fieldsByName.put("Confirm password", confirmField);
        buildForm();
        buildTable();
        wireActions();
        clearForm();
    }

    // ------------------------------------------------------------------ layout

    private void buildForm() {
        Theme.styleCombo(roleBox);
        Widgets.showWords(roleBox, role -> role == User.Role.ADMIN ? "Admin" : "User");
        Theme.tip(roleBox, "Admin: manages the system. User: applies for claims.");
        Theme.tip(usernameField, "What they type to log in (required, must be unique)");
        Theme.tip(fullNameField, "Their real name (required)");
        Theme.tip(contactField, "Mobile or phone number (required)");
        Theme.tip(addressField, "Home address (optional)");
        Theme.tip(passwordField, "At least 8 characters, with a letter and a number (required)");
        Theme.tip(confirmField, "Type the same password again (required)");

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        addField(form, 0, Theme.fieldLabel("Role"), roleBox);
        addField(form, 1, Theme.requiredLabel("Username"), usernameField);
        addField(form, 2, Theme.requiredLabel("Full name"), fullNameField);
        addField(form, 3, Theme.requiredLabel("Contact number"), contactField);
        addField(form, 4, Theme.fieldLabel("Address"), addressField);
        addField(form, 5, Theme.requiredLabel("Password"), passwordField);
        addField(form, 6, Theme.requiredLabel("Confirm password"), confirmField);

        addButton.setMnemonic(KeyEvent.VK_A);
        updateButton.setMnemonic(KeyEvent.VK_U);
        clearButton.setMnemonic(KeyEvent.VK_C);
        deleteButton.setMnemonic(KeyEvent.VK_D);
        Theme.tip(addButton, "Create the account (Alt+A)");
        Theme.tip(updateButton, "Save the changes (Alt+U)");
        Theme.tip(clearButton, "Empty the form to add a new account (Alt+C or Ctrl+N)");
        Theme.tip(blockButton, "Deactivate: the account cannot log in until you unblock it. Unblock: let it log in again.");
        Theme.tip(deleteButton, "Delete the selected account for good (Alt+D). Asks for your password.");

        JPanel topRow = new JPanel(new GridLayout(1, 3, 10, 0));
        topRow.setOpaque(false);
        topRow.add(addButton);
        topRow.add(updateButton);
        topRow.add(clearButton);
        JPanel bottomRow = new JPanel(new GridLayout(1, 2, 10, 0));
        bottomRow.setOpaque(false);
        bottomRow.add(blockButton);
        bottomRow.add(deleteButton);
        JPanel buttons = new JPanel(new GridLayout(2, 1, 0, 10));
        buttons.setOpaque(false);
        buttons.add(topRow);
        buttons.add(bottomRow);

        JPanel south = new JPanel(new BorderLayout(0, 10));
        south.setOpaque(false);
        south.add(buttons, BorderLayout.NORTH);
        south.add(statusLabel, BorderLayout.SOUTH);

        JPanel card = Theme.card(24);
        card.setLayout(new BorderLayout(0, 14));
        card.setPreferredSize(new Dimension(340, 0));
        card.add(formTitle, BorderLayout.NORTH);
        card.add(Widgets.scrollDown(form), BorderLayout.CENTER);    // fields scroll if the window is short
        card.add(south, BorderLayout.SOUTH);
        getBody().setLayout(new BorderLayout(16, 0));
        getBody().add(card, BorderLayout.WEST);
    }

    private void buildTable() {
        Theme.tip(searchField, "Type part of an ID, username, name or contact (Ctrl+F)");
        table.colourColumn(5, (row, text) -> text.equals("Blocked") ? Theme.ERROR : Theme.SUCCESS);

        JPanel searchRow = new JPanel(new BorderLayout(10, 0));
        searchRow.setOpaque(false);
        searchRow.add(Theme.fieldLabel("Search"), BorderLayout.WEST);
        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(countLabel, BorderLayout.EAST);

        JPanel card = Theme.card(20);
        card.setLayout(new BorderLayout(0, 14));
        card.add(searchRow, BorderLayout.NORTH);
        card.add(Widgets.scroll(table), BorderLayout.CENTER);
        getBody().add(card, BorderLayout.CENTER);
    }

    private void wireActions() {
        addButton.addActionListener(e -> addUser());
        updateButton.addActionListener(e -> updateUser());
        clearButton.addActionListener(e -> {
            clearForm();
            setStatus(" ", true);
        });
        blockButton.addActionListener(e -> {
            if (selected != null && selected.isBlocked()) {
                unblockUser();
            } else {
                deactivateUser();
            }
        });
        deleteButton.addActionListener(e -> deleteUser());
        Widgets.onTextChange(searchField, () -> refreshTable());
        table.onRowSelected(() -> loadSelected());
        onKey("ctrl N", () -> clearForm());                         // keyboard shortcuts (rubric I.A.3)
        onKey("ctrl F", () -> searchField.requestFocusInWindow());
    }

    @Override
    public void onShow() {
        refreshTable();
    }

    // ------------------------------------------------------------------ the buttons

    private void addUser() {
        try {
            User created = service.createAccount((User.Role) roleBox.getSelectedItem(), usernameField.getText(),
                    new String(passwordField.getPassword()), new String(confirmField.getPassword()),
                    fullNameField.getText(), contactField.getText(), addressField.getText());
            refreshTable();
            clearForm();
            setStatus("Created " + created.getId() + " (" + created.getUsername() + "). They can log in now.", true);
        } catch (InvalidInputException e) {
            showFieldError(e);
        } catch (ICSException e) {
            setStatus(e.getMessage(), false);
        }
    }

    private void updateUser() {
        String id = selected.getId();
        try {
            service.updateProfile(id, fullNameField.getText(), contactField.getText(), addressField.getText());
            refreshTable();
            table.selectQuietly(id);
            setStatus("Saved the changes to " + id + ".", true);
        } catch (InvalidInputException e) {
            showFieldError(e);
        } catch (ICSException e) {
            setStatus(e.getMessage(), false);
        }
    }

    private void unblockUser() {
        String id = selected.getId();
        try {
            service.unblock(id);
            refreshTable();
            setStatus("Unblocked " + id + ". They can log in again.", true);
        } catch (ICSException e) {
            setStatus(e.getMessage(), false);
        }
    }

    private void deactivateUser() {
        String id = selected.getId();
        if (!confirm("Deactivate " + selected.getUsername() + " (" + id + ")?\n"
                + "They will not be able to log in until you unblock them.")) {
            setStatus("Nothing was changed.", true);
            return;
        }
        try {
            service.deactivate(id, admin);
            refreshTable();
            setStatus("Deactivated " + id + ". Unblock it to let them log in again.", true);
        } catch (ICSException e) {
            setStatus(e.getMessage(), false);
        }
    }

    /** Asks for the Admin password first (runProtected, in BasePanel). */
    private void deleteUser() {
        String id = selected.getId();
        Outcome outcome = runProtected("delete the account " + selected.getUsername() + " (" + id + ")",
                password -> service.delete(id, admin, password));
        if (outcome == Outcome.DONE) {
            refreshTable();
            clearForm();
            setStatus("Deleted " + id + ".", true);
        } else {
            setStatus(getProtectedMessage(), outcome == Outcome.CANCELLED);
        }
    }

    // ------------------------------------------------------------------ the form and the table

    /** Fills the table from the search box, and keeps the edited account selected. */
    private void refreshTable() {
        List<User> found = service.search(searchField.getText());
        List<Object[]> rows = new ArrayList<>();
        for (User u : found) {
            rows.add(new Object[] {u.getId(), u.getUsername(), u.getFullName(),
                    u.canManageUsers() ? "Admin" : "User", u.getContact(), u.isBlocked() ? "Blocked" : "Active"});
        }
        table.setRows(rows, selected == null ? null : selected.getId());
        countLabel.setText(found.size() + (found.size() == 1 ? " user" : " users"));
        if (selected != null) {                       // the account may have changed: read it again
            for (User u : found) {
                if (u.getId().equals(selected.getId())) {
                    selected = u;
                }
            }
            setEditing(true);
        }
    }

    /** A row was clicked: show that account in the form. */
    private void loadSelected() {
        String id = (String) table.getValueAt(table.getSelectedRow(), 0);
        for (User u : service.search("")) {
            if (u.getId().equals(id)) {
                selected = u;
            }
        }
        roleBox.setSelectedItem(selected.getRole());
        usernameField.setText(selected.getUsername());
        fullNameField.setText(selected.getFullName());
        contactField.setText(selected.getContact());
        addressField.setText(selected.getAddress());
        passwordField.setText("");
        confirmField.setText("");
        setEditing(true);
        setStatus(" ", true);
    }

    private void clearForm() {
        selected = null;
        table.clearSelectionQuietly();
        roleBox.setSelectedItem(User.Role.USER);
        usernameField.setText("");
        fullNameField.setText("");
        contactField.setText("");
        addressField.setText("");
        passwordField.setText("");
        confirmField.setText("");
        setEditing(false);
        usernameField.requestFocusInWindow();
    }

    /** Editing: username, role and password are locked; Update, Deactivate/Unblock and Delete are available. */
    private void setEditing(boolean editing) {
        formTitle.setText(editing ? "Edit account" : "New account");
        roleBox.setEnabled(!editing);
        usernameField.setEnabled(!editing);
        passwordField.setEnabled(!editing);
        confirmField.setEnabled(!editing);
        addButton.setEnabled(!editing);
        updateButton.setEnabled(editing);
        blockButton.setEnabled(editing);
        deleteButton.setEnabled(editing);
        boolean blocked = editing && selected != null && selected.isBlocked();
        blockButton.setText(blocked ? "Unblock" : "Deactivate");
        blockButton.setMnemonic(blocked ? KeyEvent.VK_B : KeyEvent.VK_V);
    }

    /** Shows the message and puts the cursor in the field that is wrong. */
    private void showFieldError(InvalidInputException e) {
        setStatus(e.getMessage(), false);
        JComponent field = fieldsByName.get(e.getFieldName());
        if (field != null) {
            field.requestFocusInWindow();
        }
    }
}
