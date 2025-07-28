import sys
import serial
import serial.tools.list_ports
from PyQt5.QtWidgets import (QApplication, QMainWindow, QPushButton, QVBoxLayout,
                             QHBoxLayout, QWidget, QSlider, QLabel, QDialog,
                             QListWidget, QDialogButtonBox, QGraphicsEllipseItem, QGraphicsScene,
                             QTextEdit)
from PyQt5.QtCore import Qt, QTimer, QPointF
from PyQt5.QtChart import QChart, QChartView, QLineSeries, QValueAxis
from PyQt5.QtGui import QPainter, QColor, QPen, QMouseEvent

class DraggablePoint(QGraphicsEllipseItem):
    def __init__(self, x, y, radius, index, callback):
        super().__init__(-radius, -radius, radius*2, radius*2)
        self.setBrush(QColor("green"))
        self.setFlag(QGraphicsEllipseItem.ItemIsMovable, True)
        self.setFlag(QGraphicsEllipseItem.ItemSendsGeometryChanges, True)
        self.setZValue(10)
        self.index = index
        self.callback = callback
        self.setPos(x, y)

    def itemChange(self, change, value):
        if change == QGraphicsEllipseItem.ItemPositionChange and self.callback:
            self.callback(self.index, value)
        return super().itemChange(change, value)

class DebugDialog(QDialog):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("Debug: Serielle Kommunikation")
        self.resize(600, 300)
        self.textbox = QTextEdit()
        self.textbox.setReadOnly(True)
        self.clear_btn = QPushButton("Clear")
        self.clear_btn.clicked.connect(self.clear_text)

        layout = QVBoxLayout()
        layout.addWidget(self.textbox)
        layout.addWidget(self.clear_btn)
        self.setLayout(layout)

    def append_text(self, text):
        self.textbox.append(text)

    def clear_text(self):
        self.textbox.clear()

class COMDialog(QDialog):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("Wähle COM Port")
        layout = QVBoxLayout()

        self.port_list = QListWidget()
        self.ports = [port.device for port in serial.tools.list_ports.comports()]
        self.port_list.addItems(self.ports)
        layout.addWidget(self.port_list)

        self.buttons = QDialogButtonBox(QDialogButtonBox.Ok | QDialogButtonBox.Cancel)
        self.buttons.accepted.connect(self.accept)
        self.buttons.rejected.connect(self.reject)
        layout.addWidget(self.buttons)

        self.setLayout(layout)

    def selected_port(self):
        selected_items = self.port_list.selectedItems()
        return selected_items[0].text() if selected_items else None

class ChartWidget(QWidget):
    def __init__(self):
        super().__init__()
        self.series_points = QLineSeries()
        self.series_temp = QLineSeries()
        self.series_force = QLineSeries()
        self.series_points.setColor(Qt.blue)
        self.series_temp.setColor(Qt.red)
        self.series_force.setColor(Qt.gray)

        self.chart = QChart()
        self.chart.addSeries(self.series_points)
        self.chart.addSeries(self.series_temp)
        self.chart.addSeries(self.series_force)

        self.axisX = QValueAxis()
        self.axisX.setRange(0, 120)
        self.axisX.setTitleText("Zeit (s)")
        self.chart.addAxis(self.axisX, Qt.AlignBottom)

        self.axisYTemp = QValueAxis()
        self.axisYTemp.setRange(0, 250)
        self.axisYTemp.setTitleText("Temperatur (°C)")
        self.chart.addAxis(self.axisYTemp, Qt.AlignLeft)

        self.axisYForce = QValueAxis()
        self.axisYForce.setRange(0, 30)
        self.axisYForce.setTitleText("Kraft (N)")
        self.chart.addAxis(self.axisYForce, Qt.AlignRight)

        self.series_points.attachAxis(self.axisX)
        self.series_points.attachAxis(self.axisYTemp)
        self.series_temp.attachAxis(self.axisX)
        self.series_temp.attachAxis(self.axisYTemp)
        self.series_force.attachAxis(self.axisX)
        self.series_force.attachAxis(self.axisYForce)

        self.chart.legend().hide()
        self.view = QChartView(self.chart)
        self.view.setRenderHint(QPainter.Antialiasing)

        layout = QVBoxLayout()
        layout.addWidget(self.view)
        self.setLayout(layout)

        self.drag_points = []
        self.point_data = []
        self.create_draggable_points()

    def create_draggable_points(self):
        self.point_data = [(i * 20, 40) for i in range(6)]
        self.update_points()

        for i, (x, y) in enumerate(self.point_data):
            pt_x = x / 120 * self.view.width()
            pt_y = (1 - y / 250) * self.view.height()
            pt = DraggablePoint(pt_x, pt_y, 6, i, self.point_moved)
            self.view.scene().addItem(pt)
            self.drag_points.append(pt)

    def point_moved(self, index, pos):
        x = pos.x() / self.view.width() * 120
        y = (1 - pos.y() / self.view.height()) * 250
        self.point_data[index] = (x, y)
        self.update_points()

    def update_points(self):
        self.series_points.clear()
        for x, y in self.point_data:
            self.series_points.append(x, y)

class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("PyQtChart mit Serial Port")

        self.serial_port = None
        self.timer = QTimer()
        self.timer.timeout.connect(self.read_serial_data)

        self.chart_widget = ChartWidget()
        self.debug_window = DebugDialog()

        self.start_btn = QPushButton("Start")
        self.stop_btn = QPushButton("Stop")
        self.tare_btn = QPushButton("Tare")
        self.send_btn = QPushButton("Send")
        self.com_btn = QPushButton("COM")
        self.debug_btn = QPushButton("Debug")

        self.start_btn.clicked.connect(lambda: self.send_command("o1\n"))
        self.stop_btn.clicked.connect(lambda: self.send_command("o0\n"))
        self.tare_btn.clicked.connect(lambda: self.send_command("t\n"))
        self.send_btn.clicked.connect(self.send_points)
        self.com_btn.clicked.connect(self.select_com_port)
        self.debug_btn.clicked.connect(self.debug_window.show)

        self.slider = QSlider(Qt.Horizontal)
        self.slider.setMinimum(0)
        self.slider.setMaximum(200)
        self.slider.valueChanged.connect(self.slider_changed)
        self.slider_label = QLabel("Kraft-Schwelle: 0.0 N")

        control_layout = QHBoxLayout()
        control_layout.addWidget(self.start_btn)
        control_layout.addWidget(self.stop_btn)
        control_layout.addWidget(self.tare_btn)
        control_layout.addWidget(self.send_btn)
        control_layout.addWidget(self.com_btn)
        control_layout.addWidget(self.debug_btn)

        slider_layout = QHBoxLayout()
        slider_layout.addWidget(self.slider_label)
        slider_layout.addWidget(self.slider)

        main_layout = QVBoxLayout()
        main_layout.addWidget(self.chart_widget)
        main_layout.addLayout(control_layout)
        main_layout.addLayout(slider_layout)

        central_widget = QWidget()
        central_widget.setLayout(main_layout)
        self.setCentralWidget(central_widget)

    def send_command(self, cmd):
        if self.serial_port and self.serial_port.is_open:
            self.serial_port.write(cmd.encode())
            self.debug_window.append_text(f"Gesendet: {cmd.strip()}")

    def send_points(self):
        for i, (zeit, temp) in enumerate(self.chart_widget.point_data):
            cmd = f"p{i},{zeit:.1f},{temp:.1f}\n"
            self.send_command(cmd)

    def slider_changed(self, value):
        newton = value / 10.0
        self.slider_label.setText(f"Kraft-Schwelle: {newton:.1f} N")
        self.send_command(f"r{value}\n")

    def select_com_port(self):
        dialog = COMDialog()
        if dialog.exec_() == QDialog.Accepted:
            port = dialog.selected_port()
            if port:
                if self.serial_port:
                    self.serial_port.close()
                self.serial_port = serial.Serial(port, 115200, timeout=0.1)
                self.timer.start(100)
                self.debug_window.append_text(f"Verbunden mit: {port}")

    def read_serial_data(self):
        if self.serial_port and self.serial_port.in_waiting:
            line = self.serial_port.readline().decode(errors='ignore').strip()
            self.debug_window.append_text(f"Empfangen: {line}")
            if ',' in line:
                parts = line.split(',')
                if len(parts) >= 3:
                    try:
                        ms = float(parts[-3])
                        temp = float(parts[-2])
                        force_mN = float(parts[-1])
                        force_N = force_mN / 1000.0
                        x = ms / 1000.0
                        self.chart_widget.series_temp.append(x, temp)
                        self.chart_widget.series_force.append(x, force_N)
                    except ValueError:
                        pass

if __name__ == '__main__':
    app = QApplication(sys.argv)
    window = MainWindow()
    window.resize(1000, 600)
    window.show()
    sys.exit(app.exec_())
