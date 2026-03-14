package com.sky.mapper;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ShoppingCartMapper {

    /**
     * 动态条件查询购物车
     * @param shoppingCart
     * @return
     */
    List<ShoppingCart> list (ShoppingCart shoppingCart);

    /**
     * 根据id修改商品数量
     * @param shoppingCart
     */
    @Update("update shopping_cart set number = #{number} where id = #{id}")
    void updateNumberById(ShoppingCart shoppingCart);

    @Insert("insert into shopping_cart (name, user_id, dish_id, setmeal_id, dish_flavor, number, amount, image, create_time)" +
            " values (#{name}, #{userId}, #{dishId}, #{setmealId}, #{dishFlavor}, #{number}, #{amount}, #{image}, #{createTime})")
    void insert(ShoppingCart shoppingCart);

    /**
     * 根据用户id清空购物车
     * @param userId
     */
    @Delete("delete from shopping_cart where user_id = #{userId}")
    void deleteByUserId(Long userId);

    /**
     * 根据用户id和菜品id删除菜品
     * @param userId
     * @param dishId
     */
    @Delete("delete from shopping_cart where user_id = #{userId} and dish_id = #{dishId}")
    void deleteByUserIdWithDishId(Long userId, Long dishId);

    /**
     * 根据用户id和套餐id删除套餐
     * @param userId
     * @param setmealId
     */
    @Delete("delete from shopping_cart where user_id = #{userId} and setmeal_id = #{setmealId}")
    void deleteByUserIdWithSetmealId(Long userId, Long setmealId);
}
