package com.techfix.app.activities;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.techfix.app.R;
import com.techfix.app.database.AppointmentDAO;

import com.techfix.app.database.DatabaseHelper;
import com.techfix.app.databinding.ActivityAppointmentDetailBinding;
import com.techfix.app.models.Appointment;
import com.techfix.app.models.PaymentStatus;
import com.techfix.app.models.RepairStatus;
import com.techfix.app.models.UserRole;
import com.techfix.app.session.SessionManager;
import com.techfix.app.util.Feedback;
import com.techfix.app.util.RepairPhotoStorage;
import com.techfix.app.util.WindowInsetsHelper;
import java.util.List;

public class AppointmentDetailActivity extends AppCompatActivity {
    private ActivityAppointmentDetailBinding binding;
    private AppointmentDAO appointments;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAppointmentDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowInsetsHelper.applyHeader(binding.detailContent);
        WindowInsetsHelper.applyBottomInset(binding.detailFooter);
        appointments = new AppointmentDAO(DatabaseHelper.getInstance(this));

        long id = getIntent().getLongExtra("appointmentId", -1);
        render(id);

        SessionManager session = new SessionManager(this);
        if (session.getRole() == UserRole.STAFF) {
            binding.detailPayButton.setVisibility(android.view.View.GONE);
        } else {
            binding.detailPayButton.setOnClickListener(v -> pay(id));
            if (!"Pending".equals(paymentState(id))) binding.detailPayButton.setVisibility(android.view.View.GONE);
        }
    }

    private String paymentState(long id) {
        Appointment a = appointments.get(id);
        return a == null ? "" : a.payment;
    }

    private void render(long id) {
        Appointment a = appointments.get(id);
        if (a == null) { finish(); return; }
        binding.detailDocketNumber.setText("Docket #" + String.format("%06d", a.id));
        binding.detailTitle.setText(a.device + " \u00B7 " + a.service);
        binding.detailProblem.setText(a.problem);
        binding.detailBranchValue.setText(a.branch);
        binding.detailTechValue.setText(a.technician.isEmpty() ? "Unassigned" : a.technician);
        binding.detailSlotValue.setText(a.timeSlot.isEmpty() ? "Not scheduled" : a.timeSlot);
        binding.detailBookedValue.setText(a.createdAt.isEmpty() ? "\u2014" : a.createdAt);
        binding.detailStatus.setText(a.status);

        if (a.photoUri.isEmpty()) {
            binding.detailPhotoCard.setVisibility(android.view.View.GONE);
        } else {
            binding.detailPhotoCard.setVisibility(android.view.View.VISIBLE);
            RepairPhotoStorage.load(binding.detailPhotoImage, a.photoUri);
        }

        boolean paid = PaymentStatus.PAID.label.equals(a.payment);
        binding.detailAmount.setText("Rs " + String.format("%,.0f", a.price));
        binding.detailPaymentState.setText(a.payment);
        binding.detailPaymentState.setTextColor(getColor(paid ? R.color.success : R.color.warning));

        List<RepairStatus> history = appointments.statusHistory(id);
        binding.timelineList.removeAllViews();
        if (history.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No status updates yet — the workshop will update this docket once work begins.");
            empty.setTextSize(13);
            empty.setTextColor(getColor(R.color.muted_text));
            empty.setBackgroundResource(R.drawable.bg_field);
            empty.setPadding(18, 14, 18, 14);
            binding.timelineList.addView(empty);
            return;
        }
        for (int i = 0; i < history.size(); i++) {
            RepairStatus step = history.get(i);
            View row = getLayoutInflater().inflate(R.layout.item_timeline_step, binding.timelineList, false);
            boolean isLatest = i == history.size() - 1;

            TextView status = row.findViewById(R.id.stepStatus);
            status.setText(step.status);
            status.setTextColor(getColor(isLatest ? R.color.primary : R.color.ink));

            ((TextView) row.findViewById(R.id.stepDate)).setText(step.updatedAt);

            TextView note = row.findViewById(R.id.stepNote);
            if (step.note.isEmpty()) {
                note.setVisibility(android.view.View.GONE);
            } else {
                note.setVisibility(android.view.View.VISIBLE);
                note.setText(step.note);
            }

            if (isLatest) row.findViewById(R.id.timelineLine).setVisibility(android.view.View.GONE);
            binding.timelineList.addView(row);
        }
    }

    private void pay(long id) {
        Appointment a = appointments.get(id);
        if (a == null) { finish(); return; }
        if (!PaymentStatus.PENDING.label.equals(a.payment)) { Feedback.error(binding.getRoot(), "Already paid"); return; }
        String[] methods = {"Cash at counter", "Card", "Bank transfer"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Pay Rs " + (long) a.price + " \u00B7 " + a.service)
                .setItems(methods, (d, w) -> {
                    boolean ok = appointments.pay(id, a.price, methods[w]);
                    if (ok) {
                        Feedback.success(binding.getRoot(), "Payment recorded \u00B7 " + methods[w]);
                        com.techfix.app.util.Analytics.log(this, "payment_made", "method", methods[w]);
                    }
                    else Feedback.error(binding.getRoot(), "Payment failed");
                    binding.detailPayButton.setVisibility(android.view.View.GONE);
                    render(id);
                }).setNegativeButton("Cancel", null).show();
    }
}
