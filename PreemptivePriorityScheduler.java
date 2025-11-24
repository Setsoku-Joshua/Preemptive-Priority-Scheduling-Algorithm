import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class PreemptivePriorityScheduler extends JFrame {
    private DefaultTableModel procTableModel;
    private JTable procTable;
    private DefaultTableModel resultTableModel;
    private JTable resultTable;
    private GanttPanel ganttPanel;
    private JLabel avgLabel;

    private int nextPid = 1;

    public PreemptivePriorityScheduler() {
        super("Preemptive Priority Scheduler (Single-file)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 650);
        setLocationRelativeTo(null);
        initUI();
    }

    private void initUI() {
        procTableModel = new DefaultTableModel(new Object[]{"PID", "Arrival", "Burst", "Priority"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column != 0; 
            }
        };
        procTable = new JTable(procTableModel);
        procTable.setFillsViewportHeight(true);
        procTable.getColumnModel().getColumn(0).setMaxWidth(60);

        JScrollPane procScroll = new JScrollPane(procTable);
        procScroll.setPreferredSize(new Dimension(420, 200));

        JButton addBtn = new JButton("Add Row");
        JButton removeBtn = new JButton("Remove Row");
        JButton sampleBtn = new JButton("Auto Fill Sample");
        JButton clearBtn = new JButton("Clear");
        JButton runBtn = new JButton("Run Scheduler");

        addBtn.addActionListener(e -> addRow());
        removeBtn.addActionListener(e -> removeSelectedRows());
        clearBtn.addActionListener(e -> clearAll());
        sampleBtn.addActionListener(e -> fillSample());
        runBtn.addActionListener(e -> runScheduler());

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controlPanel.add(addBtn);
        controlPanel.add(removeBtn);
        controlPanel.add(sampleBtn);
        controlPanel.add(clearBtn);
        controlPanel.add(runBtn);

        JPanel topLeft = new JPanel(new BorderLayout(5,5));
        topLeft.add(new JLabel("Processes (PID auto-assigned)"), BorderLayout.NORTH);
        topLeft.add(procScroll, BorderLayout.CENTER);
        topLeft.add(controlPanel, BorderLayout.SOUTH);

        resultTableModel = new DefaultTableModel(new Object[]{"PID", "Arrival", "Burst", "Priority", "Waiting", "Turnaround"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        resultTable = new JTable(resultTableModel);
        JScrollPane resultScroll = new JScrollPane(resultTable);
        resultScroll.setPreferredSize(new Dimension(420, 200));

        avgLabel = new JLabel("Average Waiting Time: -    Average Turnaround Time: -");

        JPanel topRight = new JPanel(new BorderLayout(5,5));
        topRight.add(new JLabel("Results"), BorderLayout.NORTH);
        topRight.add(resultScroll, BorderLayout.CENTER);
        topRight.add(avgLabel, BorderLayout.SOUTH);

        JPanel topPanel = new JPanel(new GridLayout(1,2,10,10));
        topPanel.add(topLeft);
        topPanel.add(topRight);

        ganttPanel = new GanttPanel();
        ganttPanel.setPreferredSize(new Dimension(820, 280));
        JPanel center = new JPanel(new BorderLayout());
        center.setBorder(BorderFactory.createTitledBorder("Gantt Chart"));
        center.add(ganttPanel, BorderLayout.CENTER);

        getContentPane().setLayout(new BorderLayout(8,8));
        getContentPane().add(topPanel, BorderLayout.NORTH);
        getContentPane().add(center, BorderLayout.CENTER);

        fillDefault();
    }

    private void addRow() {
        procTableModel.addRow(new Object[]{nextPid++, 0, 1, 1});
    }

    private void removeSelectedRows() {
        int[] rows = procTable.getSelectedRows();
        if (rows.length == 0) {
            JOptionPane.showMessageDialog(this, "Select at least one row to remove.", "No selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Arrays.sort(rows);
        for (int i = rows.length - 1; i >= 0; i--) {
            procTableModel.removeRow(rows[i]);
        }
    }

    private void fillDefault() {
        procTableModel.setRowCount(0);
        procTableModel.addRow(new Object[]{nextPid++, 0, 5, 2});
        procTableModel.addRow(new Object[]{nextPid++, 1, 3, 1});
        procTableModel.addRow(new Object[]{nextPid++, 2, 8, 3});
        procTableModel.addRow(new Object[]{nextPid++, 3, 6, 2});
    }

    private void fillSample() {
        procTableModel.setRowCount(0);
        nextPid = 1;
        procTableModel.addRow(new Object[]{nextPid++, 0, 4, 2});
        procTableModel.addRow(new Object[]{nextPid++, 1, 3, 1});
        procTableModel.addRow(new Object[]{nextPid++, 2, 1, 4});
        procTableModel.addRow(new Object[]{nextPid++, 3, 2, 2});
        procTableModel.addRow(new Object[]{nextPid++, 5, 4, 1});
    }

    private void clearAll() {
        procTableModel.setRowCount(0);
        resultTableModel.setRowCount(0);
        ganttPanel.setSegments(Collections.emptyList(), 0);
        avgLabel.setText("Average Waiting Time: -    Average Turnaround Time: -");
        nextPid = 1;
    }

    private void runScheduler() {
        int rows = procTableModel.getRowCount();
        if (rows == 0) {
            JOptionPane.showMessageDialog(this, "No processes defined. Add some rows or click Auto Fill Sample.", "No processes", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<Proc> procs = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            try {
                int pid = Integer.parseInt(procTableModel.getValueAt(i, 0).toString());
                int arrival = Integer.parseInt(procTableModel.getValueAt(i, 1).toString());
                int burst = Integer.parseInt(procTableModel.getValueAt(i, 2).toString());
                int prio = Integer.parseInt(procTableModel.getValueAt(i, 3).toString());
                if (arrival < 0 || burst <= 0) {
                    JOptionPane.showMessageDialog(this, "Arrival must be >= 0 and Burst must be > 0.", "Invalid input", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                procs.add(new Proc(pid, arrival, burst, prio));
            } catch (NumberFormatException nfe) {
                JOptionPane.showMessageDialog(this, "Please ensure Arrival, Burst and Priority are integer values.", "Invalid input", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        SchedulingResult result = simulatePreemptivePriority(procs);

        resultTableModel.setRowCount(0);
        double totWait = 0;
        double totTurn = 0;
        List<Proc> sorted = new ArrayList<>(result.finalProcs);
        sorted.sort(Comparator.comparingInt(p -> p.pid));
        for (Proc p : sorted) {
            resultTableModel.addRow(new Object[]{p.pid, p.arrival, p.burstOriginal, p.priority, p.waitingTime, p.turnaroundTime});
            totWait += p.waitingTime;
            totTurn += p.turnaroundTime;
        }
        double avgWait = totWait / sorted.size();
        double avgTurn = totTurn / sorted.size();
        avgLabel.setText(String.format("Average Waiting Time: %.2f    Average Turnaround Time: %.2f", avgWait, avgTurn));

        ganttPanel.setSegments(result.segments, result.totalTime);
        ganttPanel.repaint();
    }

    private SchedulingResult simulatePreemptivePriority(List<Proc> inputProcs) {
        List<Proc> procs = new ArrayList<>();
        for (Proc p : inputProcs) procs.add(new Proc(p.pid, p.arrival, p.burstOriginal, p.priority));

        procs.sort(Comparator.comparingInt(a -> a.arrival));

        int time = 0;
        int completed = 0;
        int n = procs.size();

        Map<Integer, Proc> pidMap = new HashMap<>();
        for (Proc p : procs) pidMap.put(p.pid, p);

        List<GanttSegment> segments = new ArrayList<>();

        Proc current = null;
        int segStart = 0;

        while (completed < n) {
            List<Proc> ready = new ArrayList<>();
            for (Proc p : procs) {
                if (p.arrival <= time && p.remaining > 0) ready.add(p);
            }

            Proc toRun = null;
            if (!ready.isEmpty()) {
                toRun = ready.stream()
                        .min(Comparator.comparingInt((Proc p) -> p.priority)
                                .thenComparingInt(p -> p.arrival)
                                .thenComparingInt(p -> p.pid))
                        .get();
            }

            if (toRun == null) {
                if (current == null || current.pid != -1) {
                    if (current != null) {
                        segments.add(new GanttSegment(current.pid, segStart, time));
                    }
                    current = new Proc(-1, time, 0, Integer.MAX_VALUE); 
                    segStart = time;
                }
                time++;
                continue;
            } else {
                if (current == null || current.pid != toRun.pid) {
                    if (current != null) {
                        segments.add(new GanttSegment(current.pid, segStart, time));
                    }
                    current = toRun;
                    segStart = time;
                }
                toRun.remaining -= 1;
                time++;
                if (toRun.remaining == 0) {
                    toRun.completion = time;
                    completed++;
                }
            }
        }


        if (current != null) {
            segments.add(new GanttSegment(current.pid, segStart, time));
        }


        List<Proc> finalProcs = new ArrayList<>();
        for (Proc p : procs) {
            p.turnaroundTime = p.completion - p.arrival;
            p.waitingTime = p.turnaroundTime - p.burstOriginal;
            finalProcs.add(p);
        }

        return new SchedulingResult(segments, finalProcs, time);
    }


    private static class Proc {
        int pid;
        int arrival;
        int burstOriginal;
        int remaining;
        int priority;
        int completion = 0;
        int waitingTime = 0;
        int turnaroundTime = 0;

        Proc(int pid, int arrival, int burst, int priority) {
            this.pid = pid;
            this.arrival = arrival;
            this.burstOriginal = burst;
            this.remaining = burst;
            this.priority = priority;
        }

        @Override
        public String toString() {
            return String.format("P%d(a=%d,b=%d,pr=%d,rem=%d)", pid, arrival, burstOriginal, priority, remaining);
        }
    }

    private static class GanttSegment {
        int pid;
        int start;
        int end;
        Color color; 

        GanttSegment(int pid, int start, int end) {
            this.pid = pid;
            this.start = start;
            this.end = end;
        }

        int length() {
            return end - start;
        }
    }

    private static class SchedulingResult {
        List<GanttSegment> segments;
        List<Proc> finalProcs;
        int totalTime;

        SchedulingResult(List<GanttSegment> segments, List<Proc> finalProcs, int totalTime) {
            this.segments = segments;
            this.finalProcs = finalProcs;
            this.totalTime = totalTime;
        }
    }

    private static class GanttPanel extends JPanel {
        private List<GanttSegment> segments = new ArrayList<>();
        private int totalTime = 0;

        void setSegments(List<GanttSegment> segs, int totalTime) {
            this.segments = new ArrayList<>();
            Map<Integer, Color> colorMap = new HashMap<>();
            for (GanttSegment s : segs) {
                if (!colorMap.containsKey(s.pid)) {
                    colorMap.put(s.pid, colorForPid(s.pid));
                }
                GanttSegment copy = new GanttSegment(s.pid, s.start, s.end);
                copy.color = colorMap.get(s.pid);
                this.segments.add(copy);
            }
            this.totalTime = Math.max(1, totalTime); 
        }

        private static Color colorForPid(int pid) {
            if (pid == -1) return Color.LIGHT_GRAY;
            float hue = (pid * 0.17f) % 1.0f;
            return Color.getHSBColor(hue, 0.6f, 0.85f);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            g2.setColor(Color.WHITE);
            g2.fillRect(0,0,w,h);

            int marginLeft = 40;
            int marginRight = 20;
            int marginTop = 20;
            int marginBottom = 40;

            int chartX = marginLeft;
            int chartY = marginTop;
            int chartW = w - marginLeft - marginRight;
            int chartH = h - marginTop - marginBottom;

            g2.setColor(Color.DARK_GRAY);
            g2.drawRect(chartX, chartY, chartW, chartH);

            if (segments == null || segments.isEmpty()) {
                g2.setColor(Color.DARK_GRAY);
                g2.drawString("No schedule to display. Click Run.", chartX + 10, chartY + 20);
                g2.dispose();
                return;
            }

            double pxPerTime = (double) chartW / (double) totalTime;

            for (GanttSegment s : segments) {
                int sx = chartX + (int) Math.round(s.start * pxPerTime);
                int ex = chartX + (int) Math.round(s.end * pxPerTime);
                int segW = Math.max(1, ex - sx);

                Color fill = s.color != null ? s.color : colorForPid(s.pid);
                g2.setColor(fill);
                g2.fillRect(sx, chartY+1, segW, chartH-1);
                g2.setColor(Color.BLACK);
                g2.drawRect(sx, chartY+1, segW, chartH-1);

                String label = (s.pid == -1) ? "IDLE" : "P" + s.pid;
                FontMetrics fm = g2.getFontMetrics();
                int textW = fm.stringWidth(label);
                int textX = sx + Math.max(2, (segW - textW)/2);
                int textY = chartY + (chartH + fm.getAscent())/2 - 4;
                if (segW < textW + 6) {

                    String shortLabel = label;
                    if (segW < 20) shortLabel = label.substring(0, Math.min(3, label.length()));
                    g2.setColor(Color.BLACK);
                    g2.drawString(shortLabel, sx + 2, textY);
                } else {
                    g2.setColor(Color.BLACK);
                    g2.drawString(label, textX, textY);
                }
            }

            g2.setColor(Color.BLACK);
            for (int t = 0; t <= totalTime; t++) {
                int x = chartX + (int) Math.round(t * pxPerTime);
                int y1 = chartY + chartH;
                int y2 = y1 + 6;
                g2.drawLine(x, y1, x, y2);
                String ts = Integer.toString(t);
                FontMetrics fm = g2.getFontMetrics();
                int tx = x - fm.stringWidth(ts)/2;
                g2.drawString(ts, tx, y2 + fm.getAscent()+2);
            }

            int lx = chartX;
            int ly = chartY + chartH + 30;
            int legendItemW = 110;
            Set<Integer> added = new LinkedHashSet<>();
            for (GanttSegment s : segments) {
                if (added.contains(s.pid)) continue;
                added.add(s.pid);
            }
            int idx = 0;
            for (Integer pid : added) {
                int px = lx + (idx % 6) * legendItemW;
                int py = ly + (idx / 6) * 20;
                Color c = colorForPid(pid);
                g2.setColor(c);
                g2.fillRect(px, py - 12, 12, 12);
                g2.setColor(Color.BLACK);
                String txt = (pid == -1) ? "IDLE" : "P" + pid;
                g2.drawString(txt, px + 18, py - 2);
                idx++;
            }

            g2.dispose();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PreemptivePriorityScheduler app = new PreemptivePriorityScheduler();
            app.setVisible(true);
        });
    }
}
