package com.techfix.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.techfix.app.R;
import com.techfix.app.database.DatabaseHelper;
import com.techfix.app.database.ServiceDAO;
import com.techfix.app.database.SparePartDAO;
import com.techfix.app.databinding.ActivityHomeBinding;
import com.techfix.app.models.Service;
import com.techfix.app.models.SparePart;
import com.techfix.app.util.WindowInsetsHelper;

import java.util.List;

/**
 * HomeActivity - Landing Page for TechFix Store with Bottom Navigation.
 * TechFix is a newly established computer and mobile phone repair shop.
 * This screen displays the store showcase with images, available repair services,
 * in-stock spare parts, branch locations, and bottom navigation.
 */
public class HomeActivity extends AppCompatActivity {

    // View binding instance for activity_home.xml
    private ActivityHomeBinding binding;

    // Database access objects
    private ServiceDAO serviceDAO;
    private SparePartDAO sparePartDAO;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Inflate layout and apply insets
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.apply(binding.homeContent, binding.homeContent);

        // 2. Initialize Database DAOs
        DatabaseHelper dbHelper = DatabaseHelper.getInstance(this);
        serviceDAO = new ServiceDAO(dbHelper);
        sparePartDAO = new SparePartDAO(dbHelper);

        // 3. Setup Buttons & Bottom Navigation
        setupButtons();
        setupBottomNavigation();

        // 4. Load and display store items with images
        loadAvailableServices();
        loadAvailableSpareParts();
    }

    /**
     * Setup navigation button listeners.
     */
    private void setupButtons() {
        // "Get Started / Book a Repair" button -> opens Login / Sign-up screen
        binding.continueButton.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
            startActivity(intent);
        });

        // Colombo Branch -> Open location in Google Maps
        binding.colomboChip.setOnClickListener(v -> {
            openLocationInMaps("6.9271,79.8612", "TechFix Colombo Branch");
        });

        // Galle Branch -> Open location in Google Maps
        binding.galleChip.setOnClickListener(v -> {
            openLocationInMaps("6.0329,80.2168", "TechFix Galle Branch");
        });
    }

    /**
     * Setup bottom navigation bar (Store, Branches, Account).
     */
    private void setupBottomNavigation() {
        // Select 'Store' as default selected tab
        binding.bottomNavigation.setSelectedItemId(R.id.nav_home_store);

        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home_store) {
                // Scroll up to top to view repair store services & items
                binding.homeScrollView.smoothScrollTo(0, 0);
                return true;

            } else if (itemId == R.id.nav_home_branches) {
                // Scroll down to the branch locations section
                binding.homeScrollView.post(() -> {
                    int targetY = binding.branchesSectionTitle.getTop();
                    binding.homeScrollView.smoothScrollTo(0, targetY);
                });
                return true;

            } else if (itemId == R.id.nav_home_account) {
                // Open Customer & Staff login / account screen
                Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
                startActivity(intent);
                return false; // keep tab selected on home
            }

            return false;
        });
    }

    /**
     * Load all available computer and mobile phone repair services
     * from the database and add them to the landing page with images.
     */
    private void loadAvailableServices() {
        binding.servicesContainer.removeAllViews();

        List<Service> services = serviceDAO.list();

        for (Service service : services) {
            // Inflate service item layout
            View itemView = getLayoutInflater().inflate(R.layout.item_home_service, binding.servicesContainer, false);

            ImageView imageView = itemView.findViewById(R.id.serviceImageView);
            TextView nameText = itemView.findViewById(R.id.serviceNameText);
            TextView priceText = itemView.findViewById(R.id.servicePriceText);
            TextView categoryText = itemView.findViewById(R.id.serviceCategoryText);
            TextView partText = itemView.findViewById(R.id.servicePartText);

            // Set image according to service type
            imageView.setImageResource(getServiceImageResource(service.name, service.category));

            // Set service details
            nameText.setText(service.name);
            priceText.setText("Rs " + (long) service.price);
            categoryText.setText(service.category);

            if (service.requiredPart != null && !service.requiredPart.isEmpty()) {
                partText.setText("Includes part: " + service.requiredPart);
                partText.setVisibility(View.VISIBLE);
            } else {
                partText.setText("Full diagnostic & service");
                partText.setVisibility(View.VISIBLE);
            }

            // Click service item -> open login to book
            itemView.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
                startActivity(intent);
            });

            // Add the item view into the container
            binding.servicesContainer.addView(itemView);
        }
    }

    /**
     * Load all in-stock spare parts and store items
     * from the database and add them to the landing page with images.
     */
    private void loadAvailableSpareParts() {
        binding.partsContainer.removeAllViews();

        List<SparePart> parts = sparePartDAO.all();

        for (SparePart part : parts) {
            // Inflate spare part item layout
            View itemView = getLayoutInflater().inflate(R.layout.item_home_part, binding.partsContainer, false);

            ImageView imageView = itemView.findViewById(R.id.partImageView);
            TextView nameText = itemView.findViewById(R.id.partNameText);
            TextView statusBadge = itemView.findViewById(R.id.partStatusBadge);
            TextView quantityText = itemView.findViewById(R.id.partQuantityText);
            TextView branchText = itemView.findViewById(R.id.partBranchText);

            // Set product image
            imageView.setImageResource(getPartImageResource(part.name));

            // Set part details
            nameText.setText(part.name);
            quantityText.setText("Stock: " + part.quantity + " units available");
            branchText.setText(part.branch);

            // Show stock status
            if (part.quantity > 0) {
                statusBadge.setText("In Stock");
                statusBadge.setTextColor(getResources().getColor(R.color.success, null));
            } else {
                statusBadge.setText("Out of Stock");
                statusBadge.setTextColor(getResources().getColor(R.color.error, null));
            }

            // Add the item view into the container
            binding.partsContainer.addView(itemView);
        }
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
