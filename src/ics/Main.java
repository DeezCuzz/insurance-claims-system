package ics;

import ics.exception.ICSException;
import ics.gui.LoginPanel;
import ics.gui.ShellPanel;
import ics.gui.Theme;
import ics.gui.Widgets;
import ics.model.User;
import ics.repository.DataStore;
import ics.service.AuthService;
import ics.util.FileHelper;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.file.Path;


 // <your home folder>/ICS-data (see util/FileHelper.java).
 
@SuppressWarnings({"serial", "this-escape"})
public class Main extends JFrame {

    private final DataStore store;
    private final AuthService auth;

    public Main(DataStore store, AuthService auth) {
        super("Insurance Claims System");
        this.store = store;
        this.auth = auth;
        setIconImage(new Widgets.ShieldIcon(64, Theme.NAVY, Theme.WHITE).toImage());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(Theme.MIN_WINDOW);      // Bulletin Board: never smaller than 1024 x 768
        setSize(Theme.DEFAULT_WINDOW);
        setLocationRelativeTo(null);           // centre of the screen
        showLogin();
    }

    /** Shows the login screen. Also used by "Log out". */
    public void showLogin() {
        LoginPanel login = new LoginPanel(auth, user -> showShell(user), () -> {
            dispose();                         // rubric II.C.6: after the third wrong password the program closes
            System.exit(0);
        });
        setContentPane(login);
        revalidate();
        repaint();
    }

    /** After a good login: the sidebar and the screens for this person's role. */
    public void showShell(User user) {
        setContentPane(new ShellPanel(store, user, () -> showLogin()));
        revalidate();
        
        
        repaint();
    }

    public static void main(String[] args) {
        // Swing windows must be built on Swing's own thread; invokeLater puts our code there.
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            Path folder = FileHelper.dataFolder();
            DataStore store = new DataStore(folder);
            AuthService auth = new AuthService(store);
            try {
                if (FileHelper.moveOldDataIfNeeded(folder)) {
                    JOptionPane.showMessageDialog(null, "Your data was moved to one fixed folder, so it is the same "
                            + "however you start the program:\n\n" + folder, "Data folder", JOptionPane.INFORMATION_MESSAGE);
                }
                store.loadAll();
                if (!store.loadWarnings().isEmpty()) {           // fix D4: say what was skipped, then carry on
                    StringBuilder message = new StringBuilder("Some lines in the data files were damaged and were skipped:\n\n");
                    for (String warning : store.loadWarnings()) {
                        message.append("- ").append(warning).append("\n");
                    }
                    message.append("\nA copy of each damaged file was kept as a .bak file in:\n").append(folder);
                    JOptionPane.showMessageDialog(null, message.toString(), "Damaged data", JOptionPane.WARNING_MESSAGE);
                }
                if (auth.ensureFirstAdmin()) {
                    JOptionPane.showMessageDialog(null, "First run: an Admin account was created.\n\n"
                            + "Username:  " + AuthService.DEFAULT_ADMIN_USERNAME + 
                            "\n" + "Password:  " + AuthService.DEFAULT_ADMIN_PASSWORD + "\n\n"
                            + "Change it right away under Change Password.\n"
                            + "Your data is kept in: " + folder, "Welcome", JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (ICSException | IOException e) {
                JOptionPane.showMessageDialog(null, e.getMessage(), "Cannot start", JOptionPane.ERROR_MESSAGE);
                return;
            }
            new Main(store, auth).setVisible(true);
        });
    }
}
