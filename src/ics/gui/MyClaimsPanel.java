package ics.gui;

import ics.exception.ICSException;
import ics.exception.InvalidInputException;
import ics.model.Claim;
import ics.model.Policy;
import ics.model.User;
import ics.service.ClaimService;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * User screen: follow your claims and answer the Admin.
 *
 *   left   your claims; the ones waiting for your reply come first
 *   right  where the claim is (progress dots), what that means in plain words, the Admin's note,
 *          a reply box when the Admin asked for more information, and the payout slip
 *
 * The Admin's note and the User's reply are the two-way communication between the two kinds of user (rubric II.C.7).
 */
@SuppressWarnings({"serial", "this-escape"})
public class MyClaimsPanel extends BasePanel {

    private final ClaimService service;
    private final User applicant;
    private List<Claim> shown = new ArrayList<>();
    private String selectedId;

    private final Widgets.Table table = new Widgets.Table(
            new String[] {"ID", "Claimed", "Status", "Filed"}, new int[] {82, 115, 135, 100});
    private final JLabel countLabel = Theme.label("", Theme.FONT_CAPTION, Theme.TEXT_MUTED);

    private final CardLayout detailCards = new CardLayout();
    private final JPanel detailHolder = new JPanel(detailCards);
    private final JLabel titleLabel = Theme.label(" ", Theme.FONT_HEADING, Theme.TEXT_DARK);
    private final JLabel chip = Widgets.chip();
    private final Widgets.ClaimProgress progress = new Widgets.ClaimProgress();
    private final JLabel summaryLabel = Theme.label(" ", Theme.FONT_LABEL, Theme.TEXT_DARK);
    private final JLabel factsLabel = Theme.label(" ", Theme.FONT_BODY, Theme.TEXT_DARK);
    private final JLabel adminNoteHeading = Theme.fieldLabel("Note from the Admin");
    private final JLabel adminNoteLabel = Theme.label(" ", Theme.FONT_BODY, Theme.TEXT_DARK);
    private final JLabel replyHeading = Theme.fieldLabel("Your reply");
    private final JLabel sentReplyLabel = Theme.label(" ", Theme.FONT_BODY, Theme.TEXT_DARK);
    private final JTextArea replyArea = new JTextArea(3, 20);
    private final JScrollPane replyScroll = new JScrollPane(replyArea);
    private final JPanel buttonRow = new JPanel(new GridLayout(1, 0, 8, 0));
    private final Widgets.RoundedButton replyButton = new Widgets.RoundedButton("Send reply", Widgets.RoundedButton.Kind.PRIMARY);
    private final Widgets.RoundedButton slipButton = new Widgets.RoundedButton("View payout slip", Widgets.RoundedButton.Kind.PRIMARY);

    public MyClaimsPanel(ClaimService service, User applicant) {
        super("My Claims", "Follow your claims and answer the Admin");
        this.service = service;
        this.applicant = applicant;
        buildUi();
        table.onRowSelected(() -> {
            selectedId = shown.get(table.getSelectedRow()).getId();
            setStatus(" ", true);
            loadDetail();
        });
        replyButton.addActionListener(e -> sendReply());
        slipButton.addActionListener(e -> {
            try {
                showSlip(selectedId, service.payoutSlipFor(applicant, selectedId));
            } catch (ICSException ex) {
                setStatus(ex.getMessage(), false);
            }
        });
    }

    /** Plain words for the User: "Info requested" means the next step is theirs. */
    private static String userLabel(Claim.Status status) {
        return status == Claim.Status.INFO_REQUESTED ? "Needs your reply" : status.getLabel();
    }

    // ------------------------------------------------------------------ layout

    private void buildUi() {
        table.colourColumn(2, (row, text) -> row < shown.size() ? Widgets.colorFor(shown.get(row).getStatus()) : Theme.TEXT_DARK);
        JPanel listHeader = new JPanel(new BorderLayout());
        listHeader.setOpaque(false);
        listHeader.add(Theme.label("Your claims", Theme.FONT_HEADING, Theme.TEXT_DARK), BorderLayout.WEST);
        listHeader.add(countLabel, BorderLayout.EAST);
        JPanel listCard = Theme.card(20);
        listCard.setLayout(new BorderLayout(0, 14));
        listCard.add(listHeader, BorderLayout.NORTH);
        listCard.add(Widgets.scroll(table), BorderLayout.CENTER);

        JLabel empty = Theme.label("<html><center>You have not applied for any claims yet.<br>"
                + "Use Apply for Claim to register one.</center></html>", Theme.FONT_BODY, Theme.TEXT_MUTED);
        empty.setHorizontalAlignment(JLabel.CENTER);
        summaryLabel.setOpaque(true);
        summaryLabel.setBorder(new EmptyBorder(10, 12, 10, 12));
        factsLabel.setVerticalAlignment(JLabel.TOP);
        replyArea.setLineWrap(true);
        replyArea.setWrapStyleWord(true);
        replyArea.setFont(Theme.FONT_BODY);
        replyArea.setBorder(new EmptyBorder(6, 8, 6, 8));
        Theme.tip(replyArea, "Answer the Admin's question (required, up to " + Claim.MAX_NOTE_LENGTH + " characters)");
        replyScroll.setBorder(new LineBorder(Theme.BORDER, 1, true));
        replyScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));

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
        stack.add(left(progress));
        stack.add(Box.createVerticalStrut(8));
        stack.add(left(summaryLabel));
        stack.add(Box.createVerticalStrut(12));
        stack.add(left(factsLabel));
        stack.add(Box.createVerticalStrut(12));
        stack.add(left(adminNoteHeading));
        stack.add(Box.createVerticalStrut(4));
        stack.add(left(adminNoteLabel));
        stack.add(Box.createVerticalStrut(12));
        stack.add(left(replyHeading));
        stack.add(Box.createVerticalStrut(4));
        stack.add(left(sentReplyLabel));
        stack.add(left(replyScroll));

        replyButton.setMnemonic(KeyEvent.VK_S);
        slipButton.setMnemonic(KeyEvent.VK_V);
        Theme.tip(replyButton, "Send your answer to the Admin. The claim goes back to review (Alt+S)");
        Theme.tip(slipButton, "Show and print your payout slip (Alt+V)");
        replyButton.setPreferredSize(new Dimension(0, 40));
        slipButton.setPreferredSize(new Dimension(0, 40));
        buttonRow.setOpaque(false);
        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.setOpaque(false);
        south.add(buttonRow, BorderLayout.NORTH);
        south.add(statusLabel, BorderLayout.SOUTH);

        JPanel detail = new JPanel(new BorderLayout(0, 10));
        detail.setOpaque(false);
        detail.add(header, BorderLayout.NORTH);
        detail.add(Widgets.scrollDown(stack), BorderLayout.CENTER);
        detail.add(south, BorderLayout.SOUTH);
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

    // ------------------------------------------------------------------ behaviour

    @Override
    public void onShow() {
        refreshList();
        if (shown.isEmpty()) {
            selectedId = null;
            detailCards.show(detailHolder, "empty");
            return;
        }
        boolean stillThere = false;
        for (Claim c : shown) {
            if (c.getId().equals(selectedId)) {
                stillThere = true;
            }
        }
        if (!stillThere) {
            selectedId = shown.get(0).getId();            // start with the first (the most urgent)
        }
        table.selectQuietly(selectedId);
        loadDetail();
    }

    private void sendReply() {
        String id = selectedId;
        try {
            service.respond(applicant, id, replyArea.getText());
            replyArea.setText("");
            refreshList();
            loadDetail();
            setStatus("Reply sent. " + id + " is back with the Admin for review.", true);
        } catch (InvalidInputException e) {
            setStatus(e.getMessage(), false);
            replyArea.requestFocusInWindow();
        } catch (ICSException e) {
            setStatus(e.getMessage(), false);
        }
    }

    /** Shows the slip window. (A separate method so tests can catch it.) */
    protected void showSlip(String claimId, List<String> lines) {
        Dialogs.showSlip(this, "Payout slip - " + claimId, lines);
    }

    private void refreshList() {
        shown = service.claimsOf(applicant);
        List<Object[]> rows = new ArrayList<>();
        int waiting = 0;
        for (Claim c : shown) {
            rows.add(new Object[] {c.getId(), Theme.money(c.getClaimedAmount()), userLabel(c.getStatus()),
                    c.getFiledDate().toString()});
            if (c.getStatus() == Claim.Status.INFO_REQUESTED) {
                waiting++;
            }
        }
        table.setRows(rows, selectedId);
        countLabel.setText(shown.size() + (shown.size() == 1 ? " claim" : " claims")
                + (waiting > 0 ? ", " + waiting + (waiting == 1 ? " needs" : " need") + " your reply" : ""));
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
        boolean waitingForMe = status == Claim.Status.INFO_REQUESTED;

        titleLabel.setText(c.getId());
        chip.setText(userLabel(status));
        chip.setBackground(Widgets.colorFor(status));
        progress.showClaim(c);
        summaryLabel.setText(Theme.wrap(summary(c), 360));
        summaryLabel.setBackground(waitingForMe ? Theme.AMBER_FILL : Theme.SOFT_PURPLE);
        summaryLabel.setForeground(waitingForMe ? Theme.AMBER : Theme.NAVY);

        StringBuilder facts = new StringBuilder("<table cellspacing='0' cellpadding='2'>");
        Theme.tableRow(facts, "Policy", p.getId() + " - " + p.getType().getLabel());
        Theme.tableRow(facts, "Incident date", c.getIncidentDate().toString());
        Theme.tableRow(facts, "Filed", c.getFiledDate().toString());
        Theme.tableRow(facts, "Claimed", Theme.money(c.getClaimedAmount()));
        if (c.getApprovedAmount() > 0) {
            Theme.tableRow(facts, "Approved", Theme.money(c.getApprovedAmount()));
        }
        if (c.getPaidAmount() > 0) {
            Theme.tableRow(facts, "Paid to you", Theme.money(c.getPaidAmount()));
        }
        if (c.getReason() != null) {
            Theme.tableRow(facts, "Reason", c.getReason().getLabel());
        }
        Theme.tableRow(facts, "What happened", c.getDescription());
        factsLabel.setText(Theme.wrapHtml(facts.append("</table>").toString(), 360));

        boolean hasNote = !c.getReviewerNote().isBlank();
        adminNoteHeading.setVisible(hasNote);
        adminNoteLabel.setVisible(hasNote);
        adminNoteLabel.setText(Theme.wrap(c.getReviewerNote(), 360));

        boolean hasReply = !c.getApplicantNote().isBlank();
        replyHeading.setVisible(waitingForMe || hasReply);
        replyHeading.setText(waitingForMe ? "Your reply to the Admin" : "Your reply");
        sentReplyLabel.setVisible(hasReply && !waitingForMe);
        sentReplyLabel.setText(Theme.wrap(c.getApplicantNote(), 360));
        replyScroll.setVisible(waitingForMe);

        buttonRow.removeAll();
        if (waitingForMe) {
            buttonRow.add(replyButton);
        }
        if (c.getPaidAmount() > 0) {
            buttonRow.add(slipButton);
        }
        buttonRow.revalidate();
        buttonRow.repaint();
        detailCards.show(detailHolder, "detail");
    }

    /** What the status means, in plain words. */
    private static String summary(Claim c) {
        Claim.Status s = c.getStatus();
        if (s == Claim.Status.FILED) {
            return "Received. It will be reviewed soon.";
        }
        if (s == Claim.Status.UNDER_REVIEW) {
            return c.getApplicantNote().isBlank() ? "The Admin is reviewing your claim."
                    : "Thank you for your reply. The Admin is reviewing your claim again.";
        }
        if (s == Claim.Status.INFO_REQUESTED) {
            return "The Admin needs more information from you. Read the note below and reply.";
        }
        if (s == Claim.Status.APPROVED || s == Claim.Status.PAID) {
            if (c.getPaidAmount() > 0) {
                return "Approved: " + Theme.money(c.getPaidAmount()) + " was released to you. You can view and print "
                        + "your payout slip.";
            }
            return "Approved for " + Theme.money(c.getApprovedAmount()) + ".";
        }
        if (s == Claim.Status.DENIED) {
            return "Denied: " + c.getReason().getLabel() + ".";
        }
        if (s == Claim.Status.REJECTED) {
            return "Rejected automatically: " + c.getReason().getLabel() + ".";
        }
        return c.getPaidAmount() > 0 ? "Closed after paying you " + Theme.money(c.getPaidAmount()) + "." : "Closed.";
    }
}
