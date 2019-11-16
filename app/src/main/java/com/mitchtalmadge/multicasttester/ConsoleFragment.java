package com.mitchtalmadge.multicasttester;

import android.content.Context;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.mitchtalmadge.multicasttester.receivers.WifiMonitoringReceiver;

import java.util.Objects;
import java.util.logging.Logger;

import static android.content.Context.CONNECTIVITY_SERVICE;

public class ConsoleFragment extends Fragment implements View.OnClickListener {

    private EditText multicastIPField;
    private EditText multicastPortField;
    private TextView consoleView;
    private EditText messageToSendField;

    private boolean isListening = false;
    private boolean isDisplayedInHex = false;
    private boolean errorDisplayed = false;
    private MulticastListenerThread multicastListenerThread;
    private MulticastSenderThread multicastSenderThread;
    private WifiManager.MulticastLock wifiLock;

    private WifiMonitoringReceiver wifiMonitoringReceiver;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_console, container, false);

        // Click listeners
        view.findViewById(R.id.startListeningButton).setOnClickListener(this);
        view.findViewById(R.id.clearConsoleButton).setOnClickListener(this);
        view.findViewById(R.id.sendMessageButton).setOnClickListener(this);
        view.findViewById(R.id.hexDisplayCheckBox).setOnClickListener(this);

        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        View view = Objects.requireNonNull(getView());

        this.multicastIPField = view.findViewById(R.id.multicastIP);
        this.multicastPortField = view.findViewById(R.id.multicastPort);
        this.consoleView = view.findViewById(R.id.consoleTextView);
        this.messageToSendField = view.findViewById(R.id.messageToSend);

        setWifiMonitorRegistered(true);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        setWifiMonitorRegistered(false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isListening)
            stopListening();
        stopThreads();
    }

    private void setWifiMonitorRegistered(boolean registered) {
        if (registered) {
            if (this.wifiMonitoringReceiver != null)
                getActivity().unregisterReceiver(this.wifiMonitoringReceiver);
            this.wifiMonitoringReceiver = new WifiMonitoringReceiver(this);
            getActivity().registerReceiver(this.wifiMonitoringReceiver, new IntentFilter("android.net.wifi.STATE_CHANGE"));
        } else {
            if (this.wifiMonitoringReceiver != null) {
                getActivity().unregisterReceiver(this.wifiMonitoringReceiver);
                this.wifiMonitoringReceiver = null;
            }
        }
    }

    @Override
    public void onClick(View view) {
        // Hide the keyboard
        InputMethodManager inputMethodManager = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);

        switch (view.getId()) {
            case R.id.startListeningButton:
                if (this.isListening) {
                    stopListening();
                } else {
                    startListening();
                }
                break;
            case R.id.clearConsoleButton:
                this.clearConsole();
                break;
            case R.id.sendMessageButton:
                sendMulticastMessage(getMessageToSend());
                break;
            case R.id.hexDisplayCheckBox:
                this.isDisplayedInHex = ((CheckBox) view).isChecked();
                break;
        }
    }

    private void startListening() {
        if (!isListening) {
            ConnectivityManager connectivityManager = (ConnectivityManager) getActivity().getSystemService(CONNECTIVITY_SERVICE);

            if (connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected()) {
                if (validateInputFields()) {
                    setWifiLockAcquired(true);

                    if (errorDisplayed) {
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
        this.log("Sending Message: " + message);
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

            WifiManager wifi = (WifiManager) getActivity().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
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
        View view = Objects.requireNonNull(getView());

        ((Button) view.findViewById(R.id.startListeningButton)).setText((isListening) ? R.string.stop_listening : R.string.start_listening);
        ((MaterialButton) view.findViewById(R.id.startListeningButton))
                .setIcon(isListening
                        ? ContextCompat.getDrawable(getActivity().getApplicationContext(), R.drawable.ic_stop_black_24dp)
                        : ContextCompat.getDrawable(getActivity().getApplicationContext(), R.drawable.ic_play_arrow));

        view.findViewById(R.id.sendMessageButton).setEnabled(isListening);
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
        if (errorDisplayed) {
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
