package com.messlens;

import com.messlens.dao.ComplaintDao;
import com.messlens.dao.DatabaseManager;
import com.messlens.dao.MenuDao;
import com.messlens.dao.RatingDao;
import com.messlens.exception.AccessDeniedException;
import com.messlens.exception.ValidationException;
import com.messlens.model.Complaint;
import com.messlens.model.MealRating;
import com.messlens.model.MenuEntry;
import com.messlens.model.RatingSummary;
import com.messlens.model.User;
import com.messlens.service.AuthService;
import com.messlens.service.FeedbackService;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Checks the weekly menu, meal ratings and complaints end to end (run it with: java com.messlens.FeedbackFlowTest). */
public class FeedbackFlowTest {

    static int passed = 0, failed = 0;

    static void check(String name, boolean ok) {
        if (ok) passed++; else failed++;
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + name);
    }

    interface Action { void run() throws Exception; }

    static boolean throwsType(Class<? extends Exception> type, Action a) {
        try { a.run(); return false; } catch (Exception e) { return type.isInstance(e); }
    }

    static int count(String sql) throws Exception {
        try (Connection c = DatabaseManager.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public static void main(String[] args) throws Exception {
        DatabaseManager.initializeDatabase();
        try (Connection c = DatabaseManager.getConnection(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM complaints");
            s.execute("DELETE FROM meal_ratings");
        }
        AuthService auth = new AuthService();
        User student = auth.login("student1", "student123");
        User head = auth.login("head1", "head123");
        User worker = auth.login("worker1", "worker123");
        FeedbackService svc = new FeedbackService();
        MenuDao menuDao = new MenuDao();
        RatingDao ratingDao = new RatingDao();
        ComplaintDao complaintDao = new ComplaintDao();
        int hostel = head.getHostelId();
        User otherHead = new User(99999, "other", "x", "x", "HEAD", hostel + 1);

        System.out.println("Weekly menu");
        check("South, North and International each have 28 meals", count("SELECT COUNT(*) FROM mess_menu") == 84
                && menuDao.findByMessType("SOUTH").size() == 28 && menuDao.findByMessType("NORTH").size() == 28
                && menuDao.findByMessType("INTERNATIONAL").size() == 28);
        MenuEntry monB = menuDao.findByMessType("SOUTH").get(0);
        check("Monday breakfast comes from the official menu", monB.getDayNo() == 1 && monB.getMeal().equals("Breakfast")
                && monB.getItems().startsWith("Malabar Paratha"));
        check("a hostel starts on the South menu", menuDao.getHostelMessType(hostel).equals("SOUTH"));
        svc.setMessType(head, "North");
        check("mess head can switch the hostel to North", menuDao.getHostelMessType(hostel).equals("NORTH"));
        check("an invalid mess type is rejected", throwsType(ValidationException.class, () -> svc.setMessType(head, "Mars")));
        svc.saveMenu(head, "NORTH", 2, "Lunch", "Dal Makhani ,  Jeera Rice, Roti,,");
        String lunch = menuDao.findByMessType("NORTH").stream().filter(e -> e.getDayNo() == 2 && e.getMeal().equals("Lunch")).findFirst().get().getItems();
        check("edited menu is saved and tidied", lunch.equals("Dal Makhani, Jeera Rice, Roti"));
        check("South menu was not touched by the North edit", !menuDao.findByMessType("SOUTH").stream()
                .filter(e -> e.getDayNo() == 2 && e.getMeal().equals("Lunch")).findFirst().get().getItems().equals(lunch));
        check("head cannot edit another mess's menu", throwsType(AccessDeniedException.class, () -> svc.saveMenu(head, "SOUTH", 1, "Lunch", "X")));
        check("kitchen staff cannot edit the menu", throwsType(AccessDeniedException.class, () -> svc.saveMenu(worker, "NORTH", 1, "Lunch", "X")));
        check("empty menu text is rejected", throwsType(ValidationException.class, () -> svc.saveMenu(head, "NORTH", 1, "Lunch", " , ")));
        svc.setMessType(head, "South");

        System.out.println("Meal ratings");
        String today = LocalDate.now().toString();
        svc.rateMeal(student, "Lunch", 4, "Rice was good");
        svc.rateMeal(student, "Lunch", 2, "Changed my mind, rice was cold");
        check("rating the same meal twice keeps one row", count("SELECT COUNT(*) FROM meal_ratings") == 1);
        Map<String, MealRating> mine = ratingDao.findForUserOnDate(student.getId(), today);
        check("the latest rating wins", mine.get("Lunch").getStars() == 2 && mine.get("Lunch").getNote().contains("cold"));
        svc.rateMeal(student, "Dinner", 5, "");
        List<RatingSummary> sum = ratingDao.summaryForHostel(student.getHostelId(), today);
        check("summary has one row per rated meal", sum.size() == 2);
        check("average and count are correct", sum.stream().anyMatch(s -> s.getMeal().equals("Lunch") && s.getAverage() == 2.0 && s.getCount() == 1)
                && sum.stream().anyMatch(s -> s.getMeal().equals("Dinner") && s.getAverage() == 5.0));
        List<MealRating> notes = ratingDao.recentWithNotes(student.getHostelId(), 10);
        check("only ratings with comments are listed, with the student's name", notes.size() == 1 && notes.get(0).getStudentName() != null);
        check("0 and 6 stars are rejected", throwsType(ValidationException.class, () -> svc.rateMeal(student, "Lunch", 0, ""))
                && throwsType(ValidationException.class, () -> svc.rateMeal(student, "Lunch", 6, "")));
        check("unknown meal is rejected", throwsType(ValidationException.class, () -> svc.rateMeal(student, "Brunch", 3, "")));
        check("only students can rate", throwsType(AccessDeniedException.class, () -> svc.rateMeal(head, "Lunch", 3, "")));
        check("a very long comment is rejected", throwsType(ValidationException.class, () -> svc.rateMeal(student, "Lunch", 3, "x".repeat(301))));

        System.out.println("Complaints");
        svc.fileComplaint(student, "Hygiene", "Dinner", "There was a hair in my rice at dinner today.");
        svc.fileComplaint(student, "Quantity", null, "Curd runs out before nine every night.");
        List<Complaint> mineC = complaintDao.findByStudent(student.getId());
        check("student sees both complaints, newest first", mineC.size() == 2 && mineC.get(0).getCategory().equals("Quantity"));
        check("a complaint can be about no particular meal", mineC.get(0).getMeal() == null);
        check("complaints start OPEN", mineC.get(0).getStatus().equals("OPEN") && complaintDao.countOpen(hostel) == 2);
        check("too short a description is rejected", throwsType(ValidationException.class, () -> svc.fileComplaint(student, "Hygiene", null, "bad")));
        check("unknown category is rejected", throwsType(ValidationException.class, () -> svc.fileComplaint(student, "Gossip", null, "This is long enough text")));
        check("head cannot file a complaint", throwsType(AccessDeniedException.class, () -> svc.fileComplaint(head, "Hygiene", null, "This is long enough text")));
        int id = mineC.get(1).getId();
        check("a head of another hostel cannot reply", throwsType(AccessDeniedException.class, () -> svc.replyToComplaint(otherHead, id, "We will check")));
        check("a student cannot reply", throwsType(AccessDeniedException.class, () -> svc.replyToComplaint(student, id, "We will check")));
        check("empty reply is rejected", throwsType(ValidationException.class, () -> svc.replyToComplaint(head, id, "  ")));
        svc.replyToComplaint(head, id, "Sorry about that. The cook has been told to wear a hair net.");
        Complaint done = complaintDao.findById(id);
        check("reply saves and resolves the complaint", done.getStatus().equals("RESOLVED") && done.getReply().startsWith("Sorry") && done.getRepliedAt() != null);
        check("open count drops to 1", complaintDao.countOpen(hostel) == 1);
        check("head sees all complaints of the hostel", complaintDao.findByHostel(hostel).size() == 2);

        System.out.println("Withdrawing a complaint");
        svc.fileComplaint(student, "Delay in serving", "Lunch", "Lunch was served forty minutes late today.");
        int openId = complaintDao.findByStudent(student.getId()).get(0).getId();
        User otherStudent = new User(99998, "other", "x", "x", "STUDENT", hostel);
        check("a student cannot withdraw someone else's complaint", throwsType(AccessDeniedException.class, () -> svc.withdrawComplaint(otherStudent, openId)));
        check("the mess head cannot withdraw a complaint", throwsType(AccessDeniedException.class, () -> svc.withdrawComplaint(head, openId)));
        check("a complaint that already has a reply cannot be withdrawn", throwsType(ValidationException.class, () -> svc.withdrawComplaint(student, id)));
        svc.withdrawComplaint(student, openId);
        check("withdrawing an open complaint deletes it", complaintDao.findById(openId) == null && complaintDao.findByStudent(student.getId()).size() == 2);
        check("withdrawing it again says it no longer exists", throwsType(ValidationException.class, () -> svc.withdrawComplaint(student, openId)));

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
