# MessLens Pro - Mess Transparency & Evidence Management System

A desktop app built with **Java 17+ (JavaFX)**, **JDBC (SQLite or MySQL)** and **Sarxos Webcam Capture**.
Kitchen staff photograph each stage of a dish (**Ingredients -> Cooking -> Serving**) with a quality checklist,
the mess head approves or rejects it, the warden confirms, and students see the verified timeline, rate meals and send complaints (with an optional photo).

---

## 1. Quick start

You need two things installed: **Java JDK 17 or newer** and **Apache Maven**. Nothing else. The database is a local SQLite file that is created automatically, and JavaFX and all other libraries are downloaded by Maven on the first run (**internet needed the first time only**).

### Step 1 - Install Java and Maven (one time)

Pick your operating system and run the commands exactly as shown.

#### Windows

Open **PowerShell** and run:
```powershell
winget install EclipseAdoptium.Temurin.17.JDK
```
Then install Maven. **Option A** (if you have Chocolatey):
```powershell
choco install maven
```
**Option B** (manual): download the *Binary zip archive* from https://maven.apache.org/download.cgi, unzip it to `C:\apache-maven`, then add `C:\apache-maven\bin` to your PATH (Start menu -> "Edit the system environment variables" -> Environment Variables -> Path -> New).

**Close PowerShell and open a new one**, then check:
```powershell
java -version
mvn -v
```

#### macOS

Open **Terminal**. Install Homebrew first from https://brew.sh if you do not have it, then run:
```bash
brew install --cask temurin@17
brew install maven
```
Check:
```bash
java -version
mvn -v
```
For the built-in camera, also run once: `brew install imagesnap`

#### Linux (Ubuntu / Debian)

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk maven
```
Check:
```bash
java -version
mvn -v
```
Other distributions: Fedora `sudo dnf install java-17-openjdk-devel maven`, Arch `sudo pacman -S jdk17-openjdk maven`.

> `java -version` must show **17 or higher**. If `java` or `mvn` is "not recognized" / "command not found", close the terminal, open a new one and try again.

### Step 2 - Unzip the project

Unzip it anywhere (for example your Downloads folder). You should get a folder containing `pom.xml`, `src`, `config.properties` and `README.md`.

### Step 3 - Run the app

Open a terminal **inside that folder** and run the same command on every operating system:

```bash
mvn javafx:run
```

Where to open the terminal:
- **Windows:** open the folder in File Explorer, click the address bar, type `cmd` and press Enter. Or in PowerShell: `cd C:\path\to\mess_lens`
- **macOS / Linux:** `cd ~/Downloads/mess_lens` (use your real folder name; put it in quotes if it has a space, for example `cd "mess_lens 8"`)

The first start takes a few minutes because Maven downloads the libraries. After that it opens in seconds. If the app misbehaves after you replaced files, rebuild with:

```bash
mvn clean javafx:run
```

Optional shortcuts (only if the folder contains them): Windows `run.bat` (double-click) and macOS / Linux `./run.sh` (if "permission denied": `chmod +x run.sh`, or run `bash run.sh`). They just check Java and Maven and then run `mvn javafx:run`.

### Step 4 - Sign in

The sign-in screen lists every demo account; click a row to sign in. Or type them:

| Role | Username | Password |
|---|---|---|
| Kitchen staff | `worker1` | `worker123` |
| Mess head | `head1` | `head123` |
| Warden | `warden1` | `warden1234` |
| Student | `student1` | `student123` |
| Administrator | `admin` | `admin123` |

New students can also register on the **New student** tab (full name, registration number, `@srmap.edu.in` email, hostel, password of at least 6 characters). Use the eye button to see what you typed.

> **No virtual environment needed.** Java projects do not use `venv`. Maven keeps the libraries in your user folder (`~/.m2`), and the app keeps its data in `messlens.db`, plus `.messlens` in your user folder, so there is nothing to activate.

### Camera

| OS | What to do |
|---|---|
| **Windows / Linux** | The built-in webcam is used directly with a live preview. |
| **macOS** | `brew install imagesnap` once; *Take a photo* then uses the built-in camera (no live preview). On Apple Silicon (M1/M2/M3) the live-preview library cannot load; the warning in the terminal is harmless. |
| **Any computer, no camera** | Click **Choose a photo from this computer**. Photos from `sample_test_images/` work well. |

---

## 2. Switching to MySQL (optional)

The default is SQLite (`DB_TYPE=sqlite` in `config.properties`). To use a MySQL 8 server instead:
1. Install MySQL Server 8 and start it (Windows: service "MySQL80"; Linux: `sudo service mysql start`; macOS: `brew services start mysql`).
2. In `config.properties` set `DB_TYPE=mysql` and `MYSQL_PASSWORD=your_password`.
3. Run the app. The `messlens` database and all tables are created automatically.

---

## 3. Where your data lives, and how to reset

| What | Where |
|---|---|
| Database (SQLite) | `messlens.db` in the project folder |
| Photos | `~/.messlens/evidence` (Windows: `C:\Users\<you>\.messlens\evidence`) |
| Audit-log key | `~/.messlens/audit` |

**Reset everything** (fresh demo data): close the app, delete `messlens.db` and the `.messlens` folder in your user folder, then start again.
With MySQL also run: `mysql -u root -p -e "DROP DATABASE messlens;"`.

---

## 4. Troubleshooting

| Problem | Fix |
|---|---|
| `mvn` / `java` "not recognized" or "command not found" | Install the tools (1.1) and open a **new** terminal. |
| `release version 17 not supported` | Your Java is older than 17. Install JDK 17+ and check `java -version`. |
| `UnsatisfiedLinkError ... libbridj ... incompatible architecture` and "Webcam initialization notice" | Harmless on Apple Silicon Macs. Use *Take a photo* with `imagesnap`, or *Choose a photo from this computer*. |
| "Communications link failure" | You chose MySQL and it is not running. Start it, or set `DB_TYPE=sqlite`. |
| "Audit key state is missing" | Already handled: `AUDIT_AUTO_RESET=true` in `config.properties` starts a new audit chain. |
| Stored photos check says "photo file is missing" | The photo files were deleted or came from another computer. Harmless for a demo; reset (section 3) for a clean result. |
| Red errors in VS Code but the app runs | VS Code's Java index is stale: Cmd/Ctrl+Shift+P -> "Java: Clean Java Language Server Workspace" -> Restart and delete. Check with `mvn clean test-compile`. |

---

## 5. What each role can do

| Role | Pages |
|---|---|
| **Kitchen staff** | **Capture**: meal, dish, stage, photo, and a **quality checklist** (temperatures and pass/fail points) -> *Send for approval*. Several photos per stage are allowed. **My Submissions**: status, reviewer notes, withdraw; the red "rejected" banner disappears once a rejected photo has been opened. |
| **Mess head** | **Overview**, **Review Queue** (large photo, quality checklist panel, quick notes, Approve / Reject), **All Records** (filters, CSV export), **Complaints** (reply is optional; *Mark as read*), **Meal Ratings**, **Weekly Menu**, **Kitchen Staff**. |
| **Warden** | Second reviewer: confirms or overturns the mess head's decisions; reads the audit log. |
| **Student** | **Food Timeline** (all approved photos per stage, with both the mess head's and the warden's decision), **Hostel Quality**, **Rate Meals**, **Complaints** (optional photo), **Weekly Menu**. |
| **Administrator** | Creates mess heads, wardens and kitchen staff; **Audit & Integrity** (verify the audit chain and stored photos). |
| **Everyone** | **Settings** (account, change password), **Help**. |

Quality limits used by the checklist (change them in `model/QualityChecklist.java` to match your hostel's food-safety standard): cold storage at most 5 deg C, cooking at least 75 deg C, serving at least 60 deg C.

---

## 6. Architecture

One window with a **sidebar + page** layout (`AppShell`); every sidebar item is a `Page`. Styling is in `styles/theme.css`; icons are vector (`Icons`).

- `com.messlens.model` - entities (`User`, `Hostel`, `Upload`, `Approval`, `Complaint`, `QualityChecklist`, ...)
- `com.messlens.dao` - JDBC data access and `DatabaseManager` (SQLite or MySQL, automatic upgrades)
- `com.messlens.service` - business rules (uploads, reviews, complaints, authentication)
- `com.messlens.security` / `com.messlens.audit` - access control, password hashing, hash-chained audit log
- `com.messlens.ui` - JavaFX views and event handling
- `com.messlens.util` - configuration, SHA-256 hashing, webcam bridge, alerts

### SRM University AP - CSE 307 syllabus mapping

| Unit | Topic | Where in MessLens |
|---|---|---|
| 1 | OOP, inheritance, packages | Model hierarchy and the `com.messlens.*` packages |
| 2 | Custom exceptions, polymorphism | `MessLensException` and its subclasses (`ValidationException`, `DuplicateEvidenceException`, `AuthenticationException`, ...) |
| 3 | Multithreading, byte streams | Background webcam thread, file streams for photos |
| 4 | JDBC, collections with Iterator | `DatabaseManager`, DAOs, `CollectionUtil.filterWithIterator()` |
| 5 | Generics, JavaFX controls | `Pair<K, V>`, `CrudRepository<T, ID>`, `AppShell`, `TableView`, `ComboBox` |

### Security design

Five published works guide access control, authentication and auditing; see `docs/RESEARCH_BASIS.md` and `docs/EVALUATION.md`.
Roles plus attribute rules (Kuhn et al.) decided by one `AccessDecisionService` (NIST SP 800-162); worker -> head -> warden review with separation of duties (Clark-Wilson);
PBKDF2 password hashing, lock-out and session timeouts (NIST SP 800-63B); hash-chained, forward-secure audit log (Schneier-Kelsey).
Photos are fingerprinted with SHA-256 and duplicates are rejected.

**Demo settings to change before real use:** remove the demo sign-in list, change the seeded passwords, and set `AUDIT_AUTO_RESET=false` in `config.properties`.

---

## 7. Developer commands

```bash
mvn javafx:run                  # run the app
mvn clean test-compile          # compile everything including tests
# automated flow test (deletes uploads and approvals of the configured database - use a scratch DB):
mvn test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.messlens.FlowVerificationTest
```

## 8. Known limitations

- Only one webcam (the OS default) is supported.
- Photos are stored on local disk; for a multi-hostel deployment they could move to cloud storage in `UploadService`.
- Out of scope: escalation timers, automated hygiene scorecards, PDF export.