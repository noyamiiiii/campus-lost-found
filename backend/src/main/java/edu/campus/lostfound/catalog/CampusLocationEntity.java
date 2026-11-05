package edu.campus.lostfound.catalog;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 只映射发帖需要的字段，point 是空间类型，不在这里读取。
 */
@TableName("campus_location")
public class CampusLocationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String alias;
    private String building;
    private String type;
    private Boolean isServicePoint;
    private Boolean enabled;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getBuilding() {
        return building;
    }

    public void setBuilding(String building) {
        this.building = building;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Boolean getIsServicePoint() {
        return isServicePoint;
    }

    public void setIsServicePoint(Boolean isServicePoint) {
        this.isServicePoint = isServicePoint;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
