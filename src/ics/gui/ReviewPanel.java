package ics.gui;

import ics.exception.*;
import ics.model.*;
import ics.service.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

@SuppressWarnings({"serial", "this-escape"})
public class ReviewPanel extends BasePanel {
    private static final Claim.Reason[] ADMIN_REASONS = {
        Claim.Reason.EXCLUDED_CAUSE, Claim.Reason.INSUFFICIENT_DOCUMENTS, Claim.Reason.SUSPECTED_FRAUD,
        Claim.Reason.POLICY_INACTIVE, Claim.Reason.OUTSIDE_COVERAGE_PERIOD, Claim.Reason.WAITING_PERIOD,
        Claim.Reason.LATE_FILING, Claim.Reason.DUPLICATE_CLAIM, Claim.Reason.LIMIT_EXHAUSTED
    };

    private final ClaimService service;
    private final User admin;
    private List<Claim> shown = new ArrayList<>();
    private String selectedId;

    private final JComboBox<ClaimService.Filter> filterBox = new JComboBox<>(ClaimService.Filter.values());
    private final JComboBox<ClaimService.SortBy> sortBox = new JComboBox<>(ClaimService.SortBy.values());
    private final Widgets.RoundedButton nextButton = button("Next in line", KeyEvent.VK_N, true,
            "Open the claim at the front of the review queue: first filed, first decided (Alt+N)");
    private final JTextField searchField = Theme.styleField(new JTextField());
    private final JLabel countLabel = Theme.label("", Theme.FONT_CAPTION, Theme.TEXT_MUTED);
    private final Widgets.Table table = new Widgets.Table(
            new String[] {"ID", "Holder", "Claimed", "Status", "Filed"}, new int[] {80, 80, 110, 105, 95});

    private final CardLayout detailCards = new CardLayout();
    private final JPanel detailHolder = new JPanel(detailCards);
    private final JLabel titleLabel = Theme.label(" ", Theme.FONT_HEADING, Theme.TEXT_DARK);
    private final JLabel chip = Widgets.chip();
    private final JLabel factsLabel = Theme.label(" ", Theme.FONT_BODY, Theme.TEXT_DARK);
    private final JTextArea descriptionArea = new JTextArea(3, 20);
    private final JLabel replyHeading = Theme.fieldLabel("Reply from the user");
    private final JLabel replyLabel = Theme.label(" ", Theme.FONT_BODY, Theme.NAVY);
    private final JLabel payoutHeading = Theme.fieldLabel("How the payout would be worked out");
    private final JLabel payoutLabel = Theme.label(" ", Theme.FONT_CAPTION, Theme.TEXT_DARK);
    private final JPanel decisionPanel = new JPanel();
    private final JComboBox<Claim.Reason> reasonBox = new JComboBox<>(ADMIN_REASONS);
    private final JTextArea noteArea = new JTextArea(3, 20);
    private final JLabel savedNoteHeading = Theme.fieldLabel("Note to the user");
    private final JLabel savedNoteLabel = Theme.label(" ", Theme.FONT_BODY, Theme.TEXT_DARK);

    private final JPanel buttonRow = new JPanel(new GridLayout(1, 0, 8, 0));
    private final Widgets.RoundedButton approveButton = button("Approve", KeyEvent.VK_A, true,
            "Approve and release the payout shown above (Alt+A). Asks for your password.");
    private final Widgets.RoundedButton denyButton = button("Deny", KeyEvent.VK_D, false, "Deny with the reason chosen above (Alt+D)");
    private final Widgets.RoundedButton infoButton = button("Ask for info", KeyEvent.VK_I, false, "Send your note to the user and wait for their reply (Alt+I)");
    private final Widgets.RoundedButton resumeButton = button("Resume review", KeyEvent.VK_R, true, "Continue without waiting for the user's reply (Alt+R)");
    private final Widgets.RoundedButton slipButton = button("View slip", KeyEvent.VK_V, false, "Show and print the payout slip (Alt+V)");
    private final Widgets.RoundedButton closeButton = button("Close claim", KeyEvent.VK_C, false, "Finish this claim (Alt+C)");

    public ReviewPanel(ClaimService service, User admin) {
        super("Review Claims", "Admin: decide on the claims users applied for");
        this.service = service;
        this.admin = admin;
        buildList();
        buildDetail();
        wireActions();
    }

    private static Widgets.RoundedButton button(String text, int key, boolean primary, String tip) {
        Widgets.RoundedButton b = new Widgets.RoundedButton(text,
                primary ? Widgets.RoundedButton.Kind.PRIMARY : Widgets.RoundedButton.Kind.SECONDARY);
        b.setMnemonic(key);
        b.setPreferredSize(new Dimension(0, 36));
        Theme.tip(b, tip);
        return b;
    }

    private void buildList() {
        Theme.styleCombo(filterBox);
        Theme.tip(filterBox, "Which claims to show. 'Needs action' = waiting for you to decide.");
        Theme.tip(searchField, "Type part of a claim ID, policy, holder or description. A full claim ID (CLM-0005) uses binary search.");
        Theme.styleCombo(sortBox);
        Theme.tip(sortBox, "How to order the table (insertion sort)");
        table.colourColumn(3, (row, text) -> row < shown.size() ? Widgets.colorFor(shown.get(row).getStatus()) : Theme.TEXT_DARK);

        JPanel top = new JPanel(new BorderLayout(10, 0));
        top.setOpaque(false);
        filterBox.setPreferredSize(new Dimension(170, 32));
        sortBox.setPreferredSize(new Dimension(150, 32));
        nextButton.setPreferredSize(new Dimension(120, 32));
        JPanel choices = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        choices.setOpaque(false);
        choices.add(filterBox);
        choices.add(Box.createHorizontalStrut(8));
        choices.add(sortBox);
        JPanel searchRow = new JPanel(new BorderLayout(10, 0));
        searchRow.setOpaque(false);
        searchRow.add(choices, BorderLayout.WEST);
        searchRow.add(searchField, BorderLayout.CENTER);
        JPanel queueRow = new JPanel(new BorderLayout(10, 0));
        queueRow.setOpaque(false);
        queueRow.add(countLabel, BorderLayout.CENTER);
        queueRow.add(nextButton, BorderLayout.EAST);
        top.setLayout(new BorderLayout(0, 10));
        top.add(searchRow, BorderLayout.NORTH);
        top.add(queueRow, BorderLayout.SOUTH);

        JPanel card = Theme.card(20);
        card.setLayout(new BorderLayout(0, 14));
        card.add(top, BorderLayout.NORTH);
        card.add(Widgets.scroll(table), BorderLayout.CENTER);
        getBody().setLayout(new BorderLayout(16, 0));
        getBody().add(card, BorderLayout.CENTER);
    }

    private void buildDetail() {
        JLabel empty = Theme.label("Choose a claim on the left.", Theme.FONT_BODY, Theme.TEXT_MUTED);
        empty.setHorizontalAlignment(JLabel.CENTER);
        factsLabel.setVerticalAlignment(JLabel.TOP);

        descriptionArea.setEditable(false);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setFont(Theme.FONT_BODY);
        descriptionArea.setBackground(Theme.BACKGROUND);
        descriptionArea.setBorder(new EmptyBorder(6, 8, 6, 8));
        JScrollPane descriptionScroll = new JScrollPane(descriptionArea);
        descriptionScroll.setBorder(new LineBorder(Theme.BORDER, 1, true));
        descriptionScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));

        Widgets.showWords(reasonBox, reason -> reason.getLabel());
        Theme.styleCombo(reasonBox);
        Theme.tip(reasonBox, "Required if you deny. The user will see it.");
        noteArea.setLineWrap(true);
        noteArea.setWrapStyleWord(true);
        noteArea.setFont(Theme.FONT_BODY);
        noteArea.setBorder(new EmptyBorder(6, 8, 6, 8));
        Theme.tip(noteArea, "The user can read this (up to " + Claim.MAX_NOTE_LENGTH
                + " characters). Required when you ask for info.");
        JScrollPane noteScroll = new JScrollPane(noteArea);
        noteScroll.setBorder(new LineBorder(Theme.BORDER, 1, true));
        noteScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        decisionPanel.setOpaque(false);
        decisionPanel.setLayout(new BoxLayout(decisionPanel, BoxLayout.Y_AXIS));
        decisionPanel.add(left(Theme.fieldLabel("Reason if you deny")));
        decisionPanel.add(Box.createVerticalStrut(4));
        reasonBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        decisionPanel.add(left(reasonBox));
        decisionPanel.add(Box.createVerticalStrut(10));
        decisionPanel.add(left(Theme.fieldLabel("Note to the user (they can read it)")));
        decisionPanel.add(Box.createVerticalStrut(4));
        decisionPanel.add(left(noteScroll));

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.add(left(factsLabel));
        stack.add(Box.createVerticalStrut(10));
        stack.add(left(Theme.fieldLabel("Description")));
        stack.add(Box.createVerticalStrut(4));
        stack.add(left(descriptionScroll));
        stack.add(Box.createVerticalStrut(8));
        stack.add(left(replyHeading));
        stack.add(Box.createVerticalStrut(4));
        stack.add(left(replyLabel));
        stack.add(Box.createVerticalStrut(10));
        stack.add(left(payoutHeading));
        stack.add(Box.createVerticalStrut(4));
        stack.add(left(payoutLabel));
        stack.add(Box.createVerticalStrut(10));
        stack.add(left(savedNoteHeading));
        stack.add(Box.createVerticalStrut(4));
        stack.add(left(savedNoteLabel));
        stack.add(left(decisionPanel));

        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        header.add(titleLabel, BorderLayout.WEST);
        JPanel chipHolder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 6));
        chipHolder.setOpaque(false);
        chipHolder.add(chip);
        header.add(chipHolder, BorderLayout.CENTER);

        buttonRow.setOpaque(false);
        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.setOpaque(false);
        south.add(buttonRow, BorderLayout.NORTH);
        south.add(statusLabel, BorderLayout.SOUTH);

        JPanel detail = new JPanel(new BorderLayout(0, 12));
        detail.setOpaque(false);
        detail.add(header, BorderLayout.NORTH);
        detail.add(Widgets.scrollDown(stack), BorderLayout.CENTER);
        detail.add(south, BorderLayout.SOUTH);

        detailHolder.setOpaque(false);
        detailHolder.add(empty, "empty");
        detailHolder.add(detail, "detail");
        JPanel card = Theme.card(22);
        card.setLayout(new BorderLayout());
        card.setPreferredSize(new Dimension(420, 0));
        card.add(detailHolder, BorderLayout.CENTER);
        getBody().add(card, BorderLayout.EAST);
    }

    private static JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private void wireActions() {
        filterBox.addActionListener(e -> refreshList());
        sortBox.addActionListener(e -> refreshList());
        nextButton.addActionListener(e -> openNextInLine());
        Widgets.onTextChange(searchField, () -> refreshList());
        table.onRowSelected(() -> {
            selectedId = shown.get(table.getSelectedRow()).getId();
            setStatus(" ", true);
            loadDetail();
        });
        approveButton.addActionListener(e -> approve());
        denyButton.addActionListener(e -> deny());
        infoButton.addActionListener(e -> askForInfo());
        resumeButton.addActionListener(e -> step("Back under review: " + selectedId + ".",
                () -> service.resumeReview(selectedId, admin)));
        slipButton.addActionListener(e -> openSlip(selectedId));
        closeButton.addActionListener(e -> {
            if (confirm("Close " + selectedId + "?\nA closed claim is finished and cannot be reopened.")) {
                step("Closed " + selectedId + ".", () -> service.close(selectedId, admin));
            } else {
                setStatus("Nothing was changed.", true);
            }
        });
    }

    @Override
    public void onShow() {
        refreshList();
        if (selectedId != null) {
            loadDetail();
        }
    }

    private void openNextInLine() {
        Claim next = service.nextInLine();
        if (next == null) {
            setStatus("The queue is empty: no claim is waiting for a decision.", true);
            return;
        }
        selectedId = next.getId();
        searchField.setText("");
        filterBox.setSelectedItem(ClaimService.Filter.NEEDS_ACTION);
        sortBox.setSelectedItem(ClaimService.SortBy.QUEUE);
        refreshList();
        loadDetail();
        setStatus("Front of the queue: " + next.getId() + " (filed " + next.getFiledDate() + ").", true);
    }

    private void approve() {
        String id = selectedId;
        try {
            service.checkTurnToApprove(id);
            Claim claim = service.getClaim(id);
            Policy policy = service.policyOf(claim);
            double amount = policy.calculatePayout(claim.getClaimedAmount());
            String formula = policy.payoutFormula(claim.getClaimedAmount(), Theme.currencySign());
            if (!confirm("Approve " + id + " for " + Theme.money(amount) + "?\n\n" + formula
                    + "\n\nThe money is released at once. This cannot be undone.")) {
                setStatus("Nothing was changed.", true);
                return;
            }
        } catch (ICSException e) {
            setStatus(e.getMessage(), false);
            return;
        }
        Outcome outcome = runProtected("approve " + id + " and release the payment",
                password -> service.approve(id, admin, noteArea.getText(), password));
        if (outcome == Outcome.DONE) {
            afterChange();
            try {
                Claim claim = service.getClaim(id);
                setStatus("Approved " + id + ": " + Theme.money(claim.getPaidAmount()) + " released. The policy has "
                        + Theme.money(service.policyOf(claim).getRemainingLimit()) + " left.", true);
            } catch (ICSException e) {
                setStatus(e.getMessage(), false);
            }
            openSlip(id);
        } else {
            setStatus(getProtectedMessage(), outcome == Outcome.CANCELLED);
        }
    }

    private void deny() {
        String id = selectedId;
        Claim.Reason reason = (Claim.Reason) reasonBox.getSelectedItem();
        if (!confirm("Deny " + id + " because: " + reason.getLabel()
                + "?\nThe user will see this reason and your note. This cannot be undone.")) {
            setStatus("Nothing was changed.", true);
            return;
        }
        step("Denied " + id + ". The user will see the reason and your note.",
                () -> service.deny(id, admin, reason, noteArea.getText()));
    }

    private void askForInfo() {
        String id = selectedId;
        step("Asked the user for more information on " + id + ". They will see your note.",
                () -> service.requestInfo(id, admin, noteArea.getText()));
    }

    private interface Step {
        void run() throws ICSException;
    }

    private void step(String doneMessage, Step action) {
        try {
            action.run();
            afterChange();
            setStatus(doneMessage, true);
        } catch (InvalidInputException e) {
            setStatus(e.getMessage(), false);
            noteArea.requestFocusInWindow();
        } catch (ICSException e) {
            setStatus(e.getMessage(), false);
        }
    }

    private void openSlip(String id) {
        try {
            showSlip(id, service.payoutSlip(id));
        } catch (ICSException e) {
            setStatus(e.getMessage(), false);
        }
    }

    protected void showSlip(String claimId, List<String> lines) {
        Dialogs.showSlip(this, "Payout slip - " + claimId, lines);
    }

    private void afterChange() {
        refreshList();
        loadDetail();
    }

    private void refreshList() {
        shown = service.search(searchField.getText(), (ClaimService.Filter) filterBox.getSelectedItem(),
                (ClaimService.SortBy) sortBox.getSelectedItem());
        List<Object[]> rows = new ArrayList<>();
        for (Claim c : shown) {
            rows.add(new Object[] {c.getId(), service.holderName(c), Theme.money(c.getClaimedAmount()),
                    c.getStatus().getLabel(), c.getFiledDate().toString()});
        }
        table.setRows(rows, selectedId);
        String count = shown.size() + (shown.size() == 1 ? " claim" : " claims");
        int steps = service.getLastSearchSteps();
        if (steps > 0) {
            count += " (binary search: " + steps + (steps == 1 ? " check)" : " checks)");
        }
        countLabel.setText(count + "  |  " + service.reviewQueue().size() + " in queue");
        if (selectedId == null) {
            detailCards.show(detailHolder, "empty");
        }
    }

    private void loadDetail() {
        Claim c;
        Policy p;
        try {
            c = service.getClaim(selectedId);
            p = service.policyOf(c);
        } catch (ICSException e) {
            setStatus(e.getMessage(), false);
            return;
        }
        Claim.Status status = c.getStatus();
        titleLabel.setText(c.getId());
        chip.setText(status.getLabel());
        chip.setBackground(Widgets.colorFor(status));

        User holder = service.holderOf(c);
        StringBuilder facts = new StringBuilder("<table cellspacing='0' cellpadding='2'>");
        Theme.tableRow(facts, "Holder", holder == null ? "(removed)" : holder.getFullName() + " (" + holder.getUsername() + ")");
        Theme.tableRow(facts, "Policy", p.getId() + " - " + p.getType().getLabel());
        Theme.tableRow(facts, "Incident date", c.getIncidentDate().toString());
        Theme.tableRow(facts, "Filed", c.getFiledDate().toString());
        int place = service.placeInLine(c.getId());
        if (place > 0) {
            Theme.tableRow(facts, "Place in queue", place + " of " + service.reviewQueue().size());
        }
        Theme.tableRow(facts, "Claimed", Theme.money(c.getClaimedAmount()));
        Theme.tableRow(facts, "Reserve", Theme.money(c.getReserveAmount()));
        if (c.getApprovedAmount() > 0) {
            Theme.tableRow(facts, "Approved", Theme.money(c.getApprovedAmount()));
        }
        if (c.getPaidAmount() > 0) {
            Theme.tableRow(facts, "Paid", Theme.money(c.getPaidAmount()));
        }
        if (c.getReason() != null) {
            Theme.tableRow(facts, "Reason", c.getReason().getLabel());
        }
        if (c.getHandledBy() != null) {
            Theme.tableRow(facts, "Decided by", service.userName(c.getHandledBy()));
        }
        factsLabel.setText(Theme.wrapHtml(facts.append("</table>").toString(), 340));
        descriptionArea.setText(c.getDescription());

        boolean hasReply = !c.getApplicantNote().isBlank();
        replyHeading.setVisible(hasReply);
        replyLabel.setVisible(hasReply);
        replyLabel.setText(Theme.wrap(c.getApplicantNote(), 340));

        Payout payout = service.payoutOf(c);
        List<String> steps = new ArrayList<>();
        if (payout != null) {
            payoutHeading.setText("How the payout was worked out");
            for (String line : payout.getBreakdown().split("\n")) {
                steps.add(line);
            }
        } else if (status == Claim.Status.UNDER_REVIEW || status == Claim.Status.INFO_REQUESTED) {
            payoutHeading.setText("How the payout would be worked out");
            steps = p.explainPayout(c.getClaimedAmount());
        }
        payoutHeading.setVisible(!steps.isEmpty());
        payoutLabel.setVisible(!steps.isEmpty());
        if (payout == null && !steps.isEmpty()) {
            String formula = p.payoutFormula(c.getClaimedAmount(), Theme.currencySign());
            payoutLabel.setText(Theme.wrapHtml("<b>" + Theme.escape(formula) + "</b><br><br>"
                    + Theme.escape(String.join("\n", steps)), 340));
        } else {
            payoutLabel.setText(Theme.wrap(String.join("\n", steps), 340));
        }

        boolean underReview = status == Claim.Status.UNDER_REVIEW;
        decisionPanel.setVisible(underReview);
        noteArea.setText(c.getReviewerNote());
        boolean showSavedNote = !underReview && !c.getReviewerNote().isBlank();
        savedNoteHeading.setVisible(showSavedNote);
        savedNoteLabel.setVisible(showSavedNote);
        savedNoteLabel.setText(Theme.wrap(c.getReviewerNote(), 340));

        buttonRow.removeAll();
        if (underReview) {
            addButtons(approveButton, denyButton, infoButton);
        } else if (status == Claim.Status.INFO_REQUESTED) {
            addButtons(resumeButton);
        } else if (status == Claim.Status.APPROVED || status == Claim.Status.PAID) {
            if (payout != null) {
                addButtons(slipButton, closeButton);
            } else {
                addButtons(closeButton);
            }
        } else if (status == Claim.Status.DENIED) {
            addButtons(closeButton);
        } else if (status == Claim.Status.CLOSED && payout != null) {
            addButtons(slipButton);
        }
        buttonRow.revalidate();
        buttonRow.repaint();
        detailCards.show(detailHolder, "detail");
    }

    private void addButtons(JButton... buttons) {
        for (JButton b : buttons) {
            buttonRow.add(b);
        }
    }
}
