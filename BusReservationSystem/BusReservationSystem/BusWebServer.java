import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/* ============================================================================
   TIER 1 & 2: OOP DOMAIN ENTITIES & PERSISTENCE
   ============================================================================ */

class Seat implements Serializable {
    private final int seatNumber;
    private boolean isBooked;
    private String passengerName;
    private final transient ReentrantLock lock = new ReentrantLock();

    public Seat(int seatNumber) {
        this.seatNumber = seatNumber;
        this.isBooked = false;
        this.passengerName = null;
    }

    public int getSeatNumber() { return seatNumber; }
    public synchronized boolean isBooked() { return isBooked; }
    public synchronized String getPassengerName() { return passengerName; }

    public boolean reserve(String name) {
        ReentrantLock activeLock = (lock != null) ? lock : new ReentrantLock();
        activeLock.lock();
        try {
            if (isBooked) return false;
            this.isBooked = true;
            this.passengerName = name;
            return true;
        } finally {
            activeLock.unlock();
        }
    }

    public synchronized void cancel() {
        this.isBooked = false;
        this.passengerName = null;
    }
}

class Route implements Serializable {
    private final String routeId, origin, destination, departureTime, platform;
    private final double distanceKm;

    public Route(String routeId, String origin, String destination, double distanceKm, String departureTime, String platform) {
        this.routeId = routeId;
        this.origin = origin;
        this.destination = destination;
        this.distanceKm = distanceKm;
        this.departureTime = departureTime;
        this.platform = platform;
    }

    public String getRouteId() { return routeId; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public double getDistanceKm() { return distanceKm; }
    public String getDepartureTime() { return departureTime; }
    public String getPlatform() { return platform; }
}

abstract class Bus implements Serializable {
    private final String busId, regNo;
    private final Route route;
    private final Map<Integer, Seat> seats = new HashMap<>();

    public Bus(String busId, String regNo, Route route, int totalSeats) {
        this.busId = busId;
        this.regNo = regNo;
        this.route = route;
        for (int i = 1; i <= totalSeats; i++) seats.put(i, new Seat(i));
    }

    public String getBusId() { return busId; }
    public String getRegNo() { return regNo; }
    public Route getRoute() { return route; }
    public Map<Integer, Seat> getSeats() { return seats; }
    public abstract String getBusType();
    public abstract double calculateBaseFare();

    public double calculateTotalFareWithGst() {
        return Math.round(calculateBaseFare() * 1.05); // Includes 5% GST
    }
}

class SuperFastBus extends Bus {
    public SuperFastBus(String id, String reg, Route r, int s) { super(id, reg, r, s); }
    @Override public String getBusType() { return "KSRTC Super Fast Express (Non-AC)"; }
    @Override public double calculateBaseFare() { return Math.round(getRoute().getDistanceKm() * 1.30 + 25.0); }
}

class AiravatVolvoBus extends Bus {
    public AiravatVolvoBus(String id, String reg, Route r, int s) { super(id, reg, r, s); }
    @Override public String getBusType() { return "Airavat Club Class Volvo Multi-Axle AC"; }
    @Override public double calculateBaseFare() { return Math.round((getRoute().getDistanceKm() * 2.15) + 120.0); }
}

class SwiftSleeperBus extends Bus {
    public SwiftSleeperBus(String id, String reg, Route r, int s) { super(id, reg, r, s); }
    @Override public String getBusType() { return "KSRTC-SWIFT Gajaraj AC Sleeper"; }
    @Override public double calculateBaseFare() { return Math.round((getRoute().getDistanceKm() * 2.65) + 200.0); }
}

class Ticket implements Serializable {
    private final String pnr, busId, name, mobile, date;
    private final int seatNumber;
    private final double fare;
    private boolean active;

    public Ticket(String pnr, String busId, String name, String mobile, int seatNumber, double fare) {
        this.pnr = pnr;
        this.busId = busId;
        this.name = name;
        this.mobile = mobile;
        this.seatNumber = seatNumber;
        this.fare = fare;
        this.date = new SimpleDateFormat("dd-MMM-yyyy hh:mm a").format(new Date());
        this.active = true;
    }

    public String getPnr() { return pnr; }
    public String getBusId() { return busId; }
    public String getName() { return name; }
    public String getMobile() { return mobile; }
    public int getSeatNumber() { return seatNumber; }
    public double getFare() { return fare; }
    public String getDate() { return date; }
    public boolean isActive() { return active; }
    public void cancel() { this.active = false; }
}

/* ============================================================================
   TIER 3: CENTRAL APPLICATION REPOSITORY & WEB SERVER
   ============================================================================ */

class BusRepository {
    private static BusRepository instance;
    private final Map<String, Bus> buses = new ConcurrentHashMap<>();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    private BusRepository() {
        Route r1 = new Route("RT-101", "Thiruvananthapuram (TVC)", "Ernakulam South (KSRTC)", 218.0, "06:00 AM", "Platform 3");
        Route r2 = new Route("RT-204", "Kozhikode Bus Stand", "Bengaluru (Shantinagar)", 354.0, "09:30 PM", "Platform 7");
        Route r3 = new Route("RT-308", "Ernakulam Vyttila Hub", "Chennai (CMBT / Koyambedu)", 684.0, "04:45 PM", "Platform 1");

        buses.put("KL-15-A-1102", new SuperFastBus("KL-15-A-1102", "KL-15-A-1102", r1, 28));
        buses.put("KA-57-F-9090", new AiravatVolvoBus("KA-57-F-9090", "KA-57-F-9090", r2, 24));
        buses.put("SWIFT-KL-01-999", new SwiftSleeperBus("SWIFT-KL-01-999", "KL-01-CZ-8888", r3, 20));
    }

    public static synchronized BusRepository getInstance() {
        if (instance == null) instance = new BusRepository();
        return instance;
    }

    public List<Bus> getAllBuses() { return new ArrayList<>(buses.values()); }
    public Bus getBus(String id) { return buses.get(id); }
    public List<Ticket> getAllTickets() { return new ArrayList<>(tickets.values()); }

    public synchronized Ticket book(String busId, int seatNum, String name, String mobile) throws Exception {
        Bus bus = getBus(busId);
        if (bus == null) throw new IllegalArgumentException("Bus schedule not found.");
        Seat s = bus.getSeats().get(seatNum);
        if (s == null) throw new IllegalArgumentException("Invalid seat requested.");
        if (!s.reserve(name)) throw new IllegalStateException("Seat #" + seatNum + " was just booked by another user!");

        String pnr = "KL" + (1000000 + new Random().nextInt(9000000));
        Ticket t = new Ticket(pnr, busId, name, mobile, seatNum, bus.calculateTotalFareWithGst());
        tickets.put(pnr, t);
        return t;
    }

    public synchronized boolean cancel(String pnr) {
        Ticket t = tickets.get(pnr);
        if (t == null || !t.isActive()) return false;
        Bus bus = getBus(t.getBusId());
        if (bus != null) {
            Seat s = bus.getSeats().get(t.getSeatNumber());
            if (s != null) s.cancel();
        }
        t.cancel();
        return true;
    }
}

public class BusWebServer {
    private static final int PORT = 8080;

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new MainHandler());
        server.setExecutor(null);
        System.out.println("==================================================================");
        System.out.println("  BHARAT YATRA - WEB ERP SERVER RUNNING ON PORT " + PORT);
        System.out.println("  Local URL: http://localhost:" + PORT);
        System.out.println("==================================================================");
        server.start();
    }

    static class MainHandler implements HttpHandler {
        private final BusRepository repo = BusRepository.getInstance();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = new HashMap<>();

            if ("POST".equalsIgnoreCase(method)) {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) body.append(line);
                params = parseForm(body.toString());
            } else if (query != null) {
                params = parseForm(query);
            }

            String alertMessage = null;
            Ticket confirmedTicket = null;

            if ("POST".equalsIgnoreCase(method)) {
                String action = params.get("action");
                if ("book".equals(action)) {
                    try {
                        String busId = params.get("busId");
                        int seatNum = Integer.parseInt(params.get("seatNumber"));
                        String name = params.get("name");
                        String mobile = params.get("mobile");
                        confirmedTicket = repo.book(busId, seatNum, name, mobile);
                    } catch (Exception ex) {
                        alertMessage = "Booking Failed: " + ex.getMessage();
                    }
                } else if ("cancel".equals(action)) {
                    String pnr = params.get("pnr");
                    if (repo.cancel(pnr)) {
                        alertMessage = "Ticket " + pnr + " successfully cancelled. Berth returned to pool.";
                    } else {
                        alertMessage = "Unable to cancel ticket " + pnr;
                    }
                }
            }

            String selectedBusId = params.getOrDefault("busId", repo.getAllBuses().get(0).getBusId());
            Bus selectedBus = repo.getBus(selectedBusId);

            String html = renderHtml(selectedBus, alertMessage, confirmedTicket);
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }

        private Map<String, String> parseForm(String raw) {
            Map<String, String> map = new HashMap<>();
            if (raw == null || raw.isEmpty()) return map;
            String[] pairs = raw.split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=");
                if (kv.length == 2) {
                    try {
                        map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name()),
                                URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name()));
                    } catch (Exception ignored) {}
                }
            }
            return map;
        }

        private String renderHtml(Bus bus, String alert, Ticket ticket) {
            StringBuilder sb = new StringBuilder();
            sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1'>");
            sb.append("<title>Bharat Yatra - Intercity Bus Booking</title>");
            sb.append("<style>");
            sb.append("body{font-family:'Segoe UI',sans-serif;margin:0;background:#f0f2f5;color:#333}");
            sb.append(".header{background:linear-gradient(135deg, #102a43, #243b53);color:#fff;padding:18px 24px;border-top:4px solid #ff7a00;display:flex;justify-content:space-between;align-items:center;}");
            sb.append(".container{max-width:1150px;margin:20px auto;padding:0 15px;}");
            sb.append(".alert{padding:12px 18px;margin-bottom:15px;border-radius:6px;background:#ffebee;color:#c62828;border:1px solid #ffcdd2;font-weight:bold;}");
            sb.append(".card{background:#fff;border-radius:8px;padding:20px;margin-bottom:20px;box-shadow:0 2px 8px rgba(0,0,0,0.06);}");
            sb.append(".grid{display:grid;grid-template-columns:2fr 1fr;gap:20px;}");
            sb.append(".bus-chassis{background:#e2e8f0;border-radius:24px;border:3px solid #627d98;padding:20px;position:relative;margin:15px 0;}");
            sb.append(".cabin{background:#9fb3c8;color:#102a43;font-weight:bold;text-align:center;padding:8px;border-radius:8px;margin-bottom:15px;letter-spacing:1px;}");
            sb.append(".seat-matrix{display:grid;grid-template-columns:repeat(4, 1fr);gap:12px;}");
            sb.append(".seat{padding:14px 6px;text-align:center;border-radius:6px;font-weight:bold;cursor:pointer;border:none;}");
            sb.append(".avail{background:#27ae60;color:#fff;} .avail:hover{background:#219653;}");
            sb.append(".booked{background:#eb5757;color:#fff;cursor:not-allowed;}");
            sb.append("input,select,button{width:100%;padding:10px;margin:6px 0;border-radius:6px;border:1px solid #cbd5e1;box-sizing:border-box;font-size:14px;}");
            sb.append("button.btn{background:#102a43;color:#fff;font-weight:bold;cursor:pointer;border:none;} button.btn:hover{background:#243b53;}");
            sb.append("table{width:100%;border-collapse:collapse;margin-top:10px;} th,td{padding:10px;text-align:left;border-bottom:1px solid #e2e8f0;} th{background:#f8fafc;}");
            sb.append(".ticket-modal{background:#e6fffa;border:2px dashed #319795;border-radius:8px;padding:20px;margin-bottom:20px;}");
            sb.append("</style></head><body>");

            sb.append("<div class='header'><div><h2 style='margin:0'>BHARAT YATRA (KSRTC & CO-OP ERP)</h2><small>State Road Transport Online Portal</small></div>");
            sb.append("<div><b>Status:</b> Live & Connected</div></div>");

            sb.append("<div class='container'>");

            if (alert != null) sb.append("<div class='alert'>").append(alert).append("</div>");

            if (ticket != null) {
                sb.append("<div class='ticket-modal'>");
                sb.append("<h3 style='margin-top:0;color:#234e52;'>CONFIRMED DIGITAL BOARDING PASS</h3>");
                sb.append("<p><b>PNR Number:</b> ").append(ticket.getPnr()).append(" | <b>Date:</b> ").append(ticket.getDate()).append("</p>");
                sb.append("<p><b>Passenger:</b> ").append(ticket.getName()).append(" (").append(ticket.getMobile()).append(")</p>");
                sb.append("<p><b>Bus Service:</b> ").append(bus.getBusType()).append(" (").append(bus.getRegNo()).append(")</p>");
                sb.append("<p><b>Allocated Seat:</b> Seat #").append(ticket.getSeatNumber()).append(" | <b>Total Fare Paid:</b> ₹").append((int)ticket.getFare()).append("</p>");
                sb.append("</div>");
            }

            sb.append("<div class='card'>");
            sb.append("<form method='GET'><label><b>Select Bus Schedule:</b></label><select name='busId' onchange='this.form.submit()'>");
            for (Bus b : repo.getAllBuses()) {
                String sel = b.getBusId().equals(bus.getBusId()) ? "selected" : "";
                sb.append("<option value='").append(b.getBusId()).append("' ").append(sel).append(">")
                  .append(b.getBusType()).append(" | ").append(b.getRoute().getOrigin()).append(" ➔ ")
                  .append(b.getRoute().getDestination()).append(" (₹").append((int)b.calculateTotalFareWithGst()).append(")")
                  .append("</option>");
            }
            sb.append("</select></form></div>");

            sb.append("<div class='grid'>");

            // Left: Bus Coach Blueprint
            sb.append("<div class='card'>");
            sb.append("<h3>2x2 Coach Blueprint (").append(bus.getRegNo()).append(")</h3>");
            sb.append("<div class='bus-chassis'>");
            sb.append("<div class='cabin'>DRIVER CABIN / FRONT WINDSHIELD</div>");
            sb.append("<div class='seat-matrix'>");

            for (int i = 1; i <= bus.getSeats().size(); i++) {
                Seat s = bus.getSeats().get(i);
                if (s.isBooked()) {
                    sb.append("<div class='seat booked'>Seat ").append(i).append("<br/><small>Booked</small></div>");
                } else {
                    sb.append("<button type='button' class='seat avail' onclick='selectSeat(").append(i).append(")'>Seat ").append(i).append("<br/><small>Available</small></button>");
                }
            }
            sb.append("</div></div>");
            sb.append("<small style='color:#627d98;'>Tip: Click any green seat to automatically populate the booking form on the right.</small>");
            sb.append("</div>");

            // Right: Instant Reservation Form
            sb.append("<div class='card'>");
            sb.append("<h3>Book Selected Berth</h3>");
            sb.append("<p><b>Origin:</b> ").append(bus.getRoute().getOrigin()).append("<br/><b>Destination:</b> ").append(bus.getRoute().getDestination()).append("</p>");
            sb.append("<p><b>Total Fare:</b> ₹").append((int)bus.calculateTotalFareWithGst()).append(" <small>(5% GST Included)</small></p>");
            sb.append("<form method='POST'>");
            sb.append("<input type='hidden' name='action' value='book'/>");
            sb.append("<input type='hidden' name='busId' value='").append(bus.getBusId()).append("'/>");
            sb.append("<label>Seat Number:</label><input type='number' id='seatNumber' name='seatNumber' required readonly placeholder='Click a seat on map'/>");
            sb.append("<label>Full Passenger Name:</label><input type='text' name='name' required placeholder='e.g., Anjana Nair'/>");
            sb.append("<label>10-Digit Mobile:</label><input type='tel' name='mobile' required pattern='[0-9]{10}' placeholder='9447012345'/>");
            sb.append("<button type='submit' class='btn' style='margin-top:15px;'>Confirm & Issue Boarding Pass</button>");
            sb.append("</form></div>");

            sb.append("</div>"); // End grid

            // Bottom: Real-Time Booked Tickets
            sb.append("<div class='card'>");
            sb.append("<h3>Active Bookings & Real-Time Cancellation</h3>");
            List<Ticket> tickets = repo.getAllTickets();
            if (tickets.isEmpty()) {
                sb.append("<p style='color:#64748b;'>No tickets reserved yet. Book a seat above!</p>");
            } else {
                sb.append("<table><tr><th>PNR</th><th>Bus</th><th>Seat #</th><th>Passenger</th><th>Fare</th><th>Date</th><th>Status</th><th>Action</th></tr>");
                for (Ticket t : tickets) {
                    sb.append("<tr><td><b>").append(t.getPnr()).append("</b></td>");
                    sb.append("<td>").append(t.getBusId()).append("</td>");
                    sb.append("<td>Seat ").append(t.getSeatNumber()).append("</td>");
                    sb.append("<td>").append(t.getName()).append("</td>");
                    sb.append("<td>₹").append((int)t.getFare()).append("</td>");
                    sb.append("<td>").append(t.getDate()).append("</td>");
                    sb.append("<td>").append(t.isActive() ? "<span style='color:green;font-weight:bold'>CONFIRMED</span>" : "<span style='color:red;'>CANCELLED</span>").append("</td>");
                    sb.append("<td>");
                    if (t.isActive()) {
                        sb.append("<form method='POST' style='margin:0;' onsubmit='return confirm(\"Cancel ticket ").append(t.getPnr()).append("?\");'>");
                        sb.append("<input type='hidden' name='action' value='cancel'/>");
                        sb.append("<input type='hidden' name='pnr' value='").append(t.getPnr()).append("'/>");
                        sb.append("<button type='submit' style='background:#eb5757;color:#fff;border:none;padding:5px 10px;cursor:pointer;border-radius:4px;'>Cancel</button>");
                        sb.append("</form>");
                    } else {
                        sb.append("-");
                    }
                    sb.append("</td></tr>");
                }
                sb.append("</table>");
            }
            sb.append("</div>");

            sb.append("</div>"); // End container
            sb.append("<script>function selectSeat(n){document.getElementById('seatNumber').value=n;}</script>");
            sb.append("</body></html>");
            return sb.toString();
        }
    }
}