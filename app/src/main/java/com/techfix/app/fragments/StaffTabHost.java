package com.techfix.app.fragments;

public interface StaffTabHost {

    void switchToTab(int position);

    String getSelectedBranch();

    void setSelectedBranch(String branch);
}
