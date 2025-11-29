import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;

public class PreemptivePriorityScheduler extends JFrame {
    private static final int MIN_PROCS = 2;
    private static final int MAX_PROCS = 15;

    private DefaultTableModel procTableModel;
    private JTable procTable;
    private DefaultTableModel resultTableModel;
    private JTable resultTable;
    private GanttPanel ganttPanel;
    private JLabel avgLabel;

    private JButton addBtn;
    private JButton removeBtn;
    private JButton sampleBtn;
    private JButton clearBtn;
    private JButton runBtn;

    private int nextPid = 1;

    public PreemptivePriorityScheduler() {
        super("Preemptive Priority Scheduler (Single-file)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 650);
        setLocationRelativeTo(null);
        initUI();
        setVisible(true);
    }

    private void initUI() {
        procTableModel = new DefaultTableModel(new Object[]{"PID", "Arrival", "Burst", "Priority"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column != 0;
            }
        };

        procTableModel.addTableModelListener(ev -> updateControls());

        procTable = new JTable(procTableModel);
        procTable.setFillsViewportHeight(true);
        procTable.getColumnModel().getColumn(0).setMaxWidth(60);

        JScrollPane procScroll = new JScrollPane(procTable);
        procScroll.setPreferredSize(new Dimension(420, 200));

        addBtn = new JButton("Add Row");
        removeBtn = new JButton("Remove Row");
        sampleBtn = new JButton("Auto Fill Sample");
        clearBtn = new JButton("Clear");
        runBtn = new JButton("Run Scheduler");

        addBtn.addActionListener(e -> addRow());
        removeBtn.addActionListener(e -> removeSelectedRows());
        clearBtn.addActionListener(e -> clearAll());
        sampleBtn.addActionListener(e -> { fillSample(); updateControls(); });
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
        updateControls();
    }

    private void updateControls() {
        int rows = procTableModel.getRowCount();
        addBtn.setEnabled(rows < MAX_PROCS);
        runBtn.setEnabled(rows >= MIN_PROCS && rows <= MAX_PROCS);
        removeBtn.setEnabled(rows > 0);
    }

    private void addRow() {
        int rows = procTableModel.getRowCount();
        if (rows >= MAX_PROCS) {
            JOptionPane.showMessageDialog(this,
                    "Cannot add more than " + MAX_PROCS + " processes.",
                    "Process Limit Reached",
                    JOptionPane.WARNING_MESSAGE);
            addBtn.setEnabled(false);
            return;
        }
        procTableModel.addRow(new Object[]{nextPid++, 0, 1, 1});
        updateControls();
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
        updateControls();
    }

    private void fillDefault() {
        procTableModel.setRowCount(0);
        nextPid = 1;
        procTableModel.addRow(new Object[]{nextPid++, 0, 5, 2});
        procTableModel.addRow(new Object[]{nextPid++, 1, 3, 1});
        procTableModel.addRow(new Object[]{nextPid++, 2, 8, 3});
        procTableModel.addRow(new Object[]{nextPid++, 3, 6, 2});
        updateControls();
    }

    private void fillSample() {
        procTableModel.setRowCount(0);
        nextPid = 1;
        Object[][] sample = {
                {nextPid++, 0, 4, 2},
                {nextPid++, 1, 3, 1},
                {nextPid++, 2, 1, 4},
                {nextPid++, 3, 2, 2},
                {nextPid++, 5, 4, 1}
        };
        for (Object[] r : sample) {
            if (procTableModel.getRowCount() >= MAX_PROCS) break;
            procTableModel.addRow(r);
        }
        updateControls();
    }

    private void clearAll() {
        procTableModel.setRowCount(0);
        resultTableModel.setRowCount(0);
        ganttPanel.setSegments(Collections.emptyList(), 0);
        avgLabel.setText("Average Waiting Time: -    Average Turnaround Time: -");
        nextPid = 1;
        updateControls();
    }

    private void runScheduler() {
        int rows = procTableModel.getRowCount();

        if (rows < MIN_PROCS || rows > MAX_PROCS) {
            JOptionPane.showMessageDialog(this,
                    "The scheduler requires at least " + MIN_PROCS + " processes and a maximum of " + MAX_PROCS + " processes.",
                    "Invalid Number of Processes",
                    JOptionPane.ERROR_MESSAGE);
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

        Collections.sort(sorted, (a, b) -> Integer.compare(a.pid, b.pid));
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

        Collections.sort(procs, Comparator.comparingInt(a -> a.arrival));

        int time = 0;
        int completed = 0;
        int n = procs.size();

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
                toRun = Collections.min(ready, (a, b) -> {
                    int cmp = Integer.compare(a.priority, b.priority);
                    if (cmp != 0) return cmp;
                    cmp = Integer.compare(a.arrival, b.arrival);
                    if (cmp != 0) return cmp;
                    return Integer.compare(a.pid, b.pid);
                });
            }

            if (toRun == null) {
                if (current == null || current.pid != -1) {
                    if (current != null) segments.add(new GanttSegment(current.pid, segStart, time));
                    current = new Proc(-1, time, 0, Integer.MAX_VALUE);
                    segStart = time;
                }
                time++;
            } else {
                if (current == null || current.pid != toRun.pid) {
                    if (current != null) segments.add(new GanttSegment(current.pid, segStart, time));
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

        if (current != null) segments.add(new GanttSegment(current.pid, segStart, time));

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
                if (!colorMap.containsKey(s.pid)) colorMap.put(s.pid, colorForPid(s.pid));
                GanttSegment copy = new GanttSegment(s.pid, s.start, s.end);
                copy.color = colorMap.get(s.pid);
                this.segments.add(copy);
            }
            this.totalTime = Math.max(1, totalTime);
            setPreferredSize(new Dimension(Math.max(800, this.totalTime * 20), 280));
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

            g2.dispose();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PreemptivePriorityScheduler());
    }
}
