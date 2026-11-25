package com.messlens.ui;

import com.messlens.model.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Plain-language help for the signed-in role. The course/syllabus info lives at the very bottom. */
public class HelpPage implements Page {

    private final User user;
    private final Node view;

    public HelpPage(User user) {
        this.user = user;
        VBox content = new VBox(16);
        content.setPadding(new Insets(4, 32, 28, 32));
        build(content);
        this.view = Ui.scroll(content);
    }

    @Override public String id() { return "help"; }
    @Override public String navLabel() { return "Help"; }
    @Override public String icon() { return "help"; }
    @Override public String title() { return "Help"; }
    @Override public String subtitle() { return "How MessLens works, step by step."; }
    @Override public Node view() { return view; }
    @Override public double maxWidth() { return 900; }

    private void build(VBox content) {
        String role = user.getRole() == null ? "STUDENT" : user.getRole().toUpperCase();
        String[][] steps;
        String[][] faq;

        switch (role) {
            case "WORKER":
                steps = new String[][]{
                        {"Pick the meal and name the dish", "On the Capture page choose Breakfast, Lunch or Dinner and type or pick the dish."},
                        {"Photograph each stage", "Take one photo of the Ingredients, one while Cooking and one when Serving. The stage moves on automatically."},
                        {"Send for approval", "Press \"Send for approval\". The mess head will review the photo."},
                        {"Check the result", "Open My Submissions to see what was approved, and why a photo was rejected."}};
                faq = new String[][]{
                        {"What does Pending mean?", "The photo was sent but the mess head has not looked at it yet."},
                        {"Why was my photo rejected?", "The mess head must leave a reason. Click the photo in My Submissions to read it, then retake the photo."},
                        {"I sent the wrong photo", "While it is still Pending, click it in My Submissions and press Withdraw."},
                        {"Can I send the same photo twice?", "No. Every photo gets a unique fingerprint, so an old photo cannot be reused as new evidence."},
                        {"There is no camera on this computer", "Use \"Choose a photo from this computer\" on the Capture page."},
                        {"Taking photos on a Mac", "Run  brew install imagesnap  once in Terminal, then Take photo uses the built-in camera. If macOS asks, allow camera access for Terminal."},
                        {"Why does it say my photo looks old?", "Photos chosen from a file show when the file was saved. Fresh evidence should be taken while the food is being prepared."}};
                break;
            case "WARDEN":
                steps = new String[][]{
                        {"Open Second Review", "Every photo the mess head has decided on is listed. The badge shows how many still wait for you."},
                        {"Confirm or overturn", "Confirm keeps the head's decision. Overturn reverses it; you must give a reason and act within the review window."},
                        {"Check the records", "All Records lists every photo of your hostel. The pass rate counts decisions after your overturns."},
                        {"Verify the system", "Audit & Integrity re-checks the tamper-evident log and every stored photo."}};
                faq = new String[][]{
                        {"Why is Overturn greyed out?", "The review window for that decision has closed, or you uploaded the photo yourself."},
                        {"Can I review the same decision twice?", "No. A decision is countersigned once; the result is final and recorded in the audit log."},
                        {"What does 'Verify audit log' do?", "It recomputes the chain of authentication codes. Any edited, removed or inserted entry is reported."}};
                break;
            case "ADMIN":
                steps = new String[][]{
                        {"Check every mess", "Overview lists each mess, its pass rate and whether it has a mess head."},
                        {"Create a mess head or warden", "Open Staff, choose the role, pick the mess and give a username. A temporary password is generated."},
                        {"Share the login", "Copy the login details and send them to the person. They must choose their own password at first sign-in."},
                        {"Let heads add their kitchen staff", "Each mess head can create kitchen staff for their own mess. You can also do it from Staff."}};
                faq = new String[][]{
                        {"Someone forgot their password", "Open Staff and press Reset password. A new temporary password is shown once."},
                        {"Why can't I remove an account?", "Accounts that already sent or reviewed photos are kept so the audit trail stays complete."},
                        {"How do students get an account?", "They register themselves on the sign-in screen with their registration number and college email."}};
                break;
            case "HEAD":
                steps = new String[][]{
                        {"Open the Review Queue", "Photos waiting for you are listed oldest first. The number in the sidebar shows how many."},
                        {"Click a photo", "It opens large on the right, with the dish, meal, stage and who sent it."},
                        {"Approve or reject", "Add a note (the quick notes save typing) and press Approve. A note is required to Reject."},
                        {"Track progress", "Overview shows today's dishes and pass rate. All Records has every photo and a CSV export."},
                        {"Add your kitchen staff", "Open Kitchen Staff to create accounts for your cooks. Each gets a temporary password to change at first sign-in."}};
                faq = new String[][]{
                        {"What happens when I reject a photo?", "The worker sees your note in My Submissions and can retake the photo."},
                        {"Can I undo a decision?", "Not from the app. Decisions are kept as an audit trail."},
                        {"How do I get a report?", "Open All Records (or Overview) and press Export CSV. It opens in Excel."},
                        {"Why can't I see other hostels?", "The Review Queue and records only show your own hostel."}};
                break;
            default:
                steps = new String[][]{
                        {"Open the Food Timeline", "It shows food that the mess head has verified."},
                        {"Choose hostel, date and meal", "The list updates as soon as you change a choice."},
                        {"Click any photo", "See it large with the time it was taken and the reviewer's note."},
                        {"Compare hostels", "Hostel Quality shows how often each hostel's photos pass inspection."}};
                faq = new String[][]{
                        {"Why do I only see some photos?", "Only photos approved by the mess head are shown to students."},
                        {"What does 2/3 verified mean?", "Two of the three stages (Ingredients, Cooking, Serving) have been approved so far."},
                        {"What is the fingerprint in the photo window?", "A unique code of the photo. If the photo is changed even slightly, the code changes."}};
        }

        content.getChildren().add(stepsCard(steps));
        content.getChildren().add(faqCard(faq));
        content.getChildren().add(courseCard());
    }

    private Node stepsCard(String[][] steps) {
        VBox box = new VBox(16);
        box.getChildren().add(Ui.label("Quick start", "h2"));
        int i = 1;
        for (String[] s : steps) {
            Label dot = Ui.label(String.valueOf(i++), "step-dot", "step-dot-active");
            VBox text = new VBox(2, Ui.label(s[0], "h3"), Ui.wrap(s[1], "muted"));
            HBox row = new HBox(14, dot, text);
            row.setAlignment(Pos.TOP_LEFT);
            box.getChildren().add(row);
        }
        box.getStyleClass().add("card");
        return box;
    }

    private Node faqCard(String[][] faq) {
        VBox box = new VBox(14);
        box.getChildren().add(Ui.label("Common questions", "h2"));
        for (String[] q : faq) {
            box.getChildren().add(new VBox(2, Ui.label(q[0], "h3"), Ui.wrap(q[1], "muted")));
        }
        box.getStyleClass().add("card");
        return box;
    }

    private Node courseCard() {
        VBox inner = new VBox(8,
                Ui.wrap("Built for Advanced Java Programming (CSE 307), SRM University AP.", "body"),
                Ui.wrap("Unit 1 - OOP, inheritance, packages: the model, dao, service, ui and util packages.", "muted"),
                Ui.wrap("Unit 2 - Polymorphism and a custom exception hierarchy (MessLensException and subclasses).", "muted"),
                Ui.wrap("Unit 3 - Multithreading (daemon webcam thread) and byte streams (FileInputStream / FileOutputStream).", "muted"),
                Ui.wrap("Unit 4 - JDBC with MySQL, transactions, collections with Iterator.", "muted"),
                Ui.wrap("Unit 5 - Generics (Pair<K,V>, CrudRepository<T,ID>) and the JavaFX GUI (tables, combo boxes, dialogs, event handling).", "muted"));
        inner.setPadding(new Insets(12, 4, 4, 4));
        TitledPane tp = new TitledPane("About this project (course information)", inner);
        tp.setExpanded(false);
        return tp;
    }
}
