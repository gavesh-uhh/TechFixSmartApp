package com.techfix.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.techfix.app.R;
import com.techfix.app.adapters.AppointmentAdapter;
import com.techfix.app.adapters.BranchAdapter;


import com.techfix.app.adapters.SampleImageAdapter;
import com.techfix.app.database.AppointmentDAO;
import com.techfix.app.database.BranchDAO;
import com.techfix.app.database.DatabaseHelper;
import com.techfix.app.database.SampleRepairDAO;
import com.techfix.app.database.ServiceDAO;
import com.techfix.app.database.SparePartDAO;
import com.techfix.app.database.TechnicianDAO;
import com.techfix.app.databinding.ActivityCustomerBinding;
import com.techfix.app.models.Appointment;
import com.techfix.app.database.SampleRepairDAO;
import com.techfix.app.session.SessionManager;
import com.techfix.app.util.Feedback;
import com.techfix.app.util.WindowInsetsHelper;
import java.util.List;

public class CustomerActivity extends AppCompatActivity {
    private ActivityCustomerBinding binding;
    private SessionManager session;
    private AppointmentAdapter adapter;
    private SampleImageAdapter sampleAdapter;
    private BranchAdapter branchAdapter;

    private String pendingDevice, pendingService;
    private Uri capturedPhotoUri;

    /** Camera capture with a FileProvider-backed URI so the photo is saved (Camera & Image Integrations deliverable). */
    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.TakePicture(), ok -> {
                if (ok) Feedback.success(binding.getRoot(), "Photo attached — it will be saved with your booking");
            });

    /** Runtime GPS permission (Locations / Map GPS deliverable). */
    private final ActivityResultLauncher<String> locationPermission =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) fetchLocationAndBook(); else bookAt(6.9271, 79.8612, "Location denied — using Colombo as reference");
            });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
        if (!session.isLoggedIn()) { goHome(); return; }
        binding = ActivityCustomerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.apply(binding.customerHeader, binding.dashboardContent);

        binding.deviceSpinner.setAdapter(new ArrayAdapter<>(this, R.layout.item_dropdown,
                new String[]{"Mobile phone", "Laptop / computer", "Tablet"}));
        ServiceDAO services = new ServiceDAO(DatabaseHelper.getInstance(this));
        binding.catalogText.setText(services.catalog());

        ArrayAdapter<String> serviceAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, services.all());
        binding.serviceSpinner.setAdapter(serviceAdapter);

        AppointmentDAO appointments = new AppointmentDAO(DatabaseHelper.getInstance(this));
        List<Appointment> initial = appointments.forUser(session.getUserId());
        render(initial);
        binding.activeCount.setText(initial.size() + " active");
        binding.lastUpdated.setText("Updated now");
        showPanel(0);

        binding.dashboardTabs.addTab(binding.dashboardTabs.newTab().setText("Book"));
        binding.dashboardTabs.addTab(binding.dashboardTabs.newTab().setText("Repairs"));
        binding.dashboardTabs.addTab(binding.dashboardTabs.newTab().setText("Explore"));
        binding.dashboardTabs.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) { showPanel(tab.getPosition()); }
            @Override public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) { }
            @Override public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) { }
        });

        binding.bookButton.setOnClickListener(v -> {
            String problem = binding.problemInput.getText().toString().trim();
            if (problem.isEmpty()) { binding.problemInput.setError("Add a short description"); return; }
            pendingDevice = (String) binding.deviceSpinner.getSelectedItem();
            pendingService = (String) binding.serviceSpinner.getSelectedItem();
            if (pendingService == null) { Feedback.error(v, "No matching service available"); return; }
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                fetchLocationAndBook();
            } else {
                locationPermission.launch(android.Manifest.permission.ACCESS_FINE_LOCATION);
            }
        });

        binding.cameraButton.setOnClickListener(v -> {
            try {
                java.io.File dir = new java.io.File(getCacheDir(), "images");
                if (!dir.exists()) dir.mkdirs();
                java.io.File photo = new java.io.File(dir, "damage_" + System.currentTimeMillis() + ".jpg");
                capturedPhotoUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photo);
                cameraLauncher.launch(capturedPhotoUri);
            } catch (Exception e) { Feedback.error(v, "Camera not available"); }
        });

        binding.payButton.setOnClickListener(this::payFirstPending);

        branchAdapter = new BranchAdapter();
        binding.branchList.setLayoutManager(new LinearLayoutManager(this));
        binding.branchList.setAdapter(branchAdapter);
        branchAdapter.submit(new BranchDAO(DatabaseHelper.getInstance(this)).branches());


        sampleAdapter = new SampleImageAdapter();
        binding.sampleList.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 2));
        binding.sampleList.setAdapter(sampleAdapter);
        sampleAdapter.submit(new SampleRepairDAO(DatabaseHelper.getInstance(this)).all());

        binding.logoutButton.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Log out").setMessage("End this session?")
                .setPositiveButton("Log out", (d, w) -> { session.logout(); goHome(); })
                .setNegativeButton("Cancel", null).show());

        binding.connectionStatus.setText("Offline · all data stored on this device");
    }

    private void showPanel(int position) {
        binding.bookPanel.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        binding.repairsPanel.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
        binding.explorePanel.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
    }

    private void render(List<Appointment> repairs) {
        binding.emptyText.setVisibility(repairs.isEmpty() ? View.VISIBLE : View.GONE);
        if (adapter == null) {
            adapter = new AppointmentAdapter(a -> startActivity(
                    new Intent(this, AppointmentDetailActivity.class).putExtra("appointmentId", a.id)));
            binding.repairList.setLayoutManager(new LinearLayoutManager(this));
            binding.repairList.setAdapter(adapter);
        }
        adapter.submitList(repairs);
    }

    private void fetchLocationAndBook() {
        com.google.android.gms.location.FusedLocationProviderClient client =
                com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this);
        client.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) bookAt(location.getLatitude(), location.getLongitude(), null);
            else bookAt(6.9271, 79.8612, "Could not read GPS — using Colombo as reference");
        }).addOnFailureListener(e -> bookAt(6.9271, 79.8612, "GPS unavailable — using Colombo as reference"));
    }

    private void bookAt(double latitude, double longitude, String notice) {
        String problem = binding.problemInput.getText().toString().trim();
        if (problem.isEmpty() || pendingDevice == null || pendingService == null) {
            Feedback.error(binding.getRoot(), "Fill the booking form first");
            return;
        }
        DatabaseHelper helper = DatabaseHelper.getInstance(this);
        ServiceDAO services = new ServiceDAO(helper);
        AppointmentDAO appointments = new AppointmentDAO(helper);

        String service = services.serviceName(pendingService);
        String branch = new BranchDAO(helper).nearestFor(pendingDevice, service, latitude, longitude);
        long id = appointments.add(session.getUserId(), pendingDevice, problem, branch, service,
                services.price(pendingService), new TechnicianDAO(helper).availableFor(branch, pendingDevice), "");
        if (capturedPhotoUri != null) appointments.setPhoto(id, capturedPhotoUri.toString());

        final View root = binding.getRoot();
        final String part = services.requiredPart(service);
        if (!part.isEmpty()) {
            SparePartDAO parts = new SparePartDAO(helper);
            new Thread(() -> {
                boolean consumed = parts.consume(part, branch);
                runOnUiThread(() -> { if (!consumed && !isFinishing()) Feedback.error(root, "Note: required part out of stock at " + branch); });
            }).start();
        }

        binding.problemInput.setText("");
        List<Appointment> updated = appointments.forUser(session.getUserId());
        render(updated);
        binding.activeCount.setText(updated.size() + " active");
        Feedback.success(root, (notice == null ? "" : notice + " · ") + "Booked at " + branch + " · technician assigned");
    }

    /** Pays the oldest unpaid repair; asks for a payment method first. */
    private void payFirstPending(View v) {
        AppointmentDAO appointments = new AppointmentDAO(DatabaseHelper.getInstance(this));
        Appointment target = null;
        for (Appointment a : appointments.forUser(session.getUserId())) {
            if ("Pending".equals(a.payment)) { target = a; break; }
        }
        final Appointment appointment = target;
        if (appointment == null) { Feedback.error(v, "No unpaid repairs"); return; }
        String[] methods = {"Cash at counter", "Card", "Bank transfer"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Pay Rs " + (long) appointment.price + " · " + appointment.service)
                .setItems(methods, (d, w) -> {
                    boolean ok = appointments.pay(appointment.id, appointment.price, methods[w]);
                    render(appointments.forUser(session.getUserId()));
                    if (ok) Feedback.success(v, "Payment recorded · " + methods[w]); else Feedback.error(v, "Payment failed");
                }).setNegativeButton("Cancel", null).show();
    }

    private void goHome() {
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }
}
