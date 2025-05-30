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

import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;

public class MenuActivity extends AppCompatActivity {

    private Handler handler;
    private boolean[] scanning = new boolean[1];
    LeDeviceListAdapter leDeviceListAdapter;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private static final long SCAN_PERIOD = 3000;
    private BluetoothGatt bluetoothGatt;
    Button scanbutton;

    @RequiresApi(api = Build.VERSION_CODES.S)
    @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN})
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.menu);
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 2);
        requestPermissions(new String[]{(Manifest.permission.BLUETOOTH_SCAN)}, 2);
        requestPermissions(new String[]{(Manifest.permission.BLUETOOTH_CONNECT)}, 1);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        scanbutton = (Button) findViewById(R.id.scanbutton);

        final BluetoothManager bluetoothManager =
        (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        int REQUEST_ENABLE_BT = 1;

        if (bluetoothAdapter == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(MenuActivity.this);
            builder.setMessage("Device doesn't support Bluetooth.");
            builder.setTitle("No Support!");
            builder.setCancelable(false);
            builder.setPositiveButton("OK", (DialogInterface.OnClickListener) (dialog, which) -> {
                finish();
            });
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            requestPermissions(new String[]{(Manifest.permission.BLUETOOTH_CONNECT)}, 1);
            requestPermissions(new String[]{(Manifest.permission.BLUETOOTH_SCAN)}, 1);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT );
        }
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        scanning[0] = false;
        handler = new Handler();
        leDeviceListAdapter = new LeDeviceListAdapter();
        //setListAdapter(leDeviceListAdapter);

        scanbutton.setOnClickListener(new View.OnClickListener() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
            public void onClick(View v) {
                requestPermissions(new String[]{(Manifest.permission.BLUETOOTH_SCAN)}, 1);
                requestPermissions(new String[]{(Manifest.permission.BLUETOOTH_CONNECT)}, 1);
                scanbutton.setText("Scanning...");
                //LeDeviceListAdapter.clear();
                scanLeDevice(true);
                scanbutton.setEnabled(false);
            }
        });
    }

    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                scanbutton.setText("connected");
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                scanbutton.setText("disconnected");
            }
        }
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(UUID.fromString("00010203-0405-0607-0809-0a0b0c0d0e0f"));
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString("00010203-0405-0607-0809-0a0b0c0d0e12"));

                bluetoothGatt.setCharacteristicNotification(characteristic, true);
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
            }
        }
            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                byte[] data = characteristic.getValue();
                if (data != null) {
                    ByteBuffer buffer = ByteBuffer.wrap(data);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    int value = buffer.getInt();
                    Log.i( "cc", value+"");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                    intent.putExtra("temp",value);
                    startActivity(intent);
                }
            }

    };
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private void scanLeDevice(final boolean enable) {
        if (enable) {

                handler.postDelayed(new Runnable() {
                    @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT})
                    @Override
                    public void run() {
                        scanbutton.setText("Start Scan");
                        scanbutton.setEnabled(false);
                        scanning[0] = false;
                        bluetoothLeScanner.stopScan( leScanCallback);
                        ListView listView = findViewById(R.id.deviceList);
                        listView.setAdapter(leDeviceListAdapter);

                        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
                            @Override
                            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                                BluetoothDevice selectedItem = (BluetoothDevice) parent.getItemAtPosition(position);
                                scanbutton.setText(selectedItem.getAddress());
                                final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(selectedItem.getAddress());
                                scanbutton.setText(device.getName());

                                if (device == null) {
                                    scanbutton.setText("Device not found.");
                                }
                                else {
                                    connect(selectedItem.getAddress());
                                    if(device.getBondState() == 12 || true)
                                    {
                                        //scanbutton.setText("Bonded");
                                        try {
                                            Thread.sleep(1000);
                                        } catch (InterruptedException e) {
                                            throw new RuntimeException(e);
                                        }
                                        bluetoothGatt.discoverServices();
                                        try {
                                            Thread.sleep(5000);
                                        } catch (InterruptedException e) {
                                            throw new RuntimeException(e);
                                        }

                                        BluetoothGattService service = bluetoothGatt.getService(UUID.fromString("00010203-0405-0607-0809-0a0b0c0d0e0f"));
                                        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString("00010203-0405-0607-0809-0a0b0c0d0e12"));
                                        bluetoothGatt.setCharacteristicNotification(characteristic, true);
                                        /*
                                        bluetoothGatt.readCharacteristic(characteristic);
                                        byte[] data = characteristic.getValue();
                                        // int str = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                                        if (data != null) {
                                            String value = new String(data, StandardCharsets.UTF_8);
                                            Log.i( "cc", value);
                                        } else {
                                            Log.w("cc", "null");
                                        }*/

                                    }
                                    else
                                    {
                                        // scanbutton.setText("Not Bonded");
                                    }
                                }

                            }
                        });



                    }
                }, SCAN_PERIOD);

                scanning[0] = true;
            ScanSettings scanSettings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();

            bluetoothLeScanner.startScan(null,scanSettings, leScanCallback);
            } else {
                scanning[0] = false;
                bluetoothLeScanner.stopScan( leScanCallback);
            }

    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public boolean connect(final String address)
    {
        try {
            final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
            bluetoothGatt = device.connectGatt(this, false, bluetoothGattCallback);
        }
        catch (IllegalArgumentException a)
        {
            return false;
        }
        return true;
    }
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<BluetoothDevice>();
            mInflator = MenuActivity.this.getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {
            if(!mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
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
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText("unknown device");
            viewHolder.deviceAddress.setText(device.getAddress());

            return view;
        }
    }

    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
                    leDeviceListAdapter.addDevice(result.getDevice());
                    leDeviceListAdapter.notifyDataSetChanged();

        }
    };

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }
}
