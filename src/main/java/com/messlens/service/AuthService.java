package com.messlens.service;

import com.messlens.audit.AuditEvent;
import com.messlens.audit.AuditLog;
import com.messlens.dao.UserDao;
import com.messlens.exception.AuthenticationException;
import com.messlens.exception.MessLensException;
import com.messlens.exception.ValidationException;
import com.messlens.model.User;
import com.messlens.security.AccessDecisionService;
import com.messlens.security.Action;
import com.messlens.security.LoginThrottle;
import com.messlens.security.PasswordPolicy;
import com.messlens.security.Resource;
import com.messlens.security.Roles;
import com.messlens.security.SessionManager;
import com.messlens.util.SecurityUtil;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Authentication, registration, staff accounts and the signed-in session.
 *
 * Follows NIST SP 800-63B: slow salted password hashing (PBKDF2), length-based password rules with a
 * common-password blocklist, rate limiting of wrong passwords, and session timeouts (see SessionManager).
 * Who may create or manage which account is decided by the Policy Decision Point.
 */
public class AuthService {

    /** College mail only: name@srmap.edu.in */
    public static final String EMAIL_DOMAIN = "@srmap.edu.in";
    private static final Pattern EMAIL = Pattern.compile("^[a-z0-9._%+\\-]+@srmap\\.edu\\.in$");
    private static final Pattern REG_NO = Pattern.compile("^[A-Za-z0-9]{6,20}$");
    private static final Pattern STAFF_USERNAME = Pattern.compile("^[a-z0-9._\\-]{3,30}$");
    private static final String TEMP_CHARS = "abcdefghjkmnpqrstuvwxyzACDEFGHJKLMNPQRTUVWXYZ2346789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String BAD_LOGIN = "Wrong registration number / username or password.";

    /** Hash checked when the username does not exist, so the response time does not reveal which accounts exist. */
    private static final String DUMMY_SALT = SecurityUtil.generateSalt();
    private static String dummyHash;

    private final UserDao userDao;
    private final AccessDecisionService pdp = AccessDecisionService.INSTANCE;
    private static User currentUser;

    public AuthService() {
        this.userDao = new UserDao();
    }

    public AuthService(UserDao userDao) {
        this.userDao = userDao;
    }

    // ------------------------------------------------------------------ sign in

    public User login(String username, String password) throws AuthenticationException, ValidationException {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            throw new ValidationException("Enter your registration number (or username) and password.");
        }
        String name = username.trim();

        long locked = LoginThrottle.secondsLocked(name);
        if (locked > 0) {
            throw new AuthenticationException("Too many wrong attempts. Try again in " + humanTime(locked) + ".");
        }

        User user = userDao.findByUsername(name);
        boolean matched;
        if (user == null) {
            if (dummyHash == null) dummyHash = SecurityUtil.hashPassword("not-a-real-password", DUMMY_SALT);
            SecurityUtil.verifyPassword(password, DUMMY_SALT, dummyHash);
            matched = false;
        } else {
            matched = SecurityUtil.verifyPassword(password, user.getSalt(), user.getPasswordHash());
        }

        if (!matched) {
            long lockNow = LoginThrottle.recordFailure(name);
            AuditLog.logQuietly(AuditEvent.anonymous(name, lockNow > 0 ? "LOGIN_LOCKED" : "LOGIN_FAILED").outcome("FAIL")
                    .detail(user == null ? "unknown username" : "wrong password"));
            if (lockNow > 0) {
                throw new AuthenticationException("Too many wrong attempts. Try again in " + humanTime(lockNow) + ".");
            }
            throw new AuthenticationException(BAD_LOGIN);
        }

        LoginThrottle.recordSuccess(name);
        if (SecurityUtil.needsRehash(user.getPasswordHash())) {
            // old fast SHA-256 account: store the same password with PBKDF2 now that we hold the plaintext
            String salt = SecurityUtil.generateSalt();
            if (userDao.rehashPassword(user.getId(), SecurityUtil.hashPassword(password, salt), salt)) {
                User refreshed = userDao.findById(user.getId());
                if (refreshed != null) user = refreshed;
            }
        }
        currentUser = user;
        SessionManager.start(user);
        AuditLog.logQuietly(AuditEvent.by(user, "LOGIN_SUCCESS").resource("user", user.getId()));
        return user;
    }

    private static String humanTime(long seconds) {
        if (seconds < 90) return seconds + " seconds";
        return ((seconds + 59) / 60) + " minutes";
    }

    /** Change own password (also used for the forced change after a temporary password). */
    public void changePassword(String oldPassword, String newPassword) throws AuthenticationException, ValidationException {
        if (currentUser == null) throw new AuthenticationException("Please sign in first.");
        if (!SecurityUtil.verifyPassword(oldPassword, currentUser.getSalt(), currentUser.getPasswordHash())) {
            throw new AuthenticationException("Your current password is not correct.");
        }
        String problem = PasswordPolicy.check(newPassword, currentUser.getUsername(), currentUser.getFullName(), emailName(currentUser.getEmail()));
        if (problem != null) throw new ValidationException(problem);
        if (newPassword.equals(oldPassword)) throw new ValidationException("New password must be different from the current one.");

        String salt = SecurityUtil.generateSalt();
        String hash = SecurityUtil.hashPassword(newPassword, salt);
        if (!userDao.updatePassword(currentUser.getId(), hash, salt)) {
            throw new ValidationException("Could not save the new password. Please try again.");
        }
        User refreshed = userDao.findById(currentUser.getId());
        if (refreshed != null) currentUser = refreshed;
        AuditLog.logQuietly(AuditEvent.by(currentUser, "PASSWORD_CHANGED").resource("user", currentUser.getId()));
    }

    private static String emailName(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    // ------------------------------------------------------------------ registration

    /**
     * Student self-registration. The registration number becomes the username,
     * so students sign in with registration number + password.
     */
    public User registerStudent(String fullName, String regNo, String email, Integer hostelId,
                                String password, String confirm) throws ValidationException {
        fullName = fullName == null ? "" : fullName.trim();
        regNo = regNo == null ? "" : regNo.trim().toUpperCase();
        email = email == null ? "" : email.trim().toLowerCase();

        if (fullName.length() < 2) throw new ValidationException("Please enter your full name.");
        if (!REG_NO.matcher(regNo).matches()) {
            throw new ValidationException("Enter your registration number (letters and digits only, 6-20 characters).");
        }
        if (!EMAIL.matcher(email).matches()) {
            throw new ValidationException("Use your college email, which ends with " + EMAIL_DOMAIN + ".");
        }
        if (hostelId == null) throw new ValidationException("Choose your hostel / mess.");
        String problem = PasswordPolicy.check(password, regNo, fullName, emailName(email));
        if (problem != null) throw new ValidationException(problem);
        if (!password.equals(confirm)) throw new ValidationException("The two passwords do not match.");
        if (userDao.findByUsername(regNo) != null) {
            throw new ValidationException("An account with this registration number already exists. Try signing in.");
        }
        if (userDao.findByEmail(email) != null) {
            throw new ValidationException("This email is already registered.");
        }

        User u = newUser(regNo, password, Roles.STUDENT, hostelId, false);
        u.setFullName(fullName);
        u.setEmail(email);
        if (!userDao.insert(u)) throw new ValidationException("Could not create your account. Please try again.");
        User created = userDao.findByUsername(regNo);
        AuditLog.logQuietly(AuditEvent.by(created, "STUDENT_REGISTERED").resource("user", created == null ? null : created.getId()));
        return created;
    }

    // ------------------------------------------------------------------ staff management

    /**
     * Creates a mess head, warden or kitchen staff account (administrator), or a kitchen staff account (mess head of that mess).
     * Returns the generated temporary password; the new user must change it at first sign-in.
     */
    public String createStaff(User actor, String role, String fullName, String username, Integer hostelId)
            throws MessLensException {
        role = role == null ? "" : role.toUpperCase();
        if (!role.equals(Roles.HEAD) && !role.equals(Roles.WORKER) && !role.equals(Roles.WARDEN)) {
            throw new ValidationException("Choose a role.");
        }
        if (actor != null && Roles.HEAD.equalsIgnoreCase(actor.getRole())) hostelId = actor.getHostelId();
        pdp.require(actor, Action.CREATE_STAFF, Resource.of("staff").hostelId(hostelId).targetRole(role));
        if (hostelId == null) throw new ValidationException("Choose the hostel / mess for this account.");

        fullName = fullName == null ? "" : fullName.trim();
        username = username == null ? "" : username.trim().toLowerCase();
        if (fullName.length() < 2) throw new ValidationException("Please enter the person's full name.");
        if (!STAFF_USERNAME.matcher(username).matches()) {
            throw new ValidationException("Username: 3-30 characters, lowercase letters, digits, dot, dash or underscore.");
        }
        if (userDao.findByUsername(username) != null) throw new ValidationException("That username is already taken.");

        String temp = randomPassword();
        User u = newUser(username, temp, role, hostelId, true);
        u.setFullName(fullName);
        if (!userDao.insert(u)) throw new ValidationException("Could not create the account. Please try again.");
        AuditLog.logQuietly(AuditEvent.by(actor, "STAFF_CREATED").resource("user", null).hostel(hostelId)
                .detail(role + " " + username));
        return temp;
    }

    /** Gives a staff member a new temporary password (they must change it at next sign-in). */
    public String resetStaffPassword(User actor, User target) throws MessLensException {
        requireManageable(actor, target);
        String temp = randomPassword();
        String salt = SecurityUtil.generateSalt();
        if (!userDao.setTemporaryPassword(target.getId(), SecurityUtil.hashPassword(temp, salt), salt)) {
            throw new ValidationException("Could not reset the password. Please try again.");
        }
        LoginThrottle.recordSuccess(target.getUsername()); // a reset also clears a lock-out
        AuditLog.logQuietly(AuditEvent.by(actor, "PASSWORD_RESET").resource("user", target.getId()).hostel(target.getHostelId())
                .detail(target.getUsername()));
        return temp;
    }

    /** Removes a staff account. Accounts that already have photos or decisions are kept to protect the audit trail. */
    public void removeStaff(User actor, User target) throws MessLensException {
        requireManageable(actor, target);
        if (userDao.hasActivity(target.getId())) {
            throw new ValidationException(target.displayName()
                    + " already has photos or decisions on record, so the account cannot be removed.");
        }
        if (!userDao.delete(target.getId())) throw new ValidationException("Could not remove the account.");
        AuditLog.logQuietly(AuditEvent.by(actor, "STAFF_REMOVED").resource("user", target.getId()).hostel(target.getHostelId())
                .detail(target.getRole() + " " + target.getUsername()));
    }

    public List<User> listStaff(User actor) {
        if (actor == null || !pdp.check(actor, Action.VIEW_STAFF, Resource.of("staff")).permit) return new ArrayList<>();
        if (Roles.ADMIN.equalsIgnoreCase(actor.getRole())) return userDao.findByRoles(null, Roles.HEAD, Roles.WARDEN, Roles.WORKER);
        return userDao.findByRoles(actor.getHostelId(), Roles.WORKER);
    }

    private void requireManageable(User actor, User target) throws MessLensException {
        if (actor == null || target == null) throw new ValidationException("Account not found.");
        pdp.require(actor, Action.MANAGE_STAFF, Resource.of("staff").id(target.getId()).hostelId(target.getHostelId()).targetRole(target.getRole()));
    }

    private static User newUser(String username, String password, String role, Integer hostelId, boolean mustChange) {
        String salt = SecurityUtil.generateSalt();
        User u = new User(0, username, SecurityUtil.hashPassword(password, salt), salt, role, hostelId);
        u.setMustChangePassword(mustChange);
        return u;
    }

    private static String randomPassword() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(TEMP_CHARS.charAt(RANDOM.nextInt(TEMP_CHARS.length())));
        return sb.toString();
    }

    // ------------------------------------------------------------------ session

    /** Clears the current session on logout. */
    public static void logout() {
        if (currentUser != null) {
            AuditLog.logQuietly(AuditEvent.by(currentUser, "LOGOUT").resource("user", currentUser.getId()));
        }
        currentUser = null;
        SessionManager.end();
    }

    /** Session ended by the idle / absolute timeout. */
    public static void expireSession(String reason) {
        if (currentUser != null) {
            AuditLog.logQuietly(AuditEvent.by(currentUser, "SESSION_EXPIRED").resource("user", currentUser.getId()).detail(reason));
        }
        currentUser = null;
        SessionManager.end();
    }

    public static User getCurrentUser() {
        return currentUser;
    }
}
