package com.techfix.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;
import com.techfix.app.activities.HomeActivity;
import com.techfix.app.database.AppointmentDAO;
import com.techfix.app.database.DatabaseHelper;
import com.techfix.app.database.ServiceDAO;
import com.techfix.app.database.SparePartDAO;
import com.techfix.app.database.TechnicianDAO;
import com.techfix.app.database.UserDAO;
import com.techfix.app.databinding.FragmentAdminBinding;
import com.techfix.app.session.SessionManager;
import com.techfix.app.util.WindowInsetsHelper;

public class AdminFragment extends Fragment {

    private FragmentAdminBinding binding;
    private AppointmentDAO appointmentDAO;
    private ServiceDAO serviceDAO;
    private TechnicianDAO technicianDAO;
    private SparePartDAO sparePartDAO;
    private UserDAO userDAO;
    private DatabaseHelper dbHelper;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        dbHelper = DatabaseHelper.getInstance(requireContext());
        appointmentDAO = new AppointmentDAO(dbHelper);
        serviceDAO = new ServiceDAO(dbHelper);
        technicianDAO = new TechnicianDAO(dbHelper);
        sparePartDAO = new SparePartDAO(dbHelper);
        userDAO = new UserDAO(dbHelper);

        WindowInsetsHelper.applyBottomInset(binding.tabProfile);

        binding.staffProfileLogoutButton.setOnClickListener(v -> performLogout());

        binding.btnSyncFirebaseNow.setOnClickListener(v -> {
            binding.btnSyncFirebaseNow.setEnabled(false);
            binding.btnSyncFirebaseNow.setText("Syncing...");
            com.techfix.app.sync.FirebaseSyncManager.getInstance().sync(requireContext(), (success, message) -> {
                binding.btnSyncFirebaseNow.setEnabled(true);
                binding.btnSyncFirebaseNow.setText("Sync Now");
                refresh();
                Snackbar.make(binding.getRoot(), success ? "Data synchronized successfully" : "Sync notice: " + message, Snackbar.LENGTH_LONG).show();
            });
        });

        binding.btnReseedDemoData.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Reset Sample Data?")
                    .setMessage("This will restore default workshop services, spare parts inventory, and sample appointments.")
                    .setPositiveButton("Reset", (dialog, which) -> {
                        dbHelper.reseedData();
                        refresh();
                        Snackbar.make(binding.getRoot(), "Sample data restored successfully", Snackbar.LENGTH_LONG).show();
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
        if (getActivity() != null) {
            requireActivity().finish();
        }
    }

    private void refresh() {
        int appointmentsCount = appointmentDAO.all().size();
        int customersCount = userDAO.allCustomers().size();
        int partsCount = sparePartDAO.all().size();
        int servicesCount = serviceDAO.list().size();
        int techsCount = technicianDAO.all().size();

        binding.dbDiagnosticsText.setText("• SQLite Database: v6 (techfix.db)\n"
                + "• Records: " + appointmentsCount + " repairs, " + customersCount + " customers, "
                + partsCount + " parts, " + servicesCount + " services, " + techsCount + " technicians\n"
                + "• Cloud Sync: Firebase Auth & Firestore enabled\n"
                + "• Branches: Colombo Branch, Galle Branch");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
