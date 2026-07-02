package com.eyetimetracker.android;

public final class ConnectionStatusText {
    private ConnectionStatusText() {
    }

    public static String format(String baseStatus, boolean isConnected, String peerName) {
        if (!isConnected || peerName == null || peerName.trim().isEmpty()) {
            return baseStatus;
        }
        return baseStatus + "（已连接" + peerName + "）";
    }

    public static String format(String baseStatus, boolean isPaired, boolean isOnline, String peerName) {
        if (!isPaired || peerName == null || peerName.trim().isEmpty()) {
            return baseStatus;
        }
        if (!isOnline) {
            return baseStatus + "（" + peerName + "暂时离线）";
        }
        return baseStatus + "（已连接" + peerName + "）";
    }
}
