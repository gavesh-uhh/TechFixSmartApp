package com.techfix.app.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

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
import com.techfix.app.sync.FirebaseSyncManager;
import com.techfix.app.util.Feedback;
import com.techfix.app.util.WindowInsetsHelper;

import java.io.File;
import java.util.List;

/**
 * CustomerActivity - Complete Customer Workspace Dashboard with Bottom Navigation.
 * Features:
 * - Customer greeting & profile info
 * - Bottom Navigation: My Repairs, Book Repair, Profile & Branches
 * - My Repairs tab: view all repairs, status badges, and open live timeline
 * - Book Repair tab: service picker, device model, photo attach, branch selector
 * - Profile & Branches tab: user account details, store branch locations & helpline
 * - Guaranteed secure logout with activity stack reset
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
    private Uri tempCameraUri = null;

    // 1. Photo picker launcher (Gallery)
    private final ActivityResultLauncher<String> photoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), (Uri uri) -> {
                if (uri != null) {
                    onPhotoReady(uri);
                }
            });

    // 2. Camera Take Picture Launcher
    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), (Boolean success) -> {
                if (Boolean.TRUE.equals(success) && tempCameraUri != null) {
                    onPhotoReady(tempCameraUri);
                }
            });

    // 3. Camera Permission Launcher
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), (Boolean isGranted) -> {
                if (Boolean.TRUE.equals(isGranted)) {
                    launchCamera();
                } else {
                    Toast.makeText(this, "Camera permission is required to take a photo", Toast.LENGTH_SHORT).show();
                }
            });

    private void onPhotoReady(Uri uri) {
        selectedPhotoUri = uri;
        binding.customerPhotoPreview.setImageURI(uri);
        binding.customerPhotoPreviewContainer.setVisibility(View.VISIBLE);
        binding.customerPhotoStatus.setText("Photo attached");
        binding.customerPhotoStatus.setTextColor(ContextCompat.getColor(this, R.color.success));
    }

    private void showPhotoOptionsDialog() {
        CharSequence[] options = {"Take Photo with Camera", "Choose from Gallery"};
        new AlertDialog.Builder(this)
                .setTitle("Attach Device Photo")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        checkCameraPermissionAndLaunch();
                    } else {
                        photoPickerLauncher.launch("image/*");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        try {
            File cacheDir = new File(getCacheDir(), "images");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            File photoFile = new File(cacheDir, "device_photo_" + System.currentTimeMillis() + ".jpg");
            tempCameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
            takePictureLauncher.launch(tempCameraUri);
        } catch (Exception e) {
            Toast.makeText(this, "Failed to open camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

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
        // Top insets handled here; the BottomNavigationView (customerBottomNavigation) applies its
        // own navigationBars inset via Material, so we don't pad it again (avoids double inset).
        WindowInsetsHelper.apply(binding.customerHeader, binding.dashboardContent);

        // 3. Initialize Database DAOs
        dbHelper = DatabaseHelper.getInstance(this);
        appointmentDAO = new AppointmentDAO(dbHelper);
        serviceDAO = new ServiceDAO(dbHelper);
        branchDAO = new BranchDAO(dbHelper);
        userDAO = new UserDAO(dbHelper);

        // 4. Initialize Firebase Cloud Sync
        FirebaseSyncManager.getInstance().init(this);
        FirebaseSyncManager.getInstance().addListener((isSyncing, success) -> {
            if (!isSyncing && success) {
                runOnUiThread(this::refreshRepairs);
            }
        });

        // 5. Setup User Header & Profile
        setupUserProfile();

        // 6. Setup Bottom Navigation
        setupBottomNavigation();

        // 7. Setup Form & Repair List
        setupBookingForm();
        setupRepairsList();
        setupBranchesList();

        // 8. Initial load of repairs
        refreshRepairs();
        showPanel(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!session.isLoggedIn()) {
            goHome();
        }
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

        // Top bar Log Out button
        // Log out lives only in the Profile panel card ("Log Out of Account").

        // Profile tab Log Out button
        binding.profileLogoutButton.setOnClickListener(v -> performLogout());
    }

    private void performLogout() {
        session.logout();
        android.widget.Toast.makeText(this, "Logged out successfully", android.widget.Toast.LENGTH_SHORT).show();
        goHome();
    }

    private void goHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    /**
     * Setup bottom navigation bar (My Repairs, Book Repair, Profile).
     */
    private void setupBottomNavigation() {
        binding.customerBottomNavigation.setSelectedItemId(R.id.nav_customer_repairs);

        binding.customerBottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_customer_repairs) {
                showPanel(0);
                return true;
            } else if (itemId == R.id.nav_customer_book) {
                showPanel(1);
                return true;
            } else if (itemId == R.id.nav_customer_profile) {
                showPanel(2);
                return true;
            }

            return false;
        });

        binding.emptyBookButton.setOnClickListener(v -> {
            binding.customerBottomNavigation.setSelectedItemId(R.id.nav_customer_book);
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
        binding.cameraButton.setOnClickListener(v -> showPhotoOptionsDialog());

        // 5. Remove Photo Button
        binding.customerRemovePhotoButton.setOnClickListener(v -> {
            selectedPhotoUri = null;
            tempCameraUri = null;
            binding.customerPhotoPreview.setImageURI(null);
            binding.customerPhotoPreviewContainer.setVisibility(View.GONE);
            binding.customerPhotoStatus.setText("No photo attached");
            binding.customerPhotoStatus.setTextColor(ContextCompat.getColor(this, R.color.muted_text));
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

        // Trigger instant cloud sync to Firebase if online
        FirebaseSyncManager.getInstance().sync(this, null);

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

                    // Switch to My Repairs bottom nav item
                    binding.customerBottomNavigation.setSelectedItemId(R.id.nav_customer_repairs);
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
}
