package edu.campus.lostfound.admin;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("local")
public interface AdminQueryMapper {

    List<AdminUserRow> findUsers(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long countUsers(@Param("keyword") String keyword, @Param("status") String status);
}
