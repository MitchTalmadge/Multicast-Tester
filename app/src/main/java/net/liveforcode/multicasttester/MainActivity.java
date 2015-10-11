package net.liveforcode.multicasttester;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.logging.Logger;

public class MainActivity extends ActionBarActivity {

    private EditText multicastIPField;
    private EditText multicastPortField;
    private TextView consoleView;
    private EditText messageToSendField;

    private boolean isListening = false;
    private MulticastListenerThread multicastListenerThread;
    private MulticastSenderThread multicastSenderThread;
    private WifiManager.MulticastLock wifiLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setIcon(R.mipmap.ic_launcher);

        setContentView(R.layout.activity_main);
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
        if (!this.multicastIPField.getText().toString().isEmpty() && !this.multicastPortField.getText().toString().isEmpty()) {
            setWifiLockAcquired(true);

            this.multicastListenerThread = new MulticastListenerThread(this, this.consoleView, getMulticastIP(), getMulticastPort());
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
            this.multicastSenderThread = new MulticastSenderThread(this, message, getMulticastIP(), getMulticastPort());
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
            if (wifiLock != null)
                wifiLock.release();

            WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                this.wifiLock = wifi.createMulticastLock("MulticastTester");
                wifiLock.acquire();
            }
        } else {
            if (wifiLock != null)
                wifiLock.release();
        }
    }

    private void updateButtonStates() {
        ((Button) findViewById(R.id.startListeningButton)).setText((isListening) ? R.string.stop_listening : R.string.start_listening);
        findViewById(R.id.clearConsoleButton).setEnabled(isListening);
        findViewById(R.id.sendMessageButton).setEnabled(isListening);
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
    }

    public void log(String message) {
        Logger.getLogger("MulticastTester").info(message);
    }

}
