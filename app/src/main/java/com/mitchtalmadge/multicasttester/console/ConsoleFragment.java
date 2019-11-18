package com.mitchtalmadge.multicasttester.console;

import android.content.Context;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.mitchtalmadge.multicasttester.R;

import java.util.Objects;

import static android.content.Context.CONNECTIVITY_SERVICE;

public class ConsoleFragment extends Fragment implements View.OnClickListener, View.OnKeyListener {

    private MaterialButton startListeningButton;
    private TextView wifiNoticeLabel;
    private EditText multicastIPField;
    private EditText multicastPortField;
    private TextView consoleView;
    private EditText messageToSendField;
    private Button sendMessageButton;

    private boolean isListening = false;
    private boolean isDisplayedInHex = false;
    private boolean errorDisplayed = false;
    private MulticastListenerThread multicastListenerThread;
    private MulticastSenderThread multicastSenderThread;
    private WifiManager.MulticastLock wifiMulticastLock;

    private ConsoleNetworkCallback consoleNetworkCallback;
    private boolean wifiConnected = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_console, container, false);

        // Assignments
        this.startListeningButton = view.findViewById(R.id.startListeningButton);
        this.wifiNoticeLabel = view.findViewById(R.id.wifiNoticeLabel);
        this.multicastIPField = view.findViewById(R.id.multicastIP);
        this.multicastPortField = view.findViewById(R.id.multicastPort);
        this.consoleView = view.findViewById(R.id.consoleTextView);
        this.messageToSendField = view.findViewById(R.id.messageToSend);
        this.sendMessageButton = view.findViewById(R.id.sendMessageButton);

        // Click listeners
        this.startListeningButton.setOnClickListener(this);
        view.findViewById(R.id.clearConsoleButton).setOnClickListener(this);
        this.sendMessageButton.setOnClickListener(this);
        view.findViewById(R.id.hexDisplayCheckBox).setOnClickListener(this);

        this.multicastIPField.setOnKeyListener(this);
        this.multicastPortField.setOnKeyListener(this);

        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);


        this.consoleNetworkCallback = new ConsoleNetworkCallback();
        ConnectivityManager connectivityManager = Objects.requireNonNull((ConnectivityManager) Objects.requireNonNull(getActivity()).getSystemService(CONNECTIVITY_SERVICE));
        connectivityManager.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), consoleNetworkCallback);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        ConnectivityManager connectivityManager = Objects.requireNonNull((ConnectivityManager) Objects.requireNonNull(getActivity()).getSystemService(CONNECTIVITY_SERVICE));
        connectivityManager.unregisterNetworkCallback(this.consoleNetworkCallback);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isListening)
            stopListening();
        stopThreads();
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

    @Override
    public boolean onKey(View view, int i, KeyEvent keyEvent) {
        updateButtonStates();
        return false;
    }

    private void startListening() {
        if (validateInputFields()) {
            acquireWifiMulticastLock();

            if (errorDisplayed) {
                clearConsole();
                errorDisplayed = false;
            }

            this.multicastListenerThread = new MulticastListenerThread(this, getMulticastIP(), getMulticastPort());
            multicastListenerThread.start();

            isListening = true;
            updateButtonStates();

        }
    }

    void stopListening() {
        if (isListening) {
            isListening = false;
            stopThreads();
            releaseWifiMulticastLock();
        }

        updateButtonStates();
    }

    private void sendMulticastMessage(String message) {
        Log.v(getClass().getName(), "Sending Message: " + message);
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

    private void acquireWifiMulticastLock() {
        releaseWifiMulticastLock();

        WifiManager wifi = (WifiManager) Objects.requireNonNull(getActivity()).getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifi != null) {
            this.wifiMulticastLock = wifi.createMulticastLock("MulticastTester");
            wifiMulticastLock.acquire();
        }
    }

    private void releaseWifiMulticastLock() {
        if (wifiMulticastLock != null && wifiMulticastLock.isHeld())
            wifiMulticastLock.release();
    }

    private void updateButtonStates() {
        boolean startListeningEnabled = true;

        // Wifi State
        if (!wifiConnected)
            startListeningEnabled = false;
        this.wifiNoticeLabel.setVisibility(wifiConnected
                ? View.GONE
                : View.VISIBLE);

        // Check entry fields
        if (this.multicastIPField.getText().length() == 0 || this.multicastPortField.getText().length() == 0)
            startListeningEnabled = false;

        this.startListeningButton.setEnabled(startListeningEnabled);
        this.startListeningButton.setText((isListening)
                ? R.string.stop_listening
                : R.string.start_listening);
        this.startListeningButton.setIcon(isListening
                ? ContextCompat.getDrawable(getActivity().getApplicationContext(), R.drawable.ic_stop)
                : ContextCompat.getDrawable(getActivity().getApplicationContext(), R.drawable.ic_play_arrow));


        this.sendMessageButton.setEnabled(isListening);
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

    private class ConsoleNetworkCallback extends ConnectivityManager.NetworkCallback {

        @Override
        public void onAvailable(@NonNull Network network) {
            wifiConnected = true;
            Objects.requireNonNull(getActivity()).runOnUiThread(ConsoleFragment.this::updateButtonStates);
        }

        @Override
        public void onLost(@NonNull Network network) {
            wifiConnected = false;
            if (isListening) {
                Objects.requireNonNull(getActivity()).runOnUiThread(ConsoleFragment.this::stopListening);
                Toast.makeText(getContext(), R.string.connectivity_lost, Toast.LENGTH_LONG).show();
            }
        }
    }

}
