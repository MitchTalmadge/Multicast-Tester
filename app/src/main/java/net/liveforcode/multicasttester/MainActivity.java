package net.liveforcode.multicasttester;

import android.app.Activity;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setIcon(R.mipmap.ic_launcher);

        setContentView(R.layout.activity_main);
        WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (wifi != null) {
            WifiManager.MulticastLock lock = wifi.createMulticastLock("multicast-tester");
            lock.acquire();
        }
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
        if (this.multicastListenerThread != null)
            this.multicastListenerThread.stopRunning();
        if (this.multicastSenderThread != null)
            this.multicastSenderThread.interrupt();
        isListening = false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        return super.onOptionsItemSelected(item);
    }

    public void onButton(View view) {
        if (view.getId() == R.id.startListeningButton) {
            if (this.isListening) {
                if (this.multicastListenerThread != null)
                    this.multicastListenerThread.stopRunning();
                isListening = false;
                ((Button) findViewById(R.id.startListeningButton)).setText(R.string.start_listening);
                findViewById(R.id.clearConsoleButton).setEnabled(false);
                findViewById(R.id.sendMessageButton).setEnabled(false);
                this.clearConsole();
            } else {
                if (!this.multicastIPField.getText().toString().isEmpty() && !this.multicastPortField.getText().toString().isEmpty()) {

                    this.multicastListenerThread = new MulticastListenerThread(this, this.consoleView, getMulticastIP(), getMulticastPort());
                    multicastListenerThread.start();
                    isListening = true;
                    ((Button) findViewById(R.id.startListeningButton)).setText(R.string.stop_listening);
                    findViewById(R.id.clearConsoleButton).setEnabled(true);
                    findViewById(R.id.sendMessageButton).setEnabled(true);
                    this.clearConsole();
                }
            }
        } else if (view.getId() == R.id.clearConsoleButton) {
            this.clearConsole();
        } else if (view.getId() == R.id.sendMessageButton) {
            if (this.isListening) {
                this.multicastSenderThread = new MulticastSenderThread(this, getMessageToSend(), getMulticastIP(), getMulticastPort());
                multicastSenderThread.start();
            }
        }
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
        Logger.getLogger("multicast-tester").info(message);
    }

}
