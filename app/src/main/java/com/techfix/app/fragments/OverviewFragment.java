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

import com.google.android.material.snackbar.Snackbar;
import com.techfix.app.R;
import com.techfix.app.database.AppointmentDAO;
import com.techfix.app.database.DatabaseHelper;
import com.techfix.app.database.ServiceDAO;
import com.techfix.app.database.SparePartDAO;
import com.techfix.app.database.TechnicianDAO;
import com.techfix.app.databinding.FragmentOverviewBinding;
import com.techfix.app.models.Technician;
import com.techfix.app.sync.FirebaseSyncManager;
import com.techfix.app.util.WindowInsetsHelper;

import java.util.List;

/**
 * TAB 1: Overview & Financial KPIs (branch filter, KPI cards, quick counter actions).
 */
public class OverviewFragment extends Fragment {

    private FragmentOverviewBinding binding;
    private AppointmentDAO appointmentDAO;
    private SparePartDAO sparePartDAO;
    private TechnicianDAO technicianDAO;
    private ServiceDAO serviceDAO;
    private String selectedBranch = "All Branches";

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

        // Bottom inset so content clears the gesture nav bar / keyboard
        WindowInsetsHelper.applyBottomInset(binding.tabOverview);

        FirebaseSyncManager.getInstance().addListener(syncListener);

        selectedBranch = host().getSelectedBranch();

        String[] branches = new com.techfix.app.database.BranchDAO(dbHelper).namesArrayWithAll();
        ArrayAdapter<String> branchAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_dropdown, branches);
        branchAdapter.setDropDownViewResource(R.layout.item_dropdown_popup);
        binding.overviewBranchSpinner.setAdapter(branchAdapter);
        for (int i = 0; i < branches.length; i++) {
            if (branches[i].equals(selectedBranch)) binding.overviewBranchSpinner.setSelection(i);
        }
        binding.overviewBranchSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedBranch = branches[position];
                host().setSelectedBranch(selectedBranch);
                host().setHeaderBadge(selectedBranch.toUpperCase());
                refresh();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Quick Actions
        binding.btnQuickWalkIn.setOnClickListener(v -> showWalkInDocketDialog());
        binding.btnQuickRestock.setOnClickListener(v -> host().switchToTab(2));
        binding.btnQuickAddService.setOnClickListener(v -> host().switchToTab(3));
        binding.btnQuickAddTech.setOnClickListener(v -> host().switchToTab(3));

        refresh();
    }

    private void refresh() {
        int totalDockets = appointmentDAO.countAll(selectedBranch);
        int activeDockets = appointmentDAO.countActive(selectedBranch);
        int completedDockets = appointmentDAO.countCompleted(selectedBranch);
        double paidRevenue = appointmentDAO.sumPaidRevenue(selectedBranch);
        double pendingRevenue = appointmentDAO.sumPendingRevenue(selectedBranch);
        int lowStockCount = sparePartDAO.getLowStockCount(2);

        binding.kpiTotalDockets.setText(String.valueOf(totalDockets));
        binding.kpiTotalDocketsSub.setText(completedDockets + " completed · " + activeDockets + " in progress");

        binding.kpiActiveDockets.setText(String.valueOf(activeDockets));
        binding.kpiActiveDocketsSub.setText("Awaiting collection & repair");

        binding.kpiRevenueCollected.setText("Rs " + String.format("%,.0f", paidRevenue));
        binding.kpiRevenuePending.setText("Rs " + String.format("%,.0f", pendingRevenue));

        if (lowStockCount > 0) {
            binding.kpiLowStockAlert.setText(lowStockCount + " Parts Low in Stock!");
            binding.kpiLowStockAlert.setTextColor(requireContext().getColor(R.color.warning));
        } else {
            binding.kpiLowStockAlert.setText("Inventory: All parts in stock");
            binding.kpiLowStockAlert.setTextColor(requireContext().getColor(R.color.navy_900));
        }

        List<Technician> techList = technicianDAO.allByBranch(selectedBranch);
        int availableTechs = 0;
        for (Technician t : techList) {
            if (t.available) availableTechs++;
        }
        binding.kpiTechDutyStatus.setText(availableTechs + "/" + techList.size() + " Techs On Duty");
    }

    /** Dialog to create a walk-in repair order right at the counter. */
    private void showWalkInDocketDialog() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = (int) (18 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        final EditText inputCustomer = new EditText(requireContext());
        inputCustomer.setHint("Customer Name (e.g. Ruwan Silva)");
        layout.addView(inputCustomer);

        final EditText inputDevice = new EditText(requireContext());
        inputDevice.setHint("Device (e.g. iPhone 14 Pro, ThinkPad X1)");
        layout.addView(inputDevice);

        final EditText inputProblem = new EditText(requireContext());
        inputProblem.setHint("Problem Description (e.g. Cracked screen, battery drain)");
        layout.addView(inputProblem);

        final Spinner spinnerBranch = new Spinner(requireContext());
        spinnerBranch.setBackgroundResource(R.drawable.bg_spinner);
        String[] branchOptions = new com.techfix.app.database.BranchDAO(DatabaseHelper.getInstance(requireContext())).namesArray();
        ArrayAdapter<String> dialogBranchAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_dropdown, branchOptions);
        dialogBranchAdapter.setDropDownViewResource(R.layout.item_dropdown_popup);
        spinnerBranch.setAdapter(dialogBranchAdapter);
        layout.addView(spinnerBranch);

        final Spinner spinnerService = new Spinner(requireContext());
        spinnerService.setBackgroundResource(R.drawable.bg_spinner);
        List<String> serviceNames = serviceDAO.all();
        ArrayAdapter<String> dialogServiceAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_dropdown, serviceNames);
        dialogServiceAdapter.setDropDownViewResource(R.layout.item_dropdown_popup);
        spinnerService.setAdapter(dialogServiceAdapter);
        layout.addView(spinnerService);

        new AlertDialog.Builder(requireContext())
                .setTitle("➕ New Walk-in Repair Docket")
                .setView(layout)
                .setPositiveButton("Create Docket", (dialog, which) -> {
                    String customer = inputCustomer.getText().toString().trim();
                    String device = inputDevice.getText().toString().trim();
                    String problem = inputProblem.getText().toString().trim();
                    String branch = (String) spinnerBranch.getSelectedItem();
                    String selectedService = (String) spinnerService.getSelectedItem();

                    if (device.isEmpty() || problem.isEmpty() || selectedService == null) {
                        Toast.makeText(requireContext(), "Please fill in device, issue, and service", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String sName = serviceDAO.serviceName(selectedService);
                    double sPrice = serviceDAO.price(selectedService);
                    String tech = technicianDAO.availableFor(branch, device);

                    long newId = appointmentDAO.add(0, device, problem + (customer.isEmpty() ? "" : " (Walk-in: " + customer + ")"), branch, sName, sPrice, tech, "Walk-in Counter");
                    FirebaseSyncManager.getInstance().sync(requireContext(), null);
                    refresh();
                    Snackbar.make(binding.getRoot(), "Walk-in Docket #" + newId + " created successfully!", Snackbar.LENGTH_LONG).show();
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
