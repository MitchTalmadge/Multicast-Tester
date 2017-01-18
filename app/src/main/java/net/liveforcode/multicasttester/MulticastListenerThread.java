package net.liveforcode.multicasttester;

import android.os.Handler;
import android.widget.TextView;

import java.io.IOException;
import java.net.DatagramPacket;

public class MulticastListenerThread extends MulticastThread {

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    private TextView consoleView;
    private DatagramPacket packet;

    public MulticastListenerThread(MainActivity activity, String multicastIP, int multicastPort, TextView consoleView) {
        super("MulticastListenerThread", activity, multicastIP, multicastPort, new Handler());
        this.consoleView = consoleView;
    }

    @Override
    public void run() {
        super.run();

        this.packet = new DatagramPacket(new byte[512], 512);

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

            if (this.activity.isDisplayedInHex()) {
                for (byte b : new String(packet.getData()).trim().getBytes()) {
                    data += "0x" + Integer.toHexString(b) + " ";
                }
            } else
                data = new String(packet.getData()).trim();

            activity.log("Received! " + data);

            final String consoleMessage = "[" + ((getLocalIP().equals(packet.getAddress().getHostAddress())) ? "You" : packet.getAddress().getHostAddress()) + "] " + data + "\n";

            this.handler.post(new Runnable() {
                @Override
                public void run() {
                    consoleView.append(consoleMessage);
                }
            });
        }
        if (multicastSocket != null)
            this.multicastSocket.close();
    }

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

}
