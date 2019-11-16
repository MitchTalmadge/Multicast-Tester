package com.mitchtalmadge.multicasttester.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;

import com.mitchtalmadge.multicasttester.ConsoleFragment;

public class WifiMonitoringReceiver extends BroadcastReceiver {
    private ConsoleFragment consoleFragment;

    public WifiMonitoringReceiver(ConsoleFragment consoleFragment) {
        this.consoleFragment = consoleFragment;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (consoleFragment != null) {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            consoleFragment.log("Checking Wifi...");
            if (!connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected()) {
                consoleFragment.log("Wifi is not enabled!");
                consoleFragment.onWifiDisconnected();
            }
        }
    }
}
