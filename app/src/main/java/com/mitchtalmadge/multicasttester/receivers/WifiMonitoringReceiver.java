package com.mitchtalmadge.multicasttester.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;

import com.mitchtalmadge.multicasttester.MainActivity;

public class WifiMonitoringReceiver extends BroadcastReceiver {
    private MainActivity mainActivity;

    public WifiMonitoringReceiver(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mainActivity != null) {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            mainActivity.log("Checking Wifi...");
            if (!connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected()) {
                mainActivity.log("Wifi is not enabled!");
                mainActivity.onWifiDisconnected();
            }
        }
    }
}
