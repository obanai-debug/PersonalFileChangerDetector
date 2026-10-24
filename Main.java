
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

public class Main extends JFrame {

    private Path selectedFolder;
    private final JLabel folderLabel = new JLabel("No folder selected");
    private final JLabel statusLabel = new JLabel("Select a folder to get started.");
    private final DefaultTableModel model = new DefaultTableModel(
            new String[] { "File", "Integrity Status" }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable table = new JTable(model);

    private final Path appDirectory = Paths.get(
            System.getProperty("user.home"),
            ".personal-file-change-detector");

    public Main() {
        setTitle("Personal File Change Detector");
        setSize(760, 500);
        setMinimumSize(new Dimension(600, 400));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        createInterface();
    }

    private void createInterface() {
        JPanel main = new JPanel(new BorderLayout(12, 12));
        main.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        main.setBackground(new Color(245, 247, 250));
        setContentPane(main);

        JLabel heading = new JLabel("Personal File Change Detector");
        heading.setFont(new Font("SansSerif", Font.BOLD, 23));

        JLabel subtitle = new JLabel(
                "Check whether your files have changed.");
        subtitle.setForeground(Color.DARK_GRAY);

        JPanel header = new JPanel(new GridLayout(2, 1, 0, 5));
        header.setOpaque(false);
        header.add(heading);
        header.add(subtitle);

        JButton selectButton = new JButton("Select Folder");
        JButton baselineButton = new JButton("Create Baseline");
        JButton checkButton = new JButton("Check Integrity");

        styleButton(selectButton);
        styleButton(baselineButton);
        styleButton(checkButton);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        buttons.setOpaque(false);
        buttons.add(selectButton);
        buttons.add(baselineButton);
        buttons.add(checkButton);

        folderLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        folderLabel.setBorder(
                BorderFactory.createEmptyBorder(5, 4, 5, 4));

        JPanel top = new JPanel(new BorderLayout(5, 8));
        top.setOpaque(false);
        top.add(header, BorderLayout.NORTH);

        JPanel folderPanel = new JPanel(new BorderLayout());
        folderPanel.setOpaque(false);
        folderPanel.add(buttons, BorderLayout.NORTH);
        folderPanel.add(folderLabel, BorderLayout.SOUTH);
        top.add(folderPanel, BorderLayout.CENTER);

        main.add(top, BorderLayout.NORTH);

        table.setRowHeight(28);
        table.setFont(new Font("SansSerif", Font.PLAIN, 13));
        table.getTableHeader().setFont(
                new Font("SansSerif", Font.BOLD, 13));
        table.setFillsViewportHeight(true);
        table.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION);

        table.getColumnModel().getColumn(0).setPreferredWidth(490);
        table.getColumnModel().getColumn(1).setPreferredWidth(180);

        table.getColumnModel().getColumn(1)
                .setCellRenderer(new StatusRenderer());

        JScrollPane scrollPane = new JScrollPane(table);
        main.add(scrollPane, BorderLayout.CENTER);

        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        statusLabel.setBorder(
                BorderFactory.createEmptyBorder(8, 4, 4, 4));
        main.add(statusLabel, BorderLayout.SOUTH);

        selectButton.addActionListener(e -> selectFolder());
        baselineButton.addActionListener(e -> createBaseline());
        checkButton.addActionListener(e -> checkIntegrity());
    }

    private void styleButton(JButton button) {
        button.setFocusPainted(false);
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setMargin(new Insets(9, 12, 9, 12));
    }

    private void selectFolder() {
        JFileChooser chooser = new JFileChooser();

        chooser.setDialogTitle("Choose a folder to monitor");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        if (selectedFolder != null) {
            chooser.setCurrentDirectory(selectedFolder.toFile());
        }

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedFolder = chooser.getSelectedFile().toPath()
                    .toAbsolutePath().normalize();

            folderLabel.setText("Selected: " + selectedFolder);
            model.setRowCount(0);
            statusLabel.setText(
                    "Folder selected. Create a baseline or check its integrity.");

        }
    }

    private String sha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(data);

        StringBuilder result = new StringBuilder();

        for (byte b : bytes) {
            result.append(String.format("%02x", b & 0xff));
        }

        return result.toString();
    }

    private String fileHash(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;

            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }

        StringBuilder result = new StringBuilder();

        for (byte b : digest.digest()) {
            result.append(String.format("%02x", b & 0xff));
        }

        return result.toString();
    }

    private Map<String, String> scanFolder() throws Exception {
        Map<String, String> hashes = new HashMap<>();

        try (Stream<Path> paths = Files.walk(selectedFolder)) {
            Iterator<Path> iterator = paths.iterator();

            while (iterator.hasNext()) {
                Path file = iterator.next();

                if (Files.isRegularFile(file)) {
                    String relative = selectedFolder
                            .relativize(file).toString();

                    hashes.put(relative, fileHash(file));
                }
            }
        }

        return hashes;
    }

    private Path baselinePath() throws Exception {
        Files.createDirectories(appDirectory);

        String folderId = sha256(selectedFolder.toString()
                .getBytes(StandardCharsets.UTF_8));

        return appDirectory.resolve(folderId + ".txt");
    }

    private void createBaseline() {
        if (selectedFolder == null) {
            showMessage("Please select a folder first.");
            return;
        }

        int answer = JOptionPane.showConfirmDialog(
                this,
                "Save the current state of this folder as the baseline?\n"
                        + "An existing baseline for this folder will be replaced.",
                "Create Baseline",
                JOptionPane.YES_NO_OPTION);

        if (answer != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            Map<String, String> hashes = scanFolder();
            Path baseline = baselinePath();

            try (BufferedWriter writer = Files.newBufferedWriter(
                    baseline, StandardCharsets.UTF_8)) {

                for (Map.Entry<String, String> entry : hashes.entrySet()) {
                    String encodedName = Base64.getEncoder().encodeToString(
                            entry.getKey().getBytes(StandardCharsets.UTF_8));

                    writer.write(encodedName + "\t" + entry.getValue());
                    writer.newLine();
                }
            }

            model.setRowCount(0);
            statusLabel.setText(
                    "Baseline saved. Files recorded: " + hashes.size());

            showMessage("Baseline created successfully!\nFiles recorded: "
                    + hashes.size());

        } catch (Exception ex) {
            showError(ex);
        }
    }

    private Map<String, String> readBaseline(Path baseline)
            throws IOException {

        Map<String, String> hashes = new HashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(
                baseline, StandardCharsets.UTF_8)) {

            String line;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t", 2);

                if (parts.length == 2) {
                    try {
                        String name = new String(
                                Base64.getDecoder().decode(parts[0]),
                                StandardCharsets.UTF_8);

                        hashes.put(name, parts[1]);
                    } catch (IllegalArgumentException ex) {
                        throw new IOException("Baseline file is invalid.");
                    }
                } else {
                    throw new IOException("Baseline file is invalid.");
                }
            }
        }

        return hashes;
    }

    private void checkIntegrity() {
        if (selectedFolder == null) {
            showMessage("Please select a folder first.");
            return;
        }

        try {
            Path baseline = baselinePath();

            if (!Files.isRegularFile(baseline)) {
                showMessage("No baseline exists for this folder.\n"
                        + "Click Create Baseline first.");
                return;
            }

            Map<String, String> oldHashes = readBaseline(baseline);
            Map<String, String> newHashes = scanFolder();

            model.setRowCount(0);

            int modified = 0;
            int added = 0;
            int deleted = 0;
            int unchanged = 0;

            for (Map.Entry<String, String> entry : newHashes.entrySet()) {
                String name = entry.getKey();
                String currentHash = entry.getValue();
                String oldHash = oldHashes.get(name);

                String result;

                if (oldHash == null) {
                    result = "NEW FILE";
                    added++;
                } else if (!oldHash.equals(currentHash)) {
                    result = "MODIFIED";
                    modified++;
                } else {
                    result = "UNCHANGED";
                    unchanged++;
                }

                model.addRow(new Object[] { name, result });
            }

            for (String name : oldHashes.keySet()) {
                if (!newHashes.containsKey(name)) {
                    model.addRow(new Object[] { name, "DELETED" });
                    deleted++;
                }
            }

            statusLabel.setText(
                    "Unchanged: " + unchanged
                            + " | Modified: " + modified
                            + " | New: " + added
                            + " | Deleted: " + deleted);

        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void showMessage(String message) {
        JOptionPane.showMessageDialog(
                this, message, "File Change Detector",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(Exception ex) {
        JOptionPane.showMessageDialog(
                this,
                "Operation failed:\n" + ex.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
    }

    private static class StatusRenderer
            extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean selected,
                boolean focused, int row, int column) {

            Component component = super.getTableCellRendererComponent(
                    table, value, selected, focused, row, column);

            if (!selected) {
                String status = String.valueOf(value);

                switch (status) {
                    case "UNCHANGED":
                        component.setForeground(new Color(25, 125, 65));
                        break;
                    case "MODIFIED":
                        component.setForeground(new Color(190, 110, 0));
                        break;
                    case "NEW FILE":
                        component.setForeground(new Color(30, 90, 190));
                        break;
                    case "DELETED":
                        component.setForeground(new Color(190, 45, 45));
                        break;
                    default:
                        component.setForeground(Color.BLACK);
                }
            }

            return component;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(
                        UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Use the default Swing appearance.
            }

            new Main().setVisible(true);
        });
    }
}
