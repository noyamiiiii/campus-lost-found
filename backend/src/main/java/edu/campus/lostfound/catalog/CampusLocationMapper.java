package edu.campus.lostfound.catalog;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("local")
public interface CampusLocationMapper extends BaseMapper<CampusLocationEntity> {
}
