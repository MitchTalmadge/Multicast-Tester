package net.liveforcode.multicasttester;

import android.content.Context;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.logging.Logger;

public class MainActivity extends AppCompatActivity {

    private EditText multicastIPField;
    private EditText multicastPortField;
    private TextView consoleView;
    private EditText messageToSendField;

    private Toolbar toolbar;

    private boolean isListening = false;
    private MulticastListenerThread multicastListenerThread;
    private MulticastSenderThread multicastSenderThread;
    private WifiManager.MulticastLock wifiLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        toolbar.setLogo(R.mipmap.ic_launcher);
    }

    @Override
    protected void onStart() {
        super.onStart();
        this.multicastIPField = (EditText) findViewById(R.id.multicastIP);
        this.multicastPortField = (EditText) findViewById(R.id.multicastPort);
        this.consoleView = (TextView) findViewById(R.id.consoleTextView);
        this.messageToSendField = (EditText) findViewById(R.id.messageToSend);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isListening)
            stopListening();
        stopThreads();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    public void onButton(View view) {
        if (view.getId() == R.id.startListeningButton) {
            if (this.isListening) {
                stopListening();
            } else {
                startListening();
            }
        } else if (view.getId() == R.id.clearConsoleButton) {
            this.clearConsole();
        } else if (view.getId() == R.id.sendMessageButton) {
            sendMulticastMessage(getMessageToSend());
        }
    }

    private void startListening() {
        if (validateInputFields()) {
            setWifiLockAcquired(true);

            this.multicastListenerThread = new MulticastListenerThread(this, getMulticastIP(), getMulticastPort(), this.consoleView);
            multicastListenerThread.start();

            isListening = true;
            updateButtonStates();
            this.clearConsole();
        }
    }

    private void stopListening() {
        isListening = false;
        updateButtonStates();

        stopThreads();
        setWifiLockAcquired(false);
        clearConsole();
    }

    private void sendMulticastMessage(String message) {
        if (this.isListening) {
            this.multicastSenderThread = new MulticastSenderThread(this, getMulticastIP(), getMulticastPort(), message);
            multicastSenderThread.start();
        }
    }

    private void stopThreads() {
        if (this.multicastListenerThread != null)
            this.multicastListenerThread.stopRunning();
        if (this.multicastSenderThread != null)
            this.multicastSenderThread.interrupt();
    }

    private void setWifiLockAcquired(boolean acquired) {
        if (acquired) {
            if (wifiLock != null && wifiLock.isHeld())
                wifiLock.release();

            WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                this.wifiLock = wifi.createMulticastLock("MulticastTester");
                wifiLock.acquire();
            }
        } else {
            if (wifiLock != null && wifiLock.isHeld())
                wifiLock.release();
        }
    }

    private void updateButtonStates() {
        ((Button) findViewById(R.id.startListeningButton)).setText((isListening) ? R.string.stop_listening : R.string.start_listening);
        findViewById(R.id.clearConsoleButton).setEnabled(isListening);
        findViewById(R.id.sendMessageButton).setEnabled(isListening);
    }

    private boolean validateInputFields() {
        //Validate IP
        if (multicastIPField.getText().toString().isEmpty()) {
            outputErrorToConsole("Error: Multicast IP is Empty!");
            return false;
        } else {
            String[] splitIP = multicastIPField.getText().toString().split("\\.");
            if (splitIP.length != 4) {
                outputErrorToConsole("Error: Please enter an IP Address in this format: xxx.xxx.xxx.xxx\n" +
                        "Each 'xxx' segment may range from 0 to 255.");
                return false;
            }
            for (int i = 0; i < splitIP.length; i++) {
                try {
                    int intSegment = Integer.parseInt(splitIP[i]);
                    if (i == 0 && (intSegment < 224 || intSegment > 239)) {
                        outputErrorToConsole("Error: Multicast IPs may only range from:\n" +
                                "224.0.0.0\n" +
                                "to\n" +
                                "239.255.255.255");
                        return false;
                    }
                    if (intSegment > 255) {
                        outputErrorToConsole("Error: Please enter an IP Address in this format: xxx.xxx.xxx.xxx\n" +
                                "Each 'xxx' segment may range from 0 to 255.");
                        return false;
                    }
                } catch (NumberFormatException e) {
                    outputErrorToConsole("Error: Multicast IP must contain only decimals and numbers.");
                    return false;
                }
            }
        }

        //Validate Port
        try {
            if (multicastPortField.getText().toString().isEmpty()) {
                outputErrorToConsole("Error: Multicast Port is Empty!");
                return false;
            } else if (Integer.parseInt(multicastPortField.getText().toString()) > 65535) {
                outputErrorToConsole("Error: Multicast Port must be between 0 and 65535.");
                return false;
            }
        } catch (NumberFormatException e) {
            outputErrorToConsole("Error: Multicast Port may only contain numbers.");
            return false;
        }

        return true; //Everything checks out if we got this far. Okay to continue.
    }

    public String getMulticastIP() {
        return this.multicastIPField.getText().toString();
    }

    public int getMulticastPort() {
        return Integer.parseInt(this.multicastPortField.getText().toString());
    }

    public String getMessageToSend() {
        return this.messageToSendField.getText().toString();
    }

    private void clearConsole() {
        log("Clearing Console");
        this.consoleView.setText("");
        this.consoleView.setTextColor(getResources().getColor(R.color.colorPrimaryDark));
    }

    public void outputErrorToConsole(String errorMessage) {
        clearConsole();
        this.consoleView.setTextColor(Color.RED);
        this.consoleView.setText(errorMessage);
    }

    public void log(String message) {
        Logger.getLogger("MulticastTester").info(message);
    }

}
