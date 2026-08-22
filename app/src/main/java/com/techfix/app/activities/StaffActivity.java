package com.techfix.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.techfix.app.R;
import com.techfix.app.adapters.ServiceCatalogAdapter;
import com.techfix.app.adapters.SparePartAdapter;
import com.techfix.app.adapters.StaffAppointmentAdapter;
import com.techfix.app.adapters.TechnicianAdapter;
import com.techfix.app.adapters.UserDirectoryAdapter;
import com.techfix.app.database.AppointmentDAO;
import com.techfix.app.database.DatabaseHelper;
import com.techfix.app.database.SampleRepairDAO;
import com.techfix.app.database.ServiceDAO;
import com.techfix.app.database.SparePartDAO;
import com.techfix.app.database.TechnicianDAO;
import com.techfix.app.database.UserDAO;
import com.techfix.app.databinding.ActivityStaffBinding;
import com.techfix.app.models.Appointment;
import com.techfix.app.models.AppointmentStatus;
import com.techfix.app.models.PaymentStatus;
import com.techfix.app.models.Service;
import com.techfix.app.models.SparePart;
import com.techfix.app.models.Technician;
import com.techfix.app.models.User;
import com.techfix.app.session.SessionManager;
import com.techfix.app.util.WindowInsetsHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * StaffActivity - Executive Workshop & Admin Dashboard.
 * Hubs:
 * 1. Overview & Financial KPIs (Revenue, receivables, low stock alerts, quick actions)
 * 2. Repair Queue & Docket Master (Multi-status chips, real-time search, workflow updates)
 * 3. Inventory & Spare Parts (Stock badges, 1-tap stock adjustments, part creation)
 * 4. Catalog & Staff Roster (Service pricing, technician duty switches, showcase camera)
 * 5. Admin & Customer Directory (Customer directory, DB diagnostics & maintenance)
 */
public class StaffActivity extends AppCompatActivity {

    private ActivityStaffBinding binding;
    private SessionManager session;
    private DatabaseHelper dbHelper;

    // DAOs
    private AppointmentDAO appointmentDAO;
    private ServiceDAO serviceDAO;
    private TechnicianDAO technicianDAO;
    private SparePartDAO sparePartDAO;
    private SampleRepairDAO sampleRepairDAO;
    private UserDAO userDAO;

    // Adapters
    private StaffAppointmentAdapter queueAdapter;
    private SparePartAdapter sparePartAdapter;
    private ServiceCatalogAdapter serviceCatalogAdapter;
    private TechnicianAdapter technicianAdapter;
    private UserDirectoryAdapter userDirectoryAdapter;

    // State
    private String currentSelectedBranch = "All Branches";
    private String currentQueueStatusFilter = "All";
    private int currentTabPosition = 0;

    private Uri pendingSampleUri;
    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (ok) publishSample();
            });

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
        WindowInsetsHelper.apply(binding.staffHeader, binding.staffContent);

        // 3. Initialize Database DAOs
        dbHelper = DatabaseHelper.getInstance(this);
        appointmentDAO = new AppointmentDAO(dbHelper);
        serviceDAO = new ServiceDAO(dbHelper);
        technicianDAO = new TechnicianDAO(dbHelper);
        sparePartDAO = new SparePartDAO(dbHelper);
        sampleRepairDAO = new SampleRepairDAO(dbHelper);
        userDAO = new UserDAO(dbHelper);

        // 4. Setup UI Hubs & Navigation
        setupHeader();
        setupBottomNavigation();
        setupOverviewTab();
        setupQueueTab();
        setupInventoryTab();
        setupCatalogTab();
        setupAdminTab();

        // 5. Initial Data Load
        showPanel(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!session.isLoggedIn()) {
            goHome();
        } else {
            refreshCurrentTab();
        }
    }

    private void setupHeader() {
        binding.staffHomeStoreButton.setOnClickListener(v -> {
            startActivity(new Intent(StaffActivity.this, HomeActivity.class));
        });

        binding.logoutButton.setOnClickListener(v -> performLogout());
        binding.staffProfileLogoutButton.setOnClickListener(v -> performLogout());
    }

    private void performLogout() {
        session.logout();
        Toast.makeText(this, "Logged out of admin workspace", Toast.LENGTH_SHORT).show();
        goHome();
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
        binding.staffBottomNavigation.setSelectedItemId(R.id.nav_staff_overview);

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
    }

    private void showPanel(int position) {
        currentTabPosition = position;
        binding.tabOverview.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        binding.tabQueue.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
        binding.tabInventory.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
        binding.tabCatalog.setVisibility(position == 3 ? View.VISIBLE : View.GONE);
        binding.tabProfile.setVisibility(position == 4 ? View.VISIBLE : View.GONE);

        refreshCurrentTab();
    }

    private void refreshCurrentTab() {
        if (currentTabPosition == 0) {
            refreshOverview();
        } else if (currentTabPosition == 1) {
            refreshQueue();
        } else if (currentTabPosition == 2) {
            refreshInventory();
        } else if (currentTabPosition == 3) {
            refreshCatalog();
        } else if (currentTabPosition == 4) {
            refreshAdminTab();
        }
    }

    // =========================================================================
    // TAB 1: OVERVIEW & FINANCIAL KPIS
    // =========================================================================

    private void setupOverviewTab() {
        String[] branches = {"All Branches", "Colombo branch", "Galle branch"};
        ArrayAdapter<String> branchAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, branches);
        binding.overviewBranchSpinner.setAdapter(branchAdapter);

        binding.overviewBranchSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentSelectedBranch = branches[position];
                binding.headerBranchBadge.setText(currentSelectedBranch.toUpperCase());
                refreshOverview();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Quick Actions
        binding.btnQuickWalkIn.setOnClickListener(v -> showWalkInDocketDialog());
        binding.btnQuickRestock.setOnClickListener(v -> {
            binding.staffBottomNavigation.setSelectedItemId(R.id.nav_staff_inventory);
        });
        binding.btnQuickAddService.setOnClickListener(v -> {
            binding.staffBottomNavigation.setSelectedItemId(R.id.nav_staff_catalog);
        });
        binding.btnQuickAddTech.setOnClickListener(v -> {
            binding.staffBottomNavigation.setSelectedItemId(R.id.nav_staff_catalog);
        });
    }

    private void refreshOverview() {
        int totalDockets = appointmentDAO.countAll(currentSelectedBranch);
        int activeDockets = appointmentDAO.countActive(currentSelectedBranch);
        int completedDockets = appointmentDAO.countCompleted(currentSelectedBranch);
        double paidRevenue = appointmentDAO.sumPaidRevenue(currentSelectedBranch);
        double pendingRevenue = appointmentDAO.sumPendingRevenue(currentSelectedBranch);
        int lowStockCount = sparePartDAO.getLowStockCount(2);

        binding.kpiTotalDockets.setText(String.valueOf(totalDockets));
        binding.kpiTotalDocketsSub.setText(completedDockets + " completed · " + activeDockets + " in progress");

        binding.kpiActiveDockets.setText(String.valueOf(activeDockets));
        binding.kpiActiveDocketsSub.setText("Awaiting collection & repair");

        binding.kpiRevenueCollected.setText("Rs " + String.format("%,.0f", paidRevenue));
        binding.kpiRevenuePending.setText("Rs " + String.format("%,.0f", pendingRevenue));

        if (lowStockCount > 0) {
            binding.kpiLowStockAlert.setText("⚠️ " + lowStockCount + " Parts Low in Stock!");
            binding.kpiLowStockAlert.setTextColor(getColor(R.color.warning));
        } else {
            binding.kpiLowStockAlert.setText("📦 Inventory: All parts in stock");
            binding.kpiLowStockAlert.setTextColor(getColor(R.color.navy_900));
        }

        List<Technician> techList = technicianDAO.allByBranch(currentSelectedBranch);
        int availableTechs = 0;
        for (Technician t : techList) {
            if (t.available) availableTechs++;
        }
        binding.kpiTechDutyStatus.setText("👨‍🔧 " + availableTechs + "/" + techList.size() + " Techs On Duty");
    }

    // =========================================================================
    // TAB 2: REPAIR QUEUE & DOCKET MANAGEMENT
    // =========================================================================

    private void setupQueueTab() {
        binding.statusSpinner.setAdapter(new ArrayAdapter<>(this, R.layout.item_dropdown, AppointmentStatus.labels()));

        queueAdapter = new StaffAppointmentAdapter(this::showDocketActionMenu);
        binding.appointmentList.setLayoutManager(new LinearLayoutManager(this));
        binding.appointmentList.setAdapter(queueAdapter);

        // Real-time search listener
        binding.searchQueueInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshQueue();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Filter chips setup
        setupFilterChips();

        // Quick status update by ID
        binding.updateStatusButton.setOnClickListener(this::updateStatusById);
    }

    private void setupFilterChips() {
        binding.chipFilterAll.setOnClickListener(v -> setQueueFilter("All", binding.chipFilterAll));
        binding.chipFilterActive.setOnClickListener(v -> setQueueFilter("Active", binding.chipFilterActive));
        binding.chipFilterReceived.setOnClickListener(v -> setQueueFilter(AppointmentStatus.REQUEST_RECEIVED.label, binding.chipFilterReceived));
        binding.chipFilterRepairing.setOnClickListener(v -> setQueueFilter(AppointmentStatus.REPAIRING.label, binding.chipFilterRepairing));
        binding.chipFilterReady.setOnClickListener(v -> setQueueFilter(AppointmentStatus.READY_FOR_COLLECTION.label, binding.chipFilterReady));
        binding.chipFilterCompleted.setOnClickListener(v -> setQueueFilter(AppointmentStatus.COMPLETED.label, binding.chipFilterCompleted));
        binding.chipFilterUnpaid.setOnClickListener(v -> setQueueFilter("Unpaid", binding.chipFilterUnpaid));
    }

    private void setQueueFilter(String status, View activeChip) {
        currentQueueStatusFilter = status;

        // Reset all chip backgrounds
        resetChipStyle(binding.chipFilterAll);
        resetChipStyle(binding.chipFilterActive);
        resetChipStyle(binding.chipFilterReceived);
        resetChipStyle(binding.chipFilterRepairing);
        resetChipStyle(binding.chipFilterReady);
        resetChipStyle(binding.chipFilterCompleted);
        resetChipStyle(binding.chipFilterUnpaid);

        // Highlight selected chip
        if (activeChip instanceof android.widget.Button) {
            ((android.widget.Button) activeChip).setBackgroundColor(getColor(R.color.navy_700));
            ((android.widget.Button) activeChip).setTextColor(getColor(R.color.white));
        }

        refreshQueue();
    }

    private void resetChipStyle(android.widget.Button btn) {
        btn.setBackgroundColor(getColor(R.color.surface));
        btn.setTextColor(getColor(R.color.navy_700));
    }

    private void refreshQueue() {
        String searchQuery = binding.searchQueueInput.getText().toString();
        List<Appointment> filtered = appointmentDAO.filter(currentSelectedBranch, currentQueueStatusFilter, searchQuery);

        queueAdapter.submit(filtered);
        binding.appointmentList.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        binding.emptyQueueContainer.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);

        int activeCount = appointmentDAO.countActive(currentSelectedBranch);
        int completedCount = appointmentDAO.countCompleted(currentSelectedBranch);
        int totalCount = appointmentDAO.countAll(currentSelectedBranch);

        binding.queueStatsTitle.setText(totalCount + (totalCount == 1 ? " Repair Docket" : " Repair Dockets"));
        binding.queueStatsSubtitle.setText(activeCount + " Active Repairs · " + completedCount + " Completed");
    }

    private void showDocketActionMenu(Appointment a) {
        String[] actions = {
                "Change Workflow Stage (" + a.status + ")",
                "Re-assign Technician (" + a.technician + ")",
                "Record Payment (Rs " + (long) a.price + ")",
                "View Full Docket & Timeline",
                "Delete Docket"
        };

        new AlertDialog.Builder(this)
                .setTitle("Docket #" + a.id + " · " + a.device)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        showStatusPicker(a);
                    } else if (which == 1) {
                        showTechnicianPicker(a);
                    } else if (which == 2) {
                        showPaymentMethodPicker(a);
                    } else if (which == 3) {
                        Intent intent = new Intent(this, AppointmentDetailActivity.class);
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
        new AlertDialog.Builder(this)
                .setTitle("Update Status · #" + a.id)
                .setItems(labels, (dialog, which) -> {
                    String newStatus = labels[which];
                    appointmentDAO.updateStatus(a.id, newStatus);
                    refreshQueue();
                    refreshOverview();
                    Snackbar.make(binding.getRoot(), "Docket #" + a.id + " updated to " + newStatus, Snackbar.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showTechnicianPicker(Appointment a) {
        List<Technician> techList = technicianDAO.all();
        String[] techNames = new String[techList.size()];
        for (int i = 0; i < techList.size(); i++) {
            techNames[i] = techList.get(i).name + " (" + techList.get(i).branch + ")";
        }

        new AlertDialog.Builder(this)
                .setTitle("Assign Technician · #" + a.id)
                .setItems(techNames, (dialog, which) -> {
                    String chosenTech = techList.get(which).name;
                    appointmentDAO.updateTechnician(a.id, chosenTech);
                    refreshQueue();
                    Snackbar.make(binding.getRoot(), "Assigned to " + chosenTech, Snackbar.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPaymentMethodPicker(Appointment a) {
        String[] methods = {"Cash at counter", "Card", "Bank transfer"};
        new AlertDialog.Builder(this)
                .setTitle("Record Payment · Rs " + (long) a.price)
                .setItems(methods, (dialog, which) -> {
                    boolean ok = appointmentDAO.pay(a.id, a.price, methods[which]);
                    refreshQueue();
                    refreshOverview();
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
        new AlertDialog.Builder(this)
                .setTitle("Delete Docket #" + a.id + "?")
                .setMessage("Are you sure you want to remove this repair docket permanently?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    appointmentDAO.delete(a.id);
                    refreshQueue();
                    refreshOverview();
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
            binding.appointmentIdInput.setText("");
            refreshQueue();
            refreshOverview();
            Snackbar.make(v, "Docket #" + id + " updated to " + status, Snackbar.LENGTH_LONG).show();
        } catch (Exception e) {
            binding.appointmentIdInput.setError("Invalid docket ID");
        }
    }

    /**
     * Dialog to create a walk-in repair order right at the counter.
     */
    private void showWalkInDocketDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.activity_appointment_detail, null, false);
        // Let's create an elegant programmatic form dialog
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = (int) (18 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        final EditText inputCustomer = new EditText(this);
        inputCustomer.setHint("Customer Name (e.g. Ruwan Silva)");
        layout.addView(inputCustomer);

        final EditText inputDevice = new EditText(this);
        inputDevice.setHint("Device (e.g. iPhone 14 Pro, ThinkPad X1)");
        layout.addView(inputDevice);

        final EditText inputProblem = new EditText(this);
        inputProblem.setHint("Problem Description (e.g. Cracked screen, battery drain)");
        layout.addView(inputProblem);

        final Spinner spinnerBranch = new Spinner(this);
        String[] branchOptions = {"Colombo branch", "Galle branch"};
        spinnerBranch.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, branchOptions));
        layout.addView(spinnerBranch);

        final Spinner spinnerService = new Spinner(this);
        List<String> serviceNames = serviceDAO.all();
        spinnerService.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, serviceNames));
        layout.addView(spinnerService);

        new AlertDialog.Builder(this)
                .setTitle("➕ New Walk-in Repair Docket")
                .setView(layout)
                .setPositiveButton("Create Docket", (dialog, which) -> {
                    String customer = inputCustomer.getText().toString().trim();
                    String device = inputDevice.getText().toString().trim();
                    String problem = inputProblem.getText().toString().trim();
                    String branch = (String) spinnerBranch.getSelectedItem();
                    String selectedService = (String) spinnerService.getSelectedItem();

                    if (device.isEmpty() || problem.isEmpty() || selectedService == null) {
                        Toast.makeText(this, "Please fill in device, issue, and service", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String sName = serviceDAO.serviceName(selectedService);
                    double sPrice = serviceDAO.price(selectedService);
                    String tech = technicianDAO.availableFor(branch, device);

                    long newId = appointmentDAO.add(0, device, problem + (customer.isEmpty() ? "" : " (Walk-in: " + customer + ")"), branch, sName, sPrice, tech, "Walk-in Counter");
                    refreshQueue();
                    refreshOverview();
                    Snackbar.make(binding.getRoot(), "Walk-in Docket #" + newId + " created successfully!", Snackbar.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // =========================================================================
    // TAB 3: INVENTORY & SPARE PARTS
    // =========================================================================

    private void setupInventoryTab() {
        String[] branches = {"All Branches", "Colombo branch", "Galle branch"};
        ArrayAdapter<String> branchAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, branches);
        binding.inventoryBranchSpinner.setAdapter(branchAdapter);

        binding.inventoryBranchSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refreshInventory();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        sparePartAdapter = new SparePartAdapter(new SparePartAdapter.OnPartActionListener() {
            @Override
            public void onAdjustQuantity(SparePart part, int delta) {
                sparePartDAO.adjustStock(part.id, delta);
                refreshInventory();
                refreshOverview();
            }

            @Override
            public void onDelete(SparePart part) {
                new AlertDialog.Builder(StaffActivity.this)
                        .setTitle("Delete " + part.name + "?")
                        .setMessage("Remove this spare part item from " + part.branch + " inventory?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            sparePartDAO.delete(part.id);
                            refreshInventory();
                            refreshOverview();
                            Snackbar.make(binding.getRoot(), "Part deleted", Snackbar.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        binding.partsList.setLayoutManager(new LinearLayoutManager(this));
        binding.partsList.setAdapter(sparePartAdapter);

        // Add part button
        binding.btnAddPartButton.setOnClickListener(v -> {
            String name = binding.newPartNameInput.getText().toString().trim();
            String branch = binding.newPartBranchInput.getText().toString().trim();
            String qtyStr = binding.newPartQtyInput.getText().toString().trim();

            if (name.isEmpty()) {
                binding.newPartNameInput.setError("Enter part name");
                return;
            }
            int qty = 1;
            try { if (!qtyStr.isEmpty()) qty = Integer.parseInt(qtyStr); } catch (Exception ignored) {}

            sparePartDAO.add(name, branch.isEmpty() ? "Colombo branch" : branch, qty);
            binding.newPartNameInput.setText("");
            binding.newPartBranchInput.setText("");
            binding.newPartQtyInput.setText("");

            refreshInventory();
            refreshOverview();
            Snackbar.make(v, "Saved " + name + " to inventory", Snackbar.LENGTH_LONG).show();
        });
    }

    private void refreshInventory() {
        String branch = (String) binding.inventoryBranchSpinner.getSelectedItem();
        List<SparePart> parts = sparePartDAO.allByBranch(branch);

        sparePartAdapter.submit(parts);
        binding.partsList.setVisibility(parts.isEmpty() ? View.GONE : View.VISIBLE);
        binding.emptyPartsContainer.setVisibility(parts.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // =========================================================================
    // TAB 4: SERVICES & STAFF CATALOG
    // =========================================================================

    private void setupCatalogTab() {
        // Services Adapter
        serviceCatalogAdapter = new ServiceCatalogAdapter(new ServiceCatalogAdapter.OnServiceActionListener() {
            @Override
            public void onEditPrice(Service service) {
                showEditServicePriceDialog(service);
            }

            @Override
            public void onDelete(Service service) {
                new AlertDialog.Builder(StaffActivity.this)
                        .setTitle("Remove Service?")
                        .setMessage("Remove \"" + service.name + "\" from service catalog?")
                        .setPositiveButton("Remove", (dialog, which) -> {
                            serviceDAO.delete(service.id);
                            refreshCatalog();
                            Snackbar.make(binding.getRoot(), "Service removed", Snackbar.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        binding.servicesList.setLayoutManager(new LinearLayoutManager(this));
        binding.servicesList.setAdapter(serviceCatalogAdapter);

        // Add Service Button
        binding.addServiceButton.setOnClickListener(v -> {
            String name = binding.newServiceName.getText().toString().trim();
            String category = binding.newServiceCategory.getText().toString().trim();
            String part = binding.newServiceRequiredPart.getText().toString().trim();
            String priceStr = binding.newServicePrice.getText().toString().trim();

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
                serviceDAO.add(name, category.isEmpty() ? "Mobile phone" : category, price, part);
                binding.newServiceName.setText("");
                binding.newServiceCategory.setText("");
                binding.newServiceRequiredPart.setText("");
                binding.newServicePrice.setText("");

                refreshCatalog();
                Snackbar.make(v, "Service \"" + name + "\" published to catalog", Snackbar.LENGTH_LONG).show();
            } catch (Exception e) {
                binding.newServicePrice.setError("Invalid price");
            }
        });

        // Technicians Adapter
        technicianAdapter = new TechnicianAdapter(new TechnicianAdapter.OnTechnicianActionListener() {
            @Override
            public void onToggleDuty(Technician technician) {
                technicianDAO.setAvailability(technician.name, !technician.available);
                refreshCatalog();
                refreshOverview();
                Snackbar.make(binding.getRoot(), technician.name + " duty status updated", Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void onDelete(Technician technician) {
                new AlertDialog.Builder(StaffActivity.this)
                        .setTitle("Remove Technician?")
                        .setMessage("Remove " + technician.name + " from technician roster?")
                        .setPositiveButton("Remove", (dialog, which) -> {
                            technicianDAO.delete(technician.id);
                            refreshCatalog();
                            refreshOverview();
                            Snackbar.make(binding.getRoot(), "Technician removed", Snackbar.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        }, techName -> technicianDAO.getActiveJobCount(techName));

        binding.techniciansList.setLayoutManager(new LinearLayoutManager(this));
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
            binding.newTechName.setText("");
            binding.newTechBranch.setText("");
            binding.newTechSkill.setText("");

            refreshCatalog();
            refreshOverview();
            Snackbar.make(v, "Technician " + name + " registered successfully", Snackbar.LENGTH_LONG).show();
        });

        // Publish Showcase Button
        binding.addSampleButton.setOnClickListener(v -> {
            String title = binding.sampleTitleInput.getText().toString().trim();
            if (title.isEmpty()) {
                binding.sampleTitleInput.setError("Add showcase title");
                return;
            }

            try {
                java.io.File dir = new java.io.File(getCacheDir(), "images");
                if (!dir.exists()) dir.mkdirs();
                java.io.File photo = new java.io.File(dir, "sample_" + System.currentTimeMillis() + ".jpg");
                pendingSampleUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photo);
                cameraLauncher.launch(pendingSampleUri);
            } catch (Exception e) {
                sampleRepairDAO.add(title, "Staff showcase", "");
                binding.sampleTitleInput.setText("");
                Snackbar.make(v, "Showcase published to Explore", Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void refreshCatalog() {
        List<Service> services = serviceDAO.list();
        serviceCatalogAdapter.submit(services);

        List<Technician> techs = technicianDAO.all();
        technicianAdapter.submit(techs);
    }

    private void showEditServicePriceDialog(Service service) {
        EditText input = new EditText(this);
        input.setHint("New Price in LKR");
        input.setText(String.valueOf((long) service.price));
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);

        new AlertDialog.Builder(this)
                .setTitle("Update Price · " + service.name)
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String pStr = input.getText().toString().trim();
                    try {
                        double price = Double.parseDouble(pStr);
                        serviceDAO.updatePrice(service.name, price);
                        refreshCatalog();
                        Snackbar.make(binding.getRoot(), "Price updated for " + service.name, Snackbar.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "Invalid price", Toast.LENGTH_SHORT).show();
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

    // =========================================================================
    // TAB 5: ADMIN & CUSTOMER DIRECTORY
    // =========================================================================

    private void setupAdminTab() {
        userDirectoryAdapter = new UserDirectoryAdapter(
                userId -> userDAO.getRepairCountForUser(userId),
                user -> {
                    Toast.makeText(this, "Customer: " + user.name + " (" + user.email + ")", Toast.LENGTH_SHORT).show();
                }
        );

        binding.customersList.setLayoutManager(new LinearLayoutManager(this));
        binding.customersList.setAdapter(userDirectoryAdapter);

        binding.searchCustomersInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshCustomers(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Reseed demo data button
        binding.btnReseedDemoData.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Reset Demo Database?")
                    .setMessage("This will reset repair appointments and inventory to clean demo default values.")
                    .setPositiveButton("Reset & Reseed", (dialog, which) -> {
                        dbHelper.reseedData();
                        refreshOverview();
                        refreshQueue();
                        refreshInventory();
                        refreshCatalog();
                        refreshAdminTab();
                        Snackbar.make(binding.getRoot(), "Demo data reseeded successfully", Snackbar.LENGTH_LONG).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void refreshAdminTab() {
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
}
