package com.eyetimetracker.android;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class LanDiscoveryAddresses {
    private static final String GLOBAL_BROADCAST = "255.255.255.255";
    private static final int MAX_CANDIDATE_HOSTS = 254;

    private LanDiscoveryAddresses() {
    }

    public static List<InetAddress> broadcastAddresses() {
        Set<String> hosts = new LinkedHashSet<>();
        hosts.add(GLOBAL_BROADCAST);
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!isUsable(networkInterface)) {
                    continue;
                }
                for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                    InetAddress broadcast = interfaceAddress.getBroadcast();
                    if (broadcast instanceof Inet4Address) {
                        hosts.add(broadcast.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return toAddresses(hosts);
    }

    public static List<InetAddress> candidateHosts() {
        Set<String> hosts = new LinkedHashSet<>();
        Set<String> localHosts = new LinkedHashSet<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!isUsable(networkInterface)) {
                    continue;
                }
                for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                    InetAddress address = interfaceAddress.getAddress();
                    if (!(address instanceof Inet4Address)) {
                        continue;
                    }
                    String localHost = address.getHostAddress();
                    localHosts.add(localHost);
                    addSubnetCandidates(hosts, (Inet4Address) address, interfaceAddress.getNetworkPrefixLength());
                    if (hosts.size() >= MAX_CANDIDATE_HOSTS) {
                        break;
                    }
                }
                if (hosts.size() >= MAX_CANDIDATE_HOSTS) {
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        hosts.removeAll(localHosts);
        return toAddresses(hosts);
    }

    private static boolean isUsable(NetworkInterface networkInterface) {
        try {
            return networkInterface != null
                    && networkInterface.isUp()
                    && !networkInterface.isLoopback()
                    && !networkInterface.isVirtual();
        } catch (Exception ex) {
            return false;
        }
    }

    private static void addSubnetCandidates(Set<String> hosts, Inet4Address address, short prefixLength) {
        byte[] bytes = address.getAddress();
        int ip = ((bytes[0] & 0xff) << 24)
                | ((bytes[1] & 0xff) << 16)
                | ((bytes[2] & 0xff) << 8)
                | (bytes[3] & 0xff);
        int effectivePrefix = prefixLength <= 0 || prefixLength > 30 ? 24 : Math.max(prefixLength, 24);
        int mask = (int) (0xffffffffL << (32 - effectivePrefix));
        int network = ip & mask;
        int broadcast = network | ~mask;
        for (int candidate = network + 1; candidate < broadcast && hosts.size() < MAX_CANDIDATE_HOSTS; candidate++) {
            hosts.add(((candidate >>> 24) & 0xff) + "."
                    + ((candidate >>> 16) & 0xff) + "."
                    + ((candidate >>> 8) & 0xff) + "."
                    + (candidate & 0xff));
        }
    }

    private static List<InetAddress> toAddresses(Set<String> hosts) {
        List<InetAddress> addresses = new ArrayList<>();
        for (String host : hosts) {
            try {
                addresses.add(InetAddress.getByName(host));
            } catch (Exception ignored) {
            }
        }
        return addresses;
    }
}
