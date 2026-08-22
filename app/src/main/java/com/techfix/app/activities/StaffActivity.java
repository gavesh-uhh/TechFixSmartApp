package com.techfix.app.activities;

import com.techfix.app.R;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.snackbar.Snackbar;
import com.techfix.app.database.AppointmentDAO;
import com.techfix.app.adapters.StaffAppointmentAdapter;
import com.techfix.app.database.DatabaseHelper;
import com.techfix.app.database.SampleRepairDAO;
import com.techfix.app.database.ServiceDAO;
import com.techfix.app.database.SparePartDAO;
import com.techfix.app.database.TechnicianDAO;
import com.techfix.app.databinding.ActivityStaffBinding;
import com.techfix.app.models.AppointmentStatus;
import com.techfix.app.models.Technician;
import com.techfix.app.session.SessionManager;
import com.techfix.app.util.WindowInsetsHelper;
import java.util.List;

public class StaffActivity extends AppCompatActivity {
    private ActivityStaffBinding binding;
    private DatabaseHelper helper;
    private ArrayAdapter<String> techAdapter;
    private TechnicianDAO technicianDAO;
    private StaffAppointmentAdapter queueAdapter;

    private Uri pendingSampleUri;

    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
                    ok -> { if (ok) publishSample(); });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SessionManager session = new SessionManager(this);
        if (!session.isLoggedIn()) { goHome(); return; }
        binding = ActivityStaffBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.apply(binding.staffHeader, binding.staffContent);
        helper = DatabaseHelper.getInstance(this);

        binding.statusSpinner.setAdapter(new ArrayAdapter<>(this, R.layout.item_dropdown, AppointmentStatus.labels()));
        binding.serviceSpinner.setAdapter(new ArrayAdapter<>(this, R.layout.item_dropdown, new ServiceDAO(helper).all()));

        technicianDAO = new TechnicianDAO(helper);
        techAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, techLabels(technicianDAO.all()));
        binding.techSpinner.setAdapter(techAdapter);

        binding.updateStatusButton.setOnClickListener(this::updateStatus);
        binding.updatePriceButton.setOnClickListener(this::updatePrice);
        binding.restockButton.setOnClickListener(v -> {
            new SparePartDAO(helper).restock(
                    binding.partInput.getText().toString(), binding.branchInput.getText().toString(), 1);
            Snackbar.make(v, "Part stock increased", Snackbar.LENGTH_LONG).show();
        });
        binding.toggleTechButton.setOnClickListener(v -> {
            int pos = binding.techSpinner.getSelectedItemPosition();
            if (pos < 0) return;
            Technician t = technicianDAO.all().get(pos);
            technicianDAO.setAvailability(t.name, !t.available);
            techAdapter.clear(); techAdapter.addAll(techLabels(technicianDAO.all())); techAdapter.notifyDataSetChanged();
            Snackbar.make(v, t.name + (t.available ? " set unavailable" : " set available"), Snackbar.LENGTH_LONG).show();
        });
        binding.addServiceButton.setOnClickListener(this::addService);
        binding.addSampleButton.setOnClickListener(this::captureSample);
        binding.logoutButton.setOnClickListener(v -> { session.logout(); goHome(); });

        binding.staffTabs.addTab(binding.staffTabs.newTab().setText("Queue"));
        binding.staffTabs.addTab(binding.staffTabs.newTab().setText("Services"));
        binding.staffTabs.addTab(binding.staffTabs.newTab().setText("Workshop"));
        binding.staffTabs.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) { showPanel(tab.getPosition()); }
            @Override public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) { }
            @Override public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) { }
        });
        showPanel(0);

        queueAdapter = new StaffAppointmentAdapter(this::showStatusDialog);
        binding.appointmentList.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        binding.appointmentList.setAdapter(queueAdapter);
        refreshQueue();
    }

    private void showPanel(int position) {
        binding.tabQueue.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        binding.tabServices.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
        binding.tabWorkshop.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
    }

    private void refreshQueue() {
        queueAdapter.submit(new AppointmentDAO(helper).all());
    }

    /** Tap a repair in the queue to move it to the next workflow stage. */
    private void showStatusDialog(com.techfix.app.models.Appointment a) {
        String[] labels = AppointmentStatus.labels();
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("#" + a.id + " · " + a.service)
                .setItems(labels, (d, w) -> {
                    new AppointmentDAO(helper).updateStatus(a.id, labels[w]);
                    refreshQueue();
                    Snackbar.make(binding.getRoot(), "#" + a.id + " → " + labels[w], Snackbar.LENGTH_LONG).show();
                }).setNegativeButton("Cancel", null).show();
    }


    private void updateStatus(View v) {
        try {
            new AppointmentDAO(helper).updateStatus(Long.parseLong(binding.appointmentIdInput.getText().toString()),
                    (String) binding.statusSpinner.getSelectedItem());
            Snackbar.make(v, "Status updated", Snackbar.LENGTH_LONG).show();
            refreshQueue();
        } catch (Exception e) { binding.appointmentIdInput.setError("Enter an appointment ID"); }
    }

    private void updatePrice(View v) {
        try {
            ServiceDAO services = new ServiceDAO(helper);
            services.updatePrice(services.serviceName((String) binding.serviceSpinner.getSelectedItem()),
                    Double.parseDouble(binding.priceInput.getText().toString()));
            Snackbar.make(v, "Price updated", Snackbar.LENGTH_LONG).show();
        } catch (Exception e) { binding.priceInput.setError("Enter a valid price"); }
    }

    private void addService(View v) {
        try {
            String name = binding.newServiceName.getText().toString();
            String category = binding.newServiceCategory.getText().toString();
            double price = Double.parseDouble(binding.newServicePrice.getText().toString());
            if (name.trim().isEmpty() || price <= 0) { binding.newServiceName.setError("Enter name and valid price"); return; }
            boolean ok = new ServiceDAO(helper).add(name, category.isEmpty() ? "General" : category, price);
            binding.newServiceName.setText(""); binding.newServiceCategory.setText(""); binding.newServicePrice.setText("");
            binding.serviceSpinner.setAdapter(new ArrayAdapter<>(this, R.layout.item_dropdown, new ServiceDAO(helper).all()));
            Snackbar.make(v, ok ? "Service published" : "Could not publish service", Snackbar.LENGTH_LONG).show();
        } catch (Exception e) { binding.newServicePrice.setError("Enter a valid price"); }
    }

    private void captureSample(View v) {
        String title = binding.sampleTitleInput.getText().toString().trim();
        if (title.isEmpty()) { binding.sampleTitleInput.setError("Add a sample title"); return; }
        try {
            java.io.File dir = new java.io.File(getCacheDir(), "images");
            if (!dir.exists()) dir.mkdirs();
            java.io.File photo = new java.io.File(dir, "sample_" + System.currentTimeMillis() + ".jpg");
            pendingSampleUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photo);
            cameraLauncher.launch(pendingSampleUri);
        } catch (Exception e) { Snackbar.make(v, "Camera not available", Snackbar.LENGTH_LONG).show(); }
    }

    private void publishSample() {
        try {
            new SampleRepairDAO(helper).add(binding.sampleTitleInput.getText().toString().trim(),
                    "Staff pick", String.valueOf(pendingSampleUri));
            binding.sampleTitleInput.setText("");
            Snackbar.make(binding.getRoot(), "Sample published to Explore", Snackbar.LENGTH_LONG).show();
        } catch (Exception e) { Snackbar.make(binding.getRoot(), "Could not save sample", Snackbar.LENGTH_LONG).show(); }
    }

    private List<String> techLabels(List<Technician> list) {
        List<String> out = new java.util.ArrayList<>();
        for (Technician t : list) out.add(t.name + " · " + t.branch + " · " + t.skill + " · " + (t.available ? "available" : "unavailable"));
        return out;
    }

    private void goHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
