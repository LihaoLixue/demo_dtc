package com.dtc.java.shucang.JFSBWGBGJ;


import com.dtc.java.analytic.V1.alter.MySQLUtil;
import com.dtc.java.analytic.V1.common.constant.PropertiesConstants;
import com.dtc.java.shucang.JFSBWGBGJ.model.ZongShu;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.source.RichSourceFunction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author : lihao
 * Created on : 2020-03-24
 * @Description : 各区域各机柜设备未关闭告警数
 */
@Slf4j
public class ReadDataQY_WGBGJ extends RichSourceFunction<ZongShu> {

    private Connection connection = null;
    private PreparedStatement ps = null;
    private volatile boolean isRunning = true;
    private ParameterTool parameterTool;


    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        parameterTool = (ParameterTool) (getRuntimeContext().getExecutionConfig().getGlobalJobParameters());
        String database = parameterTool.get(PropertiesConstants.MYSQL_DATABASE);
        String host = parameterTool.get(PropertiesConstants.MYSQL_HOST);
        String password = parameterTool.get(PropertiesConstants.MYSQL_PASSWORD);
        String port = parameterTool.get(PropertiesConstants.MYSQL_PORT);
        String username = parameterTool.get(PropertiesConstants.MYSQL_USERNAME);
        String alarm_rule_table = parameterTool.get(PropertiesConstants.MYSQL_ALAEM_TABLE);

        String driver = "com.mysql.jdbc.Driver";
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useUnicode=true&characterEncoding=UTF-8";
        connection = MySQLUtil.getConnection(driver, url, username, password);

        if (connection != null) {
           String sql =  "select a.room,a.position,a.box,count(*) as num from asset a where a.id not in (select distinct asset_id from alarm b where b.`status`=2) group by a.room,a.position,a.box having a.room is not null and a.position is not null and a.box is not null";
            ps = connection.prepareStatement(sql);
        }
    }

    @Override
    public void run(SourceContext<ZongShu> ctx) throws Exception {
        Tuple4<String, String, Short, String> test = null;
        int num = 0;
        while (isRunning) {
            ResultSet resultSet = ps.executeQuery();
            while (resultSet.next()) {
                String room = resultSet.getString("room").trim();
                String position = resultSet.getString("position").trim();
                String box = resultSet.getString("box").trim();
                num = resultSet.getInt("num");
                ZongShu order = new ZongShu(room,position,box,num);
                ctx.collect(order);
            }
            Thread.sleep(1000 * 6);
        }
    }

    @Override
    public void cancel() {
        try {
            super.close();
            if (connection != null) {
                connection.close();
            }
            if (ps != null) {
                ps.close();
            }
        } catch (Exception e) {
            log.error("runException:{}", e);
        }
        isRunning = false;
    }
}
