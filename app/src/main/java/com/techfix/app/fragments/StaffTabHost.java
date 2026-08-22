package com.techfix.app.fragments;

/**
 * Communication bridge between staff tab fragments and StaffActivity.
 * Implemented by StaffActivity.
 */
public interface StaffTabHost {

    /** Updates the branch badge shown in the staff header bar. */
    void setHeaderBadge(String text);

    /** Switches the visible tab (0=Overview, 1=Queue, 2=Inventory, 3=Catalog, 4=Admin). */
    void switchToTab(int position);

    /** Branch filter shared between Overview and Queue tabs. */
    String getSelectedBranch();

    void setSelectedBranch(String branch);
}
