package edu.campus.lostfound.admin;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("local")
public interface AdminAuditLogMapper {

    @Insert("""
            INSERT INTO admin_audit_log
                (admin_id, action, target_type, target_id, before_json, after_json, request_id, ip, user_agent)
            VALUES
                (#{adminId}, #{action}, #{targetType}, #{targetId}, #{beforeJson}, #{afterJson}, #{requestId}, #{ip}, #{userAgent})
            """)
    int insert(
            @Param("adminId") Long adminId,
            @Param("action") String action,
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            @Param("beforeJson") String beforeJson,
            @Param("afterJson") String afterJson,
            @Param("requestId") String requestId,
            @Param("ip") String ip,
            @Param("userAgent") String userAgent
    );
}
