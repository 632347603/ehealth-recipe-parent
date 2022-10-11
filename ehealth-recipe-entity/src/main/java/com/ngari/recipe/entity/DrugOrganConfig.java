package com.ngari.recipe.entity;

import ctd.schema.annotation.Dictionary;
import ctd.schema.annotation.ItemProperty;
import ctd.schema.annotation.Schema;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Time;
import java.util.Date;
import java.util.List;

import static javax.persistence.GenerationType.IDENTITY;

/**
 * 机构药品配置表
 */
@Entity
@Schema
@DynamicInsert
@DynamicUpdate
@Table(name = "drug_organ_config")
public class DrugOrganConfig implements Serializable {

    @ItemProperty(alias = "id")
    private Integer id;

    @ItemProperty(alias = "机构ID")
    private Integer organId;

    @ItemProperty(alias = "药品是否支持接口同步 默认为0（0：开关关闭；1：开关打开）")
    private Boolean enableDrugSync;

    @ItemProperty(
            alias = "药品同步  接口对接模式 1 自主查询  2 主动推送   默认 1"
    )
    private Integer dockingMode;

    @ItemProperty(alias = "新增药品目录同步是否需要人工审核开关，默认为0（0：系统审核；1：人工审核）")
    private Boolean enableDrugSyncArtificial;

    @ItemProperty(
            alias = "药品同步 定时更新时间"
    )
    private Time regularTime;

    @ItemProperty(alias = "药品数据来源（0:非互联网药品 1：互联网药品 100：全部）")
    private String drugDataSource;

    @ItemProperty(alias = "药品同步是否勾选新增")
    private Boolean enableDrugAdd;

    @ItemProperty(alias = "药品同步是否勾选更新")
    private Boolean enableDrugUpdate;

    @ItemProperty(
            alias = "药品同步是否勾选删除（禁用）"
    )
    private Boolean enableDrugDelete;


    @ItemProperty(alias = "新增 药品同步 数据范围   1药品类型 2  药品剂型  默认1")
    private Integer addDrugDataRange;

    @ItemProperty(alias = "修改 药品同步 数据范围   1药品类型 2  药品剂型  默认1")
    private Integer updateDrugDataRange;

    @ItemProperty(alias = "删除 药品同步 数据范围   1药品类型 2  药品剂型  默认1")
    private Integer delDrugDataRange;

    @ItemProperty(alias = "新增 同步药品类型  字典key用 ，隔开  eh.base.dictionary.DrugType")
    private String addSyncDrugType;

    @ItemProperty(alias = "修改 同步药品类型  字典key用 ，隔开  eh.base.dictionary.DrugType")
    private String updateSyncDrugType;

    @ItemProperty(alias = "删除 同步药品类型  字典key用 ，隔开  eh.base.dictionary.DrugType")
    private String delSyncDrugType;

    @ItemProperty(alias = "新增药品同步剂型list")
    private String addDrugFromList;
    @ItemProperty(alias = "修改药品同步剂型list")
    private String updateDrugFromList;
    @ItemProperty(alias = "删除药品同步剂型list")
    private String delDrugFromList;

    //作废
    @ItemProperty(
            alias = "药品同步 数据范围   1所有药品（无限制条件） 2  药品剂型  默认 2"
    )
    private Integer drugDataRange;

    //作废
    @ItemProperty(alias = "手动同步 剂型list暂存")
    private String drugFromList;


    @ItemProperty(alias = "创建时间")
    private Date createTime;

    @ItemProperty(alias = "更新时间")
    private Date updateTime;

    @ItemProperty(alias = "药企药品目录同步字段")
    private List<SaleDrugListSyncField> saleDrugListSyncFieldList;

    @Transient
    public List<SaleDrugListSyncField> getSaleDrugListSyncFieldList() {
        return saleDrugListSyncFieldList;
    }

    public void setSaleDrugListSyncFieldList(List<SaleDrugListSyncField> saleDrugListSyncFieldList) {
        this.saleDrugListSyncFieldList = saleDrugListSyncFieldList;
    }

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Column(name = "organ_id")
    public Integer getOrganId() {
        return organId;
    }

    public void setOrganId(Integer organId) {
        this.organId = organId;
    }

    @Column(name = "enable_drug_sync")
    public Boolean getEnableDrugSync() {
        return enableDrugSync;
    }

    public void setEnableDrugSync(Boolean enableDrugSync) {
        this.enableDrugSync = enableDrugSync;
    }

    @Column(name = "docking_mode")
    public Integer getDockingMode() {
        return dockingMode;
    }

    public void setDockingMode(Integer dockingMode) {
        this.dockingMode = dockingMode;
    }

    @Column(name = "enable_drug_sync_artificial")
    public Boolean getEnableDrugSyncArtificial() {
        return enableDrugSyncArtificial;
    }

    public void setEnableDrugSyncArtificial(Boolean enableDrugSyncArtificial) {
        this.enableDrugSyncArtificial = enableDrugSyncArtificial;
    }

    @Column(name = "regular_time")
    public Time getRegularTime() {
        return regularTime;
    }

    public void setRegularTime(Time regularTime) {
        this.regularTime = regularTime;
    }

    @Column(name = "enable_drug_add")
    public Boolean getEnableDrugAdd() {
        return enableDrugAdd;
    }

    public void setEnableDrugAdd(Boolean enableDrugAdd) {
        this.enableDrugAdd = enableDrugAdd;
    }

    @Column(name = "enable_drug_update")
    public Boolean getEnableDrugUpdate() {
        return enableDrugUpdate;
    }

    public void setEnableDrugUpdate(Boolean enableDrugUpdate) {
        this.enableDrugUpdate = enableDrugUpdate;
    }

    @Column(name = "enable_drug_delete")
    public Boolean getEnableDrugDelete() {
        return enableDrugDelete;
    }

    public void setEnableDrugDelete(Boolean enableDrugDelete) {
        this.enableDrugDelete = enableDrugDelete;
    }

    @Column(name = "add_drug_data_range")
    public Integer getAddDrugDataRange() {
        return addDrugDataRange;
    }

    public void setAddDrugDataRange(Integer addDrugDataRange) {
        this.addDrugDataRange = addDrugDataRange;
    }

    @Column(name = "update_drug_data_range")
    public Integer getUpdateDrugDataRange() {
        return updateDrugDataRange;
    }

    public void setUpdateDrugDataRange(Integer updateDrugDataRange) {
        this.updateDrugDataRange = updateDrugDataRange;
    }

    @Column(name = "del_drug_data_range")
    public Integer getDelDrugDataRange() {
        return delDrugDataRange;
    }

    public void setDelDrugDataRange(Integer delDrugDataRange) {
        this.delDrugDataRange = delDrugDataRange;
    }

    @Column(name = "add_sync_drug_type")
    public String getAddSyncDrugType() {
        return addSyncDrugType;
    }

    public void setAddSyncDrugType(String addSyncDrugType) {
        this.addSyncDrugType = addSyncDrugType;
    }

    @Column(name = "update_sync_drug_type")
    public String getUpdateSyncDrugType() {
        return updateSyncDrugType;
    }

    public void setUpdateSyncDrugType(String updateSyncDrugType) {
        this.updateSyncDrugType = updateSyncDrugType;
    }

    @Column(name = "del_sync_drug_type")
    public String getDelSyncDrugType() {
        return delSyncDrugType;
    }

    public void setDelSyncDrugType(String delSyncDrugType) {
        this.delSyncDrugType = delSyncDrugType;
    }

    @Column(name = "add_drug_from_list")
    public String getAddDrugFromList() {
        return addDrugFromList;
    }

    public void setAddDrugFromList(String addDrugFromList) {
        this.addDrugFromList = addDrugFromList;
    }

    @Column(name = "update_drug_from_list")
    public String getUpdateDrugFromList() {
        return updateDrugFromList;
    }

    public void setUpdateDrugFromList(String updateDrugFromList) {
        this.updateDrugFromList = updateDrugFromList;
    }

    @Column(name = "del_drug_from_list")
    public String getDelDrugFromList() {
        return delDrugFromList;
    }

    public void setDelDrugFromList(String delDrugFromList) {
        this.delDrugFromList = delDrugFromList;
    }

    @Column(name = "drug_data_source")
    public String getDrugDataSource() {
        return drugDataSource;
    }

    public void setDrugDataSource(String drugDataSource) {
        this.drugDataSource = drugDataSource;
    }

    @Column(name = "create_time", length = 19)
    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    @Column(name = "update_time", length = 19)
    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }



    @Column(name = "drug_data_range")
    public Integer getDrugDataRange() {
        return drugDataRange;
    }

    public void setDrugDataRange(Integer drugDataRange) {
        this.drugDataRange = drugDataRange;
    }

        @Column(name = "drugFromList")
    public String getDrugFromList() {
        return drugFromList;
    }

    public void setDrugFromList(String drugFromList) {
        this.drugFromList = drugFromList;
    }


}
