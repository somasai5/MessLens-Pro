package com.messlens.service;

import com.messlens.dao.ComplaintDao;
import com.messlens.dao.MenuDao;
import com.messlens.dao.RatingDao;
import com.messlens.exception.AccessDeniedException;
import com.messlens.exception.MessLensException;
import com.messlens.exception.ValidationException;
import com.messlens.model.Complaint;
import com.messlens.model.MessInfo;
import com.messlens.model.User;

import com.messlens.util.Config;
import com.messlens.util.PathUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Rules for the weekly menu, meal ratings and complaints. The screens call this class, which checks who is allowed
 * to do what and whether the input is valid, and only then talks to the database (DAO classes).
 */
public class FeedbackService {

    private final MenuDao menuDao = new MenuDao();
    private final RatingDao ratingDao = new RatingDao();
    private final ComplaintDao complaintDao = new ComplaintDao();

    // ------------------------------------------------------------------ ratings (students)

    /** A student rates one of today's meals from 1 to 5 stars, with an optional comment. */
    public void rateMeal(User user, String meal, int stars, String note) throws MessLensException {
        requireRole(user, "STUDENT", "students");
        int hostelId = hostelOf(user);
        if (!MessInfo.isMeal(meal)) throw new ValidationException("Choose a meal to rate.");
        if (stars < 1 || stars > 5) throw new ValidationException("Tap the stars to give 1 to 5.");
        String clean = note == null ? "" : note.trim();
        if (clean.length() > 300) throw new ValidationException("Please keep the comment under 300 characters.");
        try {
            ratingDao.save(user.getId(), hostelId, LocalDate.now().toString(), meal, stars, clean);
        } catch (SQLException e) {
            throw new MessLensException("Could not save your rating. Please try again.", e);
        }
    }

    // ------------------------------------------------------------------ complaints

    /** A student sends a complaint to the mess head of their own hostel. */
    public void fileComplaint(User user, String category, String meal, String description) throws MessLensException {
        fileComplaint(user, category, meal, description, null);
    }

    /** Same, with an optional photo (JPG or PNG bytes, max 5 MB). Pass null for no photo. */
    public void fileComplaint(User user, String category, String meal, String description, byte[] image) throws MessLensException {
        requireRole(user, "STUDENT", "students");
        int hostelId = hostelOf(user);
        if (!MessInfo.isCategory(category)) throw new ValidationException("Choose what the complaint is about.");
        if (meal != null && !MessInfo.isMeal(meal)) throw new ValidationException("Choose a valid meal.");
        String text = description == null ? "" : description.trim();
        if (text.length() < 10) throw new ValidationException("Please describe the problem in at least 10 characters.");
        if (text.length() > 500) throw new ValidationException("Please keep the complaint under 500 characters.");
        String ext = null;
        if (image != null && image.length > 0) {
            if (image.length > 5 * 1024 * 1024) throw new ValidationException("The photo is too big. Please choose one under 5 MB.");
            ext = imageExtension(image);
            if (ext == null) throw new ValidationException("The photo must be a JPG or PNG file.");
        }
        File saved = null;
        try {
            if (ext != null) {
                Config.ensureEvidenceDirExists();
                saved = new File(Config.getEvidenceDir(), "complaint_" + System.currentTimeMillis() + "_"
                        + java.util.UUID.randomUUID().toString().substring(0, 8) + "." + ext);
                Files.write(saved.toPath(), image);
            }
            complaintDao.insert(user.getId(), hostelId, category, meal, text, saved == null ? null : saved.getPath());
        } catch (SQLException | IOException e) {
            if (saved != null) saved.delete();
            throw new MessLensException("Could not send your complaint. Please try again.", e);
        }
    }

    /** "jpg" or "png" when the bytes really are that kind of image, otherwise null. */
    private static String imageExtension(byte[] b) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "jpg";
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "png";
        return null;
    }

    /** A student takes back one of their own complaints, as long as the mess head has not replied yet. */
    public void withdrawComplaint(User user, int complaintId) throws MessLensException {
        requireRole(user, "STUDENT", "students");
        Complaint c = complaintDao.findById(complaintId);
        if (c == null) throw new ValidationException("That complaint no longer exists.");
        if (c.getStudentId() != user.getId()) {
            throw new AccessDeniedException("You can only withdraw your own complaints.", "FB-OWNER");
        }
        if (!"OPEN".equals(c.getStatus())) {
            throw new ValidationException("The mess head has already read or replied to this complaint, so it can no longer be withdrawn.");
        }
        try {
            int gone = complaintDao.deleteOpen(complaintId, user.getId());
            if (gone > 0 && c.hasImage()) {
                File f = PathUtil.resolve(c.getImagePath());
                if (f != null) f.delete();
            }
        } catch (SQLException e) {
            throw new MessLensException("Could not withdraw the complaint. Please try again.", e);
        }
    }

    /** The mess head marks a complaint from their own hostel as read; no reply is needed. */
    public void markComplaintRead(User head, int complaintId) throws MessLensException {
        requireRole(head, "HEAD", "mess heads");
        int hostelId = hostelOf(head);
        Complaint c = complaintDao.findById(complaintId);
        if (c == null) throw new ValidationException("That complaint no longer exists.");
        if (c.getHostelId() != hostelId) {
            throw new AccessDeniedException("You can only open complaints from your own hostel.", "FB-HOSTEL");
        }
        try {
            complaintDao.markRead(complaintId);
        } catch (SQLException e) {
            throw new MessLensException("Could not mark the complaint as read. Please try again.", e);
        }
    }

    /** The mess head answers a complaint from their own hostel; the complaint is then marked RESOLVED. */
    public void replyToComplaint(User head, int complaintId, String reply) throws MessLensException {
        requireRole(head, "HEAD", "mess heads");
        int hostelId = hostelOf(head);
        Complaint c = complaintDao.findById(complaintId);
        if (c == null) throw new ValidationException("That complaint no longer exists.");
        if (c.getHostelId() != hostelId) {
            throw new AccessDeniedException("You can only answer complaints from your own hostel.", "FB-HOSTEL");
        }
        String text = reply == null ? "" : reply.trim();
        if (text.length() < 3) throw new ValidationException("Write a short reply for the student.");
        if (text.length() > 500) throw new ValidationException("Please keep the reply under 500 characters.");
        try {
            complaintDao.reply(complaintId, text, head.getId());
        } catch (SQLException e) {
            throw new MessLensException("Could not save the reply. Please try again.", e);
        }
    }

    // ------------------------------------------------------------------ menu (mess head)

    /** The mess head edits the dishes of one meal in the menu of the mess type their hostel serves. */
    public void saveMenu(User head, String messType, int dayNo, String meal, String items) throws MessLensException {
        requireRole(head, "HEAD", "mess heads");
        int hostelId = hostelOf(head);
        if (!messType.equals(menuDao.getHostelMessType(hostelId))) {
            throw new AccessDeniedException("You can only edit the menu of your own mess.", "FB-MESS");
        }
        if (dayNo < 1 || dayNo > 7 || !MessInfo.isMeal(meal)) throw new ValidationException("Unknown day or meal.");
        String clean = items == null ? "" : items.trim().replaceAll("\\s*,\\s*", ", ").replaceAll("(, )+$", "");
        if (clean.isEmpty()) throw new ValidationException("Add at least one dish (separate dishes with commas).");
        if (clean.length() > 600) throw new ValidationException("That list is too long (600 characters at most).");
        try {
            menuDao.upsert(messType, dayNo, meal, clean, head.getId());
        } catch (SQLException e) {
            throw new MessLensException("Could not save the menu. Please try again.", e);
        }
    }

    /** The mess head chooses which menu (South / North / International) their hostel serves. */
    public void setMessType(User head, String messType) throws MessLensException {
        requireRole(head, "HEAD", "mess heads");
        int hostelId = hostelOf(head);
        if (MessInfo.typeOf(messType) == null) throw new ValidationException("Choose South, North or International.");
        try {
            menuDao.setHostelMessType(hostelId, MessInfo.typeOf(messType));
        } catch (SQLException e) {
            throw new MessLensException("Could not change the mess type.", e);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void requireRole(User user, String role, String who) throws AccessDeniedException {
        if (user == null || user.getRole() == null || !user.getRole().equalsIgnoreCase(role)) {
            throw new AccessDeniedException("Only " + who + " can do this.", "FB-ROLE");
        }
    }

    private static int hostelOf(User user) throws ValidationException {
        if (user.getHostelId() == null) throw new ValidationException("Your account is not linked to a hostel.");
        return user.getHostelId();
    }
}
