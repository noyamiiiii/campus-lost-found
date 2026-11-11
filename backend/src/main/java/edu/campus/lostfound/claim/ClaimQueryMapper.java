package edu.campus.lostfound.claim;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Profile;

/**
 * 认领的联表查询（帖子、双方用户），SQL 见 resources/mapper/ClaimMapper.xml。
 */
@Mapper
@Profile("local")
public interface ClaimQueryMapper {

    List<ClaimRow> findMine(@Param("userId") Long userId);

    List<ClaimRow> findReceived(@Param("ownerId") Long ownerId);

    ClaimRow findOne(@Param("claimId") Long claimId);
}