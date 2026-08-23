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
import androidx.recyclerview.widget.LinearLayoutManager;

import com.techfix.app.R;
import com.techfix.app.adapters.AppointmentAdapter;
import com.techfix.app.adapters.BranchAdapter;
import com.techfix.app.database.AppointmentDAO;
import com.techfix.app.database.BranchDAO;
import com.techfix.app.database.DatabaseHelper;
import com.techfix.app.database.SampleRepairDAO;
import com.techfix.app.database.ServiceDAO;
import com.techfix.app.database.SparePartDAO;
import com.techfix.app.database.TechnicianDAO;
import com.techfix.app.database.UserDAO;
import com.techfix.app.databinding.ActivityCustomerBinding;
import com.techfix.app.models.Appointment;
import com.techfix.app.models.PaymentStatus;
import com.techfix.app.models.SampleRepair;
import com.techfix.app.models.Service;
import com.techfix.app.models.SparePart;
import com.techfix.app.models.User;
import com.techfix.app.session.SessionManager;
import com.techfix.app.sync.FirebaseSyncManager;
import com.techfix.app.util.Feedback;
import com.techfix.app.util.WindowInsetsHelper;

import java.io.File;
import java.util.List;

/**
 * CustomerActivity - Unified Customer Workspace with Integrated Store, Repairs, Booking, and Profile.
 * Features:
 * - 4 Unified Bottom Navigation tabs: Store, My Repairs, Book Repair, Profile
 * - Instant in-app booking from store service cards
 * - Real-time repair queue tracking and invoice settlement
 * - Descriptive branch dropdowns and GPS nearest-branch assistance
 * - Single-task routing eliminating all navigation loops
 */
public class CustomerActivity extends AppCompatActivity {

    private ActivityCustomerBinding binding;
    private SessionManager session;
    private DatabaseHelper dbHelper;

    // DAOs
    private AppointmentDAO appointmentDAO;
    private ServiceDAO serviceDAO;
    private SparePartDAO sparePartDAO;
    private BranchDAO branchDAO;
    private TechnicianDAO technicianDAO;
    private SampleRepairDAO sampleRepairDAO;
    private UserDAO userDAO;

    // Adapters
    private AppointmentAdapter appointmentAdapter;
    private BranchAdapter branchAdapter;

    // Photo capture / selection
    private Uri selectedPhotoUri = null;
    private Uri tempCameraUri = null;
    private String repairFilter = "Active";
    private String currentCategory = "ALL";
    private String currentPartsBranch = "All Branches";

    private final FirebaseSyncManager.SyncListener syncListener = (isSyncing, success) -> {
        if (!isSyncing && success) {
            runOnUiThread(() -> {
                refreshRepairs();
                loadCustomerServices(currentCategory);
                loadCustomerParts();
            });
        }
    };

    // Location permission launcher for nearest-branch detection
    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), (Boolean isGranted) -> {
                if (Boolean.TRUE.equals(isGranted)) {
                    suggestNearestBranch();
                }
            });

    // Photo picker launcher (Gallery)
    private final ActivityResultLauncher<String> photoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), (Uri uri) -> {
                if (uri != null) {
                    onPhotoReady(uri);
                }
            });

    // Camera Take Picture Launcher
    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), (Boolean success) -> {
                if (Boolean.TRUE.equals(success) && tempCameraUri != null) {
                    onPhotoReady(tempCameraUri);
                }
            });

    // Camera Permission Launcher
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
                    } else if (which == 1) {
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
            if (!photoFile.exists()) {
                photoFile.createNewFile();
            }
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
        WindowInsetsHelper.apply(binding.customerHeader, binding.dashboardContent);

        // 3. Initialize DAOs
        dbHelper = DatabaseHelper.getInstance(this);
        appointmentDAO = new AppointmentDAO(dbHelper);
        serviceDAO = new ServiceDAO(dbHelper);
        sparePartDAO = new SparePartDAO(dbHelper);
        branchDAO = new BranchDAO(dbHelper);
        technicianDAO = new TechnicianDAO(dbHelper);
        sampleRepairDAO = new SampleRepairDAO(dbHelper);
        userDAO = new UserDAO(dbHelper);

        // 4. Cloud Sync
        FirebaseSyncManager.getInstance().init(this);
        FirebaseSyncManager.getInstance().addListener(syncListener);

        // 5. Initialize UI Components
        setupUserProfile();
        setupRepairsList();
        setupBookingForm();
        setupBranchesList();
        setupCustomerStore();
        setupBottomNavigation();

        // Default open to Store tab (0)
        showPanel(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            goHome();
            return;
        }
        setupUserProfile();
        refreshRepairs();
        loadCustomerServices(currentCategory);
        loadCustomerParts();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FirebaseSyncManager.getInstance().removeListener(syncListener);
    }

    /**
     * Loads logged-in user profile details into the header and profile card.
     */
    private void setupUserProfile() {
        binding.customerTopLogoutButton.setOnClickListener(v -> performLogout());
        binding.profileLogoutButton.setOnClickListener(v -> performLogout());

        try {
            User user = userDAO.get(session.getUserId());
            if (user != null) {
                binding.welcomeUserText.setText("Hello, " + user.name);
                binding.userEmailText.setText(user.email);

                binding.profileNameText.setText(user.name);
                binding.profileEmailText.setText(user.email);
                binding.profilePhoneText.setText(user.phone.isEmpty() ? "Phone: Not provided" : "Phone: " + user.phone);
            }
        } catch (Exception ignored) {}
    }

    private void performLogout() {
        session.logout();
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
        goHome();
    }

    private void goHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    /**
     * Setup bottom navigation bar (Store, My Repairs, Book Repair, Profile).
     */
    private void setupBottomNavigation() {
        binding.customerBottomNavigation.setSelectedItemId(R.id.nav_customer_store);

        binding.customerBottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_customer_store) {
                showPanel(0);
                return true;
            } else if (itemId == R.id.nav_customer_repairs) {
                showPanel(1);
                return true;
            } else if (itemId == R.id.nav_customer_book) {
                showPanel(2);
                suggestNearestBranch();
                return true;
            } else if (itemId == R.id.nav_customer_profile) {
                showPanel(3);
                return true;
            }

            return false;
        });

        binding.emptyBookButton.setOnClickListener(v -> {
            binding.customerBottomNavigation.setSelectedItemId(R.id.nav_customer_book);
        });

        // Pay pending repairs button
        binding.payButton.setOnClickListener(this::payFirstPending);

        // Repairs filter chips
        binding.chipFilterActive.setOnClickListener(v -> selectRepairFilter("Active", v));
        binding.chipFilterCompleted.setOnClickListener(v -> selectRepairFilter("Completed", v));
        binding.chipFilterAll.setOnClickListener(v -> selectRepairFilter("All", v));
    }

    private void selectRepairFilter(String filter, View selectedView) {
        repairFilter = filter;
        binding.chipFilterAll.setBackgroundResource(R.drawable.bg_filter_pill);
        binding.chipFilterAll.setTextColor(getColor(R.color.ink));
        binding.chipFilterActive.setBackgroundResource(R.drawable.bg_filter_pill);
        binding.chipFilterActive.setTextColor(getColor(R.color.ink));
        binding.chipFilterCompleted.setBackgroundResource(R.drawable.bg_filter_pill);
        binding.chipFilterCompleted.setTextColor(getColor(R.color.ink));

        if (selectedView instanceof TextView) {
            selectedView.setBackgroundResource(R.drawable.bg_filter_pill_selected);
            ((TextView) selectedView).setTextColor(getColor(R.color.white));
        }
        refreshRepairs();
    }

    /**
     * If location permission is granted, auto-selects the nearest branch in the
     * booking form and tells the customer how far away it is.
     */
    private void suggestNearestBranch() {
        if (com.techfix.app.util.NearestBranch.hasPermission(this)) {
            com.techfix.app.util.NearestBranch.resolve(this, dbHelper, (branchName, km) -> runOnUiThread(() -> {
                if (branchName == null) return;
                String display = BranchDAO.toDisplayName(branchName);
                ArrayAdapter adapter = (ArrayAdapter) binding.branchSpinner.getAdapter();
                if (adapter == null) return;
                int pos = adapter.getPosition(display);
                if (pos >= 0 && binding.branchSpinner.getSelectedItemPosition() != pos) {
                    binding.branchSpinner.setSelection(pos, false);
                    Toast.makeText(this, String.format("Nearest branch: %s (%.1f km away)", display, km), Toast.LENGTH_LONG).show();
                }
            }));
        } else {
            locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    /**
     * Shows the active tab panel (0: Store, 1: Repairs, 2: Book, 3: Profile).
     */
    private void showPanel(int position) {
        binding.customerStorePanel.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        binding.repairsPanel.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
        binding.bookPanel.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
        binding.explorePanel.setVisibility(position == 3 ? View.VISIBLE : View.GONE);

        if (position == 0) {
            loadCustomerServices(currentCategory);
            loadCustomerParts();
        } else if (position == 1) {
            refreshRepairs();
        } else if (position == 2) {
            suggestNearestBranch();
        }
    }

    // ==========================================
    // STORE CATALOG TAB METHODS
    // ==========================================
    private void setupCustomerStore() {
        binding.customerCatAll.setOnClickListener(v -> selectCategory("ALL"));
        binding.customerCatPhones.setOnClickListener(v -> selectCategory("PHONES"));
        binding.customerCatComputers.setOnClickListener(v -> selectCategory("COMPUTERS"));
        binding.customerCatScreens.setOnClickListener(v -> selectCategory("SCREENS"));
        binding.customerCatBatteries.setOnClickListener(v -> selectCategory("BATTERIES"));

        String[] branchFilters = branchDAO.filterNamesArray();
        ArrayAdapter<String> partsBranchAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, branchFilters);
        partsBranchAdapter.setDropDownViewResource(R.layout.item_dropdown_popup);
        binding.customerPartsBranchSpinner.setAdapter(partsBranchAdapter);

        binding.customerPartsBranchSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentPartsBranch = BranchDAO.toDbName(branchFilters[position]);
                loadCustomerParts();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        binding.searchCustomerPartsInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                loadCustomerParts();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadCustomerServices("ALL");
        loadCustomerParts();
    }

    private void selectCategory(String categoryKey) {
        currentCategory = categoryKey;
        resetCategoryStyles();

        if ("ALL".equals(categoryKey)) {
            highlightCategory(binding.customerCircleCatAll, binding.customerIconCatAll, binding.customerLabelCatAll);
        } else if ("PHONES".equals(categoryKey)) {
            highlightCategory(binding.customerCircleCatPhones, binding.customerIconCatPhones, binding.customerLabelCatPhones);
        } else if ("COMPUTERS".equals(categoryKey)) {
            highlightCategory(binding.customerCircleCatComputers, binding.customerIconCatComputers, binding.customerLabelCatComputers);
        } else if ("SCREENS".equals(categoryKey)) {
            highlightCategory(binding.customerCircleCatScreens, binding.customerIconCatScreens, binding.customerLabelCatScreens);
        } else if ("BATTERIES".equals(categoryKey)) {
            highlightCategory(binding.customerCircleCatBatteries, binding.customerIconCatBatteries, binding.customerLabelCatBatteries);
        }

        loadCustomerServices(categoryKey);
    }

    private void resetCategoryStyles() {
        int unselectedBg = R.drawable.bg_circle_category;
        int mutedColor = getColor(R.color.muted_text);
        int primaryColor = getColor(R.color.primary);

        binding.customerCircleCatAll.setBackgroundResource(unselectedBg);
        binding.customerIconCatAll.setColorFilter(primaryColor);
        binding.customerLabelCatAll.setTextColor(mutedColor);

        binding.customerCircleCatPhones.setBackgroundResource(unselectedBg);
        binding.customerIconCatPhones.setColorFilter(primaryColor);
        binding.customerLabelCatPhones.setTextColor(mutedColor);

        binding.customerCircleCatComputers.setBackgroundResource(unselectedBg);
        binding.customerIconCatComputers.setColorFilter(primaryColor);
        binding.customerLabelCatComputers.setTextColor(mutedColor);

        binding.customerCircleCatScreens.setBackgroundResource(unselectedBg);
        binding.customerIconCatScreens.setColorFilter(primaryColor);
        binding.customerLabelCatScreens.setTextColor(mutedColor);

        binding.customerCircleCatBatteries.setBackgroundResource(unselectedBg);
        binding.customerIconCatBatteries.setColorFilter(primaryColor);
        binding.customerLabelCatBatteries.setTextColor(mutedColor);
    }

    private void highlightCategory(View circleView, ImageView iconView, TextView labelView) {
        circleView.setBackgroundResource(R.drawable.bg_circle_category_selected);
        iconView.setColorFilter(getColor(R.color.white));
        labelView.setTextColor(getColor(R.color.ink));
    }

    private void loadCustomerServices(String filter) {
        binding.customerServicesContainer.removeAllViews();
        List<Service> services = serviceDAO.list();

        for (Service service : services) {
            if (!matchesFilter(service.name, service.category, filter)) {
                continue;
            }

            View itemView = getLayoutInflater().inflate(R.layout.item_home_service, binding.customerServicesContainer, false);
            ImageView imageView = itemView.findViewById(R.id.serviceImageView);
            TextView nameText = itemView.findViewById(R.id.serviceNameText);
            TextView priceText = itemView.findViewById(R.id.servicePriceText);
            TextView categoryText = itemView.findViewById(R.id.serviceCategoryText);
            TextView partText = itemView.findViewById(R.id.servicePartText);
            TextView bookButton = itemView.findViewById(R.id.serviceBadge);

            imageView.setImageResource(getServiceImageResource(service.name, service.category));
            nameText.setText(service.name);
            priceText.setText("Rs " + String.format("%,d", (long) service.price));
            categoryText.setText(service.category);

            if (service.requiredPart != null && !service.requiredPart.isEmpty()) {
                partText.setText("Includes part: " + service.requiredPart);
                partText.setVisibility(View.VISIBLE);
            } else {
                partText.setText("Full workshop diagnostic & repair");
                partText.setVisibility(View.VISIBLE);
            }

            View.OnClickListener clickListener = v -> {
                binding.customerBottomNavigation.setSelectedItemId(R.id.nav_customer_book);
                prefillBooking(service.name, service.category, service.branch);
            };

            itemView.setOnClickListener(clickListener);
            if (bookButton != null) bookButton.setOnClickListener(clickListener);

            binding.customerServicesContainer.addView(itemView);
        }
    }

    private void prefillBooking(String serviceName, String category, String branch) {
        // 1. Select service
        ArrayAdapter<String> serviceAdapter = (ArrayAdapter<String>) binding.serviceSpinner.getAdapter();
        if (serviceAdapter != null) {
            for (int i = 0; i < serviceAdapter.getCount(); i++) {
                if (serviceAdapter.getItem(i).contains(serviceName)) {
                    binding.serviceSpinner.setSelection(i);
                    break;
                }
            }
        }

        // 2. Select device category
        ArrayAdapter<String> deviceAdapter = (ArrayAdapter<String>) binding.deviceSpinner.getAdapter();
        if (deviceAdapter != null && category != null) {
            for (int i = 0; i < deviceAdapter.getCount(); i++) {
                if (deviceAdapter.getItem(i).equalsIgnoreCase(category)) {
                    binding.deviceSpinner.setSelection(i);
                    break;
                }
            }
        }

        // 3. Select branch if specific
        if (branch != null && !branch.isEmpty() && !"All Branches".equalsIgnoreCase(branch)) {
            String display = BranchDAO.toDisplayName(branch);
            ArrayAdapter<String> branchAdapter = (ArrayAdapter<String>) binding.branchSpinner.getAdapter();
            if (branchAdapter != null) {
                for (int i = 0; i < branchAdapter.getCount(); i++) {
                    if (branchAdapter.getItem(i).equalsIgnoreCase(display)) {
                        binding.branchSpinner.setSelection(i);
                        break;
                    }
                }
            }
        }
    }

    private void loadCustomerParts() {
        binding.customerPartsContainer.removeAllViews();
        String query = binding.searchCustomerPartsInput.getText().toString().trim().toLowerCase();

        List<SparePart> parts = sparePartDAO.allByBranch(currentPartsBranch);
        int matched = 0;

        for (SparePart part : parts) {
            if (!query.isEmpty() && !part.name.toLowerCase().contains(query)) {
                continue;
            }

            matched++;
            View itemView = getLayoutInflater().inflate(R.layout.item_home_part, binding.customerPartsContainer, false);
            ImageView imageView = itemView.findViewById(R.id.partImageView);
            TextView nameText = itemView.findViewById(R.id.partNameText);
            TextView branchText = itemView.findViewById(R.id.partBranchText);
            TextView qtyText = itemView.findViewById(R.id.partQuantityText);
            TextView statusBadge = itemView.findViewById(R.id.partStatusBadge);

            nameText.setText(part.name);
            branchText.setText(BranchDAO.toDisplayName(part.branch));
            qtyText.setText(part.quantity + " units available in store");

            if (part.name.toLowerCase().contains("screen") || part.name.toLowerCase().contains("display")) {
                imageView.setImageResource(R.drawable.ic_store_phone_screen);
            } else if (part.name.toLowerCase().contains("battery")) {
                imageView.setImageResource(R.drawable.ic_store_battery);
            } else {
                imageView.setImageResource(R.drawable.ic_store_hardware_part);
            }

            if (part.quantity == 0) {
                statusBadge.setText("Out of Stock");
                statusBadge.setTextColor(getColor(R.color.error));
            } else if (part.quantity < 3) {
                statusBadge.setText("Low Stock (" + part.quantity + ")");
                statusBadge.setTextColor(getColor(R.color.warning));
            } else {
                statusBadge.setText("In Stock (" + part.quantity + ")");
                statusBadge.setTextColor(getColor(R.color.success));
            }

            itemView.setOnClickListener(v -> {
                binding.customerBottomNavigation.setSelectedItemId(R.id.nav_customer_book);
                Toast.makeText(this, "Selected " + part.name + " for repair booking", Toast.LENGTH_SHORT).show();
            });

            binding.customerPartsContainer.addView(itemView);
        }

        binding.emptyCustomerPartsContainer.setVisibility(matched == 0 ? View.VISIBLE : View.GONE);
    }

    private boolean matchesFilter(String name, String category, String filter) {
        if ("ALL".equals(filter)) return true;
        String combined = (name + " " + category).toLowerCase();

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

    // ==========================================
    // MY REPAIRS TAB METHODS
    // ==========================================
    private void setupRepairsList() {
        appointmentAdapter = new AppointmentAdapter(appointment -> {
            Intent intent = new Intent(CustomerActivity.this, AppointmentDetailActivity.class);
            intent.putExtra("appointmentId", appointment.id);
            startActivity(intent);
        });

        binding.repairList.setLayoutManager(new LinearLayoutManager(this));
        binding.repairList.setAdapter(appointmentAdapter);
    }

    private void refreshRepairs() {
        List<Appointment> repairs = appointmentDAO.forUser(session.getUserId());

        List<Appointment> filtered = new java.util.ArrayList<>();
        for (Appointment a : repairs) {
            boolean completed = com.techfix.app.models.AppointmentStatus.COMPLETED.label.equals(a.status);
            if ("Active".equals(repairFilter) && completed) continue;
            if ("Completed".equals(repairFilter) && !completed) continue;
            filtered.add(a);
        }
        appointmentAdapter.submitList(filtered);

        boolean hasRepairs = !filtered.isEmpty();
        binding.repairList.setVisibility(hasRepairs ? View.VISIBLE : View.GONE);
        binding.emptyStateContainer.setVisibility(hasRepairs ? View.GONE : View.VISIBLE);

        boolean hasPendingPayment = false;
        for (Appointment a : repairs) {
            if (PaymentStatus.PENDING.label.equalsIgnoreCase(a.payment)) {
                hasPendingPayment = true;
                break;
            }
        }
        binding.paymentCard.setVisibility(hasPendingPayment ? View.VISIBLE : View.GONE);
    }

    // ==========================================
    // BOOK REPAIR TAB METHODS
    // ==========================================
    private void setupBookingForm() {
        List<String> serviceOptions = serviceDAO.all();
        if (serviceOptions.isEmpty()) {
            serviceOptions.add("No services available right now");
        }
        ArrayAdapter<String> serviceAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, serviceOptions);
        serviceAdapter.setDropDownViewResource(R.layout.item_dropdown_popup);
        binding.serviceSpinner.setAdapter(serviceAdapter);

        String[] deviceCategories = {"Mobile phone", "Laptop / computer", "Tablet", "Other smart device"};
        ArrayAdapter<String> deviceAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, deviceCategories);
        deviceAdapter.setDropDownViewResource(R.layout.item_dropdown_popup);
        binding.deviceSpinner.setAdapter(deviceAdapter);

        String[] branches = branchDAO.displayNamesArray();
        ArrayAdapter<String> branchAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, branches);
        branchAdapter.setDropDownViewResource(R.layout.item_dropdown_popup);
        binding.branchSpinner.setAdapter(branchAdapter);

        updateCustomerBookingServicesForBranch(BranchDAO.toDbName(branches[0]));

        binding.branchSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateCustomerBookingServicesForBranch(BranchDAO.toDbName(branches[position]));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Setup Attach Photo Button & Drop Zone
        binding.cameraButton.setOnClickListener(v -> showPhotoOptionsDialog());
        binding.customerPhotoDropZone.setOnClickListener(v -> showPhotoOptionsDialog());

        binding.customerRemovePhotoButton.setOnClickListener(v -> {
            selectedPhotoUri = null;
            tempCameraUri = null;
            binding.customerPhotoPreview.setImageURI(null);
            binding.customerPhotoPreviewContainer.setVisibility(View.GONE);
            binding.customerPhotoStatus.setText("No photo attached");
            binding.customerPhotoStatus.setTextColor(ContextCompat.getColor(this, R.color.muted_text));
        });

        binding.bookButton.setOnClickListener(v -> submitBooking());
    }

    private void updateCustomerBookingServicesForBranch(String branch) {
        List<String> serviceOptions = serviceDAO.allByBranch(branch);
        if (serviceOptions.isEmpty()) {
            serviceOptions = serviceDAO.all();
        }
        if (serviceOptions.isEmpty()) {
            serviceOptions.add("No services available right now");
        }
        ArrayAdapter<String> serviceAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, serviceOptions);
        serviceAdapter.setDropDownViewResource(R.layout.item_dropdown_popup);
        binding.serviceSpinner.setAdapter(serviceAdapter);
        binding.serviceSpinner.setEnabled(!serviceOptions.isEmpty()
                && !"No services available right now".equals(serviceOptions.get(0)));
    }

    private void submitBooking() {
        String serviceSelection = (String) binding.serviceSpinner.getSelectedItem();
        String deviceCategory = (String) binding.deviceSpinner.getSelectedItem();
        String deviceModel = binding.deviceModelInput.getText().toString().trim();
        String problem = binding.problemInput.getText().toString().trim();
        String branchDisplay = (String) binding.branchSpinner.getSelectedItem();
        String branch = BranchDAO.toDbName(branchDisplay);

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

        String requiredPart = serviceDAO.requiredPart(serviceName);
        if (requiredPart != null && !requiredPart.isEmpty()) {
            if (sparePartDAO.quantity(requiredPart, branch) <= 0) {
                Feedback.error(binding.getRoot(), "Required part '" + requiredPart + "' is out of stock at " + branchDisplay
                        + ". Please choose another branch or contact the counter.");
                return;
            }
        }

        String technician = technicianDAO.availableFor(branch, deviceCategory);
        if (technician == null || technician.trim().isEmpty()) {
            technician = "Unassigned";
        }

        final String fServiceName = serviceName;
        final double fPrice = price;
        final String fDeviceInfo = fullDeviceInfo;
        final String fRequiredPart = requiredPart;
        final String fTechnician = technician;
        final String fBranchDisplay = branchDisplay;
        com.techfix.app.util.AppExecutors.run(() -> {
            long appointmentId = appointmentDAO.add(session.getUserId(), fDeviceInfo, problem, branch, fServiceName, fPrice, fTechnician, "");

            boolean partReserved = false;
            if (appointmentId > 0 && fRequiredPart != null && !fRequiredPart.isEmpty()) {
                partReserved = sparePartDAO.consume(fRequiredPart, branch);
            }

            if (selectedPhotoUri != null && appointmentId > 0) {
                appointmentDAO.setPhoto(appointmentId, selectedPhotoUri.toString());
            }

            FirebaseSyncManager.getInstance().sync(this, null);
            com.techfix.app.util.Analytics.log(this, "booking_created", "branch", branch);

            final boolean fPartReserved = partReserved;
            final long fAppointmentId = appointmentId;
            runOnUiThread(() -> {
                new AlertDialog.Builder(this)
                        .setTitle("Repair Booked!")
                        .setMessage("Appointment #" + fAppointmentId + " booked at " + fBranchDisplay + ".\nTechnician: " + ("Unassigned".equals(fTechnician) ? "will be assigned at the counter" : fTechnician) + "\nEstimated Price: Rs " + (long) fPrice
                                + (fPartReserved ? "\nPart reserved: " + fRequiredPart : ""))
                        .setPositiveButton("View Repairs", (dialog, which) -> {
                            binding.deviceModelInput.setText("");
                            binding.problemInput.setText("");
                            selectedPhotoUri = null;
                            binding.customerPhotoPreviewContainer.setVisibility(View.GONE);
                            binding.customerPhotoStatus.setText("No photo attached");

                            binding.customerBottomNavigation.setSelectedItemId(R.id.nav_customer_repairs);
                        })
                        .show();
            });
        });
    }

    // ==========================================
    // PROFILE & BRANCHES TAB METHODS
    // ==========================================
    private void setupBranchesList() {
        branchAdapter = new BranchAdapter();
        binding.branchList.setLayoutManager(new LinearLayoutManager(this));
        binding.branchList.setAdapter(branchAdapter);
        branchAdapter.submit(branchDAO.branches());
    }

    private void payFirstPending(View v) {
        List<Appointment> unpaid = new java.util.ArrayList<>();
        for (Appointment a : appointmentDAO.forUser(session.getUserId())) {
            if (PaymentStatus.PENDING.label.equalsIgnoreCase(a.payment)) {
                unpaid.add(a);
            }
        }

        if (unpaid.isEmpty()) {
            Feedback.error(v, "No unpaid repairs pending");
            return;
        }

        if (unpaid.size() == 1) {
            openPaymentDetail(unpaid.get(0).id);
            return;
        }

        String[] labels = new String[unpaid.size()];
        for (int i = 0; i < unpaid.size(); i++) {
            Appointment a = unpaid.get(i);
            labels[i] = "#" + a.id + " · " + a.device + " · Rs " + (long) a.price;
        }
        new AlertDialog.Builder(this)
                .setTitle("Choose a repair to pay")
                .setItems(labels, (d, which) -> openPaymentDetail(unpaid.get(which).id))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openPaymentDetail(long appointmentId) {
        Intent intent = new Intent(this, AppointmentDetailActivity.class);
        intent.putExtra("appointmentId", appointmentId);
        startActivity(intent);
    }
}
