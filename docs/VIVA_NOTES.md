# MessLens - viva notes (Java lab project, "Outstanding" tier)

**Rubric target:** Event handling + JavaFX + MySQL with rich operations (15 marks).

## One-line pitch
Kitchen staff photograph each cooking stage; the mess head approves or rejects; students see only verified photos. Every photo is SHA-256 hashed, so tampering or re-uploading the same photo is detected.

## Where each rubric item is shown
| Rubric | Where to show it |
|---|---|
| Event handling | Button clicks (`setOnAction`), table row clicks, tab toggles, login form. Example: Approve button in `HeadReviewPage`. |
| JavaFX | Whole UI: `ui/` package, styled by `styles/theme.css`. |
| MySQL | `dao/DatabaseManager.java` (connection + tables), `dao/UploadDao`, `ApprovalDao`, `UserDao`. |
| Rich operations | Role-based login (worker, head, warden, student, admin), upload, approve/reject with remark, withdraw, search and filter, CSV export, password change, audit log, duplicate-photo detection. |

## Database tables
`hostels`, `users`, `uploads`, `approvals`, `warden_reviews`, `audit_log`, `login_throttle`, and the new `mess_menu`, `meal_ratings`, `complaints`.

## JDBC points the examiner may ask
- Connection: `DriverManager.getConnection(url, user, password)`.
- Safe queries: `PreparedStatement` with `?` placeholders (stops SQL injection).
- Transactions: `setAutoCommit(false)`, then `commit()` / `rollback()` (see `ApprovalDao`).
- Passwords: never stored as plain text. They are salted and run through PBKDF2-HMAC-SHA256 (600,000 rounds, deliberately slow). Plain SHA-256 is used for the food photos, where speed is a feature.

## Demo accounts
worker1 / worker123, head1 / head123, student1 / student123, warden1 / warden1234, admin / admin123.

## New features: weekly menu, meal ratings, complaints
| Feature | Who | Where in the code |
|---|---|---|
| Weekly menu (South / North / International, 7 days x 4 meals) | Everyone sees it; a mess head edits their own mess | `ui/MenuPage`, `dao/MenuDao`, table `mess_menu` |
| Meal ratings (1-5 stars + comment, one rating per meal per day) | Students rate; mess head sees averages and comments | `ui/RatingsPage`, `dao/RatingDao`, table `meal_ratings` |
| Complaints with replies (OPEN -> RESOLVED) | Students send; mess head of the same hostel replies | `ui/ComplaintsPage`, `dao/ComplaintDao`, table `complaints` |

The rules (who may do what, input checks) are in `service/FeedbackService`. The screens never touch SQL directly: **UI -> Service -> DAO -> MySQL**.

SQL worth knowing for the viva:
- `SELECT meal, AVG(stars), COUNT(*) FROM meal_ratings WHERE hostel_id = ? GROUP BY meal` (average per meal)
- `... FROM complaints c JOIN users u ON u.id = c.student_id ...` (show the student's name)
- `UNIQUE (user_id, rating_date, meal)` stops a student rating the same meal twice in one day; the DAO updates instead.
- `UPDATE ... ; if no row changed, INSERT` is the "upsert" used for the menu and ratings.

Run the checks for these features with `com.messlens.FeedbackFlowTest` (31 checks).
