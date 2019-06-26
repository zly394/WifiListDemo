package com.zly.wifilistdemo;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import java.lang.reflect.Method;

/**
 * Created by zhuleiyue on 2018/3/12.
 */

public class AccessPoint implements Parcelable, Comparable<AccessPoint> {
    public static final int SECURITY_NONE = 0;
    public static final int SECURITY_WEP = 1;
    public static final int SECURITY_PSK = 2;
    public static final int SECURITY_EAP = 3;

    private static final int PSK_UNKNOWN = 0;
    private static final int PSK_WPA = 1;
    private static final int PSK_WPA2 = 2;
    private static final int PSK_WPA_WPA2 = 3;

    /**
     * Anything worse than or equal to this will show 0 bars.
     */
    private static final int MIN_RSSI = -100;

    /**
     * Anything better than or equal to this will show the max bars.
     */
    private static final int MAX_RSSI = -55;

    public static final int SIGNAL_LEVELS = 4;

    public static final int INVALID_NETWORK_ID = -1;

    public Context context;
    public String ssid;
    public String bssid;
    public int security;
    public int networkId = INVALID_NETWORK_ID;
    public int pskType = PSK_UNKNOWN;

    public WifiConfiguration wifiConfiguration;

    private int rssi = Integer.MAX_VALUE;

    public WifiInfo wifiInfo;
    public NetworkInfo networkInfo;

    public boolean isSecured = true;
    public String password;

    private boolean isPasswordError = false;

    public AccessPoint(Context context, ScanResult scanResult) {
        this.context = context;
        initWithScanResult(scanResult);
    }

    public AccessPoint(Context context, WifiConfiguration configuration) {
        this.context = context;
        initWithConfiguration(configuration);
    }

    public AccessPoint(WifiConfiguration configuration) {
        initWithConfiguration(configuration);
    }

    /**
     * 根据 ScanResult 初始化 AccessPoint
     */
    private void initWithScanResult(ScanResult result) {
        this.ssid = result.SSID;
        this.bssid = result.BSSID;
        this.security = getSecurity(result);
        if (this.security == SECURITY_PSK) {
            this.pskType = getPskType(result);
        }
        if (this.security == SECURITY_NONE) {
            this.isSecured = false;
        }
        this.rssi = result.level;
    }

    private void initWithConfiguration(WifiConfiguration configuration) {
        this.ssid = (configuration.SSID == null ? "" : removeDoubleQuotes(configuration.SSID));
        this.bssid = configuration.BSSID;
        this.security = getSecurity(configuration);
        this.networkId = configuration.networkId;
        this.wifiConfiguration = configuration;
    }

    /**
     * 获取状态概要
     */
    public String getStatusSummary() {
        if (isActive()) {
            NetworkInfo.DetailedState state = getDetailedState();
            if (state == null) {
                return context.getString(R.string.network_wifi_status_idle);
            }
            if (state == NetworkInfo.DetailedState.CONNECTED) {
                ConnectivityManager cm = (ConnectivityManager)
                        context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                WifiManager wifiManager = (WifiManager)
                        context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                Network network;
                try {
                    Method getCurrentNetwork = WifiManager.class.getMethod("getCurrentNetwork");
                    network = (Network) getCurrentNetwork.invoke(wifiManager);
                } catch (Exception e) {
                    network = null;
                }
                NetworkCapabilities networkCapabilities = cm.getNetworkCapabilities(network);
                if (networkCapabilities != null && !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    return context.getString(R.string.network_wifi_status_connected_no_internet);
                }
            }
            switch (state) {
                case IDLE:
                    return context.getString(R.string.network_wifi_status_idle);
                case SCANNING:
                    return context.getString(R.string.network_wifi_status_scanning);
                case CONNECTING:
                    return context.getString(R.string.network_wifi_status_connecting);
                case AUTHENTICATING:
                    return context.getString(R.string.network_wifi_status_authenticating);
                case OBTAINING_IPADDR:
                    return context.getString(R.string.network_wifi_status_obtaining_ip_address);
                case CONNECTED:
                    isPasswordError = false;
                    return context.getString(R.string.network_wifi_status_connected);
                case SUSPENDED:
                    return context.getString(R.string.network_wifi_status_suspended);
                case DISCONNECTING:
                    return context.getString(R.string.network_wifi_status_disconnecting);
                case DISCONNECTED:
                    return context.getString(R.string.network_wifi_status_disconnected);
                case FAILED:
                    return context.getString(R.string.network_wifi_status_failed);
                case BLOCKED:
                    return context.getString(R.string.network_wifi_status_blocked);
                case VERIFYING_POOR_LINK:
                    return context.getString(R.string.network_wifi_status_verifying_poor_link);
                case CAPTIVE_PORTAL_CHECK:
                default:
                    return context.getString(R.string.network_wifi_status_idle);
            }
        } else if (!isNetworkEnabled()) {
            switch (getNetworkSelectionDisableReason()) {
                case 2:
                    return context.getString(R.string.network_wifi_status_disabled);
                case 3:
                    return context.getString(R.string.network_wifi_status_password_failure);
                case 4:
                case 5:
                    return context.getString(R.string.network_wifi_status_network_failure);
                default:
                    return context.getString(R.string.network_wifi_status_wifi_failure);
            }
        } else if (isSaved()) {
            return context.getString(R.string.network_wifi_status_saved);
        } else {
            return context.getString(R.string.network_wifi_status_idle);
        }
    }

    public int getSignalLevel() {
        if (rssi == Integer.MAX_VALUE || rssi <= -100) {
            return 0;
        }
        return calculateSignalLevel(rssi, SIGNAL_LEVELS);
    }

    public static int calculateSignalLevel(int rssi, int numLevels) {
        if (rssi <= MIN_RSSI) {
            return 0;
        } else if (rssi >= MAX_RSSI) {
            return numLevels - 1;
        } else {
            float inputRange = (MAX_RSSI - MIN_RSSI);
            float outputRange = (numLevels - 1);
            return (int) ((float) (rssi - MIN_RSSI) * outputRange / inputRange);
        }
    }

    /**
     * 获取 PSK 类型
     */
    private static int getPskType(ScanResult result) {
        boolean wpa = result.capabilities.contains("WPA-PSK");
        boolean wpa2 = result.capabilities.contains("WPA2-PSK");
        if (wpa2 && wpa) {
            return PSK_WPA_WPA2;
        } else if (wpa2) {
            return PSK_WPA2;
        } else if (wpa) {
            return PSK_WPA;
        } else {
            return PSK_UNKNOWN;
        }
    }

    /**
     * 根据 ScanResult 获取加密类型
     */
    private static int getSecurity(ScanResult result) {
        if (result.capabilities.contains("WEP")) {
            return SECURITY_WEP;
        } else if (result.capabilities.contains("PSK")) {
            return SECURITY_PSK;
        } else if (result.capabilities.contains("EAP")) {
            return SECURITY_EAP;
        }
        return SECURITY_NONE;
    }

    /**
     * 根据 WifiConfiguration 获取加密类型
     */
    private static int getSecurity(WifiConfiguration config) {
        if (config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK)) {
            return SECURITY_PSK;
        }
        if (config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_EAP) ||
                config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.IEEE8021X)) {
            return SECURITY_EAP;
        }
        return (config.wepKeys[0] != null) ? SECURITY_WEP : SECURITY_NONE;
    }

    /**
     * 获取加密类型的字符串描述
     */
    public static String securityToString(int security, int pskType) {
        if (security == SECURITY_WEP) {
            return "WEP";
        } else if (security == SECURITY_PSK) {
            if (pskType == PSK_WPA) {
                return "WPA";
            } else if (pskType == PSK_WPA2) {
                return "WPA2";
            } else if (pskType == PSK_WPA_WPA2) {
                return "WPA_WPA2";
            }
            return "PSK";
        } else if (security == SECURITY_EAP) {
            return "EAP";
        }
        return "NONE";
    }

    public NetworkInfo.DetailedState getDetailedState() {
        if (networkInfo != null) {
            return networkInfo.getDetailedState();
        }
        return null;
    }

    public boolean update(WifiConfiguration config, WifiInfo info, NetworkInfo networkInfo) {
        boolean reorder = false;
        if (info != null && isInfoForThisAccessPoint(config, info)) {
            reorder = (this.wifiInfo == null);
            this.wifiInfo = info;
            this.networkInfo = networkInfo;
        } else if (this.wifiInfo != null) {
            reorder = true;
            this.wifiInfo = null;
            this.networkInfo = null;
        }
        return reorder;
    }

    public void setWifiConfiguration(WifiConfiguration config) {
        this.wifiConfiguration = config;
        networkId = config.networkId;
    }

    /**
     * 生成 wifiConfiguration
     */
    public void generateNetworkConfig() {
        if (wifiConfiguration != null)
            return;
        wifiConfiguration = new WifiConfiguration();
        wifiConfiguration.SSID = getQuotedSSID();
        switch (security) {
            case SECURITY_NONE:
                wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                break;
            case SECURITY_WEP:
                wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                wifiConfiguration.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
                wifiConfiguration.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
                int length = password.length();
                if ((length == 10 || length == 26 || length == 58) &&
                        password.matches("[0-9A-Fa-f]*")) {
                    wifiConfiguration.wepKeys[0] = password;
                } else {
                    wifiConfiguration.wepKeys[0] = '"' + password + '"';
                }
                break;
            case SECURITY_PSK:
                wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                if (password.matches("[0-9A-Fa-f]{64}")) {
                    wifiConfiguration.preSharedKey = password;
                } else {
                    wifiConfiguration.preSharedKey = '"' + password + '"';
                }
                break;
            case SECURITY_EAP:
                // 暂时忽略
                break;
        }

    }

    /**
     * Identify if this configuration represents a Passpoint network
     */
    private boolean isPasspoint(WifiConfiguration config) {
        return config != null && !TextUtils.isEmpty(config.FQDN) &&
                config.enterpriseConfig != null &&
                config.enterpriseConfig.getEapMethod() != WifiEnterpriseConfig.Eap.NONE;
    }

    /**
     * Return whether the given {@link WifiInfo} is for this access point.
     * If the current AP does not have a network Id then the wifiConfiguration is used to
     * match based on SSID and security.
     */
    private boolean isInfoForThisAccessPoint(WifiConfiguration config, WifiInfo info) {
        if (!isPasspoint(wifiConfiguration) && networkId != INVALID_NETWORK_ID) {
            return networkId == info.getNetworkId();
        } else if (config != null) {
            return matches(config);
        } else {
            return ssid.equals(removeDoubleQuotes(info.getSSID()));
        }
    }

    private boolean matches(WifiConfiguration config) {
        if (isPasspoint(config) && this.wifiConfiguration != null && isPasspoint(wifiConfiguration)) {
            return config.FQDN.equals(this.wifiConfiguration.FQDN);
        } else {
            return ssid.equals(removeDoubleQuotes(config.SSID)) &&
                    security == getSecurity(config) &&
                    this.wifiConfiguration == null;
        }
    }

    public void setPasswordError(boolean passwordError) {
        isPasswordError = passwordError;
        wifiConfiguration = null;
        networkId = INVALID_NETWORK_ID;
    }

    public boolean isPasswordError() {
        return isPasswordError;
    }

    public boolean isSaved() {
        return networkId != INVALID_NETWORK_ID;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * 通过反射获取 NetworkSelectionStatus
     */
    public boolean isNetworkEnabled() {
        boolean enabled = true;
        if (wifiConfiguration != null) {
            try {
                Class NetworkSelectionStatus = Class.forName("android.net.wifi.WifiConfiguration$NetworkSelectionStatus");
                Method getNetworkSelectionStatus = WifiConfiguration.class.getMethod("getNetworkSelectionStatus");
                Object networkSelectionStatus = getNetworkSelectionStatus.invoke(wifiConfiguration);
                Method isNetworkEnabled = NetworkSelectionStatus.getMethod("isNetworkEnabled");
                enabled = (boolean) isNetworkEnabled.invoke(networkSelectionStatus);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return enabled;
    }

    /**
     * 通过反射获取 NetworkSelectionStatus
     */
    public int getNetworkSelectionDisableReason() {
        int status = 0;
        if (wifiConfiguration != null) {
            try {
                Class NetworkSelectionStatus = Class.forName("android.net.wifi.WifiConfiguration$NetworkSelectionStatus");
                Method getNetworkSelectionStatus = WifiConfiguration.class.getMethod("getNetworkSelectionStatus");
                Object networkSelectionStatus = getNetworkSelectionStatus.invoke(wifiConfiguration);
                Method getDisableReason = NetworkSelectionStatus.getMethod("getNetworkSelectionDisableReason");
                status = (int) getDisableReason.invoke(networkSelectionStatus);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return status;
    }

    public boolean isActive() {
        return networkInfo != null &&
                (networkId != INVALID_NETWORK_ID ||
                        networkInfo.getState() != NetworkInfo.State.DISCONNECTED);
    }

    public boolean isActivated() {
        return networkId != INVALID_NETWORK_ID &&
                networkInfo != null &&
                networkInfo.getState() == NetworkInfo.State.CONNECTED;
    }

    /**
     * 添加双引号
     */
    public String getQuotedSSID() {
        return "\"" + ssid + "\"";
    }

    /**
     * 移除双引号
     */
    static String removeDoubleQuotes(String string) {
        if (TextUtils.isEmpty(string)) {
            return "";
        }
        int length = string.length();
        if ((length > 1) && (string.charAt(0) == '"')
                && (string.charAt(length - 1) == '"')) {
            return string.substring(1, length - 1);
        }
        return string;
    }

    @Override
    public int compareTo(@NonNull AccessPoint other) {
        // Active one goes first.
        if (isActive() && !other.isActive()) return -1;
        if (!isActive() && other.isActive()) return 1;
        // Reachable one goes before unreachable one.
        if (this.rssi != Integer.MAX_VALUE && other.rssi == Integer.MAX_VALUE) return -1;
        if (this.rssi == Integer.MAX_VALUE && other.rssi != Integer.MAX_VALUE) return 1;
        // Configured one goes before unConfigured one.
        if (isSaved() && !other.isSaved()) return -1;
        if (!isSaved() && other.isSaved()) return 1;
        int difference = other.getSignalLevel() - this.getSignalLevel();
        if (difference != 0) {
            return difference;
        }
        return this.ssid.compareToIgnoreCase(other.ssid);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        if (obj instanceof AccessPoint) {
            return ssid.equals(((AccessPoint) obj).ssid);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = 0;
        if (wifiInfo != null) result += 13 * wifiInfo.hashCode();
        result += 19 * networkId;
        result += 23 * ssid.hashCode();
        return result;
    }

    protected AccessPoint(Parcel in) {
        ssid = in.readString();
        bssid = in.readString();
        security = in.readInt();
        networkId = in.readInt();
        pskType = in.readInt();
        wifiConfiguration = in.readParcelable(WifiConfiguration.class.getClassLoader());
        rssi = in.readInt();
        wifiInfo = in.readParcelable(WifiInfo.class.getClassLoader());
        networkInfo = in.readParcelable(NetworkInfo.class.getClassLoader());
        isSecured = in.readByte() != 0;
    }

    public static final Creator<AccessPoint> CREATOR = new Creator<AccessPoint>() {
        @Override
        public AccessPoint createFromParcel(Parcel in) {
            return new AccessPoint(in);
        }

        @Override
        public AccessPoint[] newArray(int size) {
            return new AccessPoint[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(ssid);
        dest.writeString(bssid);
        dest.writeInt(security);
        dest.writeInt(networkId);
        dest.writeInt(pskType);
        dest.writeParcelable(wifiConfiguration, flags);
        dest.writeInt(rssi);
        dest.writeParcelable(wifiInfo, flags);
        dest.writeParcelable(networkInfo, flags);
        dest.writeByte((byte) (isSecured ? 1 : 0));
    }
}
