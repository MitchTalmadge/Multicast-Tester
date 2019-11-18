package com.mitchtalmadge.multicasttester.console;

import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.util.Arrays;

class MulticastListenerThread extends MulticastThread {

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    MulticastListenerThread(ConsoleFragment activity, String multicastIP, int multicastPort) {
        super("MulticastListenerThread", activity, multicastIP, multicastPort, new Handler());
    }

    @Override
    public void run() {
        super.run();

        DatagramPacket packet = new DatagramPacket(new byte[512], 512);

        while (running.get()) {
            packet.setData(new byte[1024]);

            try {
                if (multicastSocket != null)
                    multicastSocket.receive(packet);
                else
                    break;
            } catch (IOException ignored) {
                continue;
            }

            String data = "";

            if (this.fragment.isDisplayedInHex()) {
                byte[] trimmedData = Arrays.copyOf(packet.getData(), packet.getLength());
                data += bytesToHex(trimmedData);
            } else
                data = new String(packet.getData()).trim();

            Log.v(getClass().getName(), "Received Message: " + data);

            final String consoleMessage = "[" + ((getLocalIP().equals(packet.getAddress().getHostAddress())) ? "You" : packet.getAddress().getHostAddress()) + "] " + data + "\n";

            this.handler.post(() -> fragment.outputTextToConsole(consoleMessage));
        }
        if (multicastSocket != null)
            this.multicastSocket.close();
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }

        StringBuilder hexStringBuilder = new StringBuilder();
        for (int i = 0; i < hexChars.length; i += 2) {
            hexStringBuilder.append("0x").append(hexChars[i]).append(hexChars[i + 1]).append(" ");
        }

        return hexStringBuilder.toString();
    }

}
