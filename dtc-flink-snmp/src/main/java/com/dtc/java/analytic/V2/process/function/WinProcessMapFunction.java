package com.dtc.java.analytic.V2.process.function;

import com.dtc.java.analytic.V1.common.constant.PropertiesConstants;
import com.dtc.java.analytic.V2.common.model.DataStruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.api.java.tuple.Tuple6;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created on 2020-02-21
 *
 * @author :hao.li
 */
@Slf4j
public class WinProcessMapFunction extends ProcessWindowFunction<DataStruct, DataStruct, Tuple, TimeWindow> {

    //磁盘描述
    private Map<String, String> diskDescribe = new HashMap();
    //磁盘每个块的大小
    private Map<String, String> diskBlockSize = new HashMap();
    //磁盘块的个数
    private Map<String, String> diskBlockNum = new HashMap();
    //磁盘容量
    private Map<String, String> diskCaption = new HashMap();
    //cpu的核数
    private Map<String, String> cpuNum = new HashMap<>();
    private boolean flag = false;

    @Override
    public void process(Tuple tuple, Context context, Iterable<DataStruct> iterable, Collector<DataStruct> collector) throws Exception {
        ParameterTool parameters = (ParameterTool)
                getRuntimeContext().getExecutionConfig().getGlobalJobParameters();

        String userName = parameters.get(PropertiesConstants.MYSQL_USERNAME);
        String passWord = parameters.get(PropertiesConstants.MYSQL_PASSWORD);
        String host = parameters.get(PropertiesConstants.MYSQL_HOST);
        String port = parameters.get(PropertiesConstants.MYSQL_PORT);
        String database = parameters.get(PropertiesConstants.MYSQL_DATABASE);
        String mysql_win_table_sql = parameters.get(PropertiesConstants.MYSQL_WINDOWS_TABLE);
        double cpu_sum = 0;
        double net_num = 0;
        double rec_total = 0;
        double rec_total_count = 0;
        double sent_total = 0;
        double sent_total_count = 0;
        double discard_package_in_num = 0;
        double discard_in_count = 0;
        double discard_package_out_num = 0;
        double discard_out_count = 0;
        double error_in_num = 0;
        double error_in_count = 0;
        double error_out_num = 0;
        double error_out_count = 0;
        double cpuCount = 0;
        int count = 0;
        Map<String, String> usedDiskMap = new HashMap<>();

        for (DataStruct wc : iterable) {
            String keyValue = wc.getHost() + "_" + wc.getZbLastCode();

            /**
             *
             * 系统启动时间
             *
             * */
            if ("101_100_105_102_102".equals(wc.getZbFourName())) {
                //TODO:写hbase
                collector.collect(new DataStruct(wc.getSystem_name(), wc.getHost(), wc.getZbFourName(), wc.getZbLastCode(), wc.getNameCN(), wc.getNameEN(), wc.getTime(), wc.getValue()));
                continue;
            }


            /**
             * cpu使用率
             * */
            if ("101_100_101_101_101".equals(wc.getZbFourName())) {
                if (!cpuNum.containsKey(keyValue)) {
                    cpuNum.put(keyValue, "1");
                    continue;
                } else {
                    flag = true;
                }
            }
            if (flag) {
                if ("101_100_101_101_101".equals(wc.getZbFourName())) {
                    if (!(cpuCount == cpuNum.size())) {
                        cpuCount += 1;
                        cpu_sum += Double.parseDouble(wc.getValue());
                        continue;
                    } else {
                        if (count < 1) {
                            double result = cpu_sum / cpuNum.size();
                            collector.collect(new DataStruct(wc.getSystem_name(), wc.getHost(), wc.getZbFourName(), "", wc.getNameCN(), wc.getNameEN(), wc.getTime(), String.valueOf(result)));
                            count += 1;
                            continue;
                        }
                    }
                }

            }
            /**
             * 磁盘描述：存储的目的是为了保存指标与磁盘的对应关系
             * */
            if ("101_100_103_103_103".equals(wc.getZbFourName())) {
                if ((!diskDescribe.containsKey(keyValue)) || (diskDescribe.containsKey(keyValue) && (!diskDescribe.get(keyValue).equals(wc.getValue())))) {
                    diskDescribe.put(keyValue, wc.getValue());
                    String Url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useUnicode=true&characterEncoding=UTF-8";
                    String JDBCDriver = "com.mysql.jdbc.Driver";
                    Connection con = null;
                    try {
                        //向DriverManager注册自己
                        Class.forName(JDBCDriver);
                        //与数据库建立连接
                        con = DriverManager.getConnection(Url, userName, passWord);
                        //用来执行SQL语句查询，对sql语句进行预编译处理
                        PreparedStatement pst = con.prepareStatement(mysql_win_table_sql);
                        pst.setString(1, wc.getHost());
                        pst.setString(2, wc.getZbLastCode());
                        pst.setString(3, wc.getValue());
                        pst.executeUpdate();
                    } catch (ClassNotFoundException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } finally {
                        if (con != null) {
                            con.close();
                        }

                    }
                }
            }
            /**
             * 每个块的大小
             * */
            if ("101_100_103_104_104".equals(wc.getZbFourName())) {
                if ((!diskBlockSize.containsKey(keyValue)) || (diskBlockSize.containsKey(keyValue) && (!diskBlockSize.get(keyValue).equals(wc.getValue())))) {
                    diskBlockSize.put(keyValue, wc.getValue());
                }
            }
            /**
             * 磁盘块的个数
             * */
            if ("101_100_103_105_105".equals(wc.getZbFourName())) {
                if ((!diskBlockNum.containsKey(keyValue)) || (diskBlockNum.containsKey(keyValue) && (!diskBlockNum.get(keyValue).equals(wc.getValue())))) {
                    diskBlockNum.put(keyValue, wc.getValue());
                }
            }


            /**
             * 每个盘的总容量
             * */
            if (getLikeByMap(diskBlockNum, wc.getHost()) == getLikeByMap(diskBlockSize, wc.getHost())) {
                for (String keyA : diskBlockSize.keySet()) {
                    for (String keyB : diskBlockNum.keySet()) {
                        if (keyA.equals(keyB)) {
                            double valueA = Double.parseDouble(diskBlockNum.get(keyA));
                            double valueB = Double.parseDouble(diskBlockSize.get(keyB));
                            if ((!diskCaption.containsKey(keyA)) || (diskCaption.containsKey(keyA) && !(diskCaption.get(keyA).equals(String.valueOf(valueA * valueB))))) {
                                diskCaption.put(keyA, String.valueOf(valueA * valueB));
                            }
                        }
                    }
                }
            }
            /**
             * 每个磁盘使用率
             * 虚拟/物理内存使用率
             * */
            if ("101_100_103_106_106".equals(wc.getZbFourName()) && diskCaption.containsKey(keyValue)) {
                Double used_disk = Double.parseDouble(wc.getValue()) * Double.parseDouble(diskBlockSize.get(keyValue));
                Double diskUsedCapacity = Double.parseDouble(diskCaption.get(keyValue));
                //磁盘使用量
//                collector.collect(new DataStruct(wc.getSystem_name(), wc.getHost(), wc.getZbFourName(), wc.getZbLastCode(), wc.getNameCN(), wc.getNameEN(), wc.getTime(), String.valueOf(used_disk)));
//                //磁盘总量
//                collector.collect(new DataStruct(wc.getSystem_name(), wc.getHost(), wc.getZbFourName(), wc.getZbLastCode(), wc.getNameCN(), wc.getNameEN(), wc.getTime(), String.valueOf(diskUsedCapacity)));
                //磁盘使用率
                Double rato_used_disk = used_disk / diskUsedCapacity;
                collector.collect(new DataStruct(wc.getSystem_name(), wc.getHost(), "101_100_103_107_107", wc.getZbLastCode(), wc.getNameCN(), wc.getNameEN(), wc.getTime(), String.valueOf(rato_used_disk)));
                usedDiskMap.put(keyValue, wc.getValue());
                if (usedDiskMap.size() == diskBlockSize.size()) {
                    Double re = 0.0;
                    Double res = 0.0;
                    for (String s : usedDiskMap.values()) {
                        re += Double.parseDouble(s);
                    }
                    for (String s : diskCaption.values()) {
                        res += Double.parseDouble(s);
                    }
                    collector.collect(new DataStruct(wc.getSystem_name(), wc.getHost(), "101_100_103_108_108", "", wc.getNameCN(), wc.getNameEN(), wc.getTime(), String.valueOf(rato_used_disk)));
                }
                continue;
            }
            /**
             *
             * 网络接口相关
             * */
            if ("101_100_104_101_101".equals(wc.getZbFourName())) {
                net_num = Double.valueOf(wc.getValue());
                collector.collect(new DataStruct(wc.getSystem_name(), wc.getHost(), wc.getZbFourName(), "", wc.getNameCN(), wc.getNameEN(), wc.getTime(), wc.getValue()));
                continue;
            }
            if ("101_100_104_102_102".equals(wc.getZbFourName())) {
                rec_total += Double.valueOf(wc.getValue());
                rec_total_count += rec_total_count;
                if (rec_total_count == net_num) {
                    collector.collect(new DataStruct(wc.getSystem_name(), wc.getHost(), wc.getZbFourName(), "", wc.getNameCN(), wc.getNameEN(), wc.getTime(), String.valueOf(rec_total)));
                }
            } else if ("101_100_104_103_103".equals(wc.getZbFourName())) {
                sent_total += Double.valueOf(wc.getValue());
                sent_total_count += sent_total_count;
                if (sent_total_count == net_num) {
                    collector.collect(new DataStruct(wc.getSystem_name(), wc.getHost(), wc.getZbFourName(), "", wc.getNameCN(), wc.getNameEN(), wc.getTime(), String.valueOf(sent_total)));
                }
            } else if ("101_100_104_104_104".equals(wc.getZbFourName())) {
                discard_package_in_num += Double.valueOf(wc.getValue());
                discard_in_count += discard_in_count;
                if (discard_in_count == net_num) {
                    collector.collect(new DataStruct(wc.getSystem_name(), wc.getHost(), wc.getZbFourName(), "", wc.getNameCN(), wc.getNameEN(), wc.getTime(), String.valueOf(discard_package_in_num)));
                }
            } else if ("101_100_104_105_105".equals(wc.getZbFourName())) {
                error_in_num += Double.valueOf(wc.getValue());
                error_in_count += error_in_count;
                if (error_in_count == net_num) {
                    collector.collect(new DataStruct(wc.getSystem_name(), wc.getHost(), wc.getZbFourName(), "", wc.getNameCN(), wc.getNameEN(), wc.getTime(), String.valueOf(error_in_num)));
                }
            } else if ("101_100_104_106_106".equals(wc.getZbFourName())) {
                discard_package_out_num += Double.valueOf(wc.getValue());
                discard_out_count += discard_out_count;
                if (discard_out_count == net_num) {
                    collector.collect(new DataStruct(wc.getSystem_name(), wc.getHost(), wc.getZbFourName(), "", wc.getNameCN(), wc.getNameEN(), wc.getTime(), String.valueOf(discard_package_out_num)));
                }
            } else if ("101_100_104_107_107".equals(wc.getZbFourName())) {
                error_out_count += Double.valueOf(wc.getValue());
                error_out_num += error_out_num;
                if (error_out_num == net_num) {
                    collector.collect(new DataStruct(wc.getSystem_name(), wc.getHost(), wc.getZbFourName(), "", wc.getNameCN(), wc.getNameEN(), wc.getTime(), String.valueOf(error_out_count)));
                }
            }
        }
    }


    private int getLikeByMap(Map<String, String> map, String keyLike) {
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, String> entity : map.entrySet()) {
            if (entity.getKey().startsWith(keyLike)) {
                list.add(entity.getValue());
            }
        }
        return list.size();
    }
}