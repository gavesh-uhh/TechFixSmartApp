package com.techfix.app.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.techfix.app.R;
import com.techfix.app.adapters.SampleImageAdapter;
import com.techfix.app.database.AppointmentDAO;
import com.techfix.app.database.DatabaseHelper;
import com.techfix.app.database.SampleRepairDAO;
import com.techfix.app.database.ServiceDAO;
import com.techfix.app.database.SparePartDAO;
import com.techfix.app.database.TechnicianDAO;
import com.techfix.app.databinding.ActivityHomeBinding;
import com.techfix.app.models.Service;
import com.techfix.app.models.SparePart;
import com.techfix.app.models.UserRole;
import com.techfix.app.session.SessionManager;
import com.techfix.app.sync.FirebaseSyncManager;
import com.techfix.app.util.WindowInsetsHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * HomeActivity - Landing Page & Booking for TechFix Repair Shop.
 * TechFix is a dedicated computer and mobile phone repair shop.
 * Features:
 * - Round category quick-filters (All, Phones, Computers, Screens, Batteries)
 * - Available repair services catalog with pricing in LKR
 * - Dedicated Spare Parts Inventory navigation
 * - Bottom navigation: Store, Parts, Book Appointment, Branches, Account
 * - Mandatory login/account check before placing repair appointments
 */
public class HomeActivity extends AppCompatActivity {

    // View binding instance for activity_home.xml
    private ActivityHomeBinding binding;

    // Database access objects
    private DatabaseHelper dbHelper;
    private ServiceDAO serviceDAO;
    private SparePartDAO sparePartDAO;
    private AppointmentDAO appointmentDAO;
    private SampleRepairDAO sampleRepairDAO;
    private SessionManager session;

    // Showcase gallery adapter
    private SampleImageAdapter sampleImageAdapter;

    // Currently selected category filter: "ALL", "PHONES", "COMPUTERS", "SCREENS", "BATTERIES"
    private String currentCategory = "ALL";
    private String currentPartsBranch = "All Branches";
    private boolean resumedOnce = false;

    private final FirebaseSyncManager.SyncListener syncListener = (isSyncing, success) -> {
        if (!isSyncing && success) {
            runOnUiThread(() -> {
                loadAvailableServices(currentCategory);
                loadAvailableParts();
                loadSampleShowcase();
            });
        }
    };

    // Attached device photo URI & temp camera URI
    private Uri selectedPhotoUri = null;
    private Uri tempCameraUri = null;
    private boolean locationPermissionRequested = false;

    // Location permission launcher for nearest-branch detection
    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), (Boolean isGranted) -> {
                if (Boolean.TRUE.equals(isGranted)) {
                    suggestNearestBranch();
                }
            });

    // 1. Photo Picker Launcher (Gallery)
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
        try {
            binding.bookingPhotoPreview.setImageURI(null);
            binding.bookingPhotoPreview.setImageURI(uri);
            binding.photoPreviewContainer.setVisibility(View.VISIBLE);
            binding.photoStatusText.setText("Photo attached");
            binding.photoStatusText.setTextColor(ContextCompat.getColor(this, R.color.success));
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load image preview: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showPhotoOptionsDialog() {
        CharSequence[] options = {"📷 Take Photo with Camera", "🖼️ Choose from Gallery / Files"};
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
            File storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
            if (storageDir == null) {
                storageDir = new File(getCacheDir(), "images");
            }
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }
            File photoFile = File.createTempFile("repair_" + System.currentTimeMillis(), ".jpg", storageDir);
            tempCameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);

            Intent takePictureIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            takePictureIntent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, tempCameraUri);
            takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            List<android.content.pm.ResolveInfo> resInfoList = getPackageManager().queryIntentActivities(takePictureIntent, PackageManager.MATCH_DEFAULT_ONLY);
            for (android.content.pm.ResolveInfo resolveInfo : resInfoList) {
                String packageName = resolveInfo.activityInfo.packageName;
                grantUriPermission(packageName, tempCameraUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }

            takePictureLauncher.launch(tempCameraUri);
        } catch (Exception e) {
            Toast.makeText(this, "Camera unavailable (" + e.getMessage() + "). Opening gallery instead...", Toast.LENGTH_SHORT).show();
            photoPickerLauncher.launch("image/*");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Inflate layout
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // Edge-to-edge: pad the header below status bar/cutout. The BottomNavigationView is left
        // alone because Material's BottomNavigationView applies its own navigationBars inset,
        // so padding it here too would double the bottom inset — apply only the header top inset.
        WindowInsetsHelper.applyHeader(binding.homeHeader);

        // 2. Initialize Database DAOs and Session
        dbHelper = DatabaseHelper.getInstance(this);
        serviceDAO = new ServiceDAO(dbHelper);
        sparePartDAO = new SparePartDAO(dbHelper);
        appointmentDAO = new AppointmentDAO(dbHelper);
        sampleRepairDAO = new SampleRepairDAO(dbHelper);
        session = new SessionManager(this);

        // 3. Initialize Firebase Cloud Sync (Offline-First auto-sync when online)
        FirebaseSyncManager.getInstance().init(this);
        FirebaseSyncManager.getInstance().addListener(syncListener);

        // 4. Setup UI components
        setupBranchButtons();
        setupBottomNavigation();
        setupRoundCategories();
        setupPartsPanel();
        setupBookingForm();

        // 5. Initial load of all available repair services
        selectCategory("ALL");
    }

    @Override
    protected void onResume() {
        super.onResume();
        session = new SessionManager(this);
        loadSampleShowcase();
        showStorePanel();
        binding.bottomNavigation.setSelectedItemId(R.id.nav_home_store);
        selectCategory(currentCategory != null ? currentCategory : "ALL");
        updateNavAccountItem();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        session = new SessionManager(this);
        showStorePanel();
        binding.bottomNavigation.setSelectedItemId(R.id.nav_home_store);
        selectCategory("ALL");
        updateNavAccountItem();
    }

    /**
     * Setup bottom navigation bar (Store, Parts, Book, Branches, Account).
     */
    private void setupBottomNavigation() {
        binding.bottomNavigation.setSelectedItemId(R.id.nav_home_store);

        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home_store) {
                // Show Store Catalog Panel
                showStorePanel();
                binding.storePanel.smoothScrollTo(0, 0);
                return true;

            } else if (itemId == R.id.nav_home_parts) {
                // Show Dedicated Spare Parts Panel
                showPartsPanel();
                return true;

            } else if (itemId == R.id.nav_home_book) {
                // Show Book Appointment Form Panel
                showBookAppointmentPanel();
                return true;

            } else if (itemId == R.id.nav_home_branches) {
                // Show Dedicated Store Branches Page
                showBranchesPanel();
                binding.branchesPanel.smoothScrollTo(0, 0);
                return true;

            } else if (itemId == R.id.nav_home_account) {
                // Open Customer / Staff dashboard if logged in, or Login screen if not
                navigateToDashboard();
                return false;
            }

            return false;
        });

        updateNavAccountItem();
        setupBranchesPanel();
    }

    private void navigateToDashboard() {
        SessionManager currentSession = new SessionManager(HomeActivity.this);
        if (currentSession.isLoggedIn()) {
            Class<?> target = (currentSession.getRole() == UserRole.STAFF) ? StaffActivity.class : CustomerActivity.class;
            Intent intent = new Intent(HomeActivity.this, target);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } else {
            Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
            startActivity(intent);
        }
    }

    private void updateNavAccountItem() {
        if (binding == null || binding.bottomNavigation == null) return;
        SessionManager currentSession = new SessionManager(this);
        boolean loggedIn = currentSession.isLoggedIn();
        boolean isStaff = loggedIn && currentSession.getRole() == UserRole.STAFF;

        MenuItem accountItem = binding.bottomNavigation.getMenu().findItem(R.id.nav_home_account);
        if (accountItem != null) {
            if (loggedIn) {
                if (isStaff) {
                    accountItem.setTitle("Admin Dash");
                    accountItem.setIcon(R.drawable.ic_nav_dashboard);
                } else {
                    accountItem.setTitle("My Account");
                    accountItem.setIcon(R.drawable.ic_nav_account);
                }
            } else {
                accountItem.setTitle("Sign In");
                accountItem.setIcon(R.drawable.ic_nav_account);
            }
        }
    }

    /**
     * Setup click listeners for dedicated Branches page.
     */
    private void setupBranchesPanel() {
        binding.btnColomboMap.setOnClickListener(v -> openLocationInMaps("6.9271,79.8612", "TechFix Repair Shop - Colombo Branch"));
        binding.btnGalleMap.setOnClickListener(v -> openLocationInMaps("6.0535,80.2210", "TechFix Repair Shop - Galle Branch"));
        binding.btnColomboBook.setOnClickListener(v -> selectBranchAndOpenBooking("Colombo branch"));
        binding.btnGalleBook.setOnClickListener(v -> selectBranchAndOpenBooking("Galle branch"));
    }

    private void selectBranchAndOpenBooking(String branch) {
        showBookAppointmentPanel();
        binding.bottomNavigation.setSelectedItemId(R.id.nav_home_book);
        String display = com.techfix.app.database.BranchDAO.toDisplayName(branch);
        int count = binding.bookingBranchSpinner.getAdapter() != null ? binding.bookingBranchSpinner.getAdapter().getCount() : 0;
        for (int i = 0; i < count; i++) {
            if (display.equalsIgnoreCase(String.valueOf(binding.bookingBranchSpinner.getItemAtPosition(i)))) {
                binding.bookingBranchSpinner.setSelection(i);
                break;
            }
        }
    }

    /**
     * Shows the Store Catalog panel and hides others.
     */
    private void showStorePanel() {
        binding.storePanel.setVisibility(View.VISIBLE);
        binding.partsPanel.setVisibility(View.GONE);
        binding.bookAppointmentPanel.setVisibility(View.GONE);
        binding.branchesPanel.setVisibility(View.GONE);
        binding.topBarTitle.setText("TechFix Store");
        binding.topBarSubtitle.setText("Computer & Mobile Phone Repairs");
    }

    /**
     * Shows the Dedicated Spare Parts panel and hides others.
     */
    private void showPartsPanel() {
        binding.storePanel.setVisibility(View.GONE);
        binding.partsPanel.setVisibility(View.VISIBLE);
        binding.bookAppointmentPanel.setVisibility(View.GONE);
        binding.branchesPanel.setVisibility(View.GONE);
        binding.topBarTitle.setText("Workshop Spare Parts");
        binding.topBarSubtitle.setText("Live Inventory & Component Availability");
        loadAvailableParts();
    }

    /**
     * Shows the Book Appointment panel and hides others.
     */
    private void showBookAppointmentPanel() {
        binding.storePanel.setVisibility(View.GONE);
        binding.partsPanel.setVisibility(View.GONE);
        binding.bookAppointmentPanel.setVisibility(View.VISIBLE);
        binding.branchesPanel.setVisibility(View.GONE);
        binding.topBarTitle.setText("Book Appointment");
        binding.topBarSubtitle.setText("Repair request & device details");
        suggestNearestBranch();
    }

    /**
     * If location permission is granted, auto-selects the nearest branch and tells
     * the customer how far away it is. Requests permission once if needed.
     */
    private void suggestNearestBranch() {
        if (com.techfix.app.util.NearestBranch.hasPermission(this)) {
            com.techfix.app.util.NearestBranch.resolve(this, dbHelper, (branchName, km) -> runOnUiThread(() -> {
                if (branchName == null) return;
                String display = com.techfix.app.database.BranchDAO.toDisplayName(branchName);
                ArrayAdapter adapter = (ArrayAdapter) binding.bookingBranchSpinner.getAdapter();
                if (adapter == null) return;
                int pos = adapter.getPosition(display);
                if (pos >= 0 && binding.bookingBranchSpinner.getSelectedItemPosition() != pos) {
                    binding.bookingBranchSpinner.setSelection(pos, false);
                    Toast.makeText(this, String.format("Nearest branch: %s (%.1f km away)", display, km), Toast.LENGTH_LONG).show();
                }
            }));
        } else if (!locationPermissionRequested) {
            locationPermissionRequested = true;
            locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    /**
     * Shows the Dedicated Store Branches panel and hides others.
     */
    private void showBranchesPanel() {
        binding.storePanel.setVisibility(View.GONE);
        binding.partsPanel.setVisibility(View.GONE);
        binding.bookAppointmentPanel.setVisibility(View.GONE);
        binding.branchesPanel.setVisibility(View.VISIBLE);
        binding.topBarTitle.setText("Our Store Branches");
        binding.topBarSubtitle.setText("Colombo & Galle Repair Centers");
    }

    /**
     * Setup the Book Repair Appointment form with service dropdown, device info, photo picker, and submit button.
     */
    private void setupBookingForm() {
        // 1. Setup Service Type Dropdown (no mock fallback — disable if DB is empty)
        List<String> serviceOptions = serviceDAO.all();
        if (serviceOptions.isEmpty()) {
            serviceOptions.add("No services available right now");
        }
        ArrayAdapter<String> serviceAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, serviceOptions);
        serviceAdapter.setDropDownViewResource(R.layout.item_dropdown_popup);
        binding.bookingServiceSpinner.setAdapter(serviceAdapter);
        binding.bookingServiceSpinner.setEnabled(!serviceDAO.all().isEmpty());

        // 2. Setup Device Category Dropdown
        String[] deviceCategories = {"Mobile phone", "Laptop / computer", "Tablet", "Other smart device"};
        ArrayAdapter<String> deviceAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, deviceCategories);
        deviceAdapter.setDropDownViewResource(R.layout.item_dropdown_popup);
        binding.bookingDeviceSpinner.setAdapter(deviceAdapter);

        // 3. Setup Branch Dropdown & Dynamic Service Loading (from BranchDAO — single source of truth)
        String[] branches = new com.techfix.app.database.BranchDAO(dbHelper).displayNamesArray();
        ArrayAdapter<String> branchAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, branches);
        branchAdapter.setDropDownViewResource(R.layout.item_dropdown_popup);
        binding.bookingBranchSpinner.setAdapter(branchAdapter);

        updateBookingServicesForBranch(com.techfix.app.database.BranchDAO.toDbName(branches[0]));

        binding.bookingBranchSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateBookingServicesForBranch(com.techfix.app.database.BranchDAO.toDbName(branches[position]));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 4. Setup Attach Photo Button & Drop Zone (Direct Camera Permission & Launch)
        binding.bookingPhotoButton.setOnClickListener(v -> checkCameraPermissionAndLaunch());
        binding.bookingPhotoDropZone.setOnClickListener(v -> checkCameraPermissionAndLaunch());

        // 5. Setup Remove Photo Button
        binding.removePhotoButton.setOnClickListener(v -> {
            selectedPhotoUri = null;
            tempCameraUri = null;
            binding.bookingPhotoPreview.setImageURI(null);
            binding.photoPreviewContainer.setVisibility(View.GONE);
            binding.photoStatusText.setText("No photo attached");
            binding.photoStatusText.setTextColor(ContextCompat.getColor(this, R.color.muted_text));
        });

        // 6. Setup Submit Booking Button
        binding.submitBookingButton.setOnClickListener(v -> submitAppointmentBooking());
    }

    private void updateBookingServicesForBranch(String branch) {
        List<String> serviceOptions = serviceDAO.allByBranch(branch);
        if (serviceOptions.isEmpty()) {
            serviceOptions = serviceDAO.all();
        }
        ArrayAdapter<String> serviceAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, serviceOptions);
        serviceAdapter.setDropDownViewResource(R.layout.item_dropdown_popup);
        binding.bookingServiceSpinner.setAdapter(serviceAdapter);
    }

    /**
     * Validates and submits the appointment booking to the SQLite database.
     * Requires the user to be logged in with an account before placing the appointment.
     */
    private void submitAppointmentBooking() {
        // 1. Enforce user login/account requirement
        if (!session.isLoggedIn()) {
            new AlertDialog.Builder(this)
                    .setTitle("Account Required")
                    .setMessage("To place a repair appointment, please sign in or create an account so you can track and manage your repairs.")
                    .setPositiveButton("Sign In / Create Account", (dialog, which) -> {
                        Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
                        startActivity(intent);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        String serviceSelection = (String) binding.bookingServiceSpinner.getSelectedItem();
        String deviceCategory = (String) binding.bookingDeviceSpinner.getSelectedItem();
        String deviceModel = binding.bookingModelInput.getText().toString().trim();
        String problemDescription = binding.bookingProblemInput.getText().toString().trim();
        String branchDisplay = (String) binding.bookingBranchSpinner.getSelectedItem();
        String branch = com.techfix.app.database.BranchDAO.toDbName(branchDisplay);

        // 2. Validate input fields
        if (deviceModel.isEmpty()) {
            binding.bookingModelInput.setError("Please enter your device model / brand");
            binding.bookingModelInput.requestFocus();
            return;
        }

        if (problemDescription.isEmpty()) {
            binding.bookingProblemInput.setError("Please describe the problem or damage");
            binding.bookingProblemInput.requestFocus();
            return;
        }

        // 3. Parse service name and price
        String serviceName = serviceDAO.serviceName(serviceSelection != null ? serviceSelection : "Repair Service");
        double price = serviceDAO.price(serviceSelection != null ? serviceSelection : "0");

        // 4. Combine device info: e.g. "Mobile phone (iPhone 13 Pro)"
        String fullDeviceInfo = deviceCategory + " (" + deviceModel + ")";

        // 5. Get logged-in user ID
        long userId = session.getUserId();

        // 5b. Reserve the service's required spare part at this branch, if it needs one
        com.techfix.app.database.SparePartDAO sparePartDAO = new com.techfix.app.database.SparePartDAO(dbHelper);
        String requiredPart = serviceDAO.requiredPart(serviceName);
        if (requiredPart != null && !requiredPart.isEmpty()) {
            if (sparePartDAO.quantity(requiredPart, branch) <= 0) {
                Toast.makeText(this, "Required part '" + requiredPart + "' is out of stock at " + branchDisplay + ". Please choose another branch or contact the counter.", Toast.LENGTH_LONG).show();
                return;
            }
        }

        // 6. Auto-assign available technician for the branch
        String technician = new TechnicianDAO(dbHelper).availableFor(branch, deviceCategory);
        if (technician == null || technician.trim().isEmpty()) {
            technician = "Unassigned";
        }

        // 7-9. DB writes + sync off the main thread; dialog returns via runOnUiThread
        final String fServiceName = serviceName;
        final double fPrice = price;
        final String fDeviceInfo = fullDeviceInfo;
        final String fRequiredPart = requiredPart;
        final String fTechnician = technician;
        final long fUserId = userId;
        com.techfix.app.util.AppExecutors.run(() -> {
            long appointmentId = appointmentDAO.add(fUserId, fDeviceInfo, problemDescription, branch, fServiceName, fPrice, fTechnician, "");

            boolean partReserved = false;
            if (appointmentId > 0 && fRequiredPart != null && !fRequiredPart.isEmpty()) {
                partReserved = sparePartDAO.consume(fRequiredPart, branch);
            }

            if (selectedPhotoUri != null && appointmentId > 0) {
                appointmentDAO.setPhoto(appointmentId, selectedPhotoUri.toString());
            }

            // Trigger instant cloud sync to Firebase if online
            FirebaseSyncManager.getInstance().sync(this, null);
            com.techfix.app.util.Analytics.log(this, "booking_created", "branch", branch);

            final boolean fPartReserved = partReserved;
            final long fAppointmentId = appointmentId;
            runOnUiThread(() -> {
                new AlertDialog.Builder(this)
                        .setTitle("Appointment Booked!")
                        .setMessage("Your repair appointment #" + fAppointmentId + " has been booked successfully at " + branch + ".\n\nTechnician assigned: " + ("Unassigned".equals(fTechnician) ? "will be assigned at the counter" : fTechnician) + "\nService: " + fServiceName + " (Rs " + (long) fPrice + ")"
                                + (fPartReserved ? "\nPart reserved: " + fRequiredPart : ""))
                        .setPositiveButton("OK", (dialog, which) -> {
                            // Reset form fields
                            binding.bookingModelInput.setText("");
                            binding.bookingProblemInput.setText("");
                            selectedPhotoUri = null;
                            binding.photoPreviewContainer.setVisibility(View.GONE);
                            binding.photoStatusText.setText("No photo attached");

                            // Switch back to Store panel
                            binding.bottomNavigation.setSelectedItemId(R.id.nav_home_store);
                        })
                        .show();
            });
        });
    }

    /**
     * Setup round category click listeners.
     */
    private void setupRoundCategories() {
        binding.catAll.setOnClickListener(v -> selectCategory("ALL"));
        binding.catPhones.setOnClickListener(v -> selectCategory("PHONES"));
        binding.catComputers.setOnClickListener(v -> selectCategory("COMPUTERS"));
        binding.catScreens.setOnClickListener(v -> selectCategory("SCREENS"));
        binding.catBatteries.setOnClickListener(v -> selectCategory("BATTERIES"));
    }

    /**
     * Updates visual state of round category circles and re-filters the store items.
     */
    private void selectCategory(String categoryKey) {
        currentCategory = categoryKey;

        // Reset all category circles to default unselected appearance (Navy icon on White circle)
        resetCategoryStyles();

        // Highlight the chosen category circle (White icon on Navy circle)
        if ("ALL".equals(categoryKey)) {
            highlightCategory(binding.circleCatAll, binding.iconCatAll, binding.labelCatAll);
        } else if ("PHONES".equals(categoryKey)) {
            highlightCategory(binding.circleCatPhones, binding.iconCatPhones, binding.labelCatPhones);
        } else if ("COMPUTERS".equals(categoryKey)) {
            highlightCategory(binding.circleCatComputers, binding.iconCatComputers, binding.labelCatComputers);
        } else if ("SCREENS".equals(categoryKey)) {
            highlightCategory(binding.circleCatScreens, binding.iconCatScreens, binding.labelCatScreens);
        } else if ("BATTERIES".equals(categoryKey)) {
            highlightCategory(binding.circleCatBatteries, binding.iconCatBatteries, binding.labelCatBatteries);
        }

        // Reload repair services according to the selected filter
        loadAvailableServices(categoryKey);
    }

    /**
     * Reset all category circles to unselected appearance.
     * Sets icon tint to Navy so icons are clearly visible on the white circle background.
     */
    private void resetCategoryStyles() {
        int unselectedBg = R.drawable.bg_circle_category;
        int mutedColor = getResources().getColor(R.color.muted_text, null);
        int primaryColor = getResources().getColor(R.color.primary, null);

        // Category: All
        binding.circleCatAll.setBackgroundResource(unselectedBg);
        binding.iconCatAll.setColorFilter(primaryColor);
        binding.labelCatAll.setTextColor(mutedColor);

        // Category: Phones
        binding.circleCatPhones.setBackgroundResource(unselectedBg);
        binding.iconCatPhones.setColorFilter(primaryColor);
        binding.labelCatPhones.setTextColor(mutedColor);

        // Category: Computers
        binding.circleCatComputers.setBackgroundResource(unselectedBg);
        binding.iconCatComputers.setColorFilter(primaryColor);
        binding.labelCatComputers.setTextColor(mutedColor);

        // Category: Screens
        binding.circleCatScreens.setBackgroundResource(unselectedBg);
        binding.iconCatScreens.setColorFilter(primaryColor);
        binding.labelCatScreens.setTextColor(mutedColor);

        // Category: Batteries
        binding.circleCatBatteries.setBackgroundResource(unselectedBg);
        binding.iconCatBatteries.setColorFilter(primaryColor);
        binding.labelCatBatteries.setTextColor(mutedColor);
    }

    /**
     * Highlight a single category circle when active.
     */
    private void highlightCategory(View circleView, ImageView iconView, TextView labelView) {
        circleView.setBackgroundResource(R.drawable.bg_circle_category_selected);
        iconView.setColorFilter(getResources().getColor(R.color.white, null));
        labelView.setTextColor(getResources().getColor(R.color.ink, null));
    }

    /**
     * Load computer and mobile phone repair services from the SQLite database with category filtering.
     */
    private void loadAvailableServices(String filter) {
        binding.servicesContainer.removeAllViews();

        List<Service> services = serviceDAO.list();
        int count = 0;

        for (Service service : services) {
            if (!matchesFilter(service.name, service.category, filter)) {
                continue;
            }

            count++;
            View itemView = getLayoutInflater().inflate(R.layout.item_home_service, binding.servicesContainer, false);

            ImageView imageView = itemView.findViewById(R.id.serviceImageView);
            TextView nameText = itemView.findViewById(R.id.serviceNameText);
            TextView priceText = itemView.findViewById(R.id.servicePriceText);
            TextView categoryText = itemView.findViewById(R.id.serviceCategoryText);
            TextView partText = itemView.findViewById(R.id.servicePartText);
            TextView badgeText = itemView.findViewById(R.id.serviceBadge);

            imageView.setImageResource(getServiceImageResource(service.name, service.category));
            nameText.setText(service.name);
            priceText.setText("Rs " + String.format("%,d", (long) service.price));
            categoryText.setText(service.category);
            badgeText.setText("Book Now");

            if (service.requiredPart != null && !service.requiredPart.isEmpty()) {
                partText.setText("Includes part: " + service.requiredPart);
                partText.setVisibility(View.VISIBLE);
            } else {
                partText.setText("Full diagnostic & repair");
                partText.setVisibility(View.VISIBLE);
            }

            // Click service -> open Book Appointment in bottom nav and pre-fill
            itemView.setOnClickListener(v -> {
                binding.bookingProblemInput.setText(service.name + " - " + (service.requiredPart != null && !service.requiredPart.isEmpty() ? service.requiredPart : "Diagnostic & Repair"));
                binding.bottomNavigation.setSelectedItemId(R.id.nav_home_book);
            });

            binding.servicesContainer.addView(itemView);
        }

        binding.servicesHeaderTitle.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        binding.servicesHeaderSubtitle.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        binding.servicesContainer.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
    }

    /**
     * Load sample repaired-device images published by staff into the customer showcase gallery.
     */
    private void loadSampleShowcase() {
        if (binding.samplesContainer.getAdapter() == null) {
            sampleImageAdapter = new SampleImageAdapter();
            binding.samplesContainer.setLayoutManager(
                    new androidx.recyclerview.widget.LinearLayoutManager(this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false));
            binding.samplesContainer.setAdapter(sampleImageAdapter);
        }
        List<com.techfix.app.models.SampleRepair> samples = sampleRepairDAO.all();
        sampleImageAdapter.submit(samples);
        boolean hasSamples = !samples.isEmpty();
        binding.samplesHeaderTitle.setVisibility(hasSamples ? View.VISIBLE : View.GONE);
        binding.samplesContainer.setVisibility(hasSamples ? View.VISIBLE : View.GONE);
        binding.samplesEmptyText.setVisibility(hasSamples ? View.GONE : View.VISIBLE);
    }

    /**
     * Checks if an item matches the current active category filter.
     */
    private boolean matchesFilter(String name, String extra, String filter) {
        if ("ALL".equals(filter)) return true;
        String combined = (name + " " + extra).toLowerCase();

        switch (filter) {
            case "PHONES":
                return combined.contains("phone") || combined.contains("display");
            case "COMPUTERS":
                return combined.contains("laptop") || combined.contains("computer") || combined.contains("system") || combined.contains("diagnostics");
            case "SCREENS":
                return combined.contains("screen") || combined.contains("display");
            case "BATTERIES":
                return combined.contains("battery");
            default:
                return true;
        }
    }

    /**
     * Setup Branch click listeners for Google Maps.
     */
    private void setupBranchButtons() {
        binding.btnColomboMap.setOnClickListener(v -> openLocationInMaps("6.9271,79.8612", "TechFix Colombo Branch"));
        binding.btnGalleMap.setOnClickListener(v -> openLocationInMaps("6.0329,80.2168", "TechFix Galle Branch"));
    }

    /**
     * Helper method to choose an image for a repair service.
     */
    private int getServiceImageResource(String serviceName, String category) {
        String query = (serviceName + " " + category).toLowerCase();
        if (query.contains("screen") || query.contains("display")) {
            return R.drawable.ic_store_phone_screen;
        } else if (query.contains("battery")) {
            return R.drawable.ic_store_battery;
        } else if (query.contains("diagnostics")) {
            return R.drawable.ic_store_laptop_diagnostics;
        } else if (query.contains("system") || query.contains("os") || query.contains("software")) {
            return R.drawable.ic_store_os_repair;
        } else {
            return R.drawable.ic_store_phone_screen;
        }
    }

    /**
     * Setup the Spare Parts panel: branch filter dropdown & real-time search input.
     */
    private void setupPartsPanel() {
        String[] branches = new com.techfix.app.database.BranchDAO(dbHelper).filterNamesArray();
        ArrayAdapter<String> branchAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, branches);
        branchAdapter.setDropDownViewResource(R.layout.item_dropdown_popup);
        binding.homePartsBranchSpinner.setAdapter(branchAdapter);

        binding.homePartsBranchSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentPartsBranch = com.techfix.app.database.BranchDAO.toDbName(branches[position]);
                loadAvailableParts();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        binding.searchHomePartsInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                loadAvailableParts();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    /**
     * Loads spare parts from SQLite database and inflates them into homePartsContainer.
     */
    private void loadAvailableParts() {
        binding.homePartsContainer.removeAllViews();
        List<SparePart> parts = sparePartDAO.allByBranch(currentPartsBranch);
        String searchQuery = binding.searchHomePartsInput.getText() != null
                ? binding.searchHomePartsInput.getText().toString().trim().toLowerCase()
                : "";

        int count = 0;
        for (SparePart part : parts) {
            if (!searchQuery.isEmpty() && !part.name.toLowerCase().contains(searchQuery)) {
                continue;
            }

            count++;
            View itemView = getLayoutInflater().inflate(R.layout.item_home_part, binding.homePartsContainer, false);

            ImageView imageView = itemView.findViewById(R.id.partImageView);
            TextView branchText = itemView.findViewById(R.id.partBranchText);
            TextView nameText = itemView.findViewById(R.id.partNameText);
            TextView qtyText = itemView.findViewById(R.id.partQuantityText);
            TextView badgeText = itemView.findViewById(R.id.partStatusBadge);

            branchText.setText(com.techfix.app.database.BranchDAO.toDisplayName(part.branch));
            nameText.setText(part.name);
            qtyText.setText(part.quantity + " units available in store");

            // Choose icon
            if (part.name.toLowerCase().contains("screen") || part.name.toLowerCase().contains("display")) {
                imageView.setImageResource(R.drawable.ic_store_phone_screen);
            } else if (part.name.toLowerCase().contains("battery")) {
                if (part.name.toLowerCase().contains("laptop")) {
                    imageView.setImageResource(R.drawable.ic_store_laptop_battery);
                } else {
                    imageView.setImageResource(R.drawable.ic_store_battery);
                }
            } else {
                imageView.setImageResource(R.drawable.ic_store_hardware_part);
            }

            // Stock badge status
            if (part.quantity == 0) {
                badgeText.setText("Out of Stock");
                badgeText.setTextColor(getColor(R.color.error));
            } else if (part.quantity < 3) {
                badgeText.setText("Low Stock (" + part.quantity + ")");
                badgeText.setTextColor(getColor(R.color.warning));
            } else {
                badgeText.setText("In Stock (" + part.quantity + ")");
                badgeText.setTextColor(getColor(R.color.success));
            }

            // When customer taps part -> switch to Booking tab
            itemView.setOnClickListener(v -> {
                binding.bottomNavigation.setSelectedItemId(R.id.nav_home_book);
                Toast.makeText(this, "Selected " + part.name + " for repair booking", Toast.LENGTH_SHORT).show();
            });

            binding.homePartsContainer.addView(itemView);
        }

        binding.emptyHomePartsContainer.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        binding.homePartsContainer.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
    }

    /**
     * Helper method to open geographical coordinates in Google Maps app.
     */
    private void openLocationInMaps(String coordinates, String label) {
        Uri mapUri = Uri.parse("geo:" + coordinates + "?q=" + Uri.encode(label));
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, mapUri);
        startActivity(mapIntent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FirebaseSyncManager.getInstance().removeListener(syncListener);
    }
}
