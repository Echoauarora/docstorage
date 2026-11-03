package com.docstorage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docstorage.entity.DocFilePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文件权限 Mapper
 * <p>
 * 核心权限查询思路 (三级权限判断):
 * 1. 用户直接授权 (doc_file_permission.user_id)
 * 2. 角色授权 (通过 sys_user_role → sys_role_permission 链路)
 * 3. 所有者天然拥有全部权限 (doc_file.owner_id = userId)
 */
@Mapper
public interface DocFilePermissionMapper extends BaseMapper<DocFilePermission> {

    /**
     * 检查用户是否拥有指定文件的指定权限
     * 优先级: 文件所有者 > 直接用户授权 > 角色授权
     */
    @Select("SELECT COUNT(1) FROM (" +
            "    SELECT 1 FROM doc_file f WHERE f.id = #{fileId} AND f.owner_id = #{userId} AND f.deleted = 0 " +
            "    UNION ALL " +
            "    SELECT 1 FROM doc_file_permission fp " +
            "    WHERE fp.file_id = #{fileId} AND fp.user_id = #{userId} AND fp.perm_type = #{permType} " +
            "    UNION ALL " +
            "    SELECT 1 FROM doc_file_permission fp " +
            "    INNER JOIN sys_user_role ur ON fp.role_id = ur.role_id " +
            "    WHERE fp.file_id = #{fileId} AND ur.user_id = #{userId} AND fp.perm_type = #{permType}" +
            ") t")
    int countUserHasPermission(@Param("fileId") Long fileId,
                               @Param("userId") Long userId,
                               @Param("permType") Integer permType);
}