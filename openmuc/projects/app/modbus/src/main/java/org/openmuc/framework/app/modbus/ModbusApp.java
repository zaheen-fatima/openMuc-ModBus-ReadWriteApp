package org.openmuc.framework.app.modbus;

import org.openmuc.framework.data.Record;
import org.openmuc.framework.data.Value;
import org.openmuc.framework.data.ShortValue;
import org.openmuc.framework.dataaccess.Channel;
import org.openmuc.framework.dataaccess.DataAccessService;
import org.openmuc.framework.dataaccess.RecordListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;

@Component(service = {})
public final class ModbusApp {

    private static final Logger logger = LoggerFactory.getLogger(ModbusApp.class);
    private static final String APP_NAME = "OpenMUC Modbus Reader/Writer App";

    private DataAccessService dataAccessService;
    private Channel modbusChannel;
    private RecordListener modbusListener;

    private Timer writeTimer;
    private int writeCounter = 100; // Starting value for write

    private static final String CSV_FILE_PATH = "modbus_data.csv";

    @Reference
    public void setDataAccessService(DataAccessService dataAccessService) {
        this.dataAccessService = dataAccessService;
    }

    @Activate
    private void activate() {
        logger.info("Activating {}", APP_NAME);

        // Initialize CSV file with headers
        initializeCSV();

        modbusChannel = dataAccessService.getChannel("register1");
        if (modbusChannel == null) {
            logger.error("Failed to get Modbus channel 'register1'. Check channels.xml configuration.");
            return;
        }

        // Set up reader
        modbusListener = new ModbusListener();
        modbusChannel.addListener(modbusListener);
        logger.info("Reader: Listening to Modbus channel 'register1'");

        // Set up writer (periodic)
        writeTimer = new Timer();
        writeTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                writeCounter++;
                writeToModbus(writeCounter);
            }
        }, 0, 5000); // Write every 5 seconds
        logger.info("Writer: Scheduled periodic writes to 'register1' every 5 seconds");
    }

    @Deactivate
    private void deactivate() {
        logger.info("Deactivating {}", APP_NAME);

        if (modbusChannel != null && modbusListener != null) {
            modbusChannel.removeListener(modbusListener);
        }

        if (writeTimer != null) {
            writeTimer.cancel();
        }

        logger.info("{} deactivated successfully", APP_NAME);
    }

    public void writeToModbus(Number valueToWrite) {
        if (modbusChannel == null) {
            logger.error("Cannot write: Modbus channel 'register1' not initialized.");
            return;
        }

        try {
            Value value = new ShortValue(valueToWrite.shortValue());
            logger.info("Writing value {} to Modbus channel 'register1'", valueToWrite);
            modbusChannel.write(value);
            logger.info("Successfully wrote value {} to Modbus channel 'register1'", valueToWrite);

            // Log the write action to CSV
            saveToCSV("WRITE", valueToWrite);
        } catch (Exception e) {
            logger.error("Failed to write value {} to Modbus channel 'register1': {}", valueToWrite, e.getMessage(), e);
        }
    }

    private class ModbusListener implements RecordListener {
        @Override
        public void newRecord(Record record) {
            if (record != null && record.getValue() != null) {
                Number readValue = record.getValue().asShort(); // if INT16


                logger.info("Read value from register1: {} (Timestamp: {}) (DataType: {})",
                        readValue, record.getTimestamp(), record.getValue().getValueType());

                // Log the read action to CSV
                saveToCSV("READ", readValue);
            } else {
                logger.warn("Received null record or value from register1");
            }
        }
    }

    private void initializeCSV() {
        try (FileWriter writer = new FileWriter(CSV_FILE_PATH, true)) {
            writer.append("Timestamp,Action,Value\n");
            logger.info("Initialized CSV file: {}", CSV_FILE_PATH);
        } catch (IOException e) {
            logger.error("Failed to initialize CSV file", e);
        }
    }

    private void saveToCSV(String action, Number value) {
        try (FileWriter writer = new FileWriter(CSV_FILE_PATH, true)) {
            writer.append(Instant.now().toString())
                    .append(',')
                    .append(action)
                    .append(',')
                    .append(value.toString())
                    .append('\n');
        } catch (IOException e) {
            logger.error("Failed to write to CSV", e);
        }
    }
}
