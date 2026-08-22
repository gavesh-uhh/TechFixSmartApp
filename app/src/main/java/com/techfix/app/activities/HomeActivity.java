package com.techfix.app.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.techfix.app.database.AppointmentDAO;
import com.techfix.app.database.DatabaseHelper;
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
    private SessionManager session;

    // Currently selected category filter: "ALL", "PHONES", "COMPUTERS", "SCREENS", "BATTERIES"
    private String currentCategory = "ALL";
    private String currentPartsBranch = "All Branches";

    private final FirebaseSyncManager.SyncListener syncListener = (isSyncing, success) -> {
        if (!isSyncing && success) {
            runOnUiThread(() -> {
                loadAvailableServices(currentCategory);
                loadAvailableParts();
            });
        }
    };

    // Attached device photo URI & temp camera URI
    private Uri selectedPhotoUri = null;
    private Uri tempCameraUri = null;

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
        binding.bookingPhotoPreview.setImageURI(uri);
        binding.photoPreviewContainer.setVisibility(View.VISIBLE);
        binding.photoStatusText.setText("Photo attached");
        binding.photoStatusText.setTextColor(ContextCompat.getColor(this, R.color.success));
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
            File photoFile = new File(cacheDir, "repair_photo_" + System.currentTimeMillis() + ".jpg");
            tempCameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
            takePictureLauncher.launch(tempCameraUri);
        } catch (Exception e) {
            Toast.makeText(this, "Failed to open camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
        showStorePanel();
        binding.bottomNavigation.setSelectedItemId(R.id.nav_home_store);
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
                // Show Store Catalog and scroll down to the branches section
                showStorePanel();
                binding.storePanel.post(() -> {
                    int targetY = binding.branchesSectionTitle.getTop();
                    binding.storePanel.smoothScrollTo(0, targetY);
                });
                return true;

            } else if (itemId == R.id.nav_home_account) {
                // Open Customer / Staff dashboard if logged in, or Login screen if not
                SessionManager currentSession = new SessionManager(HomeActivity.this);
                if (currentSession.isLoggedIn()) {
                    Class<?> target = currentSession.getRole() == UserRole.STAFF ? StaffActivity.class : CustomerActivity.class;
                    startActivity(new Intent(HomeActivity.this, target));
                } else {
                    Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
                    startActivity(intent);
                }
                return false;
            }

            return false;
        });
    }

    /**
     * Shows the Store Catalog panel and hides others.
     */
    private void showStorePanel() {
        binding.storePanel.setVisibility(View.VISIBLE);
        binding.partsPanel.setVisibility(View.GONE);
        binding.bookAppointmentPanel.setVisibility(View.GONE);
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
        binding.topBarTitle.setText("Book Appointment");
        binding.topBarSubtitle.setText("Repair request & device details");
    }

    /**
     * Setup the Book Repair Appointment form with service dropdown, device info, photo picker, and submit button.
     */
    private void setupBookingForm() {
        // 1. Setup Service Type Dropdown
        List<String> serviceOptions = serviceDAO.all();
        if (serviceOptions.isEmpty()) {
            serviceOptions.add("Screen replacement · Rs 8500");
            serviceOptions.add("Battery replacement · Rs 4500");
            serviceOptions.add("Laptop diagnostics · Rs 3000");
            serviceOptions.add("Operating system repair · Rs 6500");
        }
        ArrayAdapter<String> serviceAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, serviceOptions);
        serviceAdapter.setDropDownViewResource(R.layout.item_dropdown_popup);
        binding.bookingServiceSpinner.setAdapter(serviceAdapter);

        // 2. Setup Device Category Dropdown
        String[] deviceCategories = {"Mobile phone", "Laptop / computer", "Tablet", "Other smart device"};
        ArrayAdapter<String> deviceAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, deviceCategories);
        deviceAdapter.setDropDownViewResource(R.layout.item_dropdown_popup);
        binding.bookingDeviceSpinner.setAdapter(deviceAdapter);

        // 3. Setup Branch Dropdown & Dynamic Service Loading
        String[] branches = {"Colombo branch", "Galle branch"};
        ArrayAdapter<String> branchAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, branches);
        branchAdapter.setDropDownViewResource(R.layout.item_dropdown_popup);
        binding.bookingBranchSpinner.setAdapter(branchAdapter);

        updateBookingServicesForBranch(branches[0]);

        binding.bookingBranchSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateBookingServicesForBranch(branches[position]);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 4. Setup Attach Photo Button
        binding.bookingPhotoButton.setOnClickListener(v -> showPhotoOptionsDialog());

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
        String branch = (String) binding.bookingBranchSpinner.getSelectedItem();

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

        // 6. Auto-assign available technician for the branch
        String technician = new TechnicianDAO(dbHelper).availableFor(branch, deviceCategory);

        // 7. Insert appointment into SQLite database
        long appointmentId = appointmentDAO.add(userId, fullDeviceInfo, problemDescription, branch, serviceName, price, technician, "");

        // 8. Save attached photo URI if provided
        if (selectedPhotoUri != null && appointmentId > 0) {
            appointmentDAO.setPhoto(appointmentId, selectedPhotoUri.toString());
        }

        // Trigger instant cloud sync to Firebase if online
        FirebaseSyncManager.getInstance().sync(this, null);

        // 9. Show Success Confirmation Dialog
        new AlertDialog.Builder(this)
                .setTitle("Appointment Booked!")
                .setMessage("Your repair appointment #" + appointmentId + " has been booked successfully at " + branch + ".\n\nTechnician assigned: " + technician + "\nService: " + serviceName + " (Rs " + (long) price + ")")
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
        int navyColor = getResources().getColor(R.color.navy_700, null);

        // Category: All
        binding.circleCatAll.setBackgroundResource(unselectedBg);
        binding.iconCatAll.setColorFilter(navyColor);
        binding.labelCatAll.setTextColor(mutedColor);

        // Category: Phones
        binding.circleCatPhones.setBackgroundResource(unselectedBg);
        binding.iconCatPhones.setColorFilter(navyColor);
        binding.labelCatPhones.setTextColor(mutedColor);

        // Category: Computers
        binding.circleCatComputers.setBackgroundResource(unselectedBg);
        binding.iconCatComputers.setColorFilter(navyColor);
        binding.labelCatComputers.setTextColor(mutedColor);

        // Category: Screens
        binding.circleCatScreens.setBackgroundResource(unselectedBg);
        binding.iconCatScreens.setColorFilter(navyColor);
        binding.labelCatScreens.setTextColor(mutedColor);

        // Category: Batteries
        binding.circleCatBatteries.setBackgroundResource(unselectedBg);
        binding.iconCatBatteries.setColorFilter(navyColor);
        binding.labelCatBatteries.setTextColor(mutedColor);
    }

    /**
     * Highlight a single category circle when active.
     */
    private void highlightCategory(View circleView, ImageView iconView, TextView labelView) {
        circleView.setBackgroundResource(R.drawable.bg_circle_category_selected);
        iconView.setColorFilter(getResources().getColor(R.color.white, null));
        labelView.setTextColor(getResources().getColor(R.color.navy_900, null));
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

            // Click service -> open Book Appointment in bottom nav
            itemView.setOnClickListener(v -> {
                binding.bottomNavigation.setSelectedItemId(R.id.nav_home_book);
            });

            binding.servicesContainer.addView(itemView);
        }

        binding.servicesHeaderTitle.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        binding.servicesHeaderSubtitle.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        binding.servicesContainer.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
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
        binding.colomboChip.setOnClickListener(v -> openLocationInMaps("6.9271,79.8612", "TechFix Colombo Branch"));
        binding.galleChip.setOnClickListener(v -> openLocationInMaps("6.0329,80.2168", "TechFix Galle Branch"));
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
        String[] branches = {"All Branches", "Colombo branch", "Galle branch"};
        ArrayAdapter<String> branchAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, branches);
        branchAdapter.setDropDownViewResource(R.layout.item_dropdown_popup);
        binding.homePartsBranchSpinner.setAdapter(branchAdapter);

        binding.homePartsBranchSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentPartsBranch = branches[position];
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

            branchText.setText(part.branch);
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
