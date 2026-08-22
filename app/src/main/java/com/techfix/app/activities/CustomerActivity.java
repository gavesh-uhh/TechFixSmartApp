package com.techfix.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.tabs.TabLayout;
import com.techfix.app.R;
import com.techfix.app.adapters.AppointmentAdapter;
import com.techfix.app.adapters.BranchAdapter;
import com.techfix.app.database.AppointmentDAO;
import com.techfix.app.database.BranchDAO;
import com.techfix.app.database.DatabaseHelper;
import com.techfix.app.database.ServiceDAO;
import com.techfix.app.database.TechnicianDAO;
import com.techfix.app.database.UserDAO;
import com.techfix.app.databinding.ActivityCustomerBinding;
import com.techfix.app.models.Appointment;
import com.techfix.app.models.PaymentStatus;
import com.techfix.app.models.User;
import com.techfix.app.session.SessionManager;
import com.techfix.app.util.Feedback;
import com.techfix.app.util.WindowInsetsHelper;

import java.util.List;

/**
 * CustomerActivity - Complete Customer Workspace Dashboard.
 * Features:
 * - Customer greeting & profile info
 * - My Repairs tab: view all repairs, status badges, and open live timeline
 * - Book Repair tab: service picker, device model, photo attach, branch selector
 * - Profile & Branches tab: user account details, store branch locations & helpline
 * - Secure logout with confirmation
 */
public class CustomerActivity extends AppCompatActivity {

    private ActivityCustomerBinding binding;
    private SessionManager session;
    private DatabaseHelper dbHelper;

    // DAOs
    private AppointmentDAO appointmentDAO;
    private ServiceDAO serviceDAO;
    private BranchDAO branchDAO;
    private UserDAO userDAO;

    // Adapters
    private AppointmentAdapter appointmentAdapter;
    private BranchAdapter branchAdapter;

    // Photo capture / selection
    private Uri selectedPhotoUri = null;

    // Photo picker launcher
    private final ActivityResultLauncher<String> photoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), (Uri uri) -> {
                if (uri != null) {
                    selectedPhotoUri = uri;
                    binding.customerPhotoPreview.setImageURI(uri);
                    binding.customerPhotoPreviewContainer.setVisibility(View.VISIBLE);
                    binding.customerPhotoStatus.setText("Photo attached");
                    binding.customerPhotoStatus.setTextColor(getResources().getColor(R.color.success, null));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Verify user session
        session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            goHome();
            return;
        }

        // 2. Inflate layout
        binding = ActivityCustomerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.apply(binding.customerHeader, binding.dashboardContent);

        // 3. Initialize Database DAOs
        dbHelper = DatabaseHelper.getInstance(this);
        appointmentDAO = new AppointmentDAO(dbHelper);
        serviceDAO = new ServiceDAO(dbHelper);
        branchDAO = new BranchDAO(dbHelper);
        userDAO = new UserDAO(dbHelper);

        // 4. Setup User Header & Profile
        setupUserProfile();

        // 5. Setup Navigation Tabs
        setupDashboardTabs();

        // 6. Setup Form & Repair List
        setupBookingForm();
        setupRepairsList();
        setupBranchesList();

        // 7. Initial load of repairs
        refreshRepairs();
        showPanel(0);
    }

    /**
     * Loads logged-in user profile details into the header and profile card.
     */
    private void setupUserProfile() {
        User user = userDAO.get(session.getUserId());
        if (user != null) {
            binding.welcomeUserText.setText("Hello, " + user.name + " 👋");
            binding.userEmailText.setText(user.email);

            binding.profileNameText.setText(user.name);
            binding.profileEmailText.setText(user.email);
            binding.profilePhoneText.setText(user.phone.isEmpty() ? "Phone: Not provided" : "Phone: " + user.phone);
        }

        // Home / Store button
        binding.homeStoreButton.setOnClickListener(v -> {
            startActivity(new Intent(CustomerActivity.this, HomeActivity.class));
        });

        // Log out button
        binding.logoutButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Log Out")
                    .setMessage("Are you sure you want to end this session?")
                    .setPositiveButton("Log Out", (dialog, which) -> {
                        session.logout();
                        goHome();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    /**
     * Setup dashboard top tabs (My Repairs, Book Repair, Profile & Branches).
     */
    private void setupDashboardTabs() {
        binding.dashboardTabs.addTab(binding.dashboardTabs.newTab().setText("My Repairs"));
        binding.dashboardTabs.addTab(binding.dashboardTabs.newTab().setText("Book Repair"));
        binding.dashboardTabs.addTab(binding.dashboardTabs.newTab().setText("Profile & Branches"));

        binding.dashboardTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showPanel(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        // Quick book buttons
        binding.quickBookButton.setOnClickListener(v -> {
            TabLayout.Tab bookTab = binding.dashboardTabs.getTabAt(1);
            if (bookTab != null) bookTab.select();
        });

        binding.emptyBookButton.setOnClickListener(v -> {
            TabLayout.Tab bookTab = binding.dashboardTabs.getTabAt(1);
            if (bookTab != null) bookTab.select();
        });

        // Pay pending repairs button
        binding.payButton.setOnClickListener(this::payFirstPending);
    }

    /**
     * Shows the active tab panel.
     */
    private void showPanel(int position) {
        binding.repairsPanel.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        binding.bookPanel.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
        binding.explorePanel.setVisibility(position == 2 ? View.VISIBLE : View.GONE);

        if (position == 0) {
            refreshRepairs();
        }
    }

    /**
     * Setup RecyclerView for user repairs.
     */
    private void setupRepairsList() {
        appointmentAdapter = new AppointmentAdapter(appointment -> {
            Intent intent = new Intent(CustomerActivity.this, AppointmentDetailActivity.class);
            intent.putExtra("appointmentId", appointment.id);
            startActivity(intent);
        });

        binding.repairList.setLayoutManager(new LinearLayoutManager(this));
        binding.repairList.setAdapter(appointmentAdapter);
    }

    /**
     * Refreshes the list of repairs for the logged-in customer.
     */
    private void refreshRepairs() {
        List<Appointment> repairs = appointmentDAO.forUser(session.getUserId());
        appointmentAdapter.submitList(repairs);

        boolean hasRepairs = !repairs.isEmpty();
        binding.repairList.setVisibility(hasRepairs ? View.VISIBLE : View.GONE);
        binding.emptyStateContainer.setVisibility(hasRepairs ? View.GONE : View.VISIBLE);

        binding.activeCount.setText(repairs.size() + (repairs.size() == 1 ? " Total Repair" : " Total Repairs"));
        binding.lastUpdated.setText("Updated just now");

        // Check if there are any pending payments
        boolean hasPendingPayment = false;
        for (Appointment a : repairs) {
            if (PaymentStatus.PENDING.label.equalsIgnoreCase(a.payment)) {
                hasPendingPayment = true;
                break;
            }
        }
        binding.paymentCard.setVisibility(hasPendingPayment ? View.VISIBLE : View.GONE);
    }

    /**
     * Setup Book Repair Form with service dropdown, device info, and photo attachment.
     */
    private void setupBookingForm() {
        // 1. Service Type Dropdown
        List<String> serviceOptions = serviceDAO.all();
        if (serviceOptions.isEmpty()) {
            serviceOptions.add("Screen replacement · Rs 8500");
            serviceOptions.add("Battery replacement · Rs 4500");
            serviceOptions.add("Laptop diagnostics · Rs 3000");
            serviceOptions.add("Operating system repair · Rs 6500");
        }
        ArrayAdapter<String> serviceAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, serviceOptions);
        binding.serviceSpinner.setAdapter(serviceAdapter);

        // 2. Device Category Dropdown
        String[] deviceCategories = {"Mobile phone", "Laptop / computer", "Tablet", "Other smart device"};
        ArrayAdapter<String> deviceAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, deviceCategories);
        binding.deviceSpinner.setAdapter(deviceAdapter);

        // 3. Branch Dropdown
        String[] branches = {"Colombo branch", "Galle branch"};
        ArrayAdapter<String> branchAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, branches);
        binding.branchSpinner.setAdapter(branchAdapter);

        // 4. Attach Photo Button
        binding.cameraButton.setOnClickListener(v -> photoPickerLauncher.launch("image/*"));

        // 5. Remove Photo Button
        binding.customerRemovePhotoButton.setOnClickListener(v -> {
            selectedPhotoUri = null;
            binding.customerPhotoPreview.setImageURI(null);
            binding.customerPhotoPreviewContainer.setVisibility(View.GONE);
            binding.customerPhotoStatus.setText("No photo attached");
            binding.customerPhotoStatus.setTextColor(getResources().getColor(R.color.muted_text, null));
        });

        // 6. Submit Booking Button
        binding.bookButton.setOnClickListener(v -> submitBooking());
    }

    /**
     * Validates and submits a new repair booking for the customer.
     */
    private void submitBooking() {
        String serviceSelection = (String) binding.serviceSpinner.getSelectedItem();
        String deviceCategory = (String) binding.deviceSpinner.getSelectedItem();
        String deviceModel = binding.deviceModelInput.getText().toString().trim();
        String problem = binding.problemInput.getText().toString().trim();
        String branch = (String) binding.branchSpinner.getSelectedItem();

        if (deviceModel.isEmpty()) {
            binding.deviceModelInput.setError("Please enter your device model / brand");
            binding.deviceModelInput.requestFocus();
            return;
        }

        if (problem.isEmpty()) {
            binding.problemInput.setError("Please describe the problem or damage");
            binding.problemInput.requestFocus();
            return;
        }

        String serviceName = serviceDAO.serviceName(serviceSelection != null ? serviceSelection : "Repair Service");
        double price = serviceDAO.price(serviceSelection != null ? serviceSelection : "0");
        String fullDeviceInfo = deviceCategory + " (" + deviceModel + ")";

        // Auto-assign available technician for branch
        String technician = new TechnicianDAO(dbHelper).availableFor(branch, deviceCategory);

        // Save appointment to SQLite database
        long appointmentId = appointmentDAO.add(session.getUserId(), fullDeviceInfo, problem, branch, serviceName, price, technician, "");

        if (selectedPhotoUri != null && appointmentId > 0) {
            appointmentDAO.setPhoto(appointmentId, selectedPhotoUri.toString());
        }

        // Show confirmation dialog
        new AlertDialog.Builder(this)
                .setTitle("Repair Booked!")
                .setMessage("Appointment #" + appointmentId + " booked at " + branch + ".\nTechnician: " + technician + "\nEstimated Price: Rs " + (long) price)
                .setPositiveButton("View Repairs", (dialog, which) -> {
                    // Reset form
                    binding.deviceModelInput.setText("");
                    binding.problemInput.setText("");
                    selectedPhotoUri = null;
                    binding.customerPhotoPreviewContainer.setVisibility(View.GONE);
                    binding.customerPhotoStatus.setText("No photo attached");

                    // Switch to My Repairs tab
                    TabLayout.Tab repairsTab = binding.dashboardTabs.getTabAt(0);
                    if (repairsTab != null) repairsTab.select();
                })
                .show();
    }

    /**
     * Setup Branch list in the Profile & Branches tab.
     */
    private void setupBranchesList() {
        branchAdapter = new BranchAdapter();
        binding.branchList.setLayoutManager(new LinearLayoutManager(this));
        binding.branchList.setAdapter(branchAdapter);
        branchAdapter.submit(branchDAO.branches());
    }

    /**
     * Pays the oldest pending unpaid repair.
     */
    private void payFirstPending(View v) {
        List<Appointment> userRepairs = appointmentDAO.forUser(session.getUserId());
        Appointment target = null;
        for (Appointment a : userRepairs) {
            if (PaymentStatus.PENDING.label.equalsIgnoreCase(a.payment)) {
                target = a;
                break;
            }
        }

        final Appointment appointment = target;
        if (appointment == null) {
            Feedback.error(v, "No unpaid repairs pending");
            return;
        }

        String[] methods = {"Cash at counter", "Card", "Bank transfer"};
        new AlertDialog.Builder(this)
                .setTitle("Pay Rs " + (long) appointment.price + " · " + appointment.service)
                .setItems(methods, (d, which) -> {
                    boolean ok = appointmentDAO.pay(appointment.id, appointment.price, methods[which]);
                    refreshRepairs();
                    if (ok) {
                        Feedback.success(v, "Payment recorded (" + methods[which] + ")");
                    } else {
                        Feedback.error(v, "Payment failed");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void goHome() {
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }
}
