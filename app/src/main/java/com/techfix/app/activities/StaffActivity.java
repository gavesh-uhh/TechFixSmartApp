package com.techfix.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.techfix.app.R;
import com.techfix.app.databinding.ActivityStaffBinding;
import com.techfix.app.fragments.AdminFragment;
import com.techfix.app.fragments.CatalogFragment;
import com.techfix.app.fragments.InventoryFragment;
import com.techfix.app.fragments.OverviewFragment;
import com.techfix.app.fragments.QueueFragment;
import com.techfix.app.fragments.StaffTabHost;
import com.techfix.app.session.SessionManager;
import com.techfix.app.util.WindowInsetsHelper;

/**
 * StaffActivity - Executive Workshop & Admin Dashboard host.
 * Keeps only the header (title/badge/email/Store), BottomNavigationView and the
 * FragmentContainerView hosting one tab fragment at a time:
 * 1. OverviewFragment  - Overview & Financial KPIs
 * 2. QueueFragment     - Repair Queue & Docket Master
 * 3. InventoryFragment - Inventory & Spare Parts
 * 4. CatalogFragment   - Catalog & Staff Roster
 * 5. AdminFragment     - Admin & Customer Directory (+ Log Out of Account)
 */
public class StaffActivity extends AppCompatActivity implements StaffTabHost {

    private ActivityStaffBinding binding;
    private SessionManager session;
    private String selectedBranch = "All Branches";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Session Verification
        session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            goHome();
            return;
        }

        // 2. View Binding & Insets
        binding = ActivityStaffBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // Top insets handled here; the BottomNavigationView (staffBottomNavigation) applies its
        // own navigationBars inset via Material, so we don't pad it again (avoids double inset).
        WindowInsetsHelper.apply(binding.staffHeader, binding.staffContent);

        // 3. Header & Navigation
        binding.staffHomeStoreButton.setOnClickListener(v ->
                startActivity(new Intent(StaffActivity.this, HomeActivity.class)));
        binding.staffLogoutButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Log Out")
                    .setMessage("Are you sure you want to log out of the admin workspace?")
                    .setPositiveButton("Log Out", (dialog, which) -> {
                        session.logout();
                        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
                        goHome();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        setupBottomNavigation();

        if (savedInstanceState == null) {
            showPanel(0);
        }
    }

    private void goHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    /**
     * Setup 5-tab bottom navigation (Overview, Queue, Inventory, Catalog, Admin).
     */
    private void setupBottomNavigation() {
        binding.staffBottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_staff_overview) {
                showPanel(0);
                return true;
            } else if (itemId == R.id.nav_staff_queue) {
                showPanel(1);
                return true;
            } else if (itemId == R.id.nav_staff_inventory) {
                showPanel(2);
                return true;
            } else if (itemId == R.id.nav_staff_catalog) {
                showPanel(3);
                return true;
            } else if (itemId == R.id.nav_staff_profile) {
                showPanel(4);
                return true;
            }

            return false;
        });

        binding.staffBottomNavigation.setSelectedItemId(R.id.nav_staff_overview);
    }

    private void showPanel(int position) {
        Fragment fragment;
        switch (position) {
            case 1:  fragment = new QueueFragment(); break;
            case 2:  fragment = new InventoryFragment(); break;
            case 3:  fragment = new CatalogFragment(); break;
            case 4:  fragment = new AdminFragment(); break;
            default: fragment = new OverviewFragment(); break;
        }

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.staffContent, fragment)
                .commit();
    }

    // =========================================================================
    // StaffTabHost implementation (fragment -> activity bridge)
    // =========================================================================

    @Override
    public void setHeaderBadge(String text) {
        // Badge removed from top header bar
    }

    @Override
    public void switchToTab(int position) {
        int navId;
        switch (position) {
            case 1:  navId = R.id.nav_staff_queue; break;
            case 2:  navId = R.id.nav_staff_inventory; break;
            case 3:  navId = R.id.nav_staff_catalog; break;
            case 4:  navId = R.id.nav_staff_profile; break;
            default: navId = R.id.nav_staff_overview; break;
        }
        binding.staffBottomNavigation.setSelectedItemId(navId);
    }

    @Override
    public String getSelectedBranch() {
        // Kept across tab switches so Queue filters follow the Overview branch picker.
        return selectedBranch;
    }

    @Override
    public void setSelectedBranch(String branch) {
        selectedBranch = branch;
    }
}
