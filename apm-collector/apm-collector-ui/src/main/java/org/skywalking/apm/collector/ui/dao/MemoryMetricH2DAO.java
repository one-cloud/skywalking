package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.storage.define.jvm.MemoryMetricTable;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * @author clevertension
 */
public class MemoryMetricH2DAO extends H2DAO implements IMemoryMetricDAO {
    private final Logger logger = LoggerFactory.getLogger(InstanceH2DAO.class);
    private static final String GET_MEMORY_METRIC_SQL = "select * from {0} where {1} =?";
    private static final String GET_MEMORY_METRICS_SQL = "select * from {0} where {1} in (";
    @Override public JsonObject getMetric(int instanceId, long timeBucket, boolean isHeap) {
        H2Client client = getClient();
        String id = timeBucket + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + isHeap;
        String sql = MessageFormat.format(GET_MEMORY_METRIC_SQL, MemoryMetricTable.TABLE, "id");
        Object[] params = new Object[]{id};
        JsonObject metric = new JsonObject();
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                metric.addProperty("max", rs.getInt(MemoryMetricTable.COLUMN_MAX));
                metric.addProperty("init", rs.getInt(MemoryMetricTable.COLUMN_INIT));
                metric.addProperty("used", rs.getInt(MemoryMetricTable.COLUMN_USED));
            } else {
                metric.addProperty("max", 0);
                metric.addProperty("init", 0);
                metric.addProperty("used", 0);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return metric;
    }

    @Override public JsonObject getMetric(int instanceId, long startTimeBucket, long endTimeBucket, boolean isHeap) {
        H2Client client = getClient();
        String sql = MessageFormat.format(GET_MEMORY_METRICS_SQL, MemoryMetricTable.TABLE, "id");
        List<String> idList = new ArrayList<>();
        long timeBucket = startTimeBucket;
        do {
            timeBucket = TimeBucketUtils.INSTANCE.addSecondForSecondTimeBucket(TimeBucketUtils.TimeBucketType.SECOND.name(), timeBucket, 1);
            String id = timeBucket + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + isHeap;
            idList.add(id);
        }
        while (timeBucket <= endTimeBucket);

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < idList.size(); i++) {
            builder.append("?,");
        }
        builder.delete(builder.length() - 1, builder.length());
        builder.append(")");
        sql = sql + builder;
        Object[] params = idList.toArray(new String[0]);
        JsonObject metric = new JsonObject();
        JsonArray usedMetric = new JsonArray();
        try (ResultSet rs = client.executeQuery(sql, params)) {
            while (rs.next()) {
                metric.addProperty("max", rs.getLong(MemoryMetricTable.COLUMN_MAX));
                metric.addProperty("init", rs.getLong(MemoryMetricTable.COLUMN_INIT));
                usedMetric.add(rs.getLong(MemoryMetricTable.COLUMN_USED));
            }
            if (usedMetric.size() == 0) {
                metric.addProperty("max", 0);
                metric.addProperty("init",0);
                usedMetric.add(0);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }

        metric.add("used", usedMetric);
        return metric;
    }
}
