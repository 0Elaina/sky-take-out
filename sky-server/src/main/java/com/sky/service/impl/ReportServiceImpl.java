package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {

    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final WorkspaceService workspaceService;

    @Autowired
    public ReportServiceImpl(OrderMapper orderMapper, UserMapper userMapper, WorkspaceService workspaceService) {
        this.orderMapper = orderMapper;
        this.userMapper = userMapper;
        this.workspaceService = workspaceService;
    }

    /**
     * 统计指定时间区间内的营业额数据
     * @param begin
     * @param end
     * @return
     */
    @Override
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        // 1. 创建集合用于存放 begin 到 end 范围内每天的日期
        List<LocalDate> dateList = getBetweenDates(begin, end);

        // 2. 获取营业额
        // 2.1 获取起止时间
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        // 2.2 封装参数并执行单次查询
        Map<String, Object> map = new HashMap<>();
        map.put("begin", beginTime);
        map.put("end", endTime);
        map.put("status", Orders.COMPLETED);

        List<Map<String, Object>> resultMapList = orderMapper.sumGroupByDay(map);

        // 2.3 将查询结果转换为 Map 结构, 方便快速查找
        Map<LocalDate, Double> turnoverMap = resultMapList.stream()
                .collect(Collectors.toMap(
                        m -> LocalDate.parse(m.get("order_date").toString()),
                        m -> Double.parseDouble(m.get("dayTurnover").toString())
                ));

        // 2.4 遍历日期集合, 从 Map 中取值 (处理空天数补 0)
        List<Double> trunoverList = dateList.stream()
                .map(date -> turnoverMap.getOrDefault(date, 0.0))
                .collect(Collectors.toList());

        return TurnoverReportVO
                .builder()
                .dateList(StringUtils.join(dateList, ","))
                .turnoverList(StringUtils.join(trunoverList, ","))
                .build();

    }

    /**
     * 统计指定时间区间内的用户数据
     * @param begin
     * @param end
     * @return
     */
    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        // 1. 创建集合用于存放 begin 到 end 范围内每天的日期
        List<LocalDate> dateList = getBetweenDates(begin, end);


        // 2. 创建集合存放每天新增的用户量
        List<Integer> newUserList = new ArrayList<>();
        // 3. 创建集合存放每天累计的用户量
        List<Integer> totalUserList = new ArrayList<>();

        // 4. 获取日期
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime lastEndTime = LocalDateTime.of(end, LocalTime.MAX);

        Integer totalBefore = userMapper.countBefore(beginTime);

        // 5. 获取区间内的“增量”
        // 返回 List<Map>, 包含 regDate, dailyNew
        List<Map<String, Object>> stats = userMapper.getDailyNewUser(beginTime, lastEndTime);

        // 6. 将增量数据转为 Map 结构 (Key: 日期, Value: 新增数)
        Map<LocalDate, Integer> newMap = stats.stream().collect(Collectors.toMap(
                m -> LocalDate.parse(m.get("regDate").toString()),
                m -> ((Number) m.get("dailyNew")).intValue()
        ));

        // 7. 处理 "空天数" 和 "初始总量"
        int runningTotal = (totalBefore != null) ? totalBefore : 0;
        for (LocalDate date : dateList) {
            int todayNew = newMap.getOrDefault(date, 0);
            runningTotal += todayNew; // 关键：昨天的总数 + 今天新增 = 今天的总数

            newUserList.add(todayNew);
            totalUserList.add(runningTotal);
        }
        return UserReportVO
                .builder()
                .dateList(StringUtils.join(dateList, ","))
                .newUserList(StringUtils.join(newUserList, ","))
                .totalUserList(StringUtils.join(totalUserList, ","))
                .build();
    }

    /**
     * 统计指定时间区间内的订单数据
     * @param begin
     * @param end
     * @return
     */
    @Override
    public OrderReportVO getOrdersStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = getBetweenDates(begin, end);

        // 1. 一次性从数据库中获取统计数据
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);
        List<Map<String, Object>> mapList = orderMapper.getOrdersStatistics(beginTime, endTime, Orders.COMPLETED);

        // 2. 将数据库 List 转为 Map, 方便按日期快速查找
        Map<String, Map<String, Object>> statsMap = mapList.stream()
                .collect(Collectors.toMap(
                        m -> m.get("reportDate").toString(),
                        m -> m
                ));

        // 3. 定义存储结果的集合
        List<Integer> orderCountList = new ArrayList<>();
        List<Integer> validOrderCountList = new ArrayList<>();

        // 4. 遍历 dateList, 保证数据顺序并处理 "某天无订单" 的情况
        for (LocalDate date : dateList) {
            Map<String, Object> dayData = statsMap.get(date.toString());

            // 推荐使用 Number 转换，比 Integer.parseInt(toString()) 更高效且安全
            int total = (dayData != null && dayData.get("orderCount") != null)
                    ? ((Number) dayData.get("orderCount")).intValue() : 0;
            int valid = (dayData != null && dayData.get("validOrderCount") != null)
                    ? ((Number) dayData.get("validOrderCount")).intValue() : 0;

            orderCountList.add(total);
            validOrderCountList.add(valid);
        }

        // 5. 计算时间区间内的汇总值
        int totalOrderCount = orderCountList.stream().mapToInt(Integer::intValue).sum();
        int validOrderCount = validOrderCountList.stream().mapToInt(Integer::intValue).sum();

        // 6. 计算订单完成率
        Double orderCompletionRate = 0.0;
        if (totalOrderCount != 0) {
            orderCompletionRate = (double) validOrderCount / totalOrderCount;
        }

        // 7. 封装 VO 返回
        return OrderReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .orderCountList(StringUtils.join(orderCountList, ","))
                .validOrderCountList(StringUtils.join(validOrderCountList, ","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
    }

    /**
     * 统计指定时间区间内的销量排名top10
     * @param begin
     * @param end
     * @return
     */
    @Override
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        List<GoodsSalesDTO> salesTop10 = orderMapper.getSalesTop10(beginTime, endTime);
        String nameList = StringUtils.join(
                salesTop10.stream()
                        .map(GoodsSalesDTO::getName)
                        .collect(Collectors.toList()), ",");
        String numberList = StringUtils.join(
                salesTop10.stream()
                        .map(GoodsSalesDTO::getNumber)
                        .collect(Collectors.toList()), ",");

        return SalesTop10ReportVO
                .builder()
                .nameList(nameList)
                .numberList(numberList)
                .build();
    }

    /**导出近30天的运营数据报表
     * @param response
     **/
    public void exportBusinessData(HttpServletResponse response) {
        LocalDate begin = LocalDate.now().minusDays(30);
        LocalDate end = LocalDate.now().minusDays(1);
        //查询概览运营数据，提供给Excel模板文件
        BusinessDataVO businessData = workspaceService.getBusinessData(LocalDateTime.of(begin,LocalTime.MIN), LocalDateTime.of(end, LocalTime.MAX));
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");
        try {
            //基于提供好的模板文件创建一个新的Excel表格对象
            XSSFWorkbook excel = new XSSFWorkbook(inputStream);
            //获得Excel文件中的一个Sheet页
            XSSFSheet sheet = excel.getSheet("Sheet1");

            sheet.getRow(1).getCell(1).setCellValue(begin + "至" + end);
            //获得第4行
            XSSFRow row = sheet.getRow(3);
            //获取单元格
            row.getCell(2).setCellValue(businessData.getTurnover());
            row.getCell(4).setCellValue(businessData.getOrderCompletionRate());
            row.getCell(6).setCellValue(businessData.getNewUsers());
            row = sheet.getRow(4);
            row.getCell(2).setCellValue(businessData.getValidOrderCount());
            row.getCell(4).setCellValue(businessData.getUnitPrice());
            for (int i = 0; i < 30; i++) {
                LocalDate date = begin.plusDays(i);
                //准备明细数据
                businessData = workspaceService.getBusinessData(LocalDateTime.of(date,LocalTime.MIN), LocalDateTime.of(date, LocalTime.MAX));
                row = sheet.getRow(7 + i);
                row.getCell(1).setCellValue(date.toString());
                row.getCell(2).setCellValue(businessData.getTurnover());
                row.getCell(3).setCellValue(businessData.getValidOrderCount());
                row.getCell(4).setCellValue(businessData.getOrderCompletionRate());
                row.getCell(5).setCellValue(businessData.getUnitPrice());
                row.getCell(6).setCellValue(businessData.getNewUsers());
            }
            //通过输出流将文件下载到客户端浏览器中
            ServletOutputStream out = response.getOutputStream();
            excel.write(out);
            //关闭资源
            out.flush();
            out.close();
            excel.close();

        }catch (IOException e){
            e.printStackTrace();
        }
    }


    /**
     * 获取两个日期之间的所有日期
     * @param begin
     * @param end
     * @return
     */
    private List<LocalDate> getBetweenDates(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();

        dateList.add(begin);
        LocalDate temp = begin;

        while(!temp.equals(end)) {
            if (temp.isAfter(end)) {
                throw new RuntimeException("开始时间不能大于结束时间");
            }
            // 2. 日期计算, 计算指定日期的后一天对应的日期
            temp = temp.plusDays(1);
            dateList.add(temp);
        }
        return dateList;
    }
}
