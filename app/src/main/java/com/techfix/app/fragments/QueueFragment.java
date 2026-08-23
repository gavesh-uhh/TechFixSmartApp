package com.techfix.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.techfix.app.R;
import com.techfix.app.activities.AppointmentDetailActivity;
import com.techfix.app.adapters.StaffAppointmentAdapter;
import com.techfix.app.database.AppointmentDAO;
import com.techfix.app.database.DatabaseHelper;
import com.techfix.app.database.TechnicianDAO;
import com.techfix.app.databinding.FragmentQueueBinding;
import com.techfix.app.models.Appointment;
import com.techfix.app.models.AppointmentStatus;
import com.techfix.app.sync.FirebaseSyncManager;
import com.techfix.app.util.WindowInsetsHelper;

import java.util.ArrayList;
import java.util.List;

public class QueueFragment extends Fragment {

    private FragmentQueueBinding binding;
    private AppointmentDAO appointmentDAO;
    private TechnicianDAO technicianDAO;
    private StaffAppointmentAdapter queueAdapter;
    private String branchFilter = "All Branches";
    private String statusFilter = "All";
    private String sortOrder = "Newest First";

    private final FirebaseSyncManager.SyncListener syncListener = (isSyncing, success) -> {
        if (!isSyncing && success && isAdded() && binding != null) {
            requireActivity().runOnUiThread(this::refresh);
        }
    };

    private StaffTabHost host() {
        return (StaffTabHost) requireActivity();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentQueueBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        DatabaseHelper dbHelper = DatabaseHelper.getInstance(requireContext());
        appointmentDAO = new AppointmentDAO(dbHelper);
        technicianDAO = new TechnicianDAO(dbHelper);
        branchFilter = host().getSelectedBranch();

        WindowInsetsHelper.applyBottomInset(binding.tabQueue);

        FirebaseSyncManager.getInstance().addListener(syncListener);

        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_dropdown, AppointmentStatus.labels());
        statusAdapter.setDropDownViewResource(R.layout.item_dropdown_popup);
        binding.statusSpinner.setAdapter(statusAdapter);

        String[] sortOptions = {"Newest First", "Oldest First", "Price: High → Low", "Price: Low → High", "Status", "Device / Brand"};
        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_dropdown, sortOptions);
        sortAdapter.setDropDownViewResource(R.layout.item_dropdown_popup);
        binding.sortQueueSpinner.setAdapter(sortAdapter);
        binding.sortQueueSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                sortOrder = sortOptions[position];
                refresh();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        queueAdapter = new StaffAppointmentAdapter(this::showDocketActionMenu);
        binding.appointmentList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.appointmentList.setAdapter(queueAdapter);

        binding.searchQueueInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                refresh();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        setupFilterChips();

        binding.updateStatusButton.setOnClickListener(this::updateStatusById);

        refresh();
    }

    private void setupFilterChips() {
        binding.chipFilterAll.setOnClickListener(v -> selectFilterChip("All", v));
        binding.chipFilterActive.setOnClickListener(v -> selectFilterChip("Active", v));
        binding.chipFilterReceived.setOnClickListener(v -> selectFilterChip(AppointmentStatus.REQUEST_RECEIVED.label, v));
        binding.chipFilterRepairing.setOnClickListener(v -> selectFilterChip(AppointmentStatus.REPAIRING.label, v));
        binding.chipFilterReady.setOnClickListener(v -> selectFilterChip(AppointmentStatus.READY_FOR_COLLECTION.label, v));
        binding.chipFilterCompleted.setOnClickListener(v -> selectFilterChip(AppointmentStatus.COMPLETED.label, v));
        binding.chipFilterUnpaid.setOnClickListener(v -> selectFilterChip("Unpaid", v));
    }

    private void refresh() {
        String searchQuery = binding.searchQueueInput.getText().toString();
        List<Appointment> filtered = appointmentDAO.filter(branchFilter, statusFilter, searchQuery, sortOrder);

        queueAdapter.submit(filtered);
        binding.appointmentList.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        binding.emptyQueueContainer.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);

        int activeCount = appointmentDAO.countActive(branchFilter);
        int completedCount = appointmentDAO.countCompleted(branchFilter);
        int totalCount = appointmentDAO.countAll(branchFilter);

        binding.queueStatsTitle.setText(totalCount + (totalCount == 1 ? " Repair Docket" : " Repair Dockets"));
        binding.queueStatsSubtitle.setText(activeCount + " Active Repairs · " + completedCount + " Completed");
    }

    private void selectFilterChip(String status, View activeChip) {
        statusFilter = status;

        resetChipStyle(binding.chipFilterAll);
        resetChipStyle(binding.chipFilterActive);
        resetChipStyle(binding.chipFilterReceived);
        resetChipStyle(binding.chipFilterRepairing);
        resetChipStyle(binding.chipFilterReady);
        resetChipStyle(binding.chipFilterCompleted);
        resetChipStyle(binding.chipFilterUnpaid);

        if (activeChip instanceof TextView) {
            ((TextView) activeChip).setBackgroundResource(R.drawable.bg_filter_pill_selected);
            ((TextView) activeChip).setTextColor(requireContext().getColor(R.color.white));
        }

        refresh();
    }

    private void resetChipStyle(TextView chip) {
        chip.setBackgroundResource(R.drawable.bg_filter_pill);
        chip.setTextColor(requireContext().getColor(R.color.ink));
    }

    private void showDocketActionMenu(Appointment a) {
        String[] actions = {
                "Change Workflow Stage (" + a.status + ")",
                "Re-assign Technician (" + a.technician + ")",
                "Record Payment (Rs " + (long) a.price + ")",
                "View Full Docket & Timeline",
                "Delete Docket"
        };

        new AlertDialog.Builder(requireContext())
                .setTitle("Docket #" + a.id + " · " + a.device)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        showStatusPicker(a);
                    } else if (which == 1) {
                        showTechnicianPicker(a);
                    } else if (which == 2) {
                        showPaymentMethodPicker(a);
                    } else if (which == 3) {
                        Intent intent = new Intent(requireContext(), AppointmentDetailActivity.class);
                        intent.putExtra("appointmentId", a.id);
                        startActivity(intent);
                    } else if (which == 4) {
                        showDeleteConfirmation(a);
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showStatusPicker(Appointment a) {
        String[] labels = AppointmentStatus.labels();
        new AlertDialog.Builder(requireContext())
                .setTitle("Update Status · #" + a.id)
                .setItems(labels, (dialog, which) -> {
                    String newStatus = labels[which];
                    appointmentDAO.updateStatus(a.id, newStatus);
        com.techfix.app.util.Analytics.log(requireContext(), "status_updated", "status", newStatus);
                    FirebaseSyncManager.getInstance().sync(requireContext(), null);
                    refresh();
                    Snackbar.make(binding.getRoot(), "Docket #" + a.id + " updated to " + newStatus, Snackbar.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showTechnicianPicker(Appointment a) {
        List<com.techfix.app.models.Technician> available = new ArrayList<>();
        for (com.techfix.app.models.Technician t : technicianDAO.all()) {
            if (t.available) available.add(t);
        }
        available.sort((t1, t2) -> {
            boolean b1 = t1.branch != null && t1.branch.equals(a.branch);
            boolean b2 = t2.branch != null && t2.branch.equals(a.branch);
            return b1 == b2 ? t1.name.compareTo(t2.name) : (b1 ? -1 : 1);
        });

        if (available.isEmpty()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Assign Technician · #" + a.id)
                    .setMessage("No technicians are currently on duty. Toggle a technician's availability in the Catalog tab first.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        String[] techNames = new String[available.size()];
        for (int i = 0; i < available.size(); i++) {
            com.techfix.app.models.Technician t = available.get(i);
            boolean sameBranch = t.branch != null && t.branch.equals(a.branch);
            techNames[i] = t.name + " (" + t.branch + (sameBranch ? " · this branch" : "") + ")";
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Assign Technician · #" + a.id)
                .setItems(techNames, (dialog, which) -> {
                    String chosenTech = available.get(which).name;
                    appointmentDAO.updateTechnician(a.id, chosenTech);
                    FirebaseSyncManager.getInstance().sync(requireContext(), null);
                    refresh();
                    Snackbar.make(binding.getRoot(), "Assigned to " + chosenTech, Snackbar.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPaymentMethodPicker(Appointment a) {
        String[] methods = {"Cash at counter", "Card", "Bank transfer"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Record Payment · Rs " + (long) a.price)
                .setItems(methods, (dialog, which) -> {
                    boolean ok = appointmentDAO.pay(a.id, a.price, methods[which]);
                    FirebaseSyncManager.getInstance().sync(requireContext(), null);
                    refresh();
                    if (ok) {
                        Snackbar.make(binding.getRoot(), "Payment recorded (" + methods[which] + ")", Snackbar.LENGTH_LONG).show();
                    } else {
                        Snackbar.make(binding.getRoot(), "Already paid", Snackbar.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteConfirmation(Appointment a) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Docket #" + a.id + "?")
                .setMessage("Are you sure you want to remove this repair docket permanently?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    appointmentDAO.delete(a.id);
                    FirebaseSyncManager.getInstance().sync(requireContext(), null);
                    refresh();
                    Snackbar.make(binding.getRoot(), "Docket #" + a.id + " deleted", Snackbar.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateStatusById(View v) {
        String idStr = binding.appointmentIdInput.getText().toString().trim();
        if (idStr.isEmpty()) {
            binding.appointmentIdInput.setError("Please enter a docket ID");
            return;
        }

        try {
            long id = Long.parseLong(idStr);
            String status = (String) binding.statusSpinner.getSelectedItem();
            appointmentDAO.updateStatus(id, status);
            FirebaseSyncManager.getInstance().sync(requireContext(), null);
            binding.appointmentIdInput.setText("");
            refresh();
            Snackbar.make(v, "Docket #" + id + " updated to " + status, Snackbar.LENGTH_LONG).show();
        } catch (Exception e) {
            binding.appointmentIdInput.setError("Invalid docket ID");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        FirebaseSyncManager.getInstance().removeListener(syncListener);
        binding = null;
    }
}
