package com.todo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.todo.entity.Todo;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TodoMapper extends BaseMapper<Todo> {

    @Update("UPDATE todos SET sort_order = #{sortOrder} WHERE id = #{id}")
    void updateSortOrder(@Param("id") Long id, @Param("sortOrder") Integer sortOrder);

    @Select("SELECT * FROM todos WHERE deleted_time IS NOT NULL AND owner_id = #{userId} ORDER BY deleted_time DESC")
    List<Todo> selectDeleted(@Param("userId") Long userId);

    @Select("SELECT * FROM todos WHERE id = #{id}")
    Todo selectByIdIgnoreLogic(@Param("id") Long id);

    @Delete("DELETE FROM todos WHERE id = #{id}")
    int forceDelete(@Param("id") Long id);

    @Update("UPDATE todos SET deleted_time = NULL, deleted = 0 WHERE id = #{id}")
    int restoreById(@Param("id") Long id);

    @Select("SELECT * FROM todos WHERE deleted_time IS NOT NULL AND deleted_time < #{deadline}")
    List<Todo> selectExpiredDeleted(@Param("deadline") LocalDateTime deadline);

    @Delete("DELETE FROM todos WHERE deleted_time IS NOT NULL AND deleted_time < #{deadline}")
    int forceDeleteExpired(@Param("deadline") LocalDateTime deadline);
}
