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
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MenuActivity extends AppCompatActivity {

    private Handler handler;
    private boolean[] scanning = new boolean[1];
    LeDeviceListAdapter leDeviceListAdapter;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private static final long SCAN_PERIOD = 3000;
    private static BluetoothGatt bluetoothGatt;
    Button scanbutton;
    Boolean first;
    BluetoothGattService service;
    BluetoothManager bluetoothManager;
    boolean connection;
    private static BluetoothGattCharacteristic char11;
    private static BluetoothGattCharacteristic startStopChar;
    private static BluetoothGattCharacteristic tempProfileChar;
    private static BluetoothGattCharacteristic tareChar;
    private static BluetoothGattCharacteristic thresholdChar;


    @RequiresApi(api = Build.VERSION_CODES.S)
    @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN})
    protected void onCreate(Bundle savedInstanceState) {
        first = true;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.menu);
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 2);
        requestPermissions(new String[]{(Manifest.permission.BLUETOOTH_SCAN)}, 2);
        requestPermissions(new String[]{(Manifest.permission.BLUETOOTH_CONNECT)}, 1);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        scanbutton = (Button) findViewById(R.id.scanbutton);

        bluetoothManager =
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
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    protected void onResume()
    {
        super.onResume();
        boolean connection = getIntent().getBooleanExtra("connected", false);
        boolean toSend =  getIntent().getBooleanExtra("send", false);
        int start = getIntent().getIntExtra("start", 0);
        float[] xValues = getIntent().getFloatArrayExtra("xValues");
        float[] yValues = getIntent().getFloatArrayExtra("yValues");

        boolean tare = getIntent().getBooleanExtra("tare", false);
        byte tareValue = getIntent().getByteExtra("tareValue", (byte) 0);

        if (tare) {
            tareChar.setValue(new byte[]{tareValue});
            bluetoothGatt.writeCharacteristic(tareChar);
            finish();
        }

        if(start == 1)
        {
            startStopChar.setValue(new byte[] { (byte) 1});
            bluetoothGatt.writeCharacteristic(startStopChar);
            finish();
        }
        else if(start == 2)
        {
            startStopChar.setValue(new byte[] { (byte) 0});
            bluetoothGatt.writeCharacteristic(startStopChar);
            finish();
        }

        if(toSend)
        {
            float[] profileTime = xValues;
            float[] profileTemp = yValues;
            byte[] data = new byte[24];

            for (int i = 0; i < 6; i++)
            {
                int time = (int) profileTime[i];
                int temp = (int)(profileTemp[i] * 10);
                data[i * 4] = (byte)((time >> 8) & 0xFF);
                data[i * 4 + 1] = (byte)(time & 0xFF);
                data[i * 4 + 2] = (byte)((temp >> 8) & 0xFF);
                data[i * 4 + 3] = (byte)(temp & 0xFF);
            }
            tempProfileChar.setValue(data);
            bluetoothGatt.writeCharacteristic(tempProfileChar);

            scanbutton.setText("sent - Start Scan");
            finish();
        }
        if (connection)
        {
            scanbutton.setText("connected - Start Scan");
        }

    }
    public void setGatt(BluetoothGatt gatt) {
        bluetoothGatt = gatt;
    }
    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                scanbutton.setText("connected");
                connection = true;
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connection = false;
                Intent intent = new Intent("ACTION_DATA_AVAILABLE");
                intent.putExtra("connection", false);
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
                scanbutton.setText("disconnected");
            }
        }
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                service = gatt.getService(UUID.fromString("00010203-0405-0607-0809-0a0b0c0d0e0f"));
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString("00010203-0405-0607-0809-0a0b0c0d0e12"));

                bluetoothGatt.setCharacteristicNotification(characteristic, true);
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
                setGatt(gatt);

            }
        }
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

                    byte[] data = characteristic.getValue();

                    if (data != null&& data.length >= 8) {
                        boolean running =  ((data[0] & 0x02)!=0)?true:false;
                        boolean profileStarted = ((data[0] & 0x01)!=0)?true:false;
                        int tempRawUnsigned = ((data[1] & 0xFF) << 8) | (data[2] & 0xFF);
                        int tempRawSigned = tempRawUnsigned >= 0x8000 ? tempRawUnsigned - 0x10000 : tempRawUnsigned;
                        double temperature = tempRawSigned / 10.0;
                        int force = ((data[3] & 0xFF) << 24) | ((data[4] & 0xFF) << 16) | ((data[5] & 0xFF) << 8) | ((data[6] & 0xFF));
                        int currentTime = ((data[7] & 0xFF) << 8) | (data[8] & 0xFF);
//                        String logsFormat = String.format("%0.2f, %0.2f, %0.2f\n", temperature, force, currentTime);
//                        Log.d("received ",logsFormat);

                        Intent intent = new Intent("ACTION_DATA_AVAILABLE");
                        intent.putExtra("temp", temperature);
                        intent.putExtra("force", force);
                        intent.putExtra("currentTime", currentTime);
                        intent.putExtra("running",running);
                        intent.putExtra("profileStarted",profileStarted);

                        if(connection)
                        {
                            intent.putExtra("connection", true);
                        }
                        else
                        {
                            intent.putExtra("connection", false);
                        }
                        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
                        if(first) {
                            Intent activityintent = new Intent(MenuActivity.this, MainActivity.class);
                            startActivity(activityintent);
                            first = false;
                        }

                }
                    /*
                    byte[] dataToSend = new byte[]{15,20};
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    BluetoothGattService service = gatt.getService(UUID.fromString("00010203-0405-0607-0809-0a0b0c0d0e0f"));
                    BluetoothGattCharacteristic newcharacteristic = service.getCharacteristic(UUID.fromString("00010203-0405-0607-0809-0a0b0c0d0e11"));

                    int props = newcharacteristic.getProperties();
                    if ((props & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0 ||
                            (props & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0) {

                        newcharacteristic.setValue(dataToSend);
                        bluetoothGatt.writeCharacteristic(newcharacteristic);
                    } else {
                        Log.e("BLE", "Characteristic does not support WRITE or WRITE_NO_RESPONSE");
                    }
*/


            }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
               // Log.d("write", "successful" + characteristic.getUuid());
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

                        scanning[0] = false;
                        bluetoothLeScanner.stopScan( leScanCallback);
                        scanbutton.setVisibility(View.GONE);
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

                                        service = bluetoothGatt.getService(UUID.fromString("00010203-0405-0607-0809-0a0b0c0d0e0f"));
                                        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString("00010203-0405-0607-0809-0a0b0c0d0e12"));

                                        startStopChar = service.getCharacteristic(UUID.fromString("00010203-0405-0607-0809-0a0b0c0d0e10"));
                                        tempProfileChar = service.getCharacteristic(UUID.fromString("00010203-0405-0607-0809-0a0b0c0d0e11"));
                                        tareChar = service.getCharacteristic(UUID.fromString("00010203-0405-0607-0809-0a0b0c0d0e13"));
                                        thresholdChar = service.getCharacteristic(UUID.fromString("00010203-0405-0607-0809-0a0b0c0d0e14"));

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
                scanbutton.setText("Not Scanning");
            }

    }
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public boolean connect(final String address)
    {
        try {
            final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
            bluetoothGatt = device.connectGatt(this, false, bluetoothGattCallback);
            //bluetoothManager.getInstance().setGatt(bluetoothGatt);
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
