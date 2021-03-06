package com.dtc.java.SC.ZHBB.common;

import org.apache.flink.api.common.functions.CoGroupFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.io.jdbc.JDBCInputFormat;
import org.apache.flink.api.java.io.jdbc.JDBCOutputFormat;
import org.apache.flink.api.java.operators.CoGroupOperator;
import org.apache.flink.api.java.operators.DataSource;
import org.apache.flink.api.java.operators.MapOperator;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.types.Row;
import org.apache.flink.util.Collector;

import java.text.SimpleDateFormat;

/**
 * @Author : lihao
 * Created on : 2020-04-15
 * @Description : 综合表报
 */
public class SC_Scene_ZHBB {
    /**
     * 场景一
     * */
    public static void sc_Scence_one(ExecutionEnvironment env, String driver, String url, String username, String password) {
        String sence_one_query_sql = "SELECT ifNull(y.`name`,'其他') AS sblx,ifnull(c.`name`,'其他') AS zlx,ifnull(m.`name`,'其他') AS cs,COUNT(l.`name`) AS gjsl from asset_category_mapping a left join asset b on a.asset_id=b.id \n" +
                "left join asset_category c on c.id = a.asset_category_id LEFT JOIN alarm l ON a.asset_id=l.asset_id left join asset_category y on c.parent_id = y.id \n" +
                "LEFT JOIN manufacturer m ON m.id=b.manufacturer_id GROUP BY c.`name`;";
//生产环境使用
//        String sql = "SELECT ifNull(y.`name`,'其他') AS sblx,ifnull(c.`name`,'其他') AS zlx,ifnull(m.`name`,'其他') AS cs,COUNT(l.`name`) AS gjsl from asset_category_mapping a left join asset b on a.asset_id=b.id \n" +
//                "left join asset_category c on c.id = a.asset_category_id LEFT JOIN alarm l ON a.asset_id=l.asset_id and TO_DAYS(now())-TO_DAYS(l.time_occur) =1 left join asset_category y on c.parent_id = y.id \n" +
//                "LEFT JOIN manufacturer m ON m.id=b.manufacturer_id GROUP BY c.`name`";

        String sence_one_insert_sql = "replace INTO SC_ZHBB_ZYZC(riqi,`sblx`,`zlx`,`cs`,`gjsl`,`js_time`) VALUES (?,?,?,?,?,?)";
        DataSource<Row> scene_one_query = env.createInput(JDBCInputFormat.buildJDBCInputFormat()
                .setDrivername(driver)
                .setDBUrl(url)
                .setUsername(username)
                .setPassword(password)
                .setQuery(sence_one_query_sql)
                .setRowTypeInfo(new RowTypeInfo(BasicTypeInfo.STRING_TYPE_INFO, BasicTypeInfo.STRING_TYPE_INFO, BasicTypeInfo.STRING_TYPE_INFO, BasicTypeInfo.LONG_TYPE_INFO))
                .finish());

        MapOperator<Row, Row> scene_one_query_map = scene_one_query.map(new MapFunction<Row, Row>() {
            @Override
            public Row map(Row row) throws Exception {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String riqi = sdf.format(System.currentTimeMillis());
                String js_time = sdf1.format(System.currentTimeMillis());
                String sblx = (String) row.getField(0);
                String zle = (String) row.getField(1);
                String cs = (String) row.getField(2);
                Long gasl = (Long) row.getField(3);
                Row row1 = new Row(6);
                row1.setField(0, riqi);
                row1.setField(1, sblx);
                row1.setField(2, zle);
                row1.setField(3, cs);
                row1.setField(4, gasl);
                row1.setField(5, js_time);
                return row1;
            }
        });
        scene_one_query_map.output(JDBCOutputFormat.buildJDBCOutputFormat()
                .setDrivername(driver)
                .setDBUrl(url)
                .setUsername(username)
                .setPassword(password)
                .setQuery(sence_one_insert_sql)
                .finish());
    }
/**
 * 场景二
 *
 */

    public static void sc_Scence_Two(ExecutionEnvironment env, String driver, String url, String username, String password)
    {
        //测试环境使用
        String sql = "select d.level_id,d.type_id,IFNULL(d.xzNum,0) as m ,IFNULL(d.wclNum,0) as n,(IFNULL(d.xzNum,0)-IFNULL(d.wclNum,0)) as f from (select b.level_id,b.type_id,b.xzNum,c.wclNum from (select a.level_id,a.type_id,count(*) as xzNum from alarm a group by a.level_id,a.type_id) b \n" +
                "left join (select a.level_id,a.type_id,count(*) as wclNum from alarm a where a.`status`!=2 group by a.level_id,a.type_id ) c on b.level_id= c.level_id) d ";

        //正式生产环境使用
//        String sql = "select d.level_id,d.type_id,IFNULL(d.xzNum,0) as m ,IFNULL(d.wclNum,0) as n,(IFNULL(d.xzNum,0)-IFNULL(d.wclNum,0)) as f from (select b.level_id,b.type_id,b.xzNum,c.wclNum from (select a.level_id,a.type_id,count(*) as xzNum from alarm a \n" +
//                "where TO_DAYS(now())-TO_DAYS(a.time_occur) =1 group by a.level_id,a.type_id) b left join (select a.level_id,a.type_id,count(*) as wclNum from alarm a where a.`status`!=2 and TO_DAYS(now())-TO_DAYS(a.time_occur) =1 group by \n" +
//                "a.level_id,a.type_id ) c on b.level_id= c.level_id) d";

        String insert_sql = "replace into SC_ZHBB_SCRENE_TWO(riqi,level_id,type_id,zs_Num,old_Num,wcl_Num,ycl_Num,js_time) values(?,?,?,?,?,?,?,?)";
        String sql_second = "select level_id,type_id,zs_Num,old_Num,wcl_Num,ycl_Num from SC_ZHBB_SCRENE_TWO where TO_DAYS(now())-TO_DAYS(riqi) =1";
        DataSource<Row> input = env.createInput(JDBCInputFormat.buildJDBCInputFormat()
                .setDrivername(driver)
                .setDBUrl(url)
                .setUsername(username)
                .setPassword(password)
                .setQuery(sql)
                .setRowTypeInfo(new RowTypeInfo(BasicTypeInfo.INT_TYPE_INFO, BasicTypeInfo.INT_TYPE_INFO, BasicTypeInfo.LONG_TYPE_INFO, BasicTypeInfo.LONG_TYPE_INFO, BasicTypeInfo.LONG_TYPE_INFO))
                .finish());

        DataSource<Row> input1 = env.createInput(JDBCInputFormat.buildJDBCInputFormat()
                .setDrivername(driver)
                .setDBUrl(url)
                .setUsername(username)
                .setPassword(password)
                .setQuery(sql_second)
                .setRowTypeInfo(new RowTypeInfo(BasicTypeInfo.STRING_TYPE_INFO, BasicTypeInfo.STRING_TYPE_INFO, BasicTypeInfo.INT_TYPE_INFO, BasicTypeInfo.INT_TYPE_INFO, BasicTypeInfo.INT_TYPE_INFO, BasicTypeInfo.INT_TYPE_INFO))
                .finish());
        CoGroupOperator<Row, Row, Row> with = input.coGroup(input1).where(new First_one()).equalTo(new First_one()).with(new MyCoGrouper());
        MapOperator<Row, Row> map = with.map(new MapFunction<Row, Row>() {
            @Override
            public Row map(Row row) throws Exception {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String riqi = sdf.format(System.currentTimeMillis());
                String js_time = sdf1.format(System.currentTimeMillis());
                String level_id = (String) row.getField(0);
                String type_id = (String) row.getField(1);
                String xz_num = (String) row.getField(2);
                String old_num = (String) row.getField(3);
                String wcl_num = (String) row.getField(4);
                String ycl_num = (String) row.getField(5);
                Row row1 = new Row(8);
                row1.setField(0, riqi);
                row1.setField(1, level_id);
                row1.setField(2, type_id);
                row1.setField(3, xz_num);
                row1.setField(4, old_num);
                row1.setField(5, wcl_num);
                row1.setField(6, ycl_num);
                row1.setField(7, js_time);
                return row1;
            }
        });

        map.output(JDBCOutputFormat.buildJDBCOutputFormat()
                .setDrivername(driver)
                .setDBUrl(url)
                .setUsername(username)
                .setPassword(password)
                .setQuery(insert_sql)
                .finish());
    }

    static class MyCoGrouper
            implements CoGroupFunction<Row, Row, Row> {

        @Override
        public void coGroup(Iterable<Row> iVals,
                            Iterable<Row> dVals,
                            Collector<Row> out) {
            Row row1 = new Row(6);
            Long abc = 0l;
            // add all Integer values in group to set
            for (Row val : iVals) {
                row1.setField(0, String.valueOf(val.getField(0)));
                row1.setField(1, String.valueOf(val.getField(1)));
                row1.setField(2, String.valueOf(val.getField(2)));
                row1.setField(3, String.valueOf(val.getField(3)));
                row1.setField(4, String.valueOf(val.getField(3)));
                row1.setField(5, String.valueOf(val.getField(4)));
                abc = (Long) val.getField(3);

            }
            // multiply each Double value with each unique Integer values of group
            for (Row val : dVals) {
                if (row1.getField(0) == null && row1.getField(1) == null && row1.getField(3) == null & row1.getField(5) == null) {
                    row1.setField(0, String.valueOf(val.getField(0)));
                    row1.setField(1, String.valueOf(val.getField(1)));
                    row1.setField(2, String.valueOf(0));
                    row1.setField(4, String.valueOf(0));
                    row1.setField(5, String.valueOf(0));
                }
                Integer field = (Integer) val.getField(3);
                abc += field;
                row1.setField(3, String.valueOf(abc));
            }
            out.collect(row1);
        }
    }

    private static class First_one implements KeySelector<Row, String> {
        @Override
        public String getKey(Row value) {
            return value.getField(0) + "_" + value.getField(1);
        }
    }
}
