package com.techfix.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.techfix.app.R;
import com.techfix.app.activities.AppointmentDetailActivity;
import com.techfix.app.adapters.AppointmentAdapter;
import com.techfix.app.database.AppointmentDAO;
import com.techfix.app.database.DatabaseHelper;
import com.techfix.app.database.ServiceDAO;
import com.techfix.app.database.SparePartDAO;
import com.techfix.app.database.TechnicianDAO;
import com.techfix.app.databinding.DialogWalkinDocketBinding;
import com.techfix.app.databinding.FragmentOverviewBinding;
import com.techfix.app.models.Appointment;
import com.techfix.app.models.Technician;
import com.techfix.app.sync.FirebaseSyncManager;
import com.techfix.app.util.AppExecutors;
import com.techfix.app.util.WindowInsetsHelper;

import java.util.List;

public class OverviewFragment extends Fragment {

    private FragmentOverviewBinding binding;
    private AppointmentDAO appointmentDAO;
    private SparePartDAO sparePartDAO;
    private TechnicianDAO technicianDAO;
    private ServiceDAO serviceDAO;
    private AppointmentAdapter recentAdapter;
    private String selectedBranch = "All Branches";
    private int refreshGeneration = 0;

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
        binding = FragmentOverviewBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        DatabaseHelper dbHelper = DatabaseHelper.getInstance(requireContext());
        appointmentDAO = new AppointmentDAO(dbHelper);
        sparePartDAO = new SparePartDAO(dbHelper);
        technicianDAO = new TechnicianDAO(dbHelper);
        serviceDAO = new ServiceDAO(dbHelper);

        WindowInsetsHelper.applyBottomInset(binding.tabOverview);

        FirebaseSyncManager.getInstance().addListener(syncListener);

        selectedBranch = host().getSelectedBranch();

        String[] branches = new com.techfix.app.database.BranchDAO(dbHelper).filterNamesArray();
        ArrayAdapter<String> branchAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_dropdown, branches);
        branchAdapter.setDropDownViewResource(R.layout.item_dropdown_popup);
        binding.overviewBranchSpinner.setAdapter(branchAdapter);
        for (int i = 0; i < branches.length; i++) {
            if (com.techfix.app.database.BranchDAO.toDbName(branches[i]).equalsIgnoreCase(selectedBranch)) {
                binding.overviewBranchSpinner.setSelection(i);
            }
        }
        binding.overviewBranchSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedBranch = com.techfix.app.database.BranchDAO.toDbName(branches[position]);
                host().setSelectedBranch(selectedBranch);
                refresh();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        binding.btnRefreshOverview.setOnClickListener(v -> {
            refresh();
            com.google.android.material.snackbar.Snackbar.make(binding.getRoot(), "Analytics refreshed", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
        });
        binding.btnQuickWalkIn.setOnClickListener(v -> showWalkInDocketDialog());
        binding.btnQuickRestock.setOnClickListener(v -> host().switchToTab(2));
        binding.btnQuickCatalog.setOnClickListener(v -> host().switchToTab(3));

        setupRecentDockets();
        refresh();
    }

    private void setupRecentDockets() {
        recentAdapter = new AppointmentAdapter(appointment -> {
            Intent intent = new Intent(requireContext(), AppointmentDetailActivity.class);
            intent.putExtra("appointmentId", appointment.id);
            startActivity(intent);
        });
        binding.overviewRecentDocketsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.overviewRecentDocketsList.setAdapter(recentAdapter);
    }

    private void refresh() {
        final String branch = selectedBranch;
        final int generation = ++refreshGeneration;

        AppExecutors.run(() -> {
            int totalDockets = appointmentDAO.countAll(branch);
            int activeDockets = appointmentDAO.countActive(branch);
            int completedDockets = appointmentDAO.countCompleted(branch);
            double paidRevenue = appointmentDAO.sumPaidRevenue(branch);
            double pendingRevenue = appointmentDAO.sumPendingRevenue(branch);
            int lowStockCount = sparePartDAO.getLowStockCount(2);

            List<Technician> techList = technicianDAO.allByBranch(branch);
            int onDuty = 0;
            for (Technician t : techList) {
                if (t.available) onDuty++;
            }
            final int availableTechs = onDuty;

            List<Appointment> recent = appointmentDAO.all();
            int limit = Math.min(5, recent.size());

            if (!isAdded() || binding == null || generation != refreshGeneration) return;
            List<Appointment> recentSlice = new java.util.ArrayList<>(recent.subList(0, limit));
            requireActivity().runOnUiThread(() -> {
                if (!isAdded() || binding == null || generation != refreshGeneration) return;

                binding.kpiTotalDockets.setText(String.valueOf(totalDockets));
                binding.kpiTotalDocketsSub.setText(completedDockets + " completed · " + activeDockets + " in progress");

                binding.kpiActiveDockets.setText(String.valueOf(activeDockets));
                binding.kpiActiveDocketsSub.setText("Awaiting collection & repair");

                binding.kpiRevenueCollected.setText("Rs " + String.format("%,.0f", paidRevenue));
                if (pendingRevenue > 0) {
                    binding.kpiRevenuePending.setText("Rs " + String.format("%,.0f", pendingRevenue) + " pending");
                    binding.kpiRevenuePending.setTextColor(requireContext().getColor(R.color.warning));
                } else {
                    binding.kpiRevenuePending.setText("All invoices settled");
                    binding.kpiRevenuePending.setTextColor(requireContext().getColor(R.color.muted_text));
                }

                if (lowStockCount > 0) {
                    binding.kpiLowStockAlert.setText(lowStockCount + " Parts Low in Stock!");
                    binding.kpiLowStockAlert.setTextColor(requireContext().getColor(R.color.warning));
                } else {
                    binding.kpiLowStockAlert.setText("Inventory: All parts in stock");
                    binding.kpiLowStockAlert.setTextColor(requireContext().getColor(R.color.ink));
                }

                binding.kpiTechDutyStatus.setText(availableTechs + "/" + techList.size() + " Techs On Duty");

                recentAdapter.submitList(recentSlice);
                boolean hasRecent = !recentSlice.isEmpty();
                binding.overviewRecentDocketsList.setVisibility(hasRecent ? View.VISIBLE : View.GONE);
                binding.overviewRecentEmpty.setVisibility(hasRecent ? View.GONE : View.VISIBLE);
            });
        });
    }

    private void showWalkInDocketDialog() {
        DialogWalkinDocketBinding form = DialogWalkinDocketBinding.inflate(getLayoutInflater());

        String[] branchOptions = new com.techfix.app.database.BranchDAO(
                DatabaseHelper.getInstance(requireContext())).displayNamesArray();
        ArrayAdapter<String> branchAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_dropdown, branchOptions);
        branchAdapter.setDropDownViewResource(R.layout.item_dropdown_popup);
        form.walkinBranchSpinner.setAdapter(branchAdapter);

        List<String> serviceNames = serviceDAO.all();
        ArrayAdapter<String> serviceAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_dropdown, serviceNames);
        serviceAdapter.setDropDownViewResource(R.layout.item_dropdown_popup);
        form.walkinServiceSpinner.setAdapter(serviceAdapter);

        new AlertDialog.Builder(requireContext())
                .setTitle("New Walk-in Repair Docket")
                .setView(form.getRoot())
                .setPositiveButton("Create Docket", (dialog, which) -> {
                    String customer = form.walkinCustomerInput.getText().toString().trim();
                    String device = form.walkinDeviceInput.getText().toString().trim();
                    String problem = form.walkinProblemInput.getText().toString().trim();
                    String branch = com.techfix.app.database.BranchDAO.toDbName((String) form.walkinBranchSpinner.getSelectedItem());
                    String selectedService = (String) form.walkinServiceSpinner.getSelectedItem();

                    if (device.isEmpty() || problem.isEmpty() || selectedService == null) {
                        Toast.makeText(requireContext(), "Please fill in device, issue, and service", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String sName = serviceDAO.serviceName(selectedService);
                    double sPrice = serviceDAO.price(selectedService);
                    String tech = technicianDAO.availableFor(branch, device);

                    AppExecutors.run(() -> {
                        long newId = appointmentDAO.add(0, device,
                                problem + (customer.isEmpty() ? "" : " (Walk-in: " + customer + ")"),
                                branch, sName, sPrice, tech, "Walk-in Counter");
                        if (!isAdded()) return;
                        FirebaseSyncManager.getInstance().sync(requireContext(), null);
                        requireActivity().runOnUiThread(() -> {
                            if (!isAdded() || binding == null) return;
                            refresh();
                            Snackbar.make(binding.getRoot(), "Walk-in Docket #" + newId + " created successfully!", Snackbar.LENGTH_LONG).show();
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        FirebaseSyncManager.getInstance().removeListener(syncListener);
        binding = null;
    }
}
