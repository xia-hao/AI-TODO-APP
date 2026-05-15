package com.todo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.todo.entity.Subtask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SubtaskMapper extends BaseMapper<Subtask> {

    @Update("UPDATE subtasks SET sort_order = #{sortOrder}, updated_at = NOW() WHERE id = #{id}")
    void updateSortOrder(@Param("id") Long id, @Param("sortOrder") Integer sortOrder);
}