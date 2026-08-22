package com.techfix.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.techfix.app.R;
import com.techfix.app.adapters.StaffAppointmentAdapter;
import com.techfix.app.database.AppointmentDAO;
import com.techfix.app.database.DatabaseHelper;
import com.techfix.app.database.SampleRepairDAO;
import com.techfix.app.database.ServiceDAO;
import com.techfix.app.database.SparePartDAO;
import com.techfix.app.database.TechnicianDAO;
import com.techfix.app.databinding.ActivityStaffBinding;
import com.techfix.app.models.Appointment;
import com.techfix.app.models.AppointmentStatus;
import com.techfix.app.models.Technician;
import com.techfix.app.session.SessionManager;
import com.techfix.app.util.WindowInsetsHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * StaffActivity - Complete Administrator & Workshop Dashboard.
 * Features:
 * - Bottom Navigation: Queue, Services & Pricing, Technicians & Workshop, Profile
 * - Repair Queue: view all jobs, update workflow status with 1 tap, inspect dockets
 * - Services Management: adjust repair prices, publish new service offerings
 * - Workshop Management: toggle technician duty availability, restock spare parts
 * - Guaranteed one-tap logout with task stack reset
 */
public class StaffActivity extends AppCompatActivity {

    private ActivityStaffBinding binding;
    private SessionManager session;
    private DatabaseHelper dbHelper;

    // DAOs
    private AppointmentDAO appointmentDAO;
    private ServiceDAO serviceDAO;
    private TechnicianDAO technicianDAO;
    private SparePartDAO sparePartDAO;
    private SampleRepairDAO sampleRepairDAO;

    // Adapters
    private StaffAppointmentAdapter queueAdapter;
    private ArrayAdapter<String> serviceDropdownAdapter;
    private ArrayAdapter<String> techDropdownAdapter;

    private Uri pendingSampleUri;
    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (ok) publishSample();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Session Verification
        session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            goHome();
            return;
        }

        // 2. Inflate Layout
        binding = ActivityStaffBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.apply(binding.staffHeader, binding.staffContent);

        // 3. Initialize Database DAOs
        dbHelper = DatabaseHelper.getInstance(this);
        appointmentDAO = new AppointmentDAO(dbHelper);
        serviceDAO = new ServiceDAO(dbHelper);
        technicianDAO = new TechnicianDAO(dbHelper);
        sparePartDAO = new SparePartDAO(dbHelper);
        sampleRepairDAO = new SampleRepairDAO(dbHelper);

        // 4. Setup Components
        setupHeader();
        setupBottomNavigation();
        setupQueueTab();
        setupServicesTab();
        setupWorkshopTab();

        // 5. Initial Load
        refreshQueue();
        showPanel(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!session.isLoggedIn()) {
            goHome();
        }
    }

    private void setupHeader() {
        // Home / Store button
        binding.staffHomeStoreButton.setOnClickListener(v -> {
            startActivity(new Intent(StaffActivity.this, HomeActivity.class));
        });

        // Top bar Logout button
        binding.logoutButton.setOnClickListener(v -> performLogout());

        // Profile tab Logout button
        binding.staffProfileLogoutButton.setOnClickListener(v -> performLogout());
    }

    private void performLogout() {
        session.logout();
        Toast.makeText(this, "Logged out of staff workspace", Toast.LENGTH_SHORT).show();
        goHome();
    }

    private void goHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    /**
     * Setup 4-tab bottom navigation.
     */
    private void setupBottomNavigation() {
        binding.staffBottomNavigation.setSelectedItemId(R.id.nav_staff_queue);

        binding.staffBottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_staff_queue) {
                showPanel(0);
                return true;
            } else if (itemId == R.id.nav_staff_services) {
                showPanel(1);
                return true;
            } else if (itemId == R.id.nav_staff_technicians) {
                showPanel(2);
                return true;
            } else if (itemId == R.id.nav_staff_profile) {
                showPanel(3);
                return true;
            }

            return false;
        });
    }

    private void showPanel(int position) {
        binding.tabQueue.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        binding.tabServices.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
        binding.tabWorkshop.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
        binding.tabProfile.setVisibility(position == 3 ? View.VISIBLE : View.GONE);

        if (position == 0) {
            refreshQueue();
        } else if (position == 1) {
            refreshServicesDropdown();
        } else if (position == 2) {
            refreshTechniciansDropdown();
        }
    }

    /**
     * Tab 1: Queue setup and repair management.
     */
    private void setupQueueTab() {
        // Status Spinner for quick ID update
        binding.statusSpinner.setAdapter(new ArrayAdapter<>(this, R.layout.item_dropdown, AppointmentStatus.labels()));

        // Queue RecyclerView
        queueAdapter = new StaffAppointmentAdapter(this::showStatusDialog);
        binding.appointmentList.setLayoutManager(new LinearLayoutManager(this));
        binding.appointmentList.setAdapter(queueAdapter);

        // Update status by ID button
        binding.updateStatusButton.setOnClickListener(this::updateStatusById);
    }

    private void refreshQueue() {
        List<Appointment> allAppointments = appointmentDAO.all();
        queueAdapter.submit(allAppointments);

        boolean hasItems = !allAppointments.isEmpty();
        binding.appointmentList.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        binding.emptyQueueContainer.setVisibility(hasItems ? View.GONE : View.VISIBLE);

        int activeCount = 0;
        int completedCount = 0;
        for (Appointment a : allAppointments) {
            if (AppointmentStatus.COMPLETED.label.equalsIgnoreCase(a.status)) {
                completedCount++;
            } else {
                activeCount++;
            }
        }

        binding.queueStatsTitle.setText(allAppointments.size() + (allAppointments.size() == 1 ? " Repair Docket" : " Repair Dockets"));
        binding.queueStatsSubtitle.setText(activeCount + " Active in queue · " + completedCount + " Completed");
    }

    /**
     * Interactive Status Dialog: tap any repair to advance its workflow stage.
     */
    private void showStatusDialog(Appointment a) {
        String[] labels = AppointmentStatus.labels();
        new AlertDialog.Builder(this)
                .setTitle("Docket #" + a.id + " · " + a.device)
                .setItems(labels, (dialog, which) -> {
                    String newStatus = labels[which];
                    appointmentDAO.updateStatus(a.id, newStatus);
                    refreshQueue();
                    Snackbar.make(binding.getRoot(), "Docket #" + a.id + " updated to " + newStatus, Snackbar.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateStatusById(View v) {
        String idStr = binding.appointmentIdInput.getText().toString().trim();
        if (idStr.isEmpty()) {
            binding.appointmentIdInput.setError("Please enter a docket ID");
            return;
        }

        try {
            long id = Long.parseLong(idStr);
            String status = (String) binding.statusSpinner.getSelectedItem();
            appointmentDAO.updateStatus(id, status);
            binding.appointmentIdInput.setText("");
            refreshQueue();
            Snackbar.make(v, "Docket #" + id + " updated to " + status, Snackbar.LENGTH_LONG).show();
        } catch (Exception e) {
            binding.appointmentIdInput.setError("Invalid docket ID");
        }
    }

    /**
     * Tab 2: Services & Pricing Management.
     */
    private void setupServicesTab() {
        refreshServicesDropdown();

        // Update Price Button
        binding.updatePriceButton.setOnClickListener(v -> {
            String selectedService = (String) binding.serviceSpinner.getSelectedItem();
            String priceStr = binding.priceInput.getText().toString().trim();

            if (selectedService == null) return;
            if (priceStr.isEmpty()) {
                binding.priceInput.setError("Please enter a valid price");
                return;
            }

            try {
                double newPrice = Double.parseDouble(priceStr);
                String serviceName = serviceDAO.serviceName(selectedService);
                serviceDAO.updatePrice(serviceName, newPrice);

                binding.priceInput.setText("");
                refreshServicesDropdown();
                Snackbar.make(v, "Price updated for " + serviceName + " (Rs " + (long) newPrice + ")", Snackbar.LENGTH_LONG).show();
            } catch (Exception e) {
                binding.priceInput.setError("Invalid number");
            }
        });

        // Publish New Service Button
        binding.addServiceButton.setOnClickListener(v -> {
            String name = binding.newServiceName.getText().toString().trim();
            String category = binding.newServiceCategory.getText().toString().trim();
            String priceStr = binding.newServicePrice.getText().toString().trim();

            if (name.isEmpty()) {
                binding.newServiceName.setError("Enter service name");
                return;
            }
            if (priceStr.isEmpty()) {
                binding.newServicePrice.setError("Enter service price");
                return;
            }

            try {
                double price = Double.parseDouble(priceStr);
                boolean ok = serviceDAO.add(name, category.isEmpty() ? "General" : category, price);
                if (ok) {
                    binding.newServiceName.setText("");
                    binding.newServiceCategory.setText("");
                    binding.newServicePrice.setText("");
                    refreshServicesDropdown();
                    Snackbar.make(v, "Service \"" + name + "\" published to catalog", Snackbar.LENGTH_LONG).show();
                } else {
                    Snackbar.make(v, "Could not add service", Snackbar.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                binding.newServicePrice.setError("Invalid price");
            }
        });
    }

    private void refreshServicesDropdown() {
        List<String> services = serviceDAO.all();
        serviceDropdownAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, services);
        binding.serviceSpinner.setAdapter(serviceDropdownAdapter);
    }

    /**
     * Tab 3: Technicians & Workshop Management.
     */
    private void setupWorkshopTab() {
        refreshTechniciansDropdown();

        // Toggle Technician Duty Button
        binding.toggleTechButton.setOnClickListener(v -> {
            int pos = binding.techSpinner.getSelectedItemPosition();
            List<Technician> technicians = technicianDAO.all();
            if (pos >= 0 && pos < technicians.size()) {
                Technician t = technicians.get(pos);
                technicianDAO.setAvailability(t.name, !t.available);
                refreshTechniciansDropdown();
                Snackbar.make(v, t.name + " marked as " + (!t.available ? "AVAILABLE 🟢" : "BUSY / UNAVAILABLE 🔴"), Snackbar.LENGTH_LONG).show();
            }
        });

        // Restock Parts Button
        binding.restockButton.setOnClickListener(v -> {
            String part = binding.partInput.getText().toString().trim();
            String branch = binding.branchInput.getText().toString().trim();

            if (part.isEmpty()) {
                binding.partInput.setError("Enter part name");
                return;
            }
            if (branch.isEmpty()) {
                binding.branchInput.setError("Enter branch name");
                return;
            }

            sparePartDAO.restock(part, branch, 2);
            binding.partInput.setText("");
            binding.branchInput.setText("");
            Snackbar.make(v, "Restocked " + part + " at " + branch, Snackbar.LENGTH_LONG).show();
        });

        // Publish Sample Showcase Button
        binding.addSampleButton.setOnClickListener(v -> {
            String title = binding.sampleTitleInput.getText().toString().trim();
            if (title.isEmpty()) {
                binding.sampleTitleInput.setError("Add a sample title");
                return;
            }

            try {
                java.io.File dir = new java.io.File(getCacheDir(), "images");
                if (!dir.exists()) dir.mkdirs();
                java.io.File photo = new java.io.File(dir, "sample_" + System.currentTimeMillis() + ".jpg");
                pendingSampleUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photo);
                cameraLauncher.launch(pendingSampleUri);
            } catch (Exception e) {
                // If camera is not available, publish sample title directly
                sampleRepairDAO.add(title, "Staff pick", "");
                binding.sampleTitleInput.setText("");
                Snackbar.make(v, "Showcase published to Explore", Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void refreshTechniciansDropdown() {
        List<Technician> list = technicianDAO.all();
        List<String> labels = new ArrayList<>();
        for (Technician t : list) {
            String statusIndicator = t.available ? "🟢 Available" : "🔴 Busy";
            labels.add(t.name + " (" + t.branch + ") · " + statusIndicator);
        }
        techDropdownAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, labels);
        binding.techSpinner.setAdapter(techDropdownAdapter);
    }

    private void publishSample() {
        try {
            String title = binding.sampleTitleInput.getText().toString().trim();
            if (title.isEmpty()) title = "Repair Showcase";
            sampleRepairDAO.add(title, "Staff pick", String.valueOf(pendingSampleUri));
            binding.sampleTitleInput.setText("");
            Snackbar.make(binding.getRoot(), "Sample photo published to Explore", Snackbar.LENGTH_LONG).show();
        } catch (Exception e) {
            Snackbar.make(binding.getRoot(), "Could not save sample", Snackbar.LENGTH_LONG).show();
        }
    }
}
