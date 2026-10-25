package ics.gui;

import ics.exception.ICSException;
import ics.model.AutoPolicy;
import ics.model.Claim;
import ics.model.HealthPolicy;
import ics.model.Policy;
import ics.model.User;
import ics.service.ClaimService;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User screen: the policies issued to you. Read-only, because only an Admin changes policies.
 *
 *   left   your policies, active ones first
 *   right  the terms, how much of the limit is used, the claims made on it, and "Apply for a claim"
 */
@SuppressWarnings({"serial", "this-escape"})
public class MyPoliciesPanel extends BasePanel {

    /** What "Apply for a claim" does: ShellPanel opens Apply for Claim with this policy chosen. */
    public interface ApplyAction {
        void applyOn(String policyId);
    }

    private final ClaimService service;
    private final User applicant;
    private final ApplyAction onApply;
    private List<Policy> shown = new ArrayList<>();
    private String selectedId;

    private final Widgets.Table table = new Widgets.Table(
            new String[] {"ID", "Type", "Status", "Ends", "Remaining"}, new int[] {85, 70, 95, 100, 130});
    private final JLabel countLabel = Theme.label("", Theme.FONT_CAPTION, Theme.TEXT_MUTED);
    private final CardLayout detailCards = new CardLayout();
    private final JPanel detailHolder = new JPanel(detailCards);
    private final JLabel titleLabel = Theme.label(" ", Theme.FONT_HEADING, Theme.TEXT_DARK);
    private final JLabel chip = Widgets.chip();
    private final JLabel termsLabel = Theme.label(" ", Theme.FONT_BODY, Theme.TEXT_DARK);
    private final Widgets.UsageBar usageBar = new Widgets.UsageBar();
    private final JLabel usageLabel = Theme.label(" ", Theme.FONT_CAPTION, Theme.TEXT_MUTED);
    private final JLabel claimsLabel = Theme.label(" ", Theme.FONT_BODY, Theme.TEXT_DARK);
    private final Widgets.RoundedButton applyButton = new Widgets.RoundedButton("Apply for a claim", Widgets.RoundedButton.Kind.PRIMARY);

    public MyPoliciesPanel(ClaimService service, User applicant, ApplyAction onApply) {
        super("My Policies", "The policies issued to you");
        this.service = service;
        this.applicant = applicant;
        this.onApply = onApply;
        buildUi();
        table.onRowSelected(() -> {
            selectedId = shown.get(table.getSelectedRow()).getId();
            showDetail(shown.get(table.getSelectedRow()));
        });
        applyButton.addActionListener(e -> onApply.applyOn(selectedId));
    }

    private void buildUi() {
        table.colourColumn(2, (row, text) -> Widgets.colorForPolicy(text));
        JPanel listHeader = new JPanel(new BorderLayout());
        listHeader.setOpaque(false);
        listHeader.add(Theme.label("Your policies", Theme.FONT_HEADING, Theme.TEXT_DARK), BorderLayout.WEST);
        listHeader.add(countLabel, BorderLayout.EAST);
        JPanel listCard = Theme.card(20);
        listCard.setLayout(new BorderLayout(0, 14));
        listCard.add(listHeader, BorderLayout.NORTH);
        listCard.add(Widgets.scroll(table), BorderLayout.CENTER);

        JLabel empty = Theme.label("<html><center>No policies have been issued to you yet.<br>"
                + "An Admin issues them on the Policies screen.</center></html>", Theme.FONT_BODY, Theme.TEXT_MUTED);
        empty.setHorizontalAlignment(JLabel.CENTER);
        termsLabel.setVerticalAlignment(JLabel.TOP);

        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        header.add(titleLabel, BorderLayout.WEST);
        JPanel chipHolder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 6));
        chipHolder.setOpaque(false);
        chipHolder.add(chip);
        header.add(chipHolder, BorderLayout.CENTER);

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.add(left(termsLabel));
        stack.add(Box.createVerticalStrut(16));
        stack.add(left(Theme.fieldLabel("Coverage used")));
        stack.add(Box.createVerticalStrut(6));
        stack.add(left(usageBar));
        stack.add(Box.createVerticalStrut(4));
        stack.add(left(usageLabel));
        stack.add(Box.createVerticalStrut(16));
        stack.add(left(Theme.fieldLabel("Claims on this policy")));
        stack.add(Box.createVerticalStrut(4));
        stack.add(left(claimsLabel));

        applyButton.setMnemonic(KeyEvent.VK_A);
        applyButton.setPreferredSize(new Dimension(0, 40));
        JPanel detail = new JPanel(new BorderLayout(0, 12));
        detail.setOpaque(false);
        detail.add(header, BorderLayout.NORTH);
        detail.add(Widgets.scrollDown(stack), BorderLayout.CENTER);
        detail.add(applyButton, BorderLayout.SOUTH);

        detailHolder.setOpaque(false);
        detailHolder.add(empty, "empty");
        detailHolder.add(detail, "detail");
        JPanel detailCard = Theme.card(22);
        detailCard.setLayout(new BorderLayout());
        detailCard.setPreferredSize(new Dimension(420, 0));
        detailCard.add(detailHolder, BorderLayout.CENTER);

        getBody().setLayout(new BorderLayout(16, 0));
        getBody().add(listCard, BorderLayout.CENTER);
        getBody().add(detailCard, BorderLayout.EAST);
    }

    private static JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    @Override
    public void onShow() {
        shown = service.policiesOf(applicant);
        List<Object[]> rows = new ArrayList<>();
        for (Policy p : shown) {
            rows.add(new Object[] {p.getId(), p.getType().getLabel(), p.getStatus(service.today()).getLabel(),
                    p.getEndDate().toString(), Theme.money(p.getRemainingLimit())});
        }
        table.setRows(rows, selectedId);
        countLabel.setText(shown.size() + (shown.size() == 1 ? " policy" : " policies"));
        if (shown.isEmpty()) {
            selectedId = null;
            detailCards.show(detailHolder, "empty");
            return;
        }
        int row = Math.max(table.getSelectedRow(), 0);        // keep the selection, or start with the first
        table.selectQuietly(shown.get(row).getId());
        selectedId = shown.get(row).getId();
        showDetail(shown.get(row));
    }

    private void showDetail(Policy p) {
        Policy.Status status = p.getStatus(service.today());
        titleLabel.setText(p.getId() + " - " + p.getType().getLabel());
        chip.setText(status.getLabel());
        chip.setBackground(Widgets.colorForPolicy(status.getLabel()));

        StringBuilder html = new StringBuilder("<table cellspacing='0' cellpadding='2'>");
        Theme.tableRow(html, "Covers", p.getStartDate() + " to " + p.getEndDate());
        Theme.tableRow(html, "Premium", Theme.money(p.calculatePremium()));
        Theme.tableRow(html, "Coverage limit", Theme.money(p.getCoverageLimit()));
        if (p instanceof HealthPolicy) {
            HealthPolicy h = (HealthPolicy) p;
            Theme.tableRow(html, "Deductible", Theme.money(h.getDeductible()) + " for the whole policy period");
            Theme.tableRow(html, "Deductible met", Theme.money(h.getDeductibleMet()) + " (" + Theme.money(h.getRemainingDeductible()) + " to go)");
            Theme.tableRow(html, "Insurer pays", Math.round(h.getReimbursementRate() * 100) + "% after the deductible");
            Theme.tableRow(html, "Waiting period", h.getWaitingPeriodDays() == 0 ? "none" : "until " + h.getWaitingPeriodEnd());
        } else if (p instanceof AutoPolicy) {
            AutoPolicy a = (AutoPolicy) p;
            Theme.tableRow(html, "Deductible", Theme.money(a.getDeductible()) + " per claim");
            Theme.tableRow(html, "Vehicle value", Theme.money(a.getVehicleValue()));
            Theme.tableRow(html, "Plate number", a.getPlateNo());
        }
        termsLabel.setText(Theme.wrapHtml(html.append("</table>").toString(), 360));

        usageBar.setValues(p.getLimitUsed(), p.getCoverageLimit());
        long percent = Math.round(100.0 * p.getLimitUsed() / p.getCoverageLimit());
        usageLabel.setText(Theme.wrap(Theme.money(p.getLimitUsed()) + " paid out of " + Theme.money(p.getCoverageLimit())
                + " (" + percent + "%). " + Theme.money(p.getRemainingLimit()) + " left.", 360));
        claimsLabel.setText(claimsSummary(p));

        boolean canApply = status != Policy.Status.CANCELLED && status != Policy.Status.UPCOMING;
        applyButton.setEnabled(canApply);
        if (status == Policy.Status.UPCOMING) {
            Theme.tip(applyButton, "This policy starts on " + p.getStartDate() + ". You can apply once it is active.");
        } else if (status == Policy.Status.CANCELLED) {
            Theme.tip(applyButton, "This policy was cancelled, so it cannot take new claims");
        } else {
            Theme.tip(applyButton, "Open Apply for Claim with this policy already chosen (Alt+A)");
        }
        detailCards.show(detailHolder, "detail");
    }

    /** For example "3 claims: 1 under review, 2 paid. Total paid to you: ...". */
    private String claimsSummary(Policy p) {
        List<Claim> claims;
        try {
            claims = service.claimsOnPolicy(applicant, p.getId());
        } catch (ICSException e) {
            return e.getMessage();
        }
        if (claims.isEmpty()) {
            return "No claims yet.";
        }
        Map<String, Integer> counts = new LinkedHashMap<>();      // status word -> how many
        double paid = 0;
        for (Claim c : claims) {
            String word = c.getStatus().getLabel().toLowerCase();
            counts.put(word, counts.getOrDefault(word, 0) + 1);
            paid += c.getPaidAmount();
        }
        StringBuilder parts = new StringBuilder();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (parts.length() > 0) {
                parts.append(", ");
            }
            parts.append(e.getValue()).append(" ").append(e.getKey());
        }
        return Theme.wrapHtml(claims.size() + (claims.size() == 1 ? " claim: " : " claims: ") + parts
                + ".<br>Total paid to you: <b>" + Theme.money(paid) + "</b>", 360);
    }
}
