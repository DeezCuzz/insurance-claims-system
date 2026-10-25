package ics.gui;

import ics.exception.ICSException;
import ics.exception.InvalidInputException;
import ics.model.AutoPolicy;
import ics.model.HealthPolicy;
import ics.model.Policy;
import ics.model.User;
import ics.service.PolicyService;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin screen: issue, edit, cancel and delete policies (rubric II.A.1, II.A.2, II.A.5).
 *
 *   left   the form. The last two fields change with the type: Health asks for the reimbursement % and the
 *          waiting period, Auto asks for the vehicle value and the plate number. The premium updates as you type.
 *   right  search box and the table of policies
 */
@SuppressWarnings({"serial", "this-escape"})
public class PolicyPanel extends BasePanel {

    /** One choice in the "Policy holder" list. toString() is what the list shows. */
    private static final class HolderItem {
        final User user;
        HolderItem(User user) { this.user = user; }
        @Override public String toString() { return user.getUsername() + " (" + user.getFullName() + ")"; }
    }

    private final PolicyService service;
    private final User admin;
    private Policy selected;                   // the policy being edited, or null for a new one

    private final JLabel formTitle = Theme.label("Issue a policy", Theme.FONT_HEADING, Theme.TEXT_DARK);
    private final JComboBox<Policy.Type> typeBox = new JComboBox<>(Policy.Type.values());
    private final JComboBox<HolderItem> holderBox = new JComboBox<>();
    private final JTextField startField = Theme.styleField(new JTextField());
    private final JTextField endField = Theme.styleField(new JTextField());
    private final JTextField deductibleField = Theme.styleField(new JTextField());
    private final JTextField limitField = Theme.styleField(new JTextField());
    private final JTextField percentField = Theme.styleField(new JTextField());
    private final JTextField waitingField = Theme.styleField(new JTextField());
    private final JTextField vehicleField = Theme.styleField(new JTextField());
    private final JTextField plateField = Theme.styleField(new JTextField());
    private final JLabel percentLabel = Theme.requiredLabel("Reimbursement (%)");
    private final JLabel waitingLabel = Theme.requiredLabel("Waiting period (days)");
    private final JLabel vehicleLabel = Theme.requiredLabel("Vehicle value");
    private final JLabel plateLabel = Theme.requiredLabel("Plate number");
    private final JLabel premiumLabel = Theme.label(" ", Theme.FONT_BUTTON, Theme.NAVY);
    private final Map<String, JComponent> fieldsByName = new HashMap<>();

    private final Widgets.RoundedButton issueButton = new Widgets.RoundedButton("Issue", Widgets.RoundedButton.Kind.PRIMARY);
    private final Widgets.RoundedButton updateButton = new Widgets.RoundedButton("Update", Widgets.RoundedButton.Kind.PRIMARY);
    private final Widgets.RoundedButton clearButton = new Widgets.RoundedButton("Clear", Widgets.RoundedButton.Kind.SECONDARY);
    private final Widgets.RoundedButton cancelButton = new Widgets.RoundedButton("Cancel policy", Widgets.RoundedButton.Kind.SECONDARY);
    private final Widgets.RoundedButton deleteButton = new Widgets.RoundedButton("Delete", Widgets.RoundedButton.Kind.SECONDARY);

    private final JTextField searchField = Theme.styleField(new JTextField());
    private final JLabel countLabel = Theme.label("", Theme.FONT_CAPTION, Theme.TEXT_MUTED);
    private final Widgets.Table table = new Widgets.Table(
            new String[] {"ID", "Holder", "Type", "Status", "Ends", "Limit left"},
            new int[] {82, 80, 75, 95, 100, 125});         // fix F1: room for every word

    public PolicyPanel(PolicyService service, User admin) {
        super("Policies", "Admin only: issue and manage the policies users can claim on");
        this.service = service;
        this.admin = admin;
        fieldsByName.put("Policy type", typeBox);
        fieldsByName.put("Policy holder", holderBox);
        fieldsByName.put("Start date", startField);
        fieldsByName.put("End date", endField);
        fieldsByName.put("Deductible", deductibleField);
        fieldsByName.put("Coverage limit", limitField);
        fieldsByName.put("Reimbursement (%)", percentField);
        fieldsByName.put("Waiting period (days)", waitingField);
        fieldsByName.put("Vehicle value", vehicleField);
        fieldsByName.put("Plate number", plateField);
        buildForm();
        buildTable();
        wireActions();
        clearForm();
    }

    // ------------------------------------------------------------------ layout

    private void buildForm() {
        Theme.styleCombo(typeBox);
        Theme.styleCombo(holderBox);
        Widgets.showWords(typeBox, type -> type.getLabel());
        Theme.tip(typeBox, "Health pays a share of medical bills. Auto pays repair costs.");
        Theme.tip(holderBox, "The User this policy belongs to (required)");
        Theme.tip(startField, "First day of cover, year-month-day (required)");
        Theme.tip(endField, "Last day of cover, year-month-day (required)");
        Theme.tip(deductibleField, "What the holder pays first (required). Health: once for the whole policy. Auto: on every claim.");
        Theme.tip(limitField, "The most this policy will ever pay out (required)");
        Theme.tip(premiumLabel, "A deductible of " + Theme.money(Policy.DISCOUNT_DEDUCTIBLE) + " or more takes 10% off the premium");
        Theme.tip(percentField, "Health: the share of the bill the insurer pays, 1 to 100 (required)");
        Theme.tip(waitingField, "Health: days after the start before claims are accepted, 0 to 365 (required)");
        Theme.tip(vehicleField, "Auto: what the vehicle is worth (required). The premium is 3% of it.");
        Theme.tip(plateField, "Auto: the plate number (required)");

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        addField(form, 0, Theme.fieldLabel("Policy type"), typeBox);
        addField(form, 1, Theme.requiredLabel("Policy holder"), holderBox);
        addField(form, 2, Theme.requiredLabel("Start date"), startField);
        addField(form, 3, Theme.requiredLabel("End date"), endField);
        addField(form, 4, Theme.requiredLabel("Deductible"), deductibleField);
        addField(form, 5, Theme.requiredLabel("Coverage limit"), limitField);
        addField(form, 6, percentLabel, percentField);          // Health only
        addField(form, 7, waitingLabel, waitingField);          // Health only
        addField(form, 8, vehicleLabel, vehicleField);          // Auto only
        addField(form, 9, plateLabel, plateField);              // Auto only

        issueButton.setMnemonic(KeyEvent.VK_I);
        updateButton.setMnemonic(KeyEvent.VK_U);
        clearButton.setMnemonic(KeyEvent.VK_C);
        cancelButton.setMnemonic(KeyEvent.VK_N);
        deleteButton.setMnemonic(KeyEvent.VK_D);
        Theme.tip(issueButton, "Issue the policy (Alt+I)");
        Theme.tip(updateButton, "Save the changes (Alt+U)");
        Theme.tip(clearButton, "Empty the form to issue a new policy (Alt+C or Ctrl+N)");
        Theme.tip(cancelButton, "Stop the policy covering new claims (Alt+N). Asks for your password.");
        Theme.tip(deleteButton, "Delete a policy that has no claims (Alt+D). Asks for your password.");

        JPanel topRow = new JPanel(new GridLayout(1, 3, 10, 0));
        topRow.setOpaque(false);
        topRow.add(issueButton);
        topRow.add(updateButton);
        topRow.add(clearButton);
        JPanel bottomRow = new JPanel(new GridLayout(1, 2, 10, 0));
        bottomRow.setOpaque(false);
        bottomRow.add(cancelButton);
        bottomRow.add(deleteButton);
        JPanel buttons = new JPanel(new GridLayout(2, 1, 0, 10));
        buttons.setOpaque(false);
        buttons.add(topRow);
        buttons.add(bottomRow);

        JPanel south = new JPanel(new BorderLayout(0, 10));
        south.setOpaque(false);
        south.add(premiumLabel, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.CENTER);
        south.add(statusLabel, BorderLayout.SOUTH);

        JPanel card = Theme.card(24);
        card.setLayout(new BorderLayout(0, 14));
        card.setPreferredSize(new Dimension(340, 0));                     // fix F1: leaves the table more room
        card.add(formTitle, BorderLayout.NORTH);
        card.add(Widgets.scrollDown(form), BorderLayout.CENTER);
        card.add(south, BorderLayout.SOUTH);
        getBody().setLayout(new BorderLayout(16, 0));
        getBody().add(card, BorderLayout.WEST);
    }

    private void buildTable() {
        Theme.tip(searchField, "Type part of an ID, holder, type or status (Ctrl+F)");
        table.colourColumn(3, (row, text) -> Widgets.colorForPolicy(text));

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
        issueButton.addActionListener(e -> issuePolicy());
        updateButton.addActionListener(e -> updatePolicy());
        clearButton.addActionListener(e -> {
            clearForm();
            setStatus(" ", true);
        });
        cancelButton.addActionListener(e -> cancelPolicy());
        deleteButton.addActionListener(e -> deletePolicy());
        typeBox.addActionListener(e -> {
            showTypeFields();
            updatePremium();
        });
        Widgets.onTextChange(limitField, () -> updatePremium());
        Widgets.onTextChange(deductibleField, () -> updatePremium());    // fix E2: the deductible changes the price
        Widgets.onTextChange(vehicleField, () -> updatePremium());
        Widgets.onTextChange(searchField, () -> refreshTable());
        table.onRowSelected(() -> loadSelected());
        onKey("ctrl N", () -> clearForm());
        onKey("ctrl F", () -> searchField.requestFocusInWindow());
    }

    /** Reloads the holders (new accounts may exist) and the table every time the screen opens. */
    @Override
    public void onShow() {
        HolderItem current = (HolderItem) holderBox.getSelectedItem();
        holderBox.removeAllItems();
        for (User u : service.policyHolders()) {
            holderBox.addItem(new HolderItem(u));
            if (current != null && current.user.getId().equals(u.getId())) {
                holderBox.setSelectedIndex(holderBox.getItemCount() - 1);
            }
        }
        refreshTable();
    }

    // ------------------------------------------------------------------ the buttons

    private void issuePolicy() {
        HolderItem holder = (HolderItem) holderBox.getSelectedItem();
        try {
            Policy issued = service.issue((Policy.Type) typeBox.getSelectedItem(), holder == null ? "" : holder.user.getId(),
                    startField.getText(), endField.getText(), deductibleField.getText(), limitField.getText(),
                    percentField.getText(), waitingField.getText(), vehicleField.getText(), plateField.getText());
            refreshTable();
            clearForm();
            setStatus("Issued " + issued.getId() + " to " + holder.user.getUsername() + ". Premium: "
                    + Theme.money(issued.calculatePremium()) + ".", true);
        } catch (InvalidInputException e) {
            showFieldError(e);
        } catch (ICSException e) {
            setStatus(e.getMessage(), false);
        }
    }

    private void updatePolicy() {
        String id = selected.getId();
        try {
            selected = service.update(id, startField.getText(), endField.getText(), deductibleField.getText(),
                    limitField.getText(), percentField.getText(), waitingField.getText(), vehicleField.getText(),
                    plateField.getText());
            refreshTable();
            table.selectQuietly(id);
            setStatus("Saved the changes to " + id + ".", true);
        } catch (InvalidInputException e) {
            showFieldError(e);
        } catch (ICSException e) {
            setStatus(e.getMessage(), false);
        }
    }

    private void cancelPolicy() {
        String id = selected.getId();
        Outcome outcome = runProtected("cancel " + id, password -> service.cancel(id, admin, password));
        if (outcome == Outcome.DONE) {
            refreshTable();
            setStatus("Cancelled " + id + ". It will not cover new claims.", true);
        } else {
            setStatus(getProtectedMessage(), outcome == Outcome.CANCELLED);
        }
    }

    private void deletePolicy() {
        String id = selected.getId();
        Outcome outcome = runProtected("delete " + id, password -> service.delete(id, admin, password));
        if (outcome == Outcome.DONE) {
            refreshTable();
            clearForm();
            setStatus("Deleted " + id + ".", true);
        } else {
            setStatus(getProtectedMessage(), outcome == Outcome.CANCELLED);
        }
    }

    // ------------------------------------------------------------------ the form and the table

    private void refreshTable() {
        LocalDate today = LocalDate.now();
        List<Policy> found = service.search(searchField.getText());
        List<Object[]> rows = new ArrayList<>();
        for (Policy p : found) {
            rows.add(new Object[] {p.getId(), service.holderName(p.getOwnerId()), p.getType().getLabel(),
                    p.getStatus(today).getLabel(), p.getEndDate().toString(), Theme.money(p.getRemainingLimit())});
            if (selected != null && p.getId().equals(selected.getId())) {
                selected = p;                                   // read the newest copy
            }
        }
        table.setRows(rows, selected == null ? null : selected.getId());
        countLabel.setText(found.size() + (found.size() == 1 ? " policy" : " policies"));
        if (selected != null) {
            setEditing(true);
        }
    }

    /** A row was clicked: show that policy in the form. */
    private void loadSelected() {
        String id = (String) table.getValueAt(table.getSelectedRow(), 0);
        for (Policy p : service.search("")) {
            if (p.getId().equals(id)) {
                selected = p;
            }
        }
        typeBox.setSelectedItem(selected.getType());
        for (int i = 0; i < holderBox.getItemCount(); i++) {
            if (holderBox.getItemAt(i).user.getId().equals(selected.getOwnerId())) {
                holderBox.setSelectedIndex(i);
            }
        }
        startField.setText(selected.getStartDate().toString());
        endField.setText(selected.getEndDate().toString());
        deductibleField.setText(plain(selected.getDeductible()));
        limitField.setText(plain(selected.getCoverageLimit()));
        percentField.setText("80");
        waitingField.setText("30");
        vehicleField.setText("");
        plateField.setText("");
        if (selected instanceof HealthPolicy) {
            HealthPolicy h = (HealthPolicy) selected;
            percentField.setText(plain(h.getReimbursementRate() * 100));
            waitingField.setText(String.valueOf(h.getWaitingPeriodDays()));
        } else if (selected instanceof AutoPolicy) {
            AutoPolicy a = (AutoPolicy) selected;
            vehicleField.setText(plain(a.getVehicleValue()));
            plateField.setText(a.getPlateNo());
        }
        showTypeFields();
        setEditing(true);
        updatePremium();
        if (selected.isCancelled()) {
            setStatus("This policy is cancelled, so it cannot be edited. It can only be deleted if it has no claims.", true);
        } else if (selected.getStatus(LocalDate.now()) == Policy.Status.EXPIRED) {
            setStatus("This policy has expired, so it cannot be edited.", true);
        } else {
            setStatus(" ", true);
        }
    }

    private void clearForm() {
        selected = null;
        table.clearSelectionQuietly();
        typeBox.setSelectedItem(Policy.Type.HEALTH);
        LocalDate today = LocalDate.now();
        startField.setText(today.toString());
        endField.setText(today.plusYears(1).toString());
        deductibleField.setText("");
        limitField.setText("");
        percentField.setText("80");
        waitingField.setText("30");
        vehicleField.setText("");
        plateField.setText("");
        showTypeFields();
        setEditing(false);
        updatePremium();
    }

    /** Health shows the % and waiting period; Auto shows the vehicle value and plate. */
    private void showTypeFields() {
        boolean health = typeBox.getSelectedItem() == Policy.Type.HEALTH;
        percentLabel.setVisible(health);
        percentField.setVisible(health);
        waitingLabel.setVisible(health);
        waitingField.setVisible(health);
        vehicleLabel.setVisible(!health);
        vehicleField.setVisible(!health);
        plateLabel.setVisible(!health);
        plateField.setVisible(!health);
        revalidate();
    }

    /** Live premium while typing. The formula is in the policy classes; the screen only shows the answer. */
    private void updatePremium() {
        Policy.Type type = (Policy.Type) typeBox.getSelectedItem();
        try {
            double premium = service.previewPremium(type, limitField.getText(), vehicleField.getText(),
                    deductibleField.getText());
            String basis = type == Policy.Type.HEALTH ? "4% of the limit" : "3% of vehicle value";
            boolean discount = premium < service.previewPremium(type, limitField.getText(), vehicleField.getText(), "0");
            premiumLabel.setText(Theme.wrap("Premium: " + Theme.money(premium) + "  (" + basis
                    + (discount ? ", 10% off" : "") + ")", 290));
        } catch (InvalidInputException e) {
            premiumLabel.setText(Theme.wrap(type == Policy.Type.HEALTH ? "Type the coverage limit to see the premium"
                    : "Type the vehicle value to see the premium", 290));
        }
    }

    /** Editing: type and holder are locked. A cancelled or expired policy cannot be updated (fix B6). */
    private void setEditing(boolean editing) {
        boolean cancelled = editing && selected != null && selected.isCancelled();
        boolean expired = editing && selected != null && selected.getStatus(LocalDate.now()) == Policy.Status.EXPIRED;
        formTitle.setText(editing ? "Edit policy" : "Issue a policy");
        typeBox.setEnabled(!editing);
        holderBox.setEnabled(!editing);
        issueButton.setEnabled(!editing);
        updateButton.setEnabled(editing && !cancelled && !expired);        // fix B6
        cancelButton.setEnabled(editing && !cancelled);
        deleteButton.setEnabled(editing);
    }

    private void showFieldError(InvalidInputException e) {
        setStatus(e.getMessage(), false);
        JComponent field = fieldsByName.get(e.getFieldName());
        if (field != null) {
            field.requestFocusInWindow();
        }
    }

    /** 10000.0 becomes "10000", 12.5 stays "12.5" (for putting numbers back into fields). */
    private static String plain(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
