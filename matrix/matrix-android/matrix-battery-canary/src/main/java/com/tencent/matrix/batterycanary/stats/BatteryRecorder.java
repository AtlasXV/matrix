package com.tencent.matrix.batterycanary.stats;

import android.os.Process;

import com.tencent.matrix.batterycanary.utils.BatteryCanaryUtil;
import com.tencent.matrix.util.MatrixLog;
import com.tencent.mmkv.MMKV;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Kaede
 * @since 2021/12/10
 */
public interface BatteryRecorder {
    String TAG = "Matrix.battery.recorder";

    void write(String date, BatteryRecord record);

    List<BatteryRecord> read(String date);

    void clean(String date);


    class MMKVRecorder implements BatteryRecorder {
        private static final String MAGIC = "bs";
        private static String sProcNameSuffix = null;

        final int pid = Process.myPid();
        AtomicInteger inc = new AtomicInteger(0);
        final MMKV mmkv;

        public MMKVRecorder(MMKV mmkv) {
            this.mmkv = mmkv;
        }

        protected String getKeyPrefix(String date) {
            if (sProcNameSuffix == null) {
                String processName = BatteryCanaryUtil.getProcessName();
                if (processName.contains(":")) {
                    sProcNameSuffix = processName.substring(processName.lastIndexOf(":") + 1);
                } else {
                    sProcNameSuffix = "main";
                }
            }
            return MAGIC + "-" + date + "-" + sProcNameSuffix + "-"  + pid;
        }

        @Override
        public void write(String date, BatteryRecord record) {
            String key = getKeyPrefix(date) + "-" + inc.getAndIncrement();
            try {
                byte[] bytes = BatteryRecord.encode(record);
                mmkv.encode(key, bytes);
            } catch (Exception e) {
                MatrixLog.w(TAG, "record encode failed: " + e.getMessage());
            }
        }

        @Override
        public List<BatteryRecord> read(String date) {
            String[] keys = mmkv.allKeys();
            if (keys == null || keys.length == 0) {
                return Collections.emptyList();
            }
            List<BatteryRecord> records = new ArrayList<>(Math.min(16, keys.length));
            for (String item : keys) {
                if (item.startsWith(getKeyPrefix(date))) {
                    try {
                        byte[] bytes = mmkv.decodeBytes(item);
                        if (bytes != null) {
                            BatteryRecord record = BatteryRecord.decode(bytes);
                            records.add(record);
                        }
                    } catch (Exception e) {
                        MatrixLog.w(TAG, "record decode failed: " + e.getMessage());
                    }
                }
            }
            return records;
        }

        @Override
        public void clean(String date) {
            String[] keys = mmkv.allKeys();
            if (keys == null || keys.length == 0) {
                return;
            }
            for (String item : keys) {
                if (item.startsWith(getKeyPrefix(date))) {
                    try {
                        mmkv.remove(item);
                    } catch (Exception e) {
                        MatrixLog.w(TAG, "record clean failed: " + e.getMessage());
                    }
                }
            }
        }
    }
}
