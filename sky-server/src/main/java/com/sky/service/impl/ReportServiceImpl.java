package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    @Autowired
    public ReportServiceImpl(OrderMapper orderMapper, UserMapper userMapper) {
        this.orderMapper = orderMapper;
        this.userMapper = userMapper;
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
