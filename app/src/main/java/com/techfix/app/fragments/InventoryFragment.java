package com.techfix.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.techfix.app.R;
import com.techfix.app.adapters.SparePartAdapter;
import com.techfix.app.database.DatabaseHelper;
import com.techfix.app.database.SparePartDAO;
import com.techfix.app.databinding.FragmentInventoryBinding;
import com.techfix.app.models.SparePart;
import com.techfix.app.sync.FirebaseSyncManager;
import com.techfix.app.util.WindowInsetsHelper;

import java.util.List;

/**
 * TAB 3: Inventory & Spare Parts (stock badges, 1-tap stock adjustments, part creation).
 */
public class InventoryFragment extends Fragment {

    private FragmentInventoryBinding binding;
    private SparePartDAO sparePartDAO;
    private SparePartAdapter sparePartAdapter;
    private final FirebaseSyncManager.SyncListener syncListener = (isSyncing, success) -> {
        if (!isSyncing && success && isAdded() && binding != null) {
            requireActivity().runOnUiThread(this::refresh);
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentInventoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        DatabaseHelper dbHelper = DatabaseHelper.getInstance(requireContext());
        sparePartDAO = new SparePartDAO(dbHelper);

        // Bottom inset so content clears the gesture nav bar / keyboard
        WindowInsetsHelper.applyBottomInset(binding.tabInventory);

        FirebaseSyncManager.getInstance().addListener(syncListener);

        String[] branches = {"All Branches", "Colombo branch", "Galle branch"};
        ArrayAdapter<String> branchAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_dropdown, branches);
        binding.inventoryBranchSpinner.setAdapter(branchAdapter);

        binding.inventoryBranchSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refresh();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        sparePartAdapter = new SparePartAdapter(new SparePartAdapter.OnPartActionListener() {
            @Override
            public void onAdjustQuantity(SparePart part, int delta) {
                sparePartDAO.adjustStock(part.id, delta);
                FirebaseSyncManager.getInstance().sync(requireContext(), null);
                refresh();
            }

            @Override
            public void onDelete(SparePart part) {
                new AlertDialog.Builder(requireContext())
                        .setTitle("Delete " + part.name + "?")
                        .setMessage("Remove this spare part item from " + part.branch + " inventory?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            sparePartDAO.delete(part.id);
                            FirebaseSyncManager.getInstance().sync(requireContext(), null);
                            refresh();
                            Snackbar.make(binding.getRoot(), "Part deleted", Snackbar.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        binding.partsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.partsList.setAdapter(sparePartAdapter);

        // Add part button opens popup dialog
        binding.btnAddPartButton.setOnClickListener(v -> showAddPartDialog());

        refresh();
    }

    /**
     * Opens the popup dialog for adding/restocking a workshop part.
     */
    private void showAddPartDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_part, null);
        EditText inputName = dialogView.findViewById(R.id.dialogPartNameInput);
        Spinner spinnerBranch = dialogView.findViewById(R.id.dialogPartBranchSpinner);
        EditText inputQty = dialogView.findViewById(R.id.dialogPartQtyInput);

        String[] branchOptions = {"Colombo branch", "Galle branch"};
        ArrayAdapter<String> branchAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_dropdown, branchOptions);
        spinnerBranch.setAdapter(branchAdapter);

        // Pre-select branch based on current active branch filter if applicable
        String currentFilter = (String) binding.inventoryBranchSpinner.getSelectedItem();
        if ("Galle branch".equalsIgnoreCase(currentFilter)) {
            spinnerBranch.setSelection(1);
        } else {
            spinnerBranch.setSelection(0);
        }

        new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton("Save Part to Inventory", (dialog, which) -> {
                    String name = inputName.getText().toString().trim();
                    String branch = (String) spinnerBranch.getSelectedItem();
                    String qtyStr = inputQty.getText().toString().trim();

                    if (name.isEmpty()) {
                        Toast.makeText(requireContext(), "Please enter a part name", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int qty = 1;
                    try {
                        if (!qtyStr.isEmpty()) qty = Integer.parseInt(qtyStr);
                    } catch (Exception ignored) {}

                    sparePartDAO.add(name, branch != null ? branch : "Colombo branch", qty);
                    FirebaseSyncManager.getInstance().sync(requireContext(), null);
                    refresh();
                    Snackbar.make(binding.getRoot(), "Saved " + name + " to " + branch + " inventory", Snackbar.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void refresh() {
        String branch = (String) binding.inventoryBranchSpinner.getSelectedItem();
        List<SparePart> parts = sparePartDAO.allByBranch(branch);

        sparePartAdapter.submit(parts);
        binding.partsList.setVisibility(parts.isEmpty() ? View.GONE : View.VISIBLE);
        binding.emptyPartsContainer.setVisibility(parts.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
