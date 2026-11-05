package edu.campus.lostfound.catalog;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local")
public class LocationService {

    private final CampusLocationMapper locationMapper;

    public LocationService(CampusLocationMapper locationMapper) {
        this.locationMapper = locationMapper;
    }

    public List<LocationView> listEnabled() {
        return locationMapper.selectList(Wrappers.<CampusLocationEntity>lambdaQuery()
                        .eq(CampusLocationEntity::getEnabled, true)
                        .orderByDesc(CampusLocationEntity::getIsServicePoint)
                        .orderByAsc(CampusLocationEntity::getId))
                .stream()
                .map(location -> new LocationView(
                        location.getId(),
                        location.getName(),
                        location.getAlias(),
                        location.getBuilding(),
                        location.getType(),
                        Boolean.TRUE.equals(location.getIsServicePoint())))
                .toList();
    }

    public record LocationView(
            Long id,
            String name,
            String alias,
            String building,
            String type,
            boolean servicePoint
    ) {
    }
}
