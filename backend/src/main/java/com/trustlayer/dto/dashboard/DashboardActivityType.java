package com.trustlayer.dto.dashboard;

/** Distinguishes timeline cards without exposing internal database table names to the UI. */
public enum DashboardActivityType {
    URL_SCAN,
    QR_SCAN,
    TRANSACTION_SIMULATION,
    COMMUNITY_REPORT
}
