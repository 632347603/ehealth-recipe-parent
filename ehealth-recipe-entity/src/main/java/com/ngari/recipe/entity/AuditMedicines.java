package com.ngari.recipe.entity;

import ctd.schema.annotation.ItemProperty;
import ctd.schema.annotation.Schema;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

import static javax.persistence.GenerationType.IDENTITY;

/**
 * created by shiyuping on 2018/11/23
 */
@Schema
@Entity
@Table(name = "cdr_auditmedicines")
@Access(AccessType.PROPERTY)
public class AuditMedicines implements Serializable {

    private static final long serialVersionUID = 349035808126419725L;

    @ItemProperty(alias="自增id")
    private Integer id;

    @ItemProperty(alias="处方id")
    private Integer recipeId;

    @ItemProperty(alias="药品名")
    private String name;

    @ItemProperty(alias="药品编码")
    private String code;

    @ItemProperty(alias="创建时间")
    private Date createTime;

    @ItemProperty(alias = "最后修改时间")
    private Date lastModify;

    @ItemProperty(alias = "逻辑删除,1删除，0正常")
    private Integer logicalDeleted;

    @ItemProperty(alias = "状态")
    private Integer status;

    @ItemProperty(alias = "备注")
    private String remark;

    @Column(name = "createTime")
    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    @Column(name = "lastModify")
    public Date getLastModify() {
        return lastModify;
    }

    public void setLastModify(Date lastModify) {
        this.lastModify = lastModify;
    }

    @Column(name = "logicalDeleted")
    public Integer getLogicalDeleted() {
        return logicalDeleted;
    }

    public void setLogicalDeleted(Integer logicalDeleted) {
        this.logicalDeleted = logicalDeleted;
    }

    @Column(name = "status")
    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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

    @Column(name = "recipeId")
    public Integer getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(Integer recipeId) {
        this.recipeId = recipeId;
    }

    @Column(name = "name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Column(name = "code")
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Column(name = "remark")
    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
