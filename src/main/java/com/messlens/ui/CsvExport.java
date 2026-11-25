package com.messlens.ui;

import com.messlens.model.Upload;
import com.messlens.model.User;
import com.messlens.service.UploadService;
import com.messlens.util.AlertUtil;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;

/** "Export CSV" used by the mess head (opens in Excel). */
public final class CsvExport {

    private CsvExport() {}

    private static String csv(String v) {
        if (v == null) return "";
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    public static void run(User head, UploadService service) {
        int hostelId = head.getHostelId() != null ? head.getHostelId() : 1;
        if (!com.messlens.security.AccessDecisionService.INSTANCE.check(head, com.messlens.security.Action.EXPORT_RECORDS,
                com.messlens.security.Resource.hostel(hostelId)).permit) {
            AlertUtil.showWarning("Not allowed", "Your account may not export these records.");
            return;
        }
        List<Upload> all = service.getAllUploads(head, hostelId);
        if (all.isEmpty()) {
            AlertUtil.showInfo("Nothing to export", "There are no submissions for your hostel yet.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save report");
        chooser.setInitialFileName("messlens_report_" + LocalDate.now() + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV (opens in Excel)", "*.csv"));
        File file = chooser.showSaveDialog(AppNavigator.getPrimaryStage());
        if (file == null) return;

        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("ID,Hostel,Meal,Dish,Stage,Worker,Status,Submitted At,Decided At,Reviewer Remark\n");
        for (Upload u : all) {
            sb.append(u.getId()).append(',')
              .append(csv(u.getHostelName())).append(',')
              .append(csv(u.getMeal())).append(',')
              .append(csv(u.getDishName())).append(',')
              .append(csv(u.getStage())).append(',')
              .append(csv(u.getWorkerUsername())).append(',')
              .append(csv(u.getStatus())).append(',')
              .append(csv(u.getUploadedAt())).append(',')
              .append(csv(u.getApprovedAt())).append(',')
              .append(csv(u.getRemark())).append('\n');
        }
        try {
            Files.write(file.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
            UiFx.toast("Report saved: " + file.getName(), true);
        } catch (IOException ex) {
            AlertUtil.showError("Could not save the report", ex.getMessage());
        }
    }
}
