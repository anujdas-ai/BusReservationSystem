import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/* ============================================================================
   TIER 1: DATABASE PERSISTENCE LAYER (Storage & Repository)
   ============================================================================ */

interface IDatabaseRepository {
    void saveUser(User user);
    User findUserByUsername(String username);
    List<User> getAllUsers();

    void saveBus(Bus bus);
    Bus findBusById(String busId);
    List<Bus> getAllBuses();

    void saveRoute(Route route);
    Route findRouteById(String routeId);
    List<Route> getAllRoutes();

    void saveTicket(Ticket ticket);
    Ticket findTicketById(String ticketId);
    List<Ticket> getTicketsByPassenger(String passengerUsername);
    List<Ticket> getAllTickets();

    void syncToDisk();
    void loadFromDisk();
}

class FileDatabase implements IDatabaseRepository, Serializable {
    private static final long serialVersionUID = 1L;
    private static FileDatabase instance;
    private static final String STORAGE_FILE = "bharat_yatra_graphic_db.dat";

    private final Map<String, User> userTable = new ConcurrentHashMap<>();
    private final Map<String, Bus> busTable = new ConcurrentHashMap<>();
    private final Map<String, Route> routeTable = new ConcurrentHashMap<>();
    private final Map<String, Ticket> ticketTable = new ConcurrentHashMap<>();

    private FileDatabase() {}

    public static synchronized FileDatabase getInstance() {
        if (instance == null) {
            instance = new FileDatabase();
            instance.loadFromDisk();
        }
        return instance;
    }

    @Override
    public void saveUser(User user) {
        userTable.put(user.getUsername(), user);
        syncToDisk();
    }

    @Override
    public User findUserByUsername(String username) {
        return userTable.get(username);
    }

    @Override
    public List<User> getAllUsers() {
        return new ArrayList<>(userTable.values());
    }

    @Override
    public void saveBus(Bus bus) {
        busTable.put(bus.getBusId(), bus);
        syncToDisk();
    }

    @Override
    public Bus findBusById(String busId) {
        return busTable.get(busId);
    }

    @Override
    public List<Bus> getAllBuses() {
        return new ArrayList<>(busTable.values());
    }

    @Override
    public void saveRoute(Route route) {
        routeTable.put(route.getRouteId(), route);
        syncToDisk();
    }

    @Override
    public Route findRouteById(String routeId) {
        return routeTable.get(routeId);
    }

    @Override
    public List<Route> getAllRoutes() {
        return new ArrayList<>(routeTable.values());
    }

    @Override
    public void saveTicket(Ticket ticket) {
        ticketTable.put(ticket.getPnrNumber(), ticket);
        syncToDisk();
    }

    @Override
    public Ticket findTicketById(String ticketId) {
        return ticketTable.get(ticketId);
    }

    @Override
    public List<Ticket> getTicketsByPassenger(String passengerUsername) {
        List<Ticket> list = new ArrayList<>();
        for (Ticket t : ticketTable.values()) {
            if (t.getPassengerUsername().equalsIgnoreCase(passengerUsername)) {
                list.add(t);
            }
        }
        return list;
    }

    @Override
    public List<Ticket> getAllTickets() {
        return new ArrayList<>(ticketTable.values());
    }

    @Override
    public synchronized void syncToDisk() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(STORAGE_FILE))) {
            oos.writeObject(userTable);
            oos.writeObject(busTable);
            oos.writeObject(routeTable);
            oos.writeObject(ticketTable);
        } catch (IOException ignored) {}
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized void loadFromDisk() {
        File file = new File(STORAGE_FILE);
        if (!file.exists()) return;

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Map<String, User> u = (Map<String, User>) ois.readObject();
            Map<String, Bus> b = (Map<String, Bus>) ois.readObject();
            Map<String, Route> r = (Map<String, Route>) ois.readObject();
            Map<String, Ticket> t = (Map<String, Ticket>) ois.readObject();

            userTable.putAll(u);
            busTable.putAll(b);
            routeTable.putAll(r);
            ticketTable.putAll(t);
        } catch (Exception ignored) {}
    }
}

/* ============================================================================
   TIER 2: BACKEND DOMAIN MODELS & OOP HIERARCHY
   ============================================================================ */

abstract class User implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String userId;
    private final String username;
    private final String password;
    private final String fullName;

    public User(String userId, String username, String password, String fullName) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
    }

    public String getUsername() { return username; }
    public boolean authenticate(String pwd) { return this.password.equals(pwd); }
    public String getFullName() { return fullName; }
    public abstract String getRole();
}

class Passenger extends User {
    private final String mobileNumber;

    public Passenger(String userId, String username, String password, String fullName, String mobileNumber) {
        super(userId, username, password, fullName);
        this.mobileNumber = mobileNumber;
    }

    public String getMobileNumber() { return mobileNumber; }
    @Override public String getRole() { return "PASSENGER"; }
}

class Admin extends User {
    private final String designation;

    public Admin(String userId, String username, String password, String fullName, String designation) {
        super(userId, username, password, fullName);
        this.designation = designation;
    }

    public String getDesignation() { return designation; }
    @Override public String getRole() { return "DEPOT CONTROLLER"; }
}

class Seat implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int seatNumber;
    private boolean isBooked;
    private String passengerUsername;
    private final transient ReentrantLock lock = new ReentrantLock();

    public Seat(int seatNumber) {
        this.seatNumber = seatNumber;
        this.isBooked = false;
        this.passengerUsername = null;
    }

    public int getSeatNumber() { return seatNumber; }
    public synchronized boolean isBooked() { return isBooked; }

    public boolean reserve(String username) {
        ReentrantLock activeLock = (lock != null) ? lock : new ReentrantLock();
        activeLock.lock();
        try {
            if (isBooked) return false;
            this.isBooked = true;
            this.passengerUsername = username;
            return true;
        } finally {
            activeLock.unlock();
        }
    }

    public synchronized void cancel() {
        this.isBooked = false;
        this.passengerUsername = null;
    }
}

class Route implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String routeId;
    private final String origin;
    private final String destination;
    private final double distanceKm;
    private final String departureTime;
    private final String arrivalTime;
    private final String boardingPlatform;

    public Route(String routeId, String origin, String destination, double distanceKm, 
                 String departureTime, String arrivalTime, String boardingPlatform) {
        this.routeId = routeId;
        this.origin = origin;
        this.destination = destination;
        this.distanceKm = distanceKm;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.boardingPlatform = boardingPlatform;
    }

    public String getRouteId() { return routeId; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public double getDistanceKm() { return distanceKm; }
    public String getDepartureTime() { return departureTime; }
    public String getArrivalTime() { return arrivalTime; }
    public String getBoardingPlatform() { return boardingPlatform; }
}

abstract class Bus implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String busId;
    private final String registrationNumber;
    private final Route route;
    private final Map<Integer, Seat> seats = new HashMap<>();

    public Bus(String busId, String registrationNumber, Route route, int totalSeats) {
        this.busId = busId;
        this.registrationNumber = registrationNumber;
        this.route = route;
        for (int i = 1; i <= totalSeats; i++) {
            seats.put(i, new Seat(i));
        }
    }

    public String getBusId() { return busId; }
    public String getRegistrationNumber() { return registrationNumber; }
    public Route getRoute() { return route; }
    public Map<Integer, Seat> getSeats() { return seats; }
    public abstract String getBusServiceType();
    public abstract Color getThemeColor();
    public abstract double calculateBaseFare();

    public double calculateTotalFareWithGst() {
        return Math.round(calculateBaseFare() * 1.05); // 5% GST included
    }

    public synchronized int getAvailableSeatCount() {
        int count = 0;
        for (Seat s : seats.values()) {
            if (!s.isBooked()) count++;
        }
        return count;
    }
}

class SuperFastBus extends Bus {
    public SuperFastBus(String busId, String registrationNumber, Route route, int totalSeats) {
        super(busId, registrationNumber, route, totalSeats);
    }
    @Override public String getBusServiceType() { return "KSRTC Super Fast Express (Non-AC)"; }
    @Override public Color getThemeColor() { return new Color(214, 48, 49); } // Indian Red
    @Override public double calculateBaseFare() { return Math.round(getRoute().getDistanceKm() * 1.25 + 25.0); }
}

class GarudaAiravatAcBus extends Bus {
    public GarudaAiravatAcBus(String busId, String registrationNumber, Route route, int totalSeats) {
        super(busId, registrationNumber, route, totalSeats);
    }
    @Override public String getBusServiceType() { return "Airavat Club Class Multi-Axle Volvo"; }
    @Override public Color getThemeColor() { return new Color(9, 132, 227); } // Sky Blue
    @Override public double calculateBaseFare() { return Math.round((getRoute().getDistanceKm() * 2.15) + 120.0); }
}

class SwiftSleeperBus extends Bus {
    public SwiftSleeperBus(String busId, String registrationNumber, Route route, int totalSeats) {
        super(busId, registrationNumber, route, totalSeats);
    }
    @Override public String getBusServiceType() { return "KSRTC-SWIFT Gajaraj AC Sleeper"; }
    @Override public Color getThemeColor() { return new Color(108, 92, 231); } // Royal Purple
    @Override public double calculateBaseFare() { return Math.round((getRoute().getDistanceKm() * 2.65) + 200.0); }
}

class Ticket implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String pnrNumber;
    private final String busId;
    private final String passengerUsername;
    private final String passengerName;
    private final String passengerMobile;
    private final int seatNumber;
    private final double fare;
    private final String bookingDate;
    private boolean active;

    public Ticket(String pnrNumber, String busId, String passengerUsername, 
                  String passengerName, String passengerMobile, int seatNumber, double fare) {
        this.pnrNumber = pnrNumber;
        this.busId = busId;
        this.passengerUsername = passengerUsername;
        this.passengerName = passengerName;
        this.passengerMobile = passengerMobile;
        this.seatNumber = seatNumber;
        this.fare = fare;
        this.bookingDate = new SimpleDateFormat("dd-MMM-yyyy hh:mm a").format(new Date());
        this.active = true;
    }

    public String getPnrNumber() { return pnrNumber; }
    public String getBusId() { return busId; }
    public String getPassengerUsername() { return passengerUsername; }
    public String getPassengerName() { return passengerName; }
    public String getPassengerMobile() { return passengerMobile; }
    public int getSeatNumber() { return seatNumber; }
    public double getFare() { return fare; }
    public String getBookingDate() { return bookingDate; }
    public boolean isActive() { return active; }
    public void cancel() { this.active = false; }
}

/* ============================================================================
   TIER 2 (CONT): BACKEND BUSINESS SERVICE LOGIC
   ============================================================================ */

interface IReservationService {
    Ticket bookSeat(String busId, int seatNumber, User user) throws Exception;
    boolean cancelTicket(String pnrNumber) throws Exception;
}

class ReservationServiceImpl implements IReservationService {
    private final IDatabaseRepository db = FileDatabase.getInstance();

    @Override
    public synchronized Ticket bookSeat(String busId, int seatNumber, User user) throws Exception {
        Bus bus = db.findBusById(busId);
        if (bus == null) throw new IllegalArgumentException("Bus schedule not found.");

        Seat seat = bus.getSeats().get(seatNumber);
        if (seat == null) throw new IllegalArgumentException("Invalid Seat Number specified.");

        if (!seat.reserve(user.getUsername())) {
            throw new IllegalStateException("Seat #" + seatNumber + " is already occupied!");
        }

        String pnr = "KL" + (1000000 + new Random().nextInt(9000000));
        double totalFare = bus.calculateTotalFareWithGst();
        String mobile = (user instanceof Passenger) ? ((Passenger) user).getMobileNumber() : "Depot Reservation Counter";

        Ticket ticket = new Ticket(pnr, busId, user.getUsername(), user.getFullName(), mobile, seatNumber, totalFare);

        db.saveTicket(ticket);
        db.saveBus(bus);
        return ticket;
    }

    @Override
    public synchronized boolean cancelTicket(String pnrNumber) throws Exception {
        Ticket ticket = db.findTicketById(pnrNumber);
        if (ticket == null || !ticket.isActive()) return false;

        Bus bus = db.findBusById(ticket.getBusId());
        if (bus != null) {
            Seat seat = bus.getSeats().get(ticket.getSeatNumber());
            if (seat != null) seat.cancel();
            db.saveBus(bus);
        }

        ticket.cancel();
        db.saveTicket(ticket);
        return true;
    }
}

/* ============================================================================
   TIER 3: PRESENTATION LAYER (RICH GRAPHICAL SWING UI)
   ============================================================================ */

class ModernButton extends JButton {
    private Color normalColor;
    private Color hoverColor;

    public ModernButton(String text, Color normalColor, Color hoverColor) {
        super(text);
        this.normalColor = normalColor;
        this.hoverColor = hoverColor;
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setForeground(Color.WHITE);
        setFont(new Font("Segoe UI", Font.BOLD, 13));
        setCursor(new Cursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) { setBackground(hoverColor); repaint(); }
            @Override
            public void mouseExited(MouseEvent e) { setBackground(normalColor); repaint(); }
        });
        setBackground(normalColor);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(getBackground());
        g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 12, 12));
        g2.dispose();
        super.paintComponent(g);
    }
}

// Graphical Bus Coach Blueprint Canvas
class BusCoachCanvas extends JPanel {
    private final Bus bus;
    private final int selectedSeatNumber;
    private final java.util.function.Consumer<Integer> onSeatSelected;
    private final Map<Integer, Rectangle> seatHitBoxes = new HashMap<>();

    public BusCoachCanvas(Bus bus, int selectedSeatNumber, java.util.function.Consumer<Integer> onSeatSelected) {
        this.bus = bus;
        this.selectedSeatNumber = selectedSeatNumber;
        this.onSeatSelected = onSeatSelected;
        setBackground(new Color(241, 243, 248));
        setPreferredSize(new Dimension(820, 520));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                for (Map.Entry<Integer, Rectangle> entry : seatHitBoxes.entrySet()) {
                    if (entry.getValue().contains(e.getPoint())) {
                        Seat s = bus.getSeats().get(entry.getKey());
                        if (s != null && !s.isBooked()) {
                            onSeatSelected.accept(entry.getKey());
                        }
                        break;
                    }
                }
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int busX = 40;
        int busY = 30;
        int busWidth = 730;
        int busHeight = 440;

        // Shadow under bus body
        g2.setColor(new Color(0, 0, 0, 35));
        g2.fill(new RoundRectangle2D.Double(busX + 6, busY + 8, busWidth, busHeight, 40, 40));

        // Outer Bus Frame (Metallic Gradient Body)
        GradientPaint bodyGrad = new GradientPaint(
            busX, busY, new Color(255, 255, 255),
            busX, busY + busHeight, new Color(230, 235, 245)
        );
        g2.setPaint(bodyGrad);
        g2.fill(new RoundRectangle2D.Double(busX, busY, busWidth, busHeight, 40, 40));

        // Outline colored by bus brand theme
        g2.setColor(bus.getThemeColor());
        g2.setStroke(new BasicStroke(4f));
        g2.draw(new RoundRectangle2D.Double(busX, busY, busWidth, busHeight, 40, 40));

        // Front Cockpit / Windshield (Front on Left)
        g2.setColor(new Color(41, 128, 185, 90));
        g2.fill(new RoundRectangle2D.Double(busX + 15, busY + 20, 95, busHeight - 40, 25, 25));
        g2.setColor(new Color(52, 73, 94));
        g2.setStroke(new BasicStroke(2f));
        g2.draw(new RoundRectangle2D.Double(busX + 15, busY + 20, 95, busHeight - 40, 25, 25));

        // Cockpit Label & Steering Icon
        g2.setColor(new Color(44, 62, 80));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g2.drawString("DRIVER CABIN", busX + 18, busY + 45);

        // Steering Wheel Graphic
        int steerX = busX + 45;
        int steerY = busY + 70;
        g2.setColor(new Color(50, 50, 50));
        g2.fillOval(steerX, steerY, 36, 36);
        g2.setColor(Color.WHITE);
        g2.fillOval(steerX + 8, steerY + 8, 20, 20);
        g2.setColor(new Color(50, 50, 50));
        g2.fillOval(steerX + 14, steerY + 14, 8, 8);

        // Front Headlights
        g2.setColor(new Color(241, 196, 15));
        g2.fillRoundRect(busX - 5, busY + 35, 8, 25, 5, 5);
        g2.fillRoundRect(busX - 5, busY + busHeight - 60, 8, 25, 5, 5);

        // Rear Tail Lamps
        g2.setColor(new Color(231, 76, 60));
        g2.fillRoundRect(busX + busWidth - 3, busY + 40, 8, 25, 5, 5);
        g2.fillRoundRect(busX + busWidth - 3, busY + busHeight - 65, 8, 25, 5, 5);

        // Central Aisle Carpet
        int aisleY = busY + (busHeight / 2) - 26;
        g2.setColor(new Color(220, 224, 230));
        g2.fillRect(busX + 125, aisleY, busWidth - 145, 52);
        g2.setColor(new Color(189, 195, 199));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
        g2.drawString("< < CENTRAL WALKING AISLE > >", busX + 280, aisleY + 31);

        // Draw 2x2 Seating Rows
        seatHitBoxes.clear();
        int totalSeats = bus.getSeats().size();
        int cols = (int) Math.ceil((double) totalSeats / 4.0);

        int startX = busX + 135;
        int spacingX = Math.min(58, (busWidth - 160) / Math.max(1, cols));

        int seatW = 46;
        int seatH = 40;

        int row1Y = busY + 35;
        int row2Y = busY + 85;
        int row3Y = aisleY + 60;
        int row4Y = aisleY + 110;

        int seatIdx = 1;
        for (int c = 0; c < cols; c++) {
            int cx = startX + (c * spacingX);

            drawSeatGraphic(g2, cx, row1Y, seatW, seatH, seatIdx++);
            if (seatIdx <= totalSeats) drawSeatGraphic(g2, cx, row2Y, seatW, seatH, seatIdx++);
            if (seatIdx <= totalSeats) drawSeatGraphic(g2, cx, row3Y, seatW, seatH, seatIdx++);
            if (seatIdx <= totalSeats) drawSeatGraphic(g2, cx, row4Y, seatW, seatH, seatIdx++);
        }

        g2.dispose();
    }

    private void drawSeatGraphic(Graphics2D g2, int x, int y, int w, int h, int seatNum) {
        Seat seat = bus.getSeats().get(seatNum);
        if (seat == null) return;

        Rectangle rect = new Rectangle(x, y, w, h);
        seatHitBoxes.put(seatNum, rect);

        Color mainColor;
        Color headrestColor;
        String statusText;

        if (seat.isBooked()) {
            mainColor = new Color(220, 53, 69); // Occupied Crimson
            headrestColor = new Color(176, 42, 55);
            statusText = "X";
        } else if (seatNum == selectedSeatNumber) {
            mainColor = new Color(40, 167, 69); // Selected Emerald
            headrestColor = new Color(30, 126, 52);
            statusText = "OK";
        } else {
            mainColor = new Color(23, 162, 184); // Available Cyan Blue
            headrestColor = new Color(17, 122, 139);
            statusText = String.valueOf(seatNum);
        }

        // Drop shadow for seat
        g2.setColor(new Color(0, 0, 0, 30));
        g2.fill(new RoundRectangle2D.Double(x + 2, y + 2, w, h, 10, 10));

        // Seat Main Body
        g2.setColor(mainColor);
        g2.fill(new RoundRectangle2D.Double(x, y, w, h, 10, 10));

        // Headrest Pillow Graphic
        g2.setColor(headrestColor);
        g2.fill(new RoundRectangle2D.Double(x + 6, y + 3, w - 12, 10, 6, 6));

        // Seat label text
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        FontMetrics fm = g2.getFontMetrics();
        int txtW = fm.stringWidth(statusText);
        g2.drawString(statusText, x + (w - txtW) / 2, y + 28);
    }
}

public class BusReservationGUI extends JFrame {
    private final IDatabaseRepository db = FileDatabase.getInstance();
    private final IReservationService service = new ReservationServiceImpl();
    private User currentUser = null;

    private JComboBox<String> busSelector;
    private JPanel coachContainerPanel;
    private JLabel statusLabel;
    private JLabel fareBreakdownLabel;
    private DefaultTableModel ticketTableModel;
    private int selectedSeatNumber = -1;

    public BusReservationGUI() {
        seedRealisticFleetData();
        initUI();
    }

    private void seedRealisticFleetData() {
        if (db.getAllUsers().isEmpty()) {
            db.saveUser(new Admin("ADM-01", "officer", "kerala@123", "P. S. Ramanathan", "Chief Depot Controller"));
            db.saveUser(new Passenger("PAS-01", "anjana", "anjana@123", "Anjana V. Nair", "+91 94470 12345"));
            db.saveUser(new Passenger("PAS-02", "rahul", "rahul@123", "Rahul Krishnan", "+91 98460 98765"));
        }

        if (db.getAllRoutes().isEmpty()) {
            Route r1 = new Route("RT-101", "Thiruvananthapuram (TVC)", "Ernakulam South (KSRTC)", 
                                 218.0, "06:00 AM", "11:15 AM", "Platform 3");
            Route r2 = new Route("RT-204", "Kozhikode Bus Stand", "Bengaluru (Shantinagar)", 
                                 354.0, "09:30 PM", "05:45 AM", "Platform 7");
            Route r3 = new Route("RT-308", "Ernakulam Vyttila Hub", "Chennai (CMBT / Koyambedu)", 
                                 684.0, "04:45 PM", "07:30 AM", "Platform 1");

            db.saveRoute(r1);
            db.saveRoute(r2);
            db.saveRoute(r3);

            db.saveBus(new SuperFastBus("KL-15-A-1102", "KL-15-A-1102", r1, 28));
            db.saveBus(new GarudaAiravatAcBus("KA-57-F-9090", "KA-57-F-9090", r2, 24));
            db.saveBus(new SwiftSleeperBus("SWIFT-KL-01-999", "KL-01-CZ-8888", r3, 20));
        }
    }

    private void initUI() {
        setTitle("BHARAT YATRA - State Road Transport Corporation Luxury ERP System");
        setSize(1160, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        if (!authenticateUserWithDialog()) {
            System.exit(0);
        }

        // Header Strip with Saffron / Navy Tricolor Vibe
        JPanel headerPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                GradientPaint gp = new GradientPaint(0, 0, new Color(15, 32, 67), getWidth(), 0, new Color(30, 80, 160));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());

                // Subtle Saffron Top Accent Line
                g2.setColor(new Color(255, 122, 0));
                g2.fillRect(0, 0, getWidth(), 4);
                g2.dispose();
            }
        };
        headerPanel.setPreferredSize(new Dimension(getWidth(), 70));
        headerPanel.setBorder(new EmptyBorder(12, 25, 12, 25));

        JLabel titleLbl = new JLabel("BHARAT LUXURY INTERCITY TRANSIT PORTAL");
        titleLbl.setForeground(Color.WHITE);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 18));

        JLabel userBadge = new JLabel("<html><body align='right'><font color='#FFD700'><b>" + 
            currentUser.getFullName() + "</b></font><br/><font color='#E0E0E0' size='2'>" + 
            currentUser.getRole() + "</font></body></html>");
        userBadge.setHorizontalAlignment(SwingConstants.RIGHT);

        headerPanel.add(titleLbl, BorderLayout.WEST);
        headerPanel.add(userBadge, BorderLayout.EAST);

        // Tabbed Panel
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabbedPane.addTab("  Interactive Bus Seating Map  ", createBookingPanel());
        tabbedPane.addTab("  My Confirmed Bookings  ", createMyBookingsPanel());

        if (currentUser instanceof Admin) {
            tabbedPane.addTab("  Depot Master Fleet Dispatch  ", createAdminPanel());
        }

        add(headerPanel, BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);
        setVisible(true);
    }

    private boolean authenticateUserWithDialog() {
        JDialog authDialog = new JDialog(this, "Bharat Yatra Portal Security Login", true);
        authDialog.setSize(520, 390);
        authDialog.setLocationRelativeTo(null);
        authDialog.setLayout(new BorderLayout());

        JTabbedPane authTabs = new JTabbedPane();
        authTabs.setFont(new Font("Segoe UI", Font.BOLD, 12));

        // Passenger Sign-in Tab
        JPanel passengerLoginPanel = new JPanel(new GridLayout(3, 2, 10, 12));
        passengerLoginPanel.setBorder(new EmptyBorder(25, 30, 25, 30));
        passengerLoginPanel.setBackground(new Color(248, 250, 252));
        JTextField pUser = new JTextField();
        JPasswordField pPass = new JPasswordField();
        passengerLoginPanel.add(new JLabel("Passenger Username:"));
        passengerLoginPanel.add(pUser);
        passengerLoginPanel.add(new JLabel("Password:"));
        passengerLoginPanel.add(pPass);

        ModernButton pLoginBtn = new ModernButton("Passenger Sign In", new Color(40, 167, 69), new Color(30, 135, 55));
        passengerLoginPanel.add(new JLabel(""));
        passengerLoginPanel.add(pLoginBtn);

        // Register Tab
        JPanel registerPanel = new JPanel(new GridLayout(5, 2, 8, 8));
        registerPanel.setBorder(new EmptyBorder(15, 25, 15, 25));
        registerPanel.setBackground(new Color(248, 250, 252));
        JTextField regName = new JTextField();
        JTextField regPhone = new JTextField();
        JTextField regUser = new JTextField();
        JPasswordField regPass = new JPasswordField();
        registerPanel.add(new JLabel("Full Name:"));
        registerPanel.add(regName);
        registerPanel.add(new JLabel("10-Digit Mobile:"));
        registerPanel.add(regPhone);
        registerPanel.add(new JLabel("Username:"));
        registerPanel.add(regUser);
        registerPanel.add(new JLabel("Password:"));
        registerPanel.add(regPass);

        ModernButton regBtn = new ModernButton("Create New Account", new Color(23, 162, 184), new Color(18, 130, 150));
        registerPanel.add(new JLabel(""));
        registerPanel.add(regBtn);

        // Depot Master Admin Tab
        JPanel adminLoginPanel = new JPanel(new GridLayout(3, 2, 10, 12));
        adminLoginPanel.setBorder(new EmptyBorder(25, 30, 25, 30));
        adminLoginPanel.setBackground(new Color(248, 250, 252));
        JTextField admUser = new JTextField();
        JPasswordField admPass = new JPasswordField();
        adminLoginPanel.add(new JLabel("Officer Employee ID:"));
        adminLoginPanel.add(admUser);
        adminLoginPanel.add(new JLabel("Security Password:"));
        adminLoginPanel.add(admPass);

        ModernButton admLoginBtn = new ModernButton("Officer Authorization", new Color(220, 53, 69), new Color(180, 40, 50));
        adminLoginPanel.add(new JLabel(""));
        adminLoginPanel.add(admLoginBtn);

        authTabs.addTab("Passenger Login", passengerLoginPanel);
        authTabs.addTab("Register Passenger", registerPanel);
        authTabs.addTab("Depot Officer Login", adminLoginPanel);

        final boolean[] success = {false};

        pLoginBtn.addActionListener(e -> {
            User u = db.findUserByUsername(pUser.getText().trim());
            if (u != null && u.authenticate(new String(pPass.getPassword()))) {
                currentUser = u;
                success[0] = true;
                authDialog.dispose();
            } else {
                JOptionPane.showMessageDialog(authDialog, "Invalid passenger credentials.", "Authentication Denied", JOptionPane.ERROR_MESSAGE);
            }
        });

        regBtn.addActionListener(e -> {
            String name = regName.getText().trim();
            String mobile = regPhone.getText().trim();
            String usr = regUser.getText().trim();
            String pwd = new String(regPass.getPassword()).trim();

            if (name.isEmpty() || mobile.length() < 10 || usr.isEmpty() || pwd.isEmpty()) {
                JOptionPane.showMessageDialog(authDialog, "Please enter all fields and a valid 10-digit mobile number.", "Validation Notice", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (db.findUserByUsername(usr) != null) {
                JOptionPane.showMessageDialog(authDialog, "Username is already registered.", "Notice", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Passenger newPas = new Passenger("PAS-" + UUID.randomUUID().toString().substring(0, 5), usr, pwd, name, "+91 " + mobile);
            db.saveUser(newPas);
            currentUser = newPas;
            success[0] = true;
            JOptionPane.showMessageDialog(authDialog, "Account successfully registered for " + name + "!");
            authDialog.dispose();
        });

        admLoginBtn.addActionListener(e -> {
            User u = db.findUserByUsername(admUser.getText().trim());
            if (u instanceof Admin && u.authenticate(new String(admPass.getPassword()))) {
                currentUser = u;
                success[0] = true;
                authDialog.dispose();
            } else {
                JOptionPane.showMessageDialog(authDialog, "Invalid officer credentials.", "Access Prohibited", JOptionPane.ERROR_MESSAGE);
            }
        });

        authDialog.add(authTabs, BorderLayout.CENTER);
        authDialog.setVisible(true);
        return success[0];
    }

    private JPanel createBookingPanel() {
        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBackground(new Color(245, 247, 250));
        root.setBorder(new EmptyBorder(16, 18, 16, 18));

        // Top Route Filter Bar
        JPanel top = new JPanel(new BorderLayout(10, 10));
        top.setBackground(Color.WHITE);
        top.setBorder(new CompoundBorder(new LineBorder(new Color(225, 230, 238), 1, true), new EmptyBorder(12, 16, 12, 16)));

        JLabel selectLbl = new JLabel("Available Routes & Services: ");
        selectLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        selectLbl.setForeground(new Color(33, 37, 41));

        busSelector = new JComboBox<>();
        busSelector.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        refreshBusSelector();
        busSelector.addActionListener(e -> renderCoachLayout());

        top.add(selectLbl, BorderLayout.WEST);
        top.add(busSelector, BorderLayout.CENTER);

        // Center Visual Canvas Wrapper
        coachContainerPanel = new JPanel(new BorderLayout());
        coachContainerPanel.setBackground(Color.WHITE);
        coachContainerPanel.setBorder(new CompoundBorder(
            new LineBorder(new Color(220, 226, 235), 1, true),
            new EmptyBorder(10, 10, 10, 10)
        ));

        // Legend panel (Available, Selected, Booked)
        JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 8));
        legendPanel.setOpaque(false);
        legendPanel.add(createLegendBadge("Seat Available (Click to select)", new Color(23, 162, 184)));
        legendPanel.add(createLegendBadge("Your Selected Seat", new Color(40, 167, 69)));
        legendPanel.add(createLegendBadge("Already Booked [X]", new Color(220, 53, 69)));

        JPanel centerWrapper = new JPanel(new BorderLayout());
        centerWrapper.setOpaque(false);
        centerWrapper.add(legendPanel, BorderLayout.NORTH);
        centerWrapper.add(coachContainerPanel, BorderLayout.CENTER);

        // Bottom Action & Fare Bar
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(Color.WHITE);
        bottom.setBorder(new CompoundBorder(new LineBorder(new Color(225, 230, 238), 1, true), new EmptyBorder(14, 18, 14, 18)));

        JPanel textSide = new JPanel(new GridLayout(2, 1, 0, 4));
        textSide.setOpaque(false);

        statusLabel = new JLabel("Selected Seat: None");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        statusLabel.setForeground(new Color(24, 43, 73));

        fareBreakdownLabel = new JLabel("Click on any blue seat above to allocate passenger berth.");
        fareBreakdownLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        fareBreakdownLabel.setForeground(new Color(108, 117, 125));

        textSide.add(statusLabel);
        textSide.add(fareBreakdownLabel);

        ModernButton bookBtn = new ModernButton("  Proceed to Reserve & Issue Boarding Pass  ", new Color(40, 167, 69), new Color(33, 136, 56));
        bookBtn.setPreferredSize(new Dimension(320, 44));
        bookBtn.addActionListener(e -> handleBooking());

        bottom.add(textSide, BorderLayout.CENTER);
        bottom.add(bookBtn, BorderLayout.EAST);

        root.add(top, BorderLayout.NORTH);
        root.add(centerWrapper, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);

        renderCoachLayout();
        return root;
    }

    private JPanel createLegendBadge(String label, Color color) {
        JPanel badge = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        badge.setOpaque(false);

        JPanel dot = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillRoundRect(0, 0, 18, 18, 6, 6);
            }
        };
        dot.setPreferredSize(new Dimension(18, 18));
        dot.setOpaque(false);

        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lbl.setForeground(new Color(73, 80, 87));

        badge.add(dot);
        badge.add(lbl);
        return badge;
    }

    private void refreshBusSelector() {
        busSelector.removeAllItems();
        for (Bus b : db.getAllBuses()) {
            busSelector.addItem(b.getBusId() + " | " + b.getBusServiceType() + " | " + 
                b.getRoute().getOrigin() + " -> " + b.getRoute().getDestination() + 
                " | Dept: " + b.getRoute().getDepartureTime() + " | Total: INR " + (int) b.calculateTotalFareWithGst());
        }
    }

    private void renderCoachLayout() {
        coachContainerPanel.removeAll();
        selectedSeatNumber = -1;
        statusLabel.setText("Selected Seat: None");
        fareBreakdownLabel.setText("Click on any blue seat above to allocate passenger berth.");

        String selectedItem = (String) busSelector.getSelectedItem();
        if (selectedItem == null) return;
        String busId = selectedItem.split(" \\| ")[0];
        Bus bus = db.findBusById(busId);
        if (bus == null) return;

        BusCoachCanvas canvas = new BusCoachCanvas(bus, selectedSeatNumber, seatNum -> {
            selectedSeatNumber = seatNum;
            statusLabel.setText("Selected Seat: #" + seatNum + " on " + bus.getRegistrationNumber());
            fareBreakdownLabel.setText("Base Fare: INR " + (int)bus.calculateBaseFare() + " + 5% GST = Total Payable: INR " + (int)bus.calculateTotalFareWithGst());
            renderCoachLayoutWithSelected(bus, seatNum);
        });

        coachContainerPanel.add(new JScrollPane(canvas), BorderLayout.CENTER);
        coachContainerPanel.revalidate();
        coachContainerPanel.repaint();
    }

    private void renderCoachLayoutWithSelected(Bus bus, int seatNum) {
        coachContainerPanel.removeAll();
        BusCoachCanvas canvas = new BusCoachCanvas(bus, seatNum, clicked -> {
            selectedSeatNumber = clicked;
            statusLabel.setText("Selected Seat: #" + clicked + " on " + bus.getRegistrationNumber());
            fareBreakdownLabel.setText("Base Fare: INR " + (int)bus.calculateBaseFare() + " + 5% GST = Total Payable: INR " + (int)bus.calculateTotalFareWithGst());
            renderCoachLayoutWithSelected(bus, clicked);
        });
        coachContainerPanel.add(new JScrollPane(canvas), BorderLayout.CENTER);
        coachContainerPanel.revalidate();
        coachContainerPanel.repaint();
    }

    private void handleBooking() {
        if (selectedSeatNumber == -1) {
            JOptionPane.showMessageDialog(this, "Please select an available blue seat on the bus blueprint first.", "Berth Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String busId = ((String) busSelector.getSelectedItem()).split(" \\| ")[0];
        Bus bus = db.findBusById(busId);
        if (bus == null) return;

        try {
            Ticket t = service.bookSeat(busId, selectedSeatNumber, currentUser);
            showDigitalBoardingPass(t, bus);
            renderCoachLayout();
            refreshMyBookings();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Reservation Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showDigitalBoardingPass(Ticket t, Bus bus) {
        JDialog passDialog = new JDialog(this, "Official Confirmed Boarding Pass", true);
        passDialog.setSize(580, 480);
        passDialog.setLocationRelativeTo(this);
        passDialog.setLayout(new BorderLayout());

        JPanel pass = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Card background with rounded edge
                g2.setColor(Color.WHITE);
                g2.fill(new RoundRectangle2D.Double(15, 15, getWidth() - 30, getHeight() - 30, 24, 24));

                // Header banner gradient
                GradientPaint banner = new GradientPaint(15, 15, new Color(15, 32, 67), getWidth() - 15, 15, new Color(30, 80, 160));
                g2.setPaint(banner);
                g2.fill(new RoundRectangle2D.Double(15, 15, getWidth() - 30, 75, 24, 24));
                g2.fillRect(15, 60, getWidth() - 30, 30); // flat bottom for banner

                // Gold Accent line
                g2.setColor(new Color(255, 193, 7));
                g2.fillRect(15, 87, getWidth() - 30, 3);

                // Border outline
                g2.setColor(new Color(220, 226, 235));
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(new RoundRectangle2D.Double(15, 15, getWidth() - 30, getHeight() - 30, 24, 24));
                g2.dispose();
            }
        };
        pass.setLayout(null);

        JLabel orgLbl = new JLabel("BHARAT LUXURY INTERCITY CO-OPERATIVE", SwingConstants.CENTER);
        orgLbl.setForeground(Color.WHITE);
        orgLbl.setFont(new Font("Segoe UI", Font.BOLD, 15));
        orgLbl.setBounds(25, 25, 510, 25);
        pass.add(orgLbl);

        JLabel subLbl = new JLabel("OFFICIAL ELECTRONIC BOARDING PASS (IRCTC/RTC COMPLIANT)", SwingConstants.CENTER);
        subLbl.setForeground(new Color(255, 215, 0));
        subLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        subLbl.setBounds(25, 52, 510, 20);
        pass.add(subLbl);

        int y = 110;
        addPassRow(pass, "PNR NUMBER:", t.getPnrNumber(), "DATE / TIME:", t.getBookingDate(), y);
        y += 40;
        addPassRow(pass, "PASSENGER:", t.getPassengerName(), "MOBILE NO:", t.getPassengerMobile(), y);
        y += 40;
        addPassRow(pass, "FROM / ORIGIN:", bus.getRoute().getOrigin(), "TO / DEST:", bus.getRoute().getDestination(), y);
        y += 40;
        addPassRow(pass, "BUS SERVICE:", bus.getBusServiceType(), "REGISTRATION:", bus.getRegistrationNumber(), y);
        y += 40;
        addPassRow(pass, "DEPARTURE:", bus.getRoute().getDepartureTime(), "PLATFORM:", bus.getRoute().getBoardingPlatform(), y);
        y += 40;

        // Seat & Fare Highlight Pill
        JPanel pill = new JPanel(new GridLayout(1, 2));
        pill.setBackground(new Color(235, 247, 238));
        pill.setBorder(new CompoundBorder(new LineBorder(new Color(40, 167, 69), 1, true), new EmptyBorder(8, 12, 8, 12)));
        pill.setBounds(35, y, 490, 50);

        JLabel seatBadge = new JLabel("SEAT ALLOCATED: #" + t.getSeatNumber());
        seatBadge.setFont(new Font("Segoe UI", Font.BOLD, 14));
        seatBadge.setForeground(new Color(30, 126, 52));

        JLabel fareBadge = new JLabel("TOTAL FARE: INR " + (int) t.getFare(), SwingConstants.RIGHT);
        fareBadge.setFont(new Font("Segoe UI", Font.BOLD, 14));
        fareBadge.setForeground(new Color(15, 32, 67));

        pill.add(seatBadge);
        pill.add(fareBadge);
        pass.add(pill);

        ModernButton closeBtn = new ModernButton("Dismiss & Return to Portal", new Color(108, 117, 125), new Color(90, 98, 104));
        closeBtn.addActionListener(e -> passDialog.dispose());

        passDialog.add(pass, BorderLayout.CENTER);
        passDialog.add(closeBtn, BorderLayout.SOUTH);
        passDialog.setVisible(true);
    }

    private void addPassRow(JPanel parent, String title1, String val1, String title2, String val2, int y) {
        JLabel t1 = new JLabel(title1);
        t1.setFont(new Font("Segoe UI", Font.BOLD, 11));
        t1.setForeground(new Color(108, 117, 125));
        t1.setBounds(38, y, 95, 18);

        JLabel v1 = new JLabel(val1);
        v1.setFont(new Font("Segoe UI", Font.BOLD, 12));
        v1.setForeground(new Color(33, 37, 41));
        v1.setBounds(135, y, 145, 18);

        JLabel t2 = new JLabel(title2);
        t2.setFont(new Font("Segoe UI", Font.BOLD, 11));
        t2.setForeground(new Color(108, 117, 125));
        t2.setBounds(285, y, 95, 18);

        JLabel v2 = new JLabel(val2);
        v2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        v2.setForeground(new Color(33, 37, 41));
        v2.setBounds(380, y, 150, 18);

        parent.add(t1);
        parent.add(v1);
        parent.add(t2);
        parent.add(v2);
    }

    private JPanel createMyBookingsPanel() {
        JPanel panel = new JPanel(new BorderLayout(14, 14));
        panel.setBackground(new Color(245, 247, 250));
        panel.setBorder(new EmptyBorder(16, 18, 16, 18));

        String[] cols = {"PNR Number", "Bus Number", "Seat #", "Passenger", "Total Fare", "Booking Date", "Status"};
        ticketTableModel = new DefaultTableModel(cols, 0);
        JTable table = new JTable(ticketTableModel);
        table.setRowHeight(32);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JTableHeader th = table.getTableHeader();
        th.setFont(new Font("Segoe UI", Font.BOLD, 13));
        th.setBackground(new Color(24, 43, 73));
        th.setForeground(Color.WHITE);

        // Center aligned cells
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(center);
        }

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setOpaque(false);

        ModernButton cancelBtn = new ModernButton("  Cancel Selected Ticket  ", new Color(220, 53, 69), new Color(185, 40, 50));
        cancelBtn.setPreferredSize(new Dimension(220, 40));

        cancelBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row == -1) {
                JOptionPane.showMessageDialog(this, "Select a booking row to proceed with cancellation.", "Selection Required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String pnr = (String) ticketTableModel.getValueAt(row, 0);
            int confirm = JOptionPane.showConfirmDialog(this, "Verify cancellation for ticket " + pnr + "?", "Cancellation Authorization", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;

            try {
                if (service.cancelTicket(pnr)) {
                    JOptionPane.showMessageDialog(this, "Ticket " + pnr + " has been cancelled. Seat refunded to pool.");
                    refreshMyBookings();
                    renderCoachLayout();
                } else {
                    JOptionPane.showMessageDialog(this, "Ticket already cancelled.");
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage());
            }
        });

        bottom.add(cancelBtn);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);

        refreshMyBookings();
        return panel;
    }

    private void refreshMyBookings() {
        if (ticketTableModel == null) return;
        ticketTableModel.setRowCount(0);
        List<Ticket> list = (currentUser instanceof Admin) ? db.getAllTickets() : db.getTicketsByPassenger(currentUser.getUsername());

        for (Ticket t : list) {
            ticketTableModel.addRow(new Object[]{
                t.getPnrNumber(), t.getBusId(), t.getSeatNumber(), t.getPassengerName(),
                "INR " + (int) t.getFare(), t.getBookingDate(), t.isActive() ? "CONFIRMED" : "CANCELLED"
            });
        }
    }

    private JPanel createAdminPanel() {
        JPanel panel = new JPanel(new BorderLayout(14, 14));
        panel.setBackground(new Color(245, 247, 250));
        panel.setBorder(new EmptyBorder(16, 18, 16, 18));

        JPanel form = new JPanel(new GridLayout(6, 2, 10, 12));
        form.setBackground(Color.WHITE);
        form.setBorder(new CompoundBorder(
            new TitledBorder(new LineBorder(new Color(225, 230, 238)), "Register & Dispatch New Luxury Coach"),
            new EmptyBorder(14, 18, 14, 18)
        ));

        JTextField busIdField = new JTextField("KL-15-TRV-890");
        JTextField plateField = new JTextField("KL-15-TRV-890");
        JComboBox<String> typeBox = new JComboBox<>(new String[]{"KSRTC Super Fast Express (Non-AC)", "Airavat Club Class Multi-Axle Volvo", "KSRTC-SWIFT Gajaraj AC Sleeper"});
        JTextField seatsField = new JTextField("24");

        form.add(new JLabel("Service Identifier:"));
        form.add(busIdField);
        form.add(new JLabel("Vehicle Registration Plate:"));
        form.add(plateField);
        form.add(new JLabel("Coach Service Category:"));
        form.add(typeBox);
        form.add(new JLabel("Seating Capacity:"));
        form.add(seatsField);

        ModernButton addBtn = new ModernButton("Authorize & Dispatch Coach to Depot", new Color(15, 32, 67), new Color(30, 80, 160));
        addBtn.setPreferredSize(new Dimension(300, 42));

        addBtn.addActionListener(e -> {
            try {
                String id = busIdField.getText().trim();
                String plate = plateField.getText().trim();
                int seats = Integer.parseInt(seatsField.getText().trim());
                Route defaultRoute = db.getAllRoutes().get(0);

                Bus newBus;
                String type = (String) typeBox.getSelectedItem();
                if ("Airavat Club Class Multi-Axle Volvo".equals(type)) {
                    newBus = new GarudaAiravatAcBus(id, plate, defaultRoute, seats);
                } else if ("KSRTC-SWIFT Gajaraj AC Sleeper".equals(type)) {
                    newBus = new SwiftSleeperBus(id, plate, defaultRoute, seats);
                } else {
                    newBus = new SuperFastBus(id, plate, defaultRoute, seats);
                }

                db.saveBus(newBus);
                refreshBusSelector();
                JOptionPane.showMessageDialog(this, "Coach " + plate + " added to live schedule!");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Input Error: " + ex.getMessage());
            }
        });

        panel.add(form, BorderLayout.NORTH);
        return panel;
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(BusReservationGUI::new);
    }
}