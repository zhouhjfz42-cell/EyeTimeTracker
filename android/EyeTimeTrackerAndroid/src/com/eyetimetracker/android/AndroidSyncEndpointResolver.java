package com.eyetimetracker.android;

public final class AndroidSyncEndpointResolver {
    private AndroidSyncEndpointResolver() {
    }

    public static boolean applyDiscoveredPeer(
            SyncSettings settings,
            AndroidPcDiscoveryClient.DiscoveryResult discovery) {
        if (settings == null || discovery == null || !settings.isPaired || !discovery.found) {
            return false;
        }
        if (settings.peerDeviceId == null
                || settings.peerDeviceId.trim().isEmpty()
                || !settings.peerDeviceId.equals(discovery.deviceId)) {
            return false;
        }
        if (discovery.host == null || discovery.host.trim().isEmpty() || discovery.port <= 0) {
            return false;
        }

        settings.peerHost = discovery.host;
        settings.peerPort = discovery.port;
        settings.peerPlatform = discovery.platform == null || discovery.platform.trim().isEmpty()
                ? settings.peerPlatform
                : discovery.platform;
        settings.lastError = "";
        return true;
    }
}
