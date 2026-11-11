package edu.campus.lostfound.notification;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("local")
public interface NotificationMapper extends BaseMapper<NotificationEntity> {
}