package ics.gui;

import ics.model.User;
import ics.repository.DataStore;
import ics.service.AuthService;
import ics.service.ClaimService;
import ics.service.PolicyService;
import ics.service.UserService;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What you see after logging in: the sidebar on the left and one screen at a time on the right.
 *
 * WHY the screens depend on the role (rubric II.C.7): the sidebar is built from what the account CAN do
 * (canManageUsers, canApplyForClaims), so an Admin and a User get different screens, and nobody can open
 * a screen that is not theirs.
 */
@SuppressWarnings({"serial", "this-escape"})
public class ShellPanel extends JPanel {

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);              // holds every screen; shows one at a time
    private final Map<String, BasePanel> pages = new LinkedHashMap<>();   // keeps the order they were added
    private final List<Widgets.NavButton> navButtons = new ArrayList<>();

    public ShellPanel(DataStore store, User user, Runnable onLogout) {
        super(new BorderLayout());

        // one of each service, shared by all the screens
        AuthService auth = new AuthService(store);
        UserService users = new UserService(store);
        PolicyService policies = new PolicyService(store, auth);
        ClaimService claims = new ClaimService(store, auth);

        pages.put("Dashboard", new DashboardPanel(this, user, auth, users, policies, claims));
        if (user.canManageUsers()) {                               // ADMIN screens
            pages.put("Users", new UserPanel(users, user));
            pages.put("Policies", new PolicyPanel(policies, user));
            pages.put("Review Claims", new ReviewPanel(claims, user));
        }
        if (user.canApplyForClaims()) {                            // USER screens
            ApplyPanel apply = new ApplyPanel(claims, user);
            pages.put("My Policies", new MyPoliciesPanel(claims, user, policyId -> {
                apply.preselect(policyId);                        // "Apply for a claim" on My Policies
                show("Apply for Claim");
            }));
            pages.put("Apply for Claim", apply);
            pages.put("My Claims", new MyClaimsPanel(claims, user));
        }
        pages.put("Change Password", new ChangePasswordPanel(auth, user));   // ACCOUNT, for everyone

        for (Map.Entry<String, BasePanel> page : pages.entrySet()) {
            content.add(page.getValue(), page.getKey());
        }
        add(buildSidebar(user, onLogout), BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        show("Dashboard");
    }

    /** Opens a screen: shows its card, highlights its sidebar button, and lets it reload its data. */
    public void show(String key) {
        if (!pages.containsKey(key)) {
            return;                                                // not a screen for this role
        }
        cards.show(content, key);
        for (Widgets.NavButton b : navButtons) {
            b.setSelected(b.getKey().equals(key));
        }
        pages.get(key).onShow();
    }

    // ------------------------------------------------------------------ the sidebar

    private JPanel buildSidebar(User user, Runnable onLogout) {
        JPanel sidebar = new JPanel();
        sidebar.setBackground(Theme.NAVY);
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(Theme.SIDEBAR_WIDTH, 0));

        // logo and name
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        brand.setOpaque(false);
        brand.setBorder(new EmptyBorder(24, 10, 18, 12));
        brand.add(new JLabel(new Widgets.ShieldIcon(40, Theme.WHITE, Theme.NAVY)));
        JPanel words = new JPanel();
        words.setOpaque(false);
        words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
        words.add(Theme.label("ICS", Theme.FONT_HEADING, Theme.WHITE));
        words.add(Theme.label("Claims System", Theme.FONT_CAPTION, new java.awt.Color(0xD9CCF0)));
        brand.add(words);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        brand.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        sidebar.add(brand);

        // one button per screen, with a heading where each section starts
        for (String key : pages.keySet()) {
            if (key.equals("Users")) {
                sidebar.add(sectionHeading("ADMIN"));
            } else if (key.equals("My Policies")) {
                sidebar.add(sectionHeading("USER"));
            } else if (key.equals("Change Password")) {
                sidebar.add(sectionHeading("ACCOUNT"));
            }
            Widgets.NavButton button = new Widgets.NavButton(key);
            Theme.tip(button, "Open " + key);
            button.addActionListener(e -> show(key));
            navButtons.add(button);
            JPanel row = new JPanel(new BorderLayout());
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(3, 20, 3, 20));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.add(button, BorderLayout.CENTER);
            sidebar.add(row);
        }
        sidebar.add(Box.createVerticalGlue());                    // pushes the footer to the bottom

        // who is logged in, and Log out
        JPanel footer = new JPanel();
        footer.setOpaque(false);
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.setBorder(new EmptyBorder(16, 20, 24, 20));
        footer.setAlignmentX(Component.LEFT_ALIGNMENT);
        java.awt.Color softWhite = new java.awt.Color(0xD9CCF0);
        footer.add(Theme.label("Signed in as", Theme.FONT_CAPTION, softWhite));
        footer.add(Theme.label(user.getUsername(), Theme.FONT_BUTTON, Theme.WHITE));     // what they typed at login
        footer.add(Theme.label(user.canManageUsers() ? "Admin" : "User", Theme.FONT_CAPTION, softWhite));
        footer.add(Box.createVerticalStrut(12));

        // DELIBERATE IMPERFECTION: Log out keeps the old rounded style and its own width, so it does not
        // quite match the plain sidebar buttons above it. To fix it, use  new Widgets.NavButton("Log out")  instead.
        Widgets.RoundedButton logout = new Widgets.RoundedButton("Log out", Widgets.RoundedButton.Kind.SECONDARY);
        logout.setPreferredSize(new Dimension(150, 34));
        logout.setMaximumSize(new Dimension(150, 34));
        logout.setAlignmentX(Component.LEFT_ALIGNMENT);
        Theme.tip(logout, "Go back to the login screen");
        logout.addActionListener(e -> onLogout.run());
        footer.add(logout);
        sidebar.add(footer);
        return sidebar;
    }

    private static JLabel sectionHeading(String text) {
        JLabel label = Theme.label(text, Theme.FONT_LABEL, new java.awt.Color(0xD9CCF0));
        label.setBorder(new EmptyBorder(16, 20, 4, 0));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }
}
