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

@Component(service = {})
public final class ModbusApp {

    private static final Logger logger = LoggerFactory.getLogger(ModbusApp.class);
    private static final String APP_NAME = "OpenMUC Modbus Reader/Writer App";

    private DataAccessService dataAccessService;
    private Channel modbusChannel;
    private RecordListener modbusListener;

    private Timer writeTimer;
    private int writeCounter = 100; // Starting value for write

    @Reference
    public void setDataAccessService(DataAccessService dataAccessService) {
        this.dataAccessService = dataAccessService;
    }

    @Activate
    private void activate() {
        logger.info("Activating {}", APP_NAME);

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
        } catch (Exception e) {
            logger.error("Failed to write value {} to Modbus channel 'register1': {}", valueToWrite, e.getMessage(), e);
        }
    }


    private class ModbusListener implements RecordListener {
        @Override
        public void newRecord(Record record) {
            if (record != null && record.getValue() != null) {
                logger.info("Read value from register1: {} (Timestamp: {}) (DataType: {})",
                        record.getValue(), record.getTimestamp(), record.getValue().getValueType());
            } else {
                logger.warn("Received null record or value from register1");
            }
        }
    }
}
