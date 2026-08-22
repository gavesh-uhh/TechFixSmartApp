package com.techfix.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.techfix.app.R;
import com.techfix.app.database.AppointmentDAO;
import com.techfix.app.database.DatabaseHelper;
import com.techfix.app.database.ServiceDAO;
import com.techfix.app.database.SparePartDAO;
import com.techfix.app.database.TechnicianDAO;
import com.techfix.app.databinding.ActivityHomeBinding;
import com.techfix.app.models.Service;
import com.techfix.app.models.SparePart;
import com.techfix.app.session.SessionManager;
import com.techfix.app.util.Feedback;
import com.techfix.app.util.WindowInsetsHelper;

import java.util.List;

/**
 * HomeActivity - Landing Page & Booking for TechFix Repair Shop.
 * TechFix is a newly established computer and mobile phone repair shop.
 * Features:
 * - Round category quick-filters (All, Phones, Computers, Screens, Batteries, Parts)
 * - Available repair services catalog with pricing in LKR
 * - In-stock replacement spare parts & components
 * - Bottom navigation: Store, Book Appointment, Branches, Account
 * - Complete Book Appointment form with service type, device info, photo, and description
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

    // Currently selected category filter: "ALL", "PHONES", "COMPUTERS", "SCREENS", "BATTERIES", "PARTS"
    private String currentCategory = "ALL";

    // Attached device photo URI
    private Uri selectedPhotoUri = null;

    // Activity Result Launcher to pick/attach a photo of the broken device
    private final ActivityResultLauncher<String> photoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), (Uri uri) -> {
                if (uri != null) {
                    selectedPhotoUri = uri;
                    binding.bookingPhotoPreview.setImageURI(uri);
                    binding.photoPreviewContainer.setVisibility(View.VISIBLE);
                    binding.photoStatusText.setText("Photo attached");
                    binding.photoStatusText.setTextColor(getResources().getColor(R.color.success, null));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Inflate layout and apply insets
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.apply(binding.homeContent, binding.homeContent);

        // 2. Initialize Database DAOs and Session
        dbHelper = DatabaseHelper.getInstance(this);
        serviceDAO = new ServiceDAO(dbHelper);
        sparePartDAO = new SparePartDAO(dbHelper);
        appointmentDAO = new AppointmentDAO(dbHelper);
        session = new SessionManager(this);

        // 3. Setup UI components
        setupBranchButtons();
        setupBottomNavigation();
        setupRoundCategories();
        setupBookingForm();

        // 4. Initial load of all available repair services and spare parts
        selectCategory("ALL");
    }

    /**
     * Setup bottom navigation bar (Store, Book, Branches, Account).
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
                // Open Customer & Staff login / account screen
                Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
                startActivity(intent);
                return false;
            }

            return false;
        });
    }

    /**
     * Shows the Store Catalog panel and hides the Booking panel.
     */
    private void showStorePanel() {
        binding.storePanel.setVisibility(View.VISIBLE);
        binding.bookAppointmentPanel.setVisibility(View.GONE);
        binding.topBarTitle.setText("TechFix Store");
        binding.topBarSubtitle.setText("Computer & Mobile Phone Repairs");
    }

    /**
     * Shows the Book Appointment panel and hides the Store Catalog panel.
     */
    private void showBookAppointmentPanel() {
        binding.storePanel.setVisibility(View.GONE);
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
        binding.bookingServiceSpinner.setAdapter(serviceAdapter);

        // 2. Setup Device Category Dropdown
        String[] deviceCategories = {"Mobile phone", "Laptop / computer", "Tablet", "Other smart device"};
        ArrayAdapter<String> deviceAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, deviceCategories);
        binding.bookingDeviceSpinner.setAdapter(deviceAdapter);

        // 3. Setup Branch Dropdown
        String[] branches = {"Colombo branch", "Galle branch"};
        ArrayAdapter<String> branchAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, branches);
        binding.bookingBranchSpinner.setAdapter(branchAdapter);

        // 4. Setup Attach Photo Button
        binding.bookingPhotoButton.setOnClickListener(v -> {
            photoPickerLauncher.launch("image/*");
        });

        // 5. Setup Remove Photo Button
        binding.removePhotoButton.setOnClickListener(v -> {
            selectedPhotoUri = null;
            binding.bookingPhotoPreview.setImageURI(null);
            binding.photoPreviewContainer.setVisibility(View.GONE);
            binding.photoStatusText.setText("No photo attached");
            binding.photoStatusText.setTextColor(getResources().getColor(R.color.muted_text, null));
        });

        // 6. Setup Submit Booking Button
        binding.submitBookingButton.setOnClickListener(v -> submitAppointmentBooking());
    }

    /**
     * Validates and submits the appointment booking to the SQLite database.
     */
    private void submitAppointmentBooking() {
        String serviceSelection = (String) binding.bookingServiceSpinner.getSelectedItem();
        String deviceCategory = (String) binding.bookingDeviceSpinner.getSelectedItem();
        String deviceModel = binding.bookingModelInput.getText().toString().trim();
        String problemDescription = binding.bookingProblemInput.getText().toString().trim();
        String branch = (String) binding.bookingBranchSpinner.getSelectedItem();

        // Validation
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

        // Parse service name and price
        String serviceName = serviceDAO.serviceName(serviceSelection != null ? serviceSelection : "Repair Service");
        double price = serviceDAO.price(serviceSelection != null ? serviceSelection : "0");

        // Combine device info: e.g. "Mobile phone (iPhone 13 Pro)"
        String fullDeviceInfo = deviceCategory + " (" + deviceModel + ")";

        // Get user ID (or default customer ID 1)
        long userId = session.isLoggedIn() ? session.getUserId() : 1;

        // Auto-assign available technician
        String technician = new TechnicianDAO(dbHelper).availableFor(branch, deviceCategory);

        // Insert into SQLite database
        long appointmentId = appointmentDAO.add(userId, fullDeviceInfo, problemDescription, branch, serviceName, price, technician, "");

        // Save attached photo URI if provided
        if (selectedPhotoUri != null && appointmentId > 0) {
            appointmentDAO.setPhoto(appointmentId, selectedPhotoUri.toString());
        }

        // Show Success Dialog
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
        binding.catParts.setOnClickListener(v -> selectCategory("PARTS"));
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
        } else if ("PARTS".equals(categoryKey)) {
            highlightCategory(binding.circleCatParts, binding.iconCatParts, binding.labelCatParts);
        }

        // Reload repair services and spare parts according to the selected filter
        loadItemsForCategory(categoryKey);
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

        // Category: Parts
        binding.circleCatParts.setBackgroundResource(unselectedBg);
        binding.iconCatParts.setColorFilter(navyColor);
        binding.labelCatParts.setTextColor(mutedColor);
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
     * Loads and filters repair services and spare parts for the given category.
     */
    private void loadItemsForCategory(String filter) {
        loadAvailableServices(filter);
        loadAvailableSpareParts(filter);
    }

    /**
     * Load computer and mobile phone repair services from the SQLite database with category filtering.
     */
    private void loadAvailableServices(String filter) {
        binding.servicesContainer.removeAllViews();

        if ("PARTS".equals(filter)) {
            binding.servicesHeaderTitle.setVisibility(View.GONE);
            binding.servicesHeaderSubtitle.setVisibility(View.GONE);
            binding.servicesContainer.setVisibility(View.GONE);
            return;
        }

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

            // Click service -> open Book Appointment panel directly
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
     * Load in-stock replacement spare parts from the SQLite database with category filtering.
     */
    private void loadAvailableSpareParts(String filter) {
        binding.partsContainer.removeAllViews();

        List<SparePart> parts = sparePartDAO.all();
        int count = 0;

        for (SparePart part : parts) {
            if (!matchesFilter(part.name, part.branch, filter)) {
                continue;
            }

            count++;
            View itemView = getLayoutInflater().inflate(R.layout.item_home_part, binding.partsContainer, false);

            ImageView imageView = itemView.findViewById(R.id.partImageView);
            TextView nameText = itemView.findViewById(R.id.partNameText);
            TextView statusBadge = itemView.findViewById(R.id.partStatusBadge);
            TextView quantityText = itemView.findViewById(R.id.partQuantityText);
            TextView branchText = itemView.findViewById(R.id.partBranchText);

            imageView.setImageResource(getPartImageResource(part.name));
            nameText.setText(part.name);
            quantityText.setText("Stock: " + part.quantity + " units available");
            branchText.setText(part.branch);

            if (part.quantity > 0) {
                statusBadge.setText("In Stock");
                statusBadge.setTextColor(getResources().getColor(R.color.success, null));
            } else {
                statusBadge.setText("Out of Stock");
                statusBadge.setTextColor(getResources().getColor(R.color.error, null));
            }

            // Click part -> open Book Appointment panel
            itemView.setOnClickListener(v -> {
                binding.bottomNavigation.setSelectedItemId(R.id.nav_home_book);
            });

            binding.partsContainer.addView(itemView);
        }

        binding.partsHeaderTitle.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        binding.partsHeaderSubtitle.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        binding.partsContainer.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
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
            case "PARTS":
                return true;
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
            return R.drawable.ic_store_hardware_part;
        }
    }

    /**
     * Helper method to choose an image for a spare part item.
     */
    private int getPartImageResource(String partName) {
        String query = partName.toLowerCase();
        if (query.contains("display") || query.contains("screen")) {
            return R.drawable.ic_store_phone_screen;
        } else if (query.contains("laptop") && query.contains("battery")) {
            return R.drawable.ic_store_laptop_battery;
        } else if (query.contains("battery")) {
            return R.drawable.ic_store_battery;
        } else {
            return R.drawable.ic_store_hardware_part;
        }
    }

    /**
     * Helper method to open geographical coordinates in Google Maps app.
     */
    private void openLocationInMaps(String coordinates, String label) {
        Uri mapUri = Uri.parse("geo:" + coordinates + "?q=" + Uri.encode(label));
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, mapUri);
        startActivity(mapIntent);
    }
}
