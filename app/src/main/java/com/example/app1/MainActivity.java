package com.example.app1;

import static java.sql.DriverManager.println;

import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.DialogInterface;
import android.content.Intent;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private LineChart lineChart;

    //private List<String> xValues;


    TextView temperature;
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        temperature = (TextView)findViewById(R.id.temp);

        Intent intent = getIntent();
        if(intent.getExtras() != null)
        {
            int temp = intent.getExtras().getInt("temp");
            temperature.setText(temp+"");
        }

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
        Description description2 = new Description();
        description.setText("");
        lineChart.setDescription(description);
        lineChart.getAxisRight().setDrawLabels(true);

        List<String> xValues = new ArrayList<String>(Arrays.asList("t0","t1","t2","t3","t4","t5","t6"));

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

        List<Entry> entries2 = new ArrayList<>();
        entries2.add(new Entry(0, 0f));
        entries2.add(new Entry(1, 20));
        entries2.add(new Entry(2, 30));
        entries2.add(new Entry(2, 50));
        entries2.add(new Entry(3, 60));
        entries2.add(new Entry(4, 30));
        entries2.add(new Entry(4.5f, 0));
        entries2.add(new Entry(5f, 0));

        LineDataSet dataSet2 = new LineDataSet(entries2, "");
        dataSet2.setColor(Color.RED);

        LineData lineData = new LineData(dataSet2);

        lineChart.setData(lineData);
        lineChart.setTouchEnabled(true);
        lineChart.setDoubleTapToZoomEnabled(true);
        lineChart.setPinchZoom(false);
        lineChart.setHighlightPerTapEnabled(true);
        lineChart.setMaxHighlightDistance(5f);

        OnChartGestureListener gestureListener = new OnChartGestureListener() {
            @Override
            public void onChartGestureStart(MotionEvent motionEvent, ChartTouchListener.ChartGesture chartGesture) {

            }

            @Override
            public void onChartGestureEnd(MotionEvent motionEvent, ChartTouchListener.ChartGesture chartGesture) {

            }

            @Override
            public void onChartLongPressed(MotionEvent motionEvent) {

            }

            @Override
            public void onChartDoubleTapped(MotionEvent motionEvent) {

            }

            @Override
            public void onChartSingleTapped(MotionEvent motionEvent) {
            }

            @Override
            public void onChartFling(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {

            }

            @Override
            public void onChartScale(MotionEvent motionEvent, float v, float v1) {

            }

            @Override
            public void onChartTranslate(MotionEvent motionEvent, float v, float v1) {

            }
        };

        final int[] highlightX = new int[2];
        final int[] toRemove = new int[1];
        final boolean[] isFirst = {true};
        Button movebutton = (Button) findViewById(R.id.move);
        movebutton.setVisibility(View.GONE);

        OnChartValueSelectedListener valueSelectedListener = new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry entry, Highlight highlight) {

                movebutton.setVisibility(View.VISIBLE);
                highlightX[0] = (int) highlight.getY();
                toRemove[0] = (int) highlight.getX();

                /*if (isFirst[0])
                {
                    highlightX[0] = (int) highlight.getY();
                    toRemove[0] = (int) highlight.getX();
                    isFirst[0] = !isFirst[0];
                    entries2.remove(toRemove[0]);
                }
                else
                {
                    highlightX[1] = (int) highlight.getX();
                    isFirst[0] = !isFirst[0];
                    entries2.add(highlightX[1],new Entry(highlightX[1], highlightX[0]));
                }
                //lineChart.highlightValue(highlight,true);

                /*temperature.setText(entry.getY()+"");
                Log.i("a", entry.toString());
                //entries2.set((int) entry.getX()-1, new Entry(entry.getX()-1, entry.getY()));
                entries2.add(new Entry(0f, 200f));
                LineDataSet dataSet2 = new LineDataSet(entries2, "2");
                dataSet2.setColor(Color.RED);

                LineData lineData = new LineData(dataSet2);

                lineChart.setData(lineData);
                lineChart.invalidate();*/

            }

            @Override
            public void onNothingSelected() {

            }
        };
        lineChart.setOnChartGestureListener(gestureListener);
        lineChart.setOnChartValueSelectedListener(valueSelectedListener);
        lineChart.invalidate();

        CharSequence[] items = new CharSequence[xValues.size()];
        for (int i = 0; i < xValues.size(); i++)
        {
            items[i] = xValues.get(i);

        }
        final int[] i = {6};
        movebutton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                movebutton.setVisibility(View.GONE);

                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Move to:");

                builder.setCancelable(true);
                builder.setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        entries2.remove(toRemove[0]);
                        entries2.add(which,new Entry (which, highlightX[0]));
                    }});

                builder.setNegativeButton("Cancel", (DialogInterface.OnClickListener) (dialog, which) -> {

                    dialog.cancel();
                });

                // Create the Alert dialog
                AlertDialog alertDialog = builder.create();

                // Show the Alert Dialog box
                alertDialog.show();
            }
        });
        Button menubutton = findViewById(R.id.menubutton);
        menubutton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, MenuActivity.class));
            }
        });

    }
}