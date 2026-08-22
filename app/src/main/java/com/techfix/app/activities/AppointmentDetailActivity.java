package com.techfix.app.activities;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.snackbar.Snackbar;
import com.techfix.app.R;
import com.techfix.app.database.AppointmentDAO;

import com.techfix.app.database.DatabaseHelper;
import com.techfix.app.databinding.ActivityAppointmentDetailBinding;
import com.techfix.app.models.Appointment;
import com.techfix.app.models.PaymentStatus;
import com.techfix.app.models.RepairStatus;
import com.techfix.app.util.Feedback;
import com.techfix.app.util.WindowInsetsHelper;
import java.util.List;

/** Shows one repair's full details and its live progress timeline. */
public class AppointmentDetailActivity extends AppCompatActivity {
    private ActivityAppointmentDetailBinding binding;
    private AppointmentDAO appointments;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAppointmentDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Edge-to-edge: pad the 64dp header below status bar/cutout and keep the Pay footer above gesture nav/IME.
        WindowInsetsHelper.applyHeader(binding.detailHeader);
        WindowInsetsHelper.applyBottomInset(binding.detailFooter);
        appointments = new AppointmentDAO(DatabaseHelper.getInstance(this));

        long id = getIntent().getLongExtra("appointmentId", -1);
        render(id);
        binding.detailPayButton.setOnClickListener(v -> pay(id));
        if (!"Pending".equals(paymentState(id))) binding.detailPayButton.setVisibility(android.view.View.GONE);
    }

    private String paymentState(long id) {
        Appointment a = appointments.get(id);
        return a == null ? "" : a.payment;
    }

    private void render(long id) {
        Appointment a = appointments.get(id);
        if (a == null) { finish(); return; }
        binding.detailTitle.setText("#" + a.id + " \u00B7 " + a.device + " \u00B7 " + a.service);
        binding.detailProblem.setText(a.problem);
        binding.detailMeta.setText(a.branch + " \u00B7 " + a.technician
                + (a.timeSlot.isEmpty() ? "" : " \u00B7 Slot: " + a.timeSlot)
                + (a.createdAt.isEmpty() ? "" : "\nBooked " + a.createdAt));
        binding.detailPayment.setText("Price Rs " + (long) a.price + " \u00B7 Payment: " + a.payment);
        binding.detailStatus.setText(a.status);

        List<RepairStatus> history = appointments.statusHistory(id);
        binding.timelineList.removeAllViews();
        for (int i = 0; i < history.size(); i++) {
            RepairStatus step = history.get(i);
            TextView row = new TextView(this);
            String bullet = i == history.size() - 1 ? "\u25CF  " : "\u25CB  ";
            row.setText(bullet + step.status + "\n     " + step.updatedAt + (step.note.isEmpty() ? "" : " \u2014 " + step.note));
            row.setTextSize(14);
            row.setTextColor(getColor(i == history.size() - 1 ? R.color.techfix_blue_dark : R.color.techfix_muted));
            row.setBackgroundResource(R.drawable.bg_field);
            row.setPadding(18, 14, 18, 14);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.topMargin = 8;
            binding.timelineList.addView(row, lp);
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
                    if (ok) Feedback.success(binding.getRoot(), "Payment recorded \u00B7 " + methods[w]);
                    else Feedback.error(binding.getRoot(), "Payment failed");
                    binding.detailPayButton.setVisibility(android.view.View.GONE);
                    render(id);
                }).setNegativeButton("Cancel", null).show();
    }
}
