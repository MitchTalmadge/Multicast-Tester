package com.mitchtalmadge.multicasttester.console;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;

import com.mitchtalmadge.multicasttester.console.ConsoleFragment;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.concurrent.atomic.AtomicBoolean;

class MulticastThread extends Thread {

    final AtomicBoolean running = new AtomicBoolean(true);
    final ConsoleFragment fragment;
    final String multicastIP;
    final int multicastPort;
    final Handler handler;

    MulticastSocket multicastSocket;
    private InetAddress inetAddress;

    MulticastThread(String threadName, ConsoleFragment fragment, String multicastIP, int multicastPort, Handler handler) {
        super(threadName);
        this.fragment = fragment;
        this.multicastIP = multicastIP;
        this.multicastPort = multicastPort;
        this.handler = handler;
    }

    @Override
    public void run() {
        try {
            WifiManager wifiManager = (WifiManager) fragment.getActivity().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int wifiIPInt = wifiInfo.getIpAddress();
            byte[] wifiIPByte = new byte[]{
                    (byte) (wifiIPInt & 0xff),
                    (byte) (wifiIPInt >> 8 & 0xff),
                    (byte) (wifiIPInt >> 16 & 0xff),
                    (byte) (wifiIPInt >> 24 & 0xff)};
            this.inetAddress = InetAddress.getByAddress(wifiIPByte);
            NetworkInterface networkInterface = NetworkInterface.getByInetAddress(inetAddress);

            this.multicastSocket = new MulticastSocket(multicastPort);
            multicastSocket.setNetworkInterface(networkInterface);
            multicastSocket.joinGroup(InetAddress.getByName(multicastIP));
            multicastSocket.setSoTimeout(100);
            multicastSocket.setTimeToLive(2);
        } catch (BindException e) {
            handler.post(fragment::stopListening);
            String error = "Error: Cannot bind Address or Port.";
            if (multicastPort < 1024)
                error += "\nTry binding to a port larger than 1024.";
            outputErrorToConsole(error);
        } catch (IOException e) {
            handler.post(fragment::stopListening);
            String error = "Error: Cannot bind Address or Port.\n"
                    + "An error occurred: " + e.getMessage();
            outputErrorToConsole(error);
            e.printStackTrace();
        }
    }

    String getLocalIP() {
        return this.inetAddress.getHostAddress();
    }

    private void outputErrorToConsole(final String errorMessage) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                fragment.outputErrorToConsole(errorMessage);
            }
        });
    }

    void stopRunning() {
        this.running.set(false);
    }
}
