package ics.gui;

import ics.model.User;
import ics.service.AuthService;
import ics.service.ClaimService;
import ics.service.PolicyService;
import ics.service.UserService;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;

/**
 * The first screen after logging in.
 *   Admin: four counts (users, policies, claims, payouts) and shortcuts to the work waiting.
 *   User:  their own counts and shortcuts to apply or follow claims.
 * Both see a warning while the default password is still in use, and a User sees one when the Admin is
 * waiting for their reply.
 */
@SuppressWarnings({"serial", "this-escape"})
public class DashboardPanel extends BasePanel {

    private final ShellPanel shell;            // to open other screens from the shortcut buttons
    private final User user;
    private final AuthService auth;
    private final UserService users;
    private final PolicyService policies;
    private final ClaimService claims;

    // the four number cards; the words change with the role
    private final JLabel[] numbers = new JLabel[4];
    private final JLabel[] hints = new JLabel[4];
    private final JLabel replyHint = Theme.label(" ", Theme.FONT_LABEL, Theme.AMBER);
    private final JLabel passwordHint = Theme.label(" ", Theme.FONT_LABEL, Theme.ERROR);

    public DashboardPanel(ShellPanel shell, User user, AuthService auth, UserService users,
                          PolicyService policies, ClaimService claims) {
        super("Dashboard", "Welcome, " + user.getUsername());
        this.shell = shell;
        this.user = user;
        this.auth = auth;
        this.users = users;
        this.policies = policies;
        this.claims = claims;

        String[] titles = user.canManageUsers()
                ? new String[] {"USERS", "POLICIES", "CLAIMS", "PAYOUTS"}
                : new String[] {"MY POLICIES", "MY CLAIMS", "NEED YOUR REPLY", "PAID TO YOU"};
        JPanel stats = new JPanel(new GridLayout(1, 4, 16, 0));
        stats.setOpaque(false);
        for (int i = 0; i < 4; i++) {
            numbers[i] = Theme.label("0", Theme.FONT_STAT, Theme.NAVY);
            hints[i] = Theme.label(" ", Theme.FONT_CAPTION, Theme.TEXT_MUTED);
            JPanel card = Theme.card(18);
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.add(Theme.label(titles[i], Theme.FONT_LABEL, Theme.TEXT_MUTED));
            card.add(numbers[i]);
            card.add(hints[i]);
            stats.add(card);
        }

        // shortcut buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);
        if (user.canManageUsers()) {
            buttons.add(shortcut("Add user", "Users", KeyEvent.VK_A, Widgets.RoundedButton.Kind.PRIMARY));
            buttons.add(Box.createHorizontalStrut(12));
            buttons.add(shortcut("Review claims", "Review Claims", KeyEvent.VK_R, Widgets.RoundedButton.Kind.SECONDARY));
        } else {
            buttons.add(shortcut("Apply for a claim", "Apply for Claim", KeyEvent.VK_A, Widgets.RoundedButton.Kind.PRIMARY));
            buttons.add(Box.createHorizontalStrut(12));
            buttons.add(shortcut("My claims", "My Claims", KeyEvent.VK_M, Widgets.RoundedButton.Kind.SECONDARY));
        }
        JPanel actions = Theme.card(24);
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        JLabel heading = Theme.label("Quick actions", Theme.FONT_HEADING, Theme.TEXT_DARK);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        replyHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        passwordHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        actions.add(heading);
        actions.add(Box.createVerticalStrut(14));
        actions.add(buttons);
        actions.add(Box.createVerticalStrut(12));
        actions.add(replyHint);
        actions.add(passwordHint);

        JPanel column = new JPanel(new BorderLayout(0, 16));
        column.setOpaque(false);
        column.add(stats, BorderLayout.NORTH);
        column.add(actions, BorderLayout.CENTER);
        getBody().add(column, BorderLayout.NORTH);
    }

    private Widgets.RoundedButton shortcut(String text, String screen, int key, Widgets.RoundedButton.Kind kind) {
        Widgets.RoundedButton button = new Widgets.RoundedButton(text, kind);
        button.setPreferredSize(new java.awt.Dimension(160, 40));
        button.setMnemonic(key);
        Theme.tip(button, "Open " + screen);
        button.addActionListener(e -> shell.show(screen));
        return button;
    }

    /** Recounts every time the dashboard opens, so the numbers are always current. */
    @Override
    public void onShow() {
        if (user.canManageUsers()) {
            int waiting = claims.countNeedingAction();
            show(0, users.countUsers(), "accounts");
            show(1, policies.countPolicies(), "issued");
            show(2, claims.countClaims(), waiting == 0 ? "none need action" : waiting + " need action");
            show(3, claims.countPayouts(), "released");
        } else {
            int mine = claims.claimsOf(user).size();
            int replies = claims.countAwaitingReply(user);
            double paid = 0;
            for (ics.model.Claim c : claims.claimsOf(user)) {
                paid += c.getPaidAmount();
            }
            show(0, claims.policiesOf(user).size(), "issued to you");
            show(1, mine, "applied for");
            show(2, replies, replies == 0 ? "nothing waiting" : "open My Claims");
            numbers[3].setText(Theme.money(paid));
            numbers[3].setFont(Theme.FONT_HEADING);
            hints[3].setText("in total");
            replyHint.setText(replies == 0 ? " " : "The Admin is waiting for your reply on " + replies
                    + (replies == 1 ? " claim" : " claims") + ". Open My Claims.");
        }
        passwordHint.setText(auth.isUsingDefaultPassword(user)
                ? "You are still using the default password. Change it under Change Password." : " ");
    }

    private void show(int card, int number, String hint) {
        numbers[card].setText(String.valueOf(number));
        hints[card].setText(hint);
    }
}
