package com.example.app1;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MenuActivity extends AppCompatActivity {

    private static final String TAG = "MenuActivity";

    private static final long SCAN_PERIOD = 5000; // etwas länger scannen
    private static final int REQ_BT_PERMISSIONS = 1001;
    private static final int REQ_ENABLE_BT = 1002;

    private Handler handler;
    private boolean scanning;
    private LeDeviceListAdapter leDeviceListAdapter;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private static BluetoothGatt bluetoothGatt;
    private BluetoothManager bluetoothManager;

    private Button scanbutton;
    private boolean first;
    private boolean connection;

    private BluetoothGattService service;
    private static BluetoothGattCharacteristic startStopChar;
    private static BluetoothGattCharacteristic tempProfileChar;
    private static BluetoothGattCharacteristic tareChar;
    private static BluetoothGattCharacteristic thresholdChar;

    private static final UUID SERVICE_UUID =
            UUID.fromString("00010203-0405-0607-0809-0a0b0c0d0e0f");
    private static final UUID NOTIFY_UUID =
            UUID.fromString("00010203-0405-0607-0809-0a0b0c0d0e12");
    private static final UUID STARTSTOP_UUID =
            UUID.fromString("00010203-0405-0607-0809-0a0b0c0d0e10");
    private static final UUID TEMPPROFILE_UUID =
            UUID.fromString("00010203-0405-0607-0809-0a0b0c0d0e11");
    private static final UUID TARE_UUID =
            UUID.fromString("00010203-0405-0607-0809-0a0b0c0d0e13");
    private static final UUID THRESHOLD_UUID =
            UUID.fromString("00010203-0405-0607-0809-0a0b0c0d0e14");
    private static final UUID CLIENT_CHAR_CONFIG_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // ---- Lifecycle ----------------------------------------------------------

    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        first = true;
        connection = false;
        setContentView(R.layout.menu);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        scanbutton = findViewById(R.id.scanbutton);

        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;

        if (bluetoothAdapter == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(MenuActivity.this);
            builder.setMessage("Device doesn't support Bluetooth.");
            builder.setTitle("No Support!");
            builder.setCancelable(false);
            builder.setPositiveButton("OK", (dialog, which) -> finish());
            builder.create().show();
            return;
        }

        handler = new Handler();
        leDeviceListAdapter = new LeDeviceListAdapter();
        scanning = false;

        // Scan-Button
        scanbutton.setOnClickListener(v -> {
            scanbutton.setText("Scanning...");
            scanbutton.setEnabled(false);
            ensureBtOnAndPermissionsThenScan();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Intent-Extras aus MainActivity
        this.connection = getIntent().getBooleanExtra("connected", false);
        boolean toSend = getIntent().getBooleanExtra("send", false);
        int start = getIntent().getIntExtra("start", 0);
        float[] xValues = getIntent().getFloatArrayExtra("xValues");
        float[] yValues = getIntent().getFloatArrayExtra("yValues");

        boolean tare = getIntent().getBooleanExtra("tare", false);
        byte tareValue = getIntent().getByteExtra("tareValue", (byte) 0);

        boolean threshold = getIntent().getBooleanExtra("threshold", false);
        int thresholdValue = getIntent().getIntExtra("thresholdValue", 0);

        // UI-Text aktualisieren
        if (connection) {
            scanbutton.setText("connected - Start Scan");
        } else {
            scanbutton.setText("Start Scan");
        }

        // Ab hier immer auf vorhandene GATT/Chars prüfen
        if (threshold && bluetoothGatt != null && thresholdChar != null) {
            byte[] value = new byte[4];
            value[0] = (byte) (thresholdValue & 0xFF);
            value[1] = (byte) ((thresholdValue >> 8) & 0xFF);
            value[2] = (byte) ((thresholdValue >> 16) & 0xFF);
            value[3] = (byte) ((thresholdValue >> 24) & 0xFF);
            thresholdChar.setValue(value);
            bluetoothGatt.writeCharacteristic(thresholdChar);
            finish();
        }

        if (tare && bluetoothGatt != null && tareChar != null) {
            tareChar.setValue(new byte[]{tareValue});
            bluetoothGatt.writeCharacteristic(tareChar);
            finish();
        }

        if (start == 1 && bluetoothGatt != null && startStopChar != null) {
            startStopChar.setValue(new byte[]{(byte) 1});
            bluetoothGatt.writeCharacteristic(startStopChar);
            finish();
        } else if (start == 2 && bluetoothGatt != null && startStopChar != null) {
            startStopChar.setValue(new byte[]{(byte) 0});
            bluetoothGatt.writeCharacteristic(startStopChar);
            finish();
        }

        if (toSend && bluetoothGatt != null && tempProfileChar != null && xValues != null && yValues != null) {
            float[] profileTime = xValues;
            float[] profileTemp = yValues;
            if (profileTime.length >= 6 && profileTemp.length >= 6) {
                byte[] data = new byte[24];
                for (int i = 0; i < 6; i++) {
                    int time = (int) profileTime[i];
                    int temp = (int) (profileTemp[i] * 10);
                    data[i * 4] = (byte) ((time >> 8) & 0xFF);
                    data[i * 4 + 1] = (byte) (time & 0xFF);
                    data[i * 4 + 2] = (byte) ((temp >> 8) & 0xFF);
                    data[i * 4 + 3] = (byte) (temp & 0xFF);
                }
                tempProfileChar.setValue(data);
                bluetoothGatt.writeCharacteristic(tempProfileChar);
            }
            scanbutton.setText("sent - Start Scan");
            finish();
        }
    }

    // ---- Permissions & Bluetooth --------------------------------------------

    private boolean hasBtPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestBtPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQ_BT_PERMISSIONS);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    REQ_BT_PERMISSIONS);
        }
    }

    private void ensureBtOnAndPermissionsThenScan() {
        if (bluetoothAdapter == null) return;

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQ_ENABLE_BT);
            return;
        }

        if (!hasBtPermissions()) {
            requestBtPermissions();
            return;
        }

        startScan();
    }

    private void ensureScanner() {
        if (bluetoothAdapter == null) return;
        if (bluetoothLeScanner == null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            if (bluetoothLeScanner == null) {
                Log.e(TAG, "BluetoothLeScanner ist null");
            }
        }
    }

    private void startScan() {
        if (scanning) return;
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth deaktiviert oder Adapter null.");
            scanbutton.setEnabled(true);
            scanbutton.setText("Start Scan");
            return;
        }

        if (!hasBtPermissions()) {
            requestBtPermissions();
            scanbutton.setEnabled(true);
            scanbutton.setText("Start Scan");
            return;
        }

        ensureScanner();
        if (bluetoothLeScanner == null) {
            scanbutton.setEnabled(true);
            scanbutton.setText("Start Scan");
            return;
        }

        // Scan nach einer Zeit stoppen
        handler.postDelayed(() -> {
            scanning = false;
            if (bluetoothLeScanner != null) {
                bluetoothLeScanner.stopScan(leScanCallback);
            }
            scanbutton.setVisibility(View.GONE);

            ListView listView = findViewById(R.id.deviceList);
            listView.setAdapter(leDeviceListAdapter);

            listView.setOnItemClickListener((parent, view, position, id) -> {
                BluetoothDevice selectedItem = (BluetoothDevice) parent.getItemAtPosition(position);
                if (selectedItem == null) return;

                scanbutton.setVisibility(View.VISIBLE);
                scanbutton.setEnabled(true);
                scanbutton.setText(selectedItem.getAddress());

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        ActivityCompat.checkSelfPermission(MenuActivity.this,
                                Manifest.permission.BLUETOOTH_CONNECT)
                                != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MenuActivity.this,
                            new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                            REQ_BT_PERMISSIONS);
                    return;
                }

                connect(selectedItem.getAddress());
            });

        }, SCAN_PERIOD);

        scanning = true;
        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        bluetoothLeScanner.startScan(null, scanSettings, leScanCallback);
    }

    private void stopScan() {
        if (!scanning) return;
        scanning = false;
        if (bluetoothLeScanner != null) {
            bluetoothLeScanner.stopScan(leScanCallback);
        }
        scanbutton.setText("Not Scanning");
        scanbutton.setEnabled(true);
        scanbutton.setVisibility(View.VISIBLE);
    }

    // ---- GATT / Connection --------------------------------------------------

    public void setGatt(BluetoothGatt gatt) {
        bluetoothGatt = gatt;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public boolean connect(final String address) {
        try {
            if (bluetoothAdapter == null) return false;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(this,
                            Manifest.permission.BLUETOOTH_CONNECT)
                            != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                        REQ_BT_PERMISSIONS);
                return false;
            }

            final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
            bluetoothGatt = device.connectGatt(this, false, bluetoothGattCallback);
            return true;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "connect: IllegalArgumentException", e);
            return false;
        }
    }

    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(@NonNull BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connection = true;
                runOnUiThread(() -> scanbutton.setText("connected"));
                // Services asynchron entdecken
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connection = false;
                Intent intent = new Intent("ACTION_DATA_AVAILABLE");
                intent.putExtra("connection", false);
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
                runOnUiThread(() -> scanbutton.setText("disconnected"));
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onServicesDiscovered(@NonNull BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                service = gatt.getService(SERVICE_UUID);
                if (service == null) {
                    Log.e(TAG, "Service nicht gefunden");
                    return;
                }

                BluetoothGattCharacteristic characteristic =
                        service.getCharacteristic(NOTIFY_UUID);

                startStopChar = service.getCharacteristic(STARTSTOP_UUID);
                tempProfileChar = service.getCharacteristic(TEMPPROFILE_UUID);
                tareChar = service.getCharacteristic(TARE_UUID);
                thresholdChar = service.getCharacteristic(THRESHOLD_UUID);

                if (characteristic == null) {
                    Log.e(TAG, "Notify-Characteristic nicht gefunden");
                    return;
                }

                boolean notifSet =
                        gatt.setCharacteristicNotification(characteristic, true);

                BluetoothGattDescriptor descriptor =
                        characteristic.getDescriptor(CLIENT_CHAR_CONFIG_UUID);
                if (descriptor != null) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);
                } else {
                    Log.e(TAG, "CCCD-Descriptor nicht gefunden");
                }

                setGatt(gatt);
            } else {
                Log.e(TAG, "onServicesDiscovered: status=" + status);
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt,
                                            @NonNull BluetoothGattCharacteristic characteristic) {

            byte[] data = characteristic.getValue();

            // mindestens 9 Bytes: [0..8]
            if (data != null && data.length >= 9) {
                boolean running = ((data[0] & 0x02) != 0);
                boolean profileStarted = ((data[0] & 0x01) != 0);
                int tempRawUnsigned = ((data[1] & 0xFF) << 8) | (data[2] & 0xFF);
                int tempRawSigned = tempRawUnsigned >= 0x8000 ? tempRawUnsigned - 0x10000 : tempRawUnsigned;
                double temperature = tempRawSigned / 10.0;
                int force = ((data[3] & 0xFF) << 24)
                        | ((data[4] & 0xFF) << 16)
                        | ((data[5] & 0xFF) << 8)
                        | (data[6] & 0xFF);
                int currentTime = ((data[7] & 0xFF) << 8) | (data[8] & 0xFF);

                Intent intent = new Intent("ACTION_DATA_AVAILABLE");
                intent.putExtra("temp", temperature);
                intent.putExtra("force", force);
                intent.putExtra("currentTime", currentTime);
                intent.putExtra("running", running);
                intent.putExtra("profileStarted", profileStarted);
                intent.putExtra("connection", connection);

                LocalBroadcastManager.getInstance(getApplicationContext())
                        .sendBroadcast(intent);

                if (first) {
                    Intent activityintent = new Intent(MenuActivity.this, MainActivity.class);
                    startActivity(activityintent);
                    first = false;
                }
            } else {
                Log.w(TAG, "onCharacteristicChanged: zu wenig Daten (" +
                        (data != null ? data.length : 0) + ")");
            }
        }

        @Override
        public void onCharacteristicWrite(@NonNull BluetoothGatt gatt,
                                          @NonNull BluetoothGattCharacteristic characteristic,
                                          int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Char write successful: " + characteristic.getUuid());
            } else {
                Log.e(TAG, "Char write failed: " + characteristic.getUuid()
                        + " status=" + status);
            }
        }
    };

    // ---- Scan Callback ------------------------------------------------------

    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, @NonNull ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device != null) {
                leDeviceListAdapter.addDevice(device);
                leDeviceListAdapter.notifyDataSetChanged();
            }
        }
    };

    // ---- Adapter ------------------------------------------------------------

    private class LeDeviceListAdapter extends BaseAdapter {
        private final ArrayList<BluetoothDevice> mLeDevices;
        private final LayoutInflater mInflator;

        LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<>();
            mInflator = MenuActivity.this.getLayoutInflater();
        }

        void addDevice(BluetoothDevice device) {
            if (device != null && !mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }

        BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = view.findViewById(R.id.device_address);
                viewHolder.deviceName = view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            String deviceName = null;

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    ActivityCompat.checkSelfPermission(MenuActivity.this,
                            Manifest.permission.BLUETOOTH_CONNECT)
                            == PackageManager.PERMISSION_GRANTED) {
                try {
                    deviceName = device.getName(); // kann sonst SecurityException werfen
                } catch (SecurityException e) {
                    Log.e(TAG, "Fehlende BLUETOOTH_CONNECT Permission bei getName()", e);
                }
            }

            if (deviceName != null && deviceName.length() > 0) {
                viewHolder.deviceName.setText(deviceName);
            } else {
                viewHolder.deviceName.setText("unknown device");
            }

            viewHolder.deviceAddress.setText(device.getAddress());

            return view;
        }
    }

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }

    // ---- Activity Callbacks -------------------------------------------------

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ENABLE_BT) {
            // Bluetooth wurde evtl. aktiviert → Scanner neu holen
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                ensureBtOnAndPermissionsThenScan();
            } else {
                scanbutton.setEnabled(true);
                scanbutton.setText("Start Scan");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT_PERMISSIONS) {
            boolean allGranted = true;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                ensureBtOnAndPermissionsThenScan();
            } else {
                scanbutton.setEnabled(true);
                scanbutton.setText("Permissions required");
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopScan();
    }
}
