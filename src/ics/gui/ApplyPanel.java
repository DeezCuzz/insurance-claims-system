package ics.gui;

import ics.exception.ICSException;
import ics.exception.InvalidInputException;
import ics.model.AutoPolicy;
import ics.model.Claim;
import ics.model.HealthPolicy;
import ics.model.Policy;
import ics.model.User;
import ics.service.ClaimService;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * User screen: apply for a claim on one of your own policies (rubric II.A.4).
 *
 *   left   the form: policy, incident date, amount, what happened
 *   right  "Before you submit": the policy's terms, the filing deadline, a payout estimate, and a warning
 *          if an automatic check would reject the claim
 */
@SuppressWarnings({"serial", "this-escape"})
public class ApplyPanel extends BasePanel {

    private static final int MAX_DESCRIPTION = 300;

    /** One choice in the "Policy" list, for example "POL-0002 - Auto (Active)". */
    private static final class PolicyItem {
        final Policy policy;
        final String label;
        PolicyItem(Policy policy, LocalDate today) {
            this.policy = policy;
            this.label = policy.getId() + " - " + policy.getType().getLabel() + " (" + policy.getStatus(today).getLabel() + ")";
        }
        @Override public String toString() { return label; }
    }

    private final ClaimService service;
    private final User applicant;
    private String pendingPolicyId;            // set by preselect(), used the next time the screen opens

    private final JComboBox<PolicyItem> policyBox = new JComboBox<>();
    private final JTextField incidentField = Theme.styleField(new JTextField());
    private final JTextField amountField = Theme.styleField(new JTextField());
    private final JLabel amountLabel = Theme.requiredLabel("Claimed amount");
    private final JTextArea descriptionArea = new JTextArea(5, 20);
    private final JLabel counterLabel = Theme.label("0 / " + MAX_DESCRIPTION, Theme.FONT_CAPTION, Theme.TEXT_MUTED);
    private final Widgets.RoundedButton submitButton = new Widgets.RoundedButton("Submit claim", Widgets.RoundedButton.Kind.PRIMARY);
    private final Widgets.RoundedButton clearButton = new Widgets.RoundedButton("Clear", Widgets.RoundedButton.Kind.SECONDARY);
    private final Map<String, JComponent> fieldsByName = new HashMap<>();

    private final JLabel termsLabel = Theme.label(" ", Theme.FONT_BODY, Theme.TEXT_DARK);
    private final JLabel estimateLabel = Theme.label(" ", Theme.FONT_BUTTON, Theme.NAVY);
    private final JLabel warningLabel = Theme.label(" ", Theme.FONT_LABEL, Theme.AMBER);

    public ApplyPanel(ClaimService service, User applicant) {
        super("Apply for Claim", "Register a claim against one of your policies");
        this.service = service;
        this.applicant = applicant;
        fieldsByName.put("Policy", policyBox);
        fieldsByName.put("Incident date", incidentField);
        fieldsByName.put("Claimed amount", amountField);
        fieldsByName.put("Description", descriptionArea);
        buildUi();
        wireActions();
    }

    /** Opens the form with this policy already chosen (the "Apply for a claim" button on My Policies). */
    public void preselect(String policyId) {
        pendingPolicyId = policyId;
    }

    // ------------------------------------------------------------------ layout

    private void buildUi() {
        Theme.styleCombo(policyBox);
        // when there are no policies, say so inside the box instead of showing an empty list
        policyBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value == null) {
                    setText(index == -1 ? "No policies issued to you yet" : "");
                }
                return this;
            }
        });
        Theme.tip(policyBox, "The policy this claim is for (required). Only your own policies are listed.");
        Theme.tip(incidentField, "The day it happened, year-month-day, for example 2026-11-01 (required)");
        Theme.tip(amountField, "How much you are asking for (required). Health: the bill. Auto: the repair estimate.");
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setFont(Theme.FONT_BODY);
        descriptionArea.setBorder(new EmptyBorder(6, 8, 6, 8));
        Theme.tip(descriptionArea, "What happened, where, and what was damaged or treated (required)");
        JScrollPane descriptionScroll = new JScrollPane(descriptionArea);
        descriptionScroll.setBorder(new LineBorder(Theme.BORDER, 1, true));
        descriptionScroll.setPreferredSize(new Dimension(0, 120));
        JPanel descriptionBox = new JPanel(new BorderLayout(0, 4));
        descriptionBox.setOpaque(false);
        descriptionBox.add(descriptionScroll, BorderLayout.CENTER);
        counterLabel.setHorizontalAlignment(JLabel.RIGHT);
        descriptionBox.add(counterLabel, BorderLayout.SOUTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        addField(form, 0, Theme.requiredLabel("Policy"), policyBox);
        addField(form, 1, Theme.requiredLabel("Incident date"), incidentField);
        addField(form, 2, amountLabel, amountField);
        addField(form, 3, Theme.requiredLabel("What happened"), descriptionBox);

        submitButton.setMnemonic(KeyEvent.VK_S);
        clearButton.setMnemonic(KeyEvent.VK_C);
        Theme.tip(submitButton, "Send this claim to the Admin (Alt+S)");
        Theme.tip(clearButton, "Empty the form (Alt+C)");
        JPanel buttons = new JPanel(new GridLayout(1, 2, 10, 0));
        buttons.setOpaque(false);
        buttons.add(submitButton);
        buttons.add(clearButton);
        JPanel south = new JPanel(new BorderLayout(0, 10));
        south.setOpaque(false);
        south.add(buttons, BorderLayout.NORTH);
        south.add(statusLabel, BorderLayout.SOUTH);

        JPanel formCard = Theme.card(24);
        formCard.setLayout(new BorderLayout(0, 14));
        formCard.add(Theme.label("Your claim", Theme.FONT_HEADING, Theme.TEXT_DARK), BorderLayout.NORTH);
        formCard.add(Widgets.scrollDown(form), BorderLayout.CENTER);
        formCard.add(south, BorderLayout.SOUTH);

        // right side: what to know before submitting
        termsLabel.setVerticalAlignment(JLabel.TOP);
        estimateLabel.setOpaque(true);
        estimateLabel.setBackground(Theme.SOFT_PURPLE);
        estimateLabel.setBorder(new EmptyBorder(10, 12, 10, 12));
        warningLabel.setOpaque(true);
        warningLabel.setBorder(new EmptyBorder(10, 12, 10, 12));
        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.add(left(Theme.label("Before you submit", Theme.FONT_HEADING, Theme.TEXT_DARK)));
        info.add(Box.createVerticalStrut(12));
        info.add(left(Theme.fieldLabel("Your policy")));
        info.add(Box.createVerticalStrut(4));
        info.add(left(termsLabel));
        info.add(Box.createVerticalStrut(16));
        info.add(left(Theme.fieldLabel("If it is approved")));
        info.add(Box.createVerticalStrut(4));
        info.add(left(estimateLabel));
        info.add(Box.createVerticalStrut(4));
        info.add(left(Theme.label("Estimate only. The Admin makes the final decision.", Theme.FONT_CAPTION, Theme.TEXT_MUTED)));
        info.add(Box.createVerticalStrut(16));
        info.add(left(Theme.fieldLabel("Automatic checks")));
        info.add(Box.createVerticalStrut(4));
        info.add(left(warningLabel));
        JPanel infoCard = Theme.card(24);
        infoCard.setLayout(new BorderLayout());
        infoCard.add(Widgets.scrollDown(info), BorderLayout.CENTER);

        JPanel columns = new JPanel(new GridLayout(1, 2, 16, 0));
        columns.setOpaque(false);
        columns.add(formCard);
        columns.add(infoCard);
        getBody().add(columns, BorderLayout.CENTER);
    }

    private static JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private void wireActions() {
        submitButton.addActionListener(e -> submit());
        clearButton.addActionListener(e -> {
            clearForm();
            setStatus(" ", true);
        });
        policyBox.addActionListener(e -> refreshPreview());
        Widgets.onTextChange(incidentField, () -> refreshPreview());
        Widgets.onTextChange(amountField, () -> refreshPreview());
        Widgets.onTextChange(descriptionArea, () -> updateCounter());
    }

    @Override
    public void onShow() {
        reloadPolicies();
        if (pendingPolicyId != null) {                     // came from "Apply for a claim" on My Policies
            for (int i = 0; i < policyBox.getItemCount(); i++) {
                if (policyBox.getItemAt(i).policy.getId().equals(pendingPolicyId)) {
                    policyBox.setSelectedIndex(i);
                }
            }
            pendingPolicyId = null;
        }
        if (incidentField.getText().isBlank()) {
            incidentField.setText(service.today().toString());
        }
        refreshPreview();
        if (SwingUtilities.getRootPane(this) != null) {
            SwingUtilities.getRootPane(this).setDefaultButton(submitButton);
        }
        if (policyBox.getItemCount() == 0) {
            setStatus("You have no policies yet. Ask your Admin to issue one before applying.", false);
        }
    }

    // ------------------------------------------------------------------ submitting

    private void submit() {
        PolicyItem item = (PolicyItem) policyBox.getSelectedItem();
        String policyId = item == null ? "" : item.policy.getId();
        // warn first if an automatic rule would reject it; the User may still submit so there is a record
        Claim.Reason warning = null;
        try {
            warning = service.precheck(applicant, policyId, incidentField.getText());
        } catch (ICSException ignored) {
            // the real submit below reports what is wrong with the form
        }
        if (warning != null && !confirm("This claim will be rejected automatically:\n" + warning.getLabel()
                + ".\n\nSubmit it anyway? It will be kept on record.")) {
            setStatus("Not submitted. Nothing was saved.", true);
            return;
        }
        try {
            Claim claim = service.file(applicant, policyId, incidentField.getText(), descriptionArea.getText(),
                    amountField.getText());
            clearForm();
            if (claim.getStatus() == Claim.Status.REJECTED) {
                setStatus("Submitted " + claim.getId() + ", but it was rejected: " + claim.getReason().getLabel() + ".", false);
            } else {
                setStatus("Submitted " + claim.getId() + ". It is now under review. Follow it under My Claims.", true);
            }
        } catch (InvalidInputException e) {
            setStatus(e.getMessage(), false);
            JComponent field = fieldsByName.get(e.getFieldName());
            if (field != null) {
                field.requestFocusInWindow();
            }
        } catch (ICSException e) {
            setStatus(e.getMessage(), false);
        }
    }

    /** Asks yes or no, titled for this screen. */
    @Override
    protected boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(this, message, "Submit claim",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    // ------------------------------------------------------------------ the right side

    /** Fills the policy list. With no policy, the whole form is locked. */
    private void reloadPolicies() {
        PolicyItem current = (PolicyItem) policyBox.getSelectedItem();
        policyBox.removeAllItems();
        for (Policy p : service.policiesOf(applicant)) {
            policyBox.addItem(new PolicyItem(p, service.today()));
            if (current != null && current.policy.getId().equals(p.getId())) {
                policyBox.setSelectedIndex(policyBox.getItemCount() - 1);
            }
        }
        boolean any = policyBox.getItemCount() > 0;
        policyBox.setEnabled(any);
        incidentField.setEnabled(any);
        amountField.setEnabled(any);
        descriptionArea.setEnabled(any);
        incidentField.setBackground(any ? Theme.WHITE : Theme.BACKGROUND);
        amountField.setBackground(any ? Theme.WHITE : Theme.BACKGROUND);
        descriptionArea.setBackground(any ? Theme.WHITE : Theme.BACKGROUND);
        submitButton.setEnabled(any);
        clearButton.setEnabled(any);
    }

    /** Updates the terms, the estimate and the automatic-check box while the User types. */
    private void refreshPreview() {
        PolicyItem item = (PolicyItem) policyBox.getSelectedItem();
        if (item == null) {
            boolean none = policyBox.getItemCount() == 0;
            termsLabel.setText(none ? Theme.wrap("No policies have been issued to you yet. "
                    + "An Admin issues them on the Policies screen.", 380) : "No policy chosen.");
            estimateLabel.setText(none ? "Available once you have a policy" : "Choose a policy first");
            showCheck(Theme.BACKGROUND, Theme.TEXT_MUTED, none ? "Checks run once you have a policy." : "Choose a policy first.");
            return;
        }
        Policy p = item.policy;
        amountLabel.setText("<html>" + (p instanceof AutoPolicy ? "Repair estimate" : "Medical bill")
                + " <font color='#C0392B'>*</font></html>");

        LocalDate incident = null;
        try {
            incident = LocalDate.parse(incidentField.getText().trim());
        } catch (DateTimeParseException ignored) {
            // shown below as "type the incident date"
        }

        StringBuilder terms = new StringBuilder("<table cellspacing='0' cellpadding='2'>");
        Theme.tableRow(terms, "Covers", p.getStartDate() + " to " + p.getEndDate());
        Theme.tableRow(terms, "Limit left", Theme.money(p.getRemainingLimit()));
        if (p instanceof HealthPolicy) {
            HealthPolicy h = (HealthPolicy) p;
            Theme.tableRow(terms, "Deductible left", Theme.money(h.getRemainingDeductible()));
            Theme.tableRow(terms, "Insurer pays", Math.round(h.getReimbursementRate() * 100) + "% after the deductible");
            Theme.tableRow(terms, "Waiting period", h.getWaitingPeriodDays() == 0 ? "none" : "until " + h.getWaitingPeriodEnd());
        } else {
            Theme.tableRow(terms, "Deductible", Theme.money(p.getDeductible()) + " per claim");
        }
        if (incident != null) {
            Theme.tableRow(terms, "File by", incident.plusDays(ClaimService.FILING_WINDOW_DAYS)
                    + " (" + ClaimService.FILING_WINDOW_DAYS + " days after the incident)");
        }
        termsLabel.setText(Theme.wrapHtml(terms.append("</table>").toString(), 400));

        try {
            estimateLabel.setText("You would receive about "
                    + Theme.money(service.estimate(applicant, p.getId(), amountField.getText())));
        } catch (ICSException e) {
            estimateLabel.setText("Type the amount to see an estimate");
        }

        if (incident == null) {
            showCheck(Theme.BACKGROUND, Theme.TEXT_MUTED, "Type the incident date as year-month-day to run the checks.");
            return;
        }
        try {
            Claim.Reason broken = service.precheck(applicant, p.getId(), incidentField.getText());
            if (broken == null) {
                showCheck(Theme.GREEN_FILL, Theme.SUCCESS, "No problems found with this policy and date.");
            } else {
                showCheck(Theme.AMBER_FILL, Theme.AMBER, "This claim would be rejected: " + broken.getLabel() + ".");
            }
        } catch (ICSException e) {
            showCheck(Theme.BACKGROUND, Theme.TEXT_MUTED, e.getMessage());
        }
    }

    private void showCheck(java.awt.Color fill, java.awt.Color text, String message) {
        warningLabel.setBackground(fill);
        warningLabel.setForeground(text);
        warningLabel.setText(Theme.wrap(message, 380));
    }

    private void updateCounter() {
        int length = descriptionArea.getText().trim().length();
        counterLabel.setText(length + " / " + MAX_DESCRIPTION);
        counterLabel.setForeground(length > MAX_DESCRIPTION ? Theme.ERROR : Theme.TEXT_MUTED);
    }

    private void clearForm() {
        incidentField.setText(service.today().toString());
        amountField.setText("");
        descriptionArea.setText("");
        refreshPreview();
        incidentField.requestFocusInWindow();
    }
}
