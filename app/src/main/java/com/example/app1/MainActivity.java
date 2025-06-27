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
import android.util.Log;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
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
    boolean connection;
    List<Entry> entries;
    LineDataSet dataSet;
    LineData lineData;
    int selected = -1;

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        connection = false;
        setContentView(R.layout.activity_main);

        temperature = (TextView)findViewById(R.id.temp);
        force = (TextView)findViewById(R.id.force);
        menubutton = (TextView)findViewById(R.id.menubutton);
        startbutton = (TextView)findViewById(R.id.startbutton);
        stopbutton = (TextView)findViewById(R.id.stopbutton);
        sendbutton = (Button) findViewById(R.id.send);

        startbutton.setVisibility(View.GONE);
        stopbutton.setVisibility(View.GONE);
        sendbutton.setVisibility(View.GONE);

        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        int REQUEST_ENABLE_BT = 0;

        if (bluetoothAdapter == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
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
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT );
        }


        lineChart = findViewById(R.id.chart);

        Description description = new Description();
        description.setText("");
        lineChart.setDescription(description);
        lineChart.getAxisRight().setDrawLabels(true);

        List<String> xValues = new ArrayList<String>(Arrays.asList("t0","t1","t2","t3","t4","t5"));

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(xValues));
        xAxis.setLabelCount(4);
        xAxis.setGranularity(1f);


        YAxis yAxis = lineChart.getAxisLeft();
        yAxis.setAxisMinimum(0f);
        yAxis.setAxisMaximum(250f);
        yAxis.setAxisLineWidth(2f);
        yAxis.setAxisLineColor(Color.BLACK);
        yAxis.setLabelCount(10);

        Legend legend = lineChart.getLegend();
        legend.setEnabled(false);

        entries = new ArrayList<>();
        entries.add(new Entry(0, 0));
        entries.add(new Entry(1, 20));
        entries.add(new Entry(2, 30));
        entries.add(new Entry(3, 40));
        entries.add(new Entry(4, 30));
        entries.add(new Entry(5, 10));

        dataSet = new LineDataSet(entries, "");
        //dataSet.setColor(Color.RED);
        //dataSet.setHighlightEnabled(true);
        //dataSet.setHighlightLineWidth(1f);
        dataSet.setCircleRadius(15f);
        dataSet.setCircleColor(Color.GREEN);
        dataSet.setColor(Color.BLUE);
        dataSet.setLineWidth(1f);
        dataSet.setDrawValues(true);
        dataSet.setDrawCircles(true);
        //dataSet.setHighLightColor(Color.GREEN);

        lineData = new LineData(dataSet);
        lineChart.setData(lineData);
        lineChart.setPinchZoom(false);
        lineChart.setDragEnabled(true);
        lineChart.zoomToCenter(1,5f);
        lineChart.setTouchEnabled(true);

        /*
        lineChart.setDoubleTapToZoomEnabled(false);
        lineChart.setHighlightPerTapEnabled(true);
        lineChart.setMaxHighlightDistance(50f);
        lineChart.setHighlightPerDragEnabled(false);


        */

        lineChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        lineChart.getAxisRight().setEnabled(true);
        lineChart.invalidate();

        setupTouchListener();



        final int[] highlightX = new int[2];
        final int[] toRemove = new int[1];
        final boolean[] isFirst = {true};
        Button movebutton = (Button) findViewById(R.id.move);
        movebutton.setVisibility(View.GONE);

        /*CharSequence[] items = new CharSequence[xValues.size()];
        for (int i = 0; i < xValues.size(); i++)
        {
            items[i] = xValues.get(i);

        }*/
        final int[] i = {6};
        movebutton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                movebutton.setVisibility(View.GONE);

                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Move to:");

                builder.setCancelable(true);
               /* builder.setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        entries.remove(toRemove[0]);
                        entries.add(which,new Entry (which, highlightX[0]));
                    }});*/

                builder.setNegativeButton("Cancel", (DialogInterface.OnClickListener) (dialog, which) -> {

                    dialog.cancel();
                });

                AlertDialog alertDialog = builder.create();
                alertDialog.show();
            }
        });

        sendbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                float[] xvalues = new float[entries.size()];
                float[] yvalues = new float[entries.size()];

                for (int i = 0; i < entries.size(); i++) {
                    xvalues[i] = entries.get(i).getX();
                    yvalues[i] = entries.get(i).getY();
                }

                Intent intent = new Intent(MainActivity.this, MenuActivity.class);
                intent.putExtra("send",true);
                intent.putExtra("xValues", xvalues);
                intent.putExtra("yValues", yvalues);
                startActivity(intent);

            }
        });
        startbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, MenuActivity.class);
                intent.putExtra("send",false);
                intent.putExtra("start",1);
                startActivity(intent);
            }
        });
        stopbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, MenuActivity.class);
                intent.putExtra("send",false);
                intent.putExtra("start",2);
                startActivity(intent);
            }
        });
        menubutton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(connection) {
                    Intent intent = new Intent(MainActivity.this, MenuActivity.class);
                    intent.putExtra("connected",true);
                    startActivity(intent);
                }
                else
                {
                    startActivity(new Intent(MainActivity.this, MenuActivity.class));
                }
            }
        });

    }
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("ACTION_DATA_AVAILABLE".equals(intent.getAction())) {
                double temp = intent.getDoubleExtra("temp", 0.0);
                int forcevalue = intent.getIntExtra("force", 0);
                connection = intent.getBooleanExtra("connection", false);
                if(connection)
                {
                    menubutton.setTextColor(Color.GREEN);
                    startbutton.setVisibility(View.VISIBLE);
                    stopbutton.setVisibility(View.VISIBLE);
                    sendbutton.setVisibility(View.VISIBLE);
                }
                else
                {
                    menubutton.setTextColor(Color.RED);
                    startbutton.setVisibility(View.GONE);
                    stopbutton.setVisibility(View.GONE);
                    sendbutton.setVisibility(View.GONE);
                }
                temperature.setText(temp+" °C");
                force.setText(forcevalue+" N");
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
        lineChart.setOnTouchListener(new View.OnTouchListener() {
            float lastX, lastY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
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
            }
        });

    }

    private int getClosestEntry(double x, double y) {
        int closest = -1;
        float minDistance = 1.0f;

        for (int i = 0; i < dataSet.getEntryCount(); i++)
        {
            Entry e = dataSet.getEntryForIndex(i);
            float dx = Math.abs(e.getX() - (float)x);
            float dy = Math.abs(e.getY() - (float)y);
            if (dx < minDistance && dy < minDistance)
            {
                closest = i;
                break;
            }
        }
        return closest;
    }
}