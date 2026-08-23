package com.techfix.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

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

public class StaffActivity extends AppCompatActivity implements StaffTabHost {

    private ActivityStaffBinding binding;
    private SessionManager session;
    private String selectedBranch = "All Branches";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            goHome();
            return;
        }

        binding = ActivityStaffBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.apply(binding.staffHeader, binding.staffContent);

        com.techfix.app.sync.FirebaseSyncManager.getInstance().init(this);

        binding.staffHomeStoreButton.setOnClickListener(v -> {
            Intent intent = new Intent(StaffActivity.this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });
        binding.staffLogoutButton.setOnClickListener(v -> {
            session.logout();
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
            goHome();
        });
        setupBottomNavigation();

        if (savedInstanceState == null) {
            showPanel(0);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            goHome();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            goHome();
        }
    }

    private void goHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

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
        return selectedBranch;
    }

    @Override
    public void setSelectedBranch(String branch) {
        selectedBranch = branch;
    }
}
