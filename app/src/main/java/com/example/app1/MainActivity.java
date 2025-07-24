package com.example.app1;

import static java.sql.DriverManager.println;

import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.util.Log;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.utils.MPPointD;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private LineChart lineChart;
    TextView temperature;
    TextView force;
    TextView menubutton;
    TextView startbutton;
    TextView stopbutton;
    Button sendbutton;
    Button isRunningButton;
    Button tarebutton;
    boolean connection;
    List<Entry> entries;
    LineDataSet dataSet;
    LineDataSet actualDataSet;
    LineData lineData;
    int selected = -1;
    boolean oldProfileStarted = false;

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        connection = false;
        setContentView(R.layout.activity_main);

        temperature = findViewById(R.id.temp);
        force = findViewById(R.id.force);
        menubutton = findViewById(R.id.menubutton);
        startbutton = findViewById(R.id.startbutton);
        stopbutton = findViewById(R.id.stopbutton);
        sendbutton = findViewById(R.id.send);
        tarebutton = findViewById(R.id.tare);
        isRunningButton = findViewById(R.id.isRunning);

        startbutton.setVisibility(View.GONE);
        stopbutton.setVisibility(View.GONE);
        sendbutton.setVisibility(View.GONE);
        tarebutton.setVisibility(View.GONE);

        isRunningButton.setText("Stopped");

        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        int REQUEST_ENABLE_BT = 0;

        if (bluetoothAdapter == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setMessage("Device doesn't support Bluetooth.");
            builder.setTitle("No Support!");
            builder.setCancelable(false);
            builder.setPositiveButton("OK", (dialog, which) -> finish());
            builder.create().show();
        }
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        lineChart = findViewById(R.id.chart);
     /*   lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        lineChart.setScaleXEnabled(true);
        lineChart.setScaleYEnabled(true);
        lineChart.setPinchZoom(true);
        lineChart.setAutoScaleMinMaxEnabled(true);*/

        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        lineChart.setScaleXEnabled(true);
        lineChart.setScaleYEnabled(true);
        lineChart.setDoubleTapToZoomEnabled(true);
        lineChart.setAutoScaleMinMaxEnabled(true);
        lineChart.setVisibleXRangeMaximum(60f); // z.B. 60 Sekunden im Blick

        Description description = new Description();
        description.setText("Temperatur Sequenz");
        description.setTextColor(Color.WHITE);
        lineChart.setDescription(description);
        lineChart.getAxisRight().setDrawLabels(true);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setLabelCount(4);
        xAxis.setGranularity(1f);
        xAxis.setAxisMinimum(0f);
//        xAxis.setAxisMaximum(30f);
        xAxis.setTextColor(Color.WHITE);

        YAxis yAxis = lineChart.getAxisLeft();
        yAxis.setAxisMinimum(0f);
        yAxis.setAxisMaximum(250f);
        yAxis.setAxisLineWidth(2f);
        yAxis.setAxisLineColor(Color.BLACK);
        yAxis.setLabelCount(10);
        yAxis.setTextColor(Color.WHITE);

        YAxis rightAxis = lineChart.getAxisRight();
        rightAxis.setAxisMinimum(0f);
        rightAxis.setAxisMaximum(250f);
        rightAxis.setAxisLineWidth(2f);
        rightAxis.setAxisLineColor(Color.BLACK);
        rightAxis.setLabelCount(10);
        rightAxis.setTextColor(Color.WHITE);

        Legend legend = lineChart.getLegend();
        legend.setEnabled(false);

        entries = new ArrayList<>();
        entries.add(new Entry(1, 50));
        entries.add(new Entry(10, 90));
        entries.add(new Entry(15, 100));
        entries.add(new Entry(20, 120));
        entries.add(new Entry(25, 110));
        entries.add(new Entry(60, 40));

        dataSet = new LineDataSet(entries, "Soll-Werte");
        dataSet.setColor(Color.BLUE);
        dataSet.setLineWidth(1f);
        dataSet.setCircleRadius(10f);
        dataSet.setCircleColor(Color.GREEN);
        dataSet.setDrawValues(true);
        dataSet.setDrawCircles(true);
        dataSet.setValueTextColor(Color.GREEN);

        actualDataSet = new LineDataSet(new ArrayList<Entry>(), "Ist-Wert");
        actualDataSet.setColor(Color.RED);
        actualDataSet.setDrawCircles(false);
        actualDataSet.setLineWidth(2f);

        lineData = new LineData(dataSet, actualDataSet);
        lineChart.setData(lineData);
        lineChart.invalidate();

        setupTouchListener();

        menubutton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MenuActivity.class);
            if (connection) {
                intent.putExtra("connected", true);
            }
            startActivity(intent);
        });

        startbutton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MenuActivity.class);
            intent.putExtra("send", false);
            intent.putExtra("start", 1);
            startActivity(intent);
        });

        stopbutton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MenuActivity.class);
            intent.putExtra("send", false);
            intent.putExtra("start", 2);
            startActivity(intent);
        });

        sendbutton.setOnClickListener(v -> {
            float[] xvalues = new float[dataSet.getEntryCount()];
            float[] yvalues = new float[dataSet.getEntryCount()];

            for (int i = 0; i < dataSet.getEntryCount(); i++) {
                xvalues[i] = dataSet.getEntryForIndex(i).getX();
                yvalues[i] = dataSet.getEntryForIndex(i).getY();
            }

            Intent intent = new Intent(MainActivity.this, MenuActivity.class);
            intent.putExtra("send", true);
            intent.putExtra("xValues", xvalues);
            intent.putExtra("yValues", yvalues);
            startActivity(intent);
        });

        tarebutton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MenuActivity.class);
            intent.putExtra("send", false);
            intent.putExtra("tare", true);
            intent.putExtra("tareValue", (byte) 1);
            startActivity(intent);
        });
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("ACTION_DATA_AVAILABLE".equals(intent.getAction())) {
                double temp = intent.getDoubleExtra("temp", 0.0);
                int forcevalue = intent.getIntExtra("force", 0);
                float currentTime = (float) intent.getIntExtra("currentTime", 0) /10.0f;
                connection = intent.getBooleanExtra("connection", false);
                boolean running = intent.getBooleanExtra("running",false);
                boolean profileStarted = intent.getBooleanExtra("profileStarted", false);

                if (!running & !profileStarted){
                    isRunningButton.setText("OFF");
                    isRunningButton.setBackgroundColor(Color.GREEN);
                }
                else if (running & !profileStarted){
                    isRunningButton.setText("Waiting");
                    isRunningButton.setBackgroundColor(Color.YELLOW);
                    oldProfileStarted = false;
                }
                else if (running & profileStarted){
                    isRunningButton.setText("Run");
                    isRunningButton.setBackgroundColor(Color.RED);

                }
                else{
                    isRunningButton.setText("HE?");
                    isRunningButton.setBackgroundColor(Color.WHITE);
                }

                if (!oldProfileStarted & profileStarted) {
                    actualDataSet.clear();
                    oldProfileStarted = true;
                }
                if (connection) {
                    menubutton.setTextColor(Color.GREEN);
                    startbutton.setVisibility(View.VISIBLE);
                    stopbutton.setVisibility(View.VISIBLE);
                    sendbutton.setVisibility(View.VISIBLE);
                    tarebutton.setVisibility(View.VISIBLE);
                } else {
                    menubutton.setTextColor(Color.RED);
                    startbutton.setVisibility(View.GONE);
                    stopbutton.setVisibility(View.GONE);
                    sendbutton.setVisibility(View.GONE);
                    tarebutton.setVisibility(View.GONE);
                }
                temperature.setText(String.format("%.2f °C", temp));
                force.setText(String.format("%.2f N", (float)((float)forcevalue/1000.0f)));

                //boolean pointExists = false;

//                for (Entry e : actualDataSet.getEntriesForXValue(currentTime)) {
//                    e.setY((float) temp);
//                    pointExists = true;
//                }
//
                if (profileStarted) {
                    actualDataSet.addEntry(new Entry(currentTime, (float) temp));;
                }

                //Log.d("currenttime", String.format("%f",currentTime));
                actualDataSet.notifyDataSetChanged();
                lineData.notifyDataChanged();
                lineChart.notifyDataSetChanged();
                lineChart.invalidate();
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter("ACTION_DATA_AVAILABLE");
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupTouchListener() {
        lineChart.setOnTouchListener((v, event) -> {
            float touchX = event.getX();
            float touchY = event.getY();
            MPPointD point = lineChart.getTransformer(YAxis.AxisDependency.LEFT).getValuesByTouchPoint(touchX, touchY);
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    selected = getClosestEntry(point.x, point.y);
                    return selected != -1;
                case MotionEvent.ACTION_MOVE:
                    if (selected != -1) {
                        Entry entry = dataSet.getEntryForIndex(selected);
                        entry.setX((float) point.x);
                        entry.setY((float) point.y);
                        lineChart.getData().notifyDataChanged();
                        lineChart.notifyDataSetChanged();
                        lineChart.invalidate();
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    selected = -1;
                    return true;
            }
            return false;
        });
    }

    private int getClosestEntry(double x, double y) {
        int closest = -1;
        float minDistance = 2.5f;
        for (int i = 0; i < dataSet.getEntryCount(); i++) {
            Entry e = dataSet.getEntryForIndex(i);
            float dx = Math.abs(e.getX() - (float) x);
            float dy = Math.abs(e.getY() - (float) y);
            if (dx < minDistance && dy < minDistance) {
                closest = i;
                break;
            }
        }
        return closest;
    }
}
