package com.docstorage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docstorage.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Set;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 根据用户ID查询其拥有的权限编码集合
     * 用于接口权限拦截
     */
    @Select("SELECT DISTINCT p.perm_code " +
            "FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON p.id = rp.permission_id " +
            "INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND p.deleted = 0")
    Set<String> selectPermCodesByUserId(@Param("userId") Long userId);
}