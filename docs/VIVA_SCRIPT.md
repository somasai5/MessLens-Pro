# MessLens - viva script

Read this twice, then practise saying the pitch and the demo out loud. Everything here matches the code in this project.

## 1. The 30-second pitch
"MessLens is a JavaFX desktop app with a MySQL database for hostel mess transparency. Kitchen staff photograph each stage of a dish: ingredients, cooking and serving. The mess head approves or rejects each photo, and students see what passed. Every photo is hashed with SHA-256, so it cannot be swapped or sent twice. Students also see the weekly menu, rate meals and send complaints to the mess head."

## 2. Demo order (about 3 minutes)
1. Sign in as `worker1` / `worker123`. Upload a photo for a stage (choose a photo file if there is no webcam).
2. Sign out. Sign in as `head1` / `head123`. Open the Review Queue and approve the photo, then show All Records.
3. Open Weekly Menu. Switch South / North / International, pick a day, and press Edit on a meal.
4. Sign out. Sign in as `student1` / `student123`. Show the Food Timeline, then Rate Meals (give 4 stars and a comment).
5. Send a complaint. Show that you can Withdraw it while it is Open.
6. Sign in as `head1` again. Show Meal Ratings, then reply to a complaint. Sign back in as `student1` to show the reply.
7. Finish in MySQL: `mysql -u root -p`, then `USE messlens; SELECT * FROM meal_ratings;`

## 3. How the code is organised
Every request goes **UI -> Service -> DAO -> MySQL**.
- `ui/` JavaFX screens (what you see). `styles/theme.css` is the look.
- `service/` the rules: who may do what, and whether the input is valid (`AuthService`, `UploadService`, `ApprovalService`, `FeedbackService`).
- `dao/` the only place with SQL (`UserDao`, `UploadDao`, `MenuDao`, `RatingDao`, `ComplaintDao`). `DatabaseManager` opens connections and creates the tables.
- `model/` plain data classes (`User`, `Upload`, `Complaint`, ...). `exception/` our own exception types.
- `security/` roles and permissions, login lockout, password rules. `audit/` the tamper-evident log.

## 4. The rubric, item by item
- **Event handling:** `button.setOnAction(e -> ...)` on every button; row clicks in tables; toggle buttons for tabs and filters. Example: the Withdraw button in `ComplaintsPage`.
- **JavaFX:** `Application`, `Scene`, layout panes (`VBox`, `HBox`, `GridPane`), controls, and CSS styling.
- **MySQL with rich operations:** 10 tables, role-based login, add / view / update / delete (complaints), search and filters, CSV export, approvals with remarks, ratings with averages (`GROUP BY`), joins, audit log.

## 5. Security parts, in plain words
- **Passwords:** never stored as text. Each is salted and run through PBKDF2-HMAC-SHA256 (600,000 rounds), which is deliberately slow so stolen data is hard to crack.
- **Photos:** a SHA-256 hash of each photo is stored in a UNIQUE column, so the same photo cannot be uploaded twice. The integrity check hashes the files again to find any that changed.
- **Login lockout:** after 4 wrong tries the username is locked for 1 minute, then 2, 4, 8, 16, up to a maximum of 30 minutes. This slows down password guessing.
- **Sessions:** signed out after 15 idle minutes (or 8 hours in total).
- **Roles:** kitchen staff, mess head, warden, student, admin. A mess head and a warden only reach their own hostel's data.
- **Audit log:** every important action is written to a log. Each entry's code is built from the entry before it, so changing or deleting one old row breaks every later entry, and the check shows it. Think of numbered pages where each page carries a stamp made from the previous page. (It follows a published design by Schneier and Kelsey.)

## 6. Questions you may get
- **What is event handling?** The program reacts to something the user does (a click). We register a handler with `setOnAction`, and JavaFX calls it when the event happens.
- **Why JavaFX and not Swing or AWT?** It is the modern toolkit: CSS styling, better layouts, hardware-accelerated drawing. It is also what the "Outstanding" tier asks for.
- **What is JDBC?** Java's standard way to talk to a database. `DriverManager.getConnection(url, user, password)` opens a connection.
- **Statement vs PreparedStatement?** A PreparedStatement uses `?` placeholders, so user input is never pasted into the SQL. That stops SQL injection, and it is faster to reuse.
- **What is try-with-resources?** `try (Connection c = ...)` closes the connection automatically, even if an error happens.
- **What is a transaction?** A group of changes that all succeed or all fail. We call `setAutoCommit(false)`, then `commit()`, or `rollback()` on an error (see `ApprovalDao`).
- **What is the DAO pattern?** One class per table holds all its SQL, so screens never contain SQL.
- **Why a service layer?** Rules such as "only a student can rate" live in one place and are checked before the database is touched.
- **How do you stop a student rating a meal twice in one day?** A `UNIQUE (user_id, rating_date, meal)` constraint in MySQL, and the code updates the old rating instead of adding a new one.
- **How do you get the average rating?** `SELECT meal, AVG(stars), COUNT(*) FROM meal_ratings WHERE hostel_id = ? GROUP BY meal`.
- **What is a JOIN used for here?** To show a student's name next to a complaint: `FROM complaints c JOIN users u ON u.id = c.student_id`.
- **Why MySQL and not SQLite?** MySQL is a server, so many computers share one database. SQLite is a single local file. The app can still use SQLite by setting `DB_TYPE=sqlite`.
- **What would you improve?** A website or phone version for students, notifications for replies, and per-hostel reports.

## 7. Code you may be asked to write by hand

A simple class:
```java
public class Hostel {
    private final int id;
    private final String name;

    public Hostel(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() { return id; }
    public String getName() { return name; }
}
```

A button with an event handler:
```java
Button save = new Button("Save");
save.setOnAction(e -> System.out.println("Saved!"));
```

A minimal JavaFX program:
```java
public class Hello extends Application {
    @Override
    public void start(Stage stage) {
        Button b = new Button("Click me");
        b.setOnAction(e -> b.setText("Clicked"));
        stage.setScene(new Scene(new StackPane(b), 300, 200));
        stage.setTitle("Hello");
        stage.show();
    }
    public static void main(String[] args) { launch(args); }
}
```

Reading from MySQL with a PreparedStatement:
```java
String sql = "SELECT name FROM hostels WHERE id = ?";
try (Connection conn = DriverManager.getConnection(url, user, password);
     PreparedStatement ps = conn.prepareStatement(sql)) {
    ps.setInt(1, 1);
    try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) System.out.println(rs.getString("name"));
    }
}
```

Inserting a row:
```java
String sql = "INSERT INTO complaints (student_id, hostel_id, category, description, status) VALUES (?, ?, ?, ?, 'OPEN')";
try (Connection conn = DriverManager.getConnection(url, user, password);
     PreparedStatement ps = conn.prepareStatement(sql)) {
    ps.setInt(1, studentId);
    ps.setInt(2, hostelId);
    ps.setString(3, "Hygiene");
    ps.setString(4, "There was a hair in my rice.");
    ps.executeUpdate();
}
```

## 8. Last tips
- Open the project once on the exam computer before your viva, and check MySQL is running.
- Know where each feature lives (section 3). If you are asked to show something, go straight to that file.
- Be ready to explain any part of the code you submit, in your own words. Follow your course's rules on tools and outside help.
