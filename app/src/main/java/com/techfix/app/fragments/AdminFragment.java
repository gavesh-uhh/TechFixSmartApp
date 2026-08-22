package com.techfix.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.techfix.app.activities.HomeActivity;
import com.techfix.app.adapters.UserDirectoryAdapter;
import com.techfix.app.database.AppointmentDAO;
import com.techfix.app.database.DatabaseHelper;
import com.techfix.app.database.ServiceDAO;
import com.techfix.app.database.SparePartDAO;
import com.techfix.app.database.TechnicianDAO;
import com.techfix.app.database.UserDAO;
import com.techfix.app.databinding.FragmentAdminBinding;
import com.techfix.app.models.User;
import com.techfix.app.session.SessionManager;
import com.techfix.app.util.WindowInsetsHelper;

import java.util.List;

/**
 * TAB 5: Admin & Customer Directory (customer directory, DB diagnostics, logout).
 */
public class AdminFragment extends Fragment {

    private FragmentAdminBinding binding;
    private AppointmentDAO appointmentDAO;
    private ServiceDAO serviceDAO;
    private TechnicianDAO technicianDAO;
    private SparePartDAO sparePartDAO;
    private UserDAO userDAO;
    private UserDirectoryAdapter userDirectoryAdapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        DatabaseHelper dbHelper = DatabaseHelper.getInstance(requireContext());
        appointmentDAO = new AppointmentDAO(dbHelper);
        serviceDAO = new ServiceDAO(dbHelper);
        technicianDAO = new TechnicianDAO(dbHelper);
        sparePartDAO = new SparePartDAO(dbHelper);
        userDAO = new UserDAO(dbHelper);

        // Bottom inset so content clears the gesture nav bar / keyboard
        WindowInsetsHelper.applyBottomInset(binding.tabProfile);

        userDirectoryAdapter = new UserDirectoryAdapter(
                userId -> userDAO.getRepairCountForUser(userId),
                user -> Toast.makeText(requireContext(), "Customer: " + user.name + " (" + user.email + ")", Toast.LENGTH_SHORT).show()
        );

        binding.customersList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.customersList.setAdapter(userDirectoryAdapter);

        binding.searchCustomersInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshCustomers(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Log Out of Account (only remaining logout entry point)
        binding.staffProfileLogoutButton.setOnClickListener(v -> performLogout());

        // Reseed demo data button
        binding.btnReseedDemoData.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Reset Demo Database?")
                    .setMessage("This will reset repair appointments and inventory to clean demo default values.")
                    .setPositiveButton("Reset & Reseed", (dialog, which) -> {
                        dbHelper.reseedData();
                        refresh();
                        Snackbar.make(binding.getRoot(), "Demo data reseeded successfully", Snackbar.LENGTH_LONG).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        refresh();
    }

    private void performLogout() {
        new SessionManager(requireContext()).logout();
        Toast.makeText(requireContext(), "Logged out of admin workspace", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(requireContext(), HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        requireActivity().finish();
    }

    private void refresh() {
        refreshCustomers(binding.searchCustomersInput.getText().toString());

        int appointmentsCount = appointmentDAO.all().size();
        int customersCount = userDAO.allCustomers().size();
        int partsCount = sparePartDAO.all().size();
        int servicesCount = serviceDAO.list().size();
        int techsCount = technicianDAO.all().size();

        binding.dbDiagnosticsText.setText("• SQLite Database: v5 (techfix.db)\n"
                + "• Records: " + appointmentsCount + " repairs, " + customersCount + " customers, "
                + partsCount + " parts, " + servicesCount + " services, " + techsCount + " technicians\n"
                + "• Cloud Sync: Firebase Auth & Firestore enabled\n"
                + "• Branches: Colombo Branch, Galle Branch");
    }

    private void refreshCustomers(String query) {
        List<User> customers = userDAO.searchCustomers(query);
        userDirectoryAdapter.submit(customers);
        binding.customersList.setVisibility(customers.isEmpty() ? View.GONE : View.VISIBLE);
        binding.emptyCustomersContainer.setVisibility(customers.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
