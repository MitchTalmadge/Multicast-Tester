package net.liveforcode.multicasttester;

import android.content.Context;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import net.liveforcode.multicasttester.receivers.WifiMonitoringReceiver;

import java.util.logging.Logger;

public class MainActivity extends AppCompatActivity {

    private EditText multicastIPField;
    private EditText multicastPortField;
    private TextView consoleView;
    private EditText messageToSendField;

    private Toolbar toolbar;

    private boolean isListening = false;
    private boolean isDisplayedInHex = false;
    private boolean errorDisplayed = false;
    private MulticastListenerThread multicastListenerThread;
    private MulticastSenderThread multicastSenderThread;
    private WifiManager.MulticastLock wifiLock;

    private WifiMonitoringReceiver wifiMonitoringReceiver;

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

        setWifiMonitorRegistered(true);
    }

    @Override
    protected void onStop() {
        super.onStop();

        setWifiMonitorRegistered(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isListening)
            stopListening();
        stopThreads();
    }

    private void setWifiMonitorRegistered(boolean registered) {
        if (registered) {
            if (this.wifiMonitoringReceiver != null)
                unregisterReceiver(this.wifiMonitoringReceiver);
            this.wifiMonitoringReceiver = new WifiMonitoringReceiver(this);
            registerReceiver(this.wifiMonitoringReceiver, new IntentFilter("android.net.wifi.STATE_CHANGE"));
        } else {
            if (this.wifiMonitoringReceiver != null) {
                unregisterReceiver(this.wifiMonitoringReceiver);
                this.wifiMonitoringReceiver = null;
            }
        }
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
        // Hide the keyboard
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);

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

    public void onToggleHexCheckbox(View view) {
        if(view.getId() == R.id.hexDisplayCheckBox) {
            this.isDisplayedInHex = ((CheckBox)view).isChecked();
        }
    }

    private void startListening() {
        if (!isListening) {
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

            if (connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected()) {
                if (validateInputFields()) {
                    setWifiLockAcquired(true);

                    if(errorDisplayed) {
                        clearConsole();
                        errorDisplayed = false;
                    }

                    this.multicastListenerThread = new MulticastListenerThread(this, getMulticastIP(), getMulticastPort());
                    multicastListenerThread.start();

                    isListening = true;
                    updateButtonStates();

                }
            } else {
                outputErrorToConsole("Error: You are not connected to a WiFi network!");
            }
        }
    }

    void stopListening() {
        if (isListening) {
            isListening = false;
            updateButtonStates();

            stopThreads();
            setWifiLockAcquired(false);
        }
    }

    private void sendMulticastMessage(String message) {
        this.log("Sending Message: "+message);
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

    public boolean isDisplayedInHex() {
        return isDisplayedInHex;
    }

    private void clearConsole() {
        this.consoleView.setText("");
        this.consoleView.setTextColor(getResources().getColor(R.color.colorPrimaryDark));
    }

    public void outputTextToConsole(String message) {
        if(errorDisplayed) {
            clearConsole();
            errorDisplayed = false;
        }

        this.consoleView.append(message);

        ScrollView scrollView = ((ScrollView) this.consoleView.getParent());
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    public void outputErrorToConsole(String errorMessage) {
        clearConsole();
        outputTextToConsole(errorMessage);
        this.consoleView.setTextColor(Color.RED);
        errorDisplayed = true;
    }

    public void log(String message) {
        Logger.getLogger("MulticastTester").info(message);
    }

    public void onWifiDisconnected() {
        if (isListening) {
            stopListening();
            outputErrorToConsole("Error: WiFi has been disconnected. Listening has stopped.");
        }
    }
}
