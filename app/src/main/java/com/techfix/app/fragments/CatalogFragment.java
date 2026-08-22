package com.techfix.app.fragments;

import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.techfix.app.R;
import com.techfix.app.adapters.ServiceCatalogAdapter;
import com.techfix.app.adapters.TechnicianAdapter;
import com.techfix.app.database.DatabaseHelper;
import com.techfix.app.database.SampleRepairDAO;
import com.techfix.app.database.ServiceDAO;
import com.techfix.app.database.TechnicianDAO;
import com.techfix.app.databinding.FragmentCatalogBinding;
import java.util.List;
import com.techfix.app.models.Service;
import com.techfix.app.models.Technician;
import com.techfix.app.sync.FirebaseSyncManager;
import com.techfix.app.util.WindowInsetsHelper;

/**
 * TAB 4: Services & Staff Catalog (service pricing, technician roster, showcase camera).
 */
public class CatalogFragment extends Fragment {

    private FragmentCatalogBinding binding;
    private ServiceDAO serviceDAO;
    private TechnicianDAO technicianDAO;
    private SampleRepairDAO sampleRepairDAO;

    private final FirebaseSyncManager.SyncListener syncListener = (isSyncing, success) -> {
        if (!isSyncing && success && isAdded() && binding != null) {
            requireActivity().runOnUiThread(this::refresh);
        }
    };

    private Uri pendingSampleUri;
    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (ok) publishSample();
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCatalogBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        DatabaseHelper dbHelper = DatabaseHelper.getInstance(requireContext());
        serviceDAO = new ServiceDAO(dbHelper);
        technicianDAO = new TechnicianDAO(dbHelper);
        sampleRepairDAO = new SampleRepairDAO(dbHelper);

        // Bottom inset so content clears the gesture nav bar / keyboard
        WindowInsetsHelper.applyBottomInset(binding.tabCatalog);

        FirebaseSyncManager.getInstance().addListener(syncListener);

        setupServices();
        setupTechnicians();
        setupShowcase();

        refresh();
    }

    private String selectedCatalogBranch = "All Branches";

    private void setupServices() {
        String[] branchFilters = {"All Branches", "Colombo branch", "Galle branch"};
        ArrayAdapter<String> filterAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_dropdown, branchFilters);
        filterAdapter.setDropDownViewResource(R.layout.item_dropdown_popup);
        binding.catalogBranchSpinner.setAdapter(filterAdapter);
        binding.catalogBranchSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedCatalogBranch = branchFilters[position];
                refresh();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        String[] branchOptions = {"Colombo branch", "Galle branch", "All Branches"};
        ArrayAdapter<String> newServiceBranchAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_dropdown, branchOptions);
        newServiceBranchAdapter.setDropDownViewResource(R.layout.item_dropdown_popup);
        binding.newServiceBranchSpinner.setAdapter(newServiceBranchAdapter);

        ServiceCatalogAdapter serviceCatalogAdapter = new ServiceCatalogAdapter(new ServiceCatalogAdapter.OnServiceActionListener() {
            @Override
            public void onEditPrice(Service service) {
                showEditServicePriceDialog(service);
            }

            @Override
            public void onDelete(Service service) {
                new AlertDialog.Builder(requireContext())
                        .setTitle("Remove Service?")
                        .setMessage("Remove \"" + service.name + "\" (" + service.branch + ") from service catalog?")
                        .setPositiveButton("Remove", (dialog, which) -> {
                            serviceDAO.delete(service.id);
                            FirebaseSyncManager.getInstance().sync(requireContext(), null);
                            refresh();
                            Snackbar.make(binding.getRoot(), "Service removed", Snackbar.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        binding.servicesList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.servicesList.setAdapter(serviceCatalogAdapter);

        // Add Service Button
        binding.addServiceButton.setOnClickListener(v -> {
            String name = binding.newServiceName.getText().toString().trim();
            String category = binding.newServiceCategory.getText().toString().trim();
            String part = binding.newServiceRequiredPart.getText().toString().trim();
            String priceStr = binding.newServicePrice.getText().toString().trim();
            String branch = (String) binding.newServiceBranchSpinner.getSelectedItem();

            if (name.isEmpty()) {
                binding.newServiceName.setError("Enter service name");
                return;
            }
            if (priceStr.isEmpty()) {
                binding.newServicePrice.setError("Enter price");
                return;
            }

            try {
                double price = Double.parseDouble(priceStr);
                serviceDAO.add(name, category.isEmpty() ? "Mobile phone" : category, price, part, branch);
                FirebaseSyncManager.getInstance().sync(requireContext(), null);
                binding.newServiceName.setText("");
                binding.newServiceCategory.setText("");
                binding.newServiceRequiredPart.setText("");
                binding.newServicePrice.setText("");

                refresh();
                Snackbar.make(v, "Service \"" + name + "\" published to " + branch, Snackbar.LENGTH_LONG).show();
            } catch (Exception e) {
                binding.newServicePrice.setError("Invalid price");
            }
        });
    }

    private void setupTechnicians() {
        TechnicianAdapter technicianAdapter = new TechnicianAdapter(new TechnicianAdapter.OnTechnicianActionListener() {
            @Override
            public void onToggleDuty(Technician technician) {
                technicianDAO.setAvailability(technician.name, !technician.available);
                FirebaseSyncManager.getInstance().sync(requireContext(), null);
                refresh();
                Snackbar.make(binding.getRoot(), technician.name + " duty status updated", Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void onDelete(Technician technician) {
                new AlertDialog.Builder(requireContext())
                        .setTitle("Remove Technician?")
                        .setMessage("Remove " + technician.name + " from technician roster?")
                        .setPositiveButton("Remove", (dialog, which) -> {
                            technicianDAO.delete(technician.id);
                            FirebaseSyncManager.getInstance().sync(requireContext(), null);
                            refresh();
                            Snackbar.make(binding.getRoot(), "Technician removed", Snackbar.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        }, techName -> technicianDAO.getActiveJobCount(techName));

        binding.techniciansList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.techniciansList.setAdapter(technicianAdapter);

        // Add Tech Button
        binding.addTechButton.setOnClickListener(v -> {
            String name = binding.newTechName.getText().toString().trim();
            String branch = binding.newTechBranch.getText().toString().trim();
            String skill = binding.newTechSkill.getText().toString().trim();

            if (name.isEmpty()) {
                binding.newTechName.setError("Enter technician name");
                return;
            }

            technicianDAO.add(name, branch.isEmpty() ? "Colombo branch" : branch, skill.isEmpty() ? "Mobile phone" : skill);
            FirebaseSyncManager.getInstance().sync(requireContext(), null);
            binding.newTechName.setText("");
            binding.newTechBranch.setText("");
            binding.newTechSkill.setText("");

            refresh();
            Snackbar.make(v, "Technician " + name + " registered successfully", Snackbar.LENGTH_LONG).show();
        });
    }

    private void setupShowcase() {
        // Publish Showcase Button
        binding.addSampleButton.setOnClickListener(v -> {
            String title = binding.sampleTitleInput.getText().toString().trim();
            if (title.isEmpty()) {
                binding.sampleTitleInput.setError("Add showcase title");
                return;
            }

            try {
                java.io.File dir = new java.io.File(requireContext().getCacheDir(), "images");
                if (!dir.exists()) dir.mkdirs();
                java.io.File photo = new java.io.File(dir, "sample_" + System.currentTimeMillis() + ".jpg");
                pendingSampleUri = androidx.core.content.FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fileprovider", photo);
                cameraLauncher.launch(pendingSampleUri);
            } catch (Exception e) {
                sampleRepairDAO.add(title, "Staff showcase", "");
                binding.sampleTitleInput.setText("");
                Snackbar.make(v, "Showcase published to Explore", Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void refresh() {
        List<Service> services = serviceDAO.listByBranch(selectedCatalogBranch);
        if (binding.servicesList.getAdapter() != null) {
            ((ServiceCatalogAdapter) binding.servicesList.getAdapter()).submit(services);
        }

        List<Technician> techs = technicianDAO.all();
        if (binding.techniciansList.getAdapter() != null) {
            ((TechnicianAdapter) binding.techniciansList.getAdapter()).submit(techs);
        }
    }

    private void showEditServicePriceDialog(Service service) {
        EditText input = new EditText(requireContext());
        input.setHint("New Price in LKR");
        input.setText(String.valueOf((long) service.price));
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        new AlertDialog.Builder(requireContext())
                .setTitle("Update Price · " + service.name)
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String pStr = input.getText().toString().trim();
                    try {
                        double price = Double.parseDouble(pStr);
                        serviceDAO.updatePrice(service.name, price);
                        FirebaseSyncManager.getInstance().sync(requireContext(), null);
                        refresh();
                        Snackbar.make(binding.getRoot(), "Price updated for " + service.name, Snackbar.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "Invalid price", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void publishSample() {
        try {
            String title = binding.sampleTitleInput.getText().toString().trim();
            if (title.isEmpty()) title = "Repair Showcase";
            sampleRepairDAO.add(title, "Staff showcase", String.valueOf(pendingSampleUri));
            binding.sampleTitleInput.setText("");
            Snackbar.make(binding.getRoot(), "Sample photo published to Explore", Snackbar.LENGTH_LONG).show();
        } catch (Exception e) {
            Snackbar.make(binding.getRoot(), "Could not save showcase", Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        FirebaseSyncManager.getInstance().removeListener(syncListener);
        binding = null;
    }
}
