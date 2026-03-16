package com.sky.mapper;

import com.sky.entity.User;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper {

    /**
     * 根据openid查询用户
     * @param openid
     * @return
     */
    @Select("select * from user where openid = #{openid}")
    User getByOpenid(String openid);

    /**
     * 插入用户数据
     * @param user
     */
    void insert(User user);

    /**
     * 根据id查询用户
     * @param userId
     * @return
     */
    @Select("select * from user where id = #{userId}")
    User getById(Long userId);

    /**
     * 统计用户数量
     * @param lastEndTime
     * @return
     */
    @MapKey("name")
    List<Map<String, Object>> getDailyNewUser(LocalDateTime beginTime, LocalDateTime lastEndTime);

    /**
     * 查询指定时间的用户总数
     * @param beginTime
     * @return
     */
    @Select("select count(id) from user where create_time < #{beginTime}")
    Integer countBefore(LocalDateTime beginTime);
}
