package com.permanentdeath;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class PermanentDeathPanel extends PluginPanel {

    private final IconTextField searchBar;
    private final JPanel listContainer;
    private final JScrollPane scrollPane;

    private final Map<String, MonsterEntry> monsterEntries = new LinkedHashMap<>();
    private final Map<String, Integer> totalCounts;

    private static final Color COLOR_RED = ColorScheme.PROGRESS_ERROR_COLOR;
    private static final Color COLOR_YELLOW = ColorScheme.PROGRESS_INPROGRESS_COLOR;
    private static final Color COLOR_GREEN = ColorScheme.PROGRESS_COMPLETE_COLOR;
    private static final Color COLOR_COMPLETED = Color.CYAN;
    private static final Color BG_COLOR = ColorScheme.DARK_GRAY_COLOR;
    private static final Color BOX_COLOR = ColorScheme.DARKER_GRAY_COLOR;

    public PermanentDeathPanel(Map<String, Integer> totalCounts) {
        super(false);
        this.totalCounts = totalCounts;

        setLayout(new BorderLayout());
        setBackground(BG_COLOR);

        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        searchPanel.setBackground(BG_COLOR);

        searchBar = new IconTextField();
        searchBar.setIcon(IconTextField.Icon.SEARCH);
        searchBar.setPreferredSize(new Dimension(0, 35));
        searchBar.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filterList(); }
            @Override public void removeUpdate(DocumentEvent e) { filterList(); }
            @Override public void changedUpdate(DocumentEvent e) { filterList(); }
        });
        searchPanel.add(searchBar, BorderLayout.CENTER);
        add(searchPanel, BorderLayout.NORTH);

        JPanel listWrapper = new JPanel(new BorderLayout());
        listWrapper.setBackground(BG_COLOR);

        listContainer = new JPanel(new GridLayout(0, 1, 0, 5));
        listContainer.setBackground(BG_COLOR);
        listContainer.setBorder(new EmptyBorder(0, 10, 10, 10));

        listWrapper.add(listContainer, BorderLayout.NORTH);

        scrollPane = new JScrollPane(listWrapper);
        scrollPane.setBackground(BG_COLOR);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        add(scrollPane, BorderLayout.CENTER);
    }

    public void buildList(Set<String> monsterNames, Map<String, Integer> killCounts) {
        SwingUtilities.invokeLater(() -> {
            listContainer.removeAll();
            monsterEntries.clear();

            for (String name : monsterNames) {
                int currentKills = killCounts.getOrDefault(name, 0);
                int total = totalCounts.getOrDefault(name, -1);

                MonsterEntry entry = new MonsterEntry(name, currentKills, total);

                monsterEntries.put(name, entry);
                listContainer.add(entry.panel);
            }

            revalidate();
            repaint();
        });
    }

    public void updateMonsterCount(String name, int newCount) {
        SwingUtilities.invokeLater(() -> {
            MonsterEntry entry = monsterEntries.get(name);
            if (entry != null) {
                int total = totalCounts.getOrDefault(name, -1);

                entry.update(newCount, total);

                listContainer.remove(entry.panel);
                listContainer.add(entry.panel, 0); // Add to top

                revalidate();
                repaint();
            }
        });
    }

    private void filterList() {
        String filterText = searchBar.getText().toLowerCase();

        SwingUtilities.invokeLater(() -> {
            for (MonsterEntry entry : monsterEntries.values()) {
                boolean isVisible = filterText.isEmpty() || entry.monsterName.toLowerCase().contains(filterText);
                entry.panel.setVisible(isVisible);
            }
        });
    }

    private static class MonsterEntry {
        final String monsterName;
        final JPanel panel;
        final JLabel label;
        final JProgressBar progressBar;

        MonsterEntry(String name, int current, int total) {
            this.monsterName = name;

            panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBackground(BOX_COLOR);
            panel.setBorder(new EmptyBorder(5, 5, 5, 5));

            label = new JLabel();
            progressBar = new JProgressBar(0, total);

            update(current, total);

            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);

            label.setOpaque(false);
            progressBar.setOpaque(false);

            panel.add(label);

            if (total > 1) {
                panel.add(Box.createRigidArea(new Dimension(0, 3)));
                panel.add(progressBar);
            }
        }

        void update(int current, int total) {
            String labelText;
            if (total == 1) {
                labelText = String.format("%s: %d / 1", monsterName, current);
            } else if (total > 1) {
                labelText = String.format("%s: %d / %d", monsterName, current, total);
            } else {
                labelText = String.format("%s: %d", monsterName, current);
            }
            label.setText(labelText);

            if (total <= 1) {
                if (current >= 1) {
                    label.setForeground(COLOR_COMPLETED);
                } else {
                    label.setForeground(Color.WHITE);
                }
            } else {
                progressBar.setValue(current);
                double percent = (double) current / total;

                if (percent >= 1.0) {
                    label.setForeground(COLOR_COMPLETED);
                    progressBar.setForeground(COLOR_COMPLETED);
                } else if (percent >= 0.66) {
                    label.setForeground(Color.WHITE);
                    progressBar.setForeground(COLOR_GREEN);
                } else if (percent >= 0.33) {
                    label.setForeground(Color.WHITE);
                    progressBar.setForeground(COLOR_YELLOW);
                } else {
                    label.setForeground(Color.WHITE);
                    progressBar.setForeground(COLOR_RED);
                }
            }
        }
    }
}