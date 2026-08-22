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
import com.techfix.app.database.SparePartDAO;
import com.techfix.app.databinding.ActivityHomeBinding;
import com.techfix.app.models.SparePart;
import com.techfix.app.util.WindowInsetsHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * HomeActivity - Landing Page for TechFix Store.
 * TechFix is a newly established computer and mobile phone repair shop & device store.
 * Displays devices and items available for sale:
 * - Smartphones & Mobile Phones
 * - Laptops & Desktop Computers
 * - Batteries, Chargers & Accessories
 * - Spare Parts & Displays
 */
public class HomeActivity extends AppCompatActivity {

    // View binding instance for activity_home.xml
    private ActivityHomeBinding binding;

    // Database access object
    private SparePartDAO sparePartDAO;

    // Currently selected category filter: "ALL", "PHONES", "COMPUTERS", "SCREENS", "BATTERIES", "PARTS"
    private String currentCategory = "ALL";

    /**
     * Simple model representing a device or item for sale in the TechFix store.
     */
    public static class SaleItem {
        public final String name;
        public final String category; // "Phones", "Computers", "Screens", "Batteries", "Parts"
        public final double price;
        public final String specs;
        public final String branch;
        public final int imageRes;
        public final int stock;

        public SaleItem(String name, String category, double price, String specs, String branch, int imageRes, int stock) {
            this.name = name;
            this.category = category;
            this.price = price;
            this.specs = specs;
            this.branch = branch;
            this.imageRes = imageRes;
            this.stock = stock;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Inflate layout and apply insets
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.apply(binding.homeContent, binding.homeContent);

        // 2. Initialize Database DAO
        DatabaseHelper dbHelper = DatabaseHelper.getInstance(this);
        sparePartDAO = new SparePartDAO(dbHelper);

        // 3. Setup UI components
        setupBranchButtons();
        setupBottomNavigation();
        setupRoundCategories();

        // 4. Initial load of devices and items for sale (ALL)
        selectCategory("ALL");
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

        // Reload devices and spare parts according to the selected filter
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
     * Returns the list of devices (smartphones, laptops, computers, chargers) for sale.
     */
    private List<SaleItem> getDevicesForSale() {
        List<SaleItem> list = new ArrayList<>();

        // Smartphones for sale
        list.add(new SaleItem("Apple iPhone 13 (128GB)", "Phones", 165000, "Grade A Refurbished · Factory Unlocked", "Colombo branch", R.drawable.ic_store_phone_screen, 4));
        list.add(new SaleItem("Samsung Galaxy S22 5G (128GB)", "Phones", 142000, "Brand New Sealed · Official Warranty", "Galle branch", R.drawable.ic_store_phone_screen, 3));
        list.add(new SaleItem("Google Pixel 7 (128GB)", "Phones", 118000, "Factory Unlocked · Obsidian Black", "Colombo branch", R.drawable.ic_store_phone_screen, 5));
        list.add(new SaleItem("Xiaomi Redmi Note 12", "Phones", 49000, "Brand New · 8GB RAM / 128GB Storage", "Colombo branch", R.drawable.ic_store_phone_screen, 6));

        // Laptops & Computers for sale
        list.add(new SaleItem("Dell Latitude 5420 Laptop", "Computers", 135000, "Core i5 11th Gen · 16GB RAM · 512GB SSD", "Colombo branch", R.drawable.ic_store_laptop_diagnostics, 3));
        list.add(new SaleItem("Apple MacBook Air M1", "Computers", 215000, "8-Core CPU · 8GB RAM · 256GB SSD", "Colombo branch", R.drawable.ic_store_laptop_diagnostics, 2));
        list.add(new SaleItem("HP ProBook 450 G8 Laptop", "Computers", 128000, "Core i5 · 8GB RAM · 512GB NVMe SSD", "Galle branch", R.drawable.ic_store_laptop_diagnostics, 4));
        list.add(new SaleItem("Lenovo ThinkPad T14", "Computers", 148000, "Core i7 · 16GB RAM · 512GB SSD", "Galle branch", R.drawable.ic_store_laptop_diagnostics, 3));

        // Screens & Hardware Accessories
        list.add(new SaleItem("Original Phone Display Assembly", "Screens", 8500, "High Refresh Rate OLED Screen", "Colombo branch", R.drawable.ic_store_phone_screen, 8));
        list.add(new SaleItem("65W USB-C Fast Charger", "Batteries", 4500, "Universal Laptop & Smartphone Fast Adapter", "Colombo branch", R.drawable.ic_store_battery, 12));
        list.add(new SaleItem("Replacement Laptop Battery Pack", "Batteries", 7500, "Genuine OEM 4-Cell High Capacity", "Colombo branch", R.drawable.ic_store_laptop_battery, 4));
        list.add(new SaleItem("Original Smartphone Battery", "Batteries", 4500, "Extended Life Lithium-Ion Battery", "Galle branch", R.drawable.ic_store_battery, 5));
        list.add(new SaleItem("512GB NVMe High-Speed SSD", "Parts", 9500, "M.2 PCIe 3.0 · 3500MB/s Read Speed", "Galle branch", R.drawable.ic_store_hardware_part, 7));

        return list;
    }

    /**
     * Loads and filters devices and items for sale for the given category.
     */
    private void loadItemsForCategory(String filter) {
        loadAvailableDevices(filter);
        loadAvailableSpareParts(filter);
    }

    /**
     * Load devices for sale (phones, laptops, computers, etc.) with category filtering.
     */
    private void loadAvailableDevices(String filter) {
        binding.servicesContainer.removeAllViews();

        List<SaleItem> devices = getDevicesForSale();
        int count = 0;

        for (SaleItem item : devices) {
            // Check if device matches filter
            if (!matchesFilter(item.name, item.category, filter)) {
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

            imageView.setImageResource(item.imageRes);
            nameText.setText(item.name);
            priceText.setText("Rs " + String.format("%,d", (long) item.price));
            categoryText.setText(item.category + " • " + item.branch);
            partText.setText(item.specs);
            badgeText.setText("In Stock (" + item.stock + ")");

            // Click item -> open login / reservation flow
            itemView.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
                startActivity(intent);
            });

            binding.servicesContainer.addView(itemView);
        }

        binding.servicesHeaderTitle.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        binding.servicesHeaderSubtitle.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        binding.servicesContainer.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
    }

    /**
     * Load spare parts from database with filtering.
     */
    private void loadAvailableSpareParts(String filter) {
        binding.partsContainer.removeAllViews();

        if ("PHONES".equals(filter) || "COMPUTERS".equals(filter)) {
            // Hide separate spare parts container for specific phone/computer device filters
            binding.partsHeaderTitle.setVisibility(View.GONE);
            binding.partsHeaderSubtitle.setVisibility(View.GONE);
            binding.partsContainer.setVisibility(View.GONE);
            return;
        }

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

            itemView.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
                startActivity(intent);
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
                return combined.contains("phone") || combined.contains("iphone") || combined.contains("pixel") || combined.contains("samsung") || combined.contains("redmi");
            case "COMPUTERS":
                return combined.contains("laptop") || combined.contains("computer") || combined.contains("dell") || combined.contains("macbook") || combined.contains("probook") || combined.contains("thinkpad");
            case "SCREENS":
                return combined.contains("screen") || combined.contains("display");
            case "BATTERIES":
                return combined.contains("battery") || combined.contains("charger");
            case "PARTS":
                return combined.contains("display") || combined.contains("battery") || combined.contains("charger") || combined.contains("ssd") || combined.contains("part");
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
     * Setup bottom navigation bar (Store, Branches, Account).
     */
    private void setupBottomNavigation() {
        binding.bottomNavigation.setSelectedItemId(R.id.nav_home_store);

        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home_store) {
                binding.homeScrollView.smoothScrollTo(0, 0);
                return true;

            } else if (itemId == R.id.nav_home_branches) {
                binding.homeScrollView.post(() -> {
                    int targetY = binding.branchesSectionTitle.getTop();
                    binding.homeScrollView.smoothScrollTo(0, targetY);
                });
                return true;

            } else if (itemId == R.id.nav_home_account) {
                Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
                startActivity(intent);
                return false;
            }

            return false;
        });
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
        } else if (query.contains("battery") || query.contains("charger")) {
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
