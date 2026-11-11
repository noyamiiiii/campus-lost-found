package edu.campus.lostfound.file;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("local")
public interface FileObjectMapper extends BaseMapper<FileObjectEntity> {
}