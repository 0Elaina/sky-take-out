package com.sky.service.impl;

import com.sky.constant.StatusConstant;
import com.sky.entity.Orders;
import com.sky.mapper.DishMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.WorkspaceService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.SetmealOverViewVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class WorkspaceServiceImpl implements WorkspaceService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 根据时间段统计营业数据
     * @param begin
     * @param end
     * @return
     */
    public BusinessDataVO getBusinessData(LocalDateTime begin, LocalDateTime end) {
        /**
         * 营业额：当日已完成订单的总金额
         * 有效订单：当日已完成订单的数量
         * 订单完成率：有效订单数 / 总订单数
         * 平均客单价：营业额 / 有效订单数
         * 新增用户：当日新增用户的数量
         */

        //查询总订单数
        List<Map<String, Object>> totalOrderList = orderMapper.getOrdersStatistics(begin, end, null);
        Integer totalOrderCount = 0;
        if (totalOrderList != null && !totalOrderList.isEmpty()) {
            for (Map<String, Object> map : totalOrderList) {
                totalOrderCount += ((Number) map.get("totalCount")).intValue();
            }
        }

        //营业额和有效订单数（状态为 COMPLETED）
        List<Map<String, Object>> completedOrderList = orderMapper.getOrdersStatistics(begin, end, Orders.COMPLETED);
        Double turnover = 0.0;
        Integer validOrderCount = 0;
        
        // 计算营业额
        Map<String, Object> sumMap = new HashMap<>();
        sumMap.put("begin", begin);
        sumMap.put("end", end);
        sumMap.put("status", Orders.COMPLETED);
        List<Map<String, Object>> turnoverList = orderMapper.sumGroupByDay(sumMap);
        if (turnoverList != null && !turnoverList.isEmpty()) {
            for (Map<String, Object> map : turnoverList) {
                turnover += ((Number) map.get("dayTurnover")).doubleValue();
            }
        }

        // 计算有效订单数
        if (completedOrderList != null && !completedOrderList.isEmpty()) {
            for (Map<String, Object> map : completedOrderList) {
                validOrderCount += ((Number) map.get("validCount")).intValue();
            }
        }

        Double unitPrice = 0.0;
        Double orderCompletionRate = 0.0;
        if(totalOrderCount != 0 && validOrderCount != 0){
            //订单完成率
            orderCompletionRate = validOrderCount.doubleValue() / totalOrderCount;
            //平均客单价
            unitPrice = turnover / validOrderCount;
        }

        //新增用户数
        List<Map<String, Object>> newUsersList = userMapper.getDailyNewUser(begin.with(LocalTime.MIN), end.with(LocalTime.MAX));
        Integer newUsers = 0;
        if (newUsersList != null && !newUsersList.isEmpty()) {
            for (Map<String, Object> map : newUsersList) {
                newUsers += ((Number) map.get("dailyNew")).intValue();
            }
        }

        return BusinessDataVO.builder()
                .turnover(turnover)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .unitPrice(unitPrice)
                .newUsers(newUsers)
                .build();
    }


    /**
     * 查询订单管理数据
     *
     * @return
     */
    public OrderOverViewVO getOrderOverView() {
        LocalDateTime begin = LocalDateTime.now().with(LocalTime.MIN);
        LocalDateTime end = LocalDateTime.now().with(LocalTime.MAX);

        //待接单
        List<Map<String, Object>> waitingOrderList = orderMapper.getOrdersStatistics(begin, end, Orders.TO_BE_CONFIRMED);
        Integer waitingOrders = 0;
        if (waitingOrderList != null && !waitingOrderList.isEmpty()) {
            for (Map<String, Object> map : waitingOrderList) {
                waitingOrders += ((Number) map.get("validCount")).intValue();
            }
        }

        //待派送
        List<Map<String, Object>> deliveredOrderList = orderMapper.getOrdersStatistics(begin, end, Orders.CONFIRMED);
        Integer deliveredOrders = 0;
        if (deliveredOrderList != null && !deliveredOrderList.isEmpty()) {
            for (Map<String, Object> map : deliveredOrderList) {
                deliveredOrders += ((Number) map.get("validCount")).intValue();
            }
        }

        //已完成
        List<Map<String, Object>> completedOrderList = orderMapper.getOrdersStatistics(begin, end, Orders.COMPLETED);
        Integer completedOrders = 0;
        if (completedOrderList != null && !completedOrderList.isEmpty()) {
            for (Map<String, Object> map : completedOrderList) {
                completedOrders += ((Number) map.get("validCount")).intValue();
            }
        }

        //已取消
        List<Map<String, Object>> cancelledOrderList = orderMapper.getOrdersStatistics(begin, end, Orders.CANCELLED);
        Integer cancelledOrders = 0;
        if (cancelledOrderList != null && !cancelledOrderList.isEmpty()) {
            for (Map<String, Object> map : cancelledOrderList) {
                cancelledOrders += ((Number) map.get("validCount")).intValue();
            }
        }

        //全部订单
        List<Map<String, Object>> allOrderList = orderMapper.getOrdersStatistics(begin, end, null);
        Integer allOrders = 0;
        if (allOrderList != null && !allOrderList.isEmpty()) {
            for (Map<String, Object> map : allOrderList) {
                allOrders += ((Number) map.get("totalCount")).intValue();
            }
        }

        return OrderOverViewVO.builder()
                .waitingOrders(waitingOrders)
                .deliveredOrders(deliveredOrders)
                .completedOrders(completedOrders)
                .cancelledOrders(cancelledOrders)
                .allOrders(allOrders)
                .build();
    }

    /**
     * 查询菜品总览
     *
     * @return
     */
    public DishOverViewVO getDishOverView() {
        Map map = new HashMap();
        map.put("status", StatusConstant.ENABLE);
        Integer sold = dishMapper.countByMap(map);

        map.put("status", StatusConstant.DISABLE);
        Integer discontinued = dishMapper.countByMap(map);

        return DishOverViewVO.builder()
                .sold(sold)
                .discontinued(discontinued)
                .build();
    }

    /**
     * 查询套餐总览
     *
     * @return
     */
    public SetmealOverViewVO getSetmealOverView() {
        Map map = new HashMap();
        map.put("status", StatusConstant.ENABLE);
        Integer sold = setmealMapper.countByMap(map);

        map.put("status", StatusConstant.DISABLE);
        Integer discontinued = setmealMapper.countByMap(map);

        return SetmealOverViewVO.builder()
                .sold(sold)
                .discontinued(discontinued)
                .build();
    }
}
