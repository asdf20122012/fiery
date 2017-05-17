package com.ragnar.server.Processor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ragnar.server.statistics.ErrorStatics;
import com.ragnar.server.statistics.LogAPIStatics;
import com.ragnar.server.statistics.SQLStatics;
import com.ragnar.server.storage.DBManage;
import com.ragnar.server.storage.DBSharder;
import com.ragnar.server.util.DateTimeHepler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentLinkedQueue;

@Component
@Scope("singleton")
public class BizLogProcessor {

    private ConcurrentLinkedQueue<JsonArray> BizLogQueue = new ConcurrentLinkedQueue<>();

    //log obj
    private Logger log = LoggerFactory.getLogger(BizLogProcessor.class);

    @Autowired
    private DBManage dbmanager;

    @Autowired
    private LogAPIStatics logApi;

    @Autowired
    private SQLStatics sqlStatics;

    @Autowired
    private ErrorStatics errorStatics;

    BizLogProcessor() {

    }

    public Integer getQueueLen(){
        return BizLogQueue.size();
    }

    //main process data
    public void insertDataQueue(JsonArray data) {
        if (data != null) {
            BizLogQueue.add(data);
        }
    }

    @Scheduled(fixedRate = 500)
    private void processData() {

        int totalProcess = 0;
        JsonArray valueArr = BizLogQueue.poll();
        while (valueArr != null) {

            for (int index = 0; index < valueArr.size(); index++) {
                JsonObject valueObj = valueArr.get(index).getAsJsonObject();
                String traceid = valueObj.get("key").getAsString();
                String rpcid = valueObj.get("rpcid").getAsString();
                String timestamp = valueObj.get("timestamp").getAsString();

                //ignore the wrong one
                if (traceid.length() <= 0 || rpcid.length() <= 0 || timestamp.length() <= 0) {
                    continue;
                }

                //sotre to the rocksdb
                Long timestampLong = Long.parseLong(timestamp);
                try {
                    DBSharder dbHelper = dbmanager.getDB(timestampLong);
                    //save the kv db
                    dbHelper.put(traceid + "_" + rpcid, valueArr.toString());
                    dbHelper.merge(traceid + "_index", rpcid);
                } catch (Exception e) {
                    e.printStackTrace();
                    log.error(e.getMessage());
                }

                //statics
                JsonArray arr = valueObj.get("val").getAsJsonArray();
                for (int k = 0; k < arr.size(); k++) {
                    JsonObject obj = arr.get(k).getAsJsonObject();
                    Integer type = obj.get("t").getAsInt();
                    String path = obj.get("p").getAsString();
                    String line = obj.get("l").getAsString();

                    if (5 == type || 6 == type || 7 == type) {
                        JsonObject mObj = obj.get("m").getAsJsonObject();
                        mObj.addProperty("mytraceid", traceid);
                        mObj.addProperty("myrpcid", rpcid);
                        mObj.addProperty("filepath", path);
                        mObj.addProperty("fileline", line);
                        String msg = mObj.toString();
                        errorStatics.addAlarmLogMap(type, msg, timestampLong);
                        continue;
                    }

                    if (9 != type ||
                            obj.get("g").isJsonNull() ||
                            obj.get("e").isJsonNull() ||
                            obj.get("c").isJsonNull() ||
                            obj.get("m").isJsonNull()
                            ) {
                        continue;
                    }

                    String bflog = obj.get("g").getAsString();
                    Double costTime = obj.get("c").getAsDouble();
                    Integer hourTime = DateTimeHepler.getHourTime(timestampLong);

                    JsonObject msgObj;
                    if (obj.get("m").isJsonObject()) {
                        msgObj = obj.get("m").getAsJsonObject();
                    } else {
                        JsonParser jsonParser = new JsonParser();

                        try {
                            //log.info(obj.get("m").getAsString());
                            msgObj = jsonParser.parse(obj.get("m").getAsString()).getAsJsonObject();
                        } catch (Exception e) {
                            //e.printStackTrace();
                            continue;
                        }
                    }

                    if (bflog.equals("curl")) {
                        if (msgObj.get("url").isJsonNull()) {
                            continue;
                        }

                        String url = msgObj.get("url").getAsString();
                        JsonObject infoObj = msgObj.get("info").getAsJsonObject();
                        if (infoObj.get("http_code").isJsonNull()) {
                            continue;
                        }

                        String code = infoObj.get("http_code").getAsString();
                        logApi.addPerformMap(url, hourTime, costTime, code);

                    } else if (bflog.equals("mysql")) {

                        if (msgObj.get("sql").isJsonNull()) {
                            continue;
                        }

                        String sql = msgObj.get("sql").getAsString();
                        sqlStatics.addSqlMap(sql, hourTime, costTime);
                    }
                }
            }

            totalProcess++;

            if (totalProcess > 1000) {
                break;
            }

            //ok next one
            valueArr = BizLogQueue.poll();
        }
    }
}
